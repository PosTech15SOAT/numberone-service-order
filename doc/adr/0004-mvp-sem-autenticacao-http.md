# ADR 0004 — MVP sem autenticação ou autorização HTTP

Status: aceito para o MVP, em 13/11/2026. Substitui as decisões anteriores de
segurança HTTP da OS e a DEC-15 do pacote de integração.

O OS Service não autentica nem autoriza requisições HTTP. Spring Security,
providers de identidade, validação de roles/permissões e controle de acesso por
titularidade foram removidos. A decisão vale em todos os profiles, incluindo
rotas administrativas e internas; não existe bypass nem autenticação opcional.

`X-Authenticated-Subject` conserva o nome do contrato e representa somente UUID
de autoria declarada. É obrigatório para iniciar item, cancelar OS/item e retomar
compensação. Ausência, valor vazio ou UUID inválido retornam 400, sem identidade
padrão e sem comprovação da identidade informada. `responsibleUserId` continua
sendo enviado ao Inventory. Pedidos persistidos, autoria original e evidências
da Saga permanecem nas retentativas e na recuperação.

`X-Correlation-Id` continua obrigatório em `/api/**` (texto não vazio, até 128
caracteres, sem controles) e `/internal/**` (UUID). A validação interna é um filtro
Servlet independente. `Idempotency-Key`, timeouts, URLs configuráveis, tratamento
de falhas, compensação e consistência transacional não mudam.

Acompanhamento e histórico não verificam identidade nem posse como autorização.
Busca da OS, existência/vínculo do veículo com a filial, filtro de filial,
paginação, cursores e validação de parâmetros continuam regras funcionais.

Clientes de Billing e Inventory não enviam Authorization nem exigem credenciais
estáticas HTTP. Foram removidas `IDENTITY_PROVIDER`, `GATEWAY_HEADERS_TRUSTED`,
`LOCAL_EMPLOYEE_ID`, `BILLING_TO_OS_TOKEN`, `OS_TO_BILLING_TOKEN` e
`OS_TO_INVENTORY_TOKEN`. Credenciais de banco e do AWS SDK permanecem com suas
responsabilidades próprias. Os OpenAPI ativos refletem o transporte sem segurança;
schemas de negócio e contratos de payload permanecem iguais. O pacote v1 é histórico.

Débito técnico: serviços comunicam-se via ClusterIP, que não autentica nem
autoriza chamadores. Quem alcançar os endpoints pode consultá-los ou invocar
operações, sujeito às regras de negócio, e pode declarar autoria de terceiros.
Proteção externa está fora do escopo atual. Identidade por serviço,
NetworkPolicies e TLS/mTLS são evoluções futuras. Não houve alteração em
Lambda Authorizer, API Gateway, Terraform ou nos repositórios dos participantes.

A homologação ponta a ponta depende de Billing e Inventory também aceitarem
chamadas sem autenticação HTTP. Simuladores locais comprovam comportamento da OS;
não substituem essa homologação.
