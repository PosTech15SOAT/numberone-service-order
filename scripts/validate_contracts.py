"""Portable contract checks; no service, database, queue or remote schema access."""
import json
import re
import unittest
from decimal import Decimal
from pathlib import Path

import yaml
from jsonschema import Draft202012Validator, FormatChecker
from openapi_spec_validator import validate as validate_openapi
from referencing import Registry, Resource
from referencing.exceptions import NoSuchResource

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = ROOT / "doc/contracts/saga/v1"


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def deny_remote(uri):
    raise NoSuchResource(ref=uri)


SCHEMAS = {p.relative_to(PACKAGE).as_posix(): read(p) for p in PACKAGE.rglob("*.schema.json")}
REGISTRY = Registry(retrieve=deny_remote).with_resources(
    (schema["$id"], Resource.from_contents(schema)) for schema in SCHEMAS.values()
)
CATALOG = read(PACKAGE / "catalog.json")["messages"]
BY_TYPE = {entry["type"]: entry for entry in CATALOG}
CHECKER = FormatChecker()


def validator(path):
    return Draft202012Validator(SCHEMAS[path], registry=REGISTRY, format_checker=CHECKER)


def pointer(error):
    return "/" + "/".join(str(part) for part in error.absolute_path)


def failures(errors):
    # Never print rejected values, recipient data or complete payloads.
    return ", ".join(f"{e.validator}@{pointer(e)}" for e in errors)


