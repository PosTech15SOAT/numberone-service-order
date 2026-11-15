"""Offline validation of captured phase 5 HTTP fixtures against the active OpenAPI."""
import json
import socket
import yaml
from pathlib import Path
from openapi_spec_validator import validate
from openapi_schema_validator import OAS30Validator
from contract_revision_checks import SPECS, inline_openapi, CHECKER


def deny_network(*args, **kwargs):
    raise RuntimeError("Network is disabled")


socket.getaddrinfo = deny_network
root = Path(__file__).resolve().parents[1]
trace = json.loads((root / "target/phase5-contract-trace.json").read_text(encoding="utf-8"))
assert trace, "Run SagaCompensationIT first"
seen = set()
local_spec = yaml.safe_load((root / "doc/contracts/os-phase5.openapi.yaml").read_text(encoding="utf-8"))
validate(local_spec)
for index, case in enumerate(trace):
    spec = local_spec if case["file"] == "os-phase5.openapi.yaml" else SPECS[case["file"]]
    schema = inline_openapi(spec, spec["components"]["schemas"][case["schema"]])
    errors = list(OAS30Validator(schema, format_checker=CHECKER).iter_errors(case["body"]))
    assert not errors, f"Entry {index} {case['schema']}: " + "; ".join(str(e.message) for e in errors)
    seen.add(case["schema"])
required = {"ConsumptionRequest", "Consumed", "Rejected", "Unknown", "InventoryOperation", "ReversalRequest",
            "Closed", "Reversed", "CancellationRequest", "Cancelled", "ContextCancellationRequest", "ClosedBudgetContext", "Status", "Verification"}
assert required <= seen, f"Missing evidence: {required - seen}"
print(json.dumps({"package": "2.0.0-os-baseline.1", "capturedPayloads": len(trace),
                  "schemas": sorted(seen), "valid": True, "participants": "HTTP fixtures with PostgreSQL ledgers"}, indent=2))
