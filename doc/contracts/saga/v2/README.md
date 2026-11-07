# Contratos da Saga — 2.0.0-review.1

**Pacote ativo para revisão da fase 1B. Não aprovado entre serviços.**
Base: `c73dfb2`; desenho arquitetural v2; novos eventos com `schemaVersion: 1`.
O [ADR 0003](../../../adr/0003-revisao-1b-contratos-arquitetura-v2.md) seleciona este
pacote, preserva [v1](../v1/README.md) e explica a mudança de semântica.

| Artefato | Conteúdo para revisão |
| --- | --- |
| [Catálogo](catalog.json) e [dispatch](message.schema.json) | Quatro eventos habilitados; EV-04 condicional e desabilitado |
| [Mensagens](messages.md) | Envelope, IDs, revisões, idempotência, ordenação e recuperação |
| [Fila e DLQ](queues.md) | Billing produtor, OS consumidor, IAM e reprocessamento |
| [Operações REST](rest-operations.md) | Donos, HTTP, timeout, CLOSED e REVERSED |
| [OS interna](os-internal.openapi.yaml) | CT-02A e CT-03 para Billing |
| [Inventory](inventory.openapi.yaml) | Disponibilidade, CT-05, CT-08 e CT-08B |
| [Billing](billing.openapi.yaml) | CT-07 e CT-09 |
| [API OS](os-api.openapi.yaml) | Início pendente, acompanhamento e entrega |
| [Compatibilidade](api-compatibility.md) | Rotas e DTOs legados, mudanças observáveis |
| [Vínculo de aprovação](approval-composition.md) | Snapshot imutável e exemplo de aprovação obsoleta |
| [Transições](state-transitions.md) | Estados comerciais, Saga e compensação C1–C6 |
| [Matriz de decisões](decisions.md) | Pendências para Julio, Anderson, Marcelo e Matheus |
| [Cenários](review-scenarios.md) | Resultado esperado e limite da evidência offline |

## Validar

Na raiz do repositório, o comando do CI permanece:

```sh
docker build -f scripts/contracts.Dockerfile -t os-contract-validation .
docker run --rm --network none -v "$PWD:/workspace" os-contract-validation
```

PowerShell:

```powershell
docker build -f scripts/contracts.Dockerfile -t os-contract-validation .
docker run --rm --network none --mount "type=bind,source=$((Get-Location).Path),target=/workspace" os-contract-validation
```

Alternativa sem Docker: preparar Python e instalar `scripts/requirements-contracts.lock`
em ambiente isolado; então executar `python scripts/validate_contracts.py`.
A instalação exige rede/cache; a validação bloqueia rede via audit hook e resolve
referências de schemas localmente. OpenAPI só aceita `$ref` local. Resultado em
`target/contract-validation.json`; o CI já publica esse arquivo.

São verificados todos os schemas, exemplos positivos/negativos e os quatro OpenAPI.
Fixtures HTTP são validadas contra o schema da operação/resposta real do OpenAPI,
inclusive erros. EV-04 tem schema/fixture próprios, mas o dispatch ativo deve rejeitá-lo.
Os testes do legado continuam rodando. Fixtures de cenários descrevem guardas de
negócio, sem simular nem comprovar execução distribuída.

## Mudanças frente ao pacote anterior

Comandos SQS e eventos de Inventory foram removidos apenas da referência ativa.
Não há reserva antecipada. Não há pagamento como pré-condição para execução.
O envelope usa `eventId`, `eventType`, `schemaVersion`, `occurredAt`, `correlationId`
e `payload`; Saga e identidades REST não são campos obrigatórios dos eventos.
`START_PENDING` é estado técnico, não novo status comercial HTTP. Entrega exige CT-07.

Aceite solicitado nesta fase: **pacote revisável**, com pendências rastreáveis.
Aceites técnicos/comerciais ainda devem indicar autor, data e versão ou commit na matriz.
