# Pedido a Marcelo — governança da OS (não aplicado)

Destino previsto: `PosTech15SOAT/numberone-service-order`, público.
Referência: governance main `2d35180492efcf2ff6fcdd6ab865e8cff7abe151`.

Em `terraform/variables.tf`, preservar todas as entradas existentes e acrescentar:

```hcl
# protected_repositories:
numberone-service-order = ["main", "develop"]
# required_status_checks_by_repository (somente após execução real bem-sucedida):
numberone-service-order = ["Required validation"]
# main_promotion_repositories (set):
"numberone-service-order"
```

Atualizar a tabela do README e `terraform.tfvars.example` coerentemente. O trecho
é uma solicitação, não um arquivo Terraform completo nem evidência de aplicação.

Sequência: publicar repositório/branches e workflows após autorização; executar CI;
comprovar `Required validation`; abrir uma promoção válida de teste quando apropriado
para observar `Validate promotion source` sem merge; só então exigir os checks.
Marcelo prepara PR, `terraform fmt -check -recursive`, init sem backend, validate,
plan e revisão. Apply exige autorização própria e não faz parte desta entrega.

Atualização de publicação: repositório público e [PR #1](https://github.com/PosTech15SOAT/numberone-service-order/pull/1)/[PR #2](https://github.com/PosTech15SOAT/numberone-service-order/pull/2)
criados após autorização explícita do usuário. Consultar o registro de CI em
`doc/progress/fase-1.md` antes de exigir checks. **Rulesets da OS não aplicados/verificados;
nenhum repositório existente alterado. Validate promotion source ainda não foi
executado remotamente, pois não há PR para main.**
