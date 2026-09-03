FROM apache/superset:6.1.0@sha256:59cd4af66006fe4cc98906eda42a771dbefdacb432f9ab083e02cdc6ff01f29d

USER root

COPY requirements-local.txt /app/requirements-local.txt
RUN . /app/.venv/bin/activate && \
    uv pip install --no-cache-dir -r /app/requirements-local.txt

COPY --chown=superset:superset pythonpath_dev/superset_config.py /app/pythonpath/superset_config.py
COPY --chown=superset:superset pythonpath_dev/keycloak_security_manager.py /app/pythonpath/keycloak_security_manager.py

ENV SUPERSET_CONFIG_PATH=/app/pythonpath/superset_config.py

USER superset
