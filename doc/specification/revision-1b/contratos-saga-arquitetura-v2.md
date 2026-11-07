# Contratos da Saga · NumberOne Fase 4

Definição técnica validada por Matheus em 04/11/2026 para orientar a implementação da OS. Os alinhamentos com os outros serviços estão na seção 7. A referência é `NumberOne_plano_arquitetura_distribuida_v2.html`, seções Saga, Estados e transições, Nomenclatura e casos UC-04 a UC-10. Os IDs `EV-xx`, `CT-xx` e `Dxx` remetem ao HTML. Esta revisão incorpora o fechamento `CLOSED` no Inventory e define o DynamoDB para o histórico de revisões veiculares. A janela de compensação automática adotada como base é o cancelamento integral antes do primeiro início confirmado, com verificação de não uso dos materiais.

## 1. Mensagens SQS por microsserviço

Fila principal `numberone-billing-events` e DLQ `numberone-billing-events-dlq`, nomes propostos em NM-015 e NM-016. Um produtor e um consumidor de negócio. O tipo da fila e os parâmetros de retry ficam com Marcelo. O contrato trata reentregas e mensagens fora de ordem. [Entrega pelo menos uma vez em filas Standard](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/standard-queues-at-least-once-delivery.html)

| Serviço | Publica no SQS | Consome do SQS | Controle local |
| --- | --- | --- | --- |
| Billing | EV-01, EV-02, EV-02B, EV-03 e EV-04 conforme adoção abaixo. | Nenhuma mensagem desta Saga. Webhook do Mercado Pago chega por HTTPS. | Outbox de eventos e deduplicação/reconciliação de webhooks. |
| OS | Nenhuma mensagem desta Saga. | Eventos de orçamento e pagamento da fila Billing → OS. | Inbox, registro das operações REST e outbox local para projetar o histórico no DynamoDB. |
| Inventory | Nenhuma mensagem desta Saga. | Nenhuma mensagem desta Saga. | Registro idempotente das operações REST e de seus efeitos sobre o saldo. |

| ID | `eventType` | Quando Billing grava o evento | O que a OS faz | Situação no HTML |
| --- | --- | --- | --- | --- |
| EV-01 | `OrcamentoEnviado` | Transição comercial para `SENT` quando a revisão estiver disponível para aprovação, conforme alinhamento com Billing. | Vincula orçamento/revisão elegível e leva a Saga a `WAITING_APPROVAL`. | Previsto na v1. Julio valida o ponto exato de publicação em D09/P13. |
| EV-02 | `OrcamentoAprovado` | Aprovação integral confirmada e snapshot comercial persistido. | Registra aprovação vigente, OS `APPROVED` e Saga `READY_FOR_EXECUTION`. | Conceito no fluxo. Payload a validar. |
| EV-02B | `OrcamentoRejeitado` | Rejeição integral confirmada. | Registra rejeição vigente, OS `REJECTED` e Saga `WAITING_REVISION`. | Conceito no fluxo. Payload a validar. |
| EV-03 | `PagamentoConfirmado` | Pagamento passa para `PAID` após confirmação validada pelo Billing. | Atualiza projeção por pagamento. Pode solicitar atualização da quitação. | Conceito no fluxo. Payload a validar. |
| EV-04 | `PagamentoAtualizado` | Proposta inicial: estorno integral efetivamente confirmado, `REFUNDED`. | Atualiza projeção e reavalia quitação antes de exibir prontidão para entrega. | Entra na v1 se o Billing incluir estorno integral. Estorno parcial e outros estados exigem contrato próprio ou extensão acordada. |

Diagnóstico, execução e conclusão técnica são ações locais da OS. Consumo, reversão de estoque e cancelamento de orçamento usam CT-05/08/08B/09 por REST. O resultado desses contratos é a confirmação da etapa de compensação na OS. Estorno financeiro continua sob responsabilidade do Billing, e seu reflexo assíncrono seria EV-04 se adotado.

