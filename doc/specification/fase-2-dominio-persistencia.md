# Fase 2 - Extrair domínio e persistência da OS

**Modelo:** GPT-6.1 Sol, High. **Entrada:** fase 1 revisada e contratos publicados; código de origem da organização acessível. Leia [contexto](contexto-organizacao.md) e [contrato](contrato-saga-v1-proposta.md), usando a versão aprovada na fase 1 quando já existir.

## Objetivo

Ter uma OS independente, com dados locais corretos, sem embutir Billing ou Inventory. Essa fase não entrega ainda o atendimento distribuído completo.

## Implementações

1. Extrair `customer`, `vehicle`, `automotiveservice` e o domínio de OS/diagnóstico/itens de serviço. Portar testes existentes e preservar validações úteis, IDs e rotas sempre que compatíveis.
2. Criar schema/Flyway próprio em `service_order_db`, separado do legado. Manter relações JPA entre cliente, veículo, ordem e itens locais; remover relações para orçamento e entidades Inventory. Referências de materiais viram IDs e snapshots mínimos.
3. Separar material previsto, reservado e consumido. `quantityUsed` atual não pode afirmar consumo real antes da confirmação do Inventory. Registrar alocação por item, revisão e referências de movimentos quando confirmados posteriormente.
4. Retirar `ServiceOrderBudgetService`/entidades/repositories comerciais da OS; manter visão/snapshot aprovado onde as consultas precisarem. Remover cálculo comercial autoritativo de `ServiceOrder.calculateValue` e tracking. Estimativa pode existir, identificada como estimativa; aprovado vem de Billing.
5. Substituir gateways que acessam Inventory localmente por portas de integração. Consulta de referência/disponibilidade usa contrato HTTP/fixture de teste; não implementar banco/CRUD Inventory na OS. Não adicionar foreign key para material externo.
6. Reaproveitar autenticação/contexto/permissions da fase 3. Adaptar checagem de acesso do cliente e testes. Não reintroduzir login local JWT administrativo. Material de lookup para o auth segue a decisão com Marcelo, sem acesso direto ao banco da OS.
7. Portar abertura, início/edição de diagnóstico, itens/materiais previstos, acompanhamento, conclusão e entrega. Ajustar guardas: concluir item só após início confirmado; finalizar ordem só com todos os itens elegíveis concluídos e sem consumo pendente; entregar só ordem finalizada.
8. Preparar `READY_FOR_EXECUTION` e `START_REQUESTED` nos modelos/DTOs conforme contrato. Nesta fase, não permitir que o endpoint legado inicie reparo ignorando pagamento/reserva. A integração de início será habilitada com a fase 4; não retornar sucesso fictício.
9. Guardar `branchId` mesmo com uma filial. Se ainda não há cadastro de filiais, usar configuração/fixture explícita, sem criar uma frente de gestão de filiais. Escopo e autorização não podem depender de um UUID arbitrário enviado pelo cliente sem validação.
10. Acrescentar versão/locks e constraints que protejam edições/transições locais. Diagnóstico/snapshot aprovados não são alterados depois da aprovação nesta versão do fluxo.

Preparar planilha/tabela de migração: estrutura de origem → destino → campo removido/substituído. Para banco novo, gerar migrations próprias; não editar migrations já aplicadas nem copiar V1-V6 com RBAC/estoque/orçamento. Não executar migração/corte de produção nesta fase.

## Testes e demonstração

- CRUD cliente/veículo/serviço e abertura/diagnóstico com validações e autorização.
- Persistência PostgreSQL real via Testcontainers para constraints, relacionamentos e versões; H2 não prova locking PostgreSQL.
- Item não inicia sem confirmação financeira/material; item não iniciado não conclui; ordem com pendência não entrega.
- Modelo não importa entidades/repositories Billing ou Inventory; verificar também mappings/Flyway.
- Ajustes em tracking não apresentam estimativa como pagamento ou orçamento aprovado.
- Começar gate JaCoCo de pelo menos 80% de linhas da OS, com relatório agregado de unitários/integração e exclusões justificadas; sem aumentar exclusões para passar.

## Aceite

- [ ] API da OS compila/inicia com banco exclusivo e migrations reproduzíveis.
- [ ] Diagnóstico e itens pertencem à OS; nenhum saldo/movimento/fornecedor ou integração Mercado Pago foi copiado.
- [ ] Não há relacionamento JPA ou SQL cruzando limites dos serviços.
- [ ] Regressões relevantes portadas e fluxo local demonstrável; capacidades distribuídas ainda pendentes estão identificadas.
- [ ] Matheus revisou APIs, schema, guardas e mapa de migração.

## Prompt específico

```text
Implemente somente a fase 2: extração do domínio/persistência da OS a partir
de numberone-app-auto-service-api. Preserve a segurança fase 3 e as regras
locais, mantenha PostgreSQL exclusivo e remova relações JPA externas.
Orçamento/cálculo comercial ficam no Billing; saldo/reservas ficam no Inventory.
Não implementar Saga/transporte completos agora nem permitir início sem as
confirmações exigidas. Entregue migrations, APIs, testes, mapa de extração e
roteiro de revisão. Pare para revisão antes da fase 3.
```
