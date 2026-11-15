# Fase 3: Saga inicial, inbox e consumo SQS

Implementada em 08/11/2026 sobre o filesystem revisado da fase 2, sem merge,
commit Git ou alteração do índice. O pacote ativo permanece
`2.0.0-os-baseline.1`, `readyForOsImplementation=true`, `approvedContract=false`.
A aprovação conjunta dos participantes continua pendente; não bloqueia este recorte OS.

## Persistência e limites

- V3 cria `service_order_saga` (uma por OS) e `inbox_event` (PK global `event_id`).
  V1 e V2 são preservadas. O gateway grava a Saga `IN_DIAGNOSIS` na mesma transação
  da abertura; a OS comercial permanece `RECEIVED`.
- Backfill: ordens existentes em RECEIVED/IN_DIAGNOSIS sem orçamento/aprovação
  recebem IN_DIAGNOSIS. Os demais registros recebem MANUAL_INTERVENTION com
  motivo PHASE3_LEGACY_RECONCILIATION. Não se inventam etapas históricas.
- A ordem de locks é identidade do evento (advisory transacional PostgreSQL),
  OS (mesmo lock de escrita das ações HTTP) e Saga. PK/locks são do banco,
  não locks em memória. IDs diferentes da mesma OS também são serializados.
- Inbox, vínculo do orçamento, status comercial WAITING_APPROVAL e estado da Saga
  WAITING_APPROVAL são gravados no mesmo commit. O handler usa REQUIRES_NEW e
  timeout de 30 segundos; só retorna sucesso depois do commit real.
- `eventId` é comparado antes das guardas de domínio. Hash SHA-256 do JSON
  canônico inclui campos opcionais, ordena propriedades, preserva ordem de arrays
  e normaliza números. Mesmo conteúdo: DUPLICATE. Mesmo ID com outro conteúdo:
  EVENT_ID_COLLISION, sem substituir o registro original nem confirmar a mensagem.
- Versão inferior: OBSOLETE auditado. Mesmo vínculo com novo ID: IGNORED, sem
  regredir decisão. Mesma revisão com vínculo divergente exige reconciliação.
  Ordens terminais não reabrem. Conflito ou dependência ausente faz rollback.
- Identidades da inbox não são expurgadas nesta fase; preservar no mínimo a
  janela contratual de replay de 30 dias.

## Contratos e transporte

Os schemas do catálogo ativo são empacotados no JAR diretamente de
`doc/contracts/saga`, sem cópias mantidas à mão. Validação JSON Schema 2020-12,
assertions de formato e referências locais, sem resolução remota. Tipos e versões
desconhecidos, datas inválidas, UUIDs inválidos e JSON com chaves repetidas são recusados.
Campos aditivos são aceitos. O pacote é versão 2.0.0-os-baseline.1, mas o envelope
ativo usa `schemaVersion=1`; são conceitos distintos.

Somente OrcamentoEnviado possui handler nesta fase. Os demais tipos ativos são
validados, porém ficam sem ACK por HANDLER_NOT_AVAILABLE_PHASE3, não como APPLIED.
Por isso o modo compartilhado é recusado na inicialização; a máquina completa,
CT-10/11, consumo REST e compensações entram nas fases seguintes.

Configuração por ambiente (processo Java/Kubernetes; usar Secret/IAM para credenciais):

| Variável | Valor inicial / uso |
| --- | --- |
| BILLING_SQS_ENABLED | false; habilitar explicitamente |
| BILLING_SQS_MODE | disabled; somente dedicated é aceito nesta fase |
| BILLING_SQS_QUEUE_URL | URL da fila dedicada de teste |
| AWS_REGION | us-east-1 por padrão |
| BILLING_SQS_ENDPOINT | vazio; opcional para emulador |

O cliente usa a cadeia padrão de credenciais do AWS SDK, inclusive session token
quando aplicável. Nunca loga corpo, credenciais ou receipt handle.
Long polling 20s, visibilidade 60s, uma mensagem por poll, renovação a cada 20s.
Falha de renovação impede ACK. DeleteMessage só ocorre após retorno confirmado
do handler; falha de ACK deixa a próxima entrega deduplicar o efeito já persistido.
Logs incluem eventId, correlationId, serviceOrderId/sagaId, desfecho ou motivo
sanitizado. Métricas: os.billing.processed (inclui DUPLICATE), os.billing.failure
e os.billing.event.delay.seconds, sem IDs como tags de alta cardinalidade.

Provisionamento continua com a plataforma: fila Standard, SSE-SQS, retenção
345600s, long polling 20s, visibilidade 60s, RedrivePolicy maxReceiveCount=5;
DLQ Standard com retenção 1209600s. O consumidor confere os atributos da fila
principal e o vínculo da DLQ ao iniciar; criptografia, retenção da DLQ e permissões
devem ser verificadas pela plataforma. Permissões usadas: ReceiveMessage,
ChangeMessageVisibility, DeleteMessage e GetQueueAttributes.
Inválidos, colisões e falhas persistentes não são apagados nem enviados manualmente
à DLQ: SQS aplica a RedrivePolicy. Redrive operacional preserva corpo/eventId e
exige causa corrigida, operador, motivo e taxa controlada. Não houve deploy AWS.
A outbox publicadora pertence ao Billing e não foi implementada na OS.

## Evidências reproduzíveis

Transporte dos testes: **mock do cliente AWS SDK SQS**, não emulador nem fila AWS.
Produtor: fixtures JSON de `v2/examples`, com a identidade da OS de teste ajustada.
Banco: **PostgreSQL 17.9 real, postgres:17-alpine via Testcontainers/Docker**.
Billing real indisponível/não integrado neste recorte. A transferência efetiva
para DLQ pela AWS e o redrive operacional continuam como aceite de integração.

```powershell
.\mvnw.cmd -B -o clean verify
# Com as dependências do lock já instaladas no Python selecionado:
python scripts/validate_contracts.py
```

Verificação final: 109 testes unitários + 44 testes de integração, sem falhas,
erros ou skips. Cobertura JaCoCo: 1433/1531 linhas (93,60%), gate de 80% aprovado.
Contratos: 21 verificações offline aprovadas, incluindo proibição efetiva de rede.

`BillingInboxIT` demonstra abertura com Saga, rollback da abertura, aplicação e
ACK após commit, duplicata, colisão de ID, falha adiada no commit com rollback
de inbox/OS/Saga, reentrega após commit e ACK perdido, evento obsoleto,
handler indisponível, concorrência de IDs iguais/diferentes e proteção terminal.
`BillingEventCodecTest` verifica todas as fixtures válidas e inválidas do pacote.
`SqsBillingConsumerTest` verifica parâmetros do poll e ausência de delete nas
cinco entregas inválidas simuladas, deixando redrive a cargo do SQS.

Relatórios locais regeneráveis: [Maven](../../phase3-verify.log),
[integração](../../target/failsafe-reports/br.com.fiap.numberone.serviceorder.messaging.BillingInboxIT.txt),
[cobertura](../../target/site/jacoco/index.html),
[contratos](../../target/phase3-contract-validation.log).

O diff preparado no índice mantém a assinatura inicial
`bded155ce36c25aadb1d9b3cd099b05aee0918f9`. Alterações desta fase ficaram apenas
no filesystem; os arquivos preparados e as alterações anteriores não foram descartados.
