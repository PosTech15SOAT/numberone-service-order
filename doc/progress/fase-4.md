# Fase 4 — caminho principal da Saga

Implementação local de 08/11/2026 sobre a mensageria revisada, no checkout
`tmp/fase-2/numberone-service-order`, branch `feature/fase-2-dominio-persistencia`,
HEAD `13817e5466077bc2c6b0b9a0c61254ec6727e108`. O índice preexistente foi preservado
(hash do diff preparado: `bded155ce36c25aadb1d9b3cd099b05aee0918f9`). Sem commit,
push, merge, deploy ou acesso a banco compartilhado. Entrega para revisão de Matheus.

Pacote usado: `2.0.0-os-baseline.1`, com cinco eventos ativos e CT-02A/03/05/07/10.
Mantidos `readyForOsImplementation=true` e `approvedContract=false`; aprovação
conjunta de Billing/Inventory/plataforma não é inferida dos testes.

## Comportamento implementado

- `OrcamentoEnviado`, `OrcamentoAprovado` e `OrcamentoRejeitado` validam identidade,
  revisão vigente e composição. Aprovação positiva em BRL congela o snapshot e
  leva OS a `APPROVED` / Saga a `READY_FOR_EXECUTION`. Rejeição leva a
  `REJECTED` / `WAITING_REVISION`. A revisão cresce conforme os fatos do Billing;
  uma decisão mais nova recuperada por CT-10 pode ultrapassar a rejeição antiga
  ainda atrasada na fila. A serialização comercial das revisões pertence ao Billing.
- Decisão sem vínculo chama CT-10 **fora da transação**. Nova transação revalida
  versão da OS, revisão, composição e cancelamento. 404/503/timeout são inconclusivos:
  inbox reverte, SQS retenta segundo a política existente (cinco recebimentos/DLQ).
  Evento antigo é `OBSOLETE`; envio atrasado não regride decisão. Decisões
  conflitantes não recebem ACK. Deduplicação continua anterior às guardas.
- `GET /internal/service-orders/{id}` fornece CT-02A com `cancellationInProgress`.
  `GET /internal/service-orders/{id}/quotation-context` fornece CT-03, incluindo
  consulta exata por `compositionVersion`; versão inexistente retorna 404.
  A leitura bloqueia a OS para fornecer composição/estado consistentes.
- A rota global `/iniciar` apenas valida e devolve prontidão. A rota por item
  persiste `saga_operation`: `operationId`, intenção `START_ITEM`, item, número da
  tentativa, composição/revisão e pedido imutável com responsável autenticado.
  O commit ocorre antes de CT-05; não há transação SQL aberta durante HTTP.
- A primeira tentativa com materiais deixa Saga `START_PENDING`. Item conserva
  status comercial e mostra `executionState=START_PENDING`, `startOperationId` e
  data de início nula. Resposta inconclusiva retorna **202**, `Location` para o
  acompanhamento autorizado e operação `AWAITING_RESULT`. Não autoriza uso físico.
- Repetição ativa, inclusive com outra chave, reutiliza a tentativa e o pedido
  originais. CT-05 pode ser repetido com a mesma chave: Inventory precisa manter
  idempotência. Recusa confirmada retorna **409 INSUFFICIENT_STOCK**, item
  `WAITING_FOR_PARTS_AND_SUPPLIES`. Repetição sem chave ou com chave antiga mantém
  a recusa; só uma nova chave explícita permite nova tentativa após reposição.
- Confirmação `CONSUMED` grava movimentos e início após revalidar aprovação,
  composição, revisão, item/tentativa ativa e bloqueio da Saga. Versões técnicas
  são por composição/tentativa: a execução de outro item não invalida o pedido.
  A primeira confirmação leva a `IN_EXECUTION`; operações seguintes não fazem
  a Saga regredir. Item sem material confirma localmente, sem CT-05 vazio.
- Resultado remoto recebido após mudança de guardas fica em `saga_operation.result`,
  com motivo `LOCAL_GUARDS_CHANGED`, sem iniciar item. Compensação é fase 5.
- Conclusão de itens e OS exige início confirmado, materiais confirmados e ausência
  de pendências. Conclusão técnica grava OS `COMPLETED` e Saga `WAITING_PAYMENT`
  no mesmo commit, sem pagamento prévio. O funcionário inicia pagamento **no Billing**;
  a OS não cria cobrança. CT-02A informa o estado técnico concluído ao Billing.
- EV-03/04 atualizam `service_order_payment` por `paymentId/paymentVersion`, na
  transação da inbox. REFUNDED mais novo vence PAID atrasado. Eventos podem anteceder
  contexto comercial e podem chegar depois da entrega. `payments` e
  `pagamentoConfirmado` são projeções; não comprovam quitação.
- Evento financeiro marca `settlement_pending` duravelmente. Depois do commit,
  CT-07 atualiza a indicação `READY_FOR_DELIVERY` ou `WAITING_PAYMENT`, revalidando
  versão da Saga. Falha deixa verificação pendente; reentrega ou entrega explícita
  permite nova consulta. Recuperação periódica automática fica para fase 5.
