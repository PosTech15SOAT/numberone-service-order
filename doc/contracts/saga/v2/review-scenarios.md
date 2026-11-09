# Cenários para implementação

Os schemas e as fixtures provam formato. Os handlers da fase 3 provam inbox,
persistência, ACK e retomada. As fases 4 e 5 provam integração/compensação.

| Situação | Resultado esperado |
| --- | --- |
| EV-01 vigente | Saga WAITING_APPROVAL com orçamento e composição associados |
| Evento idêntico repetido | Recuperar desfecho da inbox e confirmar sem reaplicar |
| Mesmo ID e corpo divergente | Conflito diagnosticado, sem alterar o efeito original |
| Queda antes do commit | Reentrega pode aplicar normalmente |
| Queda após commit antes do ACK | Reentrega encontra a inbox e preserva efeito único |
| EV-02 antes de EV-01 | Recuperar revisão por CT-10 e validar snapshot antes da decisão |
| Aprovação de composição obsoleta | Auditar e impedir início dessa composição |
| EV-04 v2 antes de EV-03 v1 | Projeção mantém REFUNDED v2 |
| Consumo sem resposta | Recuperar CT-08 com o mesmo operationId |
| Fechamento antes da baixa | CLOSED e saldo preservado |
| Reversão de baixa repetida | Movimento inverso único e REVERSED |
| Cancelamento sem orçamento conhecido | CT-11 retorna contexto fechado NO_BUDGET ou CANCELLED |
| Entrega com CT-07 inconclusivo | Entrega bloqueada e verificação pendente |

As fixtures têm IDs sintéticos. Ao criar uma OS em teste da fase 3, usar seu ID e a
compositionVersion realmente persistida no evento de entrada. Manter eventId e
conteúdo em reentregas. Uma nova ação de teste recebe identidade própria.
