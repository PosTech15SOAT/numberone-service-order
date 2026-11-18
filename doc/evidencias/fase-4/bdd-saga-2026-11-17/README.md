# BDD integrado da Saga · 17/11/2026

Execução local das revisões de `develop` após a integração dos PRs de CI e documentação. **Passaram 2 cenários e 12 etapas em 48,254 s.** [Relatório JUnit](TESTS-saga.xml) · [Saída do Behave](execucao.txt)

| Cenário | Verificação |
| --- | --- |
| Entrega após execução e confirmação do pagamento | A mesma chave de início produziu uma única baixa. `OrcamentoAprovado` e `PagamentoConfirmado` apareceram uma vez como `PUBLISHED` no Billing e `APPLIED` na OS. Após confirmação de quitação, a OS terminou em `ENTREGUE` e a Saga em `COMPLETED`. |
| Cancelamento antes da execução após falta de estoque | O início retornou `409 INSUFFICIENT_STOCK`. O orçamento foi cancelado, as etapas de estoque e orçamento terminaram com sucesso e a Saga chegou a `COMPENSATED`, com saldo zero. |

## Revisões e binários

| Serviço | SHA em `develop` | SHA-256 do JAR usado |
| --- | --- | --- |
| OS | `7d134d3a45e9d79a972820a6a14e7994955031b5` | `3238c79c45864e2f8996eeaf19e22ac8d32e5302c149e458b62ee3d54cde3b55` |
| Billing | `df4a7642f4048b6aa09dddc4b815535b11129d6d` | `eeaa79691842cfb09a1f7450fd4543d5db81697431592300b8fd4189a1c7434b` |
| Inventory | `3b93db6ec92cae94f1400bee9b1389efb9b02fd8` | `9b9704f2c281307669c9b2af219ee0ab132e7019d71f6ba610fb90912aaa3ec7` |

Os serviços rodaram em contêineres locais, com três bancos lógicos PostgreSQL 17, LocalStack SQS 4.14.0 e Behave 1.3.3. O build descartável do Billing incluiu uma fixture para as chamadas ao Mercado Pago. A aplicação processou o webhook assinado e publicou o evento pelo fluxo real de outbox. O teste acessou as APIs diretamente e usou o SQS local. Gateway, Auth, ponte AWS e projeção DynamoDB ficam para a homologação na conta Academy. [Como reproduzir](../../../../scripts/bdd/README.md)
