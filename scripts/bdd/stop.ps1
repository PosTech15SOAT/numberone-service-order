param([Parameter(Mandatory)][ValidatePattern('^[a-z0-9][a-z0-9-]+$')][string]$Run)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$containers = @(docker ps -aq --filter "network=$Run" | Where-Object { $_ })
if ($containers.Count -gt 0) { docker rm -f @containers }
$networks = @(docker network ls --format '{{.Name}}')
if ($networks -contains $Run) { docker network rm $Run }
