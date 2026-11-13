# ADR 0001 — limites e origem da OS

Status: proposta para revisão de Matheus. Data: 31/10/2026.

## Contexto

O legado da organização já separou autenticação e utiliza Java 25/Spring Boot
4.0.5, Gateway/NLB interno e Datadog. O checkout antigo da proposta não representa
essa base. [Revisões verificadas](../progress/origens.md) fixam a origem.

## Decisão proposta e implementação nesta fase

OS possuirá clientes, veículos, catálogo de mão de obra, diagnóstico, composição
prevista, execução/entrega, snapshots e orquestração. Billing é autoridade de
cálculo/aprovação/cobrança; Inventory de saldo/reserva/consumo. Não compartilhar
entidades, bibliotecas de domínio ou bancos. Contratos são JSON/HTTP, sem impor linguagem.

Fase 1 cria só aplicação mínima, testes técnicos/HTTP e pacote de contratos. Sem
CRUDs, JPA, tabelas, migrações, inbox/outbox, SQS, DynamoDB, Mercado Pago ou Lambdas.
PostgreSQL exclusivo `service_order_db` pertence à extração da fase 2; provisionamento
pertence aos repositórios de Marcelo. Banco não é requisito do bootstrap sem estado.

Manter o auth existente. Lookup direto de `cliente` pelo auth é dependência de corte:
propor POST interno autenticado ou projeção própria do auth, sem conceder acesso
ao banco OS como solução final. Proposta HTTP preparada; Marcelo decide mecanismo,
identidade de serviço e transição. RBAC não migra para a OS para preservar contagem de bancos.

## Consequências

Sem relações ORM com entidades externas. A implementação de domínio fica adiada
até revisão. Health não atesta banco/mensageria. Contexto de identidade só será
habilitado junto das features e da garantia de rede/Gateway; por ora deny-all fora
das probes. Não alterar infraestrutura, auth ou governança existente automaticamente.
