# Preparação da fase 3

Base de contratos 2.0.0-os-baseline.1. O checkout para evoluir a aplicação é
`D:/Dev/PosTech15SOAT/tmp/fase-2/numberone-service-order`.

Foram incorporados os arquivos da revisão de contratos no filesystem da fase 2.
O índice Git e os arquivos de aplicação já existentes foram preservados.
O histórico da fase 1 segue no outro worktree. Nenhum merge de Git é necessário
para usar o pacote local, mas o commit/PR de entrega deve incluir a seleção ativa,
schemas, scripts e documentação incorporados.

Na fase 3, implementar migrations service_order_saga/inbox_event, abertura com Saga,
consumidor SQS e handler OrcamentoEnviado. Usar os schemas ativos, inbox única por
eventId, hash e commit antes de ACK. Validar rollback, reentrega e colisão de ID.
Fixtures de examples são o produtor de teste. Os cenários de CT-10/11 e consumo
REST entram no caminho principal/compensação das fases seguintes.

O runtime SQS, filas AWS e participantes reais serão integrados nas próximas fases.
As capacidades atuais de início e entrega continuam protegidas no código da fase 2.
A regra de domínio para valor zero e a trigger que bloqueia cancelamento de itens
aprovados devem ser ajustadas nas fases 4/5 ao ligar essas transições. O schema já
recusa aprovação de valor zero antes do handler.

Executar contratos com `python scripts/validate_contracts.py`, usando dependências
do lock e sem acesso de rede durante a validação. Para a aplicação, seguir o
relatório de adaptação da fase 2 e preservar banco/migrations já existentes.
