param(
    [Parameter(Mandatory)][ValidatePattern('^[a-z0-9][a-z0-9-]+$')][string]$Run,
    [Parameter(Mandatory)][string]$OsJar,
    [Parameter(Mandatory)][string]$BillingJar,
    [Parameter(Mandatory)][string]$InventoryJar
)
$ErrorActionPreference='Stop'
$PSNativeCommandUseErrorActionPreference=$true
$n=$Run

$java25='maven:3.9.11-eclipse-temurin-25'
$java21='maven:3.9.14-eclipse-temurin-21'
$branch='00000000-0000-4000-8000-000000000321'
$endpoint="http://${n}-sqs:4566"
$queue="$endpoint/queue/us-east-1/000000000000/numberone-billing-events"
$OsJar = (Resolve-Path -LiteralPath $OsJar).Path
$BillingJar = (Resolve-Path -LiteralPath $BillingJar).Path
$InventoryJar = (Resolve-Path -LiteralPath $InventoryJar).Path
docker network create $n | Out-Null
docker run --rm -d --name "$n-pg" --network $n -e POSTGRES_PASSWORD=local-test postgres:17-alpine
docker run --rm -d --name "$n-sqs" --network $n -e SERVICES=sqs -e SQS_ENDPOINT_STRATEGY=path localstack/localstack:4.14.0
for($i=0;$i -lt 60;$i++){
    docker exec "$n-pg" sh -c 'pg_isready -h 127.0.0.1 -U postgres >/dev/null && echo ready || true' | Set-Variable ready
    if($ready -eq 'ready'){break}
    Start-Sleep -Seconds 1
}
@"
CREATE USER os_local PASSWORD 'local-test';
CREATE USER billing_local PASSWORD 'local-test';
CREATE USER inventory_local PASSWORD 'local-test';
CREATE DATABASE os_db OWNER os_local;
CREATE DATABASE billing_db OWNER billing_local;
CREATE DATABASE inventory_db OWNER inventory_local;
"@ | docker exec -i "$n-pg" psql -X -v ON_ERROR_STOP=1 -U postgres
for($i=0;$i -lt 60;$i++){
    if((docker inspect --format '{{.State.Health.Status}}' "$n-sqs") -eq 'healthy'){break}
    Start-Sleep -Seconds 1
}
@'
import boto3,json
s=boto3.client('sqs',endpoint_url='http://localhost:4566',region_name='us-east-1',aws_access_key_id='test',aws_secret_access_key='test')
dead=s.create_queue(QueueName='numberone-billing-events-dlq',Attributes={'MessageRetentionPeriod':'1209600'})['QueueUrl']
arn=s.get_queue_attributes(QueueUrl=dead,AttributeNames=['QueueArn'])['Attributes']['QueueArn']
print(s.create_queue(QueueName='numberone-billing-events',Attributes={'MessageRetentionPeriod':'345600','VisibilityTimeout':'60','ReceiveMessageWaitTimeSeconds':'20','RedrivePolicy':json.dumps({'deadLetterTargetArn':arn,'maxReceiveCount':5})}))
'@ | docker exec -i "$n-sqs" python3 -

function Start-Service($Service,$Jar,$Image,$Settings) {
    $path = (Resolve-Path $Jar).Path

    $options=@()
    foreach($entry in $Settings.GetEnumerator()){$options+=@('-e',"$($entry.Key)=$($entry.Value)")}
    docker run --rm -d --name "$n-$Service" --network $n -p '127.0.0.1::8080' --mount "type=bind,src=$path,dst=/service.jar,readonly" @options $Image java -jar /service.jar
}
$aws=@{AWS_ACCESS_KEY_ID='test';AWS_SECRET_ACCESS_KEY='test';AWS_REGION='us-east-1'}
Start-Service inventory $InventoryJar $java21 @{
    DB_URL="jdbc:postgresql://$n-pg:5432/inventory_db";DB_USERNAME='inventory_local';DB_PASSWORD='local-test'
}
Start-Service billing $BillingJar $java25 ($aws + @{
    DB_URL="jdbc:postgresql://$n-pg:5432/billing_db";DB_USERNAME='billing_local';DB_PASSWORD='local-test'
    OS_SERVICE_BASE_URL="http://$n-os`:8080";INVENTORY_SERVICE_BASE_URL="http://$n-inventory`:8080"
    SPRING_PROFILES_ACTIVE='local';LOCAL_INTEGRATION_FIXTURE='true'
    MERCADO_PAGO_WEBHOOK_ENABLED='true';MERCADO_PAGO_WEBHOOK_ENVIRONMENT='TEST';MERCADO_PAGO_WEBHOOK_SECRET='fictitious-webhook-secret'
    OUTBOX_ENABLED='true';SQS_ENDPOINT=$endpoint;SQS_QUEUE_URL=$queue
    BUDGET_NOTIFICATION_ENCRYPTION_KEY='AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=';BUDGET_APPROVAL_BASE_URL='http://localhost:8080'
})
Start-Service os $OsJar $java25 ($aws + @{
    DB_URL="jdbc:postgresql://$n-pg:5432/os_db";DB_USERNAME='os_local';DB_PASSWORD='local-test'
    OS_BRANCH_ID=$branch
    BILLING_BASE_URL="http://$n-billing`:8080";INVENTORY_BASE_URL="http://$n-inventory`:8080"
    BILLING_SQS_ENABLED='true';BILLING_SQS_MODE='dedicated';BILLING_SQS_QUEUE_URL=$queue;BILLING_SQS_ENDPOINT=$endpoint
    APP_MATERIAL_REFERENCE_PROVIDER='http'
})
foreach($service in @('inventory','billing','os')){
    $addr=(docker port "$n-$service" 8080/tcp | Select-Object -First 1).Trim()
    $ready=$false
    for($i=0;$i -lt 55;$i++){
        try {
            if((Invoke-WebRequest "http://$addr/actuator/health" -TimeoutSec 2).StatusCode -eq 200){$ready=$true;break}
        } catch {}
        Start-Sleep -Seconds 1
    }
    if(!$ready){docker logs --tail 45 "$n-$service";throw "Serviço $service indisponível"}
    "$service http://$addr"
}
