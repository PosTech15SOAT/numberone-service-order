# Saga v1 — pacote de contratos em rascunho

**DRAFT. Não aprovado, não congelado, sem serviços integrados.** Revisores:
Matheus (OS), Julio (Billing), Anderson (Inventory), Marcelo (plataforma/auth).
Origem: [proposta preservada](../../../specification/contrato-saga-v1-proposta.md).
O nome `v1` é a versão candidata; `schemaVersion: 1` não representa aprovação.
O commit da entrega será a referência de revisão, não uma release aprovada.

## Arquivos e validação

- [Matriz das 21 mensagens](messages.md) e `catalog.json`: produtor, consumidor, schema e fixture.
- `envelope.schema.json`: campos comuns; **isoladamente não valida payload**.
- `message.schema.json`: entrada completa, seleciona exatamente um schema tipado.
- `common.schema.json`, `commands/`, `events/`: JSON Schema **Draft 2020-12**.
- `examples/`: exemplos completos, fictícios, sem CPF, JWT ou cartão.
- `invalid-examples/expectations.json`: caso negativo, schema esperado, keyword e caminho.
- [Transições e guardas](state-transitions.md), [filas](queues.md),
  [consultas internas](reconciliation.openapi.yaml), [HTTP da OS](os-api.openapi.yaml),
  [pedidos e decisões abertas](integration-requests.md).

Validador Python independente de linguagem de implementação: `jsonschema 4.25.1`
com `FormatChecker` explícito e dependências de formatos, `referencing 0.36.2` com
registro local e recuperação remota proibida. OpenAPI **3.0.3** validado com
`openapi-spec-validator 0.7.2`. Dependências diretas fixadas em
`scripts/requirements-contracts.txt`; versões transitivas resolvidas e usadas na
imagem em `scripts/requirements-contracts.lock`. Instalação exige rede; execução abaixo não.

Na raiz do checkout, PowerShell ou Bash:

```powershell
docker build -f scripts/contracts.Dockerfile -t numberone-os-contract-validation .
docker run --rm --network none --mount "type=bind,source=$((Get-Location).Path),target=/workspace" numberone-os-contract-validation
```

Bash: substituir o argumento de mount por `--mount "type=bind,source=$PWD,target=/workspace"`.
Alternativa com Python 3.13 e ambiente virtual: instalar o arquivo de requirements
`scripts/requirements-contracts.lock` e executar `python scripts/validate_contracts.py`.
Resultado em `target/contract-validation.json`; saída mostra somente nomes,
contagens e caminhos de erros, nunca valores rejeitados.

Billing/Inventory devem obter **o mesmo commit/pacote** e executar esses vetores
em seus pipelines, inclusive os negativos. Podem usar validadores nativos de
qualquer linguagem com suporte ao draft e format assertions. Não há biblioteca
Java compartilhada, geração de domínio, sétimo repositório ou chamada entre serviços.
Testar fixtures prova conformidade estrutural e coerência dos exemplos, não o
comportamento da Saga, persistência, idempotência ou entrega SQS.

## Identidades e repetição

| Campo | Responsabilidade |
| --- | --- |
| messageId | UUID da mensagem; reentrega técnica mantém; nova resposta ganha outro |
| sagaId | UUID da Saga; também MessageGroupId nas três filas |
| serviceOrderId / branchId | UUID da OS e filial, sempre obrigatórios |
| attemptId | Tentativa compartilhada entre os três serviços; fechada não reabre |
| operationId | Operação lógica persistida pelo solicitante; respostas/replays conservam |
| budgetRevision | Revisão comercial positiva; não é schemaVersion nem attemptId |
| schemaVersion | Inteiro 1 para o envelope candidato atual |
| causationId | messageId causador; null só na origem HTTP/local; eventos exigem UUID |
| occurredAt | RFC 3339 UTC com Z; não decide precedência de negócio |
| producer | Identidade lógica do produtor; validar também origem IAM/fila, não confiar só no JSON |
| correlationId / traceparent | Correlação técnica e contexto W3C opcional, sem token ou identidade pessoal |

