---
name: create-product
description: >-
  Create a new product package in the zerobias-org catalog and take it through
  the full content SDLC — scaffold → gradle gate → publishOrg + org load →
  user verifies the org artifact → PR to main only after explicit sign-off.
  USE THIS when the user says "add product X", "register <vendor>'s <tool> as
  a product", "make product X", or a ZeroBias task asks for a product package.
  Standalone: works in this repo alone (no meta-repo), and no platform task is
  required (task-driven mode is optional).
---

# create-product — new product package, org-first SDLC

Products sit below vendors (and optionally suites) in the catalog
dependency chain (vendor → suite? → **product** → …). This skill produces
ONE product package and delivers it **org-first**: the default deliverable
is the product loaded into the user's own org; the PR to `main` happens
only after the user signs off on the org-loaded result.

```
Phase 0 prerequisites (hard gate — /prerequisites must report READY)
Phase 1 resolve + existence check (product AND its vendor/suite parents)
Phase 2 branch (from main)
Phase 3 scaffold + author content
Phase 4 gate                        ← git add BEFORE gating
Phase 5 publishOrg + org load
Phase 6 user verifies org artifact  ← 🙋 explicit sign-off required
Phase 7 PR --base main              ← only after sign-off
```

**Two parent types (decide in Phase 1, before scaffolding):**

| `parentType` | Path | npm name | `zerobias.package` |
|---|---|---|---|
| `vendor` | `package/<v>/<p>/` | `@zerobias-org/product-<v>-<p>` | `<v>.<p>` |
| `suite` | `package/<v>/<s>/<p>/` | `@zerobias-org/product-<v>-<s>-<p>` | `<v>.<s>.<p>` |

