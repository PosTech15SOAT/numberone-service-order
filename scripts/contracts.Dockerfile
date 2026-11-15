FROM python:3.13-slim
COPY scripts/requirements-contracts.lock /tmp/requirements-contracts.lock
RUN pip install --no-cache-dir -r /tmp/requirements-contracts.lock
WORKDIR /workspace
ENV PYTHONDONTWRITEBYTECODE=1
CMD ["python", "scripts/validate_contracts.py"]
