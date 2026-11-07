# Matriz de decisões — Julio, Anderson, Marcelo e Matheus

Pacote **2.0.0-review.1**, 06/11/2026. Fonte: seção 7 do
[contrato arquitetural](../../../specification/revision-1b/contratos-saga-arquitetura-v2.md).
Não houve consulta/aceite externo nesta execução. **Todos os itens abaixo permanecem
pendentes de aceite dos responsáveis**, mesmo com proposta codificada no OpenAPI.
O aceite da fase é a disponibilidade deste pacote para revisão, não o fechamento
artificial dessas decisões. Base anterior `c73dfb2`; direção técnica registrada
na fonte em 04/11/2026 não equivale a aceite conjunto.

| ID / referência | Responsáveis | Proposta concreta para decidir | Evidência solicitada / impacto | Aceite (autor, data, versão/commit) |
| --- | --- | --- | --- | --- |
| DEC-01 · D09/P13 SENT | Julio + Matheus | SENT quando revisão fica disponível para decidir; outbox no mesmo commit; e-mail separado | Confirmar se aceite do provedor de e-mail é pré-condição. EV-01 e WAITING_APPROVAL dependem disso | PENDENTE |
| DEC-02 · revisão vigente | Julio + Matheus | budgetVersion crescente por OS, identidade única da revisão, estável em envio/decisão | Definir fonte/recuperação se decisão chegar antes de EV-01; não assumir endpoint não acordado | PENDENTE |
| DEC-03 · composição D02/D03 | Julio + Matheus | compositionVersion imutável no CT-03 e ecoada nos eventos de orçamento; snapshot local referenciado | Aceitar [proposta e exemplo obsoleto](approval-composition.md); atualizar perfis de ambos os serviços antes de liberar execução | PENDENTE |
| DEC-04 · EV-02 moeda/valor | Julio + Matheus | approvedAmount decimal string, currency BRL; aprovação integral | Confirmar moeda, valor zero e relação com snapshot comercial; schema não decide elegibilidade | PENDENTE |
| DEC-05 · EV-04 / D12 | Julio + Matheus | Habilitar PagamentoAtualizado somente se estorno integral confirmado entrar na v1 Billing | Registrar escopo/versionamento; parcial não coberto. Dispatch atual permanece desabilitado | PENDENTE |
| DEC-06 · CT-07 prontidão | Julio + Matheus | Eventos agendam CT-07; READY_FOR_DELIVERY é indicação; entrega sempre reconsulta | Aceitar conjunto de pagamentos, schema settled/paymentId, invalidar respostas locais obsoletas e tratar timeout | PENDENTE |
| DEC-07 · CT-05 agrupado | Anderson + Matheus | Consumo todos-ou-nenhum, IDs repetidos agregados, 409 INSUFFICIENT_STOCK durável, nova tentativa após reposição | Aceitar normalização do pedido, fingerprint, HTTP e retenção idempotência | PENDENTE |
| DEC-08 · CT-08/08B CLOSED | Anderson + Matheus | 200 resultados duráveis; 202 PENDING; 404 UNKNOWN; CLOSED antes da baixa, REVERSED após baixa; bloquear consumo tardio | Aceitar códigos ATTEMPT_CLOSED/REVERSED, serialização inclusive sem linha e replay após reversão | PENDENTE |
| DEC-09 · evidência D05/D11 | Anderson + Matheus | evidence com autor/instante/referência e unusedConfirmed; uso só após início confirmado | Fechar conferência física, autorização e devolução; boolean sozinho não comprova fato | PENDENTE |
| DEC-10 · janela D05/D10/D11 | Julio + Anderson + Matheus | C1–C6 automático apenas integral antes do primeiro início confirmado e sem uso | Confirmar política conjunta; execução iniciada/parcial exige revisão operacional | PENDENTE |
| DEC-11 · CT-09 | Julio + Matheus | CANCELLED apenas revisão vigente elegível e efeitos financeiros resolvidos; POST idempotente recupera timeout | Confirmar se orçamento APPROVED pode cancelar e códigos de conflito; não confundir cancelamento com estorno | PENDENTE |
| DEC-12 · retirada D10 | Julio + Anderson + Matheus | Não converter OS cancelada em entregue/cobrança automática | Definir todos os itens cancelados, retirada, histórico e transição legada CANCELLED → DELIVERED | PENDENTE |
| DEC-13 · estorno/entrega | Julio + Matheus | Excedente pode ter fluxo próprio Billing; cobrança que quita OS requer coordenação manual; após entrega ocorrência auditada | Aceitar janela de corrida CT-07/commit ou desenhar protocolo adicional. Não prometer atomicidade distribuída | PENDENTE |
| DEC-14 · fila/DLQ | Marcelo + Julio + Matheus | Uma fila Billing → OS; nomes e parâmetros candidatos em queues.md | Fechar Standard/FIFO, IAM/KMS, visibilidade, retry, redrive, retenção, alarmes e horizonte inbox/outbox | PENDENTE |
| DEC-15 · identidade de serviço | Marcelo + donos | Credencial interna por serviço/audiência, Gateway sobrescreve contexto e rede restringe acesso | Fechar emissão/rotação, escopos, autorização de filial/posse e identidade do funcionário CT-05 | PENDENTE |
| DEC-16 · database OS | Marcelo + Matheus | Database lógico numberone_os no RDS compartilhado, credencial isolada e migrations próprias | Confirmar nomes/grants sem acesso cruzado; não provisionar nesta fase | PENDENTE |
| DEC-17 · histórico DynamoDB | Marcelo + Matheus | vehicle_maintenance_history, PK vehicleId, SK completedAt#serviceOrderId#historyVersion; OS projeta por tarefa local | Confirmar tabela/acesso e retenção; indisponibilidade não bloqueia conclusão/pagamento/entrega | PENDENTE |
| DEC-18 · lookup cliente auth | Marcelo + Matheus | Auth consulta contrato interno autenticado ou projeção própria, sem SQL direto no database OS | Escolher mecanismo, payload, autorização e migração do login. Proposta de lookup v1 é histórica, não endpoint acordado ativo | PENDENTE |
| DEC-19 · API OS | Matheus | Sem body novo; status legados; 202 e campos técnicos aditivos; chave opcional inicialmente | Validar DTOs Fase 3, rota global /iniciar, migração de clientes e ambiguidade de retry sem chave | PENDENTE |
| DEC-20 · transições/testes | Matheus + donos nas integrações | Matriz de estados e cenários deste pacote | Registrar aceite do pacote; fases seguintes provam domínio, concorrência, transporte, persistência e recuperação reais | PENDENTE |

Para aceitar, substituir PENDENTE por nome, data, versão do pacote e commit/PR da
decisão. Para rejeitar/alterar, registrar alternativa e impacto em schema, fixture,
OpenAPI e testes. Não marcar aceite só porque o validador passou.

Entrega para **Julio**: catálogo, CT-02A/03/07/09, DEC-01–06/10–13 e vínculo de composição.
Entrega para **Anderson**: Inventory OpenAPI, fixtures CLOSED/REVERSED/recusa, DEC-07–10/12.
Entrega para **Marcelo**: queues.md e DEC-14–18; recursos e identidade efetivos não verificados.
Entrega para **Matheus**: API OS, estados, composição, cenários e decisões conjuntas.
Estas são solicitações no pacote; nenhuma mensagem foi enviada aos participantes.
