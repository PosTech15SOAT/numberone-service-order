# Orientações de implementação da equipe · Fase 4

**Definição de 07/11/2026, por delegação de Matheus ao Codex.** Este arquivo fecha as decisões DEC-01 a DEC-20 da revisão 1B para permitir o desenvolvimento em paralelo. Cada responsável implementa o seu lado do contrato. O aceite individual dos demais membros será registrado quando revisarem suas entregas.

Esta definição sucede os pontos em aberto de `2.0.0-review.1` e da seção 7 do [contrato arquitetural](../../../specification/revision-1b/contratos-saga-arquitetura-v2.md). Os schemas/OpenAPI desse pacote ainda representam a revisão anterior. A primeira tarefa de integração é publicar uma revisão dos artefatos que incorpore as decisões abaixo. Use este documento quando houver divergência com os exemplos antigos.

**Materialização local.** O pacote `2.0.0-os-baseline.1` incorpora estas definições e já está nos checkouts das fases 1 e 2. A [preparação da fase 3](phase3-handoff.md) registra a validação e a preservação das alterações existentes.

## 1. O que foi conferido

| Fonte em 07/11/2026 | Estado observado | Consequência |
| --- | --- | --- |
| OS · checkout da fase 1 | Commit local `afdd830`, pacote `2.0.0-review.1`, schemas, OpenAPI e matriz com 20 decisões. | Reaproveitar tooling e atualizar os contratos descritos aqui. A referência remota local ainda aponta para `c73dfb2`. |
| OS · checkout da fase 2 | Base `13817e5`, 11 arquivos já preparados no Git e adaptação posterior ainda sem commit. Existem `CompositionSnapshot`, `compositionVersion` e migration V2. | Preservar o trabalho. Aprovação vinculada à composição e consumo direto já têm implementação local. Início, entrega e integrações distribuídas seguem bloqueados na API. |
| Relatórios locais da fase 2 | 134 testes, zero falhas/erros/pulos. JaCoCo registra 1.273 de 1.363 linhas, 93,40%. | Evidência da adaptação local. Nesta análise foram lidos os relatórios existentes. A integração dos três serviços ainda precisa de execução própria. |
| Billing · main `6c1af681` | Árvore remota contém README e gitignore. | Os contratos podem orientar a implementação inicial. Trabalho ainda não enviado ao GitHub não foi observado. |
| Inventory · main `8e8afa1f`, bootstrap `0f6e4e97` | Main inicial. Feature contém aplicação Spring Boot 4.1.2, Java 21, JPA/Flyway e teste de contexto. | Reaproveitar a base tecnológica de Anderson. As interfaces HTTP/JSON permitem tecnologia independente por serviço. |
| Infra de Inventory · branch `feature/inventory-db-isolation`, `481f0328` | Há SQL para `inventory_db`, itens e movimentações. A V2 proposta contém reserva, `processed_message` e outbox. Os scripts estão na infraestrutura. | Adaptar ao consumo direto. Versionar migrations da aplicação no Inventory. Conferir aplicação real antes de mexer em scripts já executados. |
| Auth · main `c0464abc` | Login Python lê cliente por CPF no SQL e mantém usuário/RBAC próprios. Usa id, nome, documento, e-mail e ativo do cliente. | Trocar apenas o lookup do cliente por contrato interno da OS, preservando os claims e o restante do login. |