- Entrega consulta CT-07 mesmo sem EV-03 recebido. Exige resposta atual e
  `settled=true`, depois revalida versões OS/Saga, estado, cancelamento e conclusão
  técnica. OS `DELIVERED`, data de entrega e Saga `COMPLETED` têm commit conjunto.
  Falso retorna 409; indisponível/timeout/inválido retorna 503. Repetição de entrega
  devolve o estado entregue sem novo efeito. Estorno posterior preserva a entrega.

## Configuração e fronteiras

Migration incremental **V4**; V1/V2/V3 preservadas. V4 acrescenta operações,
projeção financeira, estados/controle de quitação, vínculo da tentativa ao item e
valor positivo BRL para novos snapshots. Remove a restrição antiga que impedia
projeção financeira antes do snapshot comercial. Backfill conservador V3 permanece:
ordens legadas em `MANUAL_INTERVENTION` exigem reconciliação explícita.

Variáveis: `BILLING_BASE_URL`, `INVENTORY_BASE_URL`, `OS_TO_BILLING_TOKEN`,
`OS_TO_INVENTORY_TOKEN`, `BILLING_TO_OS_TOKEN`. Sem configuração, dependências
retornam indisponibilidade e rotas internas negam acesso. Token Billing→OS só
autoriza os dois GETs de CT-02A/03; não autoriza Auth lookup nem rotas administrativas.
Usar rede privada/TLS e segredos de ambiente. Não há credenciais reais nos testes.

CT-05 exige UUID do funcionário: obtido do subject autenticado do Gateway, nunca
do body. O perfil local usa UUID sintético configurável por `LOCAL_EMPLOYEE_ID`.
Correlação UUID pública é propagada; correlação textual legada recebe UUID interno.
Timeout HTTP: conexão 3s, resposta 5s. CT-07 rejeita `verifiedAt` anterior a 60s ou
mais de 30s no futuro; sincronização dos relógios deve ser confirmada na integração.

SQS continua desabilitado por padrão. Todos os handlers ativos estão disponíveis;
configuração agora aceita modo `dedicated` ou `shared`, sem habilitar fila real.
O teste da fase 3 continua usando mock do SDK SQS. Não houve consumo AWS nesta fase.

## Demonstração reproduzível

No diretório do checkout, com JDK compatível, Docker Desktop e dependências locais:

```powershell
.\mvnw.cmd -B -o clean verify
# Python com scripts/requirements-contracts.lock instalado:
python scripts/validate_contracts.py
python scripts/validate_phase4_trace.py
```

O teste `SagaMainFlowIT.mainFlowTwoMaterialItemsAndOneWithoutMaterialsFinishesBeforeEmployeePayment`
cria cliente/veículo/OS, consulta CT-03, emite os eventos contratuais de envio e
aprovação, valida prontidão, inicia dois itens com materiais e um sem materiais,
conclui os três e a OS, consulta CT-02A, recusa entrega sem quitação, simula pagamento
no Billing, publica EV-03 e entrega mediante **nova** consulta CT-07.

Os demais testes cobrem resposta perdida com baixa única, UNKNOWN/PENDING, falta
de saldo e nova chave, duplicação concorrente, aprovação obsoleta por composição
e revisão, decisão anterior ao envio, CT-10 indisponível e versão local alterada,
pagamento anterior ao contexto, estorno fora de ordem, evento financeiro atrasado,
entrega com CT-07 falso/indisponível/timeout, cancelamento durante CT-05, mudança
de versão/cancelamento durante CT-07, rollback no commit da intenção e da entrega,
autorização interna e consulta histórica exata.

Evidência: PostgreSQL **17.9 real via Testcontainers**, HTTP/Tomcat real na OS,
clientes HTTP reais para **simuladores** Billing e Inventory em loopback. Os
simuladores partem das fixtures do pacote ativo e verificam token, correlação,
idempotência e visibilidade SQL da intenção antes de responder. Eventos entram
no handler real com JSON Schema; o transporte SQS é coberto separadamente pelo mock.

Relatórios regeneráveis:

- `phase4-verify.log`, `target/surefire-reports`, `target/failsafe-reports`;
- `target/site/jacoco/index.html` e `jacoco.xml`;
- `phase4-contracts.log` — schemas/fixtures originais, 21 verificações;
- `target/phase4-contract-trace.json` — payloads HTTP capturados;
- `phase4-trace-validation.log` — validação offline dos payloads contra OpenAPI.

Resultado final: **169 testes** (109 unitários + 60 de integração), zero falhas,
erros ou skips; **94,46%** de linhas (1.772/1.876), gate de 80% aprovado.
O pacote passou nas 21 verificações offline; **41 payloads HTTP capturados**
passaram contra 11 schemas dos contratos ativos. A suíte foi executada com
Java 26.0.1, compilação `--release 25`, PostgreSQL 17.9 e Docker/Testcontainers.
[Resumo persistido das evidências](evidence/fase-4.json).

## Fora deste recorte

Compensação integral C1–C6, CT-08/08B/09/11 e workers de recuperação são fase 5.
Histórico/outbox DynamoDB é fase 6. Billing/Inventory reais, Mercado Pago, fila AWS,
deploy e validação conjunta da identidade/rede são fase 7. A janela entre CT-07
e commit local não é uma transação distribuída com estorno: permanece a coordenação
operacional de estornos definida no contrato. Nenhum teste aqui comprova esses
participantes externos ou substitui o aceite de Matheus.
