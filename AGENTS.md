# Instruções do projeto

## Logs estruturados

- Para atributos estruturados, use ponto para agrupar uma entidade ou contexto com identidade própria e underline entre palavras compostas: `service_order.id`, `service_order_item.id`, `budget.revision_number` e `event.code`. `event.code` agrupa o código no contexto do evento. Não crie namespaces artificiais. Como identifica uma operação entre contextos, o atributo técnico independente é `correlation_id` no MDC e no JSON; o header permanece `X-Correlation-Id`. Preserve os nomes oficiais ECS e Datadog, como `dd.trace_id` e `dd.span_id`, e use o mesmo nome para o mesmo conceito nos serviços.
- Emitir JSON ECS em stdout; usar SLF4J Fluent API, `event.code`, mensagens objetivas em português e identificadores de negócio. Não registrar credenciais, tokens ou payloads sensíveis. Restaurar o MDC ao final da operação.
