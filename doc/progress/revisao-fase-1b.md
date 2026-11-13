# Entrega — revisão da fase 1B

Data: 06/11/2026. **Pacote revisável 2.0.0-review.1 entregue; acordos externos pendentes.**

## Estado conferido antes das edições

- Diretório: `D:/Dev/PosTech15SOAT/tmp/fase-1/numberone-service-order`.
- Branch existente: `feature/fase-1b-contratos`.
- HEAD/base: `c73dfb2b74d271b6808c98170a1bb8a9b382aac4`; árvore inicialmente limpa.
- Pacote anterior em `doc/contracts/saga/v1`, criado por `eac1b18`; validador
  percorria apenas v1: 24 schemas, 21 tipos, 31 fixtures válidas, 21 inválidas e 2 OpenAPI.
- CI existente: `Required validation` executa `scripts/validate_contracts.py` em
  container `--network none`; promoção mantém `Validate promotion source`.
  Workflows versionados não comprovam ruleset ativo; nenhum estado remoto foi consultado.

## Artefatos entregues

[Índice do pacote](../contracts/saga/v2/README.md),
[catálogo](../contracts/saga/v2/catalog.json),
[ADR de seleção](../adr/0003-revisao-1b-contratos-arquitetura-v2.md) e
[matriz para os quatro responsáveis](../contracts/saga/v2/decisions.md).

O pacote inclui envelope e quatro eventos Billing → OS, EV-04 condicional fora
do dispatch, fila/DLQ/IAM/reprocessamento, IDs/revisões/idempotência/ordenação,
estados e compensação C1–C6. Quatro documentos OpenAPI cobrem CT-02A/03,
CT-05/08/08B, CT-07/09 e início/acompanhamento/entrega OS. Disponibilidade é leitura.
Recusa de saldo, timeout, CLOSED e REVERSED têm exemplos verificáveis.

O vínculo aprovação/composição tem proposta separada e exemplo obsoleto; não foi
declarado acordo. A seção 7 foi desdobrada em decisões com donos, incluindo SENT,
revisão vigente, estorno, cancelamento, retirada, quitação/entrega e plataforma.

O diretório v1, ADR 0002, fontes e relatórios antigos permanecem intactos. Novas
fontes estão em `doc/specification/revision-1b` com hashes. README aponta para o
pacote ativo. O ponto de entrada, Dockerfile e job de CI foram reaproveitados;
apenas o nome descritivo do passo CI mudou. Acrescentado pin de `typing-extensions`,
dependência transitiva que o lock anterior ainda deixava sem versão explícita.

## Validação executada

**19 verificações aprovadas, zero falhas/erros**, incluindo os 8 checks originais.
Resultado versionado: [JSON](evidence/contract-validation-1b.json) e
[saída do validador](evidence/contract-validation-1b.txt).

| Escopo ativo | Quantidade |
| --- | --- |
| JSON Schemas | 9 |
| Eventos habilitados / condicional | 4 / 1 |
| Fixtures válidas / inválidas de eventos | 5 / 17 |
| Fixture condicional / propostas documentais | 1 / 2 |
| Documentos OpenAPI novos | 4 |
| Fixtures HTTP válidas / inválidas | 36 / 11 |

Além disso, todos os schemas, fixtures e 2 OpenAPI do legado passaram em regressão.
Os testes negativos verificam palavra-chave e caminho do erro, não apenas qualquer
falha. Referências são resolvidas localmente; o audit hook bloqueia conexão, DNS e
sendto durante a execução, com teste explícito desse bloqueio. EV-04 é válido no
schema condicional e inválido no dispatch ativo, conforme o escopo solicitado.

Comando local executado (PowerShell, Python disponível no runtime desta máquina):

```powershell
$env:PYTHONPATH = (Join-Path (Get-Location) 'target/contract-deps')
& C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe scripts/validate_contracts.py
```

As dependências do lock foram instaladas previamente em `target/contract-deps`.
O download foi separado da execução offline. O Docker Desktop não disponibilizou
o engine nesta sessão; por isso a execução local usou Python com rede bloqueada
pelo validador. O comando Docker offline já existente permanece no CI e no README.
Não foi executado CI remoto, build Maven, deploy nem integração real. Nenhum código
Java, pom, domínio, banco, migração ou infraestrutura foi alterado nesta revisão.
`git diff --check` e comparação com a base confirmaram ausência de alterações no legado
e em `src`/`pom.xml`.

## Roteiro de revisão

1. Conferir `packages.json` e ADR 0003: v2 ativo para revisão, v1 histórico preservado.
2. Abrir catálogo, exemplos dos quatro eventos e condição de EV-04.
3. Revisar aprovação obsoleta e pendência DEC-03 antes de planejar início da OS.
4. Revisar OpenAPI/fixtures Inventory: saldo insuficiente, UNKNOWN/PENDING,
   CLOSED antes da baixa, REVERSED depois e conflito de chave.
5. Revisar CT-07/09, 202 da OS e transições; confirmar que pagamento ocorre após reparo.
6. Cada dono registra aceite ou alternativa na matriz com versão/commit.
7. Reexecutar `python scripts/validate_contracts.py` ou o container offline documentado.

## Aceite da entrega

- [x] Pacote versionado e identificável; histórico anterior preservado.
- [x] CI versionado aponta ao validador que percorre revisão ativa e legado.
- [x] Validação offline real de schemas, fixtures válidas/inválidas e OpenAPI.
- [x] Eventos, endpoints, timeout e cancelamento revisáveis com resultados explícitos.
- [x] Pendências atribuídas a Julio, Anderson, Marcelo e Matheus; nenhum aceite inventado.
- [ ] Aceite técnico/comercial dos donos: registrar na matriz após revisão.
- [ ] Execução integrada/concorrência: fases posteriores, fora do aceite deste pacote.

Esta entrega encerra somente a revisão 1B solicitada. Não inicia a fase 2.
