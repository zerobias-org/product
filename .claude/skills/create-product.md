# Create Product Skill

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

**Directory structure:**
```
# Vendor product
package/{vendor}/{code}/

# Suite product
package/{vendor}/{suite}/{code}/
```

#### 4.1 Create `.npmrc`

```
@zerobias-org:registry=https://pkg.zerobias.org/
//pkg.zerobias.org/:_authToken=${ZB_TOKEN}
```

#### 4.2 Create `package.json`

**Vendor product:**
```json
{
  "name": "@zerobias-org/product-{vendor}-{code}",
  "version": "0.0.0",
  "description": "{Product Name} - {brief description}",
  "author": "team@zerobias.com",
  "license": "ISC",
  "type": "module",
  "repository": {
    "type": "git",
    "url": "git@github.com:zerobias-org/product.git",
    "directory": "package/{vendor}/{code}/"
  },
  "scripts": {
    "correct:deps": "ts-node ../../../scripts/correctDeps.ts",
    "validate": "ts-node ../../../scripts/validate.ts"
  },
  "publishConfig": {
    "registry": "https://npm.pkg.github.com/"
  },
  "files": ["catalog.yml", "index.yml", "logo.*"],
  "dependencies": {
    "@zerobias-org/vendor-{vendor}": "latest"
  },
  "auditmation": {
    "dataloader-version": "5.0.25",
    "import-artifact": "product",
    "package": "{vendor}.{code}"
  }
}
```

**Suite product:**
```json
{
  "name": "@zerobias-org/product-{vendor}-{suite}-{code}",
  "version": "0.0.0",
  "description": "{Product Name} - {brief description}",
  "author": "team@zerobias.com",
  "license": "ISC",
  "type": "module",
  "repository": {
    "type": "git",
    "url": "git@github.com:zerobias-org/product.git",
    "directory": "package/{vendor}/{suite}/{code}/"
  },
  "scripts": {
    "correct:deps": "ts-node ../../../../scripts/correctDeps.ts",
    "validate": "ts-node ../../../../scripts/validate.ts"
  },
  "publishConfig": {
    "registry": "https://npm.pkg.github.com/"
  },
  "files": ["catalog.yml", "index.yml", "logo.*"],
  "dependencies": {
    "@zerobias-org/suite-{vendor}-{suite}": "latest"
  },
  "auditmation": {
    "dataloader-version": "5.0.25",
    "import-artifact": "product",
    "package": "{vendor}.{suite}.{code}"
  }
}
```

**Key conventions:**
- Metadata key: `auditmation` (product repo has NOT migrated to `zerobias`)
- Dataloader version: `"5.0.25"`
- `catalog.yml` MUST be in the `files` array
- Script paths must match directory depth (3 levels for vendor, 4 for suite)

#### 4.3 Create `index.yml`

```yaml
id: {generate-uuid}
name: {Product Display Name}
type: product
ownerId: 00000000-0000-0000-0000-000000000000
created: '{ISO-8601-timestamp}'
updated: '{ISO-8601-timestamp}'
parentType: vendor  # or 'suite' for suite products
code: {code}
status: active
vendorCode: {vendor}
description: >-
  {Multi-line product description}
logo: https://cdn.auditmation.io/logos/{vendor}-{code}.png
url: {official-product-url}
vendorId: {vendor-uuid}        # from vendor package index.yml
# Suite products also need:
# suiteId: {suite-uuid}        # from suite package index.yml
# suiteCode: {suite-code}
tags: []
factoryTypes:
  - software                   # valid: software, firmware, hardware
```

**Getting vendor/suite IDs:**
```bash
# Vendor ID - from installed dependency
cat node_modules/@zerobias-org/vendor-{vendor}/index.yml | grep "^id:"

# Suite ID - from installed dependency
cat node_modules/@zerobias-org/suite-{vendor}-{suite}/index.yml | grep "^id:"
```

Or search via API:
```javascript
zerobias_execute("portal.Vendor.search", { searchVendorBody: { search: vendorCode }})
zerobias_execute("portal.Suite.search", { searchSuiteBody: { search: suiteCode }})
```

#### 4.4 Create `catalog.yml` (REQUIRED)

```yaml
Product:
  name: {Product Display Name}
  versions: [0.0.0]
  package: {vendor}.{code}     # or {vendor}.{suite}.{code} for suite products
  description: |-
    {Brief product description}
  link: {official-product-url}
  contentType: json
Operations:
```

The dataloader reads this file. Without it, the dataloader fails with:
`ENOENT: no such file or directory, open './catalog.yml'`

#### 4.5 Add Logo

```bash
# Download official logo
curl -o package/{vendor}/{code}/logo.png "https://official-logo-url"

# Verify
ls -lh package/{vendor}/{code}/logo.png
```

### Phase 5: Validate

```bash
cd package/{vendor}/{code}
npm install
npm run validate
```

**Common validation errors:**

| Error | Fix |
|-------|-----|
| `package.json missing auditmation section` | Use `auditmation` key, not `zerobias` |
| `factoryType documentation not valid` | Use `software`, `firmware`, or `hardware` |
| `package.json missing name` | Check name format matches convention |
| `description needs replacement from {name}` | Replace all template placeholders |

### Phase 6: Test with Dataloader

```bash
cd package/{vendor}/{code}
dataloader
```

### Phase 7: Git Operations

```bash
# Stage files
git add package/{vendor}/{code}/

# Commit
git commit -m "feat({vendor}-{code}): add {Product Name}

- Add product metadata and catalog definition
- Source: {url}

Task: {task-code}
Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"

# Push
git push origin feat/{vendor}-{code}

# Create PR targeting dev
gh pr create --base dev --title "feat({vendor}-{code}): add {Product Name}" --body "$(cat <<'EOF'
## Summary
- New product: {Product Name}
- Vendor: {vendor}
- Type: {vendor|suite} product

## Task Reference
- Task: {task-code}
- Task ID: {task-id}

## Validation
- [x] `npm run validate` passes
- [x] `dataloader` runs successfully
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
