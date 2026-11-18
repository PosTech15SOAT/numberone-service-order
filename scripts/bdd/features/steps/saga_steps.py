import hashlib
import hmac
import json
import os
import subprocess
import time
import uuid
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from behave import given, then, when


def sql(context, service, statement):
    return subprocess.check_output(
        ["docker", "exec", f"{context.run}-pg",
         "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", f"{service}_local",
         "-d", f"{service}_db", "-tA", "-c", statement],
        text=True,
    ).strip()


def call(context, service, method, path, body=None, expected=200, headers=None):
    request_headers = dict(context.admin if headers is None else headers)
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        request_headers["Content-Type"] = "application/json"
    request = Request(context.urls[service] + path, data, request_headers, method=method)
    try:
        with urlopen(request, timeout=15) as response:
            status, payload = response.status, response.read()
    except HTTPError as error:
        status, payload = error.code, error.read()
    assert status == expected, f"{service} {method} {path}: HTTP {status}, esperado {expected}"
    return json.loads(payload) if payload else None


def wait_state(context, target):
    deadline = time.monotonic() + (90 if target == "COMPENSATED" else 35)
    while time.monotonic() < deadline:
        current = call(context, "os", "GET", context.order_path)["sagaState"]
        if current == target:
            return
        time.sleep(0.5)
    raise AssertionError(f"Estado esperado {target}, recebido {current}")


def stock(context):
    return call(context, "inventory", "GET", f"/api/admin/itens/{context.material}")["quantidadeEstoque"]


def start_item(context, expected=200):
    return call(context, "os", "PATCH", context.item_path + "/iniciar", expected=expected,
                headers=context.admin | {"Idempotency-Key": context.operation})


def billing_get(context, path):
    return call(context, "billing", "GET", path, headers={
        "Authorization": "Bearer local-os-billing",
        "X-Correlation-Id": context.admin["X-Correlation-Id"],
    })


@given('uma OS diagnosticada com uma peça {availability}')
def diagnosed_order(context, availability):
    context.run = os.environ["NUMBERONE_BDD_RUN"]
    context.operation = str(uuid.uuid4())
    context.urls = {
        service: "http://" + subprocess.check_output(
            ["docker", "port", f"{context.run}-{service}", "8080/tcp"], text=True
        ).splitlines()[0]
        for service in ("os", "billing", "inventory")
    }
    context.admin = {
        "X-Authenticated-Subject": str(uuid.uuid4()),
        "X-Authenticated-Status": "ACTIVE",
        "X-Authenticated-Roles": "ADMIN",
        "X-Authenticated-Permissions": "ORDENS_SERVICO_ESCREVER",
        "X-Correlation-Id": str(uuid.uuid4()),
    }
    context.initial_stock = 3 if availability == "disponível" else 0
    suffix = uuid.uuid4().hex[:8].upper()
    context.material = call(context, "inventory", "POST", "/api/admin/itens", {
        "codigo": f"BDD-{suffix}", "nome": "Peça BDD", "tipoItem": "PECA",
        "unidadeMedida": "UNIDADE", "quantidadeEstoque": 0, "estoqueMinimo": 0,
        "custoUnitario": "10.00", "precoVenda": "20.40", "ativo": True,
    }, 201)["id"]
    if context.initial_stock:
        call(context, "inventory", "POST", "/api/admin/estoque/entrada", {
            "idItemEstoque": context.material, "origemMovimentacao": "COMPRA", "quantidade": context.initial_stock,
        }, 201)
    # A fixture usa CPFs de teste com dígitos verificadores válidos, um por cenário.
    cpf = "52998224725" if context.initial_stock else "11144477735"
    customer = call(context, "os", "POST", "/api/admin/clientes", {
        "nome": "Cliente BDD", "tipoDocumento": "PESSOA_FISICA", "documento": cpf,
        "email": "bdd@example.invalid", "telefone": "00000000000", "endereco": "Fixture", "ativo": True,
    }, 201)["id"]
    vehicle = call(context, "os", "POST", "/api/admin/veiculos", {
        "placa": "BDD1A01" if context.initial_stock else "BDD1A02",
        "marca": "Fixture", "modelo": "Fixture", "ano": 2026, "idCliente": customer,
    }, 201)["id"]
    service = call(context, "os", "POST", "/api/admin/servicos", {
        "codigo": f"BDD-{suffix}", "nome": "Serviço BDD", "descricao": "Fixture",
        "tipoServico": "REVISAO", "valorBase": "150.00", "tempoEstimadoMinutos": 30,
    }, 201)["id"]
    context.order = call(context, "os", "POST", "/api/admin/ordens-servico", {
        "idCliente": customer, "idVeiculo": vehicle, "descricaoInicial": "Inspeção BDD",
        "descricaoDiagnostico": "Inspeção", "dataHoraEntrada": "2026-11-12T10:00:00",
    }, 201)["id"]
    context.order_path = f"/api/admin/ordens-servico/{context.order}"
    call(context, "os", "PATCH", context.order_path + "/iniciar-diagnostico", {
        "descricaoDiagnosticoFinal": "Diagnóstico BDD", "dataHoraPrevista": "2026-11-12T14:00:00",
    })
    item = call(context, "os", "POST", "/api/admin/itens-ordem-servico", {
        "idOrdemServico": context.order, "idServico": service, "valor": "150.00",
    }, 201)["id"]
    context.item_path = f"/api/admin/itens-ordem-servico/{item}"
    call(context, "os", "POST", context.item_path + "/insumos", {
        "idMaterial": context.material, "quantidadePrevista": 1,
    }, 201)


