# Remoção de autenticação/autorização HTTP — relatório de revisão

Data: 13/11/2026. Decisão: [ADR 0004](../adr/0004-mvp-sem-autenticacao-http.md).

## Git e escopo

- Working tree inicialmente limpa, branch `develop` em `f6212a7`.
- `git fetch origin` concluído; `develop` e `origin/develop` sincronizadas, sem necessidade de fast-forward.
- Branch local de trabalho: `codex/remove-http-authentication`, baseada em `develop`.
- Alterações mantidas no workspace, sem stage, commit, push, merge ou deploy. A `develop` permanece em `f6212a7`.
- Nenhum outro repositório, Lambda Authorizer, API Gateway ou Terraform foi alterado.

## Classes removidas e adaptadas

Removidas após adaptar os consumidores:

- `SecurityConfiguration`, `BillingInternalSecurity`.
- `AuthenticatedUserAuthenticationFilter`, `AuthenticatedUserProperties`.
- `GatewayAuthenticatedUserProvider`, `LocalAuthenticatedUserProvider`, `AuthenticatedUserProvider`.
- `AuthenticatedUser`, `InvalidAuthenticatedUserContextException`, `AuthenticatedCustomerAccess`.

Adaptadas: `ServiceOrderItemController`, `SagaCompensationController`,
`ServiceOrderTrackingController`, `MaintenanceHistoryController`,
`ParticipantHttpClient`, `SagaHttpConfiguration`, `GlobalExceptionHandler` e
`HttpErrorResponseWriter`. Adicionados `DeclaredAuthor` e
`InternalCorrelationIdFilter`; preservado `CorrelationIdFilter` para `/api/**`.

Removido `spring-boot-starter-security` do POM. A inspeção de fontes não encontrou
imports Spring Security, SecurityContext, SecurityFilterChain, verificações de
roles/permissões nem envio de Authorization. O JAR final não contém dependências
Spring Security. Não há autenticação alternativa por profile.

Configuração atualizada em applications, Compose, `.env.example`, helper de
execução e manifests Kubernetes. Removida a injeção do Secret de tokens HTTP;
credenciais de banco e AWS mantidas. README, runbook, contratos OpenAPI ativos e
documentação de configuração refletem a decisão. Registros históricos relevantes
apontam para a decisão substituta; o pacote v1 permanece histórico.

## Autoria, correlação e negócio

`X-Authenticated-Subject` é UUID declarado, obrigatório em início de item,
cancelamento de OS/item e retomada de compensação. Ausência, vazio ou UUID
inválido resultam em 400. Não autentica identidade, não consulta permissões e
não possui valor padrão. O UUID segue como `responsibleUserId` para Inventory.
Retentativas preservam a autoria e o pedido durável original; os registros de
cancelamento e decisões operacionais permanecem.

Correlação pública mantém texto válido; `/internal/**` exige UUID, responde 400
para correlação ausente/inválida e propaga o header válido. O filtro interno
mantém correlação no MDC durante a requisição e a remove ao finalizar.

Clientes preservam conexão de 3s, resposta de 5s, URLs configuráveis,
`X-Correlation-Id` e `Idempotency-Key`, sem Bearer. Busca da OS, existência do
veículo, filtro de filial, paginação e cursores permanecem. As regras da Saga,
migrations, DTOs públicos e schemas de negócio não foram alterados.

## Validação

Comando final: `./mvnw clean verify`, com `JAVA_HOME` apontando para
`/home/julio/.sdkman/candidates/java/25-open` (OpenJDK 25, build 25+36-3489).
Resultado: **BUILD SUCCESS**, em 2min35s.

| Verificação | Resultado |
| --- | --- |
| Surefire | 104 testes, zero falhas/erros |
| Failsafe | 91 casos contabilizados: 89 executados, 2 ignorados; zero falhas/erros |
| Total executado | 193 testes |
| JaCoCo linhas | 2.337 de 2.495, **93,67%**; gate 80% aprovado |
| JaCoCo branches | 895 de 1.246, 71,83% |
| JaCoCo instruções | 13.508 de 14.465, 93,38% |
| Contratos | 21 checks aprovados, sem acesso à rede |
| Payloads HTTP do fluxo principal | 47 capturas válidas contra os contratos |
| Payloads HTTP da compensação | 89 capturas válidas contra os contratos |
| Comparação de OpenAPI | Schemas de negócio, rotas, métodos, request bodies e códigos funcionais preservados; removida a segurança HTTP |
| Git | `git diff --check` sem erros |

Testes executados incluem PostgreSQL real via Testcontainers, DynamoDB Local,
HTTP real, início/consumo, compensação, recuperação após reinício, concorrência,
duplicidade, acompanhamento, histórico e erros. Foram adaptadas as suítes
`ServiceOrderTransportIT`, `HealthTransportIT`, `SagaMainFlowIT`,
`SagaCompensationIT`, `InventoryI1IT`, `InventoryI2IT`, `CompensationProtocolTest`
e os helpers `PostgresSupport`/`HttpPostgresSupport`. Os testes exclusivos dos
providers e de autorização foram removidos/substituídos por comportamento sem
autenticação, autoria obrigatória e correlação.

Relatórios locais: `target/surefire-reports`, `target/failsafe-reports`,
`target/site/jacoco/index.html`, `target/contract-validation.json` e
`target/http-auth-removal/`. A primeira tentativa em sandbox não acessou o cache
Maven; a execução autorizada com cache/Docker concluiu normalmente.

## Não executado e pendências

- `InventoryI1IT` e `InventoryI2IT`: ignorados porque `I1_INVENTORY_URL` e `I2_INVENTORY_URL` não estão configuradas.
- Homologação com Billing e Inventory reais: não executada; URLs de integração não configuradas e nenhum participante estava em execução local no início. Os testes locais usam simuladores HTTP desses participantes.
- SQS/AWS real: não executado; mensageria validada com mock do SDK e inbox PostgreSQL.
- Não houve deploy nem validação de conectividade em cluster.

O débito técnico do MVP é explícito: endpoints acessíveis pela rede não verificam
identidade e autoria pode ser declarada por terceiros. ClusterIP não fornece
controle de identidade. Proteção externa está fora do escopo; identidade por
serviço, NetworkPolicies e TLS/mTLS ficam como evoluções futuras.

**GO técnico para iniciar integração controlada da OS com Billing e Inventory**
que já aceitem HTTP sem autenticação. **NO-GO para declarar homologação integrada
concluída** até executar os cenários com participantes reais e confirmar contratos,
configuração e conectividade no ambiente integrado.
