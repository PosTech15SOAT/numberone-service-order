# Adaptação da fase 2 após a revisão 1B

Base `feature/fase-2-dominio-persistencia`, commits `fce733a` e `13817e5`.
O pacote `2.0.0-review.1` da revisão 1B está no commit local `afdd830`, no checkout
da fase 1. A adaptação usa o perfil local proposto para DEC-03. O acordo externo
Julio/Matheus e a publicação do perfil nos eventos seguem pendentes.

## Mapa das alterações

| Área | Regra aplicada |
| --- | --- |
| Domínio | Iniciar exige aprovação vigente, vinculada à composição. Confirmar início de um item com insumos exige consumo integral e auditável. Concluir exige execução confirmada, itens aplicáveis concluídos e consumo ou compensação resolvidos. O reparo pode atingir `COMPLETED` antes da cobrança. |
| Composição | Alterações locais de diagnóstico, itens e materiais geram versões imutáveis por OS. O vínculo exige a identidade do orçamento, sua revisão e a composição técnica vigente. Uma revisão inferior é obsoleta. Vínculo divergente é conflito. O snapshot técnico registra serviços, insumos, quantidades e valor local de serviço. O valor e a moeda aprovados vêm do Billing. |
| PostgreSQL | V2 sucede V1, mantém o checksum da V1 e guarda operações e movimentos como referências externas. Consumo entre zero e a quantidade prevista é aceito. Uma baixa positiva nova exige operação e pelo menos um movimento não nulo e não vazio. A validação é adiada até o fim da transação, permitindo persistir as duas partes juntas. |
| Auditoria V1 | Quantidade e ID de reserva, referências de movimento, timestamp financeiro e snapshot comercial permanecem disponíveis. Um registro de baixa incompleta permitido pela V1 permanece para reconciliação e o domínio o trata como pendente. As novas gravações de evidência passam pela V2. A composição técnica inicial dos registros migrados tem versão 1 e as aprovações antigas permanecem sem vínculo de composição. |
| API e tracking | Os DTOs mantêm nomes, datas e valores decimais em string do checkout da fase 2. `budgetVersion` é alias de `budgetRevision`. `compositionVersion` identifica o snapshot e `consumoConfirmado` identifica uma baixa aplicável. Os campos de reserva são históricos e depreciados. `pagamentoConfirmado` representa o registro legado. O recorte está no [OpenAPI da fase 2](../contracts/os-phase2.openapi.yaml). |
| Infraestrutura | O Compose continua em `service_order_db`. A conexão externa usa `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`. O nome proposto para o RDS é `numberone_os`, pendente de alinhamento com Marcelo. |

## Compatibilidade de status

| Dados anteriores | Estado após V2 | Exposição HTTP |
| --- | --- | --- |
| OS `READY_FOR_EXECUTION` | OS comercial `APPROVED` | `status=APROVADA`. Uma aprovação legível mantém `executionState=READY_FOR_EXECUTION` como indicador técnico. A autorização de um novo início exige o vínculo vigente. |
| Item `START_REQUESTED` | Item comercial `PENDING`, `start_pending=true` | `status=PENDENTE`, `executionState=START_PENDING` e horário de início nulo na resposta. A migração preserva os timestamps gravados e não atribui ID de operação. |
| Aprovação V1 | Snapshot comercial original, vínculo técnico nulo | `orcamentoAprovado.approvedItems` mantém o JSON antigo. Uma execução válida já confirmada pode ser concluída se a baixa estiver comprovada. |

## Evidências de 07/11/2026

| Verificação | Resultado |
| --- | --- |
| Maven `-B -o verify` | 103 testes unitários e 31 de integração. Zero falhas, erros ou pulos. [Log](../../target/phase2-verify.log) |
| JaCoCo | 1.273 de 1.363 linhas cobertas (93,40%), acima do mínimo de 80%. [Relatório](../../target/site/jacoco/index.html) |
| PostgreSQL 17.9 | Banco vazio com V1 e V2. Banco separado, preparado com V1 e dados legados, atualizado para V2 com checksum V1 preservado. [Banco novo e HTTP](../../target/failsafe-reports/br.com.fiap.numberone.serviceorder.persistence.ServiceOrderPersistenceIT.txt) · [Upgrade](../../target/failsafe-reports/br.com.fiap.numberone.serviceorder.persistence.ServiceOrderV1UpgradeIT.txt) |
| Contratos | OpenAPI do recorte validado. As oito verificações de schemas e fixtures do pacote legado passaram offline. |
| Git | Os 11 arquivos preparados mantiveram a assinatura inicial do diff do índice `bded155ce36c25aadb1d9b3cd099b05aee0918f9`. |

Nesta execução o Docker estava indisponível. A suíte rodou no JDK 26.0.1,
compilando com `--release 25`, e no PostgreSQL 17.9 portátil em loopback.
Os serviços externos foram representados por dados sintéticos dos testes.

## Demonstração local

Execute na raiz do checkout com Java 25 e Docker disponível.

```powershell
.\mvnw.cmd -B verify
.\mvnw.cmd -B '-Dit.test=ServiceOrderPersistenceIT,ServiceOrderV1UpgradeIT,ServiceOrderTransportIT' verify
```

Os testes usam PostgreSQL real. A alternativa ao Testcontainers é definir
`PHASE2_TEST_POSTGRES_PORT` para a porta de um PostgreSQL descartável em
`127.0.0.1`, com banco `postgres` e usuário de teste `phase2_test`, sem senha.
O suporte cria schemas aleatórios para a aplicação e para a atualização da V1.
O roteiro anterior em `scripts/demo-phase2.ps1` corresponde à extração anterior à adaptação.

1. `approvedRepairWithDirectConsumptionCompletesOverHttpWithoutPayment` carrega
   aprovação e confirmação de início por fixtures de teste. As duas rotas de início
   retornam 409. O item sem consumo recusa o início no domínio. Após persistir
   operação e movimento com reserva zero, as rotas reais de conclusão do item e
   da OS retornam 200. A OS chega a `FINALIZADA` com `pagamentoConfirmado=false`.
   A rota de entrega continua em 409 mesmo após inserir pagamento legado de teste.
2. `directConsumptionRequiresValidQuantityOperationAndMovement` prova no
   PostgreSQL que a baixa sem reserva é aceita e que quantidade fora do limite,
   operação ausente ou nula, movimento ausente ou nulo e exclusão da última
   referência são recusados.
3. `upgradingV1PreservesLegacyAuditAndAllowsDirectConsumption` instala somente V1,
   grava dados legados, aplica V2, valida o checksum e confere status, reservas,
   movimentos e aprovação preservados. A baixa direta passa no banco atualizado.
4. `approvedSnapshotAndCompositionAreImmutableInApiAndDatabase` exercita revisão
   obsoleta, conflito de vínculo, bloqueios de edição, snapshot técnico persistido
   e valores distintos de estimativa e orçamento. `ServiceOrderTransportIT`
   também verifica tracking legado, posse, permissão e filial.

A execução integrada depende da mensageria e do estado durável das fases 3 e 4,
do CT-05 para consumo e do CT-07 para entrega. `sagaState` e `startOperationId`
dependem dessas integrações. O OpenAPI desta fase descreve os bloqueios HTTP atuais.
