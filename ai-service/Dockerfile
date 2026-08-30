# =============================================================================
# ContextClip AI Service — Docker Build
# =============================================================================
# Single-stage build: Python 3.11 slim base, installs dependencies from
# requirements.txt, runs FastAPI via uvicorn.
#
# Runtime environment variables:
#   GEMINI_API_KEY    — REQUIRED. Google Gemini API key. No default.
#   GEMINI_MODEL      — Optional. Defaults to model in gemini_service.py.
#   AI_SERVICE_HOST   — Bind address (default: 0.0.0.0 via CMD)
#   AI_SERVICE_PORT   — Bind port   (default: 8000 via CMD)
# =============================================================================

# python:3.11-slim-bookworm — minimal Debian-based image; no unnecessary tools.
# Pinned minor version to match local dev environment (requirements use Python 3.11).
FROM python:3.11-slim-bookworm

# Metadata
LABEL org.opencontainers.image.title="contextclip-ai-service"
LABEL org.opencontainers.image.description="ContextClip FastAPI AI Service (Google Gemini)"

# Prevent Python from writing .pyc files and buffering stdout/stderr.
# Both are important in containerized / log-streaming environments.
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

# Create a non-root system user
RUN groupadd --system contextclip \
 && useradd  --system --gid contextclip --no-create-home contextclip

WORKDIR /app

# Install dependencies first for better layer caching.
# Upgrade pip to avoid known-vulnerability warnings.
COPY requirements.txt .
RUN pip install --upgrade pip --quiet \
 && pip install --no-cache-dir -r requirements.txt --quiet

# Copy application source
COPY app/ ./app/

# Set ownership
RUN chown -R contextclip:contextclip /app

USER contextclip

EXPOSE 8000

# Run with uvicorn in production mode (1 worker per container — scale horizontally).
# --no-access-log keeps container logs clean; remove to restore request logging.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