## 2. Envelope v1 e exemplos

O corpo de cada mensagem SQS é **um objeto JSON**, codificado em UTF-8. Os seis campos abaixo são obrigatórios. `payload` é específico por tipo.

| Campo | Formato e regra |
| --- | --- |
| `eventId` | UUID exclusivo de um fato emitido. Retry, reentrega e redrive do mesmo fato preservam ID e conteúdo. |
| `eventType` | Nome exato da matriz. |
| `schemaVersion` | Inteiro `1`. Identifica a estrutura do evento. |
| `occurredAt` | Data/hora RFC 3339 em UTC. Serve para auditoria. |
| `correlationId` | UUID de correlação propagado na origem e usado nos logs. |
| `payload` | Objeto com referências e dados de negócio. `serviceOrderId` é a chave de associação à Saga na OS. |

Os IDs de entidades são UUIDs. Proposta para `budgetVersion`: revisão comercial inteira positiva, crescente **por OS** e estável entre envio e decisão da mesma revisão. `paymentVersion`: versão inteira positiva, crescente **por paymentId** a cada alteração publicada. `sagaId` permanece interno à OS nesta versão. Valores monetários usam string decimal com duas casas, com `currency` no evento de aprovação como complemento proposto ao HTML. Os eventos carregam referências e fatos, sem CPF, contato do cliente, QR Code, segredos ou dados de cartão.

### EV-01 · OrcamentoEnviado

```json
{
  "eventId": "66000000-0000-4000-8000-000000000001",
  "eventType": "OrcamentoEnviado",
  "schemaVersion": 1,
  "occurredAt": "2026-11-04T13:00:00Z",
  "correlationId": "77000000-0000-4000-8000-000000000001",
  "payload": {
    "serviceOrderId": "54e94616-70ad-4ce7-b6f7-41c6747d802e",
    "budgetId": "b1fb7c09-d680-4227-81c5-6b38dbaa88b9",
    "budgetVersion": 1
  }
}
```

### EV-02 · OrcamentoAprovado

```json
{
  "eventId": "66000000-0000-4000-8000-000000000002",
  "eventType": "OrcamentoAprovado",
  "schemaVersion": 1,
  "occurredAt": "2026-11-04T13:15:00Z",
  "correlationId": "77000000-0000-4000-8000-000000000002",
  "payload": {
    "serviceOrderId": "54e94616-70ad-4ce7-b6f7-41c6747d802e",
    "budgetId": "b1fb7c09-d680-4227-81c5-6b38dbaa88b9",
    "budgetVersion": 1,
    "approvedAmount": "350.00",
    "currency": "BRL"
  }
}
```

### EV-02B · OrcamentoRejeitado

Alternativa ao EV-02 para a mesma revisão.

```json
{
  "eventId": "66000000-0000-4000-8000-000000000003",
  "eventType": "OrcamentoRejeitado",
  "schemaVersion": 1,
  "occurredAt": "2026-11-04T13:15:00Z",
  "correlationId": "77000000-0000-4000-8000-000000000003",
  "payload": {
    "serviceOrderId": "54e94616-70ad-4ce7-b6f7-41c6747d802e",
    "budgetId": "b1fb7c09-d680-4227-81c5-6b38dbaa88b9",
    "budgetVersion": 1
  }
}
```

### EV-03 · PagamentoConfirmado

```json
{
  "eventId": "66000000-0000-4000-8000-000000000004",
  "eventType": "PagamentoConfirmado",
  "schemaVersion": 1,
  "occurredAt": "2026-11-04T18:10:00Z",
  "correlationId": "77000000-0000-4000-8000-000000000004",
  "payload": {
    "serviceOrderId": "54e94616-70ad-4ce7-b6f7-41c6747d802e",
    "paymentId": "33333333-3333-4333-8333-333333333333",
    "paymentStatus": "PAID",
    "paymentVersion": 1
  }
}
```

