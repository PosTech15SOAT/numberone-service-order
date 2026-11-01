# Registro de progresso — fase 1

Data: 31/10/2026. Responsável pela revisão: Matheus.
Status: **1A e 1B publicadas em PRs para revisão; fase não concluída nem aprovada**.

Entrega 1A: `11f3b82182ff694c6472e24e780d843642cde97b`, branch `feature/fase-1a-base`.
Entrega 1B: branch `feature/fase-1b-contratos`, construída sobre 1A. O commit exato
é o HEAD dessa branch (`git rev-parse feature/fase-1b-contratos`).
[PR #1 — 1A](https://github.com/PosTech15SOAT/numberone-service-order/pull/1) e
[PR #2 — 1B](https://github.com/PosTech15SOAT/numberone-service-order/pull/2) abertos.
Consultar [handoff](entrega-fase-1.md) para retomar em outra conversa.

## 1A — base

Checkout independente inicializado; aplicação mínima, testes técnicos/HTTP,
imagem, Compose, CI sem deploy e manifestos preparados. Origens verificadas em
[origens.md](origens.md).

### Comandos e resultados reais

Comandos completos de build/execução no README. Executados via Docker Desktop
29.4.2, Linux engine, Maven/Temurin 25; nenhuma credencial cloud usada.

| Verificação | Resultado |
| --- | --- |
| `sh ./mvnw -B -ntp clean verify` em Maven/Temurin 25 | BUILD SUCCESS; 7 testes técnicos + 9 HTTP real, zero falhas/erros/skips |
| JaCoCo 0.8.14, gate 80% de linhas | 41/41 linhas cobertas, 6 classes; somente bootstrap excluído |
| `docker build -t numberone-service-order:local .` | sucesso, usuário `10001:10001` |
| `docker compose up -d --no-build` | container iniciado; quatro GETs reais HTTP 200/UP |
| `/actuator/health`, `/liveness`, `/readiness` e `/api/public/health` | probes UP; health identifica `numberone-service-order` |
| `kubectl kustomize k8s/base` + kubeconform v0.8.0, Kubernetes 1.36.0 | 2 recursos válidos; 0 inválidos/erros/skips |
| `docker compose down` | somente container/rede da OS removidos após demonstração |
| GitHub Actions / rulesets | Required validation aprovado nos PRs #1/#2; rulesets continuam pendentes |

Imagem local: `sha256:f25ee490518030f35198abe3b4af39254c65077bf8ec98988529bf1b09e8ce2f`.
Build Maven aprovado às 18:43:20 America/Sao_Paulo em 31/10/2026.
Relatórios resumidos reais em [evidence](evidence/); relatórios completos regeneráveis
em `target/`. Não versionar XML de testes com propriedades do ambiente.

Primeira execução: 1 falha de asserção do Content-Type (charset acrescentado pelo
servidor); corrigida para comparar media type e usar UTF-8. Reexecução integral passou.
Tentativa por `localhost` demorou por resolução local; demonstração comprovada usando
`127.0.0.1`, a interface publicada pelo Compose. README usa esse endereço.

Testes de regra nesta etapa são **técnicos**, não de negócio. Persistência e SQS
não existem; não foram simulados nem declarados como integração comprovada.
Datadog preparado, sem prova de ingestão externa. CI local não equivale a Actions.

## 1B — contratos

[Pacote revisável](../contracts/saga/v1/README.md), 2 ADRs e pedidos de integração
preparados. A especificação é proposta: contratos continuam **DRAFT** e aprovação
dos responsáveis permanece pendente. Nenhum controller de negócio ou Saga implementado.

| Verificação | Resultado real |
| --- | --- |
| build de `scripts/contracts.Dockerfile` | sucesso; dependências diretas e transitivas fixadas em lockfile |
| `docker run --rm --network none ... numberone-os-contract-validation` | 8 grupos de verificações aprovados; zero falhas/erros |
| matriz original versus catálogo | 21/21 mensagens: 6 comandos, 15 eventos; 24 arquivos de schema no total |
| exemplos positivos/negativos | 31 válidos aceitos; 21 inválidos rejeitados pelo keyword/caminho esperado |
| formatos e referências | UUID/data efetivamente validados; referências locais, recuperação remota proibida |
| compatibilidade aditiva | campos extras aceitos em todos os níveis de objeto dos 21 tipos |
| OpenAPI 3.0.3 | consultas internas e alterações HTTP da OS válidas estruturalmente |
| actionlint 1.7.7 | workflows CI/promoção sem erros, exit 0; sem execução Actions |
| padrões comuns de segredos | nenhum match; somente nomes de arquivo seriam exibidos, nunca valores |

Evidência automática: [contract-validation.json](evidence/contract-validation.json).
Os oito grupos incluem coerência dos exemplos de R$ 350/um kit e sem materiais,
IDs de comando/resposta e cobertura da matriz lida da especificação preservada.
O teste **não** executa Saga, reserva, cobrança, compensação ou idempotência real.

Revisão de coerência com origem identificou PATCH de início e enums HTTP em
português; proposta ajustada para preservá-los e explicitar novos estados.
Mudanças incompatíveis de HTTP/body/idempotência estão registradas para decidir
versionamento ou migração coordenada; não foram publicadas silenciosamente.

### Separação de evidências

| Categoria | Comprovado | Não comprovado / fora desta fase |
| --- | --- | --- |
| Regra | 7 verificações técnicas de correlação | regras de domínio/Saga, ainda não implementadas |
| Persistência | nada; aplicação sem banco | PostgreSQL, migrations, transações, inbox/outbox |
| Contrato | JSON Schema, formatos, vetores positivos/negativos, OpenAPI e coerência de fixtures | concordância dos donos e conformidade de implementações Billing/Inventory |
| Transporte | 9 testes HTTP com Tomcat real + imagem via HTTP no host | SQS real, eventos entre serviços, DLQ/replay, auth end-to-end |
| Operação | build de imagem e validação estática de manifests/workflows | CI remoto, rulesets, deploy, AWS e ingestão Datadog |

### Decisões pendentes

Lista completa em [integration-requests.md](../contracts/saga/v1/integration-requests.md).
Prioridades: cálculo/moedas/contato/snapshot (Julio), quantidades e alocações/fechamento
de tentativa (Anderson), início HTTP e cancelamento parcial (Matheus), segurança
interna/lookup do cliente/filas/IAM e governança (Marcelo). Contratos entre serviços
permanecem independentes de linguagem. Sem congelar v1 por conta própria.

## Checklist de aceite

- [x] Checkout independente, origem registrada; nenhum legado sobrescrito.
- [x] Build, execução local, health/readiness e imagem demonstrados com comandos reais.
- [x] Workflows preparados com Required validation e Validate promotion source; nenhum deploy.
- [x] Bloqueio inicial de publicação resolvido por autorização explícita do usuário.
- [x] Cada mensagem da matriz possui schema e fixture; inválidos rejeitados por motivo esperado.
- [x] Contratos cobrem início pendente, falta de estoque, duplicação, compensação e UNKNOWN.
- [x] Pacote local, ADRs, roteiro e entrega aos quatro membros preparados.
- [x] Repositório público e PRs #1/#2 publicados após autorização explícita.
- [x] Required validation executado e aprovado nos PRs #1/#2, conferido em 01/11/2026.
- [ ] Governança aplicada e Validate promotion source executado — pendentes.
- [ ] Matheus revisou e aprovou a fase 1.
- [ ] Julio, Anderson e Marcelo validaram contratos, com commit/versão e decisões registrados.

**Não declarar fase concluída:** aceite humano obrigatório ainda não demonstrado.
Artefatos estão prontos para revisão nos PRs; [roteiro reproduzível](demonstracao-fase-1.md)
inclui PowerShell, comandos/resultados esperados e limites das provas.

## Publicação autorizada

Revisão automática inicialmente rejeitou criar repositório público/enviar commits.
Em 31/10/2026 o usuário autorizou explicitamente a publicação. Após reconferir o
destino, o repositório foi criado e as branches/PRs publicados. Nenhum dos cinco
repositórios existentes foi alterado; não houve merge, deploy ou avanço de fase.
Governança da OS continua pendente de Marcelo; contratos continuam DRAFT.

## Encerramento

Em 01/11/2026 o usuário autorizou explicitamente iniciar somente a fase 2.
Isso autoriza avançar a implementação incremental; não representa aprovação dos
contratos por Julio/Anderson/Marcelo nem autorização de merge/deploy. Os PRs #1/#2
continuam abertos. CI comprovado: [1A](https://github.com/PosTech15SOAT/numberone-service-order/actions/runs/41246164142)
e [1B](https://github.com/PosTech15SOAT/numberone-service-order/actions/runs/41246168717).
Fase 2 será empilhada sobre a 1B, preservando main/develop e os PRs existentes.
