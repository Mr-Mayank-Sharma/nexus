# 14 — AWS Dev Server: Clean, Set Up, Connect to GitHub Actions, Access by IP

> **Goal:** one AWS EC2 machine, no domain, reachable at `http://<SERVER_IP>`, that redeploys
> automatically on every push to `main`.
>
> **How it fits the existing pipeline:** `.github/workflows/cd.yml` already deploys every push
> to `main` into the GitHub environment **`staging`**: it builds images, pushes them to GHCR,
> then SSHes to the server, runs `docker compose pull && up` in `/opt/nexus-ship` and waits
> for health, rolling back on failure. This machine becomes that staging target. **Nothing in
> the production job or `docker-compose.prod.yml` changes.** Production is set up later with
> a domain and HTTPS (§9).

Placeholders used below: `<SERVER_IP>` is the EC2 public (Elastic) IP, `<KEY.pem>` is your EC2
key pair file, and `<GH_USER>` is `Mr-Mayank-Sharma`.

---

## 0. What you'll end up with

```
GitHub push to main
  └─ CD: build backend + frontend images → GHCR (ghcr.io/mr-mayank-sharma/…)
       └─ Deploy → Staging: SSH deploy@<SERVER_IP>
            └─ /opt/nexus-ship: docker compose pull && up -d → health check → rollback on failure

Browser → http://<SERVER_IP>  (nginx :80)  ── /api/* ──▶ backend :8080 ─▶ postgres, redis, kafka, minio
```

---

## 1. AWS console (one time)

1. **Instance size.** The core stack (Postgres, Redis, Kafka, backend, frontend, MinIO) needs
   about 4–5 GB of RAM; adding monitoring and AI needs about 8 GB.
   - **Recommended:** `t3.large` (2 vCPU, 8 GB), Ubuntu Server 24.04 LTS, **30 GB gp3** disk.
   - `t3.medium` (4 GB) works for the core stack only, with the swap file from §3.
2. **Elastic IP.** EC2 → Elastic IPs → *Allocate* → *Associate* with the instance. Without
   one, the public IP changes on every stop/start and you'd have to update the GitHub secret
   and `CORS_ORIGINS` each time.
3. **Security Group (inbound rules).** This is the real firewall. Docker publishes container
   ports straight through iptables and **bypasses `ufw`**, so Postgres (5433), Redis (6379,
   no password), Kafka, MinIO and the exporters must never be opened here.

   | Port | Source | Why |
   |---|---|---|
   | 80 | `0.0.0.0/0` (or your IP while testing) | The app (nginx serves the UI and proxies `/api`) |
   | 22 | `0.0.0.0/0` | SSH from GitHub Actions. Runner IPs change on every job, so key-only auth plus fail2ban (§3) is what protects it |
   | 3001 | *your IP*/32 only | Optional: Grafana, if you enable the monitoring profile |
   | 9001 | *your IP*/32 only | Optional: MinIO console |

   Delete every other inbound rule.

---

## 2. Clean the machine

> ⚠️ **This deletes every container, image and Docker volume on the machine, including
> database data.** If anything there matters, back it up first (step 2.1).

SSH in with your EC2 key:

```bash
ssh -i <KEY.pem> ubuntu@<SERVER_IP>
```

### 2.1 Optional backup of an existing Postgres

```bash
docker ps --format '{{.Names}}\t{{.Image}}'            # find the postgres container name
docker exec <postgres-container> pg_dumpall -U nexus > ~/backup-$(date +%F).sql
# copy it to your Mac from a second terminal:
scp -i <KEY.pem> ubuntu@<SERVER_IP>:~/backup-*.sql .
```

### 2.2 Remove everything Docker-related and old app files

```bash
# stop and delete all containers, then images, networks, build cache and VOLUMES
docker ps -aq | xargs -r docker rm -f
docker system prune -a --volumes -f

# old deployments / checkouts (adjust to what `ls` shows)
ls -la /opt ~
sudo rm -rf /opt/nexus-ship ~/nexus ~/Nexus-docker ~/nexus-*

# anything on the host already holding port 80 or 8080?
sudo ss -ltnp | grep -E ':80 |:8080 ' || echo "ports free"
# if a host nginx/apache shows up:
sudo systemctl disable --now nginx apache2 2>/dev/null || true

# leftover scheduled jobs
crontab -l 2>/dev/null; sudo ls /etc/cron.d
```

---

## 3. Base server setup

