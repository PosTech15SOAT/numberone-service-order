# Proposta de vínculo entre aprovação e composição técnica

**Pendente Julio + Matheus (DEC-03).** O envelope base EV-02 contém IDs, revisão e
valor aprovado, mas não prova quais serviços/materiais foram aprovados. Não usar a
lista atual da OS como prova e não inferir igualdade de composição por igualdade de valor.

## Perfil candidato para aceite

1. A OS é autoridade sobre a composição técnica. Cada alteração elegível antes
   da aprovação gera `compositionVersion` inteira crescente por OS e snapshot
   imutável. A chave é `(serviceOrderId, compositionVersion)`, nunca apenas o número.
2. CT-03 devolve essa versão com serviços, IDs de itens e materiais e suas quantidades
   absolutas por item. Billing lê um snapshot consistente, guarda sua referência
   e o snapshot comercial (preços, quantidades, moeda e total), e associa o par a
   `(serviceOrderId, budgetId, budgetVersion)`. Nunca reconsulta itens correntes
   para reconstruir o que foi aprovado.
3. Proposta: EV-01, EV-02 e EV-02B ecoam `compositionVersion`. O
   [perfil EV-02](proposals/approval-composition.schema.json) demonstra a extensão
   obrigatória candidata; o [exemplo](proposals/approval-composition.json) usa versão 3.
   Esse perfil está separado do dispatch base; adicionar campo opcional ao envelope
   base não o torna obrigatório por acordo. Após aceite, publicar nova revisão do
   pacote, perfis para os três eventos, fixtures e contratos de ambos os serviços.
4. A OS persiste o vínculo revisão comercial → snapshot técnico antes de habilitar
   execução. Confere serviceOrderId, budgetId, budgetVersion, compositionVersion,
   existência do snapshot e revisão/contexto vigentes. O snapshot aprovado vem
   **do snapshot imutável da própria OS referenciado pelo Billing**, não do payload
   mutável atual. O valor/moeda aprovado vem do Billing.
5. Alteração concorrente ao envio/aprovação invalida o contexto anterior para iniciar.
   Identidade/revisão inferior: `IGNORED_STALE` com auditoria. Mesma revisão comercial
   com composição diferente: conflito, reconciliação, sem execução. Contexto ainda
   indisponível: pendência/retry, não aprovação tácita. Após aprovação aceita, congelar
   escopo; serviços adicionais exigem outra OS conforme direção D02/D03.

A query histórica `?compositionVersion=` de CT-03 é uma proposta para recuperar
exatamente o snapshot. Versão ausente devolve 404; nunca devolver a versão atual
como substituta. Prazo de retenção acompanha auditoria/replay, a acordar.

Um hash não é necessário se a OS garantir imutabilidade e unicidade dessa chave.
Se os donos exigirem prova de conteúdo entre serviços, precisam acordar algoritmo,
ordenação, normalização de quantidades/decimais e campos cobertos antes de adicionar
`compositionHash`; não foi introduzido um hash sem especificação de canonicalização.

## Aprovação obsoleta revisável

Ver [fixture completa](proposals/stale-approval.json):

| Momento | Contexto |
| --- | --- |
| t1 | Composição 3: mão de obra 150.00 + 1 material; Billing emite orçamento revisão 1, total 350.00 |
| t2 | Antes da aprovação aceita na OS, revisão elegível muda para composição 4 com 2 materiais e orçamento revisão 2 |
| t3 | Chega EV-02 atrasado: budgetVersion=1, compositionVersion=3, approvedAmount=350.00 |
| Resultado | Evento válido no schema, mas obsoleto para a composição 4; registrar IGNORED_STALE, não iniciar e não copiar materiais correntes para o snapshot aprovado |

Se chegasse `budgetVersion=2` com `compositionVersion=3`, seria conflito de vínculo,
não simples revisão inferior. Mesmo que ambos os snapshots tivessem total 350.00,
não seriam equivalentes. Aprovação que chega antes de EV-01 só pode ser aplicada
quando a fonte acordada confirmar o vínculo/revisão; caso contrário fica pendente.

O teste offline confere formato e coerência desta fixture; a rejeição de negócio,
imutabilidade e corrida entre edição/aprovação serão testadas com domínio/persistência
nas fases seguintes. Sem acordo sobre o vínculo, não implementar início usando
somente os IDs/valor do evento base.
