# Aprovação vinculada à composição

Adotar compositionVersion obrigatória em CT-03 e nos três eventos comerciais.
A OS mantém snapshot imutável por (serviceOrderId, compositionVersion). Billing
congela seu snapshot comercial com essa referência e calcula o valor uma vez.
serviceAmount é o total de mão de obra do item. supplies.quantity é a quantidade
absoluta do material naquele item.

A OS valida revisão, identidade e snapshot ao aceitar decisão. O snapshot aprovado
vem da versão técnica registrada, e valor/moeda vêm do Billing. Após aprovação,
o escopo fica congelado. Aprovação para composição anterior não libera execução.
O exemplo em proposals/stale-approval.json permanece estruturalmente válido e
demonstra esse resultado esperado. A execução da regra depende dos testes de domínio.

CT-10 recupera a revisão vigente caso a decisão chegue antes do envio. CT-03 com
query compositionVersion recupera a versão histórica exata. Versão inexistente
responde 404. Em todas as chamadas, revalidar o estado local ao aplicar a resposta.
