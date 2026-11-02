# SPEC-014 — Pipeline de integración continua (APF3 · Cap. 9.1)

## Objetivo

Elevar `.github/workflows/ci.yml` de "build + test" a un pipeline que haga cumplir los quality gates
de `CLAUDE.md` en cada PR y deje evidencia: formato, compilación Java 25, pruebas unitarias y de
integración, análisis estático, escaneo de secretos, build de imágenes Docker y reporte de resultados.

## Trazabilidad

APF3 Cap. 9.1; `CLAUDE.md` (Quality gates); RNF-005; DEC-021 (Spotless);
`.claude/rules/testing.md`, `.claude/rules/security.md`; SPEC-013 (cambios), SPEC-015 (CD).

## Estado actual verificado

- `ci.yml`: disparadores `push` a `master` y `pull_request`; un solo job con PostgreSQL 16;
  `mvn -B verify` y una segunda corrida de integración de `incident-service`.
- `burndown.yml` existe y no se toca.
- Spotless `check` ya está ligado a `verify` en el `pom.xml` raíz.
- Faltan: concurrencia, permisos mínimos, caché explícita de reportes, análisis estático adicional,
  escaneo de secretos, build de imágenes, verificación de Compose, artefactos y badge.
- Las acciones usan etiquetas mutables (`@v4`).

## Decisiones requeridas

| ID | Decisión | Recomendación | Registro |
|---|---|---|---|
| D-20 | Herramientas de análisis y escaneo | Spotless (ya), análisis estático con la herramienta que compile en Java 25 (verificar SpotBugs/PMD/Error Prone al inicio; si ninguna soporta 25, documentar y mantener Spotless + `-Xlint:all -Werror` selectivo), escaneo de secretos con gitleaks, escaneo de dependencias con OWASP Dependency-Check o Dependabot alerts | DEC nuevo |
| D-21 | Fijar acciones por versión/SHA | Fijar a SHA completo con comentario de versión | DEC (mismo) |

## Diseño del pipeline

Jobs (en paralelo cuando no dependen entre sí):

1. **format-and-lint**: `mvn -B spotless:check`; `actionlint` sobre `.github/workflows`; `hadolint`
   sobre los 5 `Dockerfile`; `shellcheck` sobre `deploy/scripts/*.sh`; `terraform fmt -check` y
   `terraform validate` cuando existan módulos (SPEC-016, `-backend=false`, sin credenciales).
2. **secrets-scan**: gitleaks sobre el diff del PR e histórico en `master`.
3. **build-and-test**: JDK 25 Temurin, caché Maven; `mvn -B verify`; integración con servicios
   PostgreSQL y RabbitMQ (service containers) para las pruebas marcadas (hoy `-DexcludedGroups=`);
   publica reportes Surefire/Failsafe y cobertura (JaCoCo si se adopta; si no, se omite y se
   declara) como artefactos y en el *job summary*.
4. **static-analysis** y **dependency-scan** según D-20.
5. **docker-build**: construye las 5 imágenes con Buildx **sin publicar** en PRs; verifica que cada
   imagen arranca su `healthcheck`/`--version`; `docker compose -f deploy/local/docker-compose.yml
   config -q` valida la composición.
6. **compose-smoke** (solo `master` y `workflow_dispatch`): levanta el stack base en el runner y
   ejecuta `deploy/scripts/smoke-e2e.sh`; sube logs como artefacto en caso de fallo (sin secretos;
   los logs ya son estructurados y sin PII).

Propiedades transversales:

- `permissions: contents: read` por defecto; escalar por job solo si hace falta.
- `concurrency` con `cancel-in-progress` en PRs.
- Disparadores: `pull_request`, `push` a `master`, `workflow_dispatch`.
- Secretos: ninguno requerido para CI; las pruebas usan credenciales de desarrollo del
  `.env.example`.
- Protección de rama `master`: exigir los checks anteriores (configuración manual en GitHub,
  documentada; no se automatiza con credenciales).
- Badge de estado en `README.md`.

## Criterios de aceptación

1. Un PR de prueba con un error de formato falla en `format-and-lint`; con un secreto falso falla
   en `secrets-scan`; ambos casos se documentan con captura/enlace al run.
2. Un PR correcto pasa todos los jobs en verde y publica reportes.
3. `docker-build` construye las 5 imágenes y `compose-smoke` termina con código 0 en `master`.
4. Ningún workflow contiene secretos ni usa acciones sin fijar.
5. `docs/infrastructure/` documenta el pipeline (diagrama, jobs, gates y cómo reproducir cada uno
   en local) y `consistency-check` pasa.

## Tareas

1. Verificar compatibilidad Java 25 de herramientas de análisis y registrar D-20/D-21.
2. Reescribir `ci.yml` por jobs; añadir `shellcheck`/`hadolint`/`actionlint`.
3. Añadir `docker-build` y `compose-smoke`.
4. Documentar en `docs/infrastructure/ci-pipeline.md` (nuevo) y badge en `README.md`.
5. Capturar evidencia: un run verde y dos fallos intencionales (en rama descartable).
6. Configurar protección de rama (manual) y anotarlo en el documento.

## Riesgos

- Soporte de Java 25 en herramientas de análisis: se resuelve con verificación inicial.
- Minutos de GitHub Actions y tiempo de `compose-smoke`: limitar a `master` y manual.
- Pruebas de integración inestables en CI: reintento acotado solo en el job de integración y
  registrado, nunca silenciando fallos.

## Fuera de alcance

Publicación de imágenes y despliegue (SPEC-015); pruebas de carga en CI (SPEC-018).