class ContractChecks(unittest.TestCase):
    def test_schema_draft_and_all_references_resolve_without_network(self):
        def walk(node, resolver):
            if isinstance(node, dict):
                if "$ref" in node:
                    resolver.lookup(node["$ref"])
                for value in node.values():
                    walk(value, resolver)
            elif isinstance(node, list):
                for value in node:
                    walk(value, resolver)

        ids = [schema["$id"] for schema in SCHEMAS.values()]
        self.assertEqual(len(ids), len(set(ids)))
        for path, schema in SCHEMAS.items():
            with self.subTest(schema=path):
                self.assertEqual(schema["$schema"], "https://json-schema.org/draft/2020-12/schema")
                Draft202012Validator.check_schema(schema)
                walk(schema, REGISTRY.resolver(schema["$id"]))
        with self.assertRaises(NoSuchResource):
            REGISTRY.get_or_retrieve("https://unregistered.example.invalid/schema")

    def test_catalog_covers_every_message_in_original_specification(self):
        text = (ROOT / "doc/specification/contrato-saga-v1-proposta.md").read_text(encoding="utf-8")
        section = text.split("## 4. Matriz de comandos e eventos", 1)[1].split("## 5.", 1)[0]
        expected = set(re.findall(r"^\| `([A-Z][A-Za-z]+)` \|", section, flags=re.MULTILINE))
        self.assertEqual(len(expected), 21)
        self.assertEqual(set(BY_TYPE), expected)
        self.assertEqual(len(CATALOG), len(expected))
        declared_schemas = {item["schema"] for item in CATALOG}
        actual_schemas = {p for p in SCHEMAS if p.startswith(("commands/", "events/"))}
        self.assertEqual(declared_schemas, actual_schemas)
        for entry in CATALOG:
            fixture = read(PACKAGE / entry["example"])
            self.assertEqual(fixture["type"], entry["type"])
            self.assertEqual(fixture["producer"], entry["producer"])

    def test_all_valid_examples_against_typed_and_dispatch_schemas(self):
        examples = list((PACKAGE / "examples").glob("*.json"))
        self.assertGreaterEqual(len(examples), 21)
        for path in examples:
            with self.subTest(fixture=path.name):
                data = read(path)
                for schema in (BY_TYPE[data["type"]]["schema"], "message.schema.json"):
                    errors = list(validator(schema).iter_errors(data))
                    self.assertFalse(errors, f"{path.name}: {failures(errors)}")

    def test_invalid_examples_fail_at_expected_keyword_and_path(self):
        cases = read(PACKAGE / "invalid-examples/expectations.json")["cases"]
        actual = {p.name for p in (PACKAGE / "invalid-examples").glob("*.json")} - {"expectations.json"}
        self.assertEqual({case["file"] for case in cases}, actual)
        self.assertEqual(len(cases), len(actual))
        for case in cases:
            with self.subTest(fixture=case["file"]):
                data = read(PACKAGE / "invalid-examples" / case["file"])
                errors = list(validator(BY_TYPE[case["type"]]["schema"]).iter_errors(data))
                expected = (case["keyword"], case["pointer"])
                self.assertIn(expected, {(e.validator, pointer(e)) for e in errors}, failures(errors))
                self.assertFalse(validator("message.schema.json").is_valid(data))

    def test_format_assertions_are_effectively_enabled(self):
        for format_name, bad_value in (("uuid", "broken"), ("date-time", "2026-02-30T12:00:00Z"),
                                       ("email", "missing-at"), ("uri", "not a uri")):
            with self.subTest(format=format_name):
                v = Draft202012Validator({"type": "string", "format": format_name}, format_checker=CHECKER)
                self.assertFalse(v.is_valid(bad_value), "Required format checker is unavailable")

    def test_additive_fields_are_accepted_at_all_object_levels(self):
        def extend(node):
            if isinstance(node, dict):
                for value in list(node.values()):
                    extend(value)
                node["futureOptionalField"] = {"ignored": True}
            elif isinstance(node, list):
                for item in node:
                    extend(item)
        for entry in CATALOG:
            data = read(PACKAGE / entry["example"])
            extend(data)
            with self.subTest(message=entry["type"]):
                errors = list(validator(entry["schema"]).iter_errors(data))
                self.assertFalse(errors, failures(errors))

    def test_demonstration_fixtures_are_consistent_not_a_saga_execution(self):
        get = lambda name: read(PACKAGE / "examples" / (name + ".json"))
        approved = get("BudgetApproved")["payload"]
        item = approved["approvedItems"][0]
        self.assertEqual(item["quantity"], 1)
        self.assertEqual(item["materials"][0]["quantity"], 1)
        fixture_total = Decimal(item["laborUnitPrice"]) + Decimal(item["materials"][0]["approvedUnitPrice"])
        self.assertEqual(fixture_total, Decimal("350.00"))
        self.assertEqual(approved["totalAmount"], "350.00")
        for name in ("RequestPayment", "PaymentConfirmed"):
            self.assertEqual(get(name)["payload"]["totalAmount"], "350.00")
        reserve = get("ReserveMaterials")["payload"]["allocations"]
        self.assertEqual(reserve, get("MaterialsReserved")["payload"]["allocations"])
        self.assertEqual(reserve[0]["serviceOrderItemId"], item["serviceOrderItemId"])
        self.assertEqual(reserve[0]["materials"][0]["materialId"], item["materials"][0]["materialId"])
        for command, event in (("CreateBudget", "BudgetApproved"), ("ReserveMaterials", "MaterialsReserved"),
                               ("RequestPayment", "PaymentConfirmed"), ("ConsumeMaterials", "MaterialsConsumed"),
                               ("ReleaseMaterials", "MaterialsReleased"), ("CancelBilling", "BillingCompensated")):
            c, e = get(command), get(event)
            self.assertEqual(c["messageId"], e["causationId"])
            self.assertNotEqual(c["messageId"], e["messageId"])
            for key in ("operationId", "attemptId", "sagaId", "serviceOrderId", "branchId"):
                self.assertEqual(c[key], e[key])
        self.assertEqual(get("BudgetApproved.no-materials")["payload"]["approvedItems"][0]["materials"], [])
        self.assertEqual(get("RequestPayment.no-materials")["payload"]["totalAmount"], "150.00")
        self.assertEqual(get("RequestPayment.no-materials")["causationId"], get("BudgetApproved.no-materials")["messageId"])
        self.assertEqual(get("PaymentConfirmed.no-materials")["payload"]["totalAmount"], "150.00")
        self.assertEqual(get("PaymentConfirmed.no-materials")["causationId"], get("RequestPayment.no-materials")["messageId"])

    def test_openapi_documents(self):
        specs = list(PACKAGE.glob("*.openapi.yaml"))
        self.assertEqual(len(specs), 2)
        for path in specs:
            with self.subTest(document=path.name):
                document = yaml.safe_load(path.read_text(encoding="utf-8"))
                self.assertEqual(document["openapi"], "3.0.3")
                validate_openapi(document)


if __name__ == "__main__":
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(ContractChecks)
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    summary = {
        "scope": "schema-and-fixture-validation-only",
        "approvedContract": False,
        "schemaFiles": len(SCHEMAS), "messageTypes": len(CATALOG),
        "validFixtures": len(list((PACKAGE / "examples").glob("*.json"))),
        "invalidFixtures": len(read(PACKAGE / "invalid-examples/expectations.json")["cases"]),
        "checks": result.testsRun, "failures": len(result.failures), "errors": len(result.errors),
        "successful": result.wasSuccessful(), "remoteSchemaRetrieval": "denied",
    }
    output = ROOT / "target/contract-validation.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary))
    raise SystemExit(0 if result.wasSuccessful() else 1)
