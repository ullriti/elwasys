---
description: Adapts this baseline repo to the concrete project – fills AGENTS.md/conventions with stack & code style and creates the needed specialist agents. Also for adding more agents later.
---

You set up the baseline for **this** project. Work **interactively**:
analyze → propose → get confirmation → implement. **Never overwrite existing
content without asking**; fill placeholders instead of restructuring.

## Step 1 — Analyze the project

- Detect stack/languages: read manifest/build files (`package.json`,
  `pyproject.toml`/`requirements.txt`, `pom.xml`/`build.gradle`, `go.mod`,
  `Cargo.toml`, `Dockerfile`, `*.csproj`, …) and review the directory structure.
- Determine build/test/lint/format commands (derive from scripts/tooling).
- Identify modules/domains (e.g. `frontend/`, `backend/`, `infra/`).

## Step 2 — Ask for the languages

Ask the user for the three independent languages and record them in `AGENTS.md`,
section 0. Then use the **chat** language for the rest of this conversation.

- **Chat** (agent ↔ user) — how the agent talks to the user.
- **Documentation** (docs/, KB, worklog, specs, ADRs, CHANGELOG, README).
- **Code & comments** (identifiers, code comments, commit messages).

Offer sensible defaults (e.g. all English, or chat in the user's language + docs
& code in English). Note: existing template docs are English; if the user picks
another documentation language, only new/edited docs need to follow it.

## Step 3 — Fill AGENTS.md & conventions

- In `AGENTS.md` replace all `<PLACEHOLDER>`: project name, purpose, stack, setup,
  `<BUILD_CMD>`/`<TEST_CMD>`/`<LINT_CMD>`/`<FORMAT_CMD>` (languages are already set
  in step 2).
- In `AGENTS.md` (section 5, Conventions) add the **language/stack-specific code
  style** (formatter/linter, naming and structure rules, test style). Respect
  existing rules – only fill/extend. Module-specific rules go in a nested
  `CLAUDE.md`.
- For modules with their own rules, optionally propose a **nested `CLAUDE.md`**
  (see `docs/agent-setup.md`, "Rules/instructions").

## Step 4 — Create specialist agents

Determine which specialists the project **really** needs (no agent zoo) and
propose them for confirmation. Catalog of common archetypes – pick only fitting ones:

| Agent | When useful | Core tasks |
|-------|-------------|------------|
| `backend` | server/API/DB present | endpoints, services, persistence, unit tests |
| `frontend` | UI/client present | components/pages, state, API wiring, a11y |
| `devops` | CI/CD, IaC, containers | pipelines, deployments, infra-as-code, observability |
| `security` | elevated security needs | threat modeling, OWASP, secrets, dependency audit |
| `ai-expert` | LLM/ML in the project | prompts, model choice, evals, data pipelines |
| `data` | DB/ETL/analytics | schema, migrations, queries, data quality |
| `test-writer`/`e2e` | dedicated test layer | test coverage, E2E flows |

For each **confirmed** agent create a `.claude/agents/<name>.md` following the
pattern of `code-reviewer.md`/`orchestrator.md`:

- **Frontmatter:** `name`, a trigger-strong `description`, optionally `tools`/`model`.
- **Body:** mission · domain boundaries · **stack-specific rules/code style** ·
  test/security duties · reference to `AGENTS.md` · the rule
  "knowledge belongs in the repo, not in local user memory".

## Step 5 — Wire the orchestrator

In `.claude/agents/orchestrator.md` enter the **concrete specialists + domain
routing** (replace the placeholder note there) so delegation knows the real agents.

## Step 6 — Seed knowledge & verify

- Create the KB: a short project overview in `docs/kb/` and set "Current state".
- **Worklog entry** for the setup; extend the **CHANGELOG** under `[Unreleased]`.
- Run `/audit-ai-docs` (or `scripts/check-ai-docs.sh`) and fix any findings.

## Guardrails

- **Minimally invasive:** propose and get confirmation; only the needed agents.
- Respect the baseline's behavior/structure; fill placeholders, don't restructure.
- At the end, summarize what was changed/created.