### EV-04 · PagamentoAtualizado

```json
{
  "eventId": "66000000-0000-4000-8000-000000000005",
  "eventType": "PagamentoAtualizado",
  "schemaVersion": 1,
  "occurredAt": "2026-11-04T18:20:00Z",
  "correlationId": "77000000-0000-4000-8000-000000000005",
  "payload": {
    "serviceOrderId": "54e94616-70ad-4ce7-b6f7-41c6747d802e",
    "paymentId": "33333333-3333-4333-8333-333333333333",
    "paymentStatus": "REFUNDED",
    "paymentVersion": 2
  }
}
```

### Ordem e validade de negócio

- Orçamento: use revisão, identidade e transição. `SENT` atrasado da mesma revisão após decisão é ignorado. Uma revisão inferior é obsoleta. Mesma revisão com outro `budgetId` ou duas decisões divergentes exige reconciliação. Billing deve serializar as decisões e emitir eventos apenas para transições comerciais válidas.
- Se EV-02/02B chegar antes do EV-01 e faltar contexto para validar a revisão, a OS mantém a mensagem pendente e tenta novamente. O cenário deve constar nos testes. Se EV-01 não for adotado, a equipe precisa fechar outra fonte para identificar o orçamento vigente e permitir a transição.
- Pagamento: aplicar a versão mais nova de cada `paymentId`, inclusive se EV-04 chegar antes de EV-03. Um `PAID` anterior não desfaz `REFUNDED`. O estado financeiro da OS é apurado pelo Billing via CT-07, considerando todos os pagamentos válidos.
- Guardas da OS valem após a deduplicação: nenhum evento de orçamento reabre OS/Saga em cancelamento ou encerrada. Eventos financeiros posteriores à entrega podem atualizar a projeção e gerar revisão operacional conforme política do Billing. A entrega registrada é preservada para auditoria.

## 3. Inbox, outbox e recuperação

**Billing · outbox.** Na mesma transação PostgreSQL, persistir a transição do orçamento/pagamento e inserir o evento em `outbox_event`. Guardar `event_id` único, tipo, versão do schema, ID/revisão de negócio, envelope completo, status, contagem de tentativas, próxima tentativa, prazo de posse, último erro e data de publicação. Estados propostos: `PENDING`, `PUBLISHED` e `NEEDS_REVIEW` quando o retry operacional se esgotar.

Um worker adquire um lote com posse temporária em transação curta, envia ao SQS fora da transação e marca `PUBLISHED` após aceite, se ainda tiver a posse. Se enviar e cair antes de marcar, outro ciclo reenvia o mesmo envelope com o mesmo `eventId`. Falha de publicação fica na outbox com alerta e recuperação pelo Billing. [Transactional outbox na AWS](https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html)

**OS · inbox.** Validar envelope e schema, abrir transação e deduplicar por `event_id` único. Serializar as transições da mesma OS por lock ou versão com retry. Salvar inbox, projeção, transição e qualquer tarefa local decorrente no mesmo commit. Guardar `event_id`, tipo, schema, `service_order_id`, `correlation_id`, hash do envelope, desfecho `APPLIED` ou `IGNORED_STALE` e data. Mesmo ID com outro conteúdo é erro de contrato. Duplicata idêntica usa o desfecho já registrado.

O consumidor confirma o SQS após o commit. Se a aplicação cair entre commit e confirmação, a reentrega encontra o evento registrado e confirma sem reaplicar. Se faltar contexto, o commit do processamento é revertido e a mensagem volta para retry. Schema inválido, conflito de dados ou falha persistente seguem a política de DLQ com diagnóstico e redrive preservando o ID. Ajustar visibilidade, limite de recebimentos, alarmes, retenção da DLQ e retenção da inbox ao período de reprocessamento acordado. [DLQ e redrive no SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html)

