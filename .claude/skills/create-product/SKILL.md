---
name: create-product
description: Create a new product package in the ZeroBias product catalog, optionally from a platform task. Use when adding vendors, services, or tools to the product registry.
argument-hint: "[task-id-or-name]"
disable-model-invocation: true
---

Create a new product package in the ZeroBias product catalog, optionally driven by a ZeroBias platform task.

## Usage

```
/create-product [task-id-or-name]
```

If a task ID or name is provided, extract artifact details from the task. Otherwise, ask the user for the required information.

## Workflow

### Phase 1: Gather Requirements

**From a ZeroBias task:**
```javascript
const task = zerobias_execute("platform.Task.get", { id: taskId })
// Extract: vendor, suite (optional), product code, name, description, source URL
```

**From user input, collect:**
- Vendor code (e.g., `github`, `microsoft`, `oasisopen`)
- Suite code (only for suite products, e.g., `azure`, `365`, `cosai`)
- Product code (e.g., `github`, `entra`, `mcp_security`)
- Product name (display name)
- Product description
- Product URL (official page)

### Phase 2: Dependency Check

**CRITICAL: Verify dependencies exist before creating the product.**

```javascript
// Check vendor
const vendors = zerobias_execute("portal.Vendor.search", {
  searchVendorBody: { search: vendorCode }
})

// Check suite (if suite product)
if (suiteCode) {
  const suites = zerobias_execute("portal.Suite.search", {
    searchSuiteBody: { search: `${vendorCode} ${suiteCode}` }
  })
}
```

If dependencies are missing, create them first following the dependency chain:
```
vendor → suite → product
```

### Phase 3: Check Repository State

```bash
cd org/product  # from meta-repo root
git fetch origin
git checkout main
git pull origin main
git checkout -b feat/{vendor}-{code}
```

### Phase 4: Create Package Files

See [templates.md](templates.md) for complete file templates.

**Directory structure:**
```
# Vendor product
package/{vendor}/{code}/

# Suite product
package/{vendor}/{suite}/{code}/
```

**Required files:** `.npmrc`, `package.json`, `index.yml`, `catalog.yml`, logo file

**Key conventions:**
- Metadata key: `zerobias` (preferred; `auditmation` still accepted for backwards compatibility)
- Dataloader version: `"1.0.0"`
- Scripts use `tsx` (not `ts-node`)
- Starting version: `"1.0.0-rc.1"`
- `catalog.yml` MUST be in the `files` array
- Script paths must match directory depth (3 levels for vendor, 4 for suite)

### Phase 5: Install and Validate

```bash
cd package/{vendor}/{code}                              # or {vendor}/{suite}/{code}
npm install                                             # resolves the parent vendor/suite dep so ID lookup works

# Drop the gradle marker
echo 'plugins { id("zb.content") }' > build.gradle.kts

# From repo root: run the full gate (validate → compile → buildArtifacts → testIntegrationDataloader → writeGateStamp)
cd <repo-root>
./gradlew :{vendor}:{code}:gate                         # vendor-parented
./gradlew :{vendor}:{suite}:{code}:gate                 # suite-parented
```

`gate` writes `gate-stamp.json`. The publish workflow rejects any product without a committed stamp.

For file-shape checks only (no compile, no dataloader):
```bash
./gradlew :{vendor}:{code}:validateContent
```

**Common validation errors:**

| Error | Fix |
|-------|-----|
| `index.yml.parentType must be 'vendor' or 'suite'` | Set `parentType` in index.yml |
| `package.json name expected '@zerobias-org/product-<v>-<p>'` | Name must match directory; fix the name (don't rename the dir) |
| `zerobias.package expected '<v>.<p>'` | Fix the `zerobias.package` value to match directory layout |
| `logo.svg doesn't look like SVG` | Logo magic bytes don't match its extension; rename to actual format or re-source |
| `duplicate index.yml ids across the repo` | UUID collision — generate fresh with `uuidgen` |

### Phase 6: Test with Dataloader

```bash
cd package/{vendor}/{code}
dataloader
```

### Phase 7: Git Operations

```bash
git add package/{vendor}/{code}/
git commit -m "feat({vendor}-{code}): add {Product Name}

- Add product metadata and catalog definition
- Source: {url}

Task: {task-code}
Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"

git push origin feat/{vendor}-{code}

gh pr create --base dev --title "feat({vendor}-{code}): add {Product Name}" --body "$(cat <<'EOF'
## Summary
- New product: {Product Name}
- Vendor: {vendor}
- Type: {vendor|suite} product

## Task Reference
- Task: {task-code}
- Task ID: {task-id}

## Validation
- [x] `./gradlew :{vendor}:{code}:gate` passes (writes `gate-stamp.json`)
- [x] Logo included

## Test Plan
- [ ] Verify product appears in catalog after publish
- [ ] Check vendor/suite relationship is correct

Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

### Phase 8: Update Task (if task-driven)

```javascript
// Transition to awaiting_approval
const reviewTransition = task.nextTransitions.find(t => t.status === "awaiting_approval")
zerobias_execute("platform.Task.update", {
  id: taskId,
  updateTask: { transitionId: reviewTransition.id }
})
```
