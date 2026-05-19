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
- `name`, `code`, `description`, `url`, `status: "verified"`
- `parentType: "vendor"` + `vendorCode` + `vendorId`
- OR `parentType: "suite"` + `vendorCode` + `vendorId` + `suiteCode` + `suiteId`
- `logo` URL — convention: `https://cdn.auditmation.io/logos/<v>-<p>.<ext>` (or `<v>-<s>-<p>.<ext>` for suite-parented)
- `created` / `updated` — real ISO timestamps (no placeholder `00:00:00.000Z`)
- `factoryTypes: ["software"]` typical; `hostingTypes: []`; `cpeProducts: []` unless known

Vendor IDs come from `org/vendor/package/<v>/index.yml`. Suite IDs come from `org/suite/package/<v>/<s>/index.yml`.

## Validator philosophy

The dataloader is the source of truth for schema rules (UUID format, code regex, status enum, URL parse, `vendorId`/`suiteId` lookup, etc.). The gate validator only enforces what the dataloader CANNOT or DOES NOT see:

1. **Filesystem ↔ npm-name ↔ `zerobias.package` triangulation** — dataloader reads `zerobias.package` but never the npm `name` and has no view of the directory layout
2. **Logo file correctness** — dataloader only records the URL; doesn't crack the bytes
3. **Repo-wide unique `id` UUIDs** (`:validateUniqueIds`) — dataloader sees one product at a time

This avoids drift when the dataloader tightens. Every repo on the gradle pipeline keeps the philosophy comment at the top of `build.gradle.kts` so future readers don't add validations the dataloader will do better.

## Creating a new product

Use the skill: `/create-product [task-id]`. See [.claude/skills/create-product/SKILL.md](.claude/skills/create-product/SKILL.md). The skill bootstraps the directory, copies templates, looks up parent IDs, runs `./gradlew :path:validateContent`, and walks through the catalog.yml step.

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
// Check if vendor exists (REQUIRED before product)
zerobias_execute("portal.Vendor.search", { searchVendorBody: { search: "vendor name" }})

// Check if suite exists (REQUIRED for suite products)
zerobias_execute("portal.Suite.search", { searchSuiteBody: { search: "suite name" }})

// Check if product already exists
zerobias_execute("portal.Product.search", { searchProductBody: { search: "product name" }})

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

**Orchestration:**
- [DEPENDENCY_CHAIN.md](../../docs/orchestration/DEPENDENCY_CHAIN.md) — strict dependency rules
- [TASK_MANAGEMENT.md](../../docs/orchestration/TASK_MANAGEMENT.md) — task API patterns
- [API_REFERENCE.md](../../docs/orchestration/API_REFERENCE.md) — quick API reference

---

## Related Documentation

- [Root CLAUDE.md](../../CLAUDE.md) — meta-repo guidance
- [ContentArtifacts.md](../../ContentArtifacts.md) — content catalog system
- [org/vendor/CLAUDE.md](../vendor/CLAUDE.md) — vendor repo (parent dependency)
- [org/suite/CLAUDE.md](../suite/CLAUDE.md) — suite repo (parent dependency for suite-parented products)
- [com/platform/dataloader/CLAUDE.md](../../com/platform/dataloader/CLAUDE.md) — the dataloader processors
