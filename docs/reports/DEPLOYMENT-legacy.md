# NexusShip Deployment Guide

## Backend Environment Variables (Render)

| NAME | VALUE | Required |
|---|---|---|
| `NEXUS_DB_URL` | `jdbc:postgresql://dpg-d9eaiojrjlhs73c266a0-a.oregon-postgres.render.com:5432/nexus_frkl` | Yes |
| `NEXUS_DB_USERNAME` | `root` | Yes |
| `NEXUS_DB_PASSWORD` | `1cht3IDZOhlxRk71KDY4IM7t0GbKR3Jz` | Yes |
| `NEXUS_JWT_SECRET` | `DKq33JFFZL7g2gytF/juyB3cxH9V4QNkSJfG3ER/qAGgXMy9Y91tq3Ge+MQZOdNg` | Yes |
| `NEXUS_REDIS_HOST` | `red-d9eatttaeets73aq9npg` | Yes |
| `NEXUS_REDIS_PORT` | `6379` | Yes |
| `NEXUS_KAFKA_ENABLED` | `false` | Yes |
| `NEXUS_KAFKA_LISTENER_AUTO_START` | `false` | Yes |
| `NEXUS_KAFKA_AUTOCONFIG_EXCLUDE` | `org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration` | Yes |
| `app.frontend-url` | `https://nexus-silk-five-61.vercel.app` | Yes |
| `app.cors.allowed-origins` | `https://nexus-silk-five-61.vercel.app` | Yes |
| `spring.jpa.hibernate.ddl-auto` | `update` | Yes |

---

## Frontend Environment Variables (Vercel)

| NAME | VALUE |
|---|---|
| *(None required — API base URL is hardcoded as `/api/v1`)* | |

---

## Post-Deploy Steps

1. Deploy backend on Render → get URL (e.g. `https://nexus-backend-xxxx.onrender.com`)
2. Frontend already deployed at: `https://nexus-silk-five-61.vercel.app`
3. Update Render env vars `app.frontend-url` and `app.cors.allowed-origins` with Vercel URL
4. Update `nexus-oms-frontend/vercel.json` with backend URL:

```json
{
  "rewrites": [
    {
      "source": "/api/:path*",
      "destination": "https://nexus-backend-xxxx.onrender.com/api/:path*"
    }
  ]
}
```

5. Redeploy both services
