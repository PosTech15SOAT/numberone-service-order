# Fase 2 — registro de execução

Data: 01/11/2026. **Implementada e validada localmente; aguardando revisão de
Matheus. Fase não encerrada**, pois o aceite de revisão obrigatório está pendente.
Autorização: usuário solicitou iniciar a fase 2 após apresentação do escopo.
Isso não representa aprovação dos contratos pelos demais membros nem merge/deploy.

Destino: [PosTech15SOAT/numberone-service-order](https://github.com/PosTech15SOAT/numberone-service-order).
Branch `feature/fase-2-dominio-persistencia`, base `c73dfb2`, sobre a fase 1B
(`eac1b18`). PRs #1/#2 continuam abertos, sem merge. Checkout separado
`tmp/fase-2/numberone-service-order`; alterações existentes do checkout documental
foram preservadas. Instruções CONTRIBUTING de origem/destino lidas antes das
edições; nenhum AGENTS.md encontrado. A autorização para avançar foi registrada
na fase 1 sem alegar aprovação dos responsáveis pelos contratos.

Origem conferida por fetch em 01/11/2026: develop
`42b393cb66c9d0a62cf14f80dcfc37745ce09e7e`, main
`d7e489064bff04ce9620eaf48f9d070d8a13f86d`, conteúdo idêntico. O checkout antigo
PosTech15SOAT não foi usado como fonte da aplicação.

## Alterações e decisões

- Cadastros cliente/veículo/serviço extraídos seletivamente da origem atual;
  domínio de OS/diagnóstico/itens adaptado aos limites de serviços.
- PostgreSQL `service_order_db`, Flyway próprio, UUIDs, versões, lock na raiz,
  constraints de posse/contadores/datas e proteção de snapshots aprovados.
- Somente relações JPA locais; referências externas de material são IDs e
  snapshots. Previsão, reserva e consumo separados por item/revisão da ordem.
- Abertura, diagnóstico, itens/materiais previstos, tracking, cancelamento local,
  conclusão e entrega com guardas. Início sempre bloqueado até integração fase 4.
- `READY_FOR_EXECUTION` / `START_REQUESTED` modelados; nenhum endpoint pode gravar
  aprovação, pagamento ou confirmação de consumo. Estimativa não é orçamento.
- Identidade/permissões Gateway e posse preservadas; confiança em headers exige
  configuração explícita, desabilitada por padrão. Perfil local apenas demonstração.
- Filial definida no servidor; UUID informado arbitrariamente pelo cliente não
  controla escopo. Logs de identidade e detalhes SQL removidos/suprimidos.
- Contratos Saga v1 e validador compartilhado inalterados. Sem Billing, Inventory,
  Lambdas, outbox/inbox, infraestrutura compartilhada, merge ou deploy.

Detalhes, campos e mudanças HTTP: [mapa de extração](mapa-extracao-fase-2.md).
Execução PowerShell e entrega por responsável: [demonstração](demonstracao-fase-2.md).

## Evidências reais

Ambiente: Windows/PowerShell 7, Docker Desktop Linux 29.4.2, Maven Wrapper 3.9.14
em `maven:3.9.11-eclipse-temurin-25`, Spring Boot 4.0.5, Testcontainers 1.21.4,
PostgreSQL 17.9 (imagem `postgres:17-alpine` observada nesta execução).

| Comando / categoria | Resultado observado |
|---|---|
| `./mvnw -B -ntp clean verify` em Docker com socket/host override | **BUILD SUCCESS**, 1min45s, 134 testes, zero falhas/erros/ignorados |
| Surefire `*Test` — regras/componentes | **106 aprovados**, incluindo 42 casos do domínio OS; mocks não são evidência de persistência |
| Failsafe `CatalogTransportIT`, `ServiceOrderTransportIT`, `HealthTransportIT` | **22 aprovados**, HTTP/Tomcat reais e banco real; não usam MockMvc |
| Failsafe `ServiceOrderPersistenceIT` | **6 aprovados**: Flyway/FKs, constraints, versão obsoleta, lock concorrente, imutabilidade, conclusão com confirmação semeada |
| JaCoCo agregado unitários+integração | **1.164 / 1.239 linhas = 93,95%**, gate 80%; exclusão única `ServiceOrderApplication`, sem ampliar exclusões |
| Validador de contrato offline | **8 checks**, 24 schemas, 21 mensagens, 31 fixtures válidas e 21 inválidas; zero falhas/erros |
| `docker build -t numberone-service-order:phase2 .` | Imagem construída; runtime non-root executado no Compose |
| `pwsh -File scripts/demo-phase2.ps1` | Readiness UP; EM_DIAGNOSTICO; estimativa 350.00 BRL; aprovado nulo; previsto/reservado/consumido = 1/0/0; início/conclusão/entrega indevidos = 409 |
| Recriação do container PostgreSQL e repetição do script | Volume preservado, segunda OS criada; consulta real encontrou `service_order_db`, 2 ordens, 1 migration aplicada |
| `kubectl kustomize k8s/base` + kubeconform v0.8.0 / Kubernetes 1.36.0 | **2 recursos válidos**, zero inválidos/erros/ignorados; somente validação estática |
| actionlint 1.7.7 | Saída 0, sem achados |
| Inspeção de imports + SQL/JPA | Sem imports Billing/Inventory/budget; teste enumera exatamente as tabelas próprias e alvos de FKs |

Relatórios locais: `target/surefire-reports`, `target/failsafe-reports`,
`target/site/jacoco/index.html`, `target/contract-validation.json`.
Resumo sem dados pessoais: [evidencias-fase-2.json](evidencias-fase-2.json).
Imagem da demonstração: manifest list
`sha256:a17b5cb27f60f438b5b032168b56e4c2539be60657095dd8953c44528b80ac01`.
Os comandos completos reproduzíveis estão no README. CI guarda relatórios como
artefato; resultado remoto será registrado separadamente quando disponível.

Falhas encontradas e resolvidas: compilação da fixture de serviço após adicionar
`version`; script de demonstração procurava `codigo` onde a resposta do catálogo
usa `code`, corrigido e repetido com sucesso sobre volume persistente.
Mockito emitiu aviso de auto-attach do agente para futuros JDKs; não houve falha
no JDK 25 usado. Nenhuma falha conhecida permanece nos checks executados.

### Limites das evidências

- PostgreSQL e HTTP locais são reais. Confirmações Billing/Inventory são seeds
  explicitamente identificados em testes; **não demonstram integração real entre
  serviços**, pagamento, reserva, consumo externo ou Saga ponta a ponta.
- Fixture de referência de material só funciona em local/test mediante opção
  explícita; adapter padrão indisponível falha com 503. Endpoint real pendente.
- Sem cloud, Gateway/VPC Link, NLB, banco remoto, Datadog, integração auth lookup
  ou workflow de promoção executado. Recursos de banco/configuração nos
  manifestos são referências pendentes de Marcelo, não provisionamento.
- Revisão humana e aprovação de contratos não podem ser inferidas dos testes.

## Checklist de aceite

- [x] API compila/inicia com banco exclusivo e migrations reproduzíveis.
- [x] Diagnóstico e itens são da OS; não há saldo/movimento/fornecedor/Mercado Pago copiados.
- [x] Nenhum relacionamento JPA/SQL cruza limites de serviços; referências externas sem FK.
- [x] Regressões relevantes portadas e fluxo local demonstrado; capacidades distribuídas pendentes identificadas.
- [x] Gate de linhas >= 80% demonstrado sem ampliar exclusões.
- [ ] **Matheus revisou APIs, schema, guardas e mapa de migração.**

## Pendências e continuidade

Matheus: revisar DTOs/rotas, política de edição/cancelamento, filial única,
quantidades e mapa. Julio: aprovar contrato de preço/composição/moeda/contato e
snapshot Billing. Anderson: definir lookup real/material ativo e confirmar
reservas/consumo/compensação. Marcelo: auth lookup, proteção Gateway/rede,
banco próprio/configuração e governança do repositório. Contratos seguem DRAFT.
Fuso/offset dos DTOs HTTP e replanejamento de revisões precisam de decisão antes
do transporte distribuído. Nenhuma dessas pendências foi resolvida mudando o
contrato compartilhado silenciosamente. Não foram enviadas mensagens à equipe.

Em nova conversa, fornecer este relatório, o mapa, os contratos e commit/PR da
branch destino. **Parar para revisão; não começar fase 3, fazer merge ou deploy
sem nova solicitação.**
