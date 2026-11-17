# NumberOne â€” ServiÃ§o de Ordens de ServiÃ§o

OS independente em Java 25/Spring Boot 4.0.5, PostgreSQL exclusivo e Flyway.
A fase 2 extrai cadastros locais, diagnÃ³stico, itens e materiais previstos.
As fases 3 e 4 acrescentam inbox SQS Billing â†’ OS, Saga durÃ¡vel, consumo REST por
item e entrega com quitaÃ§Ã£o oficial. SQS permanece desabilitado por padrÃ£o;
os modos `dedicated` e `shared` exigem configuraÃ§Ã£o explÃ­cita.
NÃ£o hÃ¡ Billing/Inventory reais, novas Lambdas ou deploy nesta entrega.

[ImplementaÃ§Ã£o e demonstraÃ§Ã£o da fase 4](doc/progress/fase-4.md), sobre a
[mensageria da fase 3](doc/progress/fase-3.md): pacote ativo
`2.0.0-os-baseline.1`, `readyForOsImplementation=true`, `approvedContract=false`.

Status: **implementaÃ§Ã£o para revisÃ£o de Matheus; fase nÃ£o encerrada**.
[RelatÃ³rio da fase 2](doc/progress/fase-2.md),
[mapa de extraÃ§Ã£o](doc/progress/mapa-extracao-fase-2.md),
[demonstraÃ§Ã£o e entrega](doc/progress/demonstracao-fase-2.md).
Origem autoritativa: `PosTech15SOAT/numberone-app-auto-service-api`, develop
`42b393cb66c9d0a62cf14f80dcfc37745ce09e7e`, conferido em 01/11/2026.
O checkout legado nÃ£o Ã© a base desta aplicaÃ§Ã£o.

Histórico: [fase 1](doc/progress/fase-1.md),
[revisão 1B e evidências](doc/progress/revisao-fase-1b.md),
[matriz de decisões](doc/contracts/saga/v2/decisions.md) e
[ADR 0003](doc/adr/0003-revisao-1b-contratos-arquitetura-v2.md).
O [pacote v1](doc/contracts/saga/v1/README.md) permanece histórico; o pacote ativo
é `2.0.0-os-baseline.1`, descrito acima. Veja também o
[roteiro da fase 1](doc/progress/demonstracao-fase-1.md) e a
[entrega aos membros](doc/progress/entrega-fase-1.md).

## Como seguir a Saga no código

A OS orquestra a Saga porque é dona do ciclo de vida da ordem. Billing e
Inventory confirmam suas próprias operações. A OS recebe eventos de orçamento
e pagamento por SQS e usa REST para obter confirmações de estoque e quitação
e para executar as compensações.

| Entrada | Caso de uso | Adaptadores |
| --- | --- | --- |
| Iniciar item | `StartServiceOrderItem` prepara a tentativa, chama Inventory e aplica o resultado | `JdbcItemStartStore` e `HttpInventorySagaGateway` |
| Concluir ou entregar | `FinishServiceOrder` grava a conclusão e consulta o Billing antes da entrega | `JdbcOrderCompletionStore`, outbox de histórico e `HttpBillingSagaGateway` |
| Evento do Billing | `ProcessBillingEvent` aplica a decisão de orçamento ou a atualização de pagamento | `SqsBillingConsumer`, `BillingEventCodec` e `JdbcBillingInboxStore` |
| Cancelar ou retomar | `SagaCompensation` resolve estoque, encerra orçamento e conclui o cancelamento | `JdbcSagaCompensationStore` e os clientes REST dos participantes |
| Recuperar pendências | `SagaRecoveryWorker` retoma a operação a partir do PostgreSQL | `SagaRecoverySchedule` e `JdbcSagaRecoveryStore` |

As chamadas REST acontecem entre transações locais. A inbox grava o evento e
seu efeito no mesmo commit, antes de confirmar a mensagem ao SQS. O mesmo ID
de operação é reaproveitado na recuperação de uma resposta perdida.

