# Retomada e revisão operacional — fase 5

## Solicitar e acompanhar

Usar as rotas administrativas pela identidade/Gateway autorizados. Não enviar
identidade do conferente no corpo: o servidor a obtém do funcionário autenticado.

```http
PATCH /api/admin/ordens-servico/{serviceOrderId}/cancelar
Content-Type: application/json
X-Correlation-Id: <UUID>

{
  "scope": "FULL",
  "reason": "Cliente solicitou cancelamento integral antes do inicio",
  "unusedConfirmed": true,
  "physicallyRemoved": false,
  "returnConfirmed": false,
  "verifiedAt": "2026-11-08T20:00:00Z",
  "evidenceReference": "conferencia-operacional/registro-123"
}
```

O instante acima é ilustrativo: registrar o instante real da conferência. Se a
peça saiu fisicamente, informar `physicallyRemoved=true` e só informar
`returnConfirmed=true` depois da conferência da devolução. Uso efetivo ou ausência
dessa conferência exige revisão; não ajustar o payload apenas para passar na guarda.

202 significa cancelamento aceito e efeitos ainda em resolução. Consultar
`GET /api/admin/ordens-servico/{serviceOrderId}/compensacao` até COMPENSATED ou
MANUAL_INTERVENTION. Repetir a solicitação mantém o corpo original inteiro.

## Depois de timeout ou reinício

1. Verificar conectividade/autenticação com Inventory e Billing e saúde do banco.
   Manter `SAGA_RECOVERY_ENABLED=true`. O worker não depende de reenviar a chamada
   do usuário nem de receber um novo evento SQS.
2. Aguardar a posse anterior expirar (30 s) e a próxima tentativa. Não criar novas
   chaves, apagar operações ou liberar início manualmente. A varredura padrão é
   a cada 5 s, com lote de até 50 registros por classe.
3. Em COMPENSATING, confirmar RESOLVE_STOCK para **todas** as operações originais.
   CONSUMED exige resolução; CLOSED/REVERSED confirmados ou REJECTED terminal
   resolvem. UNKNOWN/202/404/timeout não encerram a etapa. O worker usa CT-08 e,
   quando necessário, CT-08B; nunca repete CT-05 durante o cancelamento.
4. Depois do estoque, localizar CANCEL_BUDGET ou CLOSE_BUDGET_CONTEXT. A operação
   usa sempre o pedido persistido. Resposta perdida de CT-09/11 é recuperada
   repetindo o POST com a mesma identidade. Um 404 isolado não dispensa Billing.
5. Conferir C6: OS/itens CANCELLED, Saga COMPENSATED e operação comercial
   SUCCEEDED. Falha de commit em C6 deixa a Saga pendente; a operação comercial
   idempotente permite nova confirmação sem novo efeito.

Fora do cancelamento, início ativo usa CT-08 para recuperar a baixa. UNKNOWN
permite retry CT-05 com o mesmo operationId e guardas atuais. Recusa de saldo
exige nova ação explícita após reposição; não fabricar nova tentativa no worker.
Rejeição de orçamento aguarda nova revisão. Pagamento não quitado após reparo
preserva COMPLETED e impede entrega; eventual estorno é tratado pelo Billing.

## Inspeção somente leitura

Executar na base da OS autorizada, parametrizando `:serviceOrderId` no cliente SQL:

```sql
SELECT o.id, o.status, s.state, s.version, s.return_state, s.reason,
       s.cancellation_in_progress, s.cancellation, s.review_decisions,
       s.recovery_owner, s.recovery_until, s.next_attempt_at
FROM ordem_servico o JOIN service_order_saga s ON s.service_order_id=o.id
WHERE o.id=:serviceOrderId;

SELECT operation_id, original_operation_id, item_id, budget_id, budget_revision, intent, state,
       request, result, late_result, reason, attempts, next_attempt_at,
       lease_owner, lease_until
FROM saga_operation WHERE service_order_id=:serviceOrderId
ORDER BY created_at, operation_id;
```

A OS guarda referências de movimentos, não o saldo do Inventory. Conferir o saldo
e o movimento inverso na auditoria do Inventory usando o operationId original e
compensationId. Nos testes, `target/phase5-evidence.json` inclui saldo, operações e
movimentos da fixture, com `original_movement` no movimento inverso.

## Acionar revisão

Consultar estado, `reason`, `returnState`, conferência e resultados. Encaminhar ao
responsável operacional os IDs da OS/operação/compensação, o motivo, a evidência
de não uso/devolução e o fato retornado pelo participante. Não incluir tokens ou
dados pessoais desnecessários. Esta implementação registra a necessidade de
revisão; não envia mensagens nem abre chamados externos automaticamente.

- OUTSIDE_AUTOMATIC_WINDOW: cancelamento parcial, início confirmado, uso físico,
  devolução ausente ou legado não reconciliado. Exige decisão humana específica;
  a rota de retomada não converte esse caso em cancelamento automático.
- MATERIAL_USE_NOT_RESOLVED/EVIDENCE_REQUIRED: validar a situação física com o
  Inventory. Restituição só depois da devolução conferida.
- BUDGET_VERSION_CONFLICT/BUDGET_NOT_ELIGIBLE/FINANCIAL_EFFECT_PENDING: resolver
  com Billing. A OS não altera revisão/pedido para contornar o conflito e não
  executa estorno.
- IDEMPOTENCY_CONFLICT/COMPENSATION_ID_CONFLICT/UNEXPECTED_STOCK_CLOSURE/
  LOCAL_GUARDS_CHANGED: reconciliar o registro original e sua identidade. Não
  remover a operação nem trocar a chave.

Após resolver externamente um conflito de cancelamento originalmente elegível,
um ADMIN registra a decisão:

```http
POST /api/admin/ordens-servico/{serviceOrderId}/compensacao/retomar
Content-Type: application/json
X-Correlation-Id: <UUID>

{"reason":"Conflito resolvido no participante; referencia do registro operacional"}
```

A rota registra autor, instante, justificativa e motivo anterior; revalida a
janela e agenda o mesmo pedido. Um conflito ainda presente volta para revisão.
Ela não afirma que o efeito remoto ocorreu; só uma confirmação válida o faz.

## Reprocessar eventos

Manter o envelope, eventId e conteúdo originais no redrive da DLQ para a fila
Billing→OS configurada. Não inventar eventId para contornar deduplicação. A inbox
faz deduplicação antes das guardas, verifica colisão de conteúdo e só confirma a
mensagem após commit. Corrigir a causa do erro antes do redrive e conferir
`inbox_event` pelo eventId. Eventos de orçamento tardios não reabrem uma OS em
cancelamento; eventos financeiros continuam sendo projeções, sem autorizar entrega.
Falhas de operações REST são retomadas pelo worker, não por publicação de eventos
falsos de consumo/reversão.

## Demonstração local

```powershell
.\mvnw.cmd -B -o '-Dit.test=SagaCompensationIT' test-compile failsafe:integration-test failsafe:verify
python scripts/validate_phase5_trace.py
```

`freshWorkerResumesAfterStockAndBillingTimeoutWithSameOperation` resolve C3, inicia
um novo processo Java que recebe resposta comercial inconclusiva, e inicia outro
que recupera a mesma operação e conclui C6. Os testes de corrida e timeout deixam
evidência em `target/phase5-evidence.json`; os logs de cada JVM ficam em
`target/recovery-process-*.log`. A base descartável é removida pelo Testcontainers.