IDs budgetId/paymentAttemptId pertencem a Billing, reservationId/movementId a
Inventory; OS não os inventa. Chave semântica inclui tentativa/revisão/tipo e item
quando aplicável. Reenvio da mesma operação não cria novo operationId/cobrança/consumo.
Respostas financeiras subsequentes podem conservar a mesma operação. Uma nova
ação de negócio usa nova operação. Mensagens com mesmo operationId e conteúdo
incompatível são conflito; não assumir sucesso só pela deduplicação de transporte.

## Política concreta de compatibilidade

Objetos aceitam propriedades adicionais, inclusive payload e estruturas aninhadas;
teste adiciona campos em todos esses níveis nas 21 mensagens. Consumidores ignoram
campos opcionais desconhecidos. Isso permite **campos opcionais aditivos**.

Não significa aceitar tipo de mensagem, schemaVersion ou valor de enum desconhecido.
Tipos/versões não suportados devem ser isolados e observados, sem efeito de negócio.
Novas mensagens exigem coordenar o roteamento/consumidores; extensões de enum que
alterem decisões não são automaticamente compatíveis. Trocar tipo, semântica,
obrigatoriedade, restrição ou remover campo exige nova versão e migração acordada.
Não estreitar schema existente depois de aprovado. Os `$id` sob domínio `.invalid`
são identificadores lógicos, não endpoints: o registro resolve arquivos locais.

## Dados comerciais e cenários

Quantidades são inteiros positivos; item/material possuem UUIDs explícitos.
Dinheiro é string decimal não negativa com duas casas, convertido para Decimal/
BigDecimal; nunca float. Currency tem sintaxe de três letras; aceitar código
ISO 4217 e moeda suportada é guarda adicional do consumidor, não comprovada pela regex.

Proposta a validar: `serviceItems[].quantity` multiplica mão de obra;
`materials[].quantity` é o **total de unidades desse material no item**, sem
multiplicar novamente pela quantidade do serviço. Materiais repetidos por UUID no
mesmo item e itens repetidos são erro semântico; schema não garante unicidade por
propriedade. Billing calcula o total e define arredondamento/descontos. OS verifica
consistência e guarda snapshot, nunca substitui o total aprovado silenciosamente.

Exemplo principal: mão de obra R$ 150,00 × 1 + um kit R$ 200,00 × 1 = R$ 350,00.
`CreateBudget`, `BudgetApproved`, `ReserveMaterials`, `RequestPayment`,
`PaymentConfirmed`, `ConsumeMaterials` e `MaterialsConsumed` conservam referências.
O teste aritmético só verifica essa fixture, não implementa o cálculo de Billing.

`*.no-materials.json`: orçamento só de mão de obra, R$ 150,00; aprovação causa
RequestPayment diretamente, sem ReserveMaterials vazio. Após pagamento confirmado,
item sem materiais inicia em transação local. `*.early.json` demonstra liberação/
cancelamento antes de conhecer reservationId/budgetId. `*.not-reserved.json` e
`*.not-charged.json` demonstram fechamento sem efeito anterior encontrado.

Fixtures com falha, sucesso e variantes são **cenários alternativos independentes**,
não uma sequência a publicar em conjunto. Reutilizam IDs didáticos; nunca alimentar
uma fila real com toda a pasta. Contato fictício fica só em CreateBudget;
Inventory não recebe informações pessoais do cliente. Não logar payloads.

## Fontes do tooling

[Validação e format checker](https://python-jsonschema.readthedocs.io/en/stable/validate/),
[registro de referências](https://python-jsonschema.readthedocs.io/en/stable/referencing/),
[validação OpenAPI](https://github.com/python-openapi/openapi-spec-validator).
As bibliotecas validam estrutura; os guardas de negócio permanecem responsabilidade
das implementações futuras e dos testes de regra/persistência/transporte separados.
