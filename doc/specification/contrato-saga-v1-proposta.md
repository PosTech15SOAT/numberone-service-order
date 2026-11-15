# Base de contrato da Saga v1 para a fase 1

**Status:** proposta técnica para discussão e geração de schemas; ainda não é contrato aprovado pela equipe. O objetivo é tirar ambiguidades antes de implementar os três serviços. A fase 1 deve transformar este texto em schemas/exemplos validados e registrar a versão revisada por Matheus, Julio, Anderson e Marcelo.

## 1. Responsabilidades e fluxo

- **OS:** diagnóstico, composição proposta, ciclo da execução, Saga e entrega.
- **Billing:** cálculo comercial, orçamento/aprovação, cobrança, confirmação/estorno Mercado Pago.
- **Inventory:** disponibilidade, reserva, consumo e liberação de materiais.

```mermaid
sequenceDiagram
    participant O as OS
    participant B as Billing
    participant I as Inventory
    Note over O: Diagnóstico local concluído
    O->>B: CreateBudget
    B->>O: BudgetSent / BudgetApproved
    O->>I: ReserveMaterials
    I->>O: MaterialsReserved
    O->>B: RequestPayment
    B->>O: PaymentPending / PaymentConfirmed
    Note over O: Equipe solicita início; item START_REQUESTED
    O->>I: ConsumeMaterials
    I->>O: MaterialsConsumed
    Note over O: Confirmar início; depois concluir/entregar localmente
```

Não criar mensagens de diagnóstico ou autorização de execução para Inventory. Se não houver materiais em toda a composição aprovada, a OS pode pular a reserva, registrar essa decisão e pedir pagamento; item sem materiais inicia localmente após guardas financeiros/cancelamento. Não enviar reserva vazia para exigir um sucesso artificial do Inventory.

## 2. Envelope comum

| Campo | Formato / regra |
| --- | --- |
| `messageId` | UUID único da mensagem; reentrega técnica preserva o ID |
| `sagaId` | UUID do processo da OS; também `MessageGroupId` nas filas |
| `serviceOrderId` | UUID da OS |
| `branchId` | UUID da filial; obrigatório mesmo no ambiente com uma única filial |
| `attemptId` | UUID da tentativa do atendimento; comum aos três serviços, diferente de messageId |
| `operationId` | UUID de uma operação lógica, persistido pelo solicitante; resposta mantém esse ID |
| `type` | Nome exato de comando/evento da matriz abaixo |
| `schemaVersion` | Inteiro `1` nesta versão; sem relação com revisão do orçamento |
| `causationId` | UUID da mensagem causadora; `null` na origem HTTP/local |
| `occurredAt` | Data/hora UTC RFC 3339; não decide ordenação de negócio sozinha |
| `producer` | `os-service`, `billing-service` ou `inventory-service` |
| `correlationId` | Correlação técnica da origem; manter nos logs com sagaId/messageId |
| `traceparent` | Opcional, contexto de tracing compatível com a instrumentação adotada |
| `payload` | Objeto tipado conforme `type` |

`MessageDeduplicationId = messageId`. FIFO ordena por grupo dentro de cada fila; não fornece commit distribuído nem substitui idempotência de negócio. URL/ARN/região são configuração, não parte do payload. Não colocar token, CPF completo, segredo ou dados de cartão em mensagens.

Reenvio da mesma operação conserva `operationId`, tentativa e referências. Uma nova mensagem de resposta usa `messageId` novo e `causationId` da mensagem que a provocou; vários resultados de um pagamento podem manter a mesma operação e tentativa. Repetição nunca deve abrir nova cobrança/consumo. Uma nova ação de negócio usa outra operação. `attemptId` fechado nunca é reaberto por mensagem atrasada.

## 3. Tipos de dados do payload

