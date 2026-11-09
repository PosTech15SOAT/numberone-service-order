# Fase 5 — compensação e recuperação

Implementação de 08/11/2026 no checkout `tmp/fase-2/numberone-service-order`.
Recorte C1–C6 de `doc/fase-4/implementacao-os/fase-5-compensacao-recuperacao.md`
do workspace, sobre a fase 4 existente. Contratos de fio:
`2.0.0-os-baseline.1`, CT-05/08/08B/09/11, sem alterar o pacote aprovado para
implementação nem inferir aceite dos serviços externos.

## Entrega

| Etapa | Implementação e evidência durável |
| --- | --- |
| C1 | `SagaCompensation.request` bloqueia a mesma OS que a confirmação de início. Aceita automaticamente apenas FULL, sem primeiro início confirmado, com não uso e devolução conferida se houve saída física. Persiste `COMPENSATING`, autor autenticado, motivo, instante, referência da conferência, composição afetada e pedidos CT-08B imutáveis no mesmo commit. |
| C2 | CT-08 consulta cada `operationId` original, incluindo tentativas recusadas e em trânsito. Timeout, UNKNOWN e PENDING não são prova de ausência de efeito. REJECTED confirmado dispensa movimento inverso; a corrida `OPERATION_ALREADY_REJECTED` exige nova consulta confirmatória. |
| C3 | CT-08B usa `compensationId` persistido. CLOSED fecha tentativa ainda sem baixa; REVERSED confirma movimento inverso. Resposta perdida é recuperada por CT-08 ou repetição da mesma chave. IDs divergentes exigem revisão. Todas as tentativas devem estar resolvidas antes do Billing. |
| C4–C5 | Persiste uma operação comercial após C3. CT-09 usa orçamento/revisão conhecidos; CT-11 fecha o contexto por OS quando o orçamento não é conhecido. NO_BUDGET só é conclusivo em resposta válida de CT-11; 404 permanece pendente. HTTP 200 sem identidades, revisão ou resultado corretos também permanece pendente. |
| C6 | Operação comercial SUCCEEDED, OS/itens CANCELLED e Saga COMPENSATED são gravados em transação local. Snapshot aprovado permanece imutável. O trigger de composição permite somente a transição operacional de C6 com efeitos resolvidos; a migração não altera V1–V4. |

O bloqueio impede início, retentativa de consumo, conclusão e edição da composição.
Uma chamada que já estava em trânsito pode chegar ao Inventory após C1: CT-08B
resolve a corrida pela identidade original. Resposta tardia fica em `late_result`
e não reabre operação, não inicia item e não regride COMPENSATED.

Operações originais resolvidas pelo cancelamento ficam SKIPPED, ou conservam
REJECTED quando já havia recusa local; o resultado terminal do estoque está na
operação RESOLVE_STOCK vinculada por `original_operation_id`. Não se apaga a
tentativa nem se inventa confirmação de início.

## Recuperação e revisão

`SagaRecoveryWorker` varre até 50 registros de cada classe por ciclo. O padrão é
5 segundos (`SAGA_RECOVERY_DELAY_MS`), habilitado por `SAGA_RECOVERY_ENABLED=true`.
As chamadas HTTP acontecem fora das transações. Leases de 30 segundos com UUID de
posse usam PostgreSQL; a aplicação de resultado revalida a posse. Reinício aguarda
expiração da posse anterior e retoma com o pedido/chave já persistidos.

O worker avança uma etapa remota de compensação por OS/ciclo. Falha técnica mantém
AWAITING_RESULT e agenda a próxima tentativa em 5 segundos. Inícios ativos usam
lease por operação e espera crescente de 5 a 60 segundos. Na recuperação normal,
CT-08 precede qualquer retry CT-05; CONSUMED confirma o início apenas com guardas
vigentes, UNKNOWN permite repetir a identidade ainda ativa, PENDING aguarda e
CLOSED/REVERSED inesperados encaminham para revisão. Durante cancelamento não se
retenta CT-05. O estado durável, e não uma fila em memória, dirige a retomada.

Cancelamento parcial, início confirmado, material usado, devolução não conferida,
legado sem reconciliação ou conflito de negócio ficam MANUAL_INTERVENTION, com
`return_state` e motivo. Não há retorno automático ao estoque nesses casos.
Conflito em cancelamento originalmente elegível pode ser retentado pela rota
auditada após resolução externa; ela preserva o pedido original e volta a validar
a janela. Não é um mecanismo para autorizar uso físico ou cancelamento parcial.

Rejeição de orçamento permanece WAITING_REVISION. A recuperação de CT-07 mantém
OS COMPLETED e entrega bloqueada enquanto a quitação for negativa; falha técnica
mantém a verificação pendente. Estorno, cobrança excedente e reconciliação do
provedor continuam no Billing.

## API

