# Contexto verificado na organização

Leitura realizada em **31/10/2026** pela API GitHub. Os arquivos abaixo foram consultados em revisões identificadas; isso comprova configuração versionada, não uma inspeção do estado atual da AWS ou a execução dos pipelines.

## Fontes de referência

| Repositório existente | Revisão consultada de main | Uso nesta implementação |
| --- | --- | --- |
| [numberone-app-auto-service-api](https://github.com/PosTech15SOAT/numberone-app-auto-service-api/tree/d7e489064bff04ce9620eaf48f9d070d8a13f86d) | `d7e489064bff04ce9620eaf48f9d070d8a13f86d` | Aplicação de origem, padrões, testes, autenticação, Kubernetes e pipelines |
| [postech15soat-infra-cloud](https://github.com/PosTech15SOAT/postech15soat-infra-cloud/tree/20c9a9b6f1215a47160269ec21c07d1e6ffe2d61) | `20c9a9b6f1215a47160269ec21c07d1e6ffe2d61` | VPC/EKS/ECR e observabilidade existentes |
| [postech15soat-infra-database](https://github.com/PosTech15SOAT/postech15soat-infra-database/tree/65fe6bba1e90df66f590e9c692f41b9d797e0fd9) | `65fe6bba1e90df66f590e9c692f41b9d797e0fd9` | RDS e estado Terraform dos bancos |
| [numberone-app-auth](https://github.com/PosTech15SOAT/numberone-app-auth/tree/c0464abc38cef6b6a667cb89de2f695d80a17c25) | `c0464abc38cef6b6a667cb89de2f695d80a17c25` | Login, authorizer, API Gateway/VPC Link e contrato de identidade |
| [postech15soat-governance](https://github.com/PosTech15SOAT/postech15soat-governance/tree/2d35180492efcf2ff6fcdd6ab865e8cff7abe151) | `2d35180492efcf2ff6fcdd6ab865e8cff7abe151` | Rulesets e checks obrigatórios |

Antes de começar, conferir se main/develop avançaram e comparar as diferenças relevantes. Usar essas revisões como evidência de origem; não descartar correções posteriores. Não extrair código de uma branch de feature sem registrar a escolha. O repositório local `D:/Dev/PosTech15SOAT` contém a proposta, mas sua aplicação corresponde a uma base anterior.

## Diferenças que o modelo precisa respeitar

1. **Autenticação já foi separada.** A aplicação da organização recebe contexto do Gateway; não copiar o login JWT administrativo do checkout local antigo para a nova OS.
2. **Entrada atual usa NLB interno.** Não trocar por ALB para seguir literalmente o desenho inicial da proposta.
3. **Datadog já faz parte da fase 3.** Reaproveitar correlação, APM, métricas e logs estruturados; não substituir tudo por CloudWatch.
4. **AWS Academy está nos workflows.** O deploy atual usa credenciais temporárias e session token. OIDC/Pod Identity não estão comprovados como disponíveis; Marcelo valida a opção viável. A OS usa a cadeia de credenciais do SDK, sem chaves em código/imagem.
5. **Existem Local e Production.** `develop` é branch de integração, não ambiente cloud de homologação. Criar ambiente adicional seria decisão nova.
6. **CI e deploy já existem.** Adaptar nomes, recursos e variáveis por serviço; não copiar o deploy legado com os mesmos Deployment, Service, secret, ECR ou banco.
7. **Governança está em repositório próprio.** Não recriar rulesets no Terraform da OS nem supor que um novo repositório já está protegido.

Fontes: [contrato de autenticação](https://github.com/PosTech15SOAT/numberone-app-auto-service-api/blob/d7e489064bff04ce9620eaf48f9d070d8a13f86d/doc/fase-3/authentication-contract.md), [CI](https://github.com/PosTech15SOAT/numberone-app-auto-service-api/blob/d7e489064bff04ce9620eaf48f9d070d8a13f86d/.github/workflows/ci.yml), [deploy](https://github.com/PosTech15SOAT/numberone-app-auto-service-api/blob/d7e489064bff04ce9620eaf48f9d070d8a13f86d/.github/workflows/deploy.yml), [infra cloud](https://github.com/PosTech15SOAT/postech15soat-infra-cloud/blob/20c9a9b6f1215a47160269ec21c07d1e6ffe2d61/README.md).

## Destino e limites de responsabilidade

**Novo repositório proposto:** `PosTech15SOAT/numberone-service-order`, preservando o nome da proposta. Ainda não existe na listagem consultada. Não renomear/apagar `numberone-app-auto-service-api`; ele continua como origem e legado durante a extração. Os nomes futuros de Billing/Inventory devem ser confirmados por seus donos, sem bloquear os schemas da Saga.

Reaproveitar `postech15soat-infra-database`, `postech15soat-infra-cloud` e `numberone-app-auth` nas três frentes de suporte descritas na proposta. Não criar outros três repositórios equivalentes com nomes novos. Preservar `postech15soat-governance` como apoio adicional. Assim, os seis repositórios de implementação previstos convivem com a governança; o legado pode continuar durante a transição.

| Dentro da OS | Fora da OS |
| --- | --- |
| Clientes, veículos, catálogo de mão de obra, OS, diagnóstico, serviços e materiais previstos | Cadastro/saldo/fornecedores/reservas/movimentos de materiais: Inventory |
| Execução e status dos itens, conclusão e entrega | Cálculo comercial, orçamento, aprovação e Mercado Pago: Billing |
| Saga, inbox/outbox, snapshots aprovados, histórico local | Login/authorizer/Gateway: numberone-app-auth, coordenado por Marcelo |
| Projeção/consulta de revisões no DynamoDB | Infra compartilhada e governança: repositórios existentes |

A OS usa PostgreSQL exclusivo e a tabela DynamoDB de histórico. O Compose da fase 2 usa `service_order_db`. O nome proposto no RDS pelo contrato atualizado é `numberone_os`. Cada serviço acessa apenas seu banco. Controles de segurança da OS continuam locais, usando o contexto autenticado existente.

## Pontos concretos para a extração

Na aplicação de origem, inspecionar especialmente:

- `ServiceOrderService`, `ServiceOrderItemService`, `ServiceOrderItemSupplyService`, `ServiceOrderTrackingService` e seus testes.
- `ServiceOrderBudgetService`: comportamento comercial vai para Billing; consultas da OS usam snapshot/contrato, sem escrita direta externa.
- `ServiceOrderEntity.budgets` e `ServiceOrderItemSupplyEntity.inventoryItemEntity`: remover relacionamentos persistentes para contextos externos.
- `InventoryWithdrawalGatewayImpl` e `ServiceOrderInventoryItemGatewayImpl`: retirar acesso local a repositories de Inventory e substituir por contratos.
- `ServiceOrder.calculateValue` e relatórios de valor: separar estimativa de diagnóstico de total aprovado, cuja autoridade é Billing.
- `db/migrations/V1` a `V6`: não copiar o schema inteiro, seeds de autenticação ou grants para o banco da OS. Criar baseline próprio para banco novo, preservando somente as estruturas da OS e IDs de migração de dados necessários.
- `shared/security`: `AuthenticatedUser`, providers gateway/local, filtro, permissions e acesso de cliente devem vir da versão fase 3.
- `shared/infrastructure/correlation`, observabilidade, `k8s`, `entrypoint.sh`, Dockerfile e workflows: reaproveitar e adaptar à identidade da OS.

A organização já possui Java 25/Spring Boot 4.0.5, Maven, JPA, Flyway, Testcontainers, Cucumber e JaCoCo. Manter essa base na OS reduz o trabalho; liberdade tecnológica dos outros serviços permanece. Não aproveitar o relatório JaCoCo existente como prova automática de 80%: definir/verificar gate no novo serviço, com exclusões justificadas e sem esconder código de negócio.

## Autenticação e separação do cliente: dependência a resolver

O código [src/shared/db.py do auth](https://github.com/PosTech15SOAT/numberone-app-auth/blob/c0464abc38cef6b6a667cb89de2f695d80a17c25/src/shared/db.py) consulta diretamente `cliente` por CPF e cria/consulta registros RBAC. Separar o banco da OS sem adaptar esse caminho quebraria o login ou manteria acesso cruzado.

**Direção proposta para alinhamento com Marcelo:** autenticação mantém seus dados de identidade/RBAC; dados cadastrais do cliente pertencem à OS. Lookup de cliente passa por contrato interno autenticado, ou por uma projeção própria do auth definida pelo responsável. Não conceder à Lambda credenciais do banco OS como solução final. O mecanismo escolhido, seu contrato e a transição precisam ser registrados na fase 1; a alteração no auth fica com Marcelo, não com a implementação da OS.

Essa decisão pode exigir armazenamento lógico adicional para identidade. Os quatro bancos da proposta são um mínimo, não um limite. Não incluir tabelas RBAC na OS somente para manter a contagem em quatro. Não aplicar corte de tráfego de produção antes de provar login e acesso do cliente após a separação.

## Contrato de segurança a preservar

Contexto de produção: `X-Authenticated-Subject`, `X-Authenticated-Customer-Id`, `X-Authenticated-Status`, `X-Authenticated-Roles`, `X-Authenticated-Permissions` e `X-Correlation-Id`, conforme fonte versionada. Não transportar JWT/CPF completo em eventos.

Esses headers só são confiáveis quando o Gateway sobrescreve os valores de entrada e o acesso direto ao workload é controlado. Provider local somente em desenvolvimento/testes. A OS continua verificando permissões, cliente dono do veículo/ordem e filial quando aplicável. Billing autoriza a operação na entrada e publica o evento na fila autorizada. A OS valida origem/fila e invariantes de negócio. Os eventos não carregam JWT de cliente.

## Acordos de execução

Trabalhar a partir de `develop`, com PR para `develop` e promoção `develop` → `main`. Manter os checks **Required validation** e **Validate promotion source** quando aplicáveis. O nome da branch de trabalho segue as instruções do executor/repositório. A revisão do Matheus por fase é explícita neste pedido, mesmo que o ruleset atual não exija quantidade mínima de aprovações.

Não alterar os cinco repositórios existentes automaticamente durante uma fase da OS. Preparar solicitações/diffs de integração para Marcelo e os donos, identificando mudanças necessárias. Readme de infra não comprova que recurso segue provisionado; validar estado real somente quando a fase de integração autorizar acesso AWS.
