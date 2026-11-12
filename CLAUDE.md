# Project: Xelerit recruitment task

Small full-stack web application, ~2–3 days of work. Quality, clarity, and a working end-to-end result matter more than feature count.

## Team (subagents in `.claude/agents/`)
| Agent | Role | Writes |
|---|---|---|
| `planner` | Idea, scope (MVP / should / won't), user stories, task breakdown | `docs/PLAN.md` |
| `architect` | Tech stack, structure, data model, API contract, decisions | `docs/ARCHITECTURE.md` |
| `developer` | Implements tasks + tests, fixes review findings | code, ticks tasks in PLAN |
| `code-reviewer` | Read-only review: correctness, security, tests, conformance | review report |

## Workflow
1. **Plan** – `planner` turns requirements into `docs/PLAN.md`. User approves idea + scope.
2. **Design** – `architect` writes `docs/ARCHITECTURE.md`. User approves stack + key decisions.
3. **Build loop** (per task or small group of tasks):
   `developer` implements → `code-reviewer` reviews → `developer` fixes blockers/majors → next task.
4. **Finish** – full-repo review by `code-reviewer`, README polish, final run of tests.

## Conventions
- `docs/PLAN.md` and `docs/ARCHITECTURE.md` are the source of truth; update them when decisions change.
- Tasks have IDs (`T-01`, …); reference them in commits and reports.
- Environment: Windows + PowerShell.
- No secrets in the repo; use `.env` + `.env.example`.
