# Entrega para revisão e continuidade em nova conversa

Status: alterações locais revisáveis, **sem PR remoto**, contratos **DRAFT**.
Nome de destino proposto: `PosTech15SOAT/numberone-service-order` (público).
Nenhuma mensagem foi enviada à equipe.

## Referências da entrega

- 1A: branch `feature/fase-1a-base`, commit `11f3b82182ff694c6472e24e780d843642cde97b`.
- 1B: branch `feature/fase-1b-contratos`, descendente da 1A. Obter commit exato com
  `git rev-parse feature/fase-1b-contratos`; relatório e contratos estão nessa revisão.
- `main`/`develop` locais contêm apenas bootstrap; nenhum merge foi realizado.
- Relatório autossuficiente: [fase-1.md](fase-1.md); roteiro: [demonstração](demonstracao-fase-1.md).
- Origens: [SHAs e decisões](origens.md); entradas preservadas em [specification](../specification/NOTICE.md).

Publicação foi rejeitada pela revisão automática de permissões: criação de repo
público e envio de commits precisam de autorização explícita. Não existe link de
commit/PR remoto a inventar. Após autorização, confirmar novamente se destino
existe e ler/preservar conteúdo/instruções antes de publicar. Se novo, publicar só
bootstrap para main/develop; mudanças via PR. Sugestão de duas revisões: 1A → develop,
1B empilhado sobre 1A enquanto ela está em revisão; após integração autorizada da 1A,
retarget de 1B para develop. Não fazer merges automaticamente. Os workflows aceitam
temporariamente PR para a branch 1A para essa revisão empilhada.

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

Acrescentar links reais de commit/PR quando publicados, preservando status DRAFT até
aceite dos quatro revisores. A preparação desses textos não autoriza enviá-los.

## Prompt para uma nova conversa de revisão

```text
Revise somente a entrega da fase 1 (1A/base e 1B/contratos) do repositório OS.
Leia README.md, CONTRIBUTING.md, doc/progress/fase-1.md, origens.md,
entrega-fase-1.md, doc/specification e doc/contracts/saga/v1/README.md.
Use o checkout/commit ou PR fornecido junto deste prompt; confirme git status e SHA.
A implementação está local e contratos são DRAFT: revisão dos donos está pendente.
Os testes de contrato são fixtures, não integração com Billing/Inventory/SQS.
Preserve mudanças existentes. Não inicie fase 2, não faça merge nem deploy.
```

Fornecer junto: resultado de `git rev-parse HEAD`, link de PR se houver, último
relatório e pedidos de revisão recebidos. Não depender do histórico do chat.
