> Fase 4: o deploy automático e o manual não usam GitHub Environment; configurações e pré-requisitos estão no procedimento `docs/phase4.md` do repositório de infraestrutura Cloud. Referências abaixo ao Environment são históricas.

# Docker, Kubernetes e CI/CD do OS

A imagem usa Java 25 e executa com UID/GID 10001. O CI empacota o JAR após `mvnw clean verify`, aplica o limite de 80% de cobertura de linhas e testa a imagem contra um PostgreSQL descartável. O CI confere o conteúdo dos contratos Saga dentro do JAR da imagem e valida os contratos separadamente. O build padrão do Docker e o Compose continuam compilando a partir das fontes. O CI e o CD usam o alvo `verified`.

## Validação local

Em um shell Bash com Docker, kubectl, jq e Java 25 e Python 3:

```bash
./mvnw --batch-mode clean verify
docker build --target verified -t numberone-os:local .
bash scripts/smoke-image.sh numberone-os:local
kubectl kustomize k8s/base
kubectl kustomize k8s/overlays/homologation
```

## Homologação no EKS

O workflow manual usa o environment `homologation`, aceita `main` e executa o CI no mesmo commit. Publique em `main` via PR originado de `develop`. Configure as revisões e restrições de branch do environment no GitHub. O workflow recebe o JAR validado, publica no ECR, resolve o digest, executa a validação no servidor e aguarda rollout e health via port-forward.

| Variável do environment | Valor esperado |
| --- | --- |
| `AWS_REGION`, `AWS_ACCOUNT_ID`, `EKS_CLUSTER_NAME` | Região, conta e nome do cluster fornecidos pela plataforma |
| `ECR_REPOSITORY` | `numberone-os` |
| `K8S_NAMESPACE` | Namespace provisionado para a aplicação |
| `DB_IDENTIFIER` | Instância RDS PostgreSQL |
| `DB_APP_USERNAME` | Usuário exclusivo da aplicação, diferente do master |

O Terraform consulta os metadados de `numberone-os-database` para resolver o ARN; o runner precisa de `secretsmanager:DescribeSecret`. Configure os secrets do GitHub `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` e `AWS_SESSION_TOKEN` para a sessão AWS Academy. O fluxo monta JDBC com TLS para `numberone_os` e sincroniza `numberone-os-database` no namespace. O usuário precisa ter acesso ao banco exclusivo e às migrations Flyway da aplicação. O Service interno é `numberone-os-service:8080` com destino na porta HTTP 8080 do container.

A plataforma deve provisionar o ConfigMap `numberone-os-runtime` com `branch-id` UUID. As URLs `BILLING_BASE_URL` e `INVENTORY_BASE_URL` estão no ConfigMap do overlay de homologação. O ConfigMap runtime pode fornecer `APP_MATERIAL_REFERENCE_PROVIDER`, `BILLING_SQS_ENABLED`, `BILLING_SQS_MODE`, `BILLING_SQS_QUEUE_URL`, `HISTORY_DYNAMODB_ENABLED`, `HISTORY_BRIDGE_URL`, `HISTORY_DYNAMODB_TABLE` e `AWS_REGION` quando os recursos e as permissões correspondentes estiverem disponíveis. A configuração do Datadog segue no Deployment. As opções de integração são injetadas quando configuradas. Mudanças de valores usados como variáveis de ambiente exigem recriar os Pods.

O tracer Datadog descarta traces `GET /actuator/health` e seus caminhos filhos
do serviço `numberone-os`; os probes Kubernetes continuam ativos.

## Diagnóstico e rollback

O resumo do workflow registra commit, digest, namespace e resultado. Use o estado de Deployment, Pods e Service para investigar rollout. Para voltar a uma imagem anterior, recupere seu digest no resumo da execução anterior e confirme que a imagem entende o schema atual. Aplique o digest anterior e aguarde readiness antes de liberar tráfego:

```bash
kubectl -n "$K8S_NAMESPACE" set image deployment/numberone-os=os="$PREVIOUS_IMAGE_URI"
kubectl -n "$K8S_NAMESPACE" rollout status deployment/numberone-os --timeout=10m
```

O rollback da imagem não reverte migrations ou configurações. Trate correções de schema pelo procedimento de banco da equipe.

O MVP não autentica nem autoriza HTTP, inclusive nas rotas internas e administrativas. Os serviços comunicam-se via ClusterIP, que não autentica o chamador. `X-Authenticated-Subject` é apenas autoria declarada UUID nas operações que exigem autor. Proteção externa está fora deste escopo; identidade por serviço, NetworkPolicies e TLS/mTLS são evoluções futuras. Veja [a decisão do MVP](../doc/adr/0004-mvp-sem-autenticacao-http.md).

Preparação transversal AWS atual: [roteiro integrado](../../numberone-billing-service/docs/deployment/eks-readiness/README.md).
O Kind compartilhado está em `numberone-billing-service/dev/kind`; os overlays deste
repositório continuam sendo reutilizados, com os ajustes de composição feitos na raiz integrada.

Fluxo de branches, CI e deploy automático: [CI/CD](CI-CD.md).

### Histórico pela ponte HTTP

Na AWS Academy, configure `HISTORY_DYNAMODB_ENABLED=true` e `HISTORY_BRIDGE_URL`
com o output `billing_events_bridge_url`. O overlay de homologação projeta o
token do ServiceAccount da OS para envio e consulta de histórico e para consumo
SQS via HTTP, com renovação feita pelo kubelet. Configure
`BILLING_SQS_BRIDGE_URL` com a mesma URL ao habilitar a fila nesse ambiente.

Com `HISTORY_BRIDGE_URL` vazio, a aplicação usa o DynamoDB direto através das
configurações de tabela, região e endpoint. O overlay Kind segue usando
DynamoDB no LocalStack.
