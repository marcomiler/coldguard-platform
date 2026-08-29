# `.claude/` — Claude Code configuration for ColdGuard

This directory configures how Claude Code (agents, skills, rules, permissions) behaves in this
repository. Everything here except `settings.local.json` is checked into Git and shared by the
whole team.

## Layout

- `agents/` — specialized subagents (`architecture-auditor`, `backlog-refiner`, `code-reviewer`).
- `skills/` — reusable playbooks invoked with `/skill-name` or by description match
  (`new-adr`, `sprint-refinement`, `consistency-check`, `coldguard-academic-trace`, plus the
  `coldguard-*` domain skills).
- `commands/` — slash commands (`/review-docs`, `/verify-backend`, `/plan-vertical-slice`).
- `rules/` — always-loaded project conventions (architecture, testing, documentation, security,
  Java/Spring, git workflow, infra/Terraform).

## command vs. skill vs. agent — decision criterion

Use this to decide where new capability goes, and to avoid recreating the duplication removed in
the cleanup below:

- **command** — a short, explicit invocation shortcut with little logic of its own: a fixed
  checklist or a pointer to where to look, no domain knowledge that needs to be "loaded" beyond
  what's in the prompt itself. If it's more than ~15-20 lines of instructions, or it encodes
  reusable domain knowledge (not just "check these files"), it has outgrown being a command.
- **skill** — deep domain knowledge loaded on demand: a documented process (e.g.
  `docs/planning/refinement-process.md`), a structural convention with rules and edge cases (e.g.
  the 8-section ADR format), or a checklist that needs context/reasoning, not just a file list.
  If two commands cover overlapping ground, that overlap is a sign the real capability belongs in
  one skill, invoked from wherever it's needed.
- **agent** — a role with a single responsibility and its own isolated context/tool set: it runs
  with a restricted tool list (e.g. `architecture-auditor` is read-only), so its purpose is
  enforcing a boundary (never edits X, only ever reports), not just organizing knowledge.

## What to configure in `settings.local.json` (per developer)

`settings.local.json` layers on top of `settings.json` and is where your personal, machine-specific
preferences go — it must never contain anything the team needs to share (that belongs in
`settings.json` or a `rules/` file instead).

Typical things to add there:

- **Extra `permissions.allow` entries** for commands you run often and are comfortable
  auto-approving on your machine (e.g. a personal `ls`/`find` alias, a local linter you always run
  read-only). Keep these narrow — prefer `Bash(mvn -pl asset-service test:*)` over a broad
  `Bash(mvn:*)`.
- **Local model/tool overrides** you want only for your own sessions, not the team's default.
- **Anything environment-specific to your machine** (paths, local ports) that would be noise or
  wrong for a teammate.

Do **not** put in `settings.local.json`:

- Secrets, tokens, or credentials of any kind — those belong in your local `.env`
  (not versioned; see `.claude/rules/security.md`), never in a Claude Code settings file.
- Any relaxation of the `deny` rules in `settings.json` (e.g. re-allowing `terraform apply` or
  `az`) — if you think a deny rule is wrong, raise it with the team and change `settings.json`,
  don't route around it locally.
- Anything the rest of the team needs — if it's shared knowledge, it goes in `rules/` or
  `settings.json`, not here.

`settings.local.json` is intentionally left untouched by this configuration pass; set it up
yourself following the guidance above.

## Structural cleanup log

Kept short on purpose — this is a log of removals/merges, not a design doc. Update it whenever a
command/skill/agent is removed or redefined, so "the cleanup below" in the section above stays
resolvable.

- Removed `commands/create-adr.md` (duplicated `skills/new-adr`), `commands/sprint-status.md`
  (absorbed into the `backlog-refiner` agent), `commands/plan-sprint.md` (merged into
  `skills/sprint-refinement`).
- Removed `skills/coldguard-documentation/SKILL.md` (near-total overlap with
  `rules/documentation.md` and `skills/consistency-check`, no independent capability) and
  `commands/prepare-apf1.md` (its APF1-only check now generalizes into the skill below). Replaced
  by **`skills/coldguard-academic-trace`** — tracks deliverable evidence for APF1/APF2/APF3/
  proyecto final by deriving each phase's sprints from `docs/planning/roadmap.md`; for APF1
  specifically it just reads `docs/academic/apf1-mapping.md` and `report-artifacts-index.md`
  (still the sole source of truth there, not duplicated); it does not create
  `apf2-mapping.md`/etc. before that phase actually starts.
