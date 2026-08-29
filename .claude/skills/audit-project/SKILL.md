---
name: audit-project
description: Repo-wide, read-only-first audit for inconsistencies, weak citations, duplication, empty files, and broken references across rules, skills, commands, agents, docs, config, and build files. Use for a general project health check, not for docs/ID-traceability specifically (see consistency-check) or ADR/architecture consistency specifically (see coldguard-architecture, architecture-auditor agent).
---

# audit-project

## Purpose

A repo-wide diagnostic pass that starts read-only: surface inconsistencies, weak or missing
citations, duplicated responsibility, empty/placeholder files presented as real content, and
broken references — across `.claude/rules/`, `.claude/skills/`, `.claude/commands/`,
`.claude/agents/`, documentation, project configuration, and build files (e.g. `pom.xml`).

## When to use

- A general project/repo health check (not scoped to `docs/architecture`/`docs/domain` IDs — that
  is `consistency-check`'s job).
- Before a sprint, before onboarding, or when drift is suspected (a rule or skill describing a
  state that no longer matches the actual files — this has happened in this project before: a
  rule referencing a since-changed dependency version).
- When asked to find dead files, duplicated skill/rule responsibility, or unverified claims.

## Inputs

- Optional scope: whole repo, or a subtree (e.g. `.claude/`, `docs/`, `apps/`).
- Optional focus area: rules, skills, commands, agents, documentation, configuration, build files.
- If neither is given, default to the whole repository.

## Ownership and related skills

- Does not re-check documentation ID integrity, cross-references (RF/RN/CU/ADR/DEC), or Markdown
  link validity inside `docs/` — that is owned by `.claude/skills/consistency-check/SKILL.md`.
  When a finding is exactly that kind of check, point to `consistency-check` instead of
  re-implementing its checks here.
- Does not draft or format ADRs — a finding that looks ADR-worthy (undocumented architectural
  decision, contradiction with a closed ADR) is handed to `.claude/skills/new-adr/SKILL.md` and,
  if the change is non-trivial, to `.claude/skills/architecture-change/SKILL.md`.
- Does not re-derive architecture-specific domain knowledge (C4 consistency, event-vs-gRPC
  criteria) — that lives in `.claude/skills/coldguard-architecture/SKILL.md` and the
  `architecture-auditor` agent; reference them instead of restating their checklists.
- This skill's own scope is the rest: cross-cutting repo health that no other skill already owns.

## Allowed actions

- Read, Grep, Glob across the chosen scope.
- Read-only shell inspection: `find`, `grep`, `git status`, `git log`, `git diff` (no staging or
  committing).
- Only the specific inspection tools actually available in this environment — do not present a
  tool as available without having used or verified it in this session.

## Forbidden actions

- No `Edit`/`Write` during the audit phase, under any finding severity.
- No `git commit`, `git push`, or any git command that changes state.
- No `mvn`, `docker`, `terraform`, or `az` command.
- Do not "fix" a finding inline while auditing — findings are reported, not corrected, in this
  skill.
- Do not invent documentary backing for a claim this skill cannot verify — if no ADR/DEC/RN/RNF/
  rule file supports a statement, report it as unverified, don't assume one exists.

## Workflow

1. **Inspect**: list the files in scope (rules, skills, commands, agents, relevant docs,
   configuration, build files).
2. **Map relevant rules/docs**: for each area touched, identify which `.claude/rules/*.md` file
   (or `docs/` file) is the stated owner of that topic.
3. **Detect**: inconsistencies against the owning rule/doc, weak citations (a claim referencing an
   ADR/DEC/RN/RNF that doesn't actually say what's claimed), duplication (two rules/skills/commands
   covering the same ground), empty files presented as populated (e.g. a `.gitkeep`-only directory
   referenced as if it has content), and broken references (a path or ID cited that doesn't
   resolve).
4. **Classify** each finding by severity (critical/high/medium/low) and by type (inconsistency,
   weak citation, duplication, empty file, broken reference).
5. **Propose a plan**: group findings by file, suggest an owner for the fix (rule edit, skill
   edit, ADR, DEC, or code change) — do not assume which skill will apply it.
6. **Stop** before writing anything, unless the user explicitly asked for implementation in the
   same request. If they did, hand off to the appropriate owner (this skill does not implement
   architecture changes, ADRs, or doc-ID fixes itself — see Ownership above).

## Stop conditions

- Any point where writing a fix would exceed this skill's own ownership (see "Ownership and
  related skills") — stop and name the skill that should take over.
- A finding depends on a claim this skill cannot verify against the actual repository state —
  report it as unverified rather than resolving it either way.
- The user asks for implementation without having reviewed the findings table first.

## Expected output

- A findings table: file | finding | severity | type | evidence.
- For each finding: whether it is a **formal decision** (backed by an ADR/DEC/RN/RF/RNF ID), a
  **stated requirement** (from `CLAUDE.md` or a rule), an **engineering convention** (a team
  practice not backed by any of the above — mark it explicitly as such, following the same
  pattern already used in `.claude/rules/testing.md`), or an **unverified/current-state claim**
  that needs checking against the live repository.
- A proposed plan: grouped by file, with a suggested owner (rule, skill, ADR, DEC, or code).
- List of impacted files.
- An explicit closing line: whether any file was modified during this run (audit mode: always
  "no files were modified").

## Example invocations

- "Run an audit-project pass over `.claude/` and tell me what's stale."
- "Audit the whole repo for broken references before the sprint review."
- "Check `docs/` and `pom.xml` for claims that don't match the current state."
