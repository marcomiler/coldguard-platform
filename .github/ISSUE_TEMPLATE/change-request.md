---
name: Cambio
about: Ticket de cambio Normal, de Emergencia o de Infraestructura (docs/operations/change-management.md)
title: "[CHG-NNN] "
labels: cambio
---

## Cambio

- **Tipo**: Normal / Emergencia / Infraestructura
- **Entorno**: local / azure-planned
- **Descripción**:
- **Ventana propuesta**:

## Evaluación de riesgo

- **Impacto** (bajo / medio / alto / crítico):
- **Probabilidad de fallo** (baja / media / alta):
- **Riesgo resultante** (bajo / medio / alto):

- [ ] Modifica datos o esquemas (migración reversible, respaldo previo)
- [ ] Cambia un contrato gRPC o un evento (versionado explícito)
- [ ] Afecta autenticación, autorización o secretos (revisión de seguridad)
- [ ] Cambia la observabilidad (sin datos sensibles)
- [ ] Genera costo en la nube (estimación y etiquetas)

## Plan de reversión

<!-- Cómo se revierte, quién lo ejecuta y cuánto tarda. -->

## Aprobación

- **Aprobador**:
- **Decisión de arquitectura asociada** (ADR/DEC, si aplica):
- Para Infraestructura: plan de Terraform revisado y adjunto (sin valores sensibles)

## Cierre

- [ ] Solicitud de integración enlazada:
- [ ] Verificación posterior realizada:
- [ ] Resultado: integrado / revertido
