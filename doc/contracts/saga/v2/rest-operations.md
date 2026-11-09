# Operações REST da implementação

| Contrato | Provedor | Uso |
| --- | --- | --- |
| CT-02A/03 | OS | Estado comercial com cancellationInProgress e composição imutável para Billing. |
| CT-05/08/08B | Inventory | Consumir, consultar e fechar/reverter com identidade durável. |
| CT-07/09 | Billing | Quitação e cancelamento por revisão conhecida. |
| CT-10/11 | Billing | Revisão vigente e fechamento por OS, inclusive sem orçamento. |
| AUTH-LOOKUP | OS | Lookup exclusivo do Auth para manter o login existente. |

Os OpenAPI contêm pedidos, retornos e códigos. A OS grava intenção antes de HTTP,
chama fora da transação e revalida versão/cancelamento na aplicação da resposta.
Timeout mantém AWAITING_RESULT. Um 404 de consulta não prova fechamento.

CT-05 usa Idempotency-Key=operationId. CT-08B usa Idempotency-Key=compensationId.
Inventory serializa consumo/fechamento pela mesma operação original, mesmo sem
linha prévia. CLOSED impede baixa tardia. REVERSED preserva o movimento original
e comprova o inverso. Falta de saldo é recusa durável. Outro pedido com a mesma
chave é conflito.

CT-11 mantém um contexto de Billing por OS, fechado inclusive em NO_BUDGET. Criação
posterior é recusada. Billing verifica cancellationInProgress=true em CT-02A e
elegibilidade anterior ao início. O campo é obrigatório para tornar verificável
o bloqueio da OS durante a compensação. CT-09 valida a revisão e fecha o mesmo contexto.

CT-07 considera o conjunto financeiro. Eventos agendam verificação e a entrega
consulta novamente. Auth recebe apenas os campos que o login já usa pelo endpoint
interno protegido. Operações são autorizadas por token opaco por par de serviços.
