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
| `ECR_REPOSITORY` | `numberone-service-order` |
| `K8S_NAMESPACE` | Namespace provisionado para a aplicação |
| `DB_IDENTIFIER`, `DB_SECRET_ARN` | Instância RDS PostgreSQL e ARN do Secret com `username` e `password` |
| `DB_APP_USERNAME` | Usuário exclusivo da aplicação, diferente do master |

Configure os secrets do GitHub `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` e `AWS_SESSION_TOKEN` para a sessão AWS Academy. O fluxo monta JDBC com TLS para `numberone_os` e sincroniza `numberone-os-database` no namespace. O usuário precisa ter acesso ao banco exclusivo e às migrations Flyway da aplicação. O Service interno é `numberone-os-service:8080` com destino na porta HTTP 8080 do container.

A plataforma deve provisionar o ConfigMap `numberone-os-runtime` com `branch-id` UUID. O mesmo ConfigMap pode fornecer `BILLING_BASE_URL`, `INVENTORY_BASE_URL`, `APP_MATERIAL_REFERENCE_PROVIDER`, `BILLING_SQS_ENABLED`, `BILLING_SQS_MODE`, `BILLING_SQS_QUEUE_URL`, `HISTORY_DYNAMODB_ENABLED`, `HISTORY_DYNAMODB_TABLE` e `AWS_REGION` quando os recursos e as permissões correspondentes estiverem disponíveis. `numberone-os-integrations` fornece `BILLING_TO_OS_TOKEN`, `OS_TO_BILLING_TOKEN`, `OS_TO_INVENTORY_TOKEN` e, se aplicável, `AUTH_TO_OS_TOKEN`. A configuração do Datadog segue no Deployment. Configure `GATEWAY_HEADERS_TRUSTED` no ConfigMap de runtime após validar a entrada confiável e a rede no Item 3. As opções de integração são injetadas quando configuradas. Mudanças de valores usados como variáveis de ambiente exigem recriar os Pods.

## Diagnóstico e rollback

O resumo do workflow registra commit, digest, namespace e resultado. Use o estado de Deployment, Pods e Service para investigar rollout. Para voltar a uma imagem anterior, recupere seu digest no resumo da execução anterior e confirme que a imagem entende o schema atual. Aplique o digest anterior e aguarde readiness antes de liberar tráfego:

```bash
kubectl -n "$K8S_NAMESPACE" set image deployment/numberone-service-order=os="$PREVIOUS_IMAGE_URI"
kubectl -n "$K8S_NAMESPACE" rollout status deployment/numberone-service-order --timeout=10m
```

O rollback da imagem não reverte migrations ou configurações. Trate correções de schema pelo procedimento de banco da equipe.
