# Contratos da Saga · 2.0.0-os-baseline.1

Base definida por delegação de Matheus, pronta para a implementação da OS com
fixtures locais. `readyForOsImplementation=true` identifica esse uso.
`approvedContract=false` registra que a integração conjunta ainda será revisada.
O pacote substitui `2.0.0-review.1` no manifesto ativo. A revisão anterior está
preservada no commit `afdd830`. O diretório v1 continua como regressão histórica.

| Artefato | Conteúdo |
| --- | --- |
| [Catálogo](catalog.json), [dispatch](message.schema.json) e [eventos](messages.md) | Cinco eventos habilitados, composição obrigatória e valor aprovado positivo. |
| [Guia da equipe](team-guidance.md) e [decisões](decisions.md) | Escolhas DEC-01 a DEC-20 e responsabilidades. |
| [Fila](queues.md) e [configuração](queue-config.json) | SQS Standard Billing → OS e DLQ. |
| [OS interna](os-internal.openapi.yaml) | CT-02A/03 e lookup do cliente para Auth. |
| [Billing](billing.openapi.yaml) | CT-07/09/10/11. |
| [Inventory](inventory.openapi.yaml) | Disponibilidade, CT-05/08/08B e evidência de reversão. |
| [API OS](os-api.openapi.yaml) | Início por item, prontidão global, cancelamento e retirada. |
| [Composição](approval-composition.md) | Snapshot imutável e recuperação de aprovação fora de ordem. |
| [Estados](state-transitions.md) e [REST](rest-operations.md) | Limites transacionais, resultado desconhecido e compensação. |
| [Preparação da fase 3](phase3-handoff.md) | Checkout, dependências, validação e escopo de implementação. |

Execute na raiz do repositório `python scripts/validate_contracts.py`, com as
dependências de `scripts/requirements-contracts.lock`. O CI existente continua
usando o mesmo ponto de entrada e execução offline. A suíte valida formatos,
schemas e fixtures, incluindo exemplos negativos. Integração real e persistência
da Saga entram nas fases seguintes.
