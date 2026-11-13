# PLAN - HaulWise

## 1. Summary / chosen idea

**HaulWise** is a dispatcher tool for a fictional Polish company with several warehouses that moves goods between them by truck. For one **run from one depot**, the app assigns shipments to trucks, orders the stops, and picks the route that is optimal by **fuel cost and travel time**. The dispatcher can **prioritise** deliveries even when that is not optimal.

**Creative twist (headline): "Cost of priority".** The app shows the optimal plan next to the priority-respecting plan, with a plain-language delta, e.g. "Delivering Gdansk first costs 112.35 PLN more (38 km and 41 min longer)."

- Stack (fixed): Java + Spring Boot backend, H2 in-memory DB, React + plain JavaScript (Vite, no TypeScript) frontend, separate `backend/` and `frontend/` folders, REST + CORS.
- English UI, currency PLN.
- Core idea in one line: a small, readable, deterministic solver (bin packing + stop ordering + fuel model) that the candidate can fully explain, rather than a black-box library.
- Optional AI (Claude API): turn a plain-language order into a pre-filled shipment form, and write a short plan summary. All numbers come from code. The app works fully without a key.

## 2. Requirements

**Explicit (from task and user decisions)**
- Separate `backend/` and `frontend/` folders; backend offers CRUD; Java + Spring Boot; H2 in-memory; React + plain JS (Vite).
- Evaluated on: architecture/project organisation (logical layers), code quality, REST API design + CORS, UX/UI polish, a creative twist, optional AI use, and the candidate's understanding of the solution over complexity.
- Planner chooses routes by fuel cost and travel time; dispatcher can prioritise deliveries even if not optimal.
- Multiple warehouses; goods moved by truck.
- English UI, PLN.
- Won't: accounts/auth, live GPS, multi-depot pickup-and-delivery VRP, persistent DB, real tolls, CO2/eco features.

**Implicit (what a reviewer looks for)**
- Clean layering (controller / service / repository / DTO / pure solver package), validation, consistent error format, correct HTTP status codes.
- Pure deterministic solver code with unit tests; API tests (MockMvc) including CORS.
- README with run instructions, architecture, algorithm, fuel/cost model, trade-offs and AI disclosure.
- Works offline and without keys (graceful degradation).
- Polished UI: loading, empty and error states; responsive layout.
- The author can explain every algorithm choice. Prefer simple over clever.

**Decisions (confirmed by user)**
- Planning is done per run from **ONE depot** (the origin warehouse). Shipments in a run all start at that depot.
- Shipments are **atomic**: never split across trucks. One truck can carry several shipments to **different destination warehouses** (multi-drop).
- Priority = soft weighted penalty + hard "pin first" override.
- Build: **Maven** (with Maven wrapper `mvnw`, so Maven need not be installed). **Java 17** language level (Spring Boot 3.x); builds fine on newer JDKs.
- UI: **a component library** (architect to confirm; Mantine suggested - works with plain JS).
- Run: **plain local run** (no Docker).

**Assumptions** (still open to change)
- **Assumption:** Optional "return to depot" toggle per run (default on).
- **Assumption:** Distances and times between warehouses come from a **precomputed, seeded distance/time matrix** of about 8-10 real Polish cities. Optional refresh through the OSRM table API with caching, and a haversine x road-factor fallback, so the app runs fully offline.
- **Assumption:** Leaflet + OpenStreetMap tiles for the route map (tiles need internet; the map degrades to a stop list if offline).
- **Assumption:** Polish defaults: diesel about 6.20 PLN/L (editable), fallback average speed about 70 km/h, truck capacity 33 pallets, payload about 24,000 kg, base consumption about 28 L/100km, extra consumption per full load about +10 L/100km, driver cost about 45 PLN/h.

## 3. Domain and model (high level, details for the architect)

**Entities (all CRUD):**
- **Warehouse**: name, city, lat, lon.
- **Truck**: plate, capacity (pallets), payload (kg), base consumption (L/100km), extra consumption per load, driver cost per hour; post-MVP also a home depot (US-16) and an available / on-route flag (US-17).
- **Shipment**: origin, destination, pallets, weight (kg), priority (URGENT / HIGH / NORMAL), optional deadline, pinned-first flag.
- **Plan / Route** (result, not hand-edited): trucks, ordered stops, km, time, fuel litres, cost, warnings.
- **Settings**: diesel price PLN/L, average speed fallback, priority weights, EU compliance parameters.