**OS · operações REST.** Registrar a intenção e o identificador estável antes do HTTP. Um worker retoma operações pendentes após reinício. A resposta é aplicada em outra transação, revalidando versão e cancelamento. Tratar timeout como `AWAITING_RESULT`. Na integração REST, quem recebe persiste chave, hash do pedido, resultado e efeito na mesma transação local. Repetição com o mesmo conteúdo devolve o resultado registrado e uso da mesma chave com conteúdo diferente retorna conflito. A chamada HTTP fica fora dos locks/transações locais.

## 4. Máquina de estados da Saga na OS

Uma Saga por ordem, criada com a abertura. `IN_DIAGNOSIS` é a etapa técnica inicial, inclusive enquanto a OS comercial ainda está `RECEIVED`. O diagnóstico e o andamento do item continuam sob as regras da OS.

| Estado técnico | Entrada, ação e próxima transição |
| --- | --- |
| `IN_DIAGNOSIS` | OS aberta, aguarda diagnóstico e envio do orçamento. EV-01 válido leva a `WAITING_APPROVAL`. |
| `WAITING_APPROVAL` | Aguarda decisão da revisão vigente. EV-02 leva a `READY_FOR_EXECUTION`. EV-02B leva a `WAITING_REVISION`. |
| `WAITING_REVISION` | Aguarda nova revisão comercial elegível ou cancelamento conforme política. EV-01 da revisão seguinte leva a `WAITING_APPROVAL`. |
| `READY_FOR_EXECUTION` | Orçamento aprovado. Gravar a primeira tentativa de consumo e entrar em `START_PENDING`. Se o item não requer insumos, a OS pode iniciar localmente conforme as guardas vigentes. |
| `START_PENDING` | Primeiro início com resultado de consumo pendente. `CONSUMED` com guardas válidas leva a `IN_EXECUTION`. Falta confirmada de saldo devolve a Saga a `READY_FOR_EXECUTION` e o item aguarda peças. Timeout mantém a tentativa pendente. |
| `IN_EXECUTION` | Execução iniciada. Demais tentativas ficam registradas por item, mantendo a etapa global. Conclusão técnica válida leva a `WAITING_PAYMENT`. |
| `WAITING_PAYMENT` | OS `COMPLETED`, funcionário inicia cobrança no Billing. Proposta técnica: confirmação da quitação via CT-07 leva a `READY_FOR_DELIVERY`. Recusa mantém a etapa. |
| `READY_FOR_DELIVERY` | Última quitação consultada positiva. Revalidar CT-07 no ato da entrega e revalidar as guardas locais antes de gravar `DELIVERED` e Saga `COMPLETED`. Quitação desfeita antes da entrega retorna a `WAITING_PAYMENT`. |
| `COMPLETED` | Entrega registrada. Terminal no atendimento. |
| `COMPENSATING` | Cancelamento elegível aceito. Impede novos inícios e acompanha C1–C6. Só avança a `COMPENSATED` com os efeitos resolvidos. |
| `COMPENSATED` | Cancelamento local registrado após confirmação das etapas aplicáveis. Terminal no atendimento. |
| `MANUAL_INTERVENTION` | Tratamento humano para efeito físico, financeiro ou divergência sem resolução automática. Guardar etapa de retorno e motivo para retomar depois da decisão auditada. |

Para a prontidão de entrega, proposta de implementação: EV-03/EV-04 gravam a projeção e agendam uma atualização de quitação. A OS chama CT-07 fora da transação da inbox e aplica a resposta se a versão da solicitação ainda for a vigente. `settled=false` confirmado pode levar a `WAITING_PAYMENT`. Timeout mantém a verificação pendente. Se a mensagem de pagamento atrasar, a ação de entrega ainda pode verificar CT-07 diretamente com a OS `COMPLETED`. CT-07 positivo e guardas locais válidas permitem avançar e finalizar no mesmo commit. O HTML já determina a checagem oficial no ato da entrega. Atualização prévia de prontidão é detalhe técnico para validar.

