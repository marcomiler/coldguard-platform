# Integración del frontend con el backend

Guía para quien construye `coldguard-frontend`. El backend se desarrolla en paralelo; el contrato
entre ambos es **`contracts/rest/openapi.yaml`**. Esta guía explica cómo levantar el backend, cómo
autenticarse, qué esperar de los errores y qué pantallas ya tienen backend real.

## 1. Fuente de verdad

- `contracts/rest/openapi.yaml`: cada operación lleva `x-status` (`implemented` o `planned`) y
  `x-roles`. Lo implementado está garantizado por un test del backend (`OpenApiContractTest`): si el
  contrato y el código se separan, el build falla. Lo `planned` puede cambiar; se marca como
  implementado cuando existe.
- El Gateway (`http://localhost:8080/api/v1`) es el **único** punto de entrada. Ningún otro servicio
  publica puertos al host y el frontend nunca debe saltárselo.

## 2. Levantar el backend

Requisitos: Docker, `curl`, `jq`, `openssl`. Una sola vez, desde la raíz del repo del backend:

```bash
deploy/scripts/generate-dev-certs.sh          # CA local, certificados mTLS y claves JWT
cp deploy/local/.env.example deploy/local/.env
```

Edita `deploy/local/.env` (no se versiona): rellena `POSTGRES_*`, `RABBITMQ_DEFAULT_*`,
`GRAFANA_ADMIN_*` y `DEMO_USERS_PASSWORD` (mínimo 8 caracteres; es la contraseña de los usuarios
demo). Para el frontend añade el origen de su servidor de desarrollo:

```
COLDGUARD_CORS_ALLOWED_ORIGINS=http://localhost:5173      # el puerto de tu frontend
ASSET_CALIBRATION_EXPIRY_CRON=0 * * * * *                 # opcional, para la demo (cada minuto)
```

Después:

```bash
cd deploy/local && docker compose up -d --build    # espera a que todo esté "healthy"
cd ../.. && deploy/scripts/seed-demo.sh            # datos de demostración (idempotente)
```

`seed-demo.sh` crea una organización, una sede, cuatro activos de criticidad distinta y ocho
sensores (uno en mantenimiento, uno inactivo y uno cuya calibración vence en unos 3 minutos). Es
seguro repetirlo; `--new-expiring` crea otro sensor de calibración corta para repetir esa demo. Los
ids reales quedan en `deploy/local/demo-seed.json` (no versionado).

Para reiniciar de cero: `docker compose down` y `docker volume rm local_postgres-data`.

## 3. Autenticación y roles

- `POST /auth/login` con `{username, password}` devuelve `{accessToken, tokenType, expiresIn}`.
  Todas las demás rutas llevan `Authorization: Bearer <accessToken>`.
- El token dura **1 hora (`expiresIn`, en segundos) y no hay refresh**: ante un `401`, volver al
  login. Guárdalo en memoria; no hay cookies ni sesión de servidor.
- Usuarios demo (misma contraseña, la de `DEMO_USERS_PASSWORD`):

| Usuario | Rol | Qué ve |
|---|---|---|
| `admin` | `PLATFORM_ADMIN` | Todo lo de administración: usuarios, organizaciones, activos, sensores, historial |
| `supervisor` | `OPERATIONS_SUPERVISOR` | Lectura de organizaciones, activos, sensores y perfiles; incidentes y métricas (cuando existan) |
| `operator` | `OPERATOR` | Solo lectura de incidentes (cuando existan) |
| `technician` | `MAINTENANCE_TECHNICIAN` | Lectura de incidentes y cierre técnico |
| `auditor` | `AUDITOR` | Solo la bitácora de auditoría (cuando exista) |

- Un rol sin permiso recibe `403`; sin token, `401`. La interfaz debe ocultar lo que el rol no
  puede hacer, pero **la autoridad es el backend**.
- Tras 5 intentos fallidos la cuenta se bloquea 15 minutos (incluso con la contraseña correcta).

## 4. Convenciones que debe respetar el cliente

- **Errores**: siempre Problem Details con `code` estable y `correlationId`. Decide por `code`, nunca
  por `detail`. Los errores de validación traen `errors: [{field, message}]` (nunca el valor
  enviado). Códigos y significado: ver las respuestas de `components` del contrato.
- **Conflicto de versión**: los activos y sensores tienen `version`; al modificarlos se envía como
  `expectedVersion`. Un `409 CONCURRENT_MODIFICATION` significa "alguien más lo cambió": recargar y
  reintentar. El perfil operativo usa `version` (ausente o 0 al crear).
