# Mapa REST e semântica proposta

Todos os contratos internos usam identidade de serviço, permissão e escopo
verificados pelo provedor. Não consultar banco alheio. `X-Correlation-Id` é UUID
neste contrato interno; a API legada aceita formato mais amplo (ver compatibilidade).
HTTP e códigos abaixo são propostas concretas para os responsáveis aceitarem.

| CT / provedor → consumidor | Método e rota | Resultado / recuperação |
| --- | --- | --- |
| CT-02A / OS → Billing | `GET /internal/service-orders/{serviceOrderId}` | 200 estado comercial; somente COMPLETED permite criar pagamento; 404 ausente; 503/504 desconhecido |
| CT-03 / OS → Billing | `GET /internal/service-orders/{serviceOrderId}/quotation-context` | 200 snapshot e compositionVersion; query opcional da versão histórica proposta; 404 OS/versão ausente; 409 contexto não elegível |
| Disponibilidade / Inventory → OS | `GET /internal/inventory/availability?inventoryItemIds=...` | 200 saldo observado, sem reserva; rota pendente Anderson; não substitui consumo nem CT-04 preços |
| CT-05 / Inventory → OS | `POST /internal/inventory/consumptions` | 200 CONSUMED; 409 REJECTED/INSUFFICIENT_STOCK com movimentos vazios; 409 ATTEMPT_CLOSED/ATTEMPT_REVERSED/IDEMPOTENCY_CONFLICT |
| CT-08 / Inventory → OS | `GET /internal/inventory/operations/{operationId}` | 200 CONSUMED/REJECTED/REVERSED/CLOSED; 202 PENDING; 404 UNKNOWN; 503/504 desconhecido |
| CT-08B / Inventory → OS | `POST /internal/inventory/consumptions/{operationId}/reversal` | 200 REVERSED ou CLOSED; 409 evidência insuficiente, material utilizado, operação já rejeitada ou conflito de chave |
| CT-07 / Billing → OS | `GET /internal/payments/service-orders/{serviceOrderId}/settlement` | 200 settled true/false; 404/503/504 bloqueiam entrega; PAID de um evento não substitui consulta |
| CT-09 / Billing → OS | `POST /internal/budgets/{budgetId}/cancellation` | 200 CANCELLED; 409 BUDGET_VERSION_CONFLICT/BUDGET_NOT_ELIGIBLE/FINANCIAL_EFFECT_PENDING/IDEMPOTENCY_CONFLICT; após timeout repetir POST |

Todas as rotas declaram 400/401/403 conforme aplicável. `503` é indisponibilidade;
`504` é timeout relatado por dependência/gateway. Timeout do cliente pode não trazer
resposta HTTP alguma e tem o mesmo tratamento de resultado desconhecido.
`404 UNKNOWN` de CT-08 não autoriza assumir ausência de baixa. Não confundir recusa
de negócio persistida com timeout, operação em andamento ou ausência transitória.

## Idempotência e fechamento

CT-05/09 exigem `Idempotency-Key` igual ao `operationId` do body; CT-08B exige chave
igual ao `compensationId`. Divergência header/body é `400 INVALID_REQUEST`.
Escopo da chave inclui serviço chamador, operação e recurso. Payload canônico inclui
recurso, composição/quantidades e autor; exclui correlação e metadados de transporte.
Reordenar materiais idênticos não muda operação; normalização precisa de acordo
Anderson/Matheus. Hash diferente conflita antes de qualquer efeito adicional.

CT-05 agrega materiais repetidos e aplica todos ou nenhum. REJECTED por falta de
saldo é durável; retry da mesma tentativa continua recusado mesmo após reposição.
Só nova ação explícita, após resultado conhecido, cria novo operationId.

CT-05 e CT-08B serializam pela mesma operationId, mesmo se não houver linha:

1. Fechamento ganha corrida: grava `CLOSED`, `compensationId`, zero movimentos.
   CT-05 tardio recebe `409 ATTEMPT_CLOSED`; nunca cria baixa.
2. Consumo ganha corrida: grava `CONSUMED`. CT-08B valida não uso/devolução e grava
   `REVERSED` com movimentos inversos uma vez; sem comprovação, 409 e intervenção.
3. Consulta encontra `REJECTED`: C2 está resolvido sem devolução; dispensar C3 com
   motivo. Se CT-08B disputou com recusa, `409 OPERATION_ALREADY_REJECTED` exige
   confirmar pelo CT-08, não fabricar REVERSED.
4. Resposta de reversão perdida: repetir compensationId ou CT-08; ambos resultados
   duráveis comprovam etapa. CLOSED/REVERSED nunca são reabertos. Retry CT-05 após
   reversão recebe ATTEMPT_REVERSED; não reproduzir CONSUMED como autorização atual.

Sem confirmação durável, manter compensação pendente. A evidência proposta contém
autor, instante e referência de conferência; `unusedConfirmed=true` significa material
não utilizado ou devolução válida conferida, não simples solicitação de cancelamento.
Formato, autorização e tratamento físico permanecem pendentes Anderson/Matheus.

## Quitação e cancelamento

Billing considera todos os pagamentos válidos, incluindo estorno excedente.
CT-07 `settled=true` inclui referência a pagamento válido; esse campo não enumera
todos os pagamentos. `settled=false` confirmado bloqueia entrega e pode retirar
prontidão. Erro de consulta mantém verificação pendente, sem inventar inadimplência.

CT-09 usa operationId próprio e revisão explícita. CANCELLED significa cancelamento
comercial, não estorno concluído. Billing recusa revisão divergente, inelegibilidade
e efeitos financeiros pendentes. Estorno exige protocolo do Billing e, se adotado,
EV-04. Não concluir C6 enquanto efeito financeiro aplicável estiver indefinido.
