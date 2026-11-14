# Arquitetura da Ordem de Serviço · Fase 4

A OS mantém clientes, veículos, catálogo de serviços, diagnóstico, itens, execução, conclusão e entrega. O orquestrador é parte da aplicação da OS. A divisão mantém as regras que alteram a mesma ordem no mesmo serviço e deixa preços, orçamento, pagamento e estoque com seus respectivos donos.

## Caminho do código

| Camada | Responsabilidade |
| --- | --- |
| API | Controllers administrativos, rastreamento da OS e histórico do veículo. CT-02A e CT-03 dão ao Billing estado comercial e composição versionada. |
| Aplicação | `StartServiceOrderItem` prepara e recupera o início, `FinishServiceOrder` conclui e entrega, `ProcessBillingEvent` aplica eventos e `SagaCompensation` coordena o cancelamento. |
| Domínio | `ServiceOrder`, itens e `CompositionSnapshot` aplicam regras de escopo, aprovação, execução e encerramento. |
| Infraestrutura | JPA para os agregados e JDBC nas operações da Saga que agrupam estado, intenção, resultado ou inbox. Adaptadores HTTP separados por participante, consumidor SQS e adaptador DynamoDB. |

`BillingEventCodec` valida as mensagens pelos schemas empacotados. `SqsBillingConsumer` entrega dados tipados ao caso de uso e confirma a mensagem após o commit. A reconciliação pelo CT-10 acontece fora da transação da inbox. A mesma transação grava `eventId`, hash, desfecho e efeito na OS. Reentrega com conteúdo idêntico fica deduplicada, e conteúdo divergente exige investigação.

## Dados e comunicações

| Componente | Estado de responsabilidade da OS |
| --- | --- |
| PostgreSQL exclusivo | Cadastros, OS e itens, snapshots de composição, `service_order_saga`, `saga_operation`, `inbox_event`, projeção de pagamento e `maintenance_history_outbox`. Flyway controla a evolução do schema. |
| DynamoDB | Projeção do histórico de manutenções. Chave de partição `vehicleId` e chave de ordenação `historyKey`. A conclusão grava a outbox local e o worker publica com escrita condicional. A consulta usa paginação e escopo de acesso. |
| REST com Inventory | Referência do material, consumo CT-05, consulta CT-08 e fechamento ou reversão CT-08B. O identificador persistido da operação acompanha toda repetição. |
| REST com Billing | Quitação CT-07, cancelamento CT-09, revisão vigente CT-10 e fechamento CT-11. |
| SQS vindo do Billing | `OrcamentoEnviado`, `OrcamentoAprovado`, `OrcamentoRejeitado`, `PagamentoConfirmado` e `PagamentoAtualizado`. A fila Standard usa DLQ e aceita entrega fora de ordem. |

## Saga orquestrada

A OS tem a informação necessária para decidir quando iniciar, cancelar e entregar. O estado da Saga serve para retomar operações e acompanhar as guardas de negócio. Billing e Inventory executam transações locais, armazenam os efeitos que produziram e respondem pelo resultado de cada tentativa.

1. A OS abre, registra o diagnóstico e define o escopo técnico. O Billing calcula o orçamento com a versão da composição e os preços do Inventory. A aprovação chega pelo SQS e, se necessário, a OS consulta a revisão vigente antes de congelar seu escopo.
2. O início do item com materiais grava uma intenção antes do CT-05. Consumo confirmado libera a execução. Recusa de saldo faz o item aguardar peças. Timeout mantém `operationId` e a execução pendente para consulta ou repetição. Um item sem materiais inicia localmente quando as guardas permitem.
3. A conclusão grava a projeção pendente de histórico e leva a Saga a `WAITING_PAYMENT`. Um evento financeiro agenda nova verificação. A entrega consulta CT-07 e revalida a OS no commit, com `DELIVERED` na ordem e `COMPLETED` na Saga.
4. Antes do primeiro início confirmado, cancelamento integral com não uso físico conferido resolve todas as tentativas de estoque. Inventory retorna `CLOSED` quando fecha tentativa sem baixa ou `REVERSED` após devolução conferida. Billing confirma `CANCELLED` ou `NO_BUDGET`. Então a OS grava `CANCELLED` e Saga `COMPENSATED`. Execução confirmada, uso físico ou efeitos sem confirmação podem exigir revisão operacional.

A confirmação da quitação e a entrega são transações em serviços diferentes. O contrato atual trata estorno antes da entrega com nova verificação e depois da entrega com registro financeiro, sem desfazer a entrega. A coordenação simultânea de estorno e entrega permanece uma decisão da equipe.

[Estados e compensações](contracts/saga/v2/state-transitions.md) · [Mensagens e identidade](contracts/saga/v2/messages.md) · [OpenAPI da Saga](contracts/saga/v2/README.md) · [Collection Postman](api/numberone-os.postman_collection.json) · [Runbook de recuperação](progress/runbook-fase-5.md)

## Execução, segurança e evidências

- Java 25, Spring Boot, PostgreSQL, Flyway, SDK AWS, Maven e GitHub Actions. O Docker publica o JAR validado, incluindo schemas, e preserva o agente Datadog. Kubernetes usa ClusterIP `numberone-os-service:8080`, liveness, readiness com banco e deploy manual para `main` com rollout automatizado.
- A revisão `f6212a7` exige Bearer e correlação UUID nas duas rotas internas de Billing. Operações administrativas dependem da identidade do gateway confiável. O Billing em `develop` já omite o Bearer nas chamadas internas. A política comum de rede e autenticação precisa ser alinhada e revalidada antes do aceite integrado.
- O CI executa testes unitários, integração com PostgreSQL, validação offline dos contratos, imagem e manifests. JaCoCo bloqueia abaixo de 80% de linhas. As evidências do CI e os relatórios de execução local cumprem papéis diferentes. [CI de f6212a7](https://github.com/PosTech15SOAT/numberone-service-order/actions/runs/43513063185) · [Guia Kubernetes](../k8s/README.md)
- Deploy EKS, isolamento de rede, validação da proteção de `main` e evidência final de observabilidade aguardam comprovação na conta da entrega.
