# Compatibilidade da API OS

Referências: contratos legados preservados em v1, rotas LEG-07A/LEG-10A do desenho
arquitetural e DTOs do checkout local `PosTech15SOAT/src/main/java/.../serviceorder/api`.
Essa leitura local não substitui comparação com o commit de extração da Fase 3:
Matheus deve validar o diff completo de DTOs na fase 2. Este OpenAPI descreve o
recorte afetado, não toda a API cadastral.

| Rota preservada | Mudança proposta e compatibilidade |
| --- | --- |
| `PATCH /api/admin/itens-ordem-servico/{id}/iniciar` | Continua sem body. 200 confirma início; novo 202 confirma intenção durável, não execução física. status legado permanece PENDENTE/AGUARDANDO_PECAS_E_INSUMOS enquanto executionState=START_PENDING. Campos startOperationId/executionState são aditivos. |
| `PATCH /api/admin/ordens-servico/{id}/iniciar` | Continua sem body. Intenção não baixa estoque; 202 mantém APROVADA enquanto itens precisam iniciar. Primeiro início confirmado muda EM_EXECUCAO. Semântica da rota global precisa de aceite Matheus. |
| `GET /api/public/ordens-servico/{id}/acompanhamento` | Preserva status, itensServico, orcamento, veiculo e datas; acrescenta sagaState, budgetVersion e estado/tentativa por item. Exige identidade e posse; não é anônimo. |
| `PATCH /api/admin/ordens-servico/{id}/entregar` | Continua sem body e responde DTO da ordem; 409 para não concluída/não quitada; 503/504 para consulta inconclusiva. Só ENTREGUE após CT-07 positivo e guardas locais. |

Não introduzir `INICIO_SOLICITADO` ou `PRONTA_PARA_EXECUCAO` como status HTTP comercial.
O catálogo em português permanece o do legado. `START_PENDING` e `READY_FOR_EXECUTION`
pertencem a campos técnicos. Preservar valores monetários numéricos dos DTOs legados,
datas locais e nomes JSON; strings monetárias/UTC são regras dos contratos internos/eventos.
Objetos cadastrais aninhados permitem campos existentes; sua modelagem completa
continua sob o DTO legado, não é substituída por novos objetos vazios.

Erros públicos preservam `status`, `message` e `errors` do ErrorResponse legado;
`code` e `correlationId` são aditivos, com códigos documentados para novas recusas
de negócio. Erros técnicos de segurança/correlação da base 1A continuam podendo
usar `application/problem+json` com `type`, `title` e `status`. O formato interno
dos outros serviços não substitui esses DTOs públicos.

`X-Correlation-Id` público mantém string 1–128, como contrato anterior; os eventos
exigem UUID. Se a entrada pública não for UUID, gerar correlação interna UUID e
reter vínculo no registro/log sanitizado. Não rejeitar cliente antigo só para copiar
literalmente seu header ao SQS. Identidade vem dos headers confiáveis do Gateway,
com sobrescrita e bloqueio de acesso direto, e verificações de permissão/posse/filial.

`Idempotency-Key` no início do item é opcional para manter a forma do request antigo.
Com chave, a OS guarda fingerprint e resultado por operação. Sem chave, serializa
por item/composição e reutiliza a tentativa ativa. Após recusa durável, distinguir
uma nova intenção de um retry sem chave é ambíguo: cliente deve consultar andamento
e realizar ação explícita. A migração coordenada para exigir chave em versão futura
é pendência de Matheus; não anunciar garantia irrestrita para clientes antigos.

O status 202 e a confirmação assíncrona são mudança comportamental: clientes que
tratam qualquer 2xx como autorização de uso físico precisam ser ajustados. A
compatibilidade de rota/body/DTO não elimina essa necessidade. Não exigir novo body
de início como fazia o rascunho v1. Sem confirmação, a interface mostra início pendente.

Falta de saldo depois do 202 aparece em acompanhamento como
AGUARDANDO_PECAS_E_INSUMOS; retry técnico não inicia nova tentativa automaticamente.
Pagamento não é guarda de início nem conclusão. OS CANCELADA não recebe ENTREGUE
automaticamente: política de retirada continua pendente, preservando a divergência
com o legado para decisão conjunta, sem inventar cobrança ou transição.