@given("um orçamento aprovado e recebido pela OS")
def approve(context):
    budget = call(context, "billing", "POST", context.order_path + "/orcamentos", {}, 201)["id"]
    call(context, "billing", "POST", f"/api/admin/orcamentos-ordem-servico/{budget}/registrar-aprovacao")
    wait_state(context, "READY_FOR_EXECUTION")


@when("o funcionário repete a solicitação de início com a mesma chave")
def repeat_start(context):
    first, second = start_item(context), start_item(context)
    assert first["operationState"] == second["operationState"] == "CONFIRMED"
    assert first["startOperationId"] == second["startOperationId"] == context.operation


@then("o Inventory baixa a peça uma única vez")
def single_consumption(context):
    assert stock(context) == context.initial_stock - 1


@when("a execução é concluída e chega o webhook de pagamento")
def payment(context):
    call(context, "os", "PATCH", context.item_path + "/concluir")
    call(context, "os", "PATCH", context.order_path + "/concluir")
    wait_state(context, "WAITING_PAYMENT")
    context.settlement = f"/internal/payments/service-orders/{context.order}/settlement"
    assert not billing_get(context, context.settlement)["settled"]
    checkout = call(context, "billing", "POST", context.order_path + "/pagamentos/checkout",
                    headers=context.admin | {"Idempotency-Key": str(uuid.uuid4())})
    provider = checkout["idOrdemProvedor"]
    call(context, "billing", "PUT", f"/fixture/provider/{provider}/processed")
    timestamp, request_id = int(time.time()), str(uuid.uuid4())
    manifest = f"id:{provider};request-id:{request_id};ts:{timestamp};"
    signature = hmac.new(b"fictitious-webhook-secret", manifest.encode(), hashlib.sha256).hexdigest()
    call(context, "billing", "POST", f"/api/public/pagamentos/notificacoes/mercado-pago?data.id={provider}&type=order",
         {"type": "order", "live_mode": False, "action": "order.processed", "data": {"id": provider}},
         headers={"x-request-id": request_id, "x-signature": f"ts={timestamp},v1={signature}",
                  "X-Correlation-Id": context.admin["X-Correlation-Id"]})


@then("a OS recebe a confirmação pela fila e permite a entrega")
def delivery(context):
    wait_state(context, "READY_FOR_DELIVERY")
    for event in ("OrcamentoAprovado", "PagamentoConfirmado"):
        predicate = f"service_order_id = '{context.order}' AND event_type = '{event}'"
        assert sql(context, "billing", f"SELECT count(*) FROM integration_event_outbox WHERE {predicate} AND status = 'PUBLISHED'") == "1"
        assert sql(context, "os", f"SELECT count(*) FROM inbox_event WHERE {predicate} AND outcome = 'APPLIED'") == "1"
    assert billing_get(context, context.settlement)["settled"]
    call(context, "os", "PATCH", context.order_path + "/entregar")
    wait_state(context, "COMPLETED")
    assert call(context, "os", "GET", context.order_path)["status"] == "ENTREGUE"


@when("o funcionário tenta iniciar a execução")
def failed_start(context):
    context.start_error = start_item(context, 409)


@then("o início é recusado por falta de estoque")
def no_stock(context):
    assert context.start_error["code"] == "INSUFFICIENT_STOCK"
    assert context.start_error["operationId"] == context.operation
    assert stock(context) == 0


@when("o funcionário confirma o cancelamento integral sem uso físico")
def cancel(context):
    call(context, "os", "PATCH", context.order_path + "/cancelar", {
        "scope": "FULL", "reason": "Cancelamento BDD", "unusedConfirmed": True,
        "physicallyRemoved": False, "returnConfirmed": False,
        "verifiedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "evidenceReference": "BDD Saga",
    }, 202)


@then("a Saga encerra o orçamento e compensa a OS sem movimentar estoque")
def compensated(context):
    wait_state(context, "COMPENSATED")
    budget = billing_get(context, f"/internal/budgets/service-orders/{context.order}/current")
    assert budget["status"] == "CANCELLED"
    receipt = call(context, "os", "GET", context.order_path + "/compensacao")
    completed = {operation["intent"] for operation in receipt["operations"] if operation["state"] == "SUCCEEDED"}
    assert {"RESOLVE_STOCK", "CANCEL_BUDGET"} <= completed
    assert stock(context) == 0
