# Preparação para iniciar a fase 3

Em 07/11/2026 foi materializado o pacote **2.0.0-os-baseline.1**.
A base pronta para implementação está em
D:/Dev/PosTech15SOAT/tmp/fase-2/numberone-service-order.

Foram atualizados schemas, exemplos, dispatch dos cinco eventos, CT-10/11,
lookup do Auth, API de início/cancelamento e retirada, decisões e instruções.
O arquivo CT-02A também inclui cancellationInProgress para o Billing verificar
o bloqueio de início ao cancelar. A fila escolhida é Standard.

O validador continua pelo mesmo comando do CI e cobre o pacote ativo e o legado.
Ver o resultado em target/contract-validation.json. Os schemas/OpenAPI e
fixtures foram executados offline nos dois checkouts.

O pacote da fase 1 foi incorporado por cópia controlada de artefatos ao filesystem
da fase 2. Os índices Git e todos os arquivos existentes de src/pom da fase 2
foram conferidos por hash. A origem Git das branches permanece registrada.
Ao preparar o próximo commit, incluir o novo pacote e o validador junto da base
de aplicação revisada, mantendo a documentação existente.

O consumidor SQS, a inbox e a persistência da Saga serão a implementação da fase 3.
Os contratos podem ser usados com fixtures e emuladores enquanto Billing,
Inventory e AWS forem integrados. O guia no pacote lista o escopo das fases 4/5,
incluindo valor aprovado positivo e o cancelamento preservando snapshot.

[Pacote ativo](../contracts/saga/v2/README.md) ·
[Próxima execução](../contracts/saga/v2/phase3-handoff.md) ·
[Registro de preservação](preparacao-fase-3.json)

**Validação final** · 21 verificações aprovadas, zero falhas/erros. Pacote ativo com 5 eventos, 9 schemas, 4 documentos OpenAPI, 6 exemplos válidos de eventos, 21 inválidos, 46 exemplos HTTP válidos e 15 inválidos. [Resultado](evidence/contract-validation-os-baseline.json).
