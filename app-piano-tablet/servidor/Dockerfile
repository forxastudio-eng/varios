# Servidor en casa de Piano Cascada: Audiveris (OMR) + conversión a notas.
#   docker compose up --build          (desde esta carpeta)
FROM ubuntu:24.04
ENV DEBIAN_FRONTEND=noninteractive PORT=8765
RUN apt-get update && apt-get install -y --no-install-recommends \
        python3 python3-venv python3-pip curl ca-certificates fontconfig libfreetype6 \
    && rm -rf /var/lib/apt/lists/*

# Audiveris: se descarga el paquete .deb de la última versión publicada para esta arquitectura.
ARG AUDIVERIS_DEB_URL=""
RUN set -eux; \
    arch="$(dpkg --print-architecture)"; \
    url="$AUDIVERIS_DEB_URL"; \
    if [ -z "$url" ]; then \
      url="$(curl -fsSL https://api.github.com/repos/Audiveris/audiveris/releases/latest | python3 -c "import sys,json; a=json.load(sys.stdin)['assets']; arch='$arch'; keys={'amd64':('amd64','x86_64'),'arm64':('arm64','aarch64')}[arch]; c=[x['browser_download_url'] for x in a if x['name'].endswith('.deb') and any(k in x['name'] for k in keys)]; print(c[0] if c else '')")"; \
    fi; \
    test -n "$url"; \
    curl -fsSL "$url" -o /tmp/audiveris.deb; \
    apt-get update; apt-get install -y --no-install-recommends /tmp/audiveris.deb; \
    rm -rf /tmp/audiveris.deb /var/lib/apt/lists/*

WORKDIR /srv
COPY requirements.txt .
RUN python3 -m venv /opt/venv && /opt/venv/bin/pip install --no-cache-dir -r requirements.txt
COPY *.py ./
EXPOSE 8765
CMD ["/opt/venv/bin/uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8765"]
