# language: pt
Funcionalidade: Orquestração da ordem de serviço

  Cenário: Entrega após execução e confirmação do pagamento
    Dado uma OS diagnosticada com uma peça disponível
    E um orçamento aprovado e recebido pela OS
    Quando o funcionário repete a solicitação de início com a mesma chave
    Então o Inventory baixa a peça uma única vez
    Quando a execução é concluída e chega o webhook de pagamento
    Então a OS recebe a confirmação pela fila e permite a entrega

  Cenário: Cancelamento antes da execução após falta de estoque
    Dado uma OS diagnosticada com uma peça sem estoque
    E um orçamento aprovado e recebido pela OS
    Quando o funcionário tenta iniciar a execução
    Então o início é recusado por falta de estoque
    Quando o funcionário confirma o cancelamento integral sem uso físico
    Então a Saga encerra o orçamento e compensa a OS sem movimentar estoque
