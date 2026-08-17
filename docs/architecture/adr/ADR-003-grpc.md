# ADR-003 gRPC para comunicación síncrona interna

## Contexto
CLAUDE.md fija la regla "REST at the edge, gRPC internally". RNF-006 exige contratos gRPC versionados. El C4 muestra al Gateway comunicándose con Asset Service e Incident Service, y a estos servicios comunicándose entre sí cuando se requiere una respuesta síncrona.

## Problema
¿Qué mecanismo de comunicación síncrona se usa entre servicios internos del backend, de forma que los contratos queden tipados y versionados (RNF-006) sin exponer detalles de implementación al frontend?

## Opciones consideradas
1. **REST interno entre servicios**: más simple de inspeccionar manualmente, pero sin tipado fuerte de contrato, mayor overhead de serialización JSON y peor ajuste para llamadas internas de alta frecuencia (por ejemplo, evaluación de telemetría).
2. **gRPC interno** (elegido): contratos tipados y versionados (RNF-006), coherente con la regla de CLAUDE.md, con soporte de streaming disponible para evolución futura.
3. **GraphQL interno**: añade complejidad de resolución de esquema sin beneficio claro para comunicación servicio-a-servicio en este MVP.

## Decisión
La comunicación síncrona entre servicios internos del backend (Gateway → Asset/Incident Service, y entre servicios de dominio cuando se requiere respuesta síncrona) se realiza mediante gRPC y Protocol Buffers, con contratos versionados en `contracts/`.

La comunicación asíncrona cross-service (por ejemplo, Incident Service → Notification Service) **no** usa gRPC: usa eventos sobre RabbitMQ (ver ADR-004, ADR-005). gRPC queda reservado exclusivamente para comunicación síncrona interna.

## Consecuencias
- Todos los contratos de servicio se definen como archivos `.proto` en `contracts/`, versionados junto con el monorepo (ADR-001).
- Los servicios generan sus stubs a partir de estos contratos como parte del build Maven.
- Cambios incompatibles de contrato requieren versionado explícito (por ejemplo, paquete `v2`) para no romper consumidores internos existentes.

## Riesgos
- Evolución de contratos gRPC sin disciplina de versionado puede romper consumidores internos de forma silenciosa.
- La depuración manual de gRPC es menos accesible que REST/JSON; se mitiga documentando el uso de herramientas como `grpcurl` en el runbook local.

## Related ADRs
- ADR-004 (RabbitMQ para comunicación asíncrona, fuera del alcance de gRPC).
- ADR-005 (Incident Service → Notification Service vía eventos, explícitamente no gRPC).
- ADR-008 (el Gateway traduce REST externo a gRPC interno).

## Evolución futura a Azure
Los contratos gRPC no cambian con la migración a Azure. Los servicios pueden desplegarse en Azure Container Apps o AKS manteniendo comunicación gRPC interna igual que en el entorno local, opcionalmente con un service mesh o Azure API Management gestionando mTLS entre servicios. No se crean recursos Azure como parte de esta decisión.
