# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Overview

Monorepo of ZeroBias product artifacts. Each `package/<vendor>/<code>/` (vendor-parented) or `package/<vendor>/<suite>/<code>/` (suite-parented) directory is one publishable product package.

The repo is on the **gradle + [zbb publish reusable workflow](https://github.com/zerobias-org/devops/blob/main/.github/workflows/zbb-publish-reusable.yml)** pipeline. Lerna and nx were removed in the post-migration cleanup — they are no longer the build or publish system. See `org/vendor` and `org/suite` for the canonical reference shapes.

## Development Commands

### Per-product

```bash
# File-shape validation only (validator philosophy: only checks things the dataloader doesn't):
./gradlew :<vendor>:<code>:validateContent              # vendor-parented
./gradlew :<vendor>:<suite>:<code>:validateContent      # suite-parented

# Full gate — validate → lint → compile → buildArtifacts → testIntegrationDataloader → writeGateStamp:
./gradlew :<vendor>:<code>:gate
./gradlew :<vendor>:<suite>:<code>:gate
```

`gate` writes `package/<path>/gate-stamp.json`. The publish workflow's preflight rejects any product without a committed stamp.

`testIntegrationDataloader` runs the dataloader against an ephemeral Neon Postgres branch. Without `NEON_API_KEY` / `NEON_PROJECT_ID` in env (vault refs in `zbb.yaml`), it's skipped locally; CI runs it on push against an ephemeral branch.

### Per-product helper

```bash
# Reset all `dependencies` versions in a package.json to "latest" (replaces lerna sync):
cd package/<vendor>/<code>
npm run correct:deps
```

### Repo-wide

```bash
# Cross-cut: fail if two products share an index.yml id UUID
./gradlew validateUniqueIds

# Info tasks (zbb CLI helpers):
./gradlew projectPaths       # emit project-to-directory mapping
./gradlew changedModules     # list products changed since last tag
```

## Product Structure

### Two parent types

| `parentType` | Path | Sample | npm name | `zerobias.package` |
|---|---|---|---|---|
| `vendor` | `package/<v>/<p>/` (depth 2) | `qualys/qualysplatform` | `@zerobias-org/product-<v>-<p>` | `<v>.<p>` |
| `suite` | `package/<v>/<s>/<p>/` (depth 3) | `amazon/aws/s3` | `@zerobias-org/product-<v>-<s>-<p>` | `<v>.<s>.<p>` |

The gate validator (`build.gradle.kts:50-93`) reads `index.yml.parentType` at runtime and applies the matching formula.

### Required files per product

- `index.yml` — product metadata (id, name, code, vendorCode, vendorId, parentType, etc.)
- `package.json` — npm name + `zerobias` block + the single `correct:deps` script
- `catalog.yml` — service catalog definition (dataloader fails without it)
- `.npmrc` — artifact-private registry config
- `build.gradle.kts` — `plugins { id("zb.content") }` (one-liner; validator handles the depth)
- `logo.svg` / `logo.png` / `logo.jpg` — optional; if present must match magic bytes for its extension, be 100B–5MB, and appear in `files` array

### package.json shape (per product)

```jsonc
{
  "name": "@zerobias-org/product-<v>-<p>",          // or product-<v>-<s>-<p>
  "version": "2.0.x",
  "files": ["index.yml", "catalog.yml", "logo.*"],
  "dependencies": {
    "@zerobias-org/vendor-<v>": "latest"            // or suite-<v>-<s>
  },
  "zerobias": {
    "package": "<v>.<p>",                            // or <v>.<s>.<p>
    "import-artifact": "product",
    "dataloader-version": "1.0.0"
  },
  "scripts": {
    "correct:deps": "tsx ../../../scripts/correctDeps.ts"   // depth-2; depth-3 uses ../../../../
  }
}
```

Legacy `auditmation` metadata key is still accepted; prefer `zerobias`.

### index.yml shape

- `id` — UUID, must be unique across the entire repo (enforced by `validateUniqueIds`)
- `name`, `code`, `description`, `url`, `status: active`
- `parentType: "vendor"` + `vendorCode` + `vendorId`
- OR `parentType: "suite"` + `vendorCode` + `vendorId` + `suiteCode` + `suiteId`
- `logo` URL — convention: `https://cdn.auditmation.io/logos/<v>-<p>.<ext>` (or `<v>-<s>-<p>.<ext>` for suite-parented)
- No `created` / `updated` on new packages — the dataloader stamps them server-side (legacy packages may carry real ISO timestamps)
- `factoryTypes: ["software"]` typical; `hostingTypes: []`; `cpeProducts: []` unless known; `segments: []` until catalog wiring assigns them

Vendor IDs come from `org/vendor/package/<v>/index.yml`. Suite IDs come from `org/suite/package/<v>/<s>/index.yml`.

## Validator philosophy

The dataloader is the source of truth for schema rules (UUID format, code regex, status enum, URL parse, `vendorId`/`suiteId` lookup, etc.). The gate validator only enforces what the dataloader CANNOT or DOES NOT see:

1. **Filesystem ↔ npm-name ↔ `zerobias.package` triangulation** — dataloader reads `zerobias.package` but never the npm `name` and has no view of the directory layout
2. **Logo file correctness** — dataloader only records the URL; doesn't crack the bytes
3. **Repo-wide unique `id` UUIDs** (`:validateUniqueIds`) — dataloader sees one product at a time

This avoids drift when the dataloader tightens. Every repo on the gradle pipeline keeps the philosophy comment at the top of `build.gradle.kts` so future readers don't add validations the dataloader will do better.

## Creating a new product

Say "add product X" / "make product X" (or run `/create-product`) — the
[create-product skill](.claude/skills/create-product/SKILL.md) handles the
whole org-first flow; a ZeroBias task id is optional. Headless works too:
`claude -p "make product x"` pre-flights credentials, runs to org load, and
stops there (sign-off and PR stay human).

Hard prerequisites live in the
[`prerequisites` skill](.claude/skills/prerequisites/SKILL.md) — run
`/prerequisites` to pre-flight the repo (tools, MCPs, credentials — the
API key must be an **org owner** key; member keys can't load artifacts to
the org). If one is missing: **install it or wait — never work around it**
(no substitute tooling, no alternative paths).

The content SDLC (the skill owns the details — don't restate them here):

1. scaffold → `zbb --slot <slot> gate` (never bare `./gradlew`) → commit `gate-stamp.json`
2. `publishOrg` → load into YOUR org → verify → 🙋 explicit user sign-off
3. only then PR → base **`main`** (this repo's PR base — unlike vendor/suite, which use `dev`)

**No ZeroBias org?** (external contributors): stop after the gate and open
the PR against `main` — maintainers run the org verification on their side.
See [CONTRIBUTING.md](CONTRIBUTING.md).

One-time credential setup (all three credential homes, check-first):
`./scripts/setup-org-credentials.sh` — run it yourself in a normal
terminal; `--launch` starts Claude Code through your zbb slot (the
session and its MCPs use the slot's identity).

## Migrating remaining lerna-era packages

Use the skill: `/migrate-packages [<v>/<p> ...|<v>/<s>/<p> ...]`. See [.claude/skills/migrate-packages/SKILL.md](.claude/skills/migrate-packages/SKILL.md). The skill drops the marker, runs `:gate`, applies the major bump where needed (`1.x → 2.0`, `0.x → 1.0`, `2.x → no-op`), commits per-product.

## Branches

- `main` — default, all PRs target it
- `dev`, `qa`, `uat` — environment branches kept in sync by the publish workflow's `sync` job after every successful main publish

## Commit format

[Conventional Commits](https://www.conventionalcommits.org/), enforced by `commitlint` (`.commitlintrc.json`).

```
feat(product-<v>-<p>): <subject>
feat(product-<v>-<s>-<p>)!: <subject> (<oldVer> → <newVer>)
```

Use `!` for major version bumps. Scopes: `product-<...>`, `bundle`, `validator`, `repo-cleanup`.

## CI/CD

Single workflow: `.github/workflows/publish.yml` — a thin wrapper around `zerobias-org/devops/.github/workflows/zbb-publish-reusable.yml@main`. Triggered on `push` to main/qa/dev/uat (paths: `package/**`, `.github/workflows/publish.yml`) and on `workflow_dispatch` (optional `product` input).

The reusable workflow's jobs:
1. **detect** — diff to find changed products
2. **version** (main only, single-writer) — bump patch version, commit
3. **publish** (matrix) — per-product publish to npm + GHCR
4. **update-bundle** (main only, after publish success) — refresh `bundle/package.json` deps from npm, patch-bump bundle, publish
5. **sync** — propagate main → uat → qa → dev

For pre-release validation on a feature branch:
```bash
gh workflow run publish.yml --ref <branch>
```

## Bundle

`bundle/` ships `@zerobias-org/product-bundle` — an aggregate npm package listing every product as a dependency. Auto-refreshed by the `update-bundle` job; no manual maintenance.

## ZeroBias Task Integration

For creating products from ZeroBias tasks: `/create-product [task-id]`.

**Dependency Chain:** `vendor → suite → product`. Products require either a vendor (vendor-parented) or a suite (suite-parented). Check/create the full chain first.

### Key APIs

```javascript
// Check if vendor exists (REQUIRED before product) — the portal.*.search
// ops found in older docs do NOT exist; vendorId comes from the result's `id`
zerobias_execute("store.Vendor.get", { vendorCode: "vendor" })

// Check if suite exists (REQUIRED for suite-parented products) — suiteId from `id`
zerobias_execute("store.Suite.get", { vendorCode: "vendor", suiteCode: "suite" })

// Check if product already exists (scan the listing for the code)
zerobias_execute("store.Vendor.listProducts", { vendorCode: "vendor" })

// Get your party ID for assignment
zerobias_execute("platform.Party.getMyParty", {})

// Transition task to in_progress (use transitionId, NOT status)
zerobias_execute("platform.Task.update", {
  id: taskId,
  updateTask: {
    assigned: partyId,
    transitionId: "7f140bbe-4c10-54ac-922c-460c66392fad"
  }
})
```

### Workflow Transitions

| Transition | Target Status | ID |
|------------|---------------|-----|
| Start | in_progress | `7f140bbe-4c10-54ac-922c-460c66392fad` |
| Peer Review | awaiting_approval | `f017a447-0994-594d-9417-39cbc9a4de88` |
| Accept | released | `1d2e9381-f609-5e26-8bc6-7bbb65a9048d` |

Always get actual IDs from `task.nextTransitions`.

---

## Related Documentation

- [CONTRIBUTING.md](CONTRIBUTING.md) — the two contribution lanes (with/without a ZeroBias account)
- [.claude/skills/create-product/SKILL.md](.claude/skills/create-product/SKILL.md) — full new-product walkthrough (org-first SDLC)
- [.claude/skills/create-product/templates.md](.claude/skills/create-product/templates.md) — exact file shapes for both parent types
- [zerobias-org/vendor](https://github.com/zerobias-org/vendor) — vendor repo (parent dependency); sibling on the same gradle pipeline
- [zerobias-org/suite](https://github.com/zerobias-org/suite) — suite repo (parent for suite-parented products)

---

## Sessions, credentials & MCPs — slot-first

<!-- Synced section: identical in vendor, suite, product, module.
     The zerobias meta-repo's CLAUDE.md carries the same rules in its
     own words. Edit in one repo, copy to all. -->

All org credentials (platform ORG key, registry key, org/env identity)
live in a **zbb slot**; Claude Code sessions are launched THROUGH the
slot so the committed `.mcp.json` templates (`${VAR}` refs — no
secrets) and the zb `env` profile resolve that identity.

- **One-time setup (per org/env):** the user runs
  `./scripts/setup-org-credentials.sh` themselves in a normal terminal
  (never inside a Claude session). Check-first and re-runnable: it
  creates the slot (`<env>-<org-prefix>`), stores the keys, and wires
  `~/.npmrc` + the zb profile.
- **Launch:** `./scripts/setup-org-credentials.sh --launch [args…]`,
  or `zbb --slot <slot> --stack <stack> exec claude` from anywhere
  (`<stack>` = this repo's `zbb.yaml` `name:` short form, e.g.
  `vendor` in the vendor repo); from this repo's root plain
  `zbb --slot <slot> exec claude` works too (cwd infers the stack).
  NEVER launch stackless from outside a `zbb.yaml` directory: a slot
  holds NO user vars of its own (only `ZB_SLOT*` identity) — every
  credential is **stack-scoped**, stored per stack inside the slot,
  and the setup script seeds every content stack it finds with the
  same creds. Add `--continue` to resume the previous session under
  another slot (sessions are keyed by cwd, not by slot).
- **Missing MCP tools / 401 / `MISSING_ENV_VAR` / `NOT SET`** means
  the session wasn't launched through a slot WITH a stack context.
  Check inside the session: `echo ${ZB_SLOT:-no-slot} ${ZB_ORG_ID:-no-stack}`
  (`no-slot` = not launched through zbb; `no-stack` = launched
  stackless). Fix the launch — exit and relaunch; `/mcp` reconnect can
  never pick up new env (it is captured once at claude startup). Do NOT
  register MCPs with pasted literal keys (a baked key silently
  overrides every slot identity, connecting as the wrong org) and do
  NOT export creds into the session as a workaround.
- **Multi-org / multi-env = one slot each**, chosen at launch time;
  switching identity means restarting claude through the other slot
  (env is read once at startup).

Deep dive: the meta-repo's
[docs/MCPs.md](https://github.com/zerobias-org/zerobias/blob/main/docs/MCPs.md).

## Windows — WSL2 only

Everything here runs only on Ubuntu (`zbb` fails on native Windows).
On Windows, work inside WSL2 end-to-end — user walkthrough:
[docs/WindowsWSLSetup.md](https://github.com/zerobias-org/zerobias/blob/main/docs/WindowsWSLSetup.md).

- **If this session runs on NATIVE Windows** (prompt `PS C:\`, paths
  under `C:\` or `/mnt/c/...`): your ONLY job is getting WSL2 + Ubuntu
  installed. Refuse repo work — no cloning, editing, git, or builds —
  and point the user to their WSL session. Never relay work between a
  Windows agent and a WSL agent.
- **In WSL:** logins and credential setup happen in the Ubuntu
  terminal (`gh auth login`, claude's first-run login,
  `setup-org-credentials.sh`). Once setup is green, offer Remote
  Control (`/remote-control`, or `--launch --remote-control`) to
  continue from the Claude desktop / mobile app.
