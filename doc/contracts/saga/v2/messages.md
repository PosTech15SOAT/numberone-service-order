# Eventos ativos e identidade

O corpo de cada mensagem é um objeto JSON UTF-8 conforme `envelope.schema.json`.
Os campos obrigatórios são eventId, eventType, schemaVersion, occurredAt,
correlationId e payload. Datas são UTC terminadas em Z. Referências são UUIDs.
Valores internos usam string decimal canônica com duas casas.

| Evento | Payload obrigatório |
| --- | --- |
| OrcamentoEnviado | serviceOrderId, budgetId, budgetVersion, compositionVersion |
| OrcamentoAprovado | Os campos do envio, approvedAmount positivo e currency=BRL |
| OrcamentoRejeitado | Os campos do envio |
| PagamentoConfirmado | serviceOrderId, paymentId, paymentStatus=PAID, paymentVersion |
| PagamentoAtualizado | serviceOrderId, paymentId, paymentStatus=REFUNDED, paymentVersion |

Todos os cinco eventos são publicados pelo Billing e consumidos pela OS. Estorno
integral precisa de confirmação do provedor. O catálogo e o dispatch incluem EV-04.
Reentrega do mesmo fato preserva eventId e conteúdo. A OS usa unicidade e hash na
inbox, gravando inbox/efeito/tarefa no mesmo commit e fazendo ACK após commit.
Hash divergente para o mesmo ID é conflito. Campos opcionais futuros são tolerados.

budgetVersion cresce por OS e compositionVersion aponta para snapshot imutável.
paymentVersion cresce por paymentId. occurredAt serve para auditoria. Uma versão
financeira inferior é obsoleta. Mesma versão com outro conteúdo exige reconciliação.

Decisão anterior a OrcamentoEnviado usa CT-10 fora da transação da inbox. Confirmar
identidade e contexto vigente e revalidar o estado local antes do commit. Se o
contexto ainda estiver indisponível, retentar sem registrar APPLIED. Evento atrasado
da mesma revisão não regride decisão. Rejeição obsoleta é auditada. O guia descreve
as regras de contexto comercial fechado e escopo aprovado.

operationId identifica uma tentativa REST durável. compensationId identifica seu
fechamento/reversão. Repetição técnica mantém chave e pedido. Nova ação após recusa
usa outra chave explícita. Os IDs diferem do operationId estático do OpenAPI.
