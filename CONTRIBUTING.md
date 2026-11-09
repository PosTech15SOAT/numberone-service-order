# Contribuição

Fluxo herdado da organização: `feature/*` → PR para `develop` → PR de promoção
`develop` → `main`. Não fazer push direto, force push, merge ou deploy durante esta
execução. O bootstrap local contém somente README. Revisão explícita de Matheus
por fase e pelo menos uma revisão de outro integrante antes de integrar.

Use Java 25 e Maven Wrapper. `./mvnw clean test` valida regras locais;
`./mvnw clean verify` acrescenta PostgreSQL via Testcontainers, HTTP real e gate JaCoCo de 80% de linhas.
Só a classe de bootstrap `ServiceOrderApplication` é excluída; configuração,
segurança, filtros e controllers continuam medidos. Não ampliar exclusões para passar.
Ver README para comandos equivalentes no PowerShell e via Docker.

Novas features seguem `api/application/domain/infrastructure`. DTOs separados,
injeção por construtor e erros sanitizados. O contrato do Gateway é a referência de identidade.
Não introduzir emissor JWT, tabelas RBAC, acesso ao banco de outro serviço ou logs
de payload/headers completos. Commits: `feat:`, `fix:`, `test:`, `docs:`, `chore:`.

Fase 1B é proposta até aprovação dos responsáveis. Não alterar silenciosamente
semântica, tipos ou obrigatoriedade de contratos compartilhados.

Fase 2 empilhada sobre `feature/fase-1b-contratos` enquanto os PRs anteriores
aguardam revisão. Reorientar para `develop` somente depois da integração aprovada.
Fixtures de confirmação não são evidência de integração Billing/Inventory.
Banco e Compose são exclusivos da OS; não executar scripts contra bancos compartilhados.
