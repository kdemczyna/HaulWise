# Project: Xelerit recruitment task

Small full-stack web application, ~2–3 days of work. Quality, clarity, and a working end-to-end result matter more than feature count.

## Team (subagents in `.claude/agents/`)
| Agent | Role | Writes |
|---|---|---|
| `planner` | Idea, scope (MVP / should / won't), user stories, task breakdown | `docs/PLAN.md` |
| `architect` | Tech stack, structure, data model, API contract, decisions | `docs/ARCHITECTURE.md` |
| `developer` | Implements tasks + tests, fixes review findings | code, ticks tasks in PLAN |
| `code-reviewer` | Read-only review: correctness, security, tests, conformance; re-reviews fixes | review report |
| `qa-tester` | Read-only acceptance testing of the running app against the user stories | QA report |

## Workflow
1. **Plan** – `planner` turns requirements into `docs/PLAN.md`. User approves idea + scope.
2. **Design** – `architect` writes `docs/ARCHITECTURE.md`. User approves stack + key decisions.
3. **Build loop** (per task, on the batch's feature branch – see "Git branches" in `docs/PLAN.md`):
   1. `developer` implements.
   2. `code-reviewer` reviews.
   3. `developer` fixes findings.
   4. **Fix check:** if the review had any **Blocker or Major**, `code-reviewer` re-reviews only the fix diff (re-review mode) until it approves. Minor/nit-only fixes are checked by the orchestrator against the findings list.
   5. Full test suite green → commit `feat(T-xx): …` → push the feature branch.
4. **Before merging a branch into `main`:** `qa-tester` checks the branch against the acceptance criteria of the user stories it touches (API/Swagger for backend-only branches, the running UI from the frontend branches on). Failures go back into the build loop. Merge (`--no-ff`) and push `main` only with the user's OK.
5. **Finish** – full-repo review by `code-reviewer`, full QA pass by `qa-tester` (T-29), README polish, final run of tests.

## Conventions
- `docs/PLAN.md` and `docs/ARCHITECTURE.md` are the source of truth; update them when decisions change.
- Tasks have IDs (`T-01`, …); reference them in commits and reports.
- Environment: Windows + PowerShell.
- No secrets in the repo; use `.env` + `.env.example`.
