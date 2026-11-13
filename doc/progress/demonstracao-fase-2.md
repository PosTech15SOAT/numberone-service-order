# Demonstração e entrega da fase 2

Pré-requisitos: Docker Desktop Linux e PowerShell 7 (`pwsh`). Executar na raiz
do repositório destino, branch `feature/fase-2-dominio-persistencia`.
Não conectar esses comandos a bancos compartilhados. As credenciais do Compose
são demonstrativas e a porta HTTP fica somente em `127.0.0.1`.

## Roteiro PowerShell

```powershell
$env:MATERIAL_REFERENCE_PROVIDER = 'fixture'
docker compose up --build -d
pwsh -NoProfile -File scripts/demo-phase2.ps1
```

O script aguarda a prontidão do banco/aplicação, cadastra cliente/veículo/serviço
sintéticos (ou reaproveita os da demonstração anterior), abre OS, inicia
diagnóstico, acrescenta um serviço estimado em 150.00 e um kit previsto em 200.00.
Consulta o tracking e exige:

```text
Readiness=UP; Estado=EM_DIAGNOSTICO; Estimativa=350.00 BRL
OrcamentoAprovado=False; Previsto=1; Reservado=0; Consumido=0
Inicio=409; ConclusaoPrematura=409; EntregaPrematura=409; Inventory=FIXTURE
```

Não gera pagamento, reserva nem consumo real. O script imprime somente resultados
técnicos e usa endereço/documento sintéticos. Não utilizar cadastros pessoais reais
na demonstração. O perfil local fornece identidade ADMIN fixa, sem JWT; os testes
HTTP do Maven exercitam separadamente o contexto Gateway e a posse do cliente.

Para demonstrar persistência, reinicie o container da aplicação e repita:

```powershell
docker compose restart os
pwsh -NoProfile -File scripts/demo-phase2.ps1
docker compose exec -T postgres psql -U os_local -d service_order_db -Atc "SELECT count(*) FROM ordem_servico; SELECT version,success FROM flyway_schema_history;"
docker compose down
```

As ordens permanecem no volume exclusivo `numberone-os-local_os-postgres`.
`down` não apaga dados. Durante a execução desta entrega foi usado o projeto
isolado `-p numberone-os-phase2`, com o volume `numberone-os-phase2_os-postgres`.
Para reproduzir nesse projeto, acrescentar `-p numberone-os-phase2` aos comandos
Compose. A aplicação da demonstração foi parada após a validação; o volume foi
preservado. Não há reset automático nem exclusão de volumes de outros projetos.

## Testes e inspeção para Matheus

Executar o `clean verify` e o validador de contratos conforme README. Conferir
`target/site/jacoco/index.html` e os XMLs Surefire/Failsafe. O relatório da fase
separa regra, persistência, contrato e transporte, com resultados reais.

Sequência sugerida de leitura:

1. `doc/progress/mapa-extracao-fase-2.md`: campos removidos/substituídos e DTOs incompatíveis.
2. `src/main/resources/db/migration/V1__service_order_baseline.sql`: banco próprio, FKs locais, contadores e snapshots imutáveis.
3. `serviceorder/domain/entities`: guardas de pagamento, início, consumo, conclusão/entrega e estimativa.
4. `ServiceOrderService`/`JpaServiceOrderGateway`: escopo de filial, locks e versões.
5. `SecurityConfiguration`/`AuthenticatedUserAuthenticationFilter`: permissões, posse e confiança de Gateway.
6. `ServiceOrderPersistenceIT`: lock em duas transações PostgreSQL, versão obsoleta, impossibilidade de mover composição aprovada.
7. `ServiceOrderTransportIT`/`CatalogTransportIT`: fluxo HTTP/validações e autorização.

O caso positivo de concluir/entregar usa **seeds de confirmação em banco
descartável**: demonstra apenas a guarda local, não entrega distribuída. Não há
endpoint de seed em produção. A validação de contratos não altera esse limite.

## Entrega aos membros

| Responsável | Entrega disponível | Revisão/decisão pendente |
|---|---|---|
| Matheus | API Java/Spring, schema, regressões, mapa, demonstração e relatório | Aceite obrigatório da fase 2; DTOs, estados, cancelamento e filial única |
| Julio | Projeção local de aprovação separada da estimativa; contratos em `doc/contracts/saga/v1` | Composição/valores/moeda/contato, critérios financeiros e compensação Billing |
| Anderson | Porta de referência e alocações previstas/reservadas/consumidas com IDs externos | Lookup HTTP real e validação dos contratos de reserva/consumo/compensação |
| Marcelo | Configuração segura necessária, probes com banco, manifestos somente para revisão | Banco exclusivo/Secret/ConfigMap, Gateway/rede, auth lookup, governança |

Nenhuma mensagem foi enviada aos membros. A tabela é material de repasse para o
usuário. Não implementar endpoints ou tabelas dos demais serviços como atalho.

## Continuidade em outra conversa

Fornecer `doc/progress/fase-2.md`, `mapa-extracao-fase-2.md`, esta demonstração,
`doc/contracts/saga/v1/README.md` e o commit/PR da entrega. Obter a revisão exata:

```powershell
git rev-parse feature/fase-2-dominio-persistencia
git status --short
```

Consultar o registro de publicação no relatório da fase. A fase 3 só pode iniciar
depois da revisão de Matheus e de nova solicitação; não fazer merge/deploy agora.
