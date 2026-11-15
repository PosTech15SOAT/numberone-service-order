"""Persist the successful phase 5 verification and its synthetic operation ledgers."""
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
assert "BUILD SUCCESS" in (root / "phase5-verify.log").read_text(encoding="utf-8", errors="replace")
counts = {}
for label, directory in [("unit", "surefire-reports"), ("integration", "failsafe-reports")]:
    reports = [ET.parse(p).getroot() for p in (root / "target" / directory).glob("TEST-*.xml")]
    assert reports, f"Missing {directory}"
    counts[label] = {key: sum(int(r.attrib[key]) for r in reports) for key in ["tests", "failures", "errors", "skipped"]}
    assert all(counts[label][key] == 0 for key in ["failures", "errors", "skipped"])
coverage = ET.parse(root / "target/site/jacoco/jacoco.xml").getroot()
line = next(c for c in coverage.findall("counter") if c.attrib["type"] == "LINE")
covered, missed = int(line.attrib["covered"]), int(line.attrib["missed"])
trace = json.loads((root / "phase5-trace-validation.log").read_text(encoding="utf-8-sig"))
assert trace["valid"]
contracts = json.loads(next(line for line in reversed((root / "phase5-contracts.log").read_text(encoding="utf-8", errors="replace").splitlines()) if line.startswith("{")))
assert contracts["successful"]
scenarios = json.loads((root / "target/phase5-evidence.json").read_text(encoding="utf-8"))
assert scenarios
source_files = [
    "src/main/resources/db/migration/V5__compensation_recovery.sql",
    "src/main/java/br/com/fiap/numberone/serviceorder/application/services/SagaCompensation.java",
    "src/main/java/br/com/fiap/numberone/serviceorder/application/services/SagaWorkflow.java",
    "src/main/java/br/com/fiap/numberone/serviceorder/application/services/SagaRecoveryWorker.java",
    "src/main/java/br/com/fiap/numberone/serviceorder/infrastructure/integration/HttpSagaParticipants.java",
    "src/main/java/br/com/fiap/numberone/serviceorder/api/controllers/SagaCompensationController.java",
    "doc/contracts/os-phase5.openapi.yaml",
]
summary = {
    "phase": 5, "scope": "OS C1-C6 and recovery only", "date": "2026-11-08",
    "baseHead": "13817e5466077bc2c6b0b9a0c61254ec6727e108",
    "preservedStagedDiffHash": "bded155ce36c25aadb1d9b3cd099b05aee0918f9",
    "contracts": "2.0.0-os-baseline.1", "approvedContract": False,
    "tests": counts, "totalTests": sum(c["tests"] for c in counts.values()),
    "coverage": {"coveredLines": covered, "totalLines": covered + missed,
                 "percent": round(100 * covered / (covered + missed), 2), "gate": 80},
    "contractChecks": contracts["checks"], "wireValidation": trace,
    "database": "PostgreSQL 17.9 Testcontainers", "java": "26.0.1, release 25",
    "transport": "real HTTP/Tomcat; simulated Inventory/Billing with durable fixture ledgers",
    "freshRecoveryJvmLogs": sorted(p.name for p in (root / "target").glob("recovery-process-*.log")),
    "scenarios": [{"scenario": s["scenario"], "balances": s["balances"], "saga": s["saga"]} for s in scenarios],
    "sourceSha256": {name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in source_files},
    "externalServicesValidated": False, "deployed": False, "committed": False,
}
destination = root / "doc/progress/evidence"
destination.mkdir(exist_ok=True, parents=True)
for name, value in [("fase-5.json", summary), ("fase-5-scenarios.json", scenarios)]:
    (destination / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps({"tests": summary["totalTests"], "coverage": summary["coverage"],
                  "payloads": trace["capturedPayloads"], "scenarios": len(scenarios)}, indent=2))