```bash
sudo apt-get update && sudo apt-get -y upgrade
sudo apt-get install -y ca-certificates curl git fail2ban unattended-upgrades
sudo timedatectl set-timezone UTC          # app timestamps are zone-less; keep host in UTC

# Docker Engine + compose plugin (official repo)
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
 | sudo tee /etc/apt/sources.list.d/docker.list
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
docker compose version

# Keep container logs from filling the disk
echo '{"log-driver":"json-file","log-opts":{"max-size":"20m","max-file":"3"}}' | sudo tee /etc/docker/daemon.json
sudo systemctl restart docker

# Swap (strongly recommended on 4–8 GB machines; Kafka + JVM + Postgres spike at start-up)
sudo fallocate -l 4G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

**SSH hardening.** Port 22 is open to the internet, so allow keys only:

```bash
sudo sed -i 's/^#\?PasswordAuthentication .*/PasswordAuthentication no/' /etc/ssh/sshd_config
sudo sed -i 's/^#\?PermitRootLogin .*/PermitRootLogin no/' /etc/ssh/sshd_config
sudo systemctl restart ssh
sudo systemctl enable --now fail2ban
```

### 3.1 A dedicated deploy user for GitHub Actions

```bash
sudo adduser --disabled-password --gecos "" deploy
sudo usermod -aG docker deploy                 # note: docker group = root-equivalent on this host
sudo mkdir -p /opt/nexus-ship && sudo chown deploy:deploy /opt/nexus-ship
sudo -u deploy mkdir -p /home/deploy/.ssh && sudo chmod 700 /home/deploy/.ssh
```

On **your Mac**, create a key used only by GitHub Actions:

```bash
ssh-keygen -t ed25519 -C "github-actions-staging" -f ~/.ssh/nexus_staging_deploy -N ""
cat ~/.ssh/nexus_staging_deploy.pub      # copy this line
```

Back on the **server**, authorise it:

```bash
echo "<PASTE THE .pub LINE>" | sudo tee /home/deploy/.ssh/authorized_keys
sudo chown deploy:deploy /home/deploy/.ssh/authorized_keys && sudo chmod 600 /home/deploy/.ssh/authorized_keys
```

Test from your Mac: `ssh -i ~/.ssh/nexus_staging_deploy deploy@<SERVER_IP> 'docker ps'`.

---

## 4. Put the app files on the server

CD only runs `docker compose pull/up` inside `/opt/nexus-ship`, so that folder needs the compose
file, the monitoring configs it mounts, and the `.env`. A shallow clone of the repo is the
simplest way to get them (no secrets live in git):

```bash
sudo -iu deploy
cd /opt/nexus-ship
git clone --depth 1 https://github.com/<GH_USER>/nexus.git .
```

> Private repo? Use a fine-grained token with *Contents: read*:
> `git clone --depth 1 https://<TOKEN>@github.com/<GH_USER>/nexus.git .`
> The token is stored in `.git/config`; remove it afterwards with
> `git remote set-url origin https://github.com/<GH_USER>/nexus.git`.

### 4.1 Server-only compose override (dev-sized, no prod changes)

`docker-compose.prod.yml` starts 18 services, including AI services that have no published
image and a monitoring stack that needs about 1.5 GB of RAM. This override leaves that file
untouched. It moves AI and monitoring behind opt-in *profiles* and caps memory so the JVM
can't take most of the host:

```bash
cat > /opt/nexus-ship/docker-compose.server.yml <<'EOF'
# Dev/staging override, server-local only (not in git). Profiles keep optional parts off by default:
#   docker compose --profile monitoring up -d     # Prometheus/Grafana/Loki/exporters
#   docker compose --profile ai up -d --build     # Python AI services (built on this host)
services:
  backend:
    mem_limit: 2g            # JVM uses 75% of its limit -> ~1.5 GB heap
  kafka:
    environment:
      KAFKA_HEAP_OPTS: "-Xmx512m -Xms256m"
    mem_limit: 1g
  postgres:
    mem_limit: 1g
  ai-ops:            { profiles: ["ai"] }
  ai-intel:          { profiles: ["ai"] }
  postgres-exporter: { profiles: ["monitoring"] }
  redis-exporter:    { profiles: ["monitoring"] }
  kafka-exporter:    { profiles: ["monitoring"] }
  node-exporter:     { profiles: ["monitoring"] }
  cadvisor:          { profiles: ["monitoring"] }
  alertmanager:      { profiles: ["monitoring"] }
  loki:              { profiles: ["monitoring"] }
  promtail:          { profiles: ["monitoring"] }
  prometheus:        { profiles: ["monitoring"] }
  grafana:           { profiles: ["monitoring"] }
EOF
```

### 4.2 The server `.env` (the real secrets live here, never in git)

