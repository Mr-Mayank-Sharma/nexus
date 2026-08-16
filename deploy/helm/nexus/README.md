# NexusShip OMS — Helm Chart

Deploys the full NexusShip OMS stack on Kubernetes, mirroring
`docker-compose.prod.yml` at repo root:

- **backend** — Spring Boot app (Deployment, 2 replicas by default)
- **frontend** — Nginx-served PWA (Deployment)
- **postgres** — pgvector/pg16 (StatefulSet + PVC)
- **redis** — Redis 7 (StatefulSet + PVC)
- **kafka** — Apache Kafka 3.7 (StatefulSet + PVC)
- **ingress** — nginx-controller ingress for the frontend

## Prerequisites

- Kubernetes 1.25+
- Helm 3
- Container images pushed to a registry reachable by the cluster
  (`nexus/oms-backend:1.0.0`, `nexus/oms-frontend:1.0.0` — override
  `image.*.repository` / `image.*.tag` in `values.yaml`)

## Install

```bash
helm repo add nexus-local ./deploy/helm/nexus   # optional
helm install nexus ./deploy/helm/nexus \
  --namespace nexus \
  --create-namespace
```

### Secrets

Secrets are **never** placed in `values.yaml`. Create the Secret first
(the chart references it via `secrets.existingSecret`, default
`nexus-secrets`):

```bash
kubectl create secret generic nexus-secrets \
  --from-literal=db-password='CHANGE_ME' \
  --from-literal=redis-password='CHANGE_ME' \
  --from-literal=jwt-secret='CHANGE_ME' \
  --from-literal=openai-api-key='' \
  --namespace nexus
```

For non-production evaluation you may let the chart create the Secret by
setting `secrets.create: true` and providing the values — never do this
in a real cluster.

### Ingress

Default `ingress.hosts[0].host` is `oms.example.com`. Override for your
domain and supply a TLS secret (`ingress.tls[0].secretName`, default
`nexus-tls`):

```bash
helm upgrade nexus ./deploy/helm/nexus \
  --namespace nexus \
  --set ingress.hosts[0].host=oms.yourdomain.com \
  --set ingress.tls[0].secretName=nexus-tls \
  --set ingress.tls[0].hosts[0]=oms.yourdomain.com
```

### Verify

```bash
kubectl rollout status deploy/nexus-backend -n nexus
kubectl rollout status deploy/nexus-frontend -n nexus
kubectl port-forward svc/nexus-backend 8080:8080 -n nexus
curl http://localhost:8080/api/v1/actuator/health
```

## Configuration

Key `values.yaml` settings:

| Parameter | Default | Description |
|---|---|---|
| `replicaCount` | `2` | Backend/frontend replicas |
| `image.*.repository` / `image.*.tag` | `nexus/*:1.0.0` | Container images |
| `postgres.storage` / `redis.storage` / `kafka.storage` | `20Gi`/`5Gi`/`20Gi` | PVC sizes |
| `env.corsOrigins` / `env.frontendUrl` | `https://oms.example.com` | Backend CORS / frontend URL |
| `ingress.enabled` | `true` | Create Ingress |
| `secrets.create` | `false` | Let chart create Secret |

## Upgrades

Backend and frontend are Deployments → rolling upgrade by default. The
stateful stores (postgres/redis/kafka) require `kubectl rollout restart`
only when their images/args change; data persists on PVCs.

## Backup & Recovery

See `docs/DEPLOYMENT.md` §4 (Database backup/restore) and §12
(Kubernetes) for the pg_dump/PG restore procedures and retention
recommendations.
