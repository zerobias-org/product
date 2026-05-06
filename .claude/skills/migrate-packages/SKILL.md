---
name: migrate-packages
description: Migrate the next batch of products to the gradle pipeline. Drops per-product build.gradle.kts marker, ensures .npmrc, runs full ./gradlew :<path>:gate (writes gate-stamp.json), fixes drift, major-bumps the version when applicable, commits per-product.
argument-hint: "[<vendor>/<product>...|<vendor>/<suite>/<product>...] [--batch=N] [--dry-run]"
---

# Migrate Product Packages

Per-repo companion to `/migrate-content-to-zbb` (which bootstrapped this repo onto gradle). Use this skill to migrate products **one at a time** within `org/product`. **Mixed-depth repo:**

| `parentType` | Path | Sample | npm name | `zerobias.package` |
|---|---|---|---|---|
| `vendor` | `package/<v>/<p>/` (depth 2) | `qualys/qualysplatform` | `@zerobias-org/product-<v>-<p>` | `<v>.<p>` |
| `suite`  | `package/<v>/<s>/<p>/` (depth 3) | `amazon/aws/s3` | `@zerobias-org/product-<v>-<s>-<p>` | `<v>.<s>.<p>` |

The validator (`build.gradle.kts:46-89`) reads `index.yml.parentType` at runtime and applies the matching formula. You don't need to tell it the depth — it figures it out.

## Trigger

```
/migrate-packages [<v>/<p> ...|<v>/<s>/<p> ...] [--batch=N] [--dry-run]
```

Examples:
- `/migrate-packages qualys/qualysplatform tanium/platform` — vendor-parented batch.
- `/migrate-packages amazon/aws/s3 amazon/aws/ec2 amazon/aws/iam` — suite-parented batch (group by parent suite to share failure modes).
- `/migrate-packages --batch=5 --dry-run` — preview the next 5 candidates.

## Pre-flight

1. `git status` — must be on a feature branch, not `main`.
2. Confirm gradle bootstrap: root `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle-ci.properties`, `.github/workflows/publish.yml` (uses `zbb-publish-reusable.yml@main`). If anything is missing, abort and direct the user to `/migrate-content-to-zbb`.
3. Identify candidates — directories WITHOUT `build.gradle.kts`. Check `MIGRATION_STATUS.md` if present, otherwise `find package -name index.yml | xargs dirname | sort -u`.
4. Confirm the **parent exists on the gradle line**:
   - vendor-parented product: `org/vendor/package/<v>/build.gradle.kts` should exist.
   - suite-parented product: `org/suite/package/<v>/<s>/build.gradle.kts` should exist.
   The dataloader looks up the parent at load time; if the parent isn't published yet, the integration step will fail.

## Per-product loop

For each product in the batch, do steps 1–6 in order, then commit and move on.

### 1. Drop the marker
Create `package/<path>/build.gradle.kts`:
```kotlin
plugins { id("zb.content") }
```
Same one-liner regardless of depth — the validator handles the formula switch via `index.yml.parentType`.

### 2. Ensure `.npmrc`
The validator requires `package/<path>/.npmrc`. Most products already have it; if not, copy from a sibling.

### 3. Run **full** `:gate` (NOT just `:validateContent`)
```bash
./gradlew :<vendor>:<product>:gate              # vendor-parented
./gradlew :<vendor>:<suite>:<product>:gate      # suite-parented
```
**Why full `:gate` matters:** the publish workflow's preflight rejects any product without a committed `gate-stamp.json` (`gate-stamp.json is missing or invalid — run zbb gate locally and commit the stamp before publishing`). The stamp is written by `:writeGateStamp` at the end of `:gate`. Running `:validateContent` alone produces NO stamp — the product will pass local file-checks but fail in CI.

`:gate` chains: `validate` → `lint` → `compile` → `test*` → `buildArtifacts` → `testIntegrationDataloader` → `writeGateStamp`. Without `NEON_API_KEY` / `NEON_PROJECT_ID` in env, `testIntegrationDataloader` is **skipped** (not failed); the stamp still gets written, and CI re-runs the dataloader test against an ephemeral Neon branch on push.

The validator surfaces drift one error at a time. Common fixes for products:

- **`index.yml.parentType` missing or wrong** — must be `vendor` or `suite`. The validator aborts with a clear message if neither.
- **`package.json name` mismatch** — must match the path-derived formula:
  - vendor-parented: `@zerobias-org/product-<v>-<p>`
  - suite-parented: `@zerobias-org/product-<v>-<s>-<p>`
