# ADR-013 Alojamiento del frontend en Azure como sitio estático sobre Storage

**Estado:** Propuesto el 2026-11-06. No hay recursos del frontend creados en Azure.

## Contexto
ADR-002 separó el frontend (React, repositorio aparte) del backend, y ADR-012 desplegó el backend en
Azure con Terraform y dejó el frontend "para después del backend", anticipando dos caminos: Static
Web Apps en el plan gratuito o un sitio estático en Storage, según lo permitiera la política de
regiones de la suscripción.

Hechos que condicionan la decisión:

- El frontend es una aplicación de una sola página compilada a archivos estáticos (unos 620 KB) con
  un enrutador que usa la URL del navegador, de modo que cualquier ruta desconocida debe devolver la
  página principal.
- La dirección del Gateway se fija al compilar (variable de entorno de la herramienta de
  construcción). Cada vez que el entorno efímero se recrea, el Gateway recibe un dominio nuevo.
- El navegador solo puede llamar al Gateway si este incluye el origen del frontend en su política
  CORS (ADR-008). Hoy esa lista está vacía a propósito.
- La suscripción Azure for Students solo permite crear recursos en `canadacentral`,
  `northcentralus`, `chilecentral`, `mexicocentral` y `westus` (ADR-012). Las regiones en las que
  Static Web Apps registra sus recursos (por ejemplo `centralus`, `eastus2`, `westeurope`) no están
  en esa lista; que la política las bloquee es un supuesto académico, no comprobado.
- El presupuesto total del proyecto es de 10 USD, con tope de 25 USD (ADR-012).
- El repositorio del frontend es público, por lo que otro flujo puede leerlo sin credenciales.

## Problema
¿Cómo publicar el frontend en Azure con costo prácticamente nulo, dentro de las regiones permitidas,
de forma reproducible, sin romper la regla de que el navegador solo habla con el Gateway, y de modo
que la dirección del Gateway y el origen permitido por CORS se mantengan coherentes sin pasos manuales
frágiles?

## Opciones consideradas
| Opción | Ventajas | Desventajas |
|---|---|---|
| Static Web Apps, plan gratuito | Sin costo, reglas de enrutamiento y cabeceras incluidas | Sus regiones no coinciden con las permitidas por la política; un fallo aparecería recién al crear el recurso |
| Sitio estático en una cuenta de Storage (elegida) | Se crea en la misma región que el backend, HTTPS incluido, costo de centavos, Terraform lo gestiona con el mismo proveedor | Sin cabeceras de seguridad propias ni CDN; las rutas desconocidas responden con el código 404 aunque entreguen la página principal |
| nginx en Azure Container Apps | Mismo patrón que el backend, control de cabeceras | Costo continuo mientras está encendido, una imagen y un flujo de publicación más, la dirección del Gateway debería inyectarse al arrancar |
| Front Door o CDN con dominio propio | Rendimiento y dominio propio | Costo y complejidad fuera del presupuesto y del alcance |

## Decisión
1. El frontend se aloja como **sitio estático en una cuenta de Storage** del mismo grupo de recursos
   y región que el backend, creada por un módulo de Terraform propio e incluida en el entorno `dev`.
   Se activa con una variable (por defecto, activa) y se destruye junto con el entorno.
2. La **política CORS del Gateway** se construye en Terraform a partir de la dirección del sitio: el
   mismo `apply` que crea el sitio actualiza el Gateway, sin pasos manuales ni orden frágil.
3. La **publicación** la hace un flujo propio de este repositorio, bajo el mismo *environment*
   protegido y la misma identidad federada que el resto del despliegue: descubre la dirección del
   Gateway en Azure, compila el frontend con esa dirección, sustituye el contenido del sitio y
   verifica tres cosas: que la página responde, que una ruta profunda devuelve la aplicación y que el
   Gateway acepta el origen del sitio.
4. La clave de la cuenta de Storage no se exporta ni se guarda: el flujo la lee de Azure en el momento
   de publicar y la enmascara en el registro. La cuenta no admite acceso público a contenedores
   individuales; solo el punto de entrada del sitio web es público.
5. Quedan fuera de esta decisión el dominio propio, la CDN y las cabeceras de seguridad avanzadas.

## Consecuencias
- El flujo completo tiene dos pasos independientes y repetibles: crear o actualizar el entorno, y
  publicar el frontend. Publicar de nuevo no toca la infraestructura.
- Cualquier cambio en el frontend se publica sin recrear el entorno; cualquier recreación del entorno
  exige publicar de nuevo, porque cambia la dirección del Gateway.
- El costo adicional es de centavos por día (estimación propia, sin verificar en la calculadora
  oficial), muy por debajo del resto del entorno.
- La política de contenido y las cabeceras de seguridad del sitio no están bajo nuestro control con
  este servicio; se documenta como limitación del prototipo.
- Las rutas profundas devuelven la página principal con estado 404; el navegador funciona, pero una
  herramienta que verifique códigos de estado lo vería. Se acepta para el alcance académico.

## Riesgos
| Riesgo | Mitigación |
|---|---|
| El punto de entrada del sitio no es público con la configuración de la cuenta | La primera publicación lo verifica; si falla, se ajusta la cuenta y se repite |
| La lista CORS del Gateway no incluye el origen (apply anterior a la creación del sitio) | La verificación final falla con un mensaje explícito; se repite el `apply` |
| La clave de la cuenta queda expuesta en un registro | Se lee en el momento, se enmascara y no se escribe en archivos ni salidas |
| Contenido obsoleto en el sitio tras varias publicaciones | Cada publicación vacía el contenedor antes de subir |

## Related ADRs
ADR-002 (frontend en repositorio separado), ADR-008 (el Gateway como único punto de entrada REST y su
política CORS), ADR-012 (infraestructura Azure de costo mínimo, regiones permitidas y presupuesto).

## Evolución futura a Azure
Si la política de regiones lo permite o se cambia de suscripción, migrar a Static Web Apps (cabeceras
de seguridad y enrutamiento declarativos) o a Front Door con dominio propio y certificados
administrados. El flujo de publicación se conserva; cambia solo el destino.
