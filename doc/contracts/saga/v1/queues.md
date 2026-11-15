# Transporte proposto — nenhuma fila provisionada

| Fila lógica FIFO | Produtor | Consumidor | DLQ FIFO candidata |
| --- | --- | --- | --- |
| numberone-inventory-commands.fifo | OS | Inventory | numberone-inventory-commands-dlq.fifo |
| numberone-billing-commands.fifo | OS | Billing | numberone-billing-commands-dlq.fifo |
| numberone-order-results.fifo | Billing e Inventory | OS | numberone-order-results-dlq.fifo |

Nomes/URLs/ARNs por ambiente, região, retention, visibility timeout e maxReceiveCount
dependem de Marcelo e dos donos. Não existem recursos ou credenciais no pacote.
Nomes são configuração externa, nunca campos do payload. Local/Production são os
ambientes conhecidos; develop é branch, não ambiente cloud adicional.

- `MessageGroupId = sagaId`; `MessageDeduplicationId = messageId`, explícitos.
- JSON completo do envelope no body; traceparent opcional, sem JWT/CPF/cartão.
- FIFO ordena por grupo **dentro de cada fila**, não globalmente entre três filas.
- Publisher futuro conserva ordem da outbox por Saga/fila usando sequência local
  durável e um publicador ativo por grupo, inclusive retries; não publicar registro
  posterior enquanto anterior permanece incerto. Marcar envio só após confirmação.
- SQS deduplica transporte por janela limitada; inbox e identidade da operação
  persistidas nos serviços continuam obrigatórias fora dessa janela.
- Consumidor valida schema/tipo/produtor e guardas, registra inbox + mudança local +
  outbox numa transação PostgreSQL própria. **Delete/ack apenas após commit**.
- Falha técnica: rollback/retentativa com mesma identidade, visibility/backoff
  coordenados com timeout de processamento. Não converter indisponibilidade em
  evento de recusa de negócio. Contrato inválido vai a tratamento observável/DLQ,
  sem efeito parcial, sem logar payload pessoal.
- Replay de DLQ preserva IDs e verifica tentativa/versão; duplicata não cobra/consome.
  Fluxo de replay operacional, alarmes e permissões serão implementados nas fases futuras.
- IAM mínimo: OS envia só para command queues e consome resultados; Billing/Inventory
  consomem somente seus comandos e enviam resultados. Origem real é validada pela
  política de acesso, não pelo campo `producer` isolado. Decisão IAM viável no Academy
  fica com Marcelo; SDK usa cadeia padrão, sem chave em código/imagem.

Preservar Datadog APM, logs estruturados e correlação. Não substituir por uma nova
infra CloudWatch como atalho. Há **zero teste SQS real** nesta fase; validar JSON
com `--network none` não é teste de transporte.