## 5. Compensação proposta · C1–C6

O cenário de referência é o cancelamento elegível antes do uso físico dos materiais. O protocolo abaixo detalha a proposta existente. A elegibilidade comercial e operacional segue pendente em D05, D10 e D11.

| Etapa | Serviço e contrato | Resultado persistido |
| --- | --- | --- |
| C1 · Bloquear início | OS aceita o cancelamento elegível e serializa a decisão com o início do item. | Saga `COMPENSATING`, escopo, motivo e autor. Resposta tardia de consumo pode registrar o fato, porém o início fica bloqueado. |
| C2 · Reconciliar | OS consulta CT-08 `GET /internal/inventory/operations/{operationId}` para cada consumo afetado. | `CONSUMED` exige resolver o efeito. `REVERSED`, `REJECTED` ou `CLOSED` comprovam que não há saldo a devolver. Com resultado desconhecido, a etapa fica pendente até confirmação do Inventory. |
| C3 · Resolver estoque | OS chama CT-08B `POST /internal/inventory/consumptions/{operationId}/reversal` com `compensationId` estável e evidência de não uso/devolução, quando aplicável. | Inventory confirma `REVERSED` após movimento inverso ou `CLOSED` após fechar a tentativa sem baixa. Uma recusa terminal consultada em C2 resolve a etapa sem reversão. |
| C4 · Solicitar cancelamento comercial | Com estoque resolvido, OS chama CT-09 `POST /internal/budgets/{budgetId}/cancellation`, vinculando a revisão. | Operação de cancelamento persistida na OS. Se nunca houve orçamento aplicável, registrar etapa dispensada com a razão verificada. |
| C5 · Confirmar cancelamento comercial | Billing valida revisão vigente, elegibilidade e efeitos financeiros. | `CANCELLED` confirmado e auditado. Conflito ou efeito financeiro impede marcar a etapa como concluída. |
| C6 · Encerrar | OS valida as etapas aplicáveis e registra o encerramento local autorizado. | Item/OS `CANCELLED` conforme o escopo e as guardas acordadas. Saga `COMPENSATED` no cancelamento integral da ordem. Política de cancelamento parcial segue em D10. |

### Contratos REST e retornos

| Contrato | Identidade e dados | Retorno esperado e recuperação |
| --- | --- | --- |
| CT-05 · Consumo | `operationId` como `Idempotency-Key`, `serviceOrderId`, `serviceOrderItemId`, `responsibleUserId`, `items[]` com `inventoryItemId` e `quantity`. | `200 CONSUMED` com movimentos. `409 INSUFFICIENT_STOCK` é recusa confirmada sem baixa parcial. Durante início ativo, retry usa a mesma chave. Após falta confirmada e reposição, nova ação usa nova tentativa. |
| CT-08 · Consulta de consumo | `operationId` original. | Proposta: `200` com resultado durável `CONSUMED`, `REJECTED`, `REVERSED` ou `CLOSED`. Operação ausente/andamento ou falha de consulta é tratada na OS como resultado desconhecido até confirmação. Alinhar o HTTP correspondente com Anderson. |
| CT-08B · Reversão ou fechamento | `operationId` original na URL, `compensationId` como `Idempotency-Key`, `reason` e `unusedConfirmed` como no HTML. OS retém autor, momento e evidência da conferência. | `200 REVERSED` após movimento inverso ou `200 CLOSED` após fechamento atômico de tentativa sem baixa. Ambos retornam `operationId` e `compensationId`. Retry usa a mesma chave. Se houver devolução física, acordar como o pedido referencia a evidência. |
| CT-09 · Cancelamento comercial | `budgetId` na URL, `operationId` como `Idempotency-Key`, `budgetVersion` e `reason`. | `200 CANCELLED` com `budgetId` e `operationId`. Proposta: `409` com código estável para revisão divergente, inelegibilidade ou efeito financeiro. Após timeout, repetir o mesmo POST para recuperar o resultado. |

