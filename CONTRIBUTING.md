# Contribuição

Fluxo herdado da organização: `feature/*` → PR para `develop` → PR de promoção
`develop` → `main`. Não fazer push direto, force push, merge ou deploy durante esta
execução. O bootstrap local contém somente README. Revisão explícita de Matheus
por fase e pelo menos uma revisão de outro integrante antes de integrar.

Use Java 25 e Maven Wrapper. `./mvnw clean test` valida regras técnicas;
`./mvnw clean verify` acrescenta HTTP real e gate JaCoCo de 80% de linhas.
Só a classe de bootstrap `ServiceOrderApplication` é excluída; configuração,
segurança, filtros e controllers continuam medidos. Não há código de negócio ainda.
Ver README para comandos equivalentes no PowerShell e via Docker.

Novas features seguem `api/application/domain/infrastructure`. DTOs separados,
injeção por construtor e erros sanitizados. Não criar camadas vazias nem extrair
CRUDs antes da fase 2. O contrato do Gateway é a referência de identidade.
Não introduzir emissor JWT, tabelas RBAC, acesso ao banco de outro serviço ou logs
de payload/headers completos. Commits: `feat:`, `fix:`, `test:`, `docs:`, `chore:`.

Fase 1B é proposta até aprovação dos responsáveis. Não alterar silenciosamente
semântica, tipos ou obrigatoriedade de contratos compartilhados.
