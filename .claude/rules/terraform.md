- Terraform is for modular, version-controlled Infrastructure as Code only — never a manual or
  console-driven change encoded after the fact.
- `terraform apply`, `terraform destroy`, and `terraform import` require explicit human approval;
  all three are also blocked outright by `.claude/settings.json`'s deny list as a technical
  backstop — that's an enforcement detail, not a substitute for getting approval.
- Never commit Terraform state files (`*.tfstate`, `*.tfstate.backup`), `.tfvars` files containing
  secrets, binary plan files (`*.tfplan`), or any credential.
- Modules and variables stay prepared for Azure (`infra/modules/`, `infra/environments/{dev,local}`
  — currently `.gitkeep` placeholders only) but **no resource is provisioned during the MVP**
  (RNF-007); "prepared" means the code exists and is reviewable, not that it has ever run against
  real Azure.
- Never hardcode a subscription ID, tenant ID, client secret, or an ephemeral resource name —
  prefer explicit variables and outputs.
- A change to infrastructure *architecture* (a new resource type, a new module boundary, a new
  cloud service selection) requires an ADR or a `decisions-log.md` entry first, matching the scope
  of the change — a naming or formatting change to existing modules does not.
- LocalStack or another cloud simulator may be adopted only once the team has actually decided to
  — not assumed from convenience. Currently deferred to Sprint 7
  (`docs/infrastructure/localstack-usage.md`); no evidence of use in the repository today.
- **Scope note**: this file governs Terraform/IaC. Local execution and the local/cloud boundary
  are governed by `.claude/rules/infra.md`.
