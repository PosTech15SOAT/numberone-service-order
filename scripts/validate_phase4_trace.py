"""Validate actual HTTP payloads captured by SagaMainFlowIT against the active contracts."""
import json
import socket
from pathlib import Path
from openapi_schema_validator import OAS30Validator
from contract_revision_checks import SPECS, inline_openapi, CHECKER


def deny_network(*args, **kwargs):
    raise RuntimeError("Network is disabled")


socket.getaddrinfo = deny_network
root = Path(__file__).resolve().parents[1]
trace = json.loads((root / "target/phase4-contract-trace.json").read_text(encoding="utf-8"))
assert trace, "Run SagaMainFlowIT first"
seen = set()
for index, case in enumerate(trace):
    document = SPECS[case["file"]]
    schema = inline_openapi(document, document["components"]["schemas"][case["schema"]])
    errors = list(OAS30Validator(schema, format_checker=CHECKER).iter_errors(case["body"]))
    assert not errors, f"Entry {index} {case['file']}:{case['schema']}: " + "; ".join(f"{list(e.absolute_path)}: {e.message}" for e in errors)
    seen.add(case["schema"])
required = {"ServiceOrderState", "QuotationContext", "ConsumptionRequest", "Consumed", "Rejected",
            "Settlement", "CurrentBudget", "StartedItem", "PendingItem", "ReadyOrder", "DeliveredOrder"}
assert required <= seen, f"Missing evidence: {required - seen}"
print(json.dumps({"package": "2.0.0-os-baseline.1", "capturedPayloads": len(trace),
                  "schemas": sorted(seen), "valid": True, "participants": "HTTP simulators", "database": "PostgreSQL 17.9"}, indent=2))
