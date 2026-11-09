# Decisões da base de implementação da OS

Matheus delegou as escolhas em 07/11/2026. Este pacote materializa DEC-01 a DEC-20
para a implementação local. O aceite individual dos outros responsáveis será
registrado quando cada integração for revisada. O [guia completo](team-guidance.md)
explica o efeito de cada definição e o trabalho de cada membro.


| Decisão | Definição de implementação | Responsável pelo código |
| --- | --- | --- |
| DEC-01 · SENT | Orçamento disponível para decisão é `SENT`. E-mail tem acompanhamento e retry próprios. Evento e estado no mesmo commit. | Julio |
| DEC-02 · revisão vigente | Uma revisão comercial vigente por OS, com `budgetVersion` crescente e identidade imutável. Consulta de recuperação descrita na seção 4. | Julio + Matheus |
| DEC-03 · composição | Adotar `compositionVersion` nos três eventos de orçamento e no CT-03. Snapshot técnico imutável por OS/versão. | Matheus + Julio |
| DEC-04 · valor | `BRL`, valor aprovado positivo, string decimal com duas casas nos contratos internos/eventos. Aprovação integral. | Julio + Matheus |
| DEC-05 · atualização financeira | Incluir `PagamentoAtualizado` para estorno integral confirmado de um pagamento. Autorização de estorno é manual. | Julio + Matheus |
| DEC-06 · quitação | CT-07 considera todos os pagamentos válidos. Consultar também no ato da entrega. Prontidão prévia é projeção. | Julio + Matheus |
| DEC-07 · consumo | Agrupado por item, todos-ou-nenhum, idempotente e sem reserva antecipada. Quantidades repetidas do mesmo material são somadas. | Anderson + Matheus |
| DEC-08 · fechamento | `CLOSED` impede baixa futura. `REVERSED` confirma reversão de baixa existente. Retornos da seção 6. | Anderson + Matheus |
| DEC-09 · evidência | Conferência autorizada com autor, instante e referência. Peça retirada só retorna ao saldo após devolução válida. | Matheus + Anderson |
| DEC-10 · janela | Compensação automática somente integral e antes do primeiro início confirmado, com não uso conferido. | Matheus |
| DEC-11 · orçamento cancelável | `DRAFT`, `SENT`, `REJECTED` e `APPROVED` podem ser cancelados na janela, com revisão vigente e sem efeito financeiro. Fechar contexto no Billing. | Julio + Matheus |
| DEC-12 · retirada cancelada | Manter OS `CANCELLED` e Saga `COMPENSATED`. Registrar retirada em campo próprio, após resolver efeitos. | Matheus |
| DEC-13 · estorno/entrega | Estorno exige autorização e reconciliação no Billing. Coordenar manualmente o estorno da cobrança que quita a OS com a entrega. | Julio + Matheus |
| DEC-14 · SQS | Uma fila Standard Billing → OS, com DLQ Standard, inbox e recuperação por estado. | Marcelo + Julio + Matheus |
| DEC-15 · serviço-serviço | Bearer opaco distinto por par chamador/destino, rotas autorizadas e rede privada. Contexto público continua no Gateway. | Marcelo + donos |
| DEC-16 · bancos | Um RDS existente. OS `numberone_os`, Billing `numberone_billing`, Inventory `inventory_db`, cada um com usuário exclusivo. | Marcelo + donos |
| DEC-17 · histórico | DynamoDB `vehicle_maintenance_history`, com prefixo de ambiente, projetado pela OS via outbox local. | Marcelo + Matheus |
| DEC-18 · login | Auth consulta cliente por POST interno na OS. Mantém seus usuários, RBAC e emissão atual de JWT. | Marcelo + Matheus |
| DEC-19 · API | Baixa no início por item. Rota global valida prontidão. Preservar os DTOs atuais da fase 2 e acrescentar campos técnicos. | Matheus |
| DEC-20 · aceite | Validar contratos e, depois, persistência/concorrência e integração real. Revisão entre fases continua com Matheus. | Todos |
