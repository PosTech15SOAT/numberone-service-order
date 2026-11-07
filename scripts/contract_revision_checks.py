"""Active review package checks, invoked by the existing CI validator."""
import copy
import hashlib
import json
import socket
import unittest
from pathlib import Path

import yaml
from jsonschema import Draft202012Validator, FormatChecker
from openapi_schema_validator import OAS30Validator
from openapi_spec_validator import validate as validate_openapi
from referencing import Registry, Resource
from referencing.exceptions import NoSuchResource

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


MANIFEST = read(ROOT / "doc/contracts/saga/packages.json")
PACKAGE = ROOT / "doc/contracts/saga" / MANIFEST["active"]
CATALOG = read(PACKAGE / "catalog.json")
ENTRIES = {entry["type"]: entry for entry in CATALOG["messages"]}
SCHEMAS = {p.relative_to(PACKAGE).as_posix(): read(p) for p in PACKAGE.rglob("*.schema.json")}


def deny_remote(uri):
    raise NoSuchResource(ref=uri)


REGISTRY = Registry(retrieve=deny_remote).with_resources(
    (schema["$id"], Resource.from_contents(schema)) for schema in SCHEMAS.values()
)
CHECKER = FormatChecker()
SPECS = {p.name: yaml.safe_load(p.read_text(encoding="utf-8")) for p in PACKAGE.glob("*.openapi.yaml")}


def validator(path):
    return Draft202012Validator(SCHEMAS[path], registry=REGISTRY, format_checker=CHECKER)


def pointer(error):
    return "/" + "/".join(str(part) for part in error.absolute_path)


def walk(node):
    if isinstance(node, dict):
        yield node
        for value in node.values():
            yield from walk(value)
    elif isinstance(node, list):
        for item in node:
            yield from walk(item)


def inline_openapi(document, node, stack=()):
    """Resolve only local references; never allow validator HTTP/file retrieval."""
    if isinstance(node, list):
        return [inline_openapi(document, value, stack) for value in node]
    if not isinstance(node, dict):
        return node
    if "$ref" in node:
        ref = node["$ref"]
        if not ref.startswith("#/") or ref in stack:
            raise ValueError("Only acyclic local OpenAPI references are allowed")
        target = document
        for part in ref[2:].split("/"):
            target = target[part.replace("~1", "/").replace("~0", "~")]
        return inline_openapi(document, target, (*stack, ref))
    return {key: inline_openapi(document, value, stack) for key, value in node.items()}


def http_validator(case):
    document = SPECS[case["document"]]
    operation = document["paths"][case["path"]][case["method"]]
    source = operation["requestBody"] if case["status"] == "request" else operation["responses"][case["status"]]
    schema = source["content"]["application/json"]["schema"]
    return OAS30Validator(inline_openapi(document, schema), format_checker=CHECKER)