- **`zerobias.package` mismatch** — vendor-parented: `<v>.<p>`; suite-parented: `<v>.<s>.<p>`. Legacy `auditmation.package` is accepted; rename to `zerobias.package`.
- **`vendorId` mismatch with parent vendor** — the dataloader rejects if `vendor.code != index.yml.vendorCode`. Run `npm install` in the product dir, then copy parent vendor's `id` from `node_modules/@zerobias-org/vendor-<v>/index.yml` into `index.yml.vendorId`.
- **Missing `.npmrc`** — see step 2.
- **Logo issues** — multiple files / magic-byte mismatch / size out of range / missing from `files` array. Logos are optional for products that don't ship one.
- **Duplicate `id` UUID** — `:validateUniqueIds` collision. Investigate which other product owns that UUID; the newcomer needs a fresh UUID via `uuidgen`. Existing UUIDs are stable.

Re-run `:gate` after each fix until it passes.

### 4. Major-bump version (conditional)
```bash
# package/<path>/package.json:
# 1.x.x → 2.0.0    (most lerna-era products)
# 0.x.x → 1.0.0    (rare)
# 2.x.x → no-op    (already on a major-bumped line — some products
#                    were bumped during a previous pass; leave alone)
```
Universal repo rule: every product's first gradle publish gets a major bump UNLESS already at 2.x. Some products in this repo are already at 2.x from an earlier pass and stay where they are. Pre-existing `1.x.x` → `2.0.0`.

### 5. (Optional) Re-run `:gate` after the version bump

### 6. Commit
One commit per product. Conventional commit format:
```
feat(product-<v>-<p>)!: migrate to gradle pipeline (<oldVer> → 2.0.0)
# or for suite-parented:
feat(product-<v>-<s>-<p>)!: migrate to gradle pipeline (<oldVer> → 2.0.0)
```
The `!` marks the major bump as breaking. Drop the `!` if no version change (already-2.x case).

Stage exactly: `package/<path>/build.gradle.kts`, `package/<path>/.npmrc` (if you added it), `package/<path>/package.json` (version bump), **`package/<path>/gate-stamp.json`** (mandatory — preflight rejects without it), and any drift fixes.

### 7. (After the batch) Verify on a feature branch
```bash
gh workflow run publish.yml --ref <branch>
```
Confirm `detect` lists exactly the products you bumped. On a feature branch, `version` (single-writer) is skipped and `publish` runs in pre-release mode.

## Picking the next batch

Order rules of thumb:
1. **Group by parent**. Suite-parented products with the same parent suite (`amazon/aws/*`, `microsoft/azure/*`) often share failure modes — fix once, apply across the batch.
2. **Vendor-parented first** for new contributors — simpler shape, no suite intermediary.
3. **Skip products whose parent isn't on the gradle line yet.** Check `org/vendor/package/<v>/build.gradle.kts` (vendor-parented) or `org/suite/package/<v>/<s>/build.gradle.kts` (suite-parented).
4. Cap each PR at ~10 products. Easier to review, easier to bisect.

## What NOT to do

- Do NOT change product `id` UUIDs — stable identifiers; changing one detaches DB rows.
- Do NOT change `vendorId` / `suiteId` to "fix" a mismatch — fix the wrong-side reference (vendor or product).
- Do NOT rename directories to make `package.json name` match. Metadata follows the directory.
- Do NOT skip the major bump for 1.x products — the bump reflects the publish-pipeline transition.
- Do NOT major-bump products already at 2.x — repo convention is no-op for already-bumped lines.
- Do NOT batch unrelated products into one commit. One commit per product keeps `git revert` precise.
- Do NOT mix vendor-parented and suite-parented products in a single PR unless they share the same parent vendor — review noise increases.

## Reference files

- `package/qualys/qualysplatform/`, `package/amazon/aws/s3/` — first migrated; use as drop-in references for vendor-parented and suite-parented respectively.
- `templates/index.yml`, `templates/package.json` — what a NEW product looks like.
- Root `build.gradle.kts:46-89` — validator with parentType switch (read once if you need to understand the formula).
- `org/vendor/`, `org/suite/` — parent dependency repos. Products' `vendorId` / `suiteId` must point to entries from there.
- `org/util/packages/build-tools/.../SchemaPrimitives.kt` — validator helpers and error message shapes.
- `com/platform/dataloader/src/processors/product/ProductFileHandler.ts` — source of truth for the parentType ↔ packageCode formula. Read if the validator's path/name-derivation behaviour is in question.

## See also

- `/migrate-content-to-zbb` — meta-repo skill that bootstrapped this repo. Use only when migrating a new repo onto gradle, not for per-product work here.