**Fuel model:** `litres = distance x (base + k x current load fraction)` per leg, so **stop order affects fuel** (delivering the heaviest drop first is cheaper). **Cost = fuel cost + driver cost x hours.**

**Solver pipeline (pure code, no Spring dependency):**
1. Assign shipments to trucks with first-fit-decreasing bin packing, sorted by priority then size. Respect pallets and kg. Anything that does not fit is reported as **unassigned** with a reason.
2. Order the stops for each truck: brute-force permutations when there are 8 stops or fewer; otherwise nearest neighbour + 2-opt.
3. Score the route with the fuel/time/cost model.
4. Priority handling: a **weighted penalty** (soft, urgent deliveries arriving late cost extra in the objective) **plus a hard "pin first" override** (the pinned shipment's stop is forced first).
5. Run the solver twice: **optimal** (priorities ignored) and **priority-respecting**. Return both plus the delta.

## 4. Scope

### Must have (MVP, core)
- CRUD for warehouses, trucks and shipments (REST, validation, consistent errors, CORS).
- Distance/time matrix between seeded warehouses.
- Fuel and cost model.
- Planner: first-fit-decreasing assignment, stop ordering (permutations / NN + 2-opt), multi-drop, optional return to depot.
- Priority handling: weighted penalty plus hard pin-first.
- **Cost of priority**: optimal vs priority-respecting plan side by side with plain-language delta (+km, +PLN, +time).
- Plan API and plan result UI: per-truck timeline, utilisation, warnings (over capacity, unassigned shipments, missed deadline).
- Route map with stops (Leaflet + OSM).
- Seeded, realistic demo data (warehouses, trucks, shipments).
- Solver unit tests; API tests.
- README documenting the algorithm, the fuel/cost model and the trade-offs (OR-Tools/Timefold as the production alternative, and why a small readable solver was chosen).

### Should have (only if MVP is done)
- EU driving-time compliance check: roughly a 45 min break after 4.5 h driving and a 9 h daily driving limit. **Implementer must verify the exact rules** (Regulation (EC) 561/2006) and make the values **configurable**.
- Load consolidation suggestion: two part-empty trucks heading to the same region could be merged; show the saving.
- Optional AI via Claude API (key from env, feature flag endpoint `/api/features`): (1) paste a plain-language order and the shipment form is filled for user review, **never auto-saved**; (2) short dispatcher summary of the plan. All numbers computed by code, never by the LLM.

### Extras (explicitly nice-to-have, lowest priority)
- Truck utilisation KPI dashboard: fill rate, cost per pallet, empty km.
- Urgent re-plan: add an urgent shipment to an existing plan and see what changes and the cost delta.

### Won't have (this time)
- Accounts/auth, live GPS tracking.
- Multi-depot pickups-and-deliveries VRP.
- Time windows beyond a simple deadline warning.
- Persistent database (H2 in-memory, reseeded on startup).
- Real tolls integration (future work: mention e-TOLL).
- CO2/eco features.

## 5. User stories and acceptance criteria

- **US-1 Manage warehouses.** As a dispatcher I add, edit and delete warehouses.
  - AC: POST/GET/PUT/DELETE work; validation returns 400 with field errors; lat/lon range checked; deleting a warehouse used by a shipment returns 409 with a clear message; matrix entries exist for every warehouse (or are estimated by haversine fallback).
- **US-2 Manage trucks.** As a dispatcher I maintain the fleet.
  - AC: CRUD with validation (capacity > 0, consumption > 0); list shows capacity and consumption; delete asks confirmation.
- **US-3 Manage shipments.** As a dispatcher I create shipments with destination, load, priority, optional deadline and a pin-first flag.
  - AC: CRUD with validation (origin != destination, pallets > 0, weight > 0); priority chips URGENT/HIGH/NORMAL; list filterable by priority.
- **US-4 Plan a run.** As a dispatcher I choose a depot, select shipments and trucks, and get a plan.
  - AC: shipments are never split; one truck may serve several destinations; each truck has an ordered stop list with km, time, litres and cost; unassigned shipments are listed with a reason; over-capacity is never produced silently.
- **US-5 Fuel-aware ordering.** As a dispatcher I want stop order to reduce fuel.
  - AC: for a fixed set of stops, the heavy drop first yields lower litres than the reverse (unit-tested with fixtures); with 8 stops or fewer the result equals the brute-force optimum.
- **US-6 Prioritise.** As a dispatcher I mark shipments URGENT/HIGH or pin one first even when it is not optimal.
  - AC: a pinned shipment is the first stop of its truck; URGENT stops are visited earlier than the optimal order when that costs extra; conflicting pins are reported as a warning.
- **US-7 Cost of priority (headline).** As a dispatcher I see what my prioritisation costs.
  - AC: the optimal and priority plans are shown side by side; a plain-language sentence states the delta in km, PLN and time; if priority costs nothing, the UI says "no extra cost"; delta values are computed by code and covered by tests.
- **US-8 See the route.** As a dispatcher I see trucks and stops on a map and on a timeline.
  - AC: each truck has a colour; markers numbered by stop order; per-truck timeline with arrival times; utilisation bar (pallets and kg); warnings visible.
US-9 to US-13 were not built: their tasks (T-22..T-28) were cut in lean mode to protect the must-haves. The compliance and consolidation settings exist and are editable (T-07, T-31) but nothing uses them yet.

- **US-9 Compliance (Should).** AC: driving time per truck checked against configurable limits; a violation shows a warning with the segment and a suggested break; limits editable in settings.
- **US-10 Consolidation (Should).** AC: when two trucks to the same region are both below a configured fill threshold and fit together, a suggestion shows the saving in PLN and km.
- **US-11 AI order entry (Should).** AC: with a key, pasted free text fills the shipment form for review (never auto-saves); a short plan summary is available; without a key the controls are hidden and nothing errors; numbers in the summary come only from code.
- **US-12 KPI dashboard (Extra).** AC: fill rate, cost per pallet and empty km per truck and per plan.
- **US-13 Urgent re-plan (Extra).** AC: adding an urgent shipment to an existing plan shows which trucks and stops change and the cost delta.
- **US-14 Truck recommendation (Extra, added post-MVP 2026-11-13).** AC: given a depot and selected shipments, the app suggests which trucks to use from the whole fleet (not just the ones already ticked; since US-16/US-17 the whole *available* fleet based at that depot), picking the cheapest combination that fits everything; the dispatcher sees why (cost, utilisation) and can still change the selection by hand before planning.
- **US-15 Editable settings (Extra, added post-MVP 2026-11-13).** AC: diesel price, average speed, road factor, priority weights and the EU driving-time limits are editable from the Settings page (not only via the API), with the same validation ranges as the backend and a success notification on save.
- **US-16 Truck home depot (Extra, added post-MVP 2026-11-14).** AC: every truck has a home warehouse; a plan can only use trucks based at the chosen depot, enforced by the API (not just hidden in the UI); the Trucks page shows and can set a truck's home depot; adding trucks at other depots doesn't slow down planning or recommending trucks at depots with a small fleet.
- **US-17 Dispatch a plan / "Send trucks" (Extra, added post-MVP 2026-11-14).** AC: from a plan result, "Send trucks" marks every truck actually used in the shown variant as on-route (unavailable for new plans) and removes every shipment that was routed in it (shipments left unassigned by the plan are untouched); nothing in-app represents messaging drivers — this only records the dispatch; a truck can be marked available again by hand on the Trucks page; sending the same plan twice is refused with a clear message, not a crash or a double delete.

## 6. Task breakdown and milestones

Each task is about 1-3 hours.

### Day 1 - Skeleton, domain, CRUD, matrix, cost model
- [x] **T-01** Repo skeleton: `backend/` (Spring Boot, Maven wrapper, Java 17), `frontend/` (Vite React JS), root `.gitignore`, `docs/`. (Must)
- [x] **T-02** Domain model + H2 config + repositories: Warehouse, Truck, Shipment, Settings; layered packages (controller / service / repository / dto / solver). (Must)
- [x] **T-03** CRUD REST API for warehouses, trucks, shipments: DTOs, validation, global error handler, correct status codes (409 on referenced delete). (Must)
- [x] **T-04** CORS config (env-driven allowed origins) and OpenAPI/Swagger page. (Must)
- [x] **T-05** Distance/time matrix: seeded matrix between 8-10 Polish cities, `DistanceProvider` interface, haversine x road-factor fallback. (Must)
- [x] **T-06** Fuel and cost model as a pure class + unit tests (load-dependent consumption, driver time, cost). (Must)
- [x] **T-07** Settings API (diesel price, average speed, priority weights, compliance limits) with Polish defaults. (Must)
- [x] **T-08** Seed loader: warehouses, trucks, a realistic set of shipments. (Must)

**Day 1 milestone:** CRUD works via Swagger/curl, CORS verified, matrix queryable, cost model tested.

### Day 2 - Planner, priority, cost-of-priority, plan API, frontend CRUD, map
- [x] **T-09** Bin packing: first-fit-decreasing by priority then size, atomic shipments, unassigned reasons + unit tests. (Must)
- [x] **T-10** Stop ordering: brute-force permutations for 8 stops or fewer; nearest neighbour + 2-opt otherwise; optional return to depot + unit tests. (Must)
- [x] **T-11** Priority handling: weighted penalty plus hard pin-first + unit tests. (Must)
- [x] **T-12** Cost of priority: run optimal and priority plans, compute delta and plain-language text + unit tests. (Must)
- [x] **T-13** Plan API: `POST /api/plans` (depot, shipment ids, truck ids, return flag) returning both plans, delta and warnings. (Must)
- [x] **T-14** Frontend setup: routing, API client, layout, CSS design tokens, loading/error/empty components. Add `matchMedia`/`ResizeObserver` mocks to `src/test/setup.js` for Mantine. (Must)
- [x] **T-15** Frontend CRUD pages for warehouses, trucks, shipments (forms, validation messages, delete confirm). (Must)
- [x] **T-16** Plan form and first plan result view; Leaflet map with numbered stops. (Must)

**Day 2 milestone:** a plan can be created from the UI, shown on a map, with the cost-of-priority numbers visible.

### Day 3 - Plan UI polish, should-haves, tests, README, QA
- [x] **T-17** Plan result UI polish: side-by-side optimal vs priority, delta sentence, per-truck timeline, utilisation bars, warnings. (Must)
- [x] **T-18** Backend API tests (MockMvc: CRUD, validation, 409, CORS, plan endpoint). (Must)
- [x] **T-19** Frontend tests (Vitest/RTL, small set: forms, delta display, warnings). (Must)
- [x] **T-20** UX polish: responsive layout, empty states, a11y pass, consistent copy. (Must)
- [x] **T-21** README: overview, screenshots, run steps, architecture, API table, algorithm, fuel/cost model, trade-offs (OR-Tools/Timefold alternative), AI disclosure, e-TOLL as future work, a "Demo data" section (seed contents, `HAULWISE_SEED_ENABLED`, approximate matrix, demo deadlines relative to the startup day, moving a warehouse back to its old coordinates keeps estimated legs until restart (D-06)). (Must)
- [ ] **T-22** EU driving-time compliance check (verify rules, configurable) + UI warning. (Should) - **cut** (lean mode)
- [ ] **T-23** Load consolidation suggestion with saving. (Should) - **cut** (lean mode)
- [ ] **T-24** Feature flag endpoint + AI order parsing (Claude API, key from env). Key set but model empty = AI disabled (or default the model). (Should) - **cut** (lean mode)
- [ ] **T-25** AI plan summary (prompt receives computed numbers only). (Should) - **cut** (lean mode)
- [ ] **T-26** Optional OSRM table refresh with cache. (Should, only if time) - **cut** (lean mode)
- [ ] **T-27** KPI dashboard: fill rate, cost per pallet, empty km. (Extra) - **cut** (lean mode)
- [ ] **T-28** Urgent re-plan with change list and cost delta. (Extra) - **cut** (lean mode)
- [x] **T-29** Final QA: clean-clone run, no-API-key run, offline run, seed sanity, lint/format. (Must) - merged with `docs/readme-final-qa`

### Post-MVP extras (added 2026-11-13, time allowed after T-29)
- [x] **T-30** Truck recommendation: given a depot + selected shipments, search combinations of the full truck fleet (since T-32/T-33: the depot's available trucks), pick the cheapest that fits everything, expose it via the API and a "Recommend trucks" button on the plan form. (Extra, US-14)
- [x] **T-31** Editable Settings page: a form for GET/PUT `/api/settings` with the same validation ranges as the backend. (Extra, US-15)
- [x] **T-32** Truck home depot: `homeWarehouseId` on Truck; keep the existing 5 WAW Central trucks unchanged (preserves the demo numbers byte-for-byte), add new trucks at POZ West (and optionally elsewhere) sized for its own shipments; `POST /api/plans` and `/api/trucks/recommend` only consider trucks homed at the chosen depot (400/no-candidate, never a cross-depot result); Trucks CRUD shows/sets the home depot; Plan form's truck checklist filters by the selected depot. A test must prove a bigger fleet at one depot doesn't slow down planning at another. (Extra, US-16)
- [x] **T-33** Dispatch a plan ("Send trucks"): `available` flag on Truck (toggle by hand on the Trucks page, reusing truck CRUD); `POST /api/plans/{id}/dispatch` (chosen variant) marks its routed trucks unavailable and deletes its routed shipments, idempotent (a second dispatch of the same plan is refused with a clear message, not a crash); plans and recommendations also exclude unavailable trucks (depends on T-32). (Extra, US-17)

**Day 3 milestone:** polished plan UI, tests green, README complete, clean-clone run works.

### Git branches (approved 2026-11-12)

One branch per batch of related tasks, cut from the latest `main`. One commit per task (`feat(T-03): ...`), review fixes as `fix(T-03): ...`. A branch is merged into `main` with `git merge --no-ff` only after the code review passes and the tests are green, then deleted. Merges and pushes need the user's OK.

| # | Branch | Tasks |
|---|---|---|
| 1 | `feature/project-skeleton` | docs + agent setup, T-01 |
| 2 | `feature/backend-core-api` | T-02, T-03, T-04, T-07 |
| 3 | `feature/distances-cost-seed` | T-05, T-06, T-08 |
| 4 | `feature/route-planner` | T-09, T-10, T-11, T-12, T-13 |
| 5 | `feature/frontend-crud` | T-14, T-15 |
| 6 | `feature/plan-ui` | T-16, T-17 |
| 7 | `test/api-and-ui-tests` | T-18, T-19 (done on `feature/plan-ui`, lean shortcut) |
| 8 | `feature/ux-polish` | T-20 (done on `feature/plan-ui`, lean shortcut) |
| 9 | `feature/compliance-consolidation` | T-22, T-23 (never created - tasks cut) |
| 10 | `feature/ai-assist` | T-24, T-25 (never created - tasks cut) |
| 11 | `feature/osrm-refresh` | T-26 (never created - task cut) |
| - | `feature/kpi-and-replan` | T-27, T-28 (never created - tasks cut) |
| 12 | `docs/readme-final-qa` | T-21, T-29 (always last, so the README describes what was built) |
| 13 | `feature/extras` | T-30, T-31 (post-MVP, after T-29; README not re-run at that point) |
| 14 | `feature/fleet-ops` | T-32, T-33 (post-MVP, after T-30/T-31; home depot and dispatch share the same "available truck pool per depot" concept) |

### CUT LINE

- **Protected (never cut):** T-01 to T-21 and T-29. If behind at the end of Day 2, protect T-17 to T-21 and T-29 first.
- **Cut in this order if time is short (first cut first):** T-28, T-27 (Extras), then T-26, T-25, T-24, T-23, T-22 (Should; AI before compliance, since compliance is closer to the domain).
- Never trade the cost-of-priority view or the solver tests for a Should/Extra.
- Time rule: if at the end of Day 1 the CRUD is not working, drop frontend tests (T-19) down to two tests and keep the rest.

## 7. Risks

| Risk | Mitigation |
|---|---|
| Solver scope creep (VRP rabbit hole) | Fixed pipeline: FFD + permutations / NN + 2-opt; one depot, atomic shipments; no time windows |
| Priority semantics unclear or confusing | Document soft penalty vs hard pin; show both plans; test with fixed fixtures |
| Brute-force blow-up | Cap at 8 stops per truck (8! = 40,320), fall back to NN + 2-opt above that |
| OSRM unavailable or rate-limited | Seeded matrix is the default; OSRM optional with timeout, cache and haversine fallback |
| Map tiles need internet | Stop list and timeline remain usable; note in README |
| Fuel numbers look arbitrary | Document the formula and defaults; all editable in settings; unit tests with hand-computed examples |
| EU rules misremembered | Implementer verifies the regulation and makes values configurable |
| AI leaks into numbers or no key | Code computes all numbers; LLM only parses text and summarises; feature flag hides UI |
| H2 data lost on restart | Seed loader on startup; documented |
| Candidate cannot explain a complex solution | Keep the solver small and readable; README explains each step; mention OR-Tools/Timefold as production alternative |
| Time budget (2-3 days) | Pure-logic first with tests; UI polish last; cut line above |

## 8. Open questions for the user

All initial questions are resolved (see Decisions in section 2). Dev machine prerequisites verified on 2026-11-12: JDK 21 (build baseline), Node.js 26 + npm 11, Git on PATH.
