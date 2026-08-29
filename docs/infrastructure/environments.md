# Entornos

Perfiles previstos y las variables que cada uno espera, sin valores reales ni secretos. El único
entorno con despliegue real hoy es **local**; **azure-planned** documenta lo ya decidido en
`docs/architecture/tech-stack.md` y `docs/architecture/deployment-view.md` como planificado, no
implementado (`.claude/rules/infra.md` exige aprobación humana explícita antes de crear cualquier
recurso Azure).

## `local` — desarrollo local (único entorno con despliegue real)

- **Ejecución**: `deploy/local/docker-compose.yml` vía `docker compose up`
  (`docs/operations/runbooks.md`, `docs/infrastructure/docker-strategy.md`).
- **Persistencia**: PostgreSQL en contenedor, una instancia con ownership lógico de esquema por
  servicio/módulo (`asset`, `telemetry`, `incident`, `identity`, `auditlog`, ADR-006).
- **Mensajería**: RabbitMQ en contenedor (ADR-004).
- **Notificaciones**: Mailpit — solo pruebas locales, no es un proveedor productivo.
- **Secretos**: `.env` no versionado (excluido por `.gitignore`) + `.env.example` sin valores
  reales (DEC-010).
- **Variables esperadas por servicio (nombres, sin valores)**:
  - Todos los servicios backend: `SPRING_PROFILES_ACTIVE=local`, credenciales de conexión a
    PostgreSQL (host, puerto, base de datos, usuario, contraseña), credenciales de conexión a
    RabbitMQ (host, puerto, usuario, contraseña), endpoint de exportación OpenTelemetry.
  - `notification-service`: host/puerto SMTP de Mailpit.
  - `gateway`: puertos/URLs internas de Asset Service e Incident Service (gRPC).
  - Exactas por servicio: pendientes de `.env.example`, no fijadas en este documento (evita
    duplicar lo que vivirá versionado en el propio archivo de ejemplo).

## `test` — pruebas automatizadas (unitarias, integración, contrato)

- **Alcance**: perfil usado por la suite de pruebas descrita en
  `docs/quality/test-strategy.md` y `.claude/rules/testing.md`, no un entorno desplegado de forma
  persistente.
- **Pruebas de dominio** (RN-001 a RN-014): sin infraestructura — no requieren este perfil, corren
  en memoria.
- **Pruebas de integración/contrato**: infraestructura efímera por ejecución (candidato:
  Testcontainers para PostgreSQL y RabbitMQ), sin estado compartido entre ejecuciones y sin
  credenciales reales — valores de prueba generados o fijos no sensibles.
- **CI**: se ejecuta en GitHub Actions (`CLAUDE.md`, Technology); la configuración concreta del
  workflow queda fuera de este documento.

## `azure-planned` — despliegue planificado (ningún recurso creado)

Documentado con detalle en `docs/architecture/deployment-view.md` (sección B) y
`docs/architecture/tech-stack.md`; este documento solo resume las variables/config esperadas por
perfil, sin duplicar el detalle de servicios:

- **Cómputo**: Azure Container Apps (DEC-002) — planificado.
- **Persistencia**: Azure Database for PostgreSQL Flexible Server (DEC-002) — planificado.
- **Mensajería**: RabbitMQ en contenedor dentro de Azure Container Apps (DEC-009) — planificado.
- **Secretos**: Azure Key Vault + Managed Identity (DEC-010) — planificado; sin variables de
  entorno con secretos embebidos, los servicios leerían credenciales vía Managed Identity en
  tiempo de ejecución.
- **Notificaciones**: Azure Communication Services Email (DEC-007), previsto para Sprint 6 —
  planificado.
- **Observabilidad**: Azure Monitor / Application Insights — planificado.
- **Estado**: **ningún recurso Azure está aprovisionado**; no se ejecuta `terraform apply` sin
  aprobación explícita (`.claude/rules/infra.md`). No se documentan variables de entorno
  específicas de este perfil porque no hay implementación que las consuma todavía.

## Pendiente (no bloquea Sprint 1)

- `.env.example` con el listado exacto de claves por servicio: se crea junto con
  `deploy/local/docker-compose.yml`, no en este documento.
- Configuración concreta del workflow de CI (GitHub Actions) para el perfil `test`: no definida
  aquí.
