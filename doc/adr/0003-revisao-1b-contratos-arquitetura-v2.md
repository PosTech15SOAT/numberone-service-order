# ADR 0003 — Pacote ativo de revisão da fase 1B

Data: 06/11/2026. Status: **proposta para revisão**, sem aceite presumido de outros serviços.

## Contexto

A árvore foi conferida limpa em `feature/fase-1b-contratos`, HEAD
`c73dfb2b74d271b6808c98170a1bb8a9b382aac4`. O pacote anterior foi introduzido
por `eac1b18`. Ele usa comandos SQS, reserva e pagamento antes da execução.
A [fonte atual](../specification/revision-1b/contratos-saga-arquitetura-v2.md)
descreve consumo REST no início e pagamento após conclusão técnica.

## Decisão deste pacote

O [manifesto](../contracts/saga/packages.json) seleciona `doc/contracts/saga/v2`,
versão **2.0.0-review.1**, como pacote ativo **para revisão**. Não representa
aprovação conjunta nem contrato publicado em produção. `v1` fica integralmente
preservado e validado como regressão. ADR 0002 e relatórios anteriores permanecem
como registros históricos; sua proposta de Saga é substituída por este ADR no pacote ativo.

`v2` identifica a família de contratos alinhada ao desenho arquitetural v2;
`2.0.0-review.1` identifica esta entrega; `schemaVersion: 1` identifica a primeira
estrutura dos novos tipos de evento. Não converter `BudgetApproved` em
`OrcamentoAprovado` por simples alias: envelope e semântica mudaram. Nenhum consumidor
real foi migrado nesta fase. Alterações incompatíveis posteriores exigem versão
de schema/contrato explícita e migração acordada; campos opcionais aditivos são tolerados.

- Billing publica quatro tipos em uma fila Billing → OS; OS consome. Inventory
  não participa de SQS nesta Saga. EV-04 fica fora do dispatch habilitado até acordo
  sobre estorno integral na v1 do Billing.
- A aprovação vigente habilita execução. Consumo confirmado habilita uso físico.
  Pagamento não é guarda de início/conclusão. Quitação oficial CT-07 guarda entrega.
- Tentativas REST têm identidade durável. `CLOSED` e `REVERSED` impedem baixa tardia.
- O vínculo aprovação/composição é proposta pendente Julio/Matheus. A validação
  estrutural do EV-02 não prova aprovação do snapshot técnico.
- O mesmo `scripts/validate_contracts.py`, imagem e job `Required validation`
  validam legado e revisão. Não substituir o CI nem afirmar ruleset ativo.

## Consequências

Catálogo, schemas, fixtures, OpenAPI, transições e decisões são artefatos revisáveis.
Não há domínio, persistência, consumidor, infraestrutura provisionada ou integração
executada. A [matriz](../contracts/saga/v2/decisions.md) registra o que cada responsável
deve aceitar com versão/commit. Código de domínio começa na fase 2 após essa revisão.

Fontes novas foram copiadas para `doc/specification/revision-1b`, com SHA-256;
as especificações anteriores não foram reescritas.
