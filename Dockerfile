# Multi-stage lightweight OOB Kittlibr Dockerfile
FROM python:3.12-slim-bookworm

# Avoid prompts from debian frontend
ENV DEBIAN_FRONTEND=noninteractive
ENV PYTHONUNBUFFERED=1

# Install runtime utilities, curl for healthchecks, and rclone binary
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    ca-certificates \
    unzip \
    && curl https://rclone.org/install.sh | bash \
    && apt-get purge -y unzip \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/*

# Create non-root container user
RUN useradd -m -u 1000 -s /bin/bash kittlibr
WORKDIR /app

# Install Python requirements
RUN pip install --no-cache-dir \
    fastapi \
    uvicorn \
    requests \
    pyyaml \
    xxhash \
    pydantic \
    httpx

# Copy Kittlibr codebase
COPY --chown=kittlibr:kittlibr . /app

# Setup default directories with proper permissions
RUN mkdir -p /app/staging_cache /app/data && chown -R kittlibr:kittlibr /app

USER kittlibr

# Default Environment Configuration (bind all interfaces for Docker container networking)
ENV KITTLIBR_STAGING_DIR=/app/staging_cache
ENV ANYMD_HOST=0.0.0.0
ENV ANYMD_PORT=3050
ENV LIBRMANAGE_HOST=0.0.0.0
ENV LIBRMANAGE_PORT=5820
ENV RCLONE_BIND_ADDR=0.0.0.0:5572
ENV RCLONE_RCD_URL=http://127.0.0.1:5572
ENV SERVE_WEB=1
ENV WEB_HOST=0.0.0.0
ENV WEB_PORT=8080
ENV RCLONE_RC_USER=kittlibr
ENV RCLONE_RC_PASS=wordsaremagic

# Expose Web UI (8080), AnyMD daemon (3050), Rclone rcd (5572), librmanage engine (5820)
EXPOSE 8080 3050 5572 5820

# Container volume for persistent eBook vaults and cache
VOLUME ["/app/staging_cache"]

# Health check to ensure AnyMD daemon and Web UI are responsive
HEALTHCHECK --interval=20s --timeout=5s --start-period=5s --retries=3 \
  CMD curl -f http://127.0.0.1:3050/ || exit 1

ENTRYPOINT ["python3", "/app/start_kittlibr.py"]
