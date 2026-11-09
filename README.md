# NumberOne — Serviço de Ordens de Serviço

OS independente em Java 25/Spring Boot 4.0.5, PostgreSQL exclusivo e Flyway.
A fase 2 extrai cadastros locais, diagnóstico, itens e materiais previstos.
As fases 3 e 4 acrescentam inbox SQS Billing → OS, Saga durável, consumo REST por
item e entrega com quitação oficial. SQS permanece desabilitado por padrão;
os modos `dedicated` e `shared` exigem configuração explícita.
Não há Billing/Inventory reais, novas Lambdas ou deploy nesta entrega.

[Implementação e demonstração da fase 4](doc/progress/fase-4.md), sobre a
[mensageria da fase 3](doc/progress/fase-3.md): pacote ativo
`2.0.0-os-baseline.1`, `readyForOsImplementation=true`, `approvedContract=false`.

Status: **implementação para revisão de Matheus; fase não encerrada**.
[Relatório da fase 2](doc/progress/fase-2.md),
[mapa de extração](doc/progress/mapa-extracao-fase-2.md),
[demonstração e entrega](doc/progress/demonstracao-fase-2.md).
Origem autoritativa: `PosTech15SOAT/numberone-app-auto-service-api`, develop
`42b393cb66c9d0a62cf14f80dcfc37745ce09e7e`, conferido em 01/11/2026.
O checkout legado não é a base desta aplicação.

Os [contratos da Saga v1](doc/contracts/saga/v1/README.md) permanecem **DRAFT**,
sem alteração nesta fase. Histórico: [fase 1](doc/progress/fase-1.md).

## Executar localmente no PowerShell

Docker Desktop com engine Linux; não requer JDK no host. O Compose usa banco
`service_order_db`, volume próprio, credencial demonstrativa e identidade ADMIN
local. Só publica HTTP no loopback; não utilizar esse perfil fora do desenvolvimento.
Não existe login nem emissor JWT. O PostgreSQL não publica porta no host.

```powershell
$env:MATERIAL_REFERENCE_PROVIDER = 'fixture'
docker compose up --build -d
Invoke-RestMethod http://127.0.0.1:18080/actuator/health/readiness
pwsh -File scripts/demo-phase2.ps1
docker compose down
```

O script aguarda readiness e falha se qualquer resultado diferir do esperado.
Usa somente dados sintéticos e mostra estados/contagens, sem corpos de cadastro.
O material fixo da demonstração **não consulta Inventory real**. Sem ativar a
fixture, consulta de referência retorna 503 `MATERIAL_REFERENCE_UNAVAILABLE`.
O volume persiste entre execuções; o script reaproveita seus cadastros sintéticos
e cria uma nova OS por execução. Readiness inclui PostgreSQL; liveness só o processo.

## Compilar e conferir

Com JDK 25 e Docker: `./mvnw clean verify` (Linux) ou `./mvnw.cmd clean verify`
(PowerShell). Com Java em container, no Docker Desktop:

```powershell
docker run --rm --mount "type=bind,source=$((Get-Location).Path),target=/workspace" --mount type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock --mount type=volume,source=numberone-os-phase1-maven,target=/root/.m2 -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -w /workspace maven:3.9.11-eclipse-temurin-25 sh ./mvnw -B -ntp clean verify
docker build -f scripts/contracts.Dockerfile -t numberone-os-contract-validation .
docker run --rm --network none --mount "type=bind,source=$((Get-Location).Path),target=/workspace" numberone-os-contract-validation
docker build -t numberone-service-order:local .
```

Primeira execução precisa de rede para dependências/imagens. O socket permite ao
Testcontainers criar e remover seu PostgreSQL descartável; nenhum teste usa o
banco do Compose ou legado. Em Linux nativo, rode Maven no host ou ajuste o acesso
ao daemon conforme a [documentação do Testcontainers](https://java.testcontainers.org/supported_docker_environment/continuous_integration/dind_patterns/).

| Evidência | Execução / relatório | O que demonstra |
|---|---|---|
| Regra | Surefire `*Test`, `target/surefire-reports` | Domínio, validações, identidade, posse e correlação |
| Persistência | `ServiceOrderPersistenceIT`, Failsafe | Flyway, FKs, constraints, locks e versões em PostgreSQL 17 real |
| Transporte | `*TransportIT`, Failsafe | HTTP/Tomcat reais em porta aleatória, segurança e CRUD sobre PostgreSQL |
| Contrato | `scripts/validate_contracts.py`, `target/contract-validation.json` | Schemas/vetores; não prova tráfego entre serviços |
| Cobertura | `target/site/jacoco/index.html` | Unitários + integração, gate 80% de linhas; só bootstrap excluído |

`SagaMainFlowIT` demonstra a execução completa por HTTP com PostgreSQL e
participantes Billing/Inventory simulados, usando o pacote real de contratos.
Os testes históricos de domínio/persistência mantêm seeds identificados.
`scripts/validate_phase4_trace.py` valida os JSONs capturados da demonstração.
Não há endpoints administrativos de confirmação nem integração externa demonstrada.

## Compensação e recuperação — fase 5

Cancelamento integral conferido antes do primeiro início usa C1–C6 e
CT-08/08B/09/11. O worker retoma operações com chaves persistidas após timeout
ou reinício. Ver [entrega e evidências](doc/progress/fase-5.md),
[roteiro de retomada e revisão](doc/progress/runbook-fase-5.md) e
[contrato das rotas](doc/contracts/os-phase5.openapi.yaml).

## Segurança e limites

`/api/*` exige `X-Correlation-Id` válido. `/api/admin/**` exige ADMIN; acompanhamento
exige ADMIN ou `SERVICE_ORDER_TRACK_OWN` com posse do cliente e filial configurada.
As rotas e mudanças de DTO estão no mapa de extração. Valores estimados são
identificados como `ESTIMATIVA`; aprovação/pagamento só são projeções confirmadas.

Fora do perfil local, fornecer `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` e
`OS_BRANCH_ID` por configuração segura. Identidade Gateway vem desligada para
confiança de headers: `GATEWAY_HEADERS_TRUSTED=false`. Marcelo precisa validar
sobrescrita dos headers no Gateway e bloqueio de acesso direto antes de habilitar.
Não se pode confiar em headers recebidos diretamente de clientes.

Logs JSON usam `correlation_id`, sem payloads, identidade ou erros SQL. Datadog
1.64.2 fixado na imagem; envio ao Datadog não demonstrado. CI executa **Required
validation**; manifestos são apenas referências para revisão. Não há workflow de
deploy. Ver [CONTRIBUTING](CONTRIBUTING.md), [manifestos](k8s/README.md) e
[governança pendente](doc/integration/governance-request.md).
