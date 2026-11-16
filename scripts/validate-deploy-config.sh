#!/usr/bin/env bash
set -euo pipefail
python3 - <<'PYTHON'
import os
import uuid
from urllib.parse import urlsplit

def require(name):
    value = os.environ.get(name, "")
    if not value:
        raise SystemExit(f"{name} obrigatório.")
    return value

def boolean(name):
    value = require(name)
    if value not in ("true", "false"):
        raise SystemExit(f"{name} deve ser true ou false.")
    return value == "true"

def queue_url(name):
    url = urlsplit(require(name))
    expected_host = f"sqs.{require('AWS_REGION')}.amazonaws.com"
    parts = url.path.strip("/").split("/")
    if (url.scheme != "https" or url.netloc != expected_host or url.query or url.fragment
            or len(parts) != 2 or parts[0] != require("AWS_ACCOUNT_ID")
            or not parts[1] or parts[1].endswith(".fifo")):
        raise SystemExit(f"{name} exige URL SQS Standard da conta/região configurada.")

def bridge_url(name):
    value = os.environ.get(name)
    if not value:
        return False
    url = urlsplit(value)
    if (url.scheme != "https" or not url.hostname or url.username is not None
            or url.path not in ("", "/") or url.query or url.fragment):
        raise SystemExit(f"{name} exige a URL base HTTPS da ponte.")
    return True


try:
    uuid.UUID(require("OS_BRANCH_ID"))
except ValueError:
    raise SystemExit("OS_BRANCH_ID exige UUID válido.") from None
if boolean("BILLING_SQS_ENABLED") and not bridge_url("BILLING_SQS_BRIDGE_URL"):
    queue_url("BILLING_SQS_QUEUE_URL")
if boolean("HISTORY_DYNAMODB_ENABLED") and not bridge_url("HISTORY_BRIDGE_URL"):
    require("HISTORY_DYNAMODB_TABLE")
PYTHON
