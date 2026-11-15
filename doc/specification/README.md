# Implementação da OS em fases

**Responsável:** Matheus. **Organização:** [PosTech15SOAT](https://github.com/PosTech15SOAT). **Especificação:** 31/10/2026.

Este pacote orienta outro modelo a implementar **uma fase por vez**, com revisão do Matheus antes de avançar. Neste momento foram preparados documentos; o repositório da OS não foi criado e nenhum recurso GitHub/AWS foi alterado.

## O que entregar ao modelo

Forneça esta pasta inteira e acesso de leitura à organização. O modelo deve ler o [contexto verificado](contexto-organizacao.md), a [base de contrato da Saga](contrato-saga-v1-proposta.md) e o arquivo da fase solicitada. A proposta geral continua em [proposta da equipe](../proposta-equipe.md), com as adaptações de infraestrutura explicitadas no contexto desta pasta.

Os contratos deste pacote são **proposta para a fase 1**, ainda sem validação dos demais membros. Não declarar v1 aprovado apenas porque os arquivos ou testes foram gerados. Após a revisão técnica de Matheus, Julio, Anderson e Marcelo, os schemas versionados no repositório OS passam a ser a referência compartilhada.

## Fases e modelos recomendados

| Fase | Entrega revisável | Modelo recomendado | Esforço sugerido |
| --- | --- | --- | --- |
| [1](fase-1-repositorio-contratos.md) | Repositório mínimo executável e pacote de contratos para a equipe | GPT-6 Astra | High |
| [2](fase-2-dominio-persistencia.md) | Clientes, veículos, diagnóstico e execução isolados na OS | GPT-6.1 Sol | High |
| [3](fase-3-mensageria.md) | Inbox/outbox PostgreSQL, SQS e primeira publicação real | GPT-6.1 Sol | High |
| [4](fase-4-saga-fluxo-principal.md) | Saga do diagnóstico até início confirmado por consumo | GPT-6 Astra | High |
| [5](fase-5-compensacao-recuperacao.md) | Cancelamento, compensação, timeout e recuperação | GPT-6 Astra | High |
| [6](fase-6-historico-nosql.md) | Histórico de revisões DynamoDB e consulta pela OS | GPT-6.1 Sol | High |
| [7](fase-7-integracao-entrega.md) | Integração real, segurança, BDD, qualidade e deploy | GPT-6.1 Sol; Astra na revisão dos fluxos críticos | High |

**Minha recomendação prática:** Sol para extração e adaptadores; Astra para os contratos e os pontos de consistência distribuída. Se preferir um único modelo para tudo, use Astra. As escolhas por fase são uma avaliação da complexidade deste projeto, não um benchmark entre modelos.

A documentação oficial posiciona Astra para trabalhos mais exigentes e Sol para trabalho complexo com menor custo. Disponibilidade varia por conta/cliente; o esforço High é uma recomendação para estas fases, ajustável conforme o resultado. Fontes consultadas em 31/10/2026: [modelos no Work/Codex](https://learn.chatgpt.com/docs/models) e [catálogo oficial](https://developers.openai.com/api/docs/models). Não é necessário Max/Ultra para executar este plano.

## Planejamento das três semanas

| Período indicativo | Fases | Resultado |
| --- | --- | --- |
| Dias úteis 1-2 | 1 | Contratos disponíveis para Billing/Inventory; base da OS compilando |
| Dias úteis 3-5 | 2 | Domínio e banco exclusivos, sem relacionamentos externos |
| Dias úteis 6-7 | 3 | Mensageria durável e fixtures integradas |
| Dias úteis 8-9 | 4 | Caminho principal funcionando |
| Dias úteis 10-11 | 5 | Falha/compensação e retomada comprovadas |
| Dia útil 12 | 6 | Histórico NoSQL funcional |
| Dias úteis 13-15 | 7 | Integração, evidências e ajustes finais |

É uma distribuição de trabalho para Matheus, não uma promessa de duração do modelo. Reservar as revisões dentro dessas janelas. Testes acompanham cada fase; não começar testes ou pipeline apenas na fase 7. Marcelo pode preparar recursos AWS e Julio/Anderson podem desenvolver a partir dos contratos após a fase 1, sem esperar o código da OS.

## Prompt de abertura

Copie o texto abaixo e informe somente a fase desejada:

```text
Implemente somente a fase [N] da especificação Implementação da OS em fases.
Leia README.md, contexto-organizacao.md, contrato-saga-v1-proposta.md e o
arquivo fase-[N]-*.md desta pasta. Use a organização github.com/PosTech15SOAT
como fonte de código e padrões. O checkout PosTech15SOAT local antigo não é
a base autoritativa da aplicação da fase 3.

Antes de editar, leia as instruções do repositório de destino e confira as
revisões das fontes. Preserve alterações existentes. Registre decisões e
limite mudanças à fase solicitada. Use a base Java/Spring atual para a OS.
Não implemente Billing, Inventory, novas Lambdas ou infraestrutura compartilhada
como atalho. Os contratos entre serviços são independentes de linguagem.

Execute os testes pertinentes e entregue evidências reais. Não trate fixtures
como integração real. Separe teste de regra, persistência, contrato e transporte.
Não exponha segredos ou dados pessoais em saída, mensagens ou logs.

Ao terminar, apresente alterações, comandos/resultados, roteiro de demonstração,
checklist de aceite, decisões pendentes e a entrega para os outros membros.
Atualize um registro de progresso no repositório de destino. Pare para revisão;
não inicie a próxima fase, não faça merge nem deploy cloud sem solicitação.
```

Em nova conversa, também fornecer o último relatório de fase e o commit/PR do destino. O modelo não deve depender do histórico deste chat para entender o escopo.

## Regra de encerramento de cada fase

- Entregar PR pequeno ou alterações locais revisáveis, conforme a autorização da execução.
- Mostrar como executar e conferir o resultado, inclusive no PowerShell quando aplicável.
- Informar testes aprovados, falhas e verificações que não puderam ser executadas.
- Não marcar uma fase como concluída se um aceite obrigatório não foi demonstrado.
- Registrar dúvidas de negócio que afetam os contratos; não mudar o contrato compartilhado silenciosamente.
- Aguardar a revisão do Matheus para começar a próxima fase, conforme o pedido de implementação incremental.

O pacote contém sete fases. A fase 1 está dividida em **1A, repositório/base**, e **1B, contratos** para permitir duas revisões curtas dentro da mesma fase.
