# Lista de verificación de despliegue a Azure

Estado: procedimiento definido, **aún no ejecutado**. Cada casilla se marca con evidencia (captura o
salida del flujo) cuando se ejecute.

## Antes de la ventana

- [ ] Cambio registrado (tipo Infraestructura) con ventana y responsable.
- [ ] Los cuatro proveedores de recursos registrados en la suscripción.
- [ ] Capa persistente (estado, identidad, presupuesto) creada y presupuesto con alertas activo.
- [ ] *Environment* `azure-dev` con revisor y variables del repositorio creadas.
- [ ] Versión publicada: las seis imágenes con la etiqueta a desplegar, públicas y escaneadas sin
      críticas con parche.
- [ ] Estimación de costo revisada en la calculadora oficial; saldo de la suscripción anotado.

## Durante la ventana

- [ ] Disparar el flujo con acción `apply`, la etiqueta y la fecha límite (`ttl`).
- [ ] Revisar el plan: solo recursos esperados, región permitida, etiquetas `project`, `env`, `owner`, `ttl`.
- [ ] Aprobar; medir el tiempo de creación.
- [ ] Gateway responde; datos sembrados; smoke de extremo a extremo correcto.
- [ ] Capturar evidencia: resumen del flujo, recursos creados, URL del Gateway, métricas y trazas.

## Al terminar

- [ ] Disparar el flujo con acción `destroy` y aprobar.
- [ ] Verificar que el grupo del entorno quedó vacío y que el gasto acumulado es el esperado.
- [ ] Registrar costo real y tiempos en el cambio.

## Rollback

1. Volver a ejecutar el flujo con acción `apply` y la etiqueta anterior.
2. Repetir el smoke. Si el entorno no se recupera, `destroy` y nueva ventana.