Generate values on the server:

```bash
openssl rand -base64 48 | tr -d '\n=/+' | cut -c1-48   # JWT_SECRET (>= 32 chars)
openssl rand -base64 32 | tr -d '\n=/+' | cut -c1-32   # APP_CREDENTIAL_ENCRYPTION_KEY (>= 16)
openssl rand -base64 24 | tr -d '\n=/+'                # DB_PASSWORD, S3_SECRET_KEY, GRAFANA_ADMIN_PASSWORD
```

```bash
cat > /opt/nexus-ship/.env <<'EOF'
# --- compose wiring: prod file + this server's override -----------------------
COMPOSE_FILE=docker-compose.prod.yml:docker-compose.server.yml
COMPOSE_PROJECT_NAME=nexus

# --- images (CD rewrites NEXUS_IMAGE_TAG on every deploy) -----------------------
NEXUS_IMAGE_OWNER=mr-mayank-sharma
NEXUS_IMAGE_TAG=latest

# --- secrets (paste generated values) ------------------------------------------
DB_PASSWORD=<generated>
JWT_SECRET=<generated, 48 chars>
APP_CREDENTIAL_ENCRYPTION_KEY=<generated, 32 chars>
S3_ACCESS_KEY=nexusminio
S3_SECRET_KEY=<generated>
GRAFANA_ADMIN_PASSWORD=<generated>

# --- runtime ---------------------------------------------------------------------
SPRING_PROFILES_ACTIVE=prod
S3_ENDPOINT=http://minio:9000
S3_BUCKET=nexus-shipping-labels
CORS_ORIGINS=http://<SERVER_IP>
FRONTEND_URL=http://<SERVER_IP>
NEXUS_AI_ENABLED=false
# Temporarily true to create the first admin (step 6), then set false and restart backend.
APP_REGISTRATION_ENABLED=true
EOF
chmod 600 /opt/nexus-ship/.env
```

Notes:
- `SPRING_PROFILES_ACTIVE=prod` turns on the startup safety check: the backend **refuses to
  start** if the JWT secret is shorter than 32 characters, the encryption key is shorter than
  16, or the DB/S3 credentials are defaults (`nexus`, `minioadmin`, …). That's intended.
- `DB_PASSWORD` is applied only when Postgres creates its data volume. Changing it later
  means changing it inside Postgres too (or wiping the volume while this is still a dev box).
- CD deploys with `--no-build`. Without the override, the AI services (which have no
  published images) would make the deploy fail; the `ai` profile keeps them out.

### 4.3 Let the server pull from GHCR

GHCR packages are private by default. Create a **classic** personal access token with only
the `read:packages` scope (GitHub → Settings → Developer settings → Tokens (classic)), then:

```bash
echo "<READ_PACKAGES_TOKEN>" | docker login ghcr.io -u <GH_USER> --password-stdin
```

The login is saved in `/home/deploy/.docker/config.json`, so CD's `docker compose pull` works.

---

## 5. Connect GitHub Actions (environment + secrets)

The staging job reads three secrets from the GitHub **environment** named `staging`:
`STAGING_HOST`, `STAGING_USER` and `STAGING_SSH_KEY`. The production job's
`PRODUCTION_*` secrets are left unset until you have a domain.

**Option A, web UI:** repo → Settings → Environments → **New environment** → `staging` →
*Environment secrets* → add:

| Secret | Value |
|---|---|
| `STAGING_HOST` | `<SERVER_IP>` |
| `STAGING_USER` | `deploy` |
| `STAGING_SSH_KEY` | full contents of `~/.ssh/nexus_staging_deploy` (the **private** key, including the BEGIN/END lines) |

Optionally, under *Deployment branches*, allow only `main`.

**Option B, from your Mac with `gh`:**

```bash
gh api -X PUT repos/<GH_USER>/nexus/environments/staging
gh secret set STAGING_HOST    --env staging --body "<SERVER_IP>"
gh secret set STAGING_USER    --env staging --body "deploy"
gh secret set STAGING_SSH_KEY --env staging < ~/.ssh/nexus_staging_deploy
gh secret list --env staging
```

> The environment's link in `cd.yml` still says `https://staging.nexus-ship.com`. It's only
> the link shown on the GitHub run page and doesn't affect the deploy, so it was left alone.
> Your app is at `http://<SERVER_IP>`.

---

## 6. First start (manual, once) and the first admin

On the server, as `deploy`:

```bash
cd /opt/nexus-ship
docker compose pull            # pulls the images CD has already pushed to GHCR
docker compose up -d
docker compose ps              # postgres/redis healthy; backend becomes healthy in ~60–90 s
docker compose logs -f backend # Ctrl-C when you see "Started NexusOmsApplication"

# Create the S3 bucket in MinIO (once). Values are read from .env.
set -a; . ./.env; set +a
docker run --rm --network nexus_nexus-network --entrypoint sh quay.io/minio/mc -c \
  "mc alias set local http://minio:9000 $S3_ACCESS_KEY $S3_SECRET_KEY && mc mb -p local/$S3_BUCKET"

curl -s http://localhost:8080/api/v1/actuator/health     # {"status":"UP"...}
```

Create the first admin (registration is open only because `APP_REGISTRATION_ENABLED=true`):

```bash
curl -s -X POST http://<SERVER_IP>/api/v1/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<STRONG_PASSWORD>","email":"you@example.com","role":"ADMIN"}'
```

Then close registration again:

```bash
sed -i 's/^APP_REGISTRATION_ENABLED=.*/APP_REGISTRATION_ENABLED=false/' .env
docker compose up -d backend
```

Open **http://<SERVER_IP>** and sign in. The new pages are at `/#/warehouse-twin` and
`/#/order-twin`; they stay empty until you add warehouses, docks, equipment and orders (or
import them through Import/Export).

---

## 7. Automatic deploys from now on

- **Every push to `main`** → CD builds and pushes images tagged `sha-xxxxxxxx` → deploys to
  this server → waits up to 150 s for `/actuator/health` → on failure rolls backend and
  frontend back to the previous tag.
- **Manual redeploy:** `gh workflow run cd.yml -f environment=staging`, or *Actions → CD →
  Run workflow*.
- **Watch it:** `gh run watch`, or *Actions* in GitHub.
- CD only updates the backend and frontend images. Postgres, Redis, Kafka and MinIO keep
  running and keep their volumes.
- **Config changes** (`.env`, `docker-compose.server.yml`) are server-local: edit them, then
  `docker compose up -d`.
- **When `docker-compose.prod.yml` or the monitoring configs change in git**, refresh the
  checkout: `cd /opt/nexus-ship && git pull && docker compose up -d`. CD doesn't pull git, only
  images.

---

## 8. Day-2 commands

```bash
cd /opt/nexus-ship
docker compose ps
docker compose logs --tail=200 backend
docker compose restart backend
docker stats --no-stream                      # memory per container
df -h && docker system df                     # disk
docker image prune -af --filter "until=168h"  # old images (CD pulls a new tag each deploy)

# backup the DB
docker exec nexus-postgres pg_dump -U nexus -Fc nexus_oms > ~/nexus-$(date +%F).dump

# optional extras
docker compose --profile monitoring up -d     # Grafana at http://<SERVER_IP>:3001 (open 3001 to your IP only)
docker compose --profile ai up -d --build     # builds the Python AI services on this host (slow, ~3 GB)
```

**Troubleshooting**

| Symptom | Check |
|---|---|
| CD fails at "Deploy → Staging" with an SSH error | `STAGING_HOST`/`USER`/`SSH_KEY` secrets, Security Group port 22, `authorized_keys` permissions (600) |
| `docker compose pull` → `denied` | §4.3 GHCR login as the `deploy` user |
| Backend restarts with "Startup validation failed" | `docker compose logs backend`: a secret is missing, too short or a default |
| Login fails with **403** in the browser | `CORS_ORIGINS` must be exactly `http://<SERVER_IP>` (no trailing slash), then `docker compose up -d backend` |
| Container killed / exit 137 | Out of memory: add swap, use a bigger instance, or keep the monitoring/AI profiles off |
| Site unreachable | Security Group port 80; `docker compose ps frontend`; `curl -I http://localhost` on the server |

---

## 9. Later: production with a domain (no code changes needed)

1. Point the domain's DNS at the production server's Elastic IP.
2. Put HTTPS in front: Caddy (automatic Let's Encrypt) or an AWS ALB with ACM. Then switch
   `CORS_ORIGINS`/`FRONTEND_URL` to `https://…`. Refresh tokens must not travel over plain
   HTTP in production.
3. Create the GitHub environment **`production`** with `PRODUCTION_HOST`, `PRODUCTION_USER`
   and `PRODUCTION_SSH_KEY`, plus *Required reviewers* so a release needs approval.
4. Release by tagging: `git tag v1.0.0 && git push origin v1.0.0`. The existing
   `deploy-production` job handles it, including rollback.
5. Use a separate server or database, separate secrets, and no `docker-compose.server.yml`
   memory caps (size the instance instead).
