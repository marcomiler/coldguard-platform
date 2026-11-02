## Qué cambia y por qué

<!-- Resumen del cambio. Enlaza el ticket de cambio (CHG-NNN) si es Normal, Emergencia o Infraestructura. -->

## Tipo de cambio

- [ ] Estándar
- [ ] Normal
- [ ] Emergencia
- [ ] Infraestructura

## Lista de verificación

- [ ] Pruebas unitarias y de integración pasan
- [ ] Formato (`mvn spotless:check`) y compilación con Java 25 correctos
- [ ] Sin secretos, credenciales, estado de Terraform ni logs
- [ ] ADR o DEC registrado si el cambio es arquitectónico
- [ ] Documentación actualizada
- [ ] Contratos gRPC/eventos versionados si hubo cambio incompatible
- [ ] Plan de reversión descrito (obligatorio en Normal, Emergencia e Infraestructura)

## Plan de reversión

<!-- Cómo se deshace este cambio y cuánto tarda. -->
