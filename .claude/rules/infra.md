- The MVP must run locally with Docker Compose (`deploy/local/docker-compose.yml`); do not require any cloud service to run it.
- Do not create Azure resources without explicit human approval.
- Do not execute `terraform apply` or `terraform destroy` without explicit approval.
- Terraform stays modular and prepared for Azure, but unapplied, during the MVP (RNF-007).
- Do not commit Terraform state files, secrets, or generated PIDs.

Nota: este archivo consolida el alcance que antes se dejó como stub en `rules/terraform.md`. Ese archivo puede eliminarse en una limpieza futura para evitar duplicidad.