As rotas exigem ADMIN, identidade autenticada, correlação e filial, como as demais
rotas administrativas. Contrato local em [os-phase5.openapi.yaml](../contracts/os-phase5.openapi.yaml).

- `PATCH /api/admin/ordens-servico/{id}/cancelar`: corpo de conferência obrigatório;
  202 em COMPENSATING, 409 com revisão durável, 200 quando a repetição exata já
  encontra COMPENSATED. A repetição preserva a conferência original, inclusive
  `verifiedAt`; conteúdo diferente recebe CANCELLATION_REQUEST_CONFLICT.
- `PATCH /api/admin/itens-ordem-servico/{id}/cancelar`: registra escopo PARTIAL e
  identificação do item para revisão operacional.
- `GET /api/admin/ordens-servico/{id}/compensacao`: estado, motivo, etapa de retorno,
  auditoria, decisões de revisão e operações com resultados JSON.
- `POST /api/admin/ordens-servico/{id}/compensacao/retomar`: motivo da decisão
  obrigatório; apenas conflitos de cancelamento inicialmente elegível, com
  revalidação. Autor e instante da decisão são auditados.

A antiga chamada sem corpo que cancelava apenas localmente deixa de ser válida.
Isso evita encerrar a OS enquanto criação de orçamento ou baixa ainda está em
trânsito. A composição pode continuar sendo editada no diagnóstico pelas rotas
de edição; cancelamento parcial pela rota de cancelamento exige revisão.

## Evidências e reprodução

```powershell
# No checkout, JDK 25+ e Docker disponíveis:
.\mvnw.cmd -B -o clean verify *> phase5-verify.log
# Ambiente Python com scripts/requirements-contracts.lock:
python scripts/validate_contracts.py *> phase5-contracts.log
python scripts/validate_phase4_trace.py > phase5-mainflow-trace.log
python scripts/validate_phase5_trace.py > phase5-trace-validation.log
python scripts/record_phase5_evidence.py
```

O modo offline Maven pressupõe dependências já em cache. A suíte usa PostgreSQL
17.9 real via Testcontainers, HTTP/Tomcat real e clientes HTTP reais. Inventory e
Billing são fixtures HTTP com registros de operação no PostgreSQL descartável.
As tabelas `fixture_*` existem somente nos testes, não nas migrations da OS.

| Cenário | Saldo observado | Resultado |
| --- | --- | --- |
| CT-08B vence CT-05 atrasado | 5 → 5 → 5 | CLOSED durável, consumo tardio recusado, nenhum movimento |
| Baixa vence e resposta chega após C6 | 5 → 4 → 5 | REVERSED, um débito e um inverso, item nunca iniciado |
| Timeout real depois da baixa e reversão repetida | 5 → 4 → 5 → 5 | Mesmo operationId/compensationId, um único movimento inverso vinculado ao original |
| Reinício entre C3 e CT-09, resposta comercial perdida | 5 → 5 | Dois novos processos JVM recuperam do banco; uma operação comercial; COMPENSATED somente após confirmação |
| Devolução física não conferida | 5 → 5 | MANUAL_INTERVENTION, nenhum participante chamado |
| Primeiro início já confirmado | 5 → 4 → 4 | MANUAL_INTERVENTION, nenhuma restituição automática |

Os testes também cobrem múltiplas tentativas afetadas, recusa de saldo, UNKNOWN
com CT-08B indisponível, PENDING, 404 do Billing, CT-11/NO_BUDGET, concorrência e
expiração de lease, pedido imutável, escopo parcial, uso físico, revisão auditada,
rollback de C1/C6, autorização e validação de respostas malformadas.

`RecoveryProcess` inicia outro processo Java com um contexto Spring novo e a mesma
base descartável após C3. O processo principal do teste mantém apenas os servidores
simulados, com seu worker automático desabilitado. Não há estado de recuperação
compartilhado em memória entre as JVMs. Os logs desses processos são gravados em
`target/recovery-process-*.log`.

Resultados: [resumo da execução](evidence/fase-5.json), [operações e movimentos
capturados](evidence/fase-5-scenarios.json). Relatórios completos regeneráveis:
`phase5-verify.log`, `target/surefire-reports`, `target/failsafe-reports`,
`target/site/jacoco/index.html`, `target/phase5-contract-trace.json` e
`target/phase5-evidence.json`.

[Roteiro operacional de retomada](runbook-fase-5.md).

## Limites do recorte

Sem fase 6, DynamoDB, fila AWS real, deploy, commit ou push. O índice preexistente
foi preservado (`bded155ce36c25aadb1d9b3cd099b05aee0918f9`). A validação comprova
o protocolo da OS contra fixtures; a serialização CT-05/08B no Inventory e o
fechamento do contexto/elegibilidade no Billing reais continuam a ser verificados
na fase 7. Não foi alterado `approvedContract=false` para simular aceite externo.
