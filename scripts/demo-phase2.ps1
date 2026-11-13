param([string]$BaseUrl = 'http://127.0.0.1:18080')
$ErrorActionPreference = 'Stop'
# Development-only synthetic data. Never print requests, identity or customer responses.
if ($BaseUrl -notmatch '^http://(127\.0\.0\.1|localhost):\d+$') { throw 'Use somente a instancia local de demonstracao.' }
$headers = @{'X-Correlation-Id'='demo-phase2'}
function Invoke-Os([string]$Method, [string]$Path, $Body = $null, [int]$Expected = 200) {
    $params = @{Uri="$BaseUrl$Path"; Method=$Method; Headers=$headers; SkipHttpErrorCheck=$true; ContentType='application/json'}
    if ($null -ne $Body) { $params.Body = ConvertTo-Json -Depth 10 -Compress -InputObject $Body }
    $response = Invoke-WebRequest @params
    if ([int]$response.StatusCode -ne $Expected) { throw "Resultado inesperado: $Method $Path HTTP $($response.StatusCode), esperado $Expected" }
    if ($response.Content) { return $response.Content | ConvertFrom-Json }
}
$ready = $false
for ($attempt = 0; $attempt -lt 60; $attempt++) {
    try { $health = Invoke-RestMethod "$BaseUrl/actuator/health/readiness"; $ready = $health.status -eq 'UP' } catch { $ready = $false }
    if ($ready) { break }; Start-Sleep -Seconds 2
}
if (-not $ready) { throw 'Readiness nao ficou UP.' }
$customers = @(Invoke-Os GET '/api/admin/clientes')
$customer = $customers | Where-Object { $_.email -eq 'fixture@example.invalid' } | Select-Object -First 1
if (-not $customer) {
    $customer = Invoke-Os POST '/api/admin/clientes' @{nome='Cadastro sintetico'; tipoDocumento='PESSOA_FISICA'; documento='52998224725'; email='fixture@example.invalid'; telefone='00000000000'; endereco='Endereco ficticio'; ativo=$true} 201
}
$vehicles = @(Invoke-Os GET '/api/admin/veiculos')
$vehicle = $vehicles | Where-Object { $_.placa -eq 'TST1A23' -and $_.idCliente -eq $customer.id } | Select-Object -First 1
if (-not $vehicle) { $vehicle = Invoke-Os POST '/api/admin/veiculos' @{placa='TST1A23'; marca='Fixture'; modelo='Fixture'; ano=2026; idCliente=$customer.id} 201 }
$services = @(Invoke-Os GET '/api/admin/servicos')
$service = $services | Where-Object { $_.code -eq 'DEMO-1' } | Select-Object -First 1
if (-not $service) {
    $service = Invoke-Os POST '/api/admin/servicos' @{codigo='DEMO-1'; nome='Servico demonstrativo'; descricao='Fixture'; tipoServico='REVISAO'; valorBase='150.00'; tempoEstimadoMinutos=30} 201
}
$order = Invoke-Os POST '/api/admin/ordens-servico' @{idCliente=$customer.id; idVeiculo=$vehicle.id; descricaoInicial='Inspecao'; descricaoDiagnostico='Inspecao prevista'; dataHoraEntrada='2026-11-01T10:00:00'} 201
$order = Invoke-Os PATCH "/api/admin/ordens-servico/$($order.id)/iniciar-diagnostico" @{descricaoDiagnosticoFinal='Diagnostico ficticio'; dataHoraPrevista='2026-11-01T14:00:00'}
$item = Invoke-Os POST '/api/admin/itens-ordem-servico' @{idOrdemServico=$order.id; idServico=$service.id; valor='150.00'} 201
$material = Invoke-Os POST "/api/admin/itens-ordem-servico/$($item.id)/insumos" @{idMaterial='80000000-0000-4000-8000-000000000001'; quantidadePrevista=1} 201
$tracking = Invoke-Os GET "/api/public/ordens-servico/$($order.id)/acompanhamento"
if ([decimal]$tracking.estimativa.valorEstimado -ne 350 -or $null -ne $tracking.orcamentoAprovado -or $tracking.pagamentoConfirmado) { throw 'Estimativa/aprovacao incorretas.' }
if ($material.quantidadePrevista -ne 1 -or $material.quantidadeReservada -ne 0 -or $material.quantidadeConsumida -ne 0) { throw 'Quantidades incorretas.' }
$start = Invoke-Os PATCH "/api/admin/itens-ordem-servico/$($item.id)/iniciar" $null 409
if ($start.message -ne 'PAYMENT_NOT_CONFIRMED') { throw 'Guarda financeira incorreta.' }
$null = Invoke-Os PATCH "/api/admin/itens-ordem-servico/$($item.id)/concluir" $null 409
$null = Invoke-Os PATCH "/api/admin/ordens-servico/$($order.id)/entregar" $null 409
[pscustomobject]@{Readiness='UP'; Estado=$tracking.status; Estimativa='350.00 BRL'; OrcamentoAprovado=$false; Previsto=1; Reservado=0; Consumido=0; Inicio=409; ConclusaoPrematura=409; EntregaPrematura=409; Inventory='FIXTURE'} | Format-List
