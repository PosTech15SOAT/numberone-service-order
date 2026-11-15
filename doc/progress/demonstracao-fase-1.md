# Roteiro de demonstração — somente fase 1

Executar na raiz do checkout OS, com Docker Desktop/Linux. Comandos abaixo em
PowerShell; não exigem AWS, banco, credencial GitHub ou JDK 25 no host.

## 1. Identificar a entrega

```powershell
git status --short
git log --oneline -4
git diff --stat develop..feature/fase-1a-base
git diff --stat feature/fase-1a-base..feature/fase-1b-contratos
```

Revisar 1A primeiro, depois 1B. Não executar `switch` com mudanças próprias sem
preservá-las. `doc/progress/origens.md` registra revisões, limites e decisões.

## 2. Testes técnicos e transporte HTTP real

```powershell
docker run --rm --mount "type=bind,source=$((Get-Location).Path),target=/workspace" --mount type=volume,source=numberone-os-phase1-maven,target=/root/.m2 -w /workspace maven:3.9.11-eclipse-temurin-25 sh ./mvnw -B -ntp clean verify
if ($LASTEXITCODE -ne 0) { throw 'Falha no Maven' }
Get-Content target/surefire-reports/*.txt
Get-Content target/failsafe-reports/*.txt
```

Esperado: 7 testes técnicos de correlação, 9 testes HTTP em porta aleatória; zero
falhas/erros/skips e gate de cobertura aprovado. Testes HTTP usam cliente JDK e
servidor Tomcat real; não provam persistência, SQS ou integração com outros serviços.
Abrir `target/site/jacoco/index.html` para conferir cobertura e exclusão do bootstrap.

## 3. Contratos sem rede

```powershell
docker build -f scripts/contracts.Dockerfile -t numberone-os-contract-validation .
if ($LASTEXITCODE -ne 0) { throw 'Falha ao preparar validador' }
docker run --rm --network none --mount "type=bind,source=$((Get-Location).Path),target=/workspace" numberone-os-contract-validation
if ($LASTEXITCODE -ne 0) { throw 'Falha de contrato' }
Get-Content target/contract-validation.json
```

Esperado: 24 schemas, 21 tipos, 31 fixtures válidas, 21 inválidas rejeitadas, 8
grupos de verificação e zero falhas. Os negativos têm keyword/caminho esperados;
uma rejeição por motivo errado falha o teste. UUID/data são validados efetivamente.
Campos opcionais desconhecidos são aceitos em todos os níveis de objeto.

Abrir a [matriz](../contracts/saga/v1/messages.md) e comparar orçamento/aprovação/
reserva/pagamento/consumo de R$ 350 com um kit. Comparar a variante sem materiais
(R$ 150), fechamento antecipado e resultados NOT_RESERVED/NOT_CHARGED.
Esses arquivos não foram enviados a filas; são exemplos estruturais alternativos.

## 4. Construir e demonstrar a imagem

```powershell
docker build -t numberone-service-order:local .
if ($LASTEXITCODE -ne 0) { throw 'Falha no build da imagem' }
docker compose up -d --no-build
# Aguardar alguns segundos pelo startup; repetir consulta se ainda não estiver pronto.
Invoke-RestMethod http://127.0.0.1:18080/actuator/health
Invoke-RestMethod http://127.0.0.1:18080/actuator/health/liveness
Invoke-RestMethod http://127.0.0.1:18080/actuator/health/readiness
Invoke-RestMethod http://127.0.0.1:18080/api/public/health -Headers @{'X-Correlation-Id'='demo-phase1'}
docker image inspect numberone-service-order:local --format '{{.Config.User}}'
docker compose down
```

Esperado: HTTP 200, status UP, aplicação numberone-service-order e usuário 10001:10001.
As sondas não atestam banco/mensageria. `/api/public/health` sem correlação retorna
400; endpoints de negócio, `/login` e `/actuator/env` retornam 403 mesmo com headers
forjados. Esses negativos já são exercitados nos testes HTTP reais.
O Compose publica somente loopback IPv4, porta 18080. Não utiliza recursos do legado.

## 5. Validação estática de manifests e workflows

```powershell
New-Item -ItemType Directory -Path rendered-manifests -Force | Out-Null
kubectl kustomize k8s/base | Set-Content -Encoding utf8 rendered-manifests/os.yaml
docker run --rm --mount "type=bind,source=$((Get-Location).Path)\rendered-manifests,target=/manifests,readonly" ghcr.io/yannh/kubeconform:v0.8.0 -strict -summary -kubernetes-version 1.36.0 /manifests
docker run --rm --mount "type=bind,source=$((Get-Location).Path),target=/repo,readonly" -w /repo rhysd/actionlint:1.7.7
```

Esperado: dois recursos válidos, zero inválidos/erros/skips; actionlint sem erros.
Não executar apply. Validação local de YAML não equivale a CI remoto, deploy,
ruleset ativo ou observabilidade recebendo dados.

## 6. Encaminhar revisão

Matheus revisa base, API e ADRs. Julio/Anderson revisam schemas/vetores. Marcelo
revisa [pedidos](../contracts/saga/v1/integration-requests.md) e governança.
Registrar aceite com commit exato. Não avançar à fase 2, fazer merge ou deploy.