Suite-parent only when the vendor genuinely markets the product as part of
that family (the suite must already exist in the catalog — this skill never
invents suites; use the suite repo's `/create-suite` first).

**Modes.** Default is **request-driven**: the user names a vendor (+
optional suite) + product; no platform task needed. If the user references
a ZeroBias task (UUID or task name), additionally follow the **task-driven
appendix** at the end.

**Headless runs (`claude -p "make product x"`).** Same flow, three hard rules:
- **Pre-flight first**: run the `prerequisites` skill (Phase 0) before
  touching anything. If anything is missing, print the exact setup
  instructions and exit — never fail mid-flow.
- **The run ENDS after Phase 5** (org load). Print what was created, the
  verification link, and: *"verify the org artifact, then run
  `claude -p 'open the PR for product <vendor>/<code>'` (or continue
  interactively)"*. Phases 6–7 are human-gated and never run headless.
- **Decision forks stop the run**: product already exists, parent
  vendor/suite missing, no official logo found, gate conflict → print a
  structured report of the state and the decision needed, exit cleanly,
  change nothing further.

**Skill-vs-reality conflicts.** If observed tool behavior contradicts this
skill, STOP: verify against the primary source (`settings.gradle.kts`,
build-tools source in `util`, the workflow YAML), act on what the source
says, and queue a fix to this skill in the same session — never force
reality to match stale text.

## Phase 0 — prerequisites (hard gate)

Invoke this repo's [`prerequisites` skill](../prerequisites/SKILL.md)
(`/prerequisites`) and get `READY` before ANYTHING else — interactive or
headless. If something is missing there are exactly two permitted actions:
install it (with consent) or stop and wait. Never work around it — no
substitute tooling, no raw HTTP instead of the `zb` MCP, no partial
continuation.

This gate applies for the WHOLE flow, not just at the start: if any
prerequisite fails mid-flow (401s, expired token, org load refused, tool
vanished), treat it as a prerequisite regression — STOP the phase you're
in, re-run `/prerequisites`, and resume only from `READY`. Never improvise
past a mid-flow credential failure.

## Phase 1 — resolve inputs + existence check

Needed: **vendor**, **optional suite**, and **product name** (natural
language). Derive:
- `vendorCode` — must match an existing vendor package code.
- `suiteCode` — only for suite-parented products; must match an existing
  suite of that vendor.
- `productCode` — lowercase, matching `^[\d_a-z]+$`; prefer plain lowercase
  alphanumeric (the UI's `vspCodeValidator` rejects underscores).
- Official product URL and logo URL (research if not provided).

Check the chain via the `zb` MCP `store` ops (the `portal.*.search` ops
found in older docs do NOT exist — if an op errors as unknown, discover
the current name with `zerobias_search("product")` and stay within
`store.*`):

```
zerobias_execute("store.Vendor.get", { vendorCode: "<vendorCode>" })
    // 404 = vendor missing → STOP; create it first (vendor repo's
    //        /create-vendor), org-first into the SAME target org.
    // Success → keep result.id: it is the index.yml vendorId.
zerobias_execute("store.Suite.get", { vendorCode: "<vendorCode>",
                                      suiteCode: "<suiteCode>" })
    // suite-parented only; 404 = suite missing → STOP; /create-suite
    //        first. Success → keep result.id: it is the suiteId.
zerobias_execute("store.Vendor.listProducts", { vendorCode: "<vendorCode>" })
    // scan for <productCode> — present = product exists → STOP and ask
    // the user what to do (update / nothing). Suite-parented: use
    // store.Suite.listProducts with vendorCode + suiteCode.
```

An **org-only parent is fine** as the dependency: the product's
`dependencies` entry resolves via the npm registry, and a first-ever
`publishOrg` of a parent force-assigns the `latest` dist-tag to its rc.
Verify the parent is loaded in the TARGET org (the `store.*.get` calls
above run against it).

Also check locally: `ls package/<vendorCode>/` (and
`package/<vendorCode>/<suiteCode>/`). If the product exists (platform or
local), STOP and ask the user what to do (update / nothing).

The `zb` MCP is a hard prerequisite (see Prerequisites) — do NOT substitute
raw HTTP calls if it's missing; stop and have it installed instead.

## Phase 2 — branch first (never commit on main)

```bash
git fetch origin
git switch -c feat/product-<vendorCode>-<productCode> origin/main
# suite-parented: feat/product-<vendorCode>-<suiteCode>-<productCode>
```

⚠ Unlike the vendor/suite repos (which PR against `dev`), **this repo's
PRs target `main`** — `main` is the default branch and the publish
workflow's sync job propagates main → uat → qa → dev. Branch from
`origin/main`, PR back to `main`.

## Phase 3 — scaffold + author

```bash
./scripts/createNewProduct.sh package/<vendorCode>/<productCode>
# suite-parented: ./scripts/createNewProduct.sh package/<vendorCode>/<suiteCode>/<productCode>
echo 'plugins { id("zb.content") }' > package/<path>/build.gradle.kts
```

The scaffold script creates the directory, copies the templates, rewrites
them for the parent type (name, dependency, script depth, `parentType`,
suite fields), and fills `{code}`/`{vendor}`/`{suite}`/`{id}`; you fill
the remaining `{name}`/`{description}`/`{url}`/`{vendorId}` (and
`{suiteId}`) placeholders. **Verify the scaffold immediately**: the file
set from `ls -A templates/` must all be present in `ls -A package/<path>/`
(dotfiles included — `.npmrc`!), and spot-check the layout against a
recently-gated product (e.g. `ls -A package/github/github`). Required
files:

```
package.json          # @zerobias-org/product-<v>-<p>  (or -<v>-<s>-<p>)
index.yml             # product metadata
catalog.yml           # service catalog definition (dataloader fails without it)
logo.{svg|png|jpg}    # official product logo (SVG preferred, unmodified)
build.gradle.kts      # one-line zb.content marker (REQUIRED for publish detect)
.npmrc                # REQUIRED — validator hard-fails with ".npmrc missing"
```

Exact file shapes, key conventions, and the catalog.yml template live in
[templates.md](templates.md). Non-negotiables:

- Never hand-edit `version` after creation — CI owns bumps.
- The parent dependency is the ONLY dependency: `vendor-<v>` for
  vendor-parented, `suite-<v>-<s>` for suite-parented.
- `zerobias.package` MUST equal the dot-joined directory path under
  `package/` (`<v>.<p>` or `<v>.<s>.<p>`).
- `vendorId` (and `suiteId`) MUST be the parent's real UUID — take them
  from the Phase 1 `store.*.get` results (`id` field). The dataloader
  looks parents up by id and REJECTS the load on a code mismatch.
- **Set `zerobias.orgId: "<target-org-uuid>"` already now, before the
  first gate.** With orgId present the gate's Neon step seeds the
  ephemeral branch with the org and runs the load org-scoped, matching
  how org-scoped tokens authorize; absent → the package is treated as
  global-catalog and org-scoped tokens can 401 the step. The gate-stamp's
  sourceHash does not cover `package.json`, so setting (and later
  deleting) orgId never invalidates the stamp.
- **Logo**: download the official asset (vendor press-kit / brand pages).
  Never modify SVG content. If none found, note it in the PR.

### Enrichment — what separates a minimal load from a complete product

The scaffold gives a minimal loadable package. A complete product (model:
`package/github/github`) also carries — fill what you can verify, leave
the rest empty rather than guessing:

- `index.yml` fields: `apiDocsUrl` (official API docs), `factoryTypes`
  (`software` default; also `hardware`/`service` where true),
  `hostingTypes` (`saas`, `onprem`, …), `cpeProducts` (entries from the
  NVD CPE dictionary, `<vendor>:<product>` form), `segments` (segment
  UUIDs from the `segment` catalog).
- `editions/` and `components/` directories and `supports.yml` — authored
  per the catalog content model; the template's `files` list already
  publishes them when present (nothing extra to wire).
- Segment associations, compliance features, and control links span
  OTHER repos — that full wiring belongs to the meta-repo
  `/create-product` orchestrator, not this skill. When running
  standalone, note unfilled enrichment in the PR body so reviewers see
  it's deliberate.

## Phase 4 — gate (git add FIRST, always via zbb)

All builds go through `zbb` — **never invoke `./gradlew` directly**. Only
zbb injects the slot env (token, URLs) into the build; a bare gradle run
silently misses it.

```bash
ls -A package/<path>                     # completeness check BEFORE first gate:
                                         # all required files incl. DOTFILES
                                         # (.npmrc!) — a miss costs a gate cycle
git add package/<path>/                  # BEFORE gating — the gate-stamp's
                                         # sourceHash enumerates git ls-files;
                                         # untracked files are invisible to it
zbb --slot <slot> stack add "$(git rev-parse --show-toplevel)"  # once per slot,
                                         # else "no added stack is reachable"
cd "$(git rev-parse --show-toplevel)/package/<path>" && zbb --slot <slot> gate
zbb gate --check                         # validate the stamp (no slot needed)
```

⚠ Write EVERY `zbb gate` / `publishOrg` as `cd <absolute-path> && zbb …`
in ONE command — never rely on inherited shell cwd (background shells
reset it, and a repo-root `publishOrg` targets the wrong project).

`gate` = `validateContent` (schema + package-identity + logo checks) +
the Neon dataloader step — which runs **iff `ZB_TOKEN` is present** in the
slot env. With the org-owner setup from Phase 0 it runs for real, resolving
the parent dependency from the registry. On success it writes
`package/<path>/gate-stamp.json` — **commit that file**; CI's publishGuard
rejects publishes without a valid committed stamp. CI does not rerun your
tests — it validates the committed stamp.

If you gated before adding new files, re-gate after `git add`.
Legacy `npm install` / `npm shrinkwrap` / `npm run validate` are gone —
zbb owns the lifecycle. Don't commit a shrinkwrap.

## Phase 5 — publishOrg + load into the user's org

Publishes an org-private rc version (`<X.Y.Z+1>-rc.<orgIdStripped>.<n>`,
computed by zbb — never hand-authored) and queues a dataloader job into
the target org — no PR, no shared catalog involved.

1. Confirm the target is set in `package.json`: `"zerobias": { …, "orgId":
   "<org-uuid>" }` — already done in Phase 3 (before the gate), where it
   belongs; set it now only if it was somehow missed.
2. Environment — must be in the **slot/stack env** (a plain shell `export`
   does not reach the gradle build); the `prerequisites` skill and
   `./scripts/setup-org-credentials.sh` own the full reference
   (`ZB_API_KEY` org key, `ZB_TOKEN` registry key, `ZB_PLATFORM_URL`,
   `NPM_CONFIG_TAG`, and the DATALOADER_SERVICE_URL leave-unset rule).
   ⚠ **Slot-env mutation gate:** changing any slot value that redirects
   traffic or identity (URLs, `ZB_ORG_ID`, keys) MID-FLOW requires showing
   the user the evidence and the exact `env set`, and getting confirmation
   BEFORE running it — even when source code proves the change correct.
   Never silently repoint an environment.
3. Run as ONE command with an absolute path — never rely on inherited cwd:
   `cd <repo>/package/<path> && zbb --slot <slot> publishOrg`
   (never bare `./gradlew` — zbb injects the slot env)
4. Verify it landed (by **codes**, not UUID): re-run the Phase 1 product
   listing (`store.Vendor.listProducts` / `store.Suite.listProducts`) and
   confirm the product appears — and show the user in the app catalog.
5. **Iterate here**: edit → re-gate → re-run `zbb --slot <slot> publishOrg`
   until the user is satisfied. Loading happens ONLY through
   `zbb publishOrg` — never POST the dataloader API directly, and never
   use the MCP to load artifacts (MCP ops are for reads/verification
   only).

⚠ **Dist-tag landmine on iteration.** The dataloader's load guard compares
the requested version against the target env's dist-tag, falling back to
`latest`. A FIRST `publishOrg` of a package works because the registry
force-assigns `latest` to that rc. But subsequent rc's only get the
`NPM_CONFIG_TAG` tag (`dev`) while `latest` stays on the first rc — so the
org load of `-rc.<org>.1+` can be REJECTED ("greater than latest"). Until
build-tools moves the env tag itself, the fix is a one-time
`npm dist-tag add <pkg>@<new-rc> latest --registry=https://pkg.zerobias.org`
(run by the user) before re-loading.

Notes: org users can only queue org-private (`-rc.<org>`) loads — a plain
catalog-semver load is 403 (platform-admin only). The `zb.content` plugin
resolves the released registry line — org loads need build-tools
≥ **1.0.137** (verify: `./gradlew buildEnvironment | grep build-tools`;
a stale locally-published copy in `~/.m2` can shadow the release).

## Phase 6 — user verification + sign-off  ⭐

Show the user the org-loaded product (catalog UI or the product listing
result). ⚠️ The logo will render BROKEN in the UI at this stage —
`cdn.auditmation.io/logos/<v>-<p>.<ext>` (or `<v>-<s>-<p>.<ext>`) 404s
until the product reaches `main`, where the publish workflow uploads it
(the dataloader never touches the CDN). Tell the user up front; have them
judge the data fields (name, description, parent binding, url, catalog
entry), and verify the logo locally (it must be inside the published rc
tarball). **Do NOT proceed to the PR until the user explicitly confirms**
(e.g. "looks good, ship it"). Silence or further tweak requests are NOT
sign-off — if unclear, ask. Headless runs never reach this phase — they
stop after Phase 5 by design.

## Phase 7 — PR to main (after sign-off only)

1. Flip ownership to the shared catalog: **delete `zerobias.orgId` from
   `package.json`**. No re-gate needed — the gate-stamp's sourceHash
   covers the `files` payload, not `package.json`. Leftover
   `-rc.<org>.<n>` npm versions don't collide with catalog semver.
2. Commit — selective staging, conventional message, no co-authors:

```bash
git add package/<path>/
git commit -m "feat(product-<vendorCode>-<productCode>): add <Product Name>"
git push -u origin feat/product-<vendorCode>-<productCode>
```

3. PR against **main** (this repo's PR base — see Phase 2):

```bash
gh pr create --base main \
  --title "feat(product-<vendorCode>-<productCode>): add <Product Name>" \
  --body "…summary, parent type + chain, validation checklist (gate ✓,
          gate-stamp committed ✓, org-loaded + user-verified ✓), and
          anything needing SME review (placeholder logo, parent-type
          judgment, naming calls)…"
```

The PR is how content reaches the shared catalog; the org-private artifact
from Phase 5 stays in the user's org either way. ⚠ If the product's parent
vendor/suite is itself org-only, the product PR must WAIT until the parent
has merged and published to the shared catalog — CI resolves the `latest`
parent dependency from the registry, and a shared-catalog product must not
depend on an org-private parent rc.

## Common issues

**First rule for any SERVER-side failure** (dataloader jobs, platform
calls): re-run the identical command ONCE before diagnosing or escalating —
pod-side state changes independently of your session, and a retry is far
cheaper than a wrong escalation.

- **`stack add` from a git worktree fails "Stack 'product' already exists"** →
  harmless: zbb resolves stacks by `zbb.yaml` name, not path, so a worktree
  of an added repo reaches the slot env without any `stack add`. Skip it.
- **Publish workflow skips the product** → missing `build.gradle.kts`
  marker; add the one-liner and push.
- **`index.yml.parentType must be 'vendor' or 'suite'`** → set `parentType`
  (the scaffold does this — check for a hand-edit).
- **`package.json name expected '@zerobias-org/product-<…>'`** → name must
  match the directory layout; fix the name, never rename the dir.
- **`zerobias.package expected '<…>'`** → must equal the dot-joined
  directory path.
- **`logo.svg doesn't look like SVG`** → magic bytes don't match the
  extension (HTML error page saved as .svg is the classic); re-source.
- **`duplicate index.yml ids across the repo`** → UUID collision; generate
  fresh with `uuidgen`.
- **Parent dependency unresolvable during gate/publish** → the parent
  package has no published version visible to your `ZB_TOKEN` (or the org
  rc's `latest` tag was reassigned). Check with
  `npm view @zerobias-org/vendor-<v> dist-tags` (or `suite-<v>-<s>`) from
  the package dir.
- **`testIntegrationDataloader` errors locally** (instead of skipping) →
  slot misconfigured; check the stack is added to the slot and the slot env
  holds `ZB_TOKEN` + `ZB_PLATFORM_URL` (`zbb --slot <slot> env get … | tail -n1`
  from INSIDE the repo — zbb may prefix a vault banner, value = last line).
- **`publishOrg` 401 on `/dana/me` or the org load is refused** →
  the ORG key (`ZB_API_KEY`, fallback `ZB_TOKEN`) is not an org OWNER key
  of the org in `zerobias.orgId` — member keys authenticate but cannot
  load; non-prod targets REQUIRE `ZB_API_KEY` (see prerequisites).
- **Org load rejected "greater than latest"** → the dist-tag landmine in
  Phase 5 — the new rc isn't covered by the env tag / `latest`.
- **`dataloaderOrgJob` fails with `npm … 401 Unauthorized`** (server-side,
  `/root/.npm` in the log) → the TARGET env's dataloader pod fetches the
  package with its OWN `ZB_TOKEN` — no client-side change can fix it.
  Retry `publishOrg` cheaply first; if it persists, escalate to platform
  infra.
- **`publishOrg` rejects the name** → the package name/code already exists
  in the shared catalog; org-publish only works for brand-new / org-owned
  names.

## Task-driven appendix (only when the user references a ZeroBias task)

- Fetch: `platform.Task.get` (UUID). Task code is not searchable.
- Assign + start: `platform.Party.getMyParty` → `platform.Task.update` with
  `assigned` (party id), `customFields` (`artifactType: product`, `repoUrl`,
  `branchName`), and the Start transition — **always take transition IDs
  from `task.nextTransitions`**, never hardcode them.
- Comment progress at start and completion (`platform.Task.addComment`).
- After the PR: transition to Peer Review. Link to a parent task with
  `platform.Resource.linkResources` (`fromResource`/`toResource`) if this
  product was created as a dependency (e.g. for a connector).

## References (this repo only)

- [`CLAUDE.md`](../../../CLAUDE.md) — repo conventions, publish workflow,
  validator philosophy, parent-type table.
- [templates.md](templates.md) — exact file shapes for both parent types.
- [`scripts/createNewProduct.sh`](../../../scripts/createNewProduct.sh) —
  scaffold script.
