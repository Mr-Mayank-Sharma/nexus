# =============================================================================
# Nexus AI Ops — Production Dockerfile
# Python order-routing ML service (Flask)
# =============================================================================

FROM python:3.11-slim

WORKDIR /app

# Install dependencies first for layer caching
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY . .

# Non-root user for security
RUN useradd -m -u 1000 nexus && chown -R nexus:nexus /app
USER nexus

EXPOSE 5000

CMD ["python", "api/api_server.py"]