- UUID para referências, datas UTC, quantidades inteiras positivas como no modelo atual. Quantidades fracionárias ficam fora do escopo inicial, salvo acordo explícito com Anderson.
- `budgetRevision` inteiro positivo; uma revisão aprovada por tentativa neste escopo.
- `currency` ISO 4217; `BRL` nos exemplos. Valores como strings decimais com duas casas (`"150.00"`), convertidos para BigDecimal/decimal exato. Sem `double`/float e sem confiar em total fornecido pelo cliente.
- `serviceItems[]`: `serviceOrderItemId`, `automotiveServiceId`, `description`, `quantity`, `laborUnitPrice`, `materials[]`.
- Cada material: `materialId`, `quantity`, `referenceUnitPrice` na proposta; Billing devolve `approvedUnitPrice` na composição aprovada. O vínculo com item de serviço permanece explícito.
- `approvedItems[]`: composição completa aprovada, com mão de obra, materiais, quantidades e preços congelados; `totalAmount` é calculado por Billing. OS valida consistência/referências e guarda snapshot, sem recalcular silenciosamente o preço aprovado.
- `allocations[]`: `serviceOrderItemId` e materiais/quantidades reservados; não contém regra de execução do reparo nem preço mutável.
- Erro de negócio: `reasonCode` estável e `detail` sanitizado; falha técnica transitória deve ser retentada, não transformada em falta de saldo ou pagamento recusado.

Billing fecha com Matheus os campos comerciais e a política de cálculo antes de publicar o schema aprovado. Contato para envio fica no payload de orçamento com o mínimo necessário; Inventory não recebe dados pessoais do cliente.

## 4. Matriz de comandos e eventos

| Mensagem | Direção | Campos específicos mínimos / efeito |
| --- | --- | --- |
| `CreateBudget` | OS → Billing | `budgetRevision`, `vehicleId`, `serviceItems`, destinatário mínimo; criar uma vez por tentativa/revisão |
| `BudgetSent` | Billing → OS | `budgetId`, `budgetRevision`, `currency`, `totalAmount`; confirma aceite do envio, não aprovação |
| `BudgetCreationFailed` | Billing → OS | Revisão, reasonCode; rejeição definitiva de criação, diferente de erro técnico retentável |
| `BudgetNotificationFailed` | Billing → OS | BudgetId/revisão, reasonCode; envio pendente de recuperação, sem inventar aprovação |
| `BudgetApproved` | Billing → OS | BudgetId/revisão, `approvedAt`, `currency`, `totalAmount`, `approvedItems` |
| `BudgetRejected` | Billing → OS | BudgetId/revisão, `rejectedAt`, reasonCode |
| `ReserveMaterials` | OS → Inventory | Revisão, `allocations`; confirmar todos ou nenhum; identidade lógica por tentativa/revisão |
| `MaterialsReserved` | Inventory → OS | `reservationId`, revisão, `allocations`; identificador escolhido pelo Inventory |
| `MaterialsReservationFailed` | Inventory → OS | Revisão, reasonCode, itens indisponíveis quando necessário; nenhum efeito parcial |
| `RequestPayment` | OS → Billing | BudgetId/revisão, `currency`, `totalAmount`; cobrança baseada no orçamento aprovado de Billing |
| `PaymentPending` | Billing → OS | `paymentAttemptId`, budgetId/revisão, referência/URL de checkout permitida; não autoriza execução |
| `PaymentConfirmed` | Billing → OS | PaymentAttemptId, budgetId/revisão, `currency`, `totalAmount`, `confirmedAt`, referência do pagamento |
| `PaymentFailed` | Billing → OS | PaymentAttemptId quando existente, revisão, reasonCode; resultado de negócio confirmado, não timeout desconhecido |
| `ConsumeMaterials` | OS → Inventory | ReservationId, revisão, `serviceOrderItemId`; consome a alocação desse item, com operationId persistente |
| `MaterialsConsumed` | Inventory → OS | ReservationId, item, `consumedAt`, referências/quantidades dos movimentos; confirmação do efeito |
| `MaterialsConsumptionFailed` | Inventory → OS | ReservationId/item, reasonCode; recusa confirmada sem novo consumo |
| `ReleaseMaterials` | OS → Inventory | Revisão, reservationId se já conhecido, motivo; fechar tentativa mesmo sem reserva encontrada |
| `MaterialsReleased` | Inventory → OS | Revisão, reservationId quando existir, `releasedAt`, resultado `RELEASED` ou `NOT_RESERVED`; tentativa fechada |
| `MaterialsAlreadyConsumed` | Inventory → OS | ReservationId, alocações consumidas/liberadas e motivos; exige decisão operacional |
| `CancelBilling` | OS → Billing | Motivo, budgetId/paymentAttemptId se conhecidos; fechar tentativa mesmo se cobrança ainda não existe |
| `BillingCompensated` | Billing → OS | Resultado `NOT_CHARGED`, `CANCELLED` ou `REFUNDED`, referências existentes e horário final; sem pendência externa |

