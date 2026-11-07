# Cenários de revisão e testes futuros

O CI desta fase valida estrutura, referências, formato e fixtures. Esta tabela
registra comportamento esperado para implementação/testes nas fases 2–7; **não**
declara execução real da Saga, concorrência ou serviços integrados.

| Cenário | Resultado esperado | Artefato / fase posterior |
| --- | --- | --- |
| Aprovar, consumir, concluir, pagar, entregar | Aprovação habilita início; consumo habilita execução; CT-07 habilita entrega | Eventos e HTTP válidos; domínio e integração futuros |
| EV-02 duplicado e EV-01 atrasado | Uma aprovação, sem regressão | messages.md; inbox e persistência futuras |
| EV-02/02B antes de EV-01 | Sem contexto, pendente/retry; não APPLIED | Fonte de revisão DEC-02 pendente |
| Revisão antiga ou mesmo número com outro budgetId | Obsoleta é ignorada; identidade divergente exige reconciliação | Domínio futuro |
| Aprovação de composição antiga | Schema válido, negócio obsoleto; não consumir lista atual | proposals/stale-approval.json; coerência documental validada |
| Billing cai após Send / OS cai após commit | Mesmo eventId, efeito único, confirmação após commit | Outbox/inbox/transporte futuros |
| CT-05 falha por saldo | 409 REJECTED/INSUFFICIENT_STOCK, nenhum movimento; espera peças | http-examples/insufficient-stock*.json |
| CT-05 baixa e perde resposta | START_PENDING/AWAITING_RESULT; CT-08 recupera CONSUMED sem nova baixa | consumed, consume-timeout, tracking-timeout; concorrência futura |
| CT-08 ausente ou em andamento | UNKNOWN/PENDING não é sucesso de cancelamento | unknown.json, pending.json |
| CT-08B fecha antes do consumo | CLOSED durável, CT-05 tardio ATTEMPT_CLOSED; zero baixa | closed*.json, attempt_closed.json; corrida futura |
| CT-05 ganha antes da reversão | REVERSED só com evidência e movimento inverso único | reversed*.json, reversal-request.json |
| Retry CT-05 após reversão | ATTEMPT_REVERSED, não iniciar item | attempt_reversed.json |
| Mesma chave com conteúdo distinto | 409 IDEMPOTENCY_CONFLICT; sem efeito novo | idempotency_conflict.json; fingerprint futuro |
| Cancelamento e resposta CONSUMED tardia | Registrar consumo, manter início bloqueado; resolver estoque antes CT-09 | C1–C6 e recuperação futura |
| Falha entre C3/C4; retry CT-08B/09 | Recuperar resultados com IDs originais; C6 somente após confirmação | closed/reversed/cancelled; persistência futura |
| Peça utilizada/sem evidência | Não repor saldo fictício, revisão operacional | invalid-evidence, invalid-missing-evidence; política DEC-09 |
| EV-04 antes de EV-03 | Se EV-04 ativado, REFUNDED versão 2 prevalece sobre PAID versão 1 | Fixture condicional; dispatch atual rejeita EV-04 |
| Estorno somente de cobrança excedente | CT-07 pode continuar settled=true | Política Billing e integração futura |
| Pagamento recusado após reparo | OS COMPLETED, entrega bloqueada; não desfazer execução | unsettled.json, delivery-unsettled.json |
| Entrega com timeout Billing | Não ENTREGUE; verificação pendente | delivery-timeout.json |
| Evento financeiro atrasado, CT-07 positivo | Entrega pode consultar autoridade diretamente e revalidar guardas | settled.json, delivered.json |
| Estorno concorre com entrega | Janela explícita, coordenação/protocolo a decidir | DEC-13, não comprovado pelo schema |
| DynamoDB indisponível na conclusão | Histórico pendente, sem bloquear fluxo; retomada idempotente | DEC-17 e testes da fase 6 |

Exemplos inválidos cobrem ausência de campo, UUID, data impossível/não UTC,
tipo, valor/formato monetário, revisão e schemaVersion; HTTP cobre quantidade zero/
fracionária, consumo vazio, evidência ausente, CLOSED com baixa, REVERSED sem
compensationId, recusa com baixa parcial, quitação incoerente e início prematuro.
