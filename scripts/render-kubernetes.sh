#!/usr/bin/env bash
set -euo pipefail

: "${IMAGE_URI:?Informe a imagem por digest.}"
: "${K8S_NAMESPACE:?Informe o namespace.}"
if [[ ! "$IMAGE_URI" =~ ^[a-zA-Z0-9./:_-]+@sha256:[a-f0-9]{64}$ ]]; then
  echo "IMAGE_URI deve identificar uma imagem por digest sha256." >&2
  exit 1
fi
render_directory="$(mktemp -d)"
trap 'rm -rf "$render_directory"' EXIT
cp -R "$(dirname "$0")/../k8s" "$render_directory/k8s"
jq -n --arg image "${IMAGE_URI%@*}" --arg digest "${IMAGE_URI##*@}" --arg namespace "$K8S_NAMESPACE" '{
  apiVersion: "kustomize.config.k8s.io/v1beta1",
  kind: "Kustomization",
  namespace: $namespace,
  resources: ["k8s/overlays/homologation"],
  images: [{name: "image.invalid/numberone-service-order", newName: $image, digest: $digest}]
}' > "$render_directory/kustomization.yaml"
kubectl kustomize "$render_directory"
