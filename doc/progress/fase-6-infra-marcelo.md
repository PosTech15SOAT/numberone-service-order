# Fase 6 · Configuração para Marcelo

Provisionar a tabela no repositório `postech15soat-infra-database` e entregar
nome, ARN e região à OS. A configuração abaixo é a solicitação de IaC para
revisão de plataforma.

| Propriedade | Valor |
| --- | --- |
| Nome | `<ambiente>_vehicle_maintenance_history`, por exemplo `production_vehicle_maintenance_history` |
| Chave de partição | `vehicleId`, String, UUID |
| Chave de ordenação | `historyKey`, String, `completedAt#serviceOrderId#historyVersion` |
| Capacidade | On-demand (`PAY_PER_REQUEST`) |
| Produção | Criptografia em repouso, PITR e proteção contra exclusão |
| TTL, índices secundários, Streams | Desabilitados |
| Tags | Ambiente, serviço `numberone-service-order` e convenções de plataforma |

O instante da chave é UTC com seis casas decimais. A versão inicial é 1. Além
das chaves, cada documento contém `serviceOrderId`, `branchId`,
`historyVersion` numérico e `snapshot` com JSON em String. A OS escreve com
`attribute_not_exists(vehicleId)`. Na rejeição condicional, lê o documento com
consistência forte e compara identidade, versão e conteúdo inteiro. A consulta
faz `Query` pela PK, em ordem decrescente, com filtro de filial.
[PutItem](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_PutItem.html),
[Query](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_Query.html).

Permissões da identidade do workload da OS, limitadas à tabela no ambiente:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["dynamodb:PutItem", "dynamodb:GetItem", "dynamodb:Query"],
    "Resource": "arn:aws:dynamodb:<regiao>:<conta>:table/<ambiente>_vehicle_maintenance_history"
  }]
}
```

Usar a cadeia padrão de credenciais do SDK. Marcelo valida o mecanismo de
identidade do workload disponível na conta, incluindo session token quando
usar credenciais temporárias. Permitir HTTPS até o endpoint regional do
DynamoDB. No deployment da OS, fornecer pelo ConfigMap de runtime:

| Variável | Valor |
| --- | --- |
| `HISTORY_DYNAMODB_ENABLED` | `true` após provisionar tabela, identidade e rede |
| `HISTORY_DYNAMODB_TABLE` | Nome da tabela do ambiente |
| `AWS_REGION` | Região da tabela |
| `HISTORY_WORKER_ENABLED` | `true`, padrão |
| `HISTORY_POLL_DELAY_MS` | `1000`, padrão |

`HISTORY_DYNAMODB_ENDPOINT` fica ausente na AWS. É reservado ao emulador.
Com a projeção desabilitada, a conclusão já acumula snapshots e pendências no
PostgreSQL. A leitura do histórico responde 503 até habilitar a integração.
Liveness e readiness continuam independentes do DynamoDB. A fila continua
Billing → OS. O worker do histórico grava diretamente na tabela.

No Gateway, rotear `GET /api/public/veiculos/{vehicleId}/historico-manutencoes`
para a OS pelo caminho autenticado existente, com sobrescrita dos headers de
identidade. A OS exige ADMIN ou a permissão existente `SERVICE_ORDER_TRACK_OWN`;
para cliente, também exige titularidade do veículo. A filial vem de `OS_BRANCH_ID`
do deployment. A leitura deve ficar fora de caches compartilhados.

No Datadog, acompanhar `os.history.pending`,
`os.history.oldest.pending.seconds`, `os.history.retries` e
`os.history.collisions` com os tags de serviço e ambiente existentes.
`os.history.projected` distingue `WRITTEN` de `REPLAYED`. Usar o backlog e
a idade como sinais por banco/ambiente, evitando somar gauges repetidos por
réplica. Colisão requer investigação pelo `orderId` e pela versão.
