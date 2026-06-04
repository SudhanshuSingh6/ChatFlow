# ChatFlow on Kubernetes (local)

Kustomize manifests for running the full stack on a local cluster (kind/minikube). The app
Deployments live in `base/`; the `overlays/local/` overlay adds the `chatflow` namespace and
single-replica dev infra (Postgres ×2, Redis, Kafka, MinIO, Jaeger) + a bucket-init Job.

## 1. Build images and load them into the cluster

The Deployments reference `chatflow-{core,ai,media,gateway}:local` with `imagePullPolicy:
IfNotPresent`, so build locally and load them in (no registry needed).

```bash
# from the reactor root (chatflow-backend/)
for m in core ai media gateway; do
  docker build -t chatflow-$m:local -f chatflow-$m/Dockerfile .
done

# kind:
kind load docker-image chatflow-core:local chatflow-ai:local chatflow-media:local chatflow-gateway:local
# minikube: eval $(minikube docker-env) before building, or `minikube image load chatflow-*:local`
```

## 2. Apply

```bash
kubectl apply -k k8s/overlays/local
kubectl -n chatflow get pods -w
```

Requires an ingress controller (e.g. ingress-nginx) for the gateway Ingress, and metrics-server
for the media HPA. Without an ingress controller, reach the gateway via:

```bash
kubectl -n chatflow port-forward svc/gateway 8088:8088
```

## 3. Verify

Same end-to-end flow as docker-compose, through `:8088` — login, send messages, `POST
/ai/conversations/{id}/ask`, upload media (thumbnail via the media worker), traces in Jaeger
(`kubectl -n chatflow port-forward svc/jaeger 16686:16686`).

## Notes
- Storage is ephemeral `emptyDir` — pods losing data on restart is expected for local dev. Swap
  in PVCs / operators for anything real.
- Secrets are in `base/secret.yaml` as dev defaults; replace via a real secret manager in prod
  (the apps also fail fast under the `prod` Spring profile if the JWT/internal defaults remain).
