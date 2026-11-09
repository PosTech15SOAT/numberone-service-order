# Fila Billing → OS

Adotar os valores de [queue-config.json](queue-config.json): fila Standard
numberone-billing-events e DLQ Standard numberone-billing-events-dlq. Billing envia.
OS recebe, altera visibilidade e exclui após commit. O papel de operação realiza
redrive. SSE-SQS, rede e permissões seguem a infraestrutura de Marcelo.

Configuração inicial: long polling 20 s, visibilidade 60 s extensível,
maxReceiveCount 5, retenção principal 4 dias e DLQ 14 dias. Tratar duplicatas e
eventos fora de ordem em aplicação. Em conflito persistente, registrar diagnóstico
e encaminhar à DLQ pela política. A retenção de identidades cobre o replay de 30 dias.

Contexto de aprovação desconhecido usa CT-10 e retry limitado. Redrive mantém corpo
e eventId e ocorre após correção da causa, com operador, motivo e taxa controlada.
As configurações são contrato de implantação. Provisionamento é da plataforma.
