# Matriz de mensagens — rascunho v1

Cada linha corresponde à matriz da proposta. Todos os envelopes são completos;
campos e obrigatoriedade constam nos schemas vinculados. Comandos não implementam
operações nesta fase; eventos não comprovam efeitos reais.

| Mensagem | Produtor → consumidor | Schema | Exemplo |
| --- | --- | --- | --- |
| CreateBudget | os-service → billing-service | [schema](commands/CreateBudget.schema.json) | [fixture](examples/CreateBudget.json) |
| BudgetSent | billing-service → os-service | [schema](events/BudgetSent.schema.json) | [fixture](examples/BudgetSent.json) |
| BudgetCreationFailed | billing-service → os-service | [schema](events/BudgetCreationFailed.schema.json) | [fixture](examples/BudgetCreationFailed.json) |
| BudgetNotificationFailed | billing-service → os-service | [schema](events/BudgetNotificationFailed.schema.json) | [fixture](examples/BudgetNotificationFailed.json) |
| BudgetApproved | billing-service → os-service | [schema](events/BudgetApproved.schema.json) | [fixture](examples/BudgetApproved.json) |
| BudgetRejected | billing-service → os-service | [schema](events/BudgetRejected.schema.json) | [fixture](examples/BudgetRejected.json) |
| ReserveMaterials | os-service → inventory-service | [schema](commands/ReserveMaterials.schema.json) | [fixture](examples/ReserveMaterials.json) |
| MaterialsReserved | inventory-service → os-service | [schema](events/MaterialsReserved.schema.json) | [fixture](examples/MaterialsReserved.json) |
| MaterialsReservationFailed | inventory-service → os-service | [schema](events/MaterialsReservationFailed.schema.json) | [fixture](examples/MaterialsReservationFailed.json) |
| RequestPayment | os-service → billing-service | [schema](commands/RequestPayment.schema.json) | [fixture](examples/RequestPayment.json) |
| PaymentPending | billing-service → os-service | [schema](events/PaymentPending.schema.json) | [fixture](examples/PaymentPending.json) |
| PaymentConfirmed | billing-service → os-service | [schema](events/PaymentConfirmed.schema.json) | [fixture](examples/PaymentConfirmed.json) |
| PaymentFailed | billing-service → os-service | [schema](events/PaymentFailed.schema.json) | [fixture](examples/PaymentFailed.json) |
| ConsumeMaterials | os-service → inventory-service | [schema](commands/ConsumeMaterials.schema.json) | [fixture](examples/ConsumeMaterials.json) |
| MaterialsConsumed | inventory-service → os-service | [schema](events/MaterialsConsumed.schema.json) | [fixture](examples/MaterialsConsumed.json) |
| MaterialsConsumptionFailed | inventory-service → os-service | [schema](events/MaterialsConsumptionFailed.schema.json) | [fixture](examples/MaterialsConsumptionFailed.json) |
| ReleaseMaterials | os-service → inventory-service | [schema](commands/ReleaseMaterials.schema.json) | [fixture](examples/ReleaseMaterials.json) |
| MaterialsReleased | inventory-service → os-service | [schema](events/MaterialsReleased.schema.json) | [fixture](examples/MaterialsReleased.json) |
| MaterialsAlreadyConsumed | inventory-service → os-service | [schema](events/MaterialsAlreadyConsumed.schema.json) | [fixture](examples/MaterialsAlreadyConsumed.json) |
| CancelBilling | os-service → billing-service | [schema](commands/CancelBilling.schema.json) | [fixture](examples/CancelBilling.json) |
| BillingCompensated | billing-service → os-service | [schema](events/BillingCompensated.schema.json) | [fixture](examples/BillingCompensated.json) |
