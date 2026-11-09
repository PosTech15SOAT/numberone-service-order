# Manifestos para revisão, sem deploy nesta fase

`base` contém apenas Deployment e Service ClusterIP exclusivos da OS, probes e
configuração do agente Datadog. A imagem inválida é intencional: substituir somente
na fase de deploy autorizada. Não executar `kubectl apply` nesta entrega.

A integração futura preserva o **NLB interno**, Gateway/VPC Link e Datadog existentes.
ClusterIP é a base interna, não uma substituição do NLB por ALB. Marcelo deve definir
overlay, namespace, ECR, NLB, restrições de rede e contexto de identidade. Nenhum
recurso compartilhado, namespace produtivo, IAM ou Secret é criado aqui.

Validação estática: `kubectl kustomize k8s/base` seguido de kubeconform v0.8.0
com schemas Kubernetes 1.36.0, como o CI da origem. Isso não prova deploy ou
acesso ao cluster.

Fase 2 acrescenta referências ao Secret exclusivo `numberone-os-database`
(`url`, `username`, `password`) e ConfigMap `numberone-os-runtime` (`branch-id`).
Esses recursos **não são criados** nesta entrega. Banco/credenciais/rede dependem
de Marcelo. O perfil é `production`, sem fixture/local identity; confiança nos
headers permanece `false` até a proteção Gateway/rede ser revisada. Readiness
agora verifica o banco. Não usar o Compose local como configuração produtiva.