## Executar localmente no PowerShell

Docker Desktop com engine Linux; nÃ£o requer JDK no host. O Compose usa banco
`service_order_db`, volume próprio e credencial demonstrativa. O serviço HTTP não
exige autenticação em nenhum perfil. O Compose publica HTTP apenas no loopback;
o PostgreSQL não publica porta no host.

```powershell
$env:MATERIAL_REFERENCE_PROVIDER = 'fixture'
docker compose up --build -d
Invoke-RestMethod http://127.0.0.1:18080/actuator/health/readiness
pwsh -File scripts/demo-phase2.ps1
docker compose down
```

O script aguarda readiness e falha se qualquer resultado diferir do esperado.
Usa somente dados sintÃ©ticos e mostra estados/contagens, sem corpos de cadastro.
O material fixo da demonstraÃ§Ã£o **nÃ£o consulta Inventory real**. Sem ativar a
fixture, consulta de referÃªncia retorna 503 `MATERIAL_REFERENCE_UNAVAILABLE`.
O volume persiste entre execuÃ§Ãµes; o script reaproveita seus cadastros sintÃ©ticos
e cria uma nova OS por execuÃ§Ã£o. Readiness inclui PostgreSQL; liveness sÃ³ o processo.

## Compilar e conferir

Veja [Docker, Kubernetes e CI/CD](k8s/README.md) para validar a imagem e preparar o deploy de homologação.

Com JDK 25 e Docker: `./mvnw clean verify` (Linux) ou `./mvnw.cmd clean verify`
(PowerShell). Com Java em container, no Docker Desktop:

```powershell
docker run --rm --mount "type=bind,source=$((Get-Location).Path),target=/workspace" --mount type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock --mount type=volume,source=numberone-os-phase1-maven,target=/root/.m2 -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -w /workspace maven:3.9.11-eclipse-temurin-25 sh ./mvnw -B -ntp clean verify
docker build -f scripts/contracts.Dockerfile -t numberone-os-contract-validation .
docker run --rm --network none --mount "type=bind,source=$((Get-Location).Path),target=/workspace" numberone-os-contract-validation
docker build -t numberone-service-order:local .
```