Fontes remotas consultadas por leitura da API GitHub: [Billing](https://github.com/PosTech15SOAT/numberone-billing-service/tree/6c1af681759a5618160f3d0d755ed5764321d67c), [Inventory bootstrap](https://github.com/PosTech15SOAT/numberone-inventory-service/tree/0f6e4e978d04bf90b55a29f0513d857e9cc7ac51), [extração de banco Inventory](https://github.com/PosTech15SOAT/postech15soat-infra-database/tree/481f0328c2f19179a4ffb8f33f81a06b151d2785), [auth](https://github.com/PosTech15SOAT/numberone-app-auth/tree/c0464abc38cef6b6a667cb89de2f695d80a17c25). Revisões versionadas comprovam código, não provisionamento AWS.

## 2. Fluxo e responsabilidades

1. **OS / Matheus** cadastra cliente/veículo, abre a ordem e registra diagnóstico, itens e materiais previstos.
2. **Billing / Julio** consulta a composição técnica e os preços necessários, cria a revisão comercial e disponibiliza o orçamento para decisão.
3. Billing publica os eventos comerciais. OS aceita a aprovação vinculada à composição e fica pronta para iniciar itens.
4. Funcionário solicita início de um item na OS. OS persiste a operação e chama Inventory por REST. **Inventory / Anderson** confirma a baixa agrupada e registra os movimentos.
5. OS confirma o início. A equipe pode usar as peças após essa confirmação. Conclusão técnica gera OS `COMPLETED`, Saga `WAITING_PAYMENT` e a tarefa de histórico.
6. Funcionário inicia PIX/cartão no Billing. Billing integra com Mercado Pago e publica o pagamento confirmado.
7. OS consulta quitação oficial antes da entrega. Com quitação e guardas locais válidas, registra `DELIVERED` e encerra a Saga.
8. **Plataforma / Marcelo** fornece os recursos, conectividade, identidade, segredos e observabilidade necessários.

## 3. Matriz de decisões fechadas

| Decisão | Definição de implementação | Responsável pelo código |
| --- | --- | --- |
| DEC-01 · SENT | Orçamento disponível para decisão é `SENT`. E-mail tem acompanhamento e retry próprios. Evento e estado no mesmo commit. | Julio |
| DEC-02 · revisão vigente | Uma revisão comercial vigente por OS, com `budgetVersion` crescente e identidade imutável. Consulta de recuperação descrita na seção 4. | Julio + Matheus |
| DEC-03 · composição | Adotar `compositionVersion` nos três eventos de orçamento e no CT-03. Snapshot técnico imutável por OS/versão. | Matheus + Julio |
| DEC-04 · valor | `BRL`, valor aprovado positivo, string decimal com duas casas nos contratos internos/eventos. Aprovação integral. | Julio + Matheus |
| DEC-05 · atualização financeira | Incluir `PagamentoAtualizado` para estorno integral confirmado de um pagamento. Autorização de estorno é manual. | Julio + Matheus |
| DEC-06 · quitação | CT-07 considera todos os pagamentos válidos. Consultar também no ato da entrega. Prontidão prévia é projeção. | Julio + Matheus |
| DEC-07 · consumo | Agrupado por item, todos-ou-nenhum, idempotente e sem reserva antecipada. Quantidades repetidas do mesmo material são somadas. | Anderson + Matheus |
| DEC-08 · fechamento | `CLOSED` impede baixa futura. `REVERSED` confirma reversão de baixa existente. Retornos da seção 6. | Anderson + Matheus |
| DEC-09 · evidência | Conferência autorizada com autor, instante e referência. Peça retirada só retorna ao saldo após devolução válida. | Matheus + Anderson |
| DEC-10 · janela | Compensação automática somente integral e antes do primeiro início confirmado, com não uso conferido. | Matheus |
| DEC-11 · orçamento cancelável | `DRAFT`, `SENT`, `REJECTED` e `APPROVED` podem ser cancelados na janela, com revisão vigente e sem efeito financeiro. Fechar contexto no Billing. | Julio + Matheus |
| DEC-12 · retirada cancelada | Manter OS `CANCELLED` e Saga `COMPENSATED`. Registrar retirada em campo próprio, após resolver efeitos. | Matheus |
| DEC-13 · estorno/entrega | Estorno exige autorização e reconciliação no Billing. Coordenar manualmente o estorno da cobrança que quita a OS com a entrega. | Julio + Matheus |
| DEC-14 · SQS | Uma fila Standard Billing → OS, com DLQ Standard, inbox e recuperação por estado. | Marcelo + Julio + Matheus |
| DEC-15 · serviço-serviço | Bearer opaco distinto por par chamador/destino, rotas autorizadas e rede privada. Contexto público continua no Gateway. | Marcelo + donos |
| DEC-16 · bancos | Um RDS existente. OS `numberone_os`, Billing `numberone_billing`, Inventory `inventory_db`, cada um com usuário exclusivo. | Marcelo + donos |
| DEC-17 · histórico | DynamoDB `vehicle_maintenance_history`, com prefixo de ambiente, projetado pela OS via outbox local. | Marcelo + Matheus |
| DEC-18 · login | Auth consulta cliente por POST interno na OS. Mantém seus usuários, RBAC e emissão atual de JWT. | Marcelo + Matheus |
| DEC-19 · API | Baixa no início por item. Rota global valida prontidão. Preservar os DTOs atuais da fase 2 e acrescentar campos técnicos. | Matheus |
| DEC-20 · aceite | Validar contratos e, depois, persistência/concorrência e integração real. Revisão entre fases continua com Matheus. | Todos |

## 4. Orçamento e composição · Julio e Matheus

### 4.1. Identidades

`compositionVersion` é gerada pela OS e muda quando a composição técnica elegível muda. `budgetVersion` é gerada pelo Billing e muda com a revisão comercial. A chave técnica é `(serviceOrderId, compositionVersion)`. Uma versão comercial aponta para exatamente uma composição e guarda seu snapshot de preços/quantidades.

CT-03 retorna a composição e sua versão em uma leitura consistente. Quantidades de materiais são totais do item. `serviceAmount` é o valor total de mão de obra daquele item. A OS local já multiplica preço unitário pela quantidade do serviço. Billing usa esse total uma vez e soma os materiais pelo preço comercial congelado.

Após aprovação, o Billing preserva a revisão e impede criar outra revisão vigente naquela OS. A OS congela os serviços e materiais aprovados. Adicionais exigem outra OS. Antes de aprovação, nova revisão após rejeição recebe novo número. Uma revisão `SENT` deve ter sua decisão ou cancelamento registrado antes da substituição.

### 4.2. Snapshot técnico nos eventos

O envelope continua com `eventId`, `eventType`, `schemaVersion`, `occurredAt`, `correlationId` e `payload`. Nos eventos EV-01, EV-02 e EV-02B, `compositionVersion` passa a ser obrigatório. Os produtores e consumidores ainda estão em preparação, então publicar uma revisão identificada do pacote antes da primeira integração. `schemaVersion: 1` identifica a estrutura inicial publicada desses tipos.

Exemplo de EV-02 para orientar a atualização do schema.

```json
{
  "eventId": "66000000-0000-4000-8000-000000000002",
  "eventType": "OrcamentoAprovado",
  "schemaVersion": 1,
  "occurredAt": "2026-11-07T13:15:00Z",
  "correlationId": "77000000-0000-4000-8000-000000000002",
  "payload": {
    "serviceOrderId": "54e94616-70ad-4ce7-b6f7-41c6747d802e",
    "budgetId": "b1fb7c09-d680-4227-81c5-6b38dbaa88b9",
    "budgetVersion": 1,
    "compositionVersion": 3,
    "approvedAmount": "350.00",
    "currency": "BRL"
  }
}
```

### 4.3. Recuperar evento fora de ordem

Adicionar **CT-10** no Billing.

`GET /internal/budgets/service-orders/{serviceOrderId}/current`

```json
{
  "serviceOrderId": "54e94616-70ad-4ce7-b6f7-41c6747d802e",
  "budgetId": "b1fb7c09-d680-4227-81c5-6b38dbaa88b9",
  "budgetVersion": 1,
  "compositionVersion": 3,
  "status": "APPROVED",
  "amount": "350.00",
  "currency": "BRL"
}
```

Retornos definidos são `200` com revisão vigente, `404 BUDGET_NOT_FOUND` quando ainda não existe orçamento e `503/504` quando inconclusivo. O 404 é uma observação naquele instante.

Se aprovação chegar antes de EV-01, a OS pode confirmar o vínculo por CT-10 e aplicar registro do orçamento e decisão no mesmo commit local. A consulta ocorre fora da transação da inbox. Antes de aplicar, a OS revalida sua versão, a composição e eventual cancelamento. Contexto divergente leva a reconciliação ou descarte obsoleto conforme a revisão. Após commit, confirma o SQS. EV-01 atrasado da mesma revisão é ignorado. Retentar de forma limitada se o contexto ainda estiver indisponível.

### 4.4. Cancelar mesmo sem budgetId conhecido

Adicionar **CT-11** no Billing.

`POST /internal/budgets/service-orders/{serviceOrderId}/cancellation`

Pedido com `Idempotency-Key = operationId`.

```json
{
  "operationId": "55000000-0000-4000-8000-000000000001",
  "reason": "SERVICE_ORDER_CANCELLED"
}
```

Resposta `200` tem `operationId`, `serviceOrderId` e `result`. Com orçamento cancelado, `result=CANCELLED` e incluir `budgetId`/`budgetVersion`. Sem orçamento, `result=NO_BUDGET` e o contexto fica fechado mesmo assim. Conflito, execução já iniciada ou efeito financeiro impedem sucesso.

Billing mantém uma linha de contexto por OS e serializa nela criação, decisão, cobrança e cancelamento. O fechamento pode criar a linha mesmo antes de existir orçamento. Um pedido de criação atrasado encontra contexto fechado. `404` de CT-10 sozinho não resolve a etapa de compensação. CT-09 por `budgetId` permanece compatível e usa a mesma regra de fechamento, verificando a revisão esperada.

A OS bloqueia o início local antes de chamar o cancelamento. Billing verifica a OS para confirmar elegibilidade antes de fechar o contexto. Uma falha dessa verificação mantém a operação pendente ou recusada para retry, com resultado explícito. Uma vez iniciada a compensação, a OS mantém o bloqueio até resolver ou encaminhar a intervenção.

CT-02A inclui `cancellationInProgress` obrigatório. A OS o retorna verdadeiro durante o bloqueio da compensação. O Billing verifica esse campo e o estado comercial antes de processar CT-09/11.

Esses dois endpoints são complementos técnicos definidos nesta revisão. CT-10 resolve recuperação do orçamento e CT-11 resolve o fechamento sem identidade comercial conhecida.

## 5. SQS, inbox, outbox e pagamentos

| Mensagem | Publica | Consome | Dados específicos obrigatórios |
| --- | --- | --- | --- |
| EV-01 `OrcamentoEnviado` | Billing | OS | OS, orçamento, revisão comercial e `compositionVersion`. |
| EV-02 `OrcamentoAprovado` | Billing | OS | Campos de EV-01, `approvedAmount` positivo e `currency=BRL`. |
| EV-02B `OrcamentoRejeitado` | Billing | OS | Campos de EV-01. |
| EV-03 `PagamentoConfirmado` | Billing | OS | OS, `paymentId`, `paymentStatus=PAID`, `paymentVersion`. |
| EV-04 `PagamentoAtualizado` | Billing | OS | OS, `paymentId`, `paymentStatus=REFUNDED`, `paymentVersion` posterior. |

EV-04 entra no contrato ativo. O estorno é solicitado por responsável autorizado, no Billing ou no painel do provedor, e só vira `REFUNDED` depois de confirmado pela integração. O escopo inicial cobre estorno integral de um pagamento. Pagamento recusado ou expirado pode ser consultado no Billing e mantém entrega bloqueada. Estorno parcial exige evolução explícita do contrato.

Billing salva mudança e evento na mesma transação SQL. Worker publica depois, com `eventId` estável. OS salva inbox, transição/projeção e tarefa decorrente no mesmo commit. Faz ACK ao SQS após commit. Duplicata idêntica recupera o resultado. IDs iguais com conteúdo divergente são conflito. Eventos de pagamento são ordenados pela versão de cada pagamento. Revisões comerciais usam identidade, versão e vínculo técnico. [Outbox transacional na AWS](https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html)

**Fila definida** · `numberone-billing-events`, Standard, com `numberone-billing-events-dlq`, Standard. Configuração inicial com long polling 20 s, visibilidade 60 s extensível, `maxReceiveCount=5`, retenção principal 4 dias e DLQ 14 dias. Marcelo ajusta valores se a medição do ambiente exigir, registrando a mudança. Usar criptografia gerenciada SSE-SQS e permissões compatíveis com a conta Academy. Billing envia e OS recebe/exclui. Redrive pertence à operação da plataforma. Filas Standard permitem duplicação e entrega fora de ordem, tratadas pela inbox e por CT-10. [Características de filas Standard](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/standard-queues.html)

Manter registros de inbox, outbox, operações e fechamentos durante o projeto. Horizonte de replay automático máximo de 30 dias. Evento mais antigo exige reconciliação antes de nova publicação. A política de limpeza posterior precisa preservar as identidades necessárias à auditoria. Retentativas técnicas conservam o ID da operação e o conteúdo. Uma nova chave corresponde a uma nova ação do usuário. [Retries idempotentes na AWS](https://aws.amazon.com/builders-library/making-retries-safe-with-idempotent-APIs/)

CT-07 calcula quitação usando o valor devido e o saldo válido dos pagamentos após estornos. `settled=true` autoriza prosseguir somente com as guardas locais satisfeitas. OS consulta após eventos financeiros para a indicação `READY_FOR_DELIVERY` e consulta novamente ao entregar. Timeout mantém verificação pendente e bloqueia entrega. A cobrança excedente estornada pode deixar a OS quitada pela cobrança correta.

## 6. Inventory · consumo, fechamento e reversão

### 6.1. Contratos

| Contrato | Resultado |
| --- | --- |
| CT-05 · POST consumo | `200 CONSUMED` com movimentos. Falta confirmada de saldo é `409 INSUFFICIENT_STOCK`, resultado `REJECTED` e movimentos vazios. |
| CT-08 · GET operação | `200` para `CONSUMED`, `REJECTED`, `CLOSED` ou `REVERSED`. `202 PENDING` se persistida e ainda em processamento. `404 UNKNOWN` se desconhecida. |
| CT-08B · POST reversão/fechamento | `200 CLOSED` sem movimento ou `200 REVERSED` com movimento inverso. |
| Repetição de CT-05 depois de fechamento | `409 ATTEMPT_CLOSED` ou `409 ATTEMPT_REVERSED`. |
| Mesma identidade e pedido diferente | `409 IDEMPOTENCY_CONFLICT`. |

`Idempotency-Key` deve corresponder a `operationId` em CT-05 e a `compensationId` em CT-08B. A identidade do consumo é global no domínio Inventory, restrita ao chamador OS. Consumo e fechamento sempre disputam a mesma operação original. Se fechamento chegou primeiro, pode não existir fingerprint de consumo. Nesse caso, a tentativa já está encerrada e continua impedindo qualquer baixa posterior.

Normalizar materiais por UUID, somar quantidades repetidas, ordenar e validar inteiros positivos e overflow antes de calcular o fingerprint. Excluir correlação e metadados de transporte do fingerprint. Incluir operação/recurso, materiais normalizados e responsável. Guardar resultado e movimentos junto com o saldo na mesma transação. Bloquear itens em ordem estável para reduzir disputas entre pedidos.

Uma falta de saldo recusada permanece recusada com a mesma chave mesmo após reposição. Nova tentativa explícita usa outro ID. Durante cancelamento, a OS para de retentar CT-05 e resolve por CT-08/08B. Resultado desconhecido continua pendente.

### 6.2. Dados que Anderson precisa criar

- `inventory_operation` com `operation_id` único, chamador, OS/item quando conhecidos, fingerprint, pedido normalizado, resultado, motivos e datas.
- Operação de compensação com `compensation_id` único e vínculo ao consumo original. Garantir um único movimento inverso efetivo por movimento original.
- Movimentação com referência de operação e, na reversão, referência ao movimento revertido. Saldo, movimentos e resultado têm commit conjunto.
- Conferência recebida com `unusedConfirmed` e `evidence` contendo `verifiedBy`, `verifiedAt` e `reference`. A OS obtém o autor do contexto autenticado e exige permissão de conferência. Inventory aceita esse dado apenas da identidade OS autorizada.

Se a peça nunca saiu fisicamente, a evidência confirma ausência de uso. Se saiu, a referência aponta para a devolução válida conferida. Material usado exige intervenção. `CLOSED` mantém saldo. `REVERSED` soma somente o que foi baixado e retornou ao estoque.

### 6.3. Ajuste do trabalho existente

A feature de infraestrutura já tem `item_estoque` e `movimentacao_estoque`, úteis para a aplicação. A reserva antecipada e a publicação SQS no Inventory ficam descontinuadas neste desenho. Se os scripts ainda forem rascunhos, preparar as migrations finais no repositório Inventory. Se já foram aplicados, preservar checksum e migrar incrementalmente. Saldos e reservas existentes exigem conciliação antes do corte. O deploy do serviço executa apenas suas migrations.

## 7. OS · estados, API e pontos de ajuste

Saga em PostgreSQL com `IN_DIAGNOSIS`, `WAITING_APPROVAL`, `WAITING_REVISION`, `READY_FOR_EXECUTION`, `START_PENDING`, `IN_EXECUTION`, `WAITING_PAYMENT`, `READY_FOR_DELIVERY`, `COMPLETED`, `COMPENSATING`, `COMPENSATED` e `MANUAL_INTERVENTION`. Operações persistidas por item guardam o resultado de cada tentativa.

A primeira confirmação de início leva a `IN_EXECUTION`. Início de outros itens mantém essa etapa global. Conclusão técnica leva a `WAITING_PAYMENT`. Entrega conclui a Saga. Compensação só conclui após estoque resolvido e contexto comercial fechado por CT-09 ou CT-11.

| Entrada HTTP | Comportamento definido |
| --- | --- |
| PATCH item `/iniciar` | Persistir tentativa. `202` com `startOperationId` se pendente. `200` quando início estiver confirmado. Item sem material pode confirmar localmente. |
| PATCH ordem `/iniciar` | Validar aprovação vigente e retornar `200` com ordem/prontidão técnica. O início físico é confirmado por item. |
| GET acompanhamento | Expor status comercial, estado da Saga, tentativa e resultado por item. |
| PATCH ordem `/cancelar` | Para cancelamento elegível, `202` com operação de compensação. Fechar o contexto comercial por CT-09/11 também antes da aprovação, pois uma criação pode estar em trânsito. Etapas sem consumo são dispensadas com resultado registrado. Conferência e motivo podem ser enviados em corpo opcional para preservar a rota. Se forem necessários e faltarem, recusar com código explícito. |
| PATCH ordem `/entregar` | `200` com ordem após CT-07 positivo e guardas locais. `409` para regra de negócio, `503/504` para consulta inconclusiva. |

Novos clientes devem enviar `Idempotency-Key` em início e cancelamento. Para a rota legada sem chave, a OS recupera a tentativa ativa ou a última tentativa durável daquele item/composição. Depois de uma recusa, devolver o resultado conhecido até que uma nova ação traga nova chave. Assim, um retry antigo não cria baixa após a reposição. Campos técnicos são aditivos. Preservar valores e datas do DTO atual da fase 2. O OpenAPI 1B que propõe restaurar valores numéricos legados precisa ser alinhado ao código antes da publicação.

Para retirada após cancelamento integral compensado, adicionar `POST /api/admin/ordens-servico/{id}/retirada`, com chave idempotente e permissão de entrega. Registrar `withdrawnAt` e autor. Exigir OS `CANCELLED`, efeitos resolvidos e conferência física da liberação do veículo. Manter estado comercial e Saga encerrados. Custos após execução ou cancelamento parcial ficam em revisão operacional e bloqueiam a retirada automática até resolução explícita.

### Ajustes identificados no código local

1. A fase 2 já mantém snapshots técnicos e a V2 aceita consumo direto. Reaproveitar a implementação. Aprovação antiga sem vínculo técnico permanece auditável e exige reconciliação para novo início.
2. `requestStart()` e `requestDelivery()` ainda lançam bloqueios de integração. Substituir esses bloqueios somente com as operações duráveis e CT-05/07 nas fases 3 e 4.
3. `cancel()` ainda é restrito à OS recebida/em diagnóstico. Estender o caminho da Saga na fase 5. A trigger `approved_item_membership_immutable` da V2 também bloqueia mudar item aprovado para cancelado. Criar migration posterior que preserve o snapshot técnico/comercial e permita apenas a transição operacional de cancelamento autorizada. Evitar chamar o cancelamento que altera composição como se fosse edição de orçamento.
4. A V2 local e a revisão 1B estão em checkouts com ancestrais diferentes. Incorporar os contratos sem perder os 11 arquivos já preparados e a adaptação posterior. Revisar diferenças e registrar commit antes da fase seguinte.
5. Implementar CT-03 e lookup interno com leitura de snapshots consistente. `CompositionSnapshot` guarda quantidade do serviço e total de mão de obra. O contrato precisa explicar isso e manter IDs/quantidades de materiais iguais aos do snapshot.
6. `approveBudget()` e a V2 ainda aceitam total zero. Ajustar a validação para aprovação positiva conforme DEC-04, preservando a leitura de registros históricos. Usar migration incremental se a regra também for protegida no banco.

## 8. Plataforma, autenticação e bancos · Marcelo

### Identidade de serviço

Usar um token opaco aleatório distinto por par, como OS → Billing, OS → Inventory, Billing → OS, Billing → Inventory e Auth → OS. Distribuir por Secrets Manager/Secret do workload. Enviar em `Authorization: Bearer`. O destino identifica o chamador pelo token e permite apenas suas rotas. Comparar em tempo constante, aceitar versão atual/anterior durante rotação e manter segredos fora do Git/logs. Em ambiente compartilhado, proteger por TLS e rede privada. Testes locais usam tokens fictícios configurados.

Manter o Gateway sobrescrevendo os headers públicos de identidade e restringir acesso direto. A credencial interna permite o escopo de serviço, e o funcionário é auditado pelo contexto obtido na entrada. O MVP permanece em uma filial configurada por serviço. Se houver múltiplas filiais, evoluir os contratos de escopo antes de habilitar uso entre filiais.

### Lookup de cliente para o auth

Adicionar na OS `POST /internal/auth/customer-lookup`, exclusivo de Auth → OS, com corpo `{"document":"CPF_SOMENTE_DIGITOS"}`. Normalizar/validar CPF. Retorno `200` com `id`, `nome`, `documento`, `email` e `ativo`, os campos que o login já usa. `404` indica cliente inexistente. Cliente inativo volta com `ativo=false`, preservando a decisão de autorização do login. `503/504` bloqueia a emissão do token por indisponibilidade.

Marcelo troca `find_active_customer_by_cpf` pelo adaptador HTTP. Usuário e permissões do auth continuam em seu armazenamento próprio. Remover eventual FK do auth para a tabela de cliente no corte por migration controlada, preservando o UUID como referência externa. Login público e claims continuam os atuais.

### Dados e recursos

- Provisionar `numberone_os`, `numberone_billing` e `inventory_db` na instância RDS existente com usuários distintos e sem permissão de ler tabelas alheias. O Compose OS pode manter `service_order_db`. Configurar URL/usuário/segredo por ambiente. Migrações de tabelas ficam nas aplicações.
- Provisionar SQS/DLQ e seus outputs no repositório cloud, com política de origem/consumo restrita e alarmes. Usar as permissões efetivas do Academy e registrar limites do laboratório.
- Provisionar DynamoDB no repositório de infraestrutura dos bancos. Nome físico `numberone-production-vehicle-maintenance-history`, modo sob demanda, chave `vehicleId` e chave de ordenação `completedAt#serviceOrderId#historyVersion`. Atributos de chave concretos são `vehicleId` e `historyKey`. Ambiente local usa tabela equivalente. Acesso de escrita/leitura apenas pela OS. Preservar revisões sem TTL automático.
- A OS escreve a partir de snapshot/outbox no PostgreSQL ao concluir uma manutenção executada. Chave estável e conteúdo imutável tornam o retry verificável. Consultar por veículo com paginação e autorização. Falha da projeção não bloqueia pagamento ou entrega.
- Mantêm-se Gateway/authorizer, EKS, NLB e Datadog da organização. Configurar rotas e serviços próprios, CI independente, qualidade e deploy por serviço.

## 9. Tarefas imediatas por membro

| Membro | Próxima entrega independente | Integração seguinte |
| --- | --- | --- |
| Matheus | Publicar revisão 1B com composição obrigatória, EV-04 ativo, CT-10/11, lookup, API de início/cancelamento e retirada. Reconciliar com a adaptação local da fase 2. | Inbox/Saga, consumo/compensação, CT-07, histórico e evidências por fase. |
| Julio | Implementar contexto comercial por OS, CT-10/11, orçamento versionado, snapshot e outbox dos cinco eventos. | CT-07/09, pagamento iniciado após OS concluída, reconciliação Mercado Pago e estorno integral autorizado. |
| Anderson | Reaproveitar bootstrap e catálogo/movimentos. Implementar as tabelas de operação e CT-05/08/08B com atomicidade e fechamento. | Testar pedidos repetidos, falta de saldo, corrida entre baixa e fechamento e reversão única. |
| Marcelo | Preparar bancos/usuários, tabela de histórico, fila/DLQ, tokens e variáveis de ambiente com outputs documentados. | Lookup auth, rotas, CI/CD, governança, alarmes e integração no ambiente. |

## 10. Checklist de revisão antes da integração

- [ ] Nova revisão dos schemas/OpenAPI indica este arquivo como base e passa no validador offline.
- [ ] CT-03 e os três eventos comerciais usam a mesma composição. Aprovação atrasada não libera itens de outra versão.
- [ ] Recuperação por CT-10 funciona quando EV-02 chega antes de EV-01.
- [ ] CT-11 fecha contexto sem orçamento e bloqueia criação comercial atrasada.
- [ ] Início duplicado não gera outra baixa. Nova tentativa após reposição usa nova chave explícita.
- [ ] Inventory consumiu, mas a resposta se perdeu. CT-08 recupera o mesmo resultado.
- [ ] Fechamento antes da baixa mantém saldo. Reversão depois da baixa válida repõe uma única vez.
- [ ] Cancelamento aprovado alcança C6 preservando os snapshots e a auditoria.
- [ ] Reparo conclui sem pagamento. CT-07 falso ou indisponível bloqueia entrega.
- [ ] EV-04 mais novo recebido antes do EV-03 preserva o estado financeiro correto.
- [ ] Retirada cancelada preserva a distinção entre cancelamento e reparo concluído.
- [ ] Login funciona após extração do cliente. Histórico DynamoDB retoma após falha.

O próximo aceite técnico é a revisão dos contratos atualizados. Depois, cada fase prova os comportamentos do seu escopo com persistência e participantes identificados. A [preparação da fase 3](phase3-handoff.md) orienta a próxima execução.
