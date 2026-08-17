Create or update one ADR only, in `docs/architecture/adr/`, following the naming convention `ADR-0NN-short-slug.md`.

Use exactly these 8 sections, in this order:
- Contexto
- Problema
- Opciones consideradas
- Decisión
- Consecuencias
- Riesgos
- Related ADRs
- Evolución futura a Azure

Rules:
- Keep the existing ADR numbering; never renumber or overwrite an unrelated ADR.
- Cross-reference other ADRs by ID in "Related ADRs" whenever the decision depends on or is depended on by them.
- "Evolución futura a Azure" describes the future path without creating any cloud resource now.
- Do not edit code.
- Do not create cloud resources.
- Do not execute `terraform apply`.
- Show the file to create/modify and its justification before writing it.
