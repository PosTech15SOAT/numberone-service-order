# Estados e guardas propostos — sem máquina de estados implementada

Saga financeira e status comercial da OS são conceitos distintos. `APPROVED` não
significa pagamento confirmado; `READY_FOR_EXECUTION` não significa reparo iniciado;
`START_REQUESTED` não permite executar antes de confirmar consumo. Após primeiro
início a Saga pode estar COMPLETED, mas itens posteriores ainda exigem consumo próprio.

| Entrada/estado | Guarda/efeito permitido | Destino |
| --- | --- | --- |
| Diagnóstico concluído em IN_DIAGNOSIS | snapshot local + CreateBudget em futura transação/outbox | WAITING_BUDGET |
| BudgetSent vigente | envio aceito; guardar budgetId/revisão, nunca aprovação | WAITING_APPROVAL |
| BudgetCreationFailed | rejeição definitiva; bloquear início e fechar tentativa pela compensação | COMPENSATING |
| BudgetNotificationFailed | envio pendente de recuperação por Billing; sem inferir aprovação/rejeição | manter WAITING_BUDGET/WAITING_APPROVAL |
| BudgetApproved vigente | snapshot completo, valor/moeda e referências válidos; ReserveMaterials se houver materiais | WAITING_RESERVATION |
| BudgetApproved sem materiais | registrar bypass; RequestPayment diretamente | WAITING_PAYMENT |
| BudgetRejected | rejeição confirmada; bloquear início, ReleaseMaterials mesmo sem reserva conhecida | COMPENSATING |
| MaterialsReserved em WAITING_RESERVATION | conferir alocações/filial/tentativa/revisão; guardar reservationId, RequestPayment | WAITING_PAYMENT |
| MaterialsReservationFailed | reserva atômica recusada, nenhum efeito parcial; liberar/fechar tentativa antes de cancelar Billing | COMPENSATING |
| PaymentPending | registrar tentativa/checkout sem regredir confirmação recebida | WAITING_PAYMENT |
| PaymentConfirmed vigente | conferir total/moeda exatos contra snapshot, IDs/estado; encerrar prazo financeiro | READY_FOR_EXECUTION |
| PaymentFailed definitivo | não usar esse evento para timeout; bloquear inícios e compensar | COMPENSATING |
| HTTP início de item com materiais, READY | permissão, filial, revisão, tentativa, pagamento, sem cancelamento; persistir START_REQUESTED + ConsumeMaterials | WAITING_CONSUMPTION no primeiro item |
| MaterialsConsumed | confirmar operação/item/reserva/movimentos; item IN_PROGRESS; primeiro início muda OS para execução | COMPLETED no primeiro início |
| MaterialsConsumptionFailed | recusa confirmada sem novo consumo; bloquear início, compensar se não houve consumo; conflito físico vai a operação manual | COMPENSATING ou MANUAL_INTERVENTION |
| Início de item sem materiais | mesmos guardas financeiros/cancelamento; transação local, sem comando vazio | item IN_PROGRESS; COMPLETED no primeiro início |
| Cancelamento antes de consumo | bloquear novos inícios; ReleaseMaterials primeiro | COMPENSATING |
| MaterialsReleased | resultado RELEASED ou NOT_RESERVED confirma tentativa fechada; só então CancelBilling | continuar COMPENSATING |
| BillingCompensated após liberação confirmada | NOT_CHARGED/CANCELLED/REFUNDED definitivo, sem pendência externa | COMPENSATED; cancelar/rejeitar OS |
| MaterialsAlreadyConsumed | não repor estoque nem estornar automaticamente; informar alocações consumidas/liberadas | MANUAL_INTERVENTION |

## Guardas obrigatórios, a implementar nas fases seguintes

Validar schema/produtor e identidade da fila; messageId, operationId, sagaId, OS,
filial, tentativa, revisão, referências comerciais, item, valores/moeda e estado.
Mensagem válida no JSON pode ser inválida no domínio. Guardar decisão e correlação
sem payload pessoal. Estado final nunca reabre tentativa por evento atrasado.

Reentrega do mesmo messageId não reaplica efeito. Nova mensagem para operationId
concluído retorna/reutiliza resultado persistido; conteúdo diferente é conflito.
Checagens e mudança local são atômicas com inbox/outbox na fase 3; FIFO não as substitui.
Consumo exige alocações compatíveis com o snapshot e movimento único por operação.
Reserva confirma todos os materiais ou nenhum; não permitir saldo/efeito parcial oculto.

BudgetApproved pode chegar antes de BudgetSent: aprovação válida vence envio;
BudgetSent posterior não regride. PaymentPending posterior a PaymentConfirmed não
regride. Um resultado válido para operação ainda sem dependência disponível fica
pendente de correlação/reconciliação durável, com observabilidade e retentativa;
não descartar silenciosamente nem executar guardas parcialmente.
Resultado de tentativa/revisão antiga é auditado, não reativa a atual.

## Compensação, cancelamento concorrente e timeout

Ordem: bloquear inícios → ReleaseMaterials → MaterialsReleased → CancelBilling →
BillingCompensated → encerrar localmente. Inventory registra fechamento mesmo sem
reserva encontrada, impedindo ReserveMaterials atrasado. Billing fecha tentativa
inexistente, impedindo CreateBudget/RequestPayment atrasados. NOT_RESERVED e
NOT_CHARGED são resultados confirmados pelo dono, diferentes de UNKNOWN/404.

Se consumo já foi solicitado e resultado é desconhecido, não estornar até consultar
operação/liberação. Se consumido, encaminhar a decisão operacional, sem estoque ou
reembolso automático. Falha técnica/timeout não vira falta de saldo/pagamento recusado.
Repetir operação preserva operationId; consulta GET não produz nova cobrança/consumo.

Timeout financeiro é configurável e seu valor está pendente com os responsáveis.
Vencer prazo inicia recuperação; não prova falha e não encerra compensação pendente.
PaymentConfirmed tardio não autoriza reparo em tentativa fechada: Billing reconcilia
e estorna conforme encerramento, emitindo resultado definitivo. Caso parcialmente
executado demanda decisão operacional e política de cancelamento ainda não aprovada.

## Demonstrações de contrato (não execuções)

1. R$ 350/um kit: aprovação → reserva → confirmação de pagamento → HTTP 202 →
   confirmação de consumo; só então execução.
2. Sem materiais: aprovação → pagamento → início local após guardas, sem reserva/consumo.
3. Falta de estoque: MaterialsReservationFailed → liberação/fechamento → compensação Billing.
4. Duplicação: mesmos IDs, zero novo efeito previsto; será provado com banco/SQS nas fases 3–5.
5. Cancelamento antes da reserva: ReleaseMaterials early → NOT_RESERVED → CancelBilling early → NOT_CHARGED.
6. Timeout: GET retorna UNKNOWN/404; manter pendência, jamais inferir ausência de efeito.
7. Já consumido: MaterialsAlreadyConsumed → intervenção manual, sem reposição/estorno automático.
