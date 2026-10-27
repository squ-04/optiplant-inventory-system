# E2E exploration findings — fixes

Objective: fix the defects reported in `docs/08-implementacion/exploracion-e2e-2026-10-27.md` that need no product decision.
Branch: `fix/e2e-exploration-findings` from `main` 01a068a. Delivery: work-unit commits on this branch, authorized by the user on 2026-10-27; no push. Runners: `node --test` (frontend), Maven Docker runner (`scripts/check_application.py backend`), browser walkthrough with `playwright-cli` on a disposable stack (option B).

## Specs

- **S1 (H1):** the operator/manager local catalog («Mi sucursal») must show current stock after movements are registered, without a full browser reload.
- **S2 (H2):** a business rejection in Administration is shown once, in its view; the shell-level alert is reserved for session problems and is cleared when the user navigates to another view, so it never sits next to a later success.
- **S3 (H3):** creating a user whose temporary password equals the code, or whose temporary password length is outside 15–128, and creating a unit with factor `0`, return a specific `detail` (code stays `VALIDATION_FAILED`). The user form states that the temporary password must differ from the code.
- **S4 (H5):** after logging in, every user lands on the first view of their navigation (or the mandatory password change), not on the view left by the previous session.
- **S5 (H6):** Consultas, Movimientos and Alertas show dates with the same `es-CO` format; Consultas history shows Spanish labels for movement kind, direction and document status; «Mi sesión» and the sidebar show the role label (Administrador/Gerente/Operador); no «a. m..» double period.
- **S6 (H8):** the unavailable-inventory message no longer attributes a server error only to lost communication.
- **S7 (H10):** the page declares a favicon so the browser no longer requests `/favicon.ico` (404).
- **S8 (H4, decided L6):** «dejemos que se use la misma contrasena temporal como nueva». The password change keeps accepting the temporary password as the new one; no code change.
- **S9 (H7, decided L6):** «los decimales tratemolos con una coma con el fin de que se diferencien del punto de separacion en los miles». Every decimal shown in the UI uses `,` as decimal separator and `.` as thousands separator, keeping the scale sent by the server; decimal inputs are typed with `,` and sent to the API with `.` (REST contract unchanged); Spanish backend messages that embed quantities use the same format.
- **S10 (H9, decided L6):** «si hay algo que se pueda mejorar con shadcn y se pueda implementar y reutilizar hazlo». Destructive and reactivation confirmations use the official shadcn/ui Alert Dialog through one shared confirmation component, replacing `window.confirm`.

## Tasks

| ID | Specs | Route | Status | Evidence |
|---|---|---|---|---|
| T1 | S3 backend messages + HTTP tests | inline | done | RED 2 failures → GREEN 9/9 (`UserManagementHttpTest`, `ProductConfigurationHttpTest`) |
| T2 | S1, S2, S4, S6, S7 frontend shell/catalog/administration | inline | done | `sessionProblem` RED → GREEN; `tsc -b` OK; browser re-verification L5 |
| T3 | S5 shared formatting helpers + tests, views | inline | done | `format.test.ts` RED → GREEN; `node --test` 42/42 |
| T4 | Browser re-verification, report correction, docs | inline | done | L5; report §Seguimiento; catálogo and acceso docs |
| T5 | S8 record decision in report and docs | inline | done | report §Seguimiento |
| T6 | S9 decimal formatting helpers + tests, views, inputs, backend messages, browser scripts, docs | inline | done | `format.test.ts` RED 2 → GREEN; `SpanishDecimalTest` + `InventoryMovementHttpTest` RED 2 → GREEN 17/17; browser L7 |
| T7 | S10 Alert Dialog component + shared confirmation, docs (Risk item 5: new dependency) | inline | done | `@radix-ui/react-alert-dialog` 1.2.1 exact; browser L7; independent verifier L8 |

## Log

