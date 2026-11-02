# SPEC-017 — Despliegue de la versión final y verificación (APF3 · "Despliegue versión final")

## Objetivo

Desplegar la versión v0.3 en Azure mediante el CD de SPEC-015 sobre la infraestructura de SPEC-016,
verificarla con el smoke end-to-end y dejar evidencia. Si la aprobación o la suscripción no existen,
entregar la **verificación equivalente en local** y declarar el límite.

## Trazabilidad

Sílabo, semana 13 ("Despliegue versión final"); APF3; `docs/planning/release-plan.md` (v0.3);
RNF-001, RNF-007; SPEC-011 (`smoke-e2e.sh`), SPEC-015, SPEC-016.

## Estado actual verificado

- Nada desplegado en Azure; sin `release` ni tag de versión (el historial no tiene tags `v*`
  verificados: comprobar con `git tag`).
- `smoke-e2e.sh` y `seed-demo.sh` funcionan contra el Gateway local; hay que parametrizar la URL base.

## Alcance

1. **Congelamiento y versionado**: elegir el commit candidato, etiquetar `v0.3.0` (previa
   confirmación del usuario; el asistente no crea tags ni hace push sin pedirlo), notas de versión en
   `docs/planning/release-plan.md` y `CHANGELOG` mínimo si se adopta.
2. **Parametrizar scripts**: `smoke-e2e.sh` y `seed-demo.sh` aceptan `GATEWAY_URL` (por defecto
   local) y credenciales por variable de entorno; sin valores embebidos; ejecutables contra HTTPS.
3. **Migraciones y datos**: Flyway corre al arrancar cada servicio contra el PostgreSQL de Azure;
   el sembrado demo usa el Gateway (no SQL directo) y solo datos ficticios.
4. **Despliegue**: `deploy-azure.yml` con `version=v0.3.0`, aprobación del environment, rollback
   automático si el smoke falla.
5. **Verificación**:
   - Salud: `readiness` de los 5 servicios y RabbitMQ en verde.
   - Smoke e2e (login → lectura fuera de rango → incidente → reconocer → escalar → cerrar →
     auditoría/métricas). El paso del correo depende de Mailpit/ACS: en Azure se verifica el
     registro de entrega en `notification_delivery`, salvo que se apruebe ACS.
   - Trazas y logs llegan a Application Insights/Log Analytics; captura de una traza completa.
   - Revisión de seguridad: el Gateway es el único ingress externo; servicios internos no
     accesibles desde Internet.
6. **Evidencia** en `docs/academic/apf3/evidencias/`: run del workflow, salida del smoke, capturas
   de recursos (sin identificadores sensibles), traza, `CHG-NNN` y registro de aprobación.
7. **Costos y cierre**: capturar el costo acumulado; decidir con el usuario si se deja en pie
   hasta la sustentación o se hace `destroy` (aprobación explícita).

## Rama sin Azure (plan B obligatorio de documentar)

Si no hay suscripción/aprobación: ejecutar el mismo smoke sobre el stack local con la imagen
etiquetada `v0.3.0` construida por `release-images.yml`, y presentar en el informe SPEC-015/016 como
"IaC y CD listos, validados con `validate`/`plan`; despliegue real pendiente". **No** se afirma que
esté desplegado.

## Criterios de aceptación

1. Existe un tag/versión `v0.3.0` creado con consentimiento y notas de versión (o queda
   documentado que se pospone).
2. `smoke-e2e.sh` pasa con código 0 contra el entorno objetivo (Azure o local según la rama).
3. La evidencia permite a un tercero reproducir la verificación, sin secretos visibles.
4. `deployment-view.md`, `environments.md` y `release-plan.md` reflejan el estado real.
5. El informe distingue claramente lo desplegado de lo planificado.

## Tareas

1. Parametrizar `smoke-e2e.sh`/`seed-demo.sh` (`GATEWAY_URL`) y probar en local.
2. Congelar, etiquetar y publicar imágenes (con consentimiento).
3. Pedir aprobación; humano ejecuta `apply` (SPEC-016) si no se hizo.
4. Ejecutar `deploy-azure.yml` y el smoke; capturar evidencia.
5. Actualizar documentación de estado y registrar `CHG-NNN`.
6. Decidir mantenimiento o `destroy`.

## Riesgos

- Es el camino crítico de APF3: cualquier fallo de ACA/mTLS/RabbitMQ se descubre aquí. Mitigación:
  intentar el despliegue en cuanto SPEC-016 esté aplicado, no al final de la semana.
- Costos por dejar recursos activos: *tags* TTL y `destroy` planificado.

## Fuera de alcance

Alta disponibilidad, backups y DR en Azure (Sprint 7); dominio propio y certificado personalizado.
