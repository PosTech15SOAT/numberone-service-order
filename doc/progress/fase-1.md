# Registro de progresso — fase 1

Data: 31/10/2026. Responsável pela revisão: Matheus.
Status: **1A implementada localmente, aguardando revisão; fase não concluída nem aprovada**.

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
| GitHub Actions / rulesets | não executado/não aplicado: publicação pendente |

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

Pendente de elaboração. Especificação é proposta, não contrato aprovado.

## Impedimentos de publicação

Revisão automática rejeitou criar repositório público/enviar commits por exigir
autorização explícita para publicação externa. Trabalho continua local, sem
modificar os cinco repositórios existentes. CI remoto e governança permanecem pendentes.

## Encerramento

Não iniciar fase 2 antes da revisão de Matheus. Não fazer merge ou deploy.
