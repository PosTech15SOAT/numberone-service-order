# Estados comerciais, técnicos e compensação

Uma Saga por serviceOrderId. Estado comercial não é estado técnico de transporte.
Deduplicação vem antes das guardas; revisão/identidade/composição e cancelamento
precisam ser revalidados também ao aplicar respostas REST tardias.

| Comercial interno | HTTP legado | Significado |
| --- | --- | --- |
| RECEIVED | RECEBIDA | Abertura |
| IN_DIAGNOSIS | EM_DIAGNOSTICO | Diagnóstico técnico |
| WAITING_APPROVAL | AGUARDANDO_APROVACAO | Revisão elegível disponível |
| APPROVED | APROVADA | Aprovação vigente aceita e escopo congelado |
| REJECTED | REJEITADA | Revisão rejeitada |
| IN_PROGRESS | EM_EXECUCAO | Primeiro início confirmado |
| COMPLETED | FINALIZADA | Reparo concluído, pagamento ainda pode faltar |
| CANCELLED | CANCELADA | Encerramento autorizado após compensação aplicável |
| DELIVERED | ENTREGUE | Entrega registrada após quitação |

Itens mantêm PENDING, WAITING_FOR_PARTS_AND_SUPPLIES, IN_PROGRESS, COMPLETED e CANCELLED;
HTTP mantém PENDENTE, AGUARDANDO_PECAS_E_INSUMOS, EM_EXECUCAO, FINALIZADO e CANCELADO.
Tentativa do item pode estar START_PENDING/AWAITING_RESULT sem promovê-lo a IN_PROGRESS.

| Saga atual | Entrada e guarda | Próximo estado / efeito |
| --- | --- | --- |
| IN_DIAGNOSIS | Abertura, mesmo com OS RECEIVED | Aguarda diagnóstico; EV-01 vigente → WAITING_APPROVAL |
| WAITING_APPROVAL | EV-02 vigente com vínculo aprovado | READY_FOR_EXECUTION, OS APPROVED; sem exigir pagamento |
| WAITING_APPROVAL | EV-02B vigente | WAITING_REVISION, OS REJECTED |
| WAITING_REVISION | EV-01 da revisão seguinte elegível | WAITING_APPROVAL |
| READY_FOR_EXECUTION | Início do primeiro item, materiais presentes | Persistir intenção CT-05, START_PENDING; item não iniciado |
| READY_FOR_EXECUTION | Início sem materiais e guardas válidas | IN_EXECUTION por commit local; sem consumo remoto vazio |
| START_PENDING | CT-05/08 CONSUMED, versão válida e sem compensação | IN_EXECUTION, item/OS em execução |
| START_PENDING | REJECTED / INSUFFICIENT_STOCK confirmado | READY_FOR_EXECUTION, item aguarda peças; nova ação após reposição |
| START_PENDING | Timeout, PENDING, UNKNOWN, 503/504 | Mantém pendente e operationId; consultar/repetir, sem uso físico |
| IN_EXECUTION | Início de outro item | Registrar tentativa por item; Saga global continua IN_EXECUTION |
| IN_EXECUTION | Todos os itens COMPLETED/CANCELLED, sem consumo pendente e conclusão elegível | WAITING_PAYMENT, OS COMPLETED; projetar histórico localmente |
| WAITING_PAYMENT | CT-07 settled=true válido | READY_FOR_DELIVERY (indicação antecipada proposta) |
| READY_FOR_DELIVERY | CT-07 settled=false confirmado antes da entrega | WAITING_PAYMENT |
| WAITING_PAYMENT ou READY_FOR_DELIVERY | Ação entrega, CT-07 true e guardas locais novamente válidas | OS DELIVERED, Saga COMPLETED no mesmo commit |
| WAITING_PAYMENT ou READY_FOR_DELIVERY | Recusa/timeout financeiro | Entrega bloqueada; timeout não inventa settled=false |
| Estado elegível anterior ao primeiro início confirmado | Cancelamento integral autorizado e não uso conferido | COMPENSATING, bloquear novos inícios |
| COMPENSATING | Etapas C1–C6 aplicáveis confirmadas | COMPENSATED, OS CANCELLED |
| Qualquer etapa não terminal | Conflito/efeito físico ou financeiro sem solução automática | MANUAL_INTERVENTION com motivo e etapa de retorno |
| COMPLETED ou COMPENSATED | Eventos de orçamento tardios | Não reabrir; registrar desfecho/diagnóstico |

SENT atrasado após decisão da mesma revisão não regride. Revisão inferior é obsoleta.
Decisão sem contexto fica pendente. Decisões divergentes exigem reconciliação.
Eventos PAID/REFUNDED só atualizam projeção e agendam CT-07; não iniciam serviço,
não entregam veículo e não substituem guarda de quitação.

## C1–C6

1. **C1 OS:** registrar autor, escopo e motivo; serializar cancelamento com início,
   entrar COMPENSATING e bloquear novas tentativas/retentativas CT-05.
2. **C2 OS → Inventory CT-08:** descobrir cada consumo. CONSUMED precisa resolver
   efeito. REJECTED, REVERSED ou CLOSED resolvem estoque com evidência durável.
   UNKNOWN/404 pode ser requisição em trânsito; não concluir por ausência.
3. **C3 OS → Inventory CT-08B:** para tentativa ainda não resolvida, persistir
   compensationId e evidência; obter CLOSED sem baixa ou REVERSED com inverso.
   Resposta tardia CONSUMED registra fato, mas não inicia item durante compensação.
4. **C4 OS → Billing CT-09:** só após todo estoque resolvido, persistir intenção
   de cancelamento do orçamento/revisão aplicável. Se inexistente, dispensar com
   motivo verificado, não por timeout de consulta.
5. **C5 Billing/OS:** confirmar CANCELLED com a operação original. Revisão divergente,
   inelegibilidade ou efeito financeiro pendente não satisfaz esta etapa.
6. **C6 OS:** validar todos os efeitos/dispensas, registrar CANCELLED e COMPENSATED.

Janela automática proposta: cancelamento **integral**, antes do primeiro início
confirmado e sem uso físico. Execução iniciada, parcial ou peças utilizadas exigem
revisão operacional; estoque só volta com devolução válida. Política de todos os
itens cancelados/retirada não foi fechada. Pagamento recusado após reparo mantém
OS COMPLETED e bloqueia entrega, sem reverter trabalho realizado.

CT-07 e commit de entrega não são atômicos com estorno Billing. A política proposta
coordena manualmente estorno da cobrança que quita a OS; protocolo adicional, se
exigido, precisa de acordo. Evento financeiro após entrega mantém registro da
entrega e gera ocorrência auditada, sem apagar história.
