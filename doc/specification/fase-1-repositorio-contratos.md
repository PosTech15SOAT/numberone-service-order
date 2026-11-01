# Fase 1 - Repositório mínimo e contratos da Saga

**Modelo:** GPT-6 Astra, esforço High. **Objetivo:** permitir que Matheus revise a base e compartilhe contratos com Julio/Anderson antes de começar a extração. Leia primeiro [README](README.md), [contexto](contexto-organizacao.md) e [base de contrato](contrato-saga-v1-proposta.md).

## 1A. Criar a base do repositório

1. Conferir se `PosTech15SOAT/numberone-service-order` já existe. Se existir, ler e preservar seu conteúdo. Caso contrário, preparar o novo repositório com esse nome, visibilidade pública como os projetos atuais e sem copiar `.git`, secrets, target, dados reais ou infraestrutura compartilhada.
2. Fazer isso somente quando a fase 1 for solicitada para execução. Este pacote autoriza planejamento, não uma criação antecipada. Usar um diretório de checkout próprio, separado da aplicação legada, escolhido conforme o workspace disponível.
3. Registrar a revisão de origem de `numberone-app-auto-service-api` e comparar main/develop. Copiar seletivamente Maven Wrapper, base Java 25/Spring Boot 4.0.5, convenções e dependências necessárias. Não copiar os seis módulos para depois dizer que já existe um microsserviço isolado.
4. Criar aplicação mínima que compile, suba localmente e exponha health/readiness, com configuração externa e profile local. Preparar PostgreSQL exclusivo em Compose quando necessário; não apontar para o banco de produção nem executar migrations legadas nele.
5. Reaproveitar a estrutura por feature e camadas internas `api/application/domain/infrastructure`, construtores, DTOs separados e tratamento de erros. Preparar a adoção do contexto autenticado fase 3; não criar emissor JWT/login novo.
6. Adicionar README, CONTRIBUTING, Dockerfile, `.gitignore`, `.env.example` sem segredos, health/probes e manifests mínimos com nomes exclusivos da OS.
7. Criar CI com job **Required validation**: build/testes, validação dos contratos e build da imagem; validar manifestos quando presentes. Preservar workflow/check de promoção **Validate promotion source**. Não habilitar deploy automático para a produção nesta fase.
8. Usar branches/PRs para mudanças após o bootstrap mínimo necessário de um repositório vazio. Não contornar rulesets, não fazer merge automático e não reutilizar nomes de recursos da aplicação legada.
9. Preparar a inclusão do novo repositório em `postech15soat-governance`, com solicitação/diff para Marcelo. Os checks precisam executar antes de serem exigidos. Código/workflow preparado e ruleset realmente ativo são evidências diferentes.

**Revisão 1A:** aplicação mínima inicia, CI passou ou limitação foi comprovada, organização/nome/revisão de origem estão registrados e o serviço não substituiu o legado. Matheus pode revisar 1A enquanto 1B é elaborado, sem começar a fase 2.

## 1B. Gerar o pacote de contratos

Produzir no repositório OS:

```text
doc/contracts/saga/v1/
  README.md
  envelope.schema.json
  common.schema.json
  commands/*.schema.json
  events/*.schema.json
  examples/*.json
  invalid-examples/*.json
  state-transitions.md
  queues.md
  reconciliation.openapi.yaml
  integration-requests.md
doc/adr/
  0001-limites-da-os.md
  0002-saga-orquestrada-e-inicio-assincrono.md
```

Os nomes podem seguir o padrão documental já existente, preservando esses conteúdos. JSON Schema com draft explicitado e suporte da biblioteca escolhido; referências locais resolvidas sem depender de download externo no CI. OpenAPI para as consultas internas deve seguir versão suportada pelo tooling. Não gerar uma biblioteca de domínio compartilhada.

O pacote deve conter:

- Todos os comandos/eventos da matriz da base de contrato, com campos, tipos, obrigatoriedade, exemplos e produtores/consumidores.
- Diferença entre messageId, sagaId, attemptId, operationId, revisão e referências comerciais; regras de reentrega e repetição semântica.
- Três filas/DLQs, group/deduplication IDs, confirmação após commit e ordenação no publicador.
- Estados e guardas; separar pagamento confirmado, OS pronta, início solicitado e início confirmado por consumo.
- Compensação anterior ao consumo, resultado desconhecido, mensagens atrasadas e caso já consumido.
- Payload de orçamento/snapshot aprovado, alocações por item e cenário de R$ 350/um kit, incluindo cenário sem materiais.
- Consultas de reconciliação, segurança e estados desconhecidos. Não tratar 404/UNKNOWN como prova de que nada ocorreu.
- Contratos HTTP da OS a alterar, inclusive início HTTP 202 e consulta de andamento; não implementar todos os controllers agora.
- Solicitações objetivas para os outros membros, incluindo a dependência de lookup do cliente pelo auth antes da separação dos bancos.

Criar teste automatizado que valide fixtures válidas e rejeite inválidas: campo obrigatório ausente, UUID/data inválidos, quantidade zero/negativa, revisão inválida, valor fora do formato e tipo/schemaVersion incompatível. Configurar a validação de `format` de forma efetiva, se a biblioteca não a habilitar por padrão. Usar o mesmo pacote nos testes dos três serviços.

Mudança aditiva deve preservar compatibilidade; campos desconhecidos não devem quebrar desnecessariamente consumidores antigos. Mudança de tipo/semântica/campo obrigatório exige versão nova. Definir isso junto com a política concreta dos schemas, não prometer compatibilidade e usar schemas que a impeçam.

## Entrega aos membros

| Destinatário | Entregar |
| --- | --- |
| Julio | CreateBudget/RequestPayment/CancelBilling, eventos financeiros, snapshot aprovado e reconciliação |
| Anderson | ReserveMaterials/ConsumeMaterials/ReleaseMaterials, alocações, resultados e reconciliação |
| Marcelo | Filas/configurações/IAM necessários, nomes de recursos OS, contexto autenticado e mudança de lookup de cliente |
| Matheus | ADRs, transições, API planejada e roteiro de testes antes da extração |

Preparar o texto para compartilhar, com links de commit/PR. Enviar mensagens à equipe somente se o usuário solicitar; preparar o material não equivale a enviá-lo.

## Fora desta fase

Não extrair CRUDs, implementar Saga completa, Mercado Pago, estoque, provisionar AWS, migrar dados de produção ou alterar os cinco repositórios existentes. Ajustes em governança/infra/auth são pedidos para seus responsáveis, salvo autorização explícita de implementação em outro repositório.

## Aceite e demonstração

- [ ] Repositório/checkouts separados, origem e configurações registrados; nenhum legado sobrescrito.
- [ ] Build, execução local, health e imagem demonstrados com comandos reais.
- [ ] CI publica os nomes de checks compatíveis; ativação da governança identificada como aplicada ou pendente.
- [ ] Cada linha da matriz possui schema e fixture válida; fixtures inválidas falham no teste esperado.
- [ ] Contratos explicam início pendente, falta de estoque, duplicação, compensação e timeout desconhecido.
- [ ] Pacote revisável está pronto para Julio/Anderson e os pedidos de plataforma estão claros.
- [ ] Matheus revisou a fase; contratos aceitos pela equipe têm versão/commit registrado. Sem aceite dos envolvidos, marcar 1B como rascunho e não congelar contrato por conta própria.

## Prompt específico

```text
Execute somente a fase 1 desta pasta, em duas entregas: 1A, repositório/base
mínima da OS, e 1B, contratos da Saga. Baseie-se nos repositórios reais da
organização PosTech15SOAT e registre a revisão usada. A base do contrato é
uma proposta: gere schemas, fixtures/testes, transições, ADRs e pedidos de
integração. Preserve NLB, Datadog, autenticação do Gateway e governança atual.
Não implemente o negócio das fases seguintes nem habilite deploy de produção.
Entregue os links/arquivos para revisão e o material para Julio/Anderson/Marcelo.
Pare ao concluir a fase 1; não comece a extração antes da revisão.
```
