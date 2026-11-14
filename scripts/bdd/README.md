# BDD da Saga da OS

O teste usa OS, Billing e Inventory em contêineres, três bancos PostgreSQL e SQS no LocalStack. As requisições passam pelas APIs dos serviços com dados sintéticos. O adaptador de teste substitui somente as chamadas externas ao Mercado Pago. O Billing processa o webhook assinado e publica seus eventos com o outbox real. O teste também lê o outbox do Billing e a inbox da OS para confirmar a publicação e aplicação dos eventos.

## Revisões compatíveis

| Aplicação | Base da execução local |
| --- | --- |
| OS | `f6212a7` ou branch derivada |
| Inventory | `b4541da` ou branch derivada |
| Billing | `da643ed` com `LocalMercadoPagoFixture.java` no build descartável |

A revisão do Billing acima envia os tokens internos aceitos pelas revisões de OS e Inventory da tabela. Em `9d5eb18`, o Billing retirou esses tokens. Ao validar revisões novas, use os três SHAs candidatos à entrega e anote quais foram executados.

## Execução

Pré-requisitos: Docker com contêineres Linux, PowerShell 7 e Python 3.10+. A aplicação usa portas locais aleatórias. Os JARs da OS e do Inventory são gerados com o Maven Wrapper de cada serviço, usando Java 25 e Java 21, respectivamente.

Gere o JAR do Billing com Java 25 em um checkout descartável do commit a testar. Copie `LocalMercadoPagoFixture.java` para `src/main/java/br/com/fiap/numberone/billing/` nesse checkout e execute `./mvnw -DskipTests package`. Use esse JAR somente no ambiente BDD. O código da fixture permanece no repositório da OS e o build publicado do Billing segue o processo normal.

Na raiz do repositório da OS, com Behave instalado em um ambiente virtual:

```powershell
python -m pip install -r scripts/bdd/requirements.txt
$env:NUMBERONE_BDD_RUN = 'numberone-saga-bdd'
$env:PYTHONDONTWRITEBYTECODE = '1'

try {
    ./scripts/bdd/start.ps1 -Run $env:NUMBERONE_BDD_RUN `
        -OsJar <os.jar> -BillingJar <billing-fixture.jar> -InventoryJar <inventory.jar>
    python -X utf8 -m behave scripts/bdd/features --junit --junit-directory target/bdd
} finally {
    ./scripts/bdd/stop.ps1 -Run $env:NUMBERONE_BDD_RUN
}
```

Use um nome exclusivo para cada execução simultânea. Cada execução começa com bancos vazios. Guarde o JUnit e os três SHAs junto à evidência da entrega. A homologação com a API real do Mercado Pago é uma evidência complementar.
