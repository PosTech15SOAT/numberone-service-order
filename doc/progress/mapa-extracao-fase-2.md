# Mapa de extração — fase 2

Origem autoritativa: `PosTech15SOAT/numberone-app-auto-service-api`, develop
`42b393cb66c9d0a62cf14f80dcfc37745ce09e7e`; main
`d7e489064bff04ce9620eaf48f9d070d8a13f86d` tem conteúdo idêntico.
Consulta/fetch em 01/11/2026. Base destino `c73dfb2`, descendente da fase 1B.
Não houve cópia de dados ou corte de produção. IDs continuam UUID; novos cadastros
usam geração UUID corrigindo o `IDENTITY` incompatível com UUID da origem.

## Estruturas

| Origem | Destino | Removido / substituído / preservado |
|---|---|---|
| `customer` | `customer`, tabela `cliente` | DTOs, CRUD, CPF/CNPJ e validações portados; versão otimista e unicidade de documento normalizado adicionadas |
| `vehicle` | `vehicle`, tabela `veiculo` | CRUD e vínculo local com cliente; unicidade de placa sem diferença de caixa, versão e validação de ano |
| `automotiveservice` | `automotiveservice`, `servico_automotivo` | Catálogo, ativação/inativação, validações; versão adicionada; bean duplicado corrigido |
| `ServiceOrder`/diagnóstico | agregado `ServiceOrder`, `ordem_servico` | Referências locais cliente/veículo; `branchId`, `attemptId`, `budgetRevision`, `version`; FK composta impede troca do dono do veículo referenciado |
| `ServiceOrderItem` | `ServiceOrderItem`, `item_ordem_servico` | FK catálogo local + snapshots de descrição/estimativa/tempo; quantidade/opcional; `START_REQUESTED` |
| Relação direta com material Inventory | `MaterialAllocation`, `material_previsto` | `materialId` externo sem FK, snapshot mínimo, quantidade prevista/reservada/consumida separadas |
| `quantityUsed` / `quantidadeUsada` | `plannedQuantity` / `quantidadePrevista` | Nunca representa consumo antecipado; API não aceita contadores reservados/consumidos nem confirmação |
| Movimentos reais de Inventory | IDs em `material_movement_reference` | Apenas referências confirmadas por alocação; nenhuma tabela de saldo/movimentação/fornecedor |
| Revisão das alocações | item → ordem → `budgetRevision` | Escopo da revisão atual pelo agregado; congelado após aprovação. Histórico/replanejamento não implementados |
| `ServiceOrderBudgetService`, orçamento/repositories | projeção `approved_*` na OS | Nenhuma entidade/repository comercial. Snapshot completo de composição aprovado e valor recebido ficam somente leitura |
| `calculateValue` comercial / orçamento no tracking | `estimate()` e `estimativa.tipo=ESTIMATIVA` | Não decide preço aprovado, pagamento ou impostos. `orcamentoAprovado` é nulo até confirmação externa |
| Gateway Inventory local | porta `MaterialReferenceGateway` | Sem CRUD Inventory; adapter indisponível retorna 503. Fixture explícita local/test contém um kit sintético |
| Identity Gateway / AuthenticatedUser / ownership | `shared/security` | Headers e permissões preservados; logs de headers removidos; confiança explícita desabilitada por padrão |
| Autenticação local de desenvolvimento | provider local restrito a local/test, excluído em production | Identidade fixa, sem login, senha administrativa, JWT, RBAC persistente ou Lambda |
| Flyway legado V1–V6 | novo `V1__service_order_baseline.sql` | Apenas sete tabelas próprias + histórico Flyway; nenhuma migration legada editada/copied |

JPA permite relações somente dentro da OS. Cliente, veículo, serviço e ordem têm
versão; mutações de filhos bloqueiam a raiz com `PESSIMISTIC_WRITE` e incrementam
sua versão. Constraints verificam contadores, datas, vínculo cliente/veículo e
completude da projeção aprovada. Triggers bloqueiam composição/diagnóstico
aprovados, inclusive tentativa de mover filho para outro pai. Banco novo; a V1
ainda não foi aplicada em nenhum ambiente compartilhado.

## Rotas e compatibilidade