Filas propostas: comandos Inventory em `numberone-inventory-commands.fifo`, comandos Billing em `numberone-billing-commands.fifo`, resultados em `numberone-order-results.fifo`. Uma DLQ FIFO para cada uma. Marcelo confirma nomes/outputs por ambiente; esses nomes não são evidência de recursos criados.

## 5. Exemplo de reserva para compartilhar

IDs e dados são fictícios. JSON abaixo é um exemplo de envelope completo a converter em fixture validada na fase 1:

```json
{
  "messageId": "10000000-0000-4000-8000-000000000001",
  "sagaId": "20000000-0000-4000-8000-000000000001",
  "serviceOrderId": "30000000-0000-4000-8000-000000000001",
  "branchId": "40000000-0000-4000-8000-000000000001",
  "attemptId": "50000000-0000-4000-8000-000000000001",
  "operationId": "60000000-0000-4000-8000-000000000001",
  "type": "ReserveMaterials",
  "schemaVersion": 1,
  "causationId": "10000000-0000-4000-8000-000000000002",
  "occurredAt": "2026-10-31T15:00:00Z",
  "producer": "os-service",
  "correlationId": "demo-os-123",
  "payload": {
    "budgetRevision": 1,
    "allocations": [
      {
        "serviceOrderItemId": "70000000-0000-4000-8000-000000000001",
        "materials": [
          {
            "materialId": "80000000-0000-4000-8000-000000000001",
            "quantity": 1
          }
        ]
      }
    ]
  }
}
```

O resultado `MaterialsReserved` conserva sagaId/OS/filial/tentativa/operationId, cria novo messageId, usa o ID do comando como causationId e acrescenta reservationId. Preço não é necessário para reservar. A fase 1 gera fixtures completas para todas as linhas da matriz, além do cenário de R$ 350 e um kit.

## 6. Estados e guardas da OS

Estados propostos da Saga, distintos dos status comerciais:

| Estado / entrada | Próximo efeito permitido |
| --- | --- |
| `IN_DIAGNOSIS` + diagnóstico concluído localmente | Gravar snapshot + CreateBudget; `WAITING_BUDGET` |
| `WAITING_BUDGET` + BudgetSent | `WAITING_APPROVAL` |
| BudgetApproved da revisão/tentativa vigentes | Gravar snapshot aprovado; ReserveMaterials e `WAITING_RESERVATION`, ou RequestPayment se sem materiais |
| `WAITING_RESERVATION` + MaterialsReserved | Gravar reserva; RequestPayment; `WAITING_PAYMENT` |
| PaymentPending vigente | Atualizar visão da cobrança sem regredir confirmação já recebida |
| PaymentConfirmed válido | Registrar confirmação; `READY_FOR_EXECUTION`; encerrar prazo financeiro |
| READY + início HTTP de item com materiais | Item `START_REQUESTED`, ConsumeMaterials; no primeiro item `WAITING_CONSUMPTION` |
| MaterialsConsumed válido | Item em execução; no primeiro início OS em execução e Saga `COMPLETED` |
| Falha/rejeição/cancelamento antes do consumo | Bloquear novos inícios; `COMPENSATING`; ReleaseMaterials |
| MaterialsReleased durante compensação | CancelBilling; manter compensação pendente |
| BillingCompensated após liberação | Cancelar/rejeitar OS localmente e marcar `COMPENSATED` |
| MaterialsAlreadyConsumed ou conflito físico não reversível | `MANUAL_INTERVENTION`; não repor nem estornar automaticamente |

