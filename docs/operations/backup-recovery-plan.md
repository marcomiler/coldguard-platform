# Plan de copia de seguridad y recuperación (entorno local)

Alcance: los datos del stack local de Docker Compose. No existe un plan para Azure: ningún recurso
está aprovisionado y los objetivos de recuperación (RPO/RTO) **no están definidos** por el negocio;
no se inventan aquí.

## Qué se guarda y dónde

| Dato | Dónde | Se conserva entre arranques |
|---|---|---|
| Datos de negocio (esquemas `asset`, `telemetry`, `incident`, `identity`, `auditlog`, `notification`) | Volumen `postgres-data` | Sí, salvo `down.sh --purge` |
| Mensajes en cola y DLQ | Volumen `rabbitmq-data` | Sí, salvo `down.sh --purge` |
| Dashboards y datasources de Grafana | Código en `observability/grafana/` | Siempre (están en el repositorio) |
| Métricas, logs y trazas | Prometheus, `loki-data`, `tempo-data` | Son diagnóstico: se pueden perder |
| Certificados y claves de desarrollo, `.env` | `deploy/local/` (no versionado) | Se regeneran (`generate-dev-certs.sh`) |

## Copia de seguridad

`deploy/scripts/backup-db.sh [directorio]` ejecuta `pg_dump` por esquema (solo lectura) y deja un
archivo `.sql` por esquema en `deploy/local/backups/<fecha>/` (no versionado). Debe ejecutarse con el
stack arriba, y antes de cualquier `down.sh --purge`.

## Recuperación

1. Levantar solo PostgreSQL: `docker compose -f deploy/local/docker-compose.yml up -d postgres`.
2. Restaurar **los seis esquemas** (`docker exec -i coldguard-postgres psql -U coldguard -d coldguard < deploy/local/backups/<fecha>/<esquema>.sql`
   para cada uno): los volcados incluyen las tablas de historial de Flyway (las de `identity` y
   `auditlog` viven en el esquema `incident`), de modo que los servicios no vuelven a migrar.
3. Levantar el resto: `deploy/scripts/up.sh`.

Los eventos que estuvieran en el Outbox sin publicar quedan en su tabla y se publican al arrancar;
los mensajes de RabbitMQ no se respaldan: tras una restauración, los consumidores son idempotentes y
se pueden volver a enviar los eventos que falten.

## Límites

- No hay copias programadas ni cifradas; es una herramienta de desarrollo.
- La restauración **no se ha probado en esta versión del documento**: antes de depender de ella,
  ejecutar el procedimiento completo una vez sobre un volumen de prueba.