| Rota | Comportamento fase 2 |
|---|---|
| `/api/admin/clientes`, `/veiculos`, `/servicos` | CRUD/ativação da origem, somente ADMIN. Exclusão de cadastro referenciado falha com 409 |
| `POST/GET /api/admin/ordens-servico[/{id}]` | Abertura/lista/consulta; filial vem do servidor; cliente precisa estar ativo e ser dono do veículo |
| `PATCH /ordens-servico/{id}/iniciar-diagnostico` | Inicia/edita diagnóstico antes de aprovação; alias `/diagnostico` |
| `POST /api/admin/itens-ordem-servico`, `DELETE /{id}` | Inclui/remove serviço em diagnóstico; `valor` é estimativa unitária, não preço aprovado |
| `/api/admin/itens-ordem-servico/{id}/insumos` | CRUD de previsão; `idMaterial`, `quantidadePrevista`; associação sempre validada no pai/filial |
| `GET /ordens-servico/{id}/calcular-servicos` | Resposta mudou para `{tipo: ESTIMATIVA,currency,valorEstimado}`; decimal serializado como string |
| `GET /ordens-servico/{id}/calcular-tempo-estimado` | Rota/campos preservados; tempo do snapshot por quantidade, exclui cancelados |
| `GET /ordens-servico/{id}/tempo-medio-execucao-servicos` | Contagem/tempo real local preservados; acrescenta contagem de início solicitado |
| `PATCH .../{id}/iniciar` (ordem/item) | Sempre 409 nesta fase: confirmação ausente ou integração indisponível. Sem sucesso fictício |
| `PATCH .../{id}/concluir` | Item requer início/consumo confirmados; ordem exige pagamento da revisão, todos os elegíveis concluídos e nenhuma compensação pendente |
| `PATCH /ordens-servico/{id}/entregar` | Somente FINALIZADA; não habilita reparo sem integração |
| `PATCH .../{id}/cancelar` | Cancelamento local só antes da aprovação e sem reserva/consumo; reconciliação distribuída ainda ausente |
| `GET /api/public/ordens-servico/{id}/acompanhamento` | Autenticado: ADMIN ou permissão `SERVICE_ORDER_TRACK_OWN` e posse do cliente; também verifica filial |

Respostas de OS/itens/material foram redesenhadas explicitamente: estado em
português + `executionState` canônico, estimativa separada do snapshot aprovado,
quantidades físicas separadas e IDs de referências. Tracking não replica dados
pessoais do cadastro nem inventa orçamento. Clientes HTTP precisam adaptar esses
DTOs; não é uma promessa de compatibilidade integral com o monólito. O OpenAPI
Saga publicado na fase 1 descreve o destino incremental; 202/idempotência ainda
não estão implementados. Nenhum contrato compartilhado foi alterado.

## Regressões e decisões

Portados testes de `CustomerService`, `VehicleService`, `AutomotiveServiceService`,
domínio/mapeamento de serviço, `AuthenticatedUser`, providers e checagem de posse.
Testes antigos de OS acoplados a estoque/orçamento foram substituídos por casos
de domínio e PostgreSQL/HTTP que verificam os novos limites. O teste que esperava
log de identidade foi substituído por um que exige ausência desses logs.

Decisões desta fase para revisão de Matheus:

- Uma filial configurada por instância; cadastros locais comuns nessa instância.
  Não foi criado sistema de cadastro/autorização multi-filial.
- Diagnóstico editável somente em RECEBIDA/EM_DIAGNOSTICO; itens/materiais em
  EM_DIAGNOSTICO. Após aprovação, nenhuma edição desta revisão.
- Quantidade prevista é total por item; mão de obra é unitária × quantidade.
  Billing continuará autoritativo para preço/composição final.
- Datas HTTP locais sem offset foram preservadas; relógio interno em UTC.
  Definição pública de fuso/offset precisa ser alinhada antes do transporte Saga.
- Sem adapter HTTP produtivo de referência até Anderson definir endpoint e
  credenciais. Fixture não consulta disponibilidade física e não reserva estoque.
- Auth lookup de cliente e proteção Gateway/rede continuam com Marcelo; não foi
  aberto acesso ao banco da OS nem criada nova Lambda.
- Revisões/reaprovação, cancelamento após aprovação, idempotência, outbox/inbox e
  confirmações distribuídas ficam para as fases contratadas, sem atalhos nesta.
