# Envelope, identidade e recuperação

O corpo SQS é um objeto JSON UTF-8, não string contendo JSON nem envelope SNS.
Os seis campos obrigatórios estão em [envelope.schema.json](envelope.schema.json).
Schemas Draft 2020-12 usam formatos UUID e date-time efetivamente validados.
Datas são RFC 3339 UTC com sufixo `Z`; revisões são inteiros positivos.
Dinheiro usa string decimal não negativa com duas casas; moeda BRL é proposta
pendente Julio. Valor zero é estruturalmente permitido; elegibilidade comercial
não é decidida pelo schema. Campos opcionais futuros são tolerados; tipo ou versão
desconhecidos não são aceitos. Não transportar CPF, contato, QR Code, tokens ou cartão.

| ID | Escopo e uso | Repetição |
| --- | --- | --- |
| `eventId` | Identidade de um fato; chave única da inbox OS | Retry, reentrega e redrive preservam ID e conteúdo |
| `correlationId` | UUID de rastreio entre HTTP, evento e logs | Não deduplica nem identifica Saga |
| `payload.serviceOrderId` | Localiza a única Saga da OS | Nunca inferir OS pela correlação |
| `operationId` | Uma operação REST mutável e sua tentativa durável | CT-05 e CT-09 têm IDs distintos; repetir mesmo pedido conserva ID |
| `compensationId` | Uma reversão/fechamento CT-08B | Referencia operationId original na URL; chave distinta e estável |
| `budgetVersion` | Revisão comercial crescente por OS, mesmo com novo budgetId | Envio e decisão da revisão conservam número e budgetId |
| `paymentVersion` | Versão crescente por paymentId | Não comparar versões entre pagamentos diferentes |
| `sagaId` | Identidade interna OS | Não exigida no envelope |

GETs não criam uma operação de negócio; o `operationId` do OpenAPI é o nome estático
da operação da especificação, diferente do UUID de idempotência REST.

| Evento | Fato e guarda | Schema / exemplo |
| --- | --- | --- |
| EV-01 `OrcamentoEnviado` | Revisão SENT disponível para decisão; semântica exata pendente Julio | [schema](events/OrcamentoEnviado.schema.json), [exemplo](examples/OrcamentoEnviado.json) |
| EV-02 `OrcamentoAprovado` | Aprovação integral persistida, IDs, revisão, valor/moeda | [schema](events/OrcamentoAprovado.schema.json), [exemplo](examples/OrcamentoAprovado.json) |
| EV-02B `OrcamentoRejeitado` | Rejeição integral, alternativa à aprovação da revisão | [schema](events/OrcamentoRejeitado.schema.json), [exemplo](examples/OrcamentoRejeitado.json) |
| EV-03 `PagamentoConfirmado` | Pagamento PAID validado no Billing; não significa quitação de toda OS por si só | [schema](events/PagamentoConfirmado.schema.json), [exemplo](examples/PagamentoConfirmado.json) |
| EV-04 `PagamentoAtualizado` | Somente REFUNDED integral, se incluído na v1 Billing | [schema condicional](conditional/PagamentoAtualizado.schema.json), [exemplo](conditional/examples/PagamentoAtualizado.json) |

EV-04 não está habilitado em `message.schema.json`; existência de fixture não é
aceite. Ativar exige decisão de Julio/Matheus com versão, atualização do catálogo,
dispatch e testes. Estorno parcial ou outros estados precisam de contrato próprio.

## Ordem e duplicatas

1. Validar schema e origem/fila. Na transação OS, deduplicar eventId e comparar hash
   do conteúdo canônico. Duplicata idêntica recupera desfecho, sem efeito adicional;
   mesmo ID com conteúdo divergente é conflito e exige diagnóstico/DLQ.
2. Serializar transições por OS, com lock ou versão e retry. Revisão inferior é
   `IGNORED_STALE`. Mesma revisão com outro budgetId ou decisões divergentes não é
   duplicata: reconciliar com Billing e encaminhar a revisão se persistir.
3. EV-01 atrasado da mesma revisão após EV-02/02B não regride estado. Decisão anterior
   a EV-01, sem contexto confiável da revisão/composição, fica pendente: não gravar
   APPLIED nem confirmar SQS; rollback e retry. Recuperação do vínculo/revisão
   depende Julio/Matheus; nenhum endpoint de reconciliação comercial foi inventado
   como acordo existente. Exaurido retry, DLQ e revisão operacional.
4. Revisão superior não basta para autorizar: verificar identidade, contexto,
   transição válida e [composição](approval-composition.md). Não aplicar orçamento
   que reabra execução aprovada congelada, cancelamento ou estado terminal.
5. Pagamentos: aplicar versão mais nova por paymentId. Menor versão é obsoleta;
   mesma versão com conteúdo divergente exige reconciliação. Se EV-04 for adotado,
   REFUNDED v2 recebido antes de PAID v1 permanece REFUNDED. Agendar CT-07 para
   reavaliar conjunto de pagamentos; um estorno excedente não implica OS não quitada.
6. `occurredAt` é auditoria, não relógio para ordenar fatos. Eventos financeiros
   após entrega atualizam projeção/incidente, preservando entrega auditada.

## Atomicidade local e retomada

Billing grava transição comercial e outbox na mesma transação. Worker com posse
temporária envia fora da transação e marca publicação após aceite SQS. Se cair entre
envio e marcação, reenvia o mesmo envelope. Falha persistente gera alerta e revisão
na outbox; não perde fato nem gera novo eventId para retry.

OS grava inbox (`APPLIED`/`IGNORED_STALE`), projeções, transição e tarefas locais no
mesmo commit; confirma SQS só depois. Queda após commit e antes de DeleteMessage
é recuperada por deduplicação. Falta de contexto não marca processamento concluído.
Ordem de processamento não pode depender de FIFO. Retenção inbox precisa cobrir
o horizonte completo de reprocessamento acordado com Marcelo.

OS persiste intenção REST e identidade antes da chamada, executa HTTP fora do lock,
e revalida versão/compensação ao aplicar resposta. Timeout vira `AWAITING_RESULT`,
nunca recusa confirmada. Inventory/Billing persistem chave, hash do pedido,
resultado e efeito juntos; mesma chave/outro conteúdo retorna `409 IDEMPOTENCY_CONFLICT`.
Não trocar chave em retry técnico. Nova chave só representa nova ação de negócio.
