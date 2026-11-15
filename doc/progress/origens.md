# Origens e decisões da fase 1 — 31/10/2026

Fonte autoritativa: organização https://github.com/PosTech15SOAT, consultada via
API GitHub e checkout limpo. O projeto antigo em `D:/Dev/PosTech15SOAT` é apenas
a origem documental da especificação. Suas alterações existentes foram preservadas.
Checkout OS independente em `tmp/fase-1/numberone-service-order`, com `.git` próprio.

| Repositório | main consultada | develop consultada |
| --- | --- | --- |
| numberone-app-auto-service-api | d7e489064bff04ce9620eaf48f9d070d8a13f86d | 42b393cb66c9d0a62cf14f80dcfc37745ce09e7e |
| postech15soat-infra-cloud | 20c9a9b6f1215a47160269ec21c07d1e6ffe2d61 | 27a4019f4256684247ce17b4cfd9da46a3ddf9ec |
| postech15soat-infra-database | 65fe6bba1e90df66f590e9c692f41b9d797e0fd9 | 2de2cef89679218f25790b46cca0839bffe04d93 |
| numberone-app-auth | c0464abc38cef6b6a667cb89de2f695d80a17c25 | e495d580da4cc7c2f9022aa8cd3acebe114fec79 |
| postech15soat-governance | 2d35180492efcf2ff6fcdd6ab865e8cff7abe151 | db2d0708a8bf7605cdc702e2b7727ace55dba2f1 |

`main` confere com as cinco revisões da especificação. Comparação nos dois sentidos:
aplicação, infra cloud, infra database e governança possuem diferenças de histórico,
mas nenhuma diferença de arquivos entre main/develop. Auth está divergente:
develop tem alterações em README, ADR-002, ADR-004, diagrama, OpenAPI, Postman e RFC-001.
Não copiar/implantar essa branch; o contrato implementado na main permanece a
referência. As diferenças documentais precisam ser conciliadas por Marcelo.

Origem escolhida para copiar seletivamente wrapper e convenções: **develop da
aplicação**, SHA acima. `CONTRIBUTING.md` e workflows foram lidos; nenhum AGENTS.md
foi encontrado na árvore versionada da origem ou nos diretórios ancestrais locais.
O destino remoto retornou 404 no acesso disponível. Não foi pressuposta governança ativa.

## Decisões 1A

- Manter Java 25/Spring Boot 4.0.5 e Wrapper 3.9.14. Host possui Java 26.0.1;
  validação usa imagem Maven/Temurin 25, evitando mudar a stack para acomodar o host.
- Sem banco nesta etapa: aplicação mínima não persiste nada. PostgreSQL próprio,
  migrations e Testcontainers pertencem à fase 2. Não copiar schema/seeds legados.
- Segurança nega tudo exceto health/probes. Provider do Gateway, permissões e
  regras de posse entram junto das features na fase 2. Não confiar em headers para
  autorizar rotas nesta fase. Erros fixos não refletem dados de entrada.
- Convenções de correlação e erros adaptadas de `shared`; probes não exigem
  correlação. Logs estruturados, StatsD opcional e agente Datadog fixado.
- Service ClusterIP como base revisável, sem provisionar NLB. A integração futura
  preserva NLB interno/Gateway/VPC Link; não introduz ALB.
- Manifests usam nomes OS exclusivos e imagem inválida até deploy autorizado.
- Única exclusão JaCoCo: método de bootstrap da aplicação. Gate de 80% de linhas
  não é prova de cobertura de negócio (não há negócio implementado ainda).
- Bootstrap mínimo local em main/develop; entregas 1A e 1B em branches separadas.
  Publicação pública foi bloqueada pela revisão automática de permissões; nenhuma
  tentativa alternativa foi feita. Após preparar o resultado local, o usuário
  autorizou a publicação: repositório público e PRs #1/#2 criados em 31/10/2026.
  O bloqueio é histórico, não impedimento atual. Nenhum merge/deploy realizado.

## Referências técnicas

- [Origem Java/Spring](https://github.com/PosTech15SOAT/numberone-app-auto-service-api/tree/d7e489064bff04ce9620eaf48f9d070d8a13f86d)
- [Identidade do Gateway](https://github.com/PosTech15SOAT/numberone-app-auto-service-api/blob/d7e489064bff04ce9620eaf48f9d070d8a13f86d/doc/fase-3/authentication-contract.md)
- [Governança](https://github.com/PosTech15SOAT/postech15soat-governance/tree/2d35180492efcf2ff6fcdd6ab865e8cff7abe151)

Headers de identidade planejados: `X-Authenticated-Subject`,
`X-Authenticated-Customer-Id`, `X-Authenticated-Status`, `X-Authenticated-Roles`,
`X-Authenticated-Permissions`, `X-Correlation-Id`. Gateway deve sobrescrever entrada;
acesso direto ao workload precisa de controle de rede antes de habilitar esse provider.
Local provider somente em desenvolvimento/teste, nunca fallback de produção.
