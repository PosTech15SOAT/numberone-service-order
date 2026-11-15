# ADR 0004 · Base local para iniciar a fase 3

Data 07/11/2026. Definida por delegação de Matheus.

O pacote ativo é 2.0.0-os-baseline.1, com cinco eventos, compositionVersion
obrigatória e CT-10/11 para recuperação/fechamento. O status representa prontidão
para desenvolver a OS. O aceite de integração dos outros serviços será registrado
nas respectivas entregas. O commit afdd830 preserva a revisão anterior.

Usar SQS Standard e idempotência em aplicação. Manter as alterações da fase 2 e
incorporar o pacote por arquivos controlados, preservando os índices Git. Revisão
de código e integração real continuam nos aceites das fases seguintes.