**Fechamento `CLOSED`.** Inventory grava o fechamento mesmo quando o consumo ainda não chegou. Durante o cancelamento, a OS bloqueia novos inícios e retentativas de CT-05. Uma consulta ausente pode indicar requisição em trânsito, então a OS envia CT-08B para resolver a tentativa e espera a confirmação do Inventory. CT-05 e CT-08B serializam pela mesma identidade de operação no PostgreSQL, inclusive quando a linha ainda não existe. Se CT-08B chegar primeiro, grava `CLOSED` e um CT-05 atrasado recebe a recusa proposta `409 ATTEMPT_CLOSED`. Se CT-05 confirmar a baixa primeiro, CT-08B encontra `CONSUMED` e só devolve saldo com a validação de não uso ou devolução. Se a resposta de CT-08B se perder, a OS repete o pedido com o mesmo `compensationId` ou consulta CT-08 para reconciliar o efeito. Sem confirmação durável, a Saga mantém a compensação pendente.

**Recusa, falha técnica e efeito físico.** Falta de saldo confirmada espera nova ação após reposição. Timeout mantém a operação para recuperação. Peça efetivamente utilizada exige tratamento explícito, e qualquer restituição de saldo depende da devolução válida. Pagamento recusado após reparo preserva OS `COMPLETED` e mantém entrega bloqueada. Em duplicidade confirmada, Billing identifica a cobrança excedente, persiste a operação de estorno, reconcilia o provedor e registra `REFUNDED` quando confirmado. EV-04 pode informar esse fato à OS. A quitação da cobrança válida é reavaliada pelo CT-07.

## 6. Persistência por microsserviço

Usar a topologia do HTML: uma instância RDS PostgreSQL com três databases lógicos isolados e migrations Flyway no repositório de cada serviço. Os nomes e tabelas abaixo são referências de implementação.

| Dono e database | Registro | Campos, estados e restrições essenciais |
| --- | --- | --- |
| OS · `numberone_os` | Ordem | Catálogo comercial do HTML: `RECEIVED`, `IN_DIAGNOSIS`, `WAITING_APPROVAL`, `APPROVED`, `REJECTED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `DELIVERED`. As guardas de cancelamento seguem em revisão. |
| OS · `numberone_os` | Itens e projeções | Item: `PENDING`, `WAITING_FOR_PARTS_AND_SUPPLIES`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` no catálogo do HTML. Persistir composição técnica, quantidades previstas, referência/revisão e valor aprovado/moeda, pagamento projetado por `paymentId` e versão e informação da última consulta CT-07. |
| OS · `numberone_os` | `service_order_saga` | `saga_id`, `service_order_id` único, `state`, versão de concorrência, referências vigentes, etapa de retorno, motivo/autor de cancelamento ou intervenção, datas. Atualizar com a transição local da ordem quando ambas mudam. |
| OS · `numberone_os` | `saga_operation` | `operation_id` único, Saga, item e tentativa quando aplicável, tipo de operação, ID original ao compensar, request imutável, resultado remoto, motivos, tentativas, próxima execução, posse e datas. Estados: `PENDING`, `AWAITING_RESULT`, `SUCCEEDED`, `REJECTED`, `SKIPPED`, `NEEDS_REVIEW`. Proteger contra duas tentativas ativas de início do mesmo item. |
| OS · `numberone_os` | `inbox_event` | `event_id` único, hash e desfecho conforme seção de inbox. Resultado pendente de aplicação segue política de retry. |
| OS · `numberone_os` | Outbox do histórico | Snapshot imutável da revisão veicular, chave de destino, status, tentativas e próxima execução. Inserção no mesmo commit da conclusão técnica. |
| OS · DynamoDB | `vehicle_maintenance_history` | Histórico consultável por veículo. Uma entrada por revisão concluída, com vínculo à OS, data de conclusão, serviços executados e materiais efetivamente utilizados, quando disponíveis no snapshot. |
| Billing · `numberone_billing` | Orçamento e revisão | `budgetId`, OS, revisão, snapshot de itens/preços, valor e moeda. `DRAFT`, `SENT`, `APPROVED`, `REJECTED` e `CANCELLED` quando elegível. Manter decisões anteriores na auditoria. |
| Billing · `numberone_billing` | Pagamento, operação financeira e webhook | `paymentId`, OS, orçamento, valor, moeda, método, referência do provedor, versão e dados de confirmação/estorno. Estados do HTML: `PENDING`, `PAID`, `FAILED`, `EXPIRED`, `REFUNDED`. Operação externa tem chave estável e resultado pendente ou confirmado. Guardar referência do webhook processado e reconciliar o estado do pagamento. |
| Billing · `numberone_billing` | Cancelamento comercial e `outbox_event` | Operação de cancelamento com ID, orçamento/revisão, hash e resultado. Outbox com envelope e estados da seção 4. |
| Inventory · `numberone_inventory` | Estoque e movimentações | Saldo e movimentos por material, operação, quantidade, tipo e referência ao movimento original na reversão. Movimentos auditáveis e consumo agrupado atômico. |
| Inventory · `numberone_inventory` | Consumo e reversão | `operationId`, referências de OS/item quando conhecidas e hash da solicitação quando recebida. Resultado `CONSUMED`, `REJECTED`, `REVERSED` ou `CLOSED`. Reversão ou fechamento com `compensationId`, operação original, evidência aplicável e resultado. Proteger uma única reversão efetiva do consumo. |