- L1 (2026-10-27): user request: «Quiero que generes una rama, y con la documentacion que hiciste y lo que encontraste soluciona los errores»
- L2: correction to the report: the local catalog does have an «Actualizar catálogo» button for operators (`frontend/src/app/Catalog.tsx:75`); the report stated otherwise. The stale data after movements still holds: the view loads only on mount and on `revision` changes.
- L3: H2 root cause: `Administration.run` sets its local error and also calls `onError`, which sets the shell alert; the shell alert is only cleared by the next shell action. H5 root cause: the URL hash of the previous session (logout happens from «Mi sesión») selects the landing view.
- L4: RED observed before each change: backend detail assertions failed with «La solicitud no es válida.»; `format.test.ts` and the `sessionProblem` test failed on missing exports. No commits: AGENTS.md §3 requires explicit authorization.
- L5 (browser, disposable stack `optiplant-e2efix`, removed afterwards): no `favicon.ico` request; duplicate branch shows one alert and success clears it (S2); temporary password equal to the code and unit factor `0` show their specific messages, with the form hint (S3); after logout from «Mi sesión» the operator lands on «Cambio inicial obligatorio» and, after relogin, on «Consulta de sucursal» with an empty hash (S4); after an entry of 2 Caja, «Mi sucursal» shows 24.000 without reload (S1); Consultas history reads «27/10/26, 10:33:01 a. m. · Ingreso ordinario · Entrada» and the live status has no double period; a simulated 500 reads «sin comunicación con el servidor o servicio no disponible» (S5, S6); role shows «Administrador» in the sidebar and «Mi sesión». Alertas dates rely on the shared helper (unit-tested), not re-walked.
- L6 (2026-10-27): user decisions, verbatim: «En cuanto a mis decisiones, dejemos que se use la misma contrasena temporal como nueva, los decimales tratemolos con una coma con el fin de que se diferencien del punto de separacion en los miles y demas. y si, como te di una instruccion si hay algo que se pueda mejorar con shadcn y se pueda implementar y reutilizar hazlo.»
- L7 (browser, disposable stack `optiplant-e2efix2`, removed afterwards): Consultas shows `8,000 unidad` and history `10,000 unidad`; a minimum typed `2.5` is blocked by the input pattern and `1250,5` is sent as `"minimumQuantity":"1250.5"` and shown as `1.250,500`; unit factor `25,5` shows «1 Saco = 25,500 kg» and its edit prefill is `25,5`; the minimum prefill is `1250,5`; the calculator shows «2 Saco = 51,000 kg (factor 25,500).» and «1.234,25 kg = 1.234,250 kg (factor 1,000).»; the server rejection reads «La salida de 1.250,000 excede las existencias físicas disponibles (8,000).». The confirmation is an `alertdialog` titled «¿Desactivar la sucursal E2E-DLG?», focus starts on «Cancelar», Escape and Cancel send no request and return focus to «Desactivar sucursal» (fixed during verification: without a Radix trigger focus fell to `body`), and confirming deactivates (200).
- L8: full suites with real exit codes: `check_application.py backend` 72/72, `frontend` 44/44 + build, `check_repository.py` OK. Independent verifier: S1–S10 pass in application code, lockfile adds only the Radix dialog chain; 2 blockers in browser scripts, both caused by this change: a missing `.` in `check_inventory_browser.py:76` and `check_identity_browser.py` still stubbing `window.confirm`. One correction batch fixed both (dialog action clicked through `getByRole('alertdialog')`); recheck: the 9 embedded JavaScript blocks pass `node --check` and no `window.confirm` remains. Advisories accepted: prefilled inputs drop trailing zeros, typed thousands separators are rejected by design. The browser scripts were not executed end to end.
- L9 (2026-10-27): user authorized commits («Si hazlo»). Work units: report `d780695`; T1 `7da82ee`; T6 backend messages `e9d4251`; T7 dependency and dialog `705e351` (compiles alone: `tsc -p tsconfig.json --noEmit` exit 0 in an isolated worktree); T2, T3, T6 frontend and T7 usage `b81f429`; this log closes the feature.
