# Pedidos de integração e decisões abertas

Estado em 31/10/2026: propostas locais, não enviadas e não aprovadas. Os responsáveis
devem revisar schemas/fixtures do **mesmo commit**, não apenas concordar com o texto.
Sem aceite, a pasta v1 permanece DRAFT. Não altera os repositórios existentes.

| Dono | Decisão/entrega solicitada | Critério de revisão |
| --- | --- | --- |
| Matheus | Estados da OS/Saga, START_REQUESTED e novo HTTP 202; status de itens propostos, posse/filial, cancelamento parcial, Idempotency-Key | aprovar transições e HTTP; decidir divergências com comportamento atual antes da extração |
| Julio | CreateBudget/RequestPayment/CancelBilling, snapshot completo, preço congelado, revisão, money/currency, estados financeiros e consulta por attemptId | validar todos os schemas e vetores; definir cálculo/arredondamento/descontos/zero e repetir operação sem cobrar novamente |
| Anderson | Reserva all-or-nothing, alocações por item, consumo/movimentos, liberação/fechamento antecipado, consulta por operationId | validar schemas/vetores e fechamento de tentativa mesmo inexistente; confirmar quantidades inteiras e unidade absoluta por item |
| Marcelo | Filas/DLQs, outputs/IAM Academy, NLB interno/VPC Link, Datadog/trace, contexto Gateway, autenticação interna e lookup de cliente | definir configurações por ambiente, credencial de serviço/rede e plano de transição; governança somente após checks reais |

## Decisões acrescentadas ao rascunho, ainda sujeitas a aceite

1. Contato mínimo de orçamento é `{channel: EMAIL, address}`. Julio confirma canal,
   minimização e retenção. Só Billing recebe; não copiar contato para Inventory/logs.
2. `budgetRevision` obrigatório em todos os payloads para guarda uniforme, inclusive
   compensação. A matriz original não repetia esse campo em todas as linhas.
3. Quantidade de material é total alocado daquele item, sem multiplicar novamente
   por quantidade de mão de obra. Revisar unidades e preço de mão de obra com Julio/Anderson.
4. `checkoutReference` obrigatório em PaymentPending, URL HTTPS opcional. Referência
   não deve conter segredo/token/cartão. Política de domínio/URL e acesso ficam com Julio.
5. MaterialsConsumed inclui `movements` com movementId/materialId/quantity; Inventory
   deve confirmar suficiência desses dados e unicidade por operação.
6. BillingCompensated: NOT_CHARGED não exige IDs inexistentes; CANCELLED refere cobrança
   existente cancelada e exige budgetId/paymentAttemptId; REFUNDED também exige
   paymentReference/refundReference. Os três são finais, sem operação externa pendente.
7. Respostas de reconciliação conhecidas carregam envelope persistido completo em
   `resultMessage`, validado pelo mesmo schema/event guards. UNKNOWN/PENDING não
   autorizam avanço. Billing deve responder PENDING enquanto resultado externo incerto.
8. Bearer de **serviço** em OpenAPI interno é opção proposta, não emissão de JWT pela
   OS nem reaproveitamento de JWT de cliente. Marcelo define identidade/audiência,
   rotação, mTLS/rede e transporte. Servidores `.invalid` não são endpoints existentes.
9. Limites estruturais candidatos: quantidade/revisão até 2.147.483.647, dinheiro até
   20 caracteres, texto livre até 256; rever limites antes de congelar. Currency
   verifica sintaxe; política de moedas ISO suportadas e preços zero precisa de Julio.
10. Timeout financeiro, retry/backoff, visibility/retention, DLQ e janela operacional
    de reconciliação não têm valores inventados neste pacote; definir por ambiente.
11. Item sem materiais: HTTP 200 com IN_PROGRESS após transação local e guardas.
    Item com materiais: 202 com START_REQUESTED; retry da mesma operação concluída
    retorna 200. Matheus confirma a semântica e compatibilidade com clientes atuais.
12. Proposta de lookup cadastral: POST interno autenticado para evitar CPF na URL;
    auth recebe somente customerId/status. Alternativa: projeção própria do auth.
    A decisão e adaptação do auth pertencem a Marcelo, não estão implementadas.
13. HTTP preserva `PATCH /api/admin/itens-ordem-servico/{id}/iniciar` e GET
    `/api/public/ordens-servico/{id}/acompanhamento`, verificados nos controllers
    de origem. Status HTTP existentes em português são preservados; acrescentar
    `PRONTA_PARA_EXECUCAO`/`INICIO_SOLICITADO`, mapeados a READY_FOR_EXECUTION/
    START_REQUESTED. Novo `executionState` explicita o código técnico. Corpo de
    tentativa/revisão e Idempotency-Key obrigatórios são incompatíveis com o cliente
    atual: **decidir versionamento HTTP ou migração coordenada antes de implementar**.
    Schemas de resposta mostram os campos afetados; a compatibilidade completa dos
    DTOs atuais será revisada na extração, sem remover campos silenciosamente.

## Dependência de autenticação antes de separar bancos

Auth main `c0464abc38cef6b6a667cb89de2f695d80a17c25` consulta `cliente` diretamente
em `src/shared/db.py`. Separação sem transição quebra login. Não conceder acesso
permanente ao banco OS; manter identidade/RBAC sob auth. Preparar lookup/projeção,
autorização de serviço e teste real de login/acesso do cliente antes de cortar tráfego.
Auth develop diverge em documentação; conciliar com o contrato implementado da main.

## Pedido de plataforma

Configuração futura da OS: três URLs de filas e respectivas DLQs, região, endpoint
local quando aplicável, credenciais via cadeia SDK, datasource PostgreSQL exclusivo
na fase 2 e histórico DynamoDB na fase 6. Confirmar nomes/ARNs, IAM de menor privilégio,
ECR/namespace, NLB e Datadog. Nenhum recurso foi criado por esta entrega.
[Governança](../../../integration/governance-request.md): incluir repositório nos
três conjuntos/mapas existentes, após execução dos checks. Não recriar rulesets na OS.

## Registro de aprovação (preencher pelos revisores)

| Revisor | Commit/schema revisado | Resultado | Data/evidência |
| --- | --- | --- | --- |
| Matheus | pendente | pendente | pendente |
| Julio | pendente | pendente | pendente |
| Anderson | pendente | pendente | pendente |
| Marcelo | pendente | pendente | pendente |

Após aceite: registrar commit/tag ou release candidata revisada e a decisão de cada
dono. Se contratos mudarem na revisão, atualizar vetores e relatório; não reaproveitar
resultados de testes de outra revisão como prova automática.