Primeira execuÃ§Ã£o precisa de rede para dependÃªncias/imagens. O socket permite ao
Testcontainers criar e remover seu PostgreSQL descartÃ¡vel; nenhum teste usa o
banco do Compose ou legado. Em Linux nativo, rode Maven no host ou ajuste o acesso
ao daemon conforme a [documentaÃ§Ã£o do Testcontainers](https://java.testcontainers.org/supported_docker_environment/continuous_integration/dind_patterns/).

| EvidÃªncia | ExecuÃ§Ã£o / relatÃ³rio | O que demonstra |
|---|---|---|
| Regra | Surefire `*Test`, `target/surefire-reports` | DomÃ­nio, validaÃ§Ãµes, autoria e correlação |
| PersistÃªncia | `ServiceOrderPersistenceIT`, Failsafe | Flyway, FKs, constraints, locks e versÃµes em PostgreSQL 17 real |
| Transporte | `*TransportIT`, Failsafe | HTTP/Tomcat reais em porta aleatÃ³ria, autoria, correlação e CRUD sobre PostgreSQL |
| Contrato | `scripts/validate_contracts.py`, `target/contract-validation.json` | Schemas/vetores; nÃ£o prova trÃ¡fego entre serviÃ§os |
| Cobertura | `target/site/jacoco/index.html` | UnitÃ¡rios + integraÃ§Ã£o, gate 80% de linhas; sÃ³ bootstrap excluÃ­do |

`SagaMainFlowIT` demonstra a execuÃ§Ã£o completa por HTTP com PostgreSQL e
participantes Billing/Inventory simulados, usando o pacote real de contratos.
Os testes histÃ³ricos de domÃ­nio/persistÃªncia mantÃªm seeds identificados.
`scripts/validate_phase4_trace.py` valida os JSONs capturados da demonstraÃ§Ã£o.
NÃ£o hÃ¡ endpoints administrativos de confirmaÃ§Ã£o nem integraÃ§Ã£o externa demonstrada.

## CompensaÃ§Ã£o e recuperaÃ§Ã£o â€” fase 5

Cancelamento integral conferido antes do primeiro inÃ­cio usa C1â€“C6 e
CT-08/08B/09/11. O worker retoma operaÃ§Ãµes com chaves persistidas apÃ³s timeout
ou reinÃ­cio. Ver [entrega e evidÃªncias](doc/progress/fase-5.md),
[roteiro de retomada e revisÃ£o](doc/progress/runbook-fase-5.md) e
[contrato das rotas](doc/contracts/os-phase5.openapi.yaml).

## SeguranÃ§a e limites

O MVP não autentica nem autoriza HTTP. Rotas administrativas, internas,
acompanhamento e histórico não exigem Authorization, roles, permissões ou posse.
`X-Authenticated-Subject` é somente UUID de autoria declarada, obrigatório para
iniciar item, cancelar OS/item e retomar compensação. Não existe identidade padrão.

`/api/**` exige `X-Correlation-Id` textual válido; `/internal/**` exige UUID.
Correlação, `Idempotency-Key`, autoria persistida e filtros de filial permanecem.
Clientes de Billing e Inventory usam URLs configuráveis, sem Bearer ou tokens.
A consulta de identidade de cliente em `POST /internal/auth/customer-identity`
também não exige Authorization; continua validando UUID, CPF e cliente ativo.
Forneça `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` e `OS_BRANCH_ID` pela configuração.

Os serviços comunicam-se via ClusterIP. Proteção externa está fora do escopo
atual; identidade por serviço, NetworkPolicies e TLS/mTLS são evoluções futuras.
Veja [a decisão e o débito técnico](doc/adr/0004-mvp-sem-autenticacao-http.md).

Logs JSON usam `correlation_id`, sem payloads, identidade ou erros SQL. Datadog
1.64.2 fixado na imagem; envio ao Datadog nÃ£o demonstrado. CI executa **Required
validation**; manifestos sÃ£o apenas referÃªncias para revisÃ£o. NÃ£o hÃ¡ workflow de
deploy. Ver [CONTRIBUTING](CONTRIBUTING.md), [manifestos](k8s/README.md) e
[governanÃ§a pendente](doc/integration/governance-request.md).

### Histórico local com Docker Compose

O modo padrão `compose.yaml` permanece independente. Para a jornada explícita
OS + Billing real e Inventory Mockoon, execute:

```bash
OS_HTTP_PORT=19080 docker compose -p numberone-r2-history -f compose.yaml -f compose.history.yaml up -d --no-build
```

O overlay usa as imagens locais verificadas `numberone-service-order:r2-20261114`
e `numberone-billing-service:r2-20261114-v2` (construí-las antes de iniciar),
bancos/volumes próprios e LocalStack com SQS/DynamoDB. Não importa credenciais AWS
ou o `.env` completo do Billing. O bootstrap de SQS reutiliza a infraestrutura
Kind no workspace; o de DynamoDB é idempotente em `docker/localstack`.

`HISTORY_BILLING_PORT` altera a porta publicada e a URL pública de decisão do
Billing. `BUDGET_APPROVAL_BASE_URL` permite usar um domínio HTTPS acessível ao
cliente. A URL pública não usa nomes internos de containers. Notificações SMTP
e pagamentos ficam desligados nesse overlay; disponibilizar uma chave AES
própria e credenciais controladas antes de testar envios nesse ambiente.

Os perfis local/kind exigem endpoint DynamoDB local explícito e usam credenciais
fictícias fixas; não há fallback para AWS. A tabela possui partition key
`vehicleId` e sort key `historyKey`, ambas String. O pool local de scheduling tem
3 threads para separar o long poll SQS dos workers de histórico e recuperação.
O LocalStack desta composição é efêmero: recriar seu container perde as projeções;
linhas PostgreSQL já PROJECTED não são reabertas automaticamente. Preservar o
container/dados ou planejar reconstrução controlada antes de removê-lo.

## Preparação integrada de deploy

A documentação transversal está no clone irmão
[Billing: prontidão EKS](../numberone-billing-service/docs/deployment/eks-readiness/README.md).
A orquestração local compartilhada passa a ficar em
[Billing/dev/kind](../numberone-billing-service/dev/kind/README.md), reutilizando este overlay Kind.


### Observabilidade Datadog

A imagem empacota `dd-java-agent` 1.64.2 com checksum e o carrega por `-javaagent`; o Deployment configura `DD_SERVICE=numberone-os`, `DD_AGENT_HOST=status.hostIP`, `DD_TRACE_ENABLED=true` e `DD_LOGS_INJECTION=true`. O Deployment usa `DD_ENV=academy`. A renderização do deploy recebe `AWS_ACCOUNT_ID` de `vars.AWS_ACCOUNT_ID` e usa `GITHUB_SHA` como `DD_VERSION`; o entrypoint acrescenta `aws_account_id:<conta>` em `DD_TAGS`, preservando as demais tags. Cada integrante usa a variável de sua conta. Confirme o Agent APM no nó e a porta TCP 8126, coleta de stdout e que não há injeção automática adicional. No kind, tracing permanece desabilitado. O tracer não exige que o Agent esteja disponível para iniciar. A imagem desabilita query strings em spans HTTP; não habilite captura de Authorization, payloads ou outros dados sensíveis.

Os logs em stdout usam JSON ECS, com timestamp, serviço, nível, logger, mensagem e contexto; `dd.trace_id` e `dd.span_id` são inseridos pelo tracer quando há span ativo. Verifique o parsing de objetos ECS e IDs como strings no Datadog antes de assumir a ligação log/trace. A OS utiliza `correlation_id` nas requisições; em `/api/**` aceita texto válido e em `/internal/**` exige UUID. A inbox/Saga e a projeção de histórico usam IDs próprios; procure por `service_order.id`, `event.id`, `saga.id` ou `correlation_id` nos eventos existentes. A propagação HTTP automática precisa ser verificada com Gateway e serviços reais; SQS e ponte HTTP não comprovam uma trace única, portanto mantenha a correlação operacional por IDs. Métricas HTTP/APM dependem da ingestão; a OS conserva DogStatsD existente. Para o vídeo, mostrar os três serviços, chamadas, erros e latência, log e trace relacionados e uma Saga por IDs, verificando ausência de segredos. Nenhuma recepção no Datadog foi comprovada localmente.

Os eventos operacionais seguem o padrão do Billing: ECS em stdout, SLF4J com `event.code` e IDs estruturados, `correlation_id` e mensagens sem payloads ou credenciais. Para APM, filtre por `env:academy`, `service:numberone-os`, `version:<sha>` e `aws_account_id:<conta>`. `DD_TAGS` não garante que a conta apareça no JSON de logs: validar enriquecimento no Agent/pipeline e ingestão real no EKS.

No JSON ECS, `service.name` deriva de `spring.application.name` (`numberone-service-order`); o tracer injeta `dd.service=numberone-os`. São campos de origens distintas; o mapeamento usado pelo pipeline Datadog deve ser verificado no EKS.

A nomenclatura de atributos usa ponto para entidades ou contextos (`service_order.id`, `budget.revision_number`, `event.code`) e underline para palavras compostas (`service_order_item.id`). O campo técnico independente `correlation_id` é usado no MDC e JSON; `X-Correlation-Id` continua como header. Campos oficiais como `dd.trace_id` e `dd.span_id` são preservados.
