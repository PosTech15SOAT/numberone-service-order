# API da OS e compatibilidade

Preservar os DTOs atuais da fase 2: status comerciais em português, datas locais,
valores monetários como strings e campos técnicos aditivos. O recorte do OpenAPI
descreve as rotas afetadas, com propriedades extensíveis. O contrato completo do
CRUD segue o código da aplicação. A integração da fase 4 evolui o comportamento.

Início por item pode retornar 202 com startOperationId antes da confirmação.
A interface só autoriza uso físico quando houver início confirmado. A rota global
/iniciar valida prontidão e retorna 200 com a ordem. Ela não cria operação pendente.

Clientes novos enviam Idempotency-Key. Sem header, recuperar a tentativa ativa ou
a última durável. Nova tentativa após recusa exige nova chave explícita. Cancelar
retorna 202 para compensação durável e fecha o contexto comercial mesmo se nenhum
orçamento tiver sido observado. Retirada de OS cancelada registra withdrawnAt e
withdrawnBy e preserva CANCELADA/COMPENSATED.

O OpenAPI 1B anterior descrevia valores numéricos em certos campos. Esta base segue
valorEstimadoUnitario string da fase 2. A correlação pública mantém string 1–128
caracteres; eventos e contratos internos usam UUID gerado internamente se necessário.