### Histórico de revisões veiculares no DynamoDB

O uso de NoSQL para a entrega será o histórico das revisões registradas pela OS, consultável por veículo. A Saga e as operações de integração permanecem no PostgreSQL. Na conclusão de uma manutenção executada, a OS grava o snapshot e a pendência de projeção na mesma transação que registra `COMPLETED`. Um worker da própria OS lê a outbox local e grava no DynamoDB com chave estável e escrita condicional. O retry usa a mesma chave e confirma a projeção já existente quando o conteúdo coincide.

Para a tabela `vehicle_maintenance_history`, a proposta anterior usa `vehicleId` como chave de partição e `completedAt#serviceOrderId#historyVersion` como chave de ordenação. A versão inicial é fixa no snapshot. A projeção pode ser retomada após indisponibilidade do DynamoDB, sem bloquear a conclusão, o pagamento ou a entrega. A OS consulta o histórico por sua API autorizada. [Especificação do histórico veicular](implementacao-os/fase-6-historico-nosql.md)

## 7. Alinhamentos com a equipe

### Com Julio · Orçamento e eventos financeiros

- **O que significa `OrcamentoEnviado`?** A proposta é que o Billing registre `SENT` quando a revisão estiver disponível para o cliente decidir, e grave o evento no mesmo commit. A OS entra em `WAITING_APPROVAL`. O resultado da notificação por e-mail fica registrado separadamente no Billing, que pode reenviar a notificação da mesma revisão. Julio precisa confirmar se disponibilizar para aprovação é suficiente ou se o processo também exige aceite do provedor de e-mail para marcar `SENT`.
- **Como identificar o orçamento vigente?** Billing gera uma revisão crescente por OS e mantém o número estável no envio e na decisão. Exemplo: o orçamento da revisão 1 é rejeitado e o da revisão 2 é aprovado. Se chegar um `OrcamentoEnviado` atrasado da revisão 1, a OS mantém a revisão 2. Julio e Matheus precisam combinar como Billing garante a identidade única da revisão e como OS recupera o vínculo caso a decisão chegue antes do envio.
- **Para que serve `PagamentoAtualizado`?** Se Billing confirmou pagamento e depois confirmou um estorno integral, EV-04 atualiza a projeção que antes mostrava `PAID`. Sugestão: incluir EV-04 na v1 se o estorno integral entrar nessa versão. Se só uma cobrança excedente for estornada, a cobrança válida ainda pode quitar a ordem. CT-07 continua sendo a confirmação de quitação, considerando o conjunto de pagamentos.