- **Listas**: `?page=&size=` devuelve `{items, page}`; las ordenadas por tiempo usan
  `?cursor=&size=` y devuelven `{items, nextCursor, hasMore}`. `size` máximo 100.
- **Correlación**: envía `X-Correlation-Id` (opcional) y muéstralo en los errores; es lo que sirve
  para rastrear una petición en los logs.
- **Enums** sin prefijo (`HIGH`, `IN_MAINTENANCE`); instantes ISO-8601 UTC; duraciones en segundos.
- **Reglas de negocio**: el frontend no las repite. Las transiciones de estado del sensor, por
  ejemplo, las decide el backend y se explican con su `code` (`CALIBRATION_EVIDENCE_REQUIRED`,
  `SENSOR_TRANSITION_NOT_ALLOWED`…); muestra el `detail` o un mensaje propio según el `code`.

## 5. Qué pantallas tienen backend real hoy

| Pantalla | Endpoints | Estado |
|---|---|---|
| Login y sesión | `POST /auth/login` | Real |
| Administración de usuarios y roles | `/users`, `/users/{id}/roles`, `/users/{id}/enabled` | Real |
| Organizaciones y sedes | `/organizations`, `/organizations/{id}/sites` | Real |
| Activos (alta, edición, criticidad) | `/assets`, `/assets/{id}` | Real |
| Sensores: alta, datos técnicos, perfil operativo | `/sensors`, `/sensors/{id}`, `/sensors/{id}/profile` | Real |
| Ciclo de vida del sensor: estado, calibración, reasignación, retiro | `/sensors/{id}/status`, `/calibrations`, `/reassignment`, `/retirement` | Real |
| Historial del sensor | `/sensors/{id}/history` | Real |
| Inyectar lecturas de prueba | `POST /telemetry/test-readings` | Planificado (SPEC-006) |
| Lecturas y conectividad | `/sensors/{id}/readings`, `/sensors/connectivity` | Planificado (SPEC-006) |
| Tablero de incidentes, detalle, reconocer, escalar, cerrar | `/incidents…` | Planificado (SPEC-007) |
| Métricas operativas | `/metrics/incidents` | Planificado (SPEC-007) |
| Bitácora de auditoría | `/audit-records` | Planificado (SPEC-007) |

Para lo planificado, construye la pantalla contra el contrato con un mock y conéctala cuando el
backend avise de que pasó a `implemented`. `POST /incidents` y `POST /incidents/{id}/close` existen
pero con una forma **legada** (enums con prefijo): no los integres, se reemplazan en SPEC-007.

## 6. Mocks y cliente generado

Sugerencias, no verificadas en el repositorio del backend:

```bash
npx @stoplight/prism-cli mock contracts/rest/openapi.yaml     # servidor de mocks del contrato
npx openapi-typescript contracts/rest/openapi.yaml -o src/api/schema.d.ts   # tipos TypeScript
```

Configura la URL base por entorno (`http://localhost:8080/api/v1` contra el backend real, la del mock
para lo planificado) y alterna por pantalla, no globalmente.

## 7. Guion de demostración con lo que existe hoy

1. Login como `admin`; el tablero de activos muestra cuatro criticidades distintas.
2. Abrir un sensor: perfil operativo (rango, bandas de magnitud, persistencia) y su historial.
3. Intentar volver un sensor en mantenimiento a `ACTIVE`: se rechaza (`CALIBRATION_EVIDENCE_REQUIRED`);
   registrar una calibración (el estado **no** cambia); ahora sí se acepta.
4. Reasignar un sensor en mantenimiento a otro activo: el historial conserva la asociación previa.
5. Mostrar el sensor `SN-DEMO-EXPIRA`: a los pocos minutos pasa **solo** a `IN_MAINTENANCE` y el
   historial lo atribuye al actor de sistema `calibration-expiry-job`.
6. Entrar como `supervisor`: puede leer pero no modificar; como `auditor`: no ve administración.
7. Administración de usuarios: asignar y revocar un rol exige motivo; el último administrador no se
   puede degradar.

## 8. Cambios previstos del contrato

- SPEC-006: aparecen `test-readings`, lecturas y conectividad (hoy `planned`).
- SPEC-007: aparecen los endpoints de incidentes; `close` pasa a devolver el incidente completo y
  `POST /incidents` deja de ser la vía de creación. Entonces los enums de incidentes usan el mismo
  vocabulario que el resto.
- Cada entrega del backend actualiza `openapi.yaml` y lo anuncia; un cambio incompatible se avisa
  antes de fusionarse.