class RevisionContractChecks(unittest.TestCase):
    def test_network_is_actually_disabled_by_entrypoint(self):
        with self.assertRaisesRegex(RuntimeError, "Network is disabled"):
            socket.getaddrinfo("offline-check.example.invalid", 443)

    def test_active_package_and_catalog_are_explicit(self):
        self.assertEqual(MANIFEST["active"], "v2")
        self.assertEqual(MANIFEST["activeVersion"], CATALOG["packageVersion"])
        self.assertFalse(MANIFEST["approvedContract"])
        self.assertEqual({e["type"] for e in CATALOG["messages"] if e["enabled"]},
                         {"OrcamentoEnviado", "OrcamentoAprovado", "OrcamentoRejeitado", "PagamentoConfirmado"})
        self.assertEqual(len(ENTRIES), len(CATALOG["messages"]))
        self.assertEqual({e["schema"] for e in ENTRIES.values()},
                         {p for p in SCHEMAS if p.startswith(("events/", "conditional/"))})
        dispatch = {x["$ref"] for x in SCHEMAS["message.schema.json"]["oneOf"]}
        self.assertEqual(dispatch, {SCHEMAS[e["schema"]]["$id"] for e in ENTRIES.values() if e["enabled"]})
        for entry in ENTRIES.values():
            self.assertEqual((entry["producer"], entry["consumer"]), ("billing-service", "os-service"))
            self.assertEqual(read(PACKAGE / entry["example"])["eventType"], entry["type"])

    def test_schemas_and_references_are_offline(self):
        self.assertEqual(len(SCHEMAS), len({s["$id"] for s in SCHEMAS.values()}))
        for name, schema in SCHEMAS.items():
            with self.subTest(schema=name):
                self.assertEqual(schema["$schema"], "https://json-schema.org/draft/2020-12/schema")
                Draft202012Validator.check_schema(schema)
                for node in walk(schema):
                    if "$ref" in node:
                        REGISTRY.resolver(schema["$id"]).lookup(node["$ref"])
        with self.assertRaises(NoSuchResource):
            REGISTRY.get_or_retrieve("https://unregistered.example.invalid/schema")

    def test_valid_event_fixtures(self):
        for path in (PACKAGE / "examples").glob("*.json"):
            data = read(path)
            with self.subTest(fixture=path.name):
                validator(ENTRIES[data["eventType"]]["schema"]).validate(data)
                validator("message.schema.json").validate(data)

    def test_invalid_event_fixtures_fail_for_expected_reason(self):
        cases = read(PACKAGE / "invalid-examples/expectations.json")["cases"]
        actual = {p.name for p in (PACKAGE / "invalid-examples").glob("*.json")} - {"expectations.json"}
        self.assertEqual({c["file"] for c in cases}, actual)
        self.assertEqual(len(cases), len(actual))
        for case in cases:
            with self.subTest(fixture=case["file"]):
                data = read(PACKAGE / "invalid-examples" / case["file"])
                errors = list(validator(ENTRIES[case["type"]]["schema"]).iter_errors(data))
                self.assertIn((case["keyword"], case["pointer"]), {(e.validator, pointer(e)) for e in errors})
                self.assertFalse(validator("message.schema.json").is_valid(data))

    def test_conditional_refund_is_not_enabled_by_existing_schema(self):
        entry = ENTRIES["PagamentoAtualizado"]
        self.assertFalse(entry["enabled"])
        self.assertTrue(entry["condition"])
        data = read(PACKAGE / entry["example"])
        validator(entry["schema"]).validate(data)
        self.assertFalse(validator("message.schema.json").is_valid(data))
        for field, value in (("paymentStatus", "PARTIALLY_REFUNDED"), ("paymentVersion", 0), ("paymentId", "bad")):
            bad = copy.deepcopy(data)
            bad["payload"][field] = value
            self.assertFalse(validator(entry["schema"]).is_valid(bad))

    def test_additive_fields_do_not_enable_unknown_types(self):
        for entry in ENTRIES.values():
            data = read(PACKAGE / entry["example"])
            data["futureField"] = True
            data["payload"]["futureField"] = True
            validator(entry["schema"]).validate(data)
            data["eventType"] = "UnknownType"
            self.assertFalse(validator("message.schema.json").is_valid(data))

    def test_composition_proposal_and_stale_fixture_consistency_only(self):
        data = read(PACKAGE / "proposals/approval-composition.json")
        validator("proposals/approval-composition.schema.json").validate(data)
        unbound = read(PACKAGE / ENTRIES["OrcamentoAprovado"]["example"])
        self.assertFalse(validator("proposals/approval-composition.schema.json").is_valid(unbound))
        scenario = read(PACKAGE / "proposals/stale-approval.json")
        validator("proposals/approval-composition.schema.json").validate(scenario["incoming"])
        self.assertLess(scenario["incoming"]["payload"]["compositionVersion"], scenario["current"]["compositionVersion"])
        self.assertLess(scenario["incoming"]["payload"]["budgetVersion"], scenario["current"]["budgetVersion"])
        self.assertNotEqual(scenario["snapshots"][0]["items"], scenario["snapshots"][1]["items"])
        self.assertFalse(scenario["expected"]["mayStart"])

    def test_openapi_documents_and_local_references(self):
        self.assertEqual(set(SPECS), set(CATALOG["openapi"]))
        contract_ids = set()
        for name, document in SPECS.items():
            with self.subTest(document=name):
                inline_openapi(document, document)
                validate_openapi(document)
                self.assertEqual(document["openapi"], "3.0.3")
                for node in walk(document):
                    if "x-contract-id" in node:
                        contract_ids.add(node["x-contract-id"])
        self.assertEqual(contract_ids, {"CT-02A", "CT-03", "CT-05", "CT-08", "CT-08B", "CT-07", "CT-09"})
        with self.assertRaises(ValueError):
            inline_openapi({}, {"$ref": "https://remote.example.invalid/schema"})

    def test_http_fixtures_against_actual_operation_schemas(self):
        cases = read(PACKAGE / "http-examples/expectations.json")["cases"]
        actual = {p.relative_to(PACKAGE).as_posix() for p in (PACKAGE / "http-examples").glob("*.json")} - {"http-examples/expectations.json"}
        self.assertEqual({c["file"] for c in cases}, actual)
        self.assertEqual(len(cases), len(actual))
        for case in cases:
            with self.subTest(fixture=case["file"]):
                errors = list(http_validator(case).iter_errors(read(PACKAGE / case["file"])))
                if case["valid"]:
                    self.assertFalse(errors, [(e.validator, pointer(e)) for e in errors])
                else:
                    self.assertIn((case["keyword"], case["pointer"]), {(e.validator, pointer(e)) for e in errors})

    def test_source_snapshots_match_recorded_hashes(self):
        for source in read(ROOT / "doc/specification/revision-1b/source-hashes.json")["sources"]:
            self.assertEqual(hashlib.sha256((ROOT / source["snapshot"]).read_bytes()).hexdigest(), source["sha256"])


def summary():
    cases = read(PACKAGE / "http-examples/expectations.json")["cases"]
    return {"package": MANIFEST["active"], "packageVersion": CATALOG["packageVersion"],
            "approvedContract": False, "schemaFiles": len(SCHEMAS),
            "enabledEventTypes": sum(e["enabled"] for e in ENTRIES.values()),
            "conditionalEventTypes": sum(not e["enabled"] for e in ENTRIES.values()),
            "validEventFixtures": len(list((PACKAGE / "examples").glob("*.json"))),
            "invalidEventFixtures": len(read(PACKAGE / "invalid-examples/expectations.json")["cases"]),
            "conditionalEventFixtures": 1, "proposalFixtures": 2,
            "openapiDocuments": len(SPECS), "validHttpFixtures": sum(c["valid"] for c in cases),
            "invalidHttpFixtures": sum(not c["valid"] for c in cases)}
