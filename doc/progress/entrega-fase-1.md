# Entrega para revisão e continuidade em nova conversa

Status: entregas publicadas em PRs para revisão, contratos **DRAFT**.
Destino: [PosTech15SOAT/numberone-service-order](https://github.com/PosTech15SOAT/numberone-service-order) (público).
Nenhuma mensagem foi enviada à equipe.

## Referências da entrega

- 1A: branch `feature/fase-1a-base`, commit `11f3b82182ff694c6472e24e780d843642cde97b`.
- [PR #1 — base da OS](https://github.com/PosTech15SOAT/numberone-service-order/pull/1), destino develop.
- 1B: branch `feature/fase-1b-contratos`, descendente da 1A. Obter commit exato com
  `git rev-parse feature/fase-1b-contratos`; relatório e contratos estão nessa revisão.
- [PR #2 — contratos da Saga](https://github.com/PosTech15SOAT/numberone-service-order/pull/2), empilhado sobre 1A.
- Contratos implementados no commit `eac1b18fbb94ed8c98a96a0ef76ee7e77bf210c0`; atualizações documentais posteriores não representam aprovação dos contratos.
- `main`/`develop` contêm apenas bootstrap; nenhum merge foi realizado.
- Relatório autossuficiente: [fase-1.md](fase-1.md); roteiro: [demonstração](demonstracao-fase-1.md).
- Origens: [SHAs e decisões](origens.md); entradas preservadas em [specification](../specification/NOTICE.md).

Publicação inicialmente bloqueada pela revisão automática, depois explicitamente
autorizada pelo usuário em 31/10/2026. Destino reconferido (404) antes da criação;
repositório público criado, bootstrap publicado para main/develop e entregas em
branches próprias. PRs #1/#2 abertos, sem merge ou deploy. Após integração autorizada
da 1A, ajustar a base da 1B para develop. Os workflows aceitam temporariamente PR
para a branch 1A para essa revisão empilhada. Governança continua pendente de Marcelo.

Definição compartilhável: [README do contrato Saga](https://github.com/PosTech15SOAT/numberone-service-order/blob/feature/fase-1b-contratos/doc/contracts/saga/v1/README.md).
Referência imutável do pacote: [contratos em eac1b18](https://github.com/PosTech15SOAT/numberone-service-order/tree/eac1b18fbb94ed8c98a96a0ef76ee7e77bf210c0/doc/contracts/saga/v1).

## Texto preparado para Matheus

“Base mínima Java 25/Spring Boot 4.0.5 e contratos em duas entregas. Revisar primeiro
1A: isolamento, health/readiness, deny-all, Docker/CI e evidências. Depois 1B:
ADRs, transições, API planejada, 21 schemas de mensagens e vetores. Mudanças HTTP
202/idempotência e cancelamento parcial ainda precisam de decisão. Fase 2 não iniciada.”

## Texto preparado para Julio

“Revisar `doc/contracts/saga/v1`: CreateBudget/RequestPayment/CancelBilling e eventos
financeiros, snapshot aprovado, composição R$ 350/um kit e sem materiais. Confirmar
preços/arredondamento/moedas/contato, resultado final de compensação e consulta por
attemptId. Rodar os mesmos vetores no serviço Billing; fixtures não comprovam integração.”

## Texto preparado para Anderson

“Revisar ReserveMaterials/ConsumeMaterials/ReleaseMaterials e resultados, alocações
por item, inteiros positivos, reserva atômica, movimentos e tentativa fechada antes
de comando atrasado. Confirmar consulta por operationId e resultado já consumido.
Rodar schemas/vetores da mesma revisão, sem dependência de biblioteca Java.”

## Texto preparado para Marcelo

“Validar filas/DLQs e outputs, IAM viável no Academy, NLB interno, Datadog e contexto
Gateway. Resolver lookup de cliente do auth antes de separar banco. OpenAPI interno
propõe credencial de serviço; mecanismo/rede pendentes. Governança tem pedido preciso
de inclusão, sem alteração aplicada; exigir checks só após execução real bem-sucedida.”

Usar os links de PR/commit acima, preservando status DRAFT até aceite dos quatro
revisores. A preparação desses textos não autoriza enviá-los.

## Prompt para uma nova conversa de revisão

```text
Revise somente a entrega da fase 1 (1A/base e 1B/contratos) do repositório OS.
Leia README.md, CONTRIBUTING.md, doc/progress/fase-1.md, origens.md,
entrega-fase-1.md, doc/specification e doc/contracts/saga/v1/README.md.
Use o checkout/commit ou PR fornecido junto deste prompt; confirme git status e SHA.
A implementação está nos PRs #1/#2 e contratos são DRAFT: revisão dos donos está pendente.
Os testes de contrato são fixtures, não integração com Billing/Inventory/SQS.
Preserve mudanças existentes. Não inicie fase 2, não faça merge nem deploy.
```

Fornecer junto: resultado de `git rev-parse HEAD`, link de PR se houver, último
relatório e pedidos de revisão recebidos. Não depender do histórico do chat.