Guardas obrigatórios: operação, tentativa, revisão, IDs, filial, valor/moeda confirmados e estado compatível. Eventos duplicados não reaplicam efeitos. Resultado obsoleto é auditado sem reativar tentativa. Resultado válido fora de ordem é classificado e recuperado, não descartado silenciosamente: por exemplo, BudgetApproved pode confirmar aprovação mesmo se BudgetSent ainda não foi processado; BudgetSent tardio não regride o fluxo.

Status comerciais atuais são preservados onde possível; acrescentar `READY_FOR_EXECUTION` e item `START_REQUESTED` quando necessário. `APPROVED` não significa pagamento confirmado. Explicitar os novos valores no OpenAPI. Depois do primeiro início, os demais itens continuam com operação de consumo própria; isso não repete a Saga financeira nem cobra de novo.

## 7. Compensação e reconciliação

Fluxo automático anterior ao consumo: bloquear inícios na OS → ReleaseMaterials → MaterialsReleased → CancelBilling → BillingCompensated → encerramento local. Inventory fecha a tentativa mesmo se ReserveMaterials ainda não chegou. Billing também fecha tentativa inexistente; comando financeiro atrasado não abre cobrança após encerramento.

Timeout não confirma falha. Para consultar resultado persistido sem mutação adicional, propõem-se contratos internos REST, a fechar com os donos na fase 1:

- Inventory: `GET /internal/v1/material-operations/{operationId}`. Estados `PENDING`, `APPLIED`, `REJECTED`, `UNKNOWN`, com resultado da operação quando conhecido.
- Billing: `GET /internal/v1/billing-attempts/{attemptId}`. Estado financeiro persistido, referências, valor/moeda e indicação de operação externa ainda desconhecida/pendente; Billing é quem consulta Mercado Pago.

`UNKNOWN`/não encontrado não significa ausência definitiva de efeito: o comando pode estar em trânsito. Consulta é protegida por autenticação de serviço e rede definidas com Marcelo, sem expor dados pessoais. Resultados confirmados entram nos mesmos guardas e transações locais dos eventos. Replay usa a mesma identidade da operação; não criar UUID novo para tentar novamente uma cobrança ou consumo incertos.

Se o prazo financeiro vencer, manter compensação pendente até esclarecer a cobrança e qualquer início já solicitado. Não reembolsar enquanto consumo/liberação estiver incerto. PaymentConfirmed tardio de tentativa encerrada não autoriza reparo; Billing deve reconciliar/estornar conforme o encerramento e emitir o resultado final.

## 8. O que fechar com cada membro na fase 1

| Pessoa | Revisão necessária |
| --- | --- |
| Matheus | Estados da OS, início pendente, composição, IDs e critérios de cancelamento |
| Julio | Cálculo/snapshot aprovado, estados financeiros, confirmação/estorno, repetição e consulta de tentativa |
| Anderson | Alocações, reserva atômica, consumo/liberação, fechamento da tentativa e consulta de operação |
| Marcelo | Filas/outputs, IAM viável no Academy, transporte de trace, autenticação de APIs internas e transição do login |

Publicar JSON Schema e fixtures, com versão/commit de referência, dentro do repositório OS; compartilhar o link do pacote. Não criar um sétimo repositório de contratos ou uma biblioteca Java obrigatória. Aprovação do texto não substitui validação dos schemas; schemas aprovados e exemplos coerentes passam a ser a referência para as implementações.
