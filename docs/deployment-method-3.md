# Method 3 Deployment

## Flow

`push -> GitHub Actions build image -> push to ACR -> SSH to server -> pull image -> restart`

## GitHub Secrets

- `ACR_REGISTRY`
- `ACR_NAMESPACE`
- `ACR_REPOSITORY`
- `ACR_USERNAME`
- `ACR_PASSWORD`
- `SERVER_HOST`
- `SERVER_PORT`
- `SERVER_USER`
- `SERVER_SSH_KEY`
- `SERVER_DEPLOY_PATH`
- `MYSQL_ROOT_PASSWORD`
- `MYSQL_DATABASE`
- `REDIS_PASSWORD`
- `RABBITMQ_USERNAME`
- `RABBITMQ_PASSWORD`
- `MYRENT_JWT_SECRET`

## Server First-Time Prep

```bash
mkdir -p /home/ubuntu/myrent
```

## After Workflow Runs

- Check GitHub Actions logs
- Check server containers

```bash
cd /home/ubuntu/myrent
docker compose ps
docker compose logs -f backend
```
