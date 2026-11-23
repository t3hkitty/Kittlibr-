#!/usr/bin/env bash
# docker-run.sh — One-command Out-of-the-Box Docker deploy script for Kittlibr
set -e

CONTAINER_NAME="kittlibr"
IMAGE_NAME="t3hkitty/kittlibr:latest"
PORT_WEB=8080
PORT_ANYMD=3050
PORT_RCLONE=5572
PORT_LIBRMANAGE=5820

echo "🐾 Deploying Kittlibr Docker container..."

# Check if docker daemon is running
if ! docker info >/dev/null 2>&1; then
    echo "❌ Error: Docker daemon is not running or current user lacks permissions."
    exit 1
fi

# Stop existing container if present
if docker ps -a --format '{{.Names}}' | grep -Eq "^${CONTAINER_NAME}\$"; then
    echo "  Stopping and removing previous container '${CONTAINER_NAME}'..."
    docker stop "${CONTAINER_NAME}" >/dev/null 2>&1 || true
    docker rm "${CONTAINER_NAME}" >/dev/null 2>&1 || true
fi

# Build image if not present or requested
if [[ "$1" == "--build" ]] || ! docker image inspect "${IMAGE_NAME}" >/dev/null 2>&1; then
    echo "🔨 Building Docker image '${IMAGE_NAME}'..."
    docker build -t "${IMAGE_NAME}" .
fi

# Create persistent volume for eBooks and AnyMD metadata
docker volume create kittlibr_vault_data >/dev/null

echo "🚀 Starting ${CONTAINER_NAME}..."
docker run -d \
    --name "${CONTAINER_NAME}" \
    --restart unless-stopped \
    -p "${PORT_WEB}:8080" \
    -p "${PORT_ANYMD}:3050" \
    -p "${PORT_RCLONE}:5572" \
    -p "${PORT_LIBRMANAGE}:5820" \
    -v kittlibr_vault_data:/app/staging_cache \
    -e SERVE_WEB=1 \
    -e WEB_HOST=0.0.0.0 \
    -e WEB_PORT=8080 \
    -e ANYMD_HOST=0.0.0.0 \
    -e ANYMD_PORT=3050 \
    -e LIBRMANAGE_HOST=0.0.0.0 \
    -e LIBRMANAGE_PORT=5820 \
    -e RCLONE_BIND_ADDR=0.0.0.0:5572 \
    -e RCLONE_RCD_URL=http://127.0.0.1:5572 \
    -e RCLONE_RC_USER=kittlibr \
    -e RCLONE_RC_PASS=wordsaremagic \
    "${IMAGE_NAME}"

echo ""
echo "==================================================================="
echo "✅ Kittlibr successfully deployed in Docker!"
echo "==================================================================="
echo "🌐 Web Reader UI  : http://localhost:${PORT_WEB} (or http://<LAN_IP>:${PORT_WEB})"
echo "⚡ Rclone JSON-RPC : http://localhost:${PORT_RCLONE}"
echo "📄 AnyMD Engine    : http://localhost:${PORT_ANYMD}"
echo "📚 librmanage      : http://localhost:${PORT_LIBRMANAGE}"
echo "💾 Vault Volume    : kittlibr_vault_data"
echo "==================================================================="
echo "To view logs: docker logs -f ${CONTAINER_NAME}"
echo "To stop:      docker stop ${CONTAINER_NAME}"
