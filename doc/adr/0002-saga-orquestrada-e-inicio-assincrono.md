# ADR 0002 — Saga orquestrada e início assíncrono

Status: proposta, dependente de Matheus/Julio/Anderson/Marcelo. Data: 31/10/2026.

## Contexto e decisão proposta

Não há transação ACID entre OS, Billing e Inventory. OS orquestra comandos/resultados
com identidades persistidas, inbox/outbox em banco próprio e compensações explícitas.
FIFO auxilia ordenação por grupo/fila, sem prometer exatamente uma execução global.

Pagamento confirmado habilita prontidão; não inicia serviço. HTTP de início com
materiais retorna 202/START_REQUESTED após commit local e publicação futura de
ConsumeMaterials. Só MaterialsConsumed confirmado muda item para IN_PROGRESS.
Item sem materiais inicia localmente após os mesmos guardas financeiros/cancelamento.

Compensação automática anterior ao consumo libera/fecha Inventory antes de cancelar
Billing. Falha física já consumida exige intervenção manual. Resultado desconhecido
permanece pendente, consultável via GET autenticado; 404 não prova ausência de efeito.
Reentrega mantém messageId; repetição semântica mantém operationId. Tentativa fechada
não reabre por mensagens tardias. Eventos fora de ordem são reconciliados, não descartados.

## Consequências e limites

HTTP passa a expor início pendente e acompanhamento. Novas transições, compensação
e timeouts precisam de testes de regra, persistência e transporte nas fases 2–5.
Nesta fase existem somente schemas, fixtures e propostas HTTP. A consistência
distribuída não foi executada/provada. Nenhum stub é apresentado como integração real.

Idempotency-Key no início, estados HTTP propostos, quantidade absoluta de materiais
por item, política de preço e credencial de serviço devem ser aprovados pelos donos.
Decisões e pendências detalhadas em [pedidos de integração](../contracts/saga/v1/integration-requests.md).