### Com o grupo · Política de referência para cancelamento e entrega

- **C1–C6 automático antes da execução confirmada.** Para a primeira versão, limitar a compensação automática ao cancelamento integral antes da confirmação do primeiro início e com ausência de uso físico confirmada. Um funcionário autorizado registra quem conferiu, quando e quais materiais estão envolvidos. Estoque resolve todas as tentativas afetadas antes de solicitar o cancelamento do orçamento vigente ao Billing, desde que elegível e sem efeito financeiro.
- **Execução já iniciada, uso físico ou cancelamento parcial.** Encaminhar para revisão operacional. A movimentação de retorno exige devolução física conferida. Se todos os itens acabarem cancelados, tratar o encerramento e a retirada pela política a validar com a equipe, sem gerar cobrança automática.
- **Estorno e entrega.** Manter CT-07 imediatamente antes de registrar a entrega. Automatizar, se fizer parte do escopo, o estorno da cobrança excedente que o Billing identificou com segurança. Para estornar a cobrança que quita a OS, orientar autorização manual e coordenação com quem entrega o veículo. Uma consulta CT-07 seguida de commit local ainda permite uma corrida com o estorno. Se o grupo exigir bloquear a concorrência por sistema, será necessário alinhar um protocolo adicional entre OS e Billing. Após entrega, tratar eventual estorno como ocorrência financeira auditada.

| Tema | O que precisa ser combinado |
| --- | --- |
| Matheus + Julio · D09/P13 e D12 | Confirmar as três decisões acima, moeda no EV-02 e uso de CT-07 para atualizar a indicação de prontidão na OS. |
| Matheus + Anderson · CT-05/08/08B | Fechamento `CLOSED` incluído no contrato. Alinhar status HTTP, códigos de resultado e dados que comprovam não uso ou devolução. |
| Matheus + Julio + Anderson · D05/D10/D11 | Alinhar a adoção da janela de cancelamento acordada para a OS, a elegibilidade do orçamento aprovado para CT-09 e a política de retirada da OS cancelada. |
| Marcelo · plataforma | Confirmar nomes, Standard/FIFO, IAM, visibilidade, redrive, retenção e alarmes da fila. Provisionar a tabela de histórico e o acesso da OS. |

### Cenários de aceite dos contratos

- Caminho normal: orçamento aprovado, consumo confirmado, reparo concluído, cobrança manual, EV-03 e entrega após CT-07 positivo.
- Repetir um EV-02 e reenviar EV-01 depois da aprovação. Confirmar uma única decisão aplicada e nenhuma regressão.
- Interromper Billing após envio ao SQS e OS após commit da inbox. Retomar ambos e verificar efeito único.
- Simular timeout após baixa em CT-05. Recuperar pelo CT-08 e verificar que há um único movimento por material.
- Cancelar antes de uso, repetir CT-08B e CT-09 e retomar a Saga após reinício entre C3 e C4. Confirmar saldo reposto uma vez e Saga compensada somente no fim.
- Encerrar a tentativa com `CLOSED` antes da chegada do consumo e provar que CT-05 atrasado recebe recusa e preserva o saldo.
- Receber EV-04 antes de EV-03 do mesmo pagamento. Verificar que a projeção usa a versão mais nova e que entrega depende da quitação oficial.
- Concluir uma manutenção com DynamoDB indisponível e retomar a projeção. Consultar o histórico do veículo e verificar uma única entrada para a OS após repetir a tarefa.
