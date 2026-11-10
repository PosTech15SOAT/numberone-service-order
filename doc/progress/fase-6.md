# Fase 6 · Histórico de manutenção por veículo

Implementação no checkout dedicado da OS, sobre `d1a2f81`. A conclusão técnica
preserva as guardas do fluxo validado na fase 4. A migration V6 adiciona a outbox
e o índice de consulta por veículo e filial.

## Conclusão e leitura

`PATCH /api/admin/ordens-servico/{id}/concluir` mantém o uso sem corpo e aceita
opcionalmente `{"quilometragem":63542}`. O valor é um inteiro não negativo em km.
A mesma transação que grava OS `COMPLETED` e Saga `WAITING_PAYMENT` insere a
versão 1 do snapshot com estado `PENDING`. O snapshot congela OS, veículo,
filial, instante da conclusão, quilometragem quando informada, serviços
concluídos e materiais com consumo confirmado. Inclui quantidades consumidas
e referências de operação e movimentos. O trigger impede alteração ou exclusão
do snapshot, inclusive depois da projeção.

`GET /api/public/veiculos/{vehicleId}/historico-manutencoes?limit=20` retorna
`entries` e `nextCursor`. A ordenação começa pela conclusão mais recente.
Passe `nextCursor` como `cursor` na próxima requisição e encerre em cursor
nulo. O limite vai de 1 a 50. A consulta exige usuário ativo com perfil ADMIN
ou permissão `SERVICE_ORDER_TRACK_OWN` e titularidade do veículo. O PostgreSQL
valida o vínculo do veículo com a filial do deployment a cada página. O
DynamoDB filtra pela mesma filial. O cursor só usa a chave de uma revisão
visível naquela filial.

| Resposta | Significado |
| --- | --- |
| 200 | Revisões já publicadas, possivelmente nenhuma enquanto o worker publica |
| 400 | Parâmetro inválido ou cursor de outro veículo/filial |
| 401/403 | Identidade ausente, permissão ausente ou cliente sem titularidade |
| 404 | Veículo sem vínculo com a filial do deployment ou inexistente |
| 503 | Integração desabilitada ou DynamoDB indisponível |

## Projeção e retomada

O worker reivindica uma linha elegível por ciclo com `FOR UPDATE SKIP LOCKED`
e lease de 30 segundos. O `PutItem` acontece depois do commit da reivindicação.
O ACK grava `PROJECTED` com o identificador da lease. Uma falha do SDK agenda
nova tentativa de 5 a 300 segundos, conforme a contagem. Uma interrupção
antes do ACK deixa a lease expirar e a mesma chave voltar a ser elegível.
O documento existente com conteúdo idêntico confirma a publicação. Conteúdo
diferente para a mesma chave mantém o documento anterior e muda a outbox
para `COLLISION`, com log e métrica.

Para revisar uma projeção, consulte `maintenance_history_outbox` pelo
`service_order_id` e `history_version`. Compare a chave e o snapshot com o
item do DynamoDB quando houver colisão. Após resolver a causa, reagende
somente o snapshot afetado com acesso operacional ao PostgreSQL:

```powershell
psql -v order_id=UUID_DA_OS -v history_version=1 -f scripts/reprocess-maintenance-history.sql
```

O script retorna `matched`, `requeued`, `in_flight`, `already_due` e
`previous_collisions`. Respeita a lease ativa, preserva tentativas acumuladas
e reaproveita a chave e o payload originais. Uma correção de negócio exige
versão nova e explícita em fluxo próprio. A V6 inicia o histórico para novas
conclusões. Backfill de ordens legadas depende de dados e escopo próprios.

[Configuração de tabela, IAM, Gateway e métricas para Marcelo](fase-6-infra-marcelo.md).

## Reprodução local

```powershell
# No checkout da OS, com JDK 25+ e Docker disponível
.\mvnw.cmd -B clean verify
# Para repetir apenas a integração do fluxo principal e histórico
.\mvnw.cmd -B "-Dit.test=SagaMainFlowIT" "-Djacoco.skip=true" verify
```

`SagaMainFlowIT` sobe PostgreSQL 17.9 e DynamoDB Local 2.6.1 pelo
Testcontainers e usa a API HTTP da OS e o SDK DynamoDB. Billing e Inventory
são simuladores HTTP com fixtures contratuais. Os testes acionam o mesmo
worker de produção de forma determinística e avançam os horários da outbox
para testar retomada sem esperar o lease ou o backoff em tempo real.

| Cenário | Resultado observado |
| --- | --- |
| Rollback no commit de conclusão | OS `IN_PROGRESS`, Saga `IN_EXECUTION`, nenhuma linha de histórico |
| DynamoDB Local pausado | OS conclui, pagamento é confirmado pelo simulador de Billing e a entrega passa com CT-07. A outbox segue pendente e é publicada quando o DynamoDB volta |
| ACK SQL falha após `PutItem` | Documento já existe. Após expiração da lease, o retry confirma o mesmo conteúdo e a consulta retorna uma entrada |
| Conteúdo divergente na mesma chave | `COLLISION` e métrica, documento existente preservado |
| Consulta autenticada | Duas páginas em ordem, filtro de filial, acesso negado a outro cliente ou a usuário sem permissão |

A execução local de 08/11/2026 passou com 199 testes Maven, sendo 113 unitários e
86 de integração, sem falhas, erros ou skips. O JaCoCo mediu 93,55% de cobertura
de linhas (2.177 de 2.327) e aprovou o gate de 80%. A regressão offline dos
contratos anteriores passou nas 21 verificações.

[Log Maven](../../phase6-verify.log) ·
[Resultado de SagaMainFlowIT](../../target/failsafe-reports/br.com.fiap.numberone.serviceorder.saga.SagaMainFlowIT.txt) ·
[Cobertura JaCoCo](../../target/site/jacoco/index.html).

O provisionamento e a verificação na AWS ficam com Marcelo. A revisão do
conteúdo e da política de reprocessamento fica com Matheus.
