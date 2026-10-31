# NumberOne — Serviço de Ordens de Serviço

Repositório da OS da organização PosTech15SOAT. Bootstrap da fase 1 em 31/10/2026.
Implementação incremental: base Java 25/Spring Boot 4.0.5 e contratos da Saga.

Origem: `PosTech15SOAT/numberone-app-auto-service-api`, develop
`42b393cb66c9d0a62cf14f80dcfc37745ce09e7e`, conteúdo igual a main
`d7e489064bff04ce9620eaf48f9d070d8a13f86d` na consulta de 31/10/2026.

Status: fase 1 em revisão. [Progresso e evidências](doc/progress/fase-1.md).
Não há deploy habilitado. Contratos dependem de revisão da equipe.

## Executar localmente (PowerShell)

Pré-requisitos: Docker Desktop com engine Linux. O JDK do host é opcional nestes comandos.

```powershell
docker compose up --build -d
Invoke-RestMethod http://127.0.0.1:18080/actuator/health/readiness
Invoke-RestMethod http://127.0.0.1:18080/api/public/health -Headers @{'X-Correlation-Id'='demo-phase1'}
docker compose down
```

Readiness e liveness retornam `UP` quando o processo está pronto. Na fase 1 isso
**não** verifica banco, SQS, Billing ou Inventory: essas dependências não existem
na aplicação mínima. Porta local padrão 18080; sobrescrever `OS_HTTP_PORT` se necessário.
O Compose é exclusivo da OS e não usa recursos do legado.

## Compilar e testar

Com JDK **25** instalado: `./mvnw clean verify` (Linux) ou `./mvnw.cmd clean verify`
(PowerShell). Sem JDK 25 no host:

```powershell
docker run --rm --mount "type=bind,source=$((Get-Location).Path),target=/workspace" --mount type=volume,source=numberone-os-phase1-maven,target=/root/.m2 -w /workspace maven:3.9.11-eclipse-temurin-25 sh ./mvnw -B -ntp clean verify
docker build -t numberone-service-order:local .
```

O primeiro download precisa de rede. `test` roda regras técnicas de correlação;
`verify` acrescenta testes HTTP com servidor real em porta aleatória, empacotamento
e gate JaCoCo. Relatórios: `target/surefire-reports`, `target/failsafe-reports` e
`target/site/jacoco/index.html`. Não há teste de persistência ou integração entre
serviços nesta fase. Os testes HTTP não utilizam MockMvc.

## Limites e padrões

Java 25, Spring Boot 4.0.5, Maven Wrapper 3.9.14. Estrutura por feature e camadas
internas; neste bootstrap só existe a feature técnica `shared`. Os seis módulos
do legado não foram copiados. Sem JPA, migrations ou PostgreSQL até a fase 2.

Endpoints liberados: health e probes. Os demais são negados, inclusive headers
de identidade forjados; não existe login, senha local ou emissor JWT. O contrato
do Gateway está registrado em [origens e decisões](doc/progress/origens.md).
`/api/*` exige `X-Correlation-Id` válido; probes dispensam esse header.

Logs estruturados e MDC preservam a convenção `correlation_id`. A imagem contém
agente Datadog **1.64.2** fixado, em vez de download `latest` a cada execução.
Tracing fica desligado no Compose; o manifesto prepara host do agente e StatsD.
Não foi demonstrado envio para Datadog. Não registrar corpos ou headers completos.

CI declara **Required validation**; promoção mantém **Validate promotion source**.
Não há workflow de deploy. [Manifestos](k8s/README.md) são somente para validação
e revisão; [pedido de governança](doc/integration/governance-request.md) depende de Marcelo.

Mudanças após bootstrap: branch `feature/*`, PR para `develop`, revisão de Matheus;
promoção posterior por PR `develop` → `main`. Ver [CONTRIBUTING](CONTRIBUTING.md).
