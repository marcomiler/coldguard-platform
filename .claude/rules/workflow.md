# Claude workflow

Read the applicable `.claude/rules/*.md` files before changing code, infrastructure, security,
tests, documentation, ADRs, or project configuration. Never claim a planned or target capability
is implemented without verifying the repository state.

## Rule

A persistent, automatic restriction that applies to a whole project area — architecture,
infrastructure, Terraform, security, testing, or documentation. Lives in `.claude/rules/*.md`.
If a file already owns a topic, don't duplicate its content elsewhere — reference it. When two
rules would overlap, identify the file that owns the subject and replace the duplicate with a
short reference to that source of authority. Don't put a one-off task into a permanent rule —
rules hold durable constraints, not temporary requests.

## Skill

A repeatable, multi-step workflow — especially one with a fixed sequence such as
audit → proposal → approval → implementation → validation. Lives in `.claude/skills/*/SKILL.md`.
A skill must state: its purpose, when to invoke it, the files/tools it may modify or execute,
required approvals, validation steps, and conditions that require stopping. Prefer a user-invoked
skill for destructive, expensive, externally visible, or approval-sensitive operations —
history rewrites, cloud changes, database migrations, releases, force-pushes. Don't create a skill
merely to store static project facts — stable project instructions belong in `CLAUDE.md` or the
applicable rule.

## Slash command

A short, explicit entry point for a frequently repeated action. Lives in `.claude/commands/*.md`.
Should delegate any non-trivial or reusable logic to a skill instead of duplicating long
instructions inline.

## Direct prompt

A one-off question, explanation, small inspection, or change that doesn't justify a reusable
workflow. No file backs it — it's just asked in the conversation.

## ADR

Records an **architectural** decision (a protocol, a service boundary, a cross-service pattern).
Lives in `docs/architecture/adr/`, one file per decision, next free `ADR-0NN` number — never
reused or renumbered.

## DEC

Records an **operational, scope, or planning** decision — not a new architectural pattern, but a
concrete choice within already-decided architecture (a pinned version, a resolved assignment gap,
a planning constraint). Lives in `docs/planning/decisions-log.md`, next free `DEC-0NN` number.

## Default pattern for sensitive tasks

For architecture, security, infrastructure, documentation structure, history rewriting, or any
other destructive or hard-to-reverse operation, default to:

`audit → proposal → explicit approval → implementation → validation`

Before modifying files, classify the task against this same sequence: inspect/audit, propose,
implement, validate, publish/deploy — and don't skip ahead of where the task actually is.

## Reporting

At the end of a task, report: files changed, files not changed, commands or tools executed,
validations performed, and unresolved risks or approvals still required.

## See also

`.claude/README.md` for the command-vs-skill-vs-agent placement criterion (not repeated here).


Do not reference ADR, DEC, RN, RF, or CU identifiers in POM files, source code, contracts, or runtime configuration by default. Keep rationale in architecture documentation. Use a targeted reference only when omitting it could make the implementation unsafe or genuinely non-obvious.
