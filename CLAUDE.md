# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Overview

This is a Lerna-based monorepo containing ZeroBias product artifacts. Each product package represents a vendor/service integration with metadata, configuration, and dependencies.

## Development Commands

### Installation and Setup
```bash
# Initial setup (required after cloning)
npm install

# Bootstrap all packages with dependencies
npm run bootstrap

# Reset entire workspace (clean + bootstrap + build)
npm run reset
```

### Common Development Tasks
```bash
# Validate all edited or added products
npm run validate

# Build all packages (runs npm shrinkwrap)
npm run build

# Run tests for all packages
npm run lerna:test

# Correct dependencies across packages
npm run correct:deps

# Clean workspace
npm run clean       # Basic clean
npm run clean:full  # Full clean including node_modules
```

### Lerna Version Management
```bash
# Dry run to preview version changes and changelog
npm run lerna:dry-run

# Version packages (used by CI/CD)
npm run lerna:version

# Publish packages (used by CI/CD)
npm run lerna:publish
```

### Creating New Products

#### Understanding Product Types
Products in this repository can be:
1. **Vendor Products**: Direct vendor integrations (e.g., `package/github/github/`)
2. **Suite Products**: Products that belong to a suite (e.g., `package/microsoft/azure/entra/`)

#### Product Directory Structure
- **Vendor Products**: `package/{vendor}/{code}/`
  - `{vendor}` - Vendor name (e.g., github, okta)
  - `{code}` - Product identifier (e.g., github, okta)
- **Suite Products**: `package/{vendor}/{suite}/{code}/`
  - `{vendor}` - Vendor name (e.g., microsoft)
  - `{suite}` - Suite identifier (e.g., azure, 365)  
  - `{code}` - Product identifier (e.g., entra, teams)

#### Manual Creation Process
Since no automated script exists yet, create products manually:

1. **Create Directory Structure**
   ```bash
   # For vendor product
   mkdir -p package/{vendor}/{code}
   
   # For suite product  
   mkdir -p package/{vendor}/{suite}/{code}
   ```

2. **Copy Template Files**
   ```bash
   # Copy from repository root templates/ directory
   cp templates/* package/{vendor}/{code}/
   cp .npmrc package/{vendor}/{code}/
   ```

3. **Configure package.json**
   - Update name: `@zerobias-org/product-{vendor}-{code}` (manually replace placeholders)
   - Set description: `"Product package for {vendor} {code}"`
   - Set repository directory path correctly:
     - **Vendor products**: `"directory": "package/{vendor}/{code}/"`
     - **Suite products**: `"directory": "package/{vendor}/{suite}/{code}/"`
   - Add dependencies:
     - **Vendor products**: `"@zerobias-org/vendor-{vendor}": "latest"`
     - **Suite products**: `"@zerobias-org/suite-{vendor}-{suite}": "latest"`
   - Update auditmation.package: `"{vendor}.{code}"`
   - Set dataloader-version: `"5.0.25"` (current standard)
   - Ensure files array includes: `["index.yml", "catalog.yml", "logo.*"]`
   - Update script paths:
     - **Vendor products**: `"../../../scripts/publish.sh"`, `"../../../scripts/prepublish.sh"`, etc.
     - **Suite products**: `"../../../../scripts/publish.sh"`, `"../../../../scripts/prepublish.sh"`, etc.

4. **Configure index.yml**
   - Generate unique UUID (lowercase)
   - Set real name, description, url (manually replace all template placeholders)
   - Set vendorCode: `{vendor}` and code: `{code}`
   - Set current timestamps for created/updated fields (both should be identical at creation)
   - Configure parentType and IDs:
     - **Vendor products**: `parentType: "vendor"`, add vendorId (get from `node_modules/@zerobias-org/vendor-{vendor}/index.yml`)
     - **Suite products**: `parentType: "suite"`, add vendorId, suiteId, suiteCode (get IDs from `node_modules/@zerobias-org/suite-{vendor}-{suite}/index.yml`)
   - Set logo URL pattern:
     - **Vendor products**: `https://cdn.auditmation.io/logos/{vendor}-{code}.svg`
     - **Suite products**: `https://cdn.auditmation.io/logos/{vendor}-{suite}-{code}.svg`
   - Set status: `"verified"`
   - Set factoryTypes: `["software"]` (standard for software products)
   - Set hostingTypes: `[]` (empty array is default)
   - Set cpeProducts: `[]` (empty unless you know specific CPE identifiers exist)

5. **Add Logo File**
   ```bash
   # Download official logo (preferred approach)
   curl -o package/{vendor}/{code}/logo.svg "https://official-logo-url.svg"
   
   # Verify download completed
   ls -lh package/{vendor}/{code}/logo.svg
   ```

5. **Create catalog.yml**
   - Set Product section with name, package, description, link
   - Include version array with current version
   - Set contentType: `"json"`
   - Omit Operations section (not required for new products)

6. **Install Dependencies and Generate Shrinkwrap**
   ```bash
   cd package/{vendor}/{code}
   npm install  # This will install vendor/suite dependency and allow ID lookup
   npm shrinkwrap
   ```

7. **Validate Product**
   ```bash
   cd package/{vendor}/{code}
   npm run validate
   ```

#### Examples from Production

**GitHub (Vendor Product):**
```yaml
# package/github/github/index.yml
id: 7129ca79-24d8-53f0-8491-e66977bee4a7
name: GitHub
code: github
vendorCode: github
vendorId: 075189d1-f253-5eab-b698-89514f327da0
parentType: vendor
```

```json
// package/github/github/package.json
{
  "name": "@zerobias-org/product-github-github",
  "dependencies": {
    "@zerobias-org/vendor-github": "latest"
  },
  "auditmation": {
    "package": "github.github"
  }
}
```

**Microsoft Entra (Suite Product):**
```yaml
# package/microsoft/azure/entra/index.yml
id: 62c17d9b-10f3-41ec-8be2-f53b3d18fe64
name: Entra ID
code: entra
vendorCode: microsoft
vendorId: f6b6f862-3012-5f64-a523-4b1f28296829
suiteCode: azure
suiteId: 430ec569-6163-517d-93ea-658e4e92033c
parentType: suite
```

## Architecture

### Monorepo Structure
- **Root**: Contains Lerna, Nx, and Husky configurations
- **package/**: All product packages organized by vendor/service
  - Each package follows pattern: `vendor/service/`
  - Contains: `index.yml`, `catalog.yml`, `logo.svg`, `package.json`, `npm-shrinkwrap.json`
- **scripts/**: Build and publishing automation scripts
- **templates/**: Package templates for new products

### Key Technologies
- **Lerna**: Independent versioning for packages
- **Nx**: Task orchestration and caching
- **Husky**: Git hooks for commit validation
- **Conventional Commits**: Enforced commit message format

### Package Structure
Each product package contains:
- `index.yml`: Product metadata and configuration
- `catalog.yml`: Service catalog information (optional)
- `package.json`: Dependencies and scripts
- `npm-shrinkwrap.json`: Locked dependency versions
- `logo.svg/png`: Product branding
- `.npmrc`: Registry configuration

### Versioning Strategy
- Independent versioning per package
- Starting version for new products: `1.0.0-rc.1`
- Version bumps handled automatically by Lerna in CI/CD
- Pre-major releases use release candidates (e.g., `1.0.0-rc.1`)

### Product Structure and Requirements

#### package.json Requirements
- Package name format: `@zerobias-org/product-{vendor}-{code}`
- Must include `auditmation` section with:
  - `package: "{vendor}.{code}"`
  - `import-artifact: "product"`
  - `dataloader-version: "5.0.25"` (current standard)
- Dependencies must include exactly one dependency:
  - **Vendor products**: `@zerobias-org/vendor-{vendor}`
  - **Suite products**: `@zerobias-org/suite-{vendor}-{suite}`
- Standard scripts: `nx:publish`, `prepublishtest`, `correct:deps`, `validate`

#### index.yml Requirements
- Must contain: `id`, `name`, `description`, `url`, `vendorCode`, `code`, `status`
- Must have real timestamps for `created` and `updated` (never use placeholder times like `00:00:00.000Z`)
- Optional: `logo`, `imageUrl`, `tags`, `aliases`, `cpeProducts`, `factoryTypes`, `hostingTypes`
- No template placeholders like `{code}`, `{name}`, etc.
- Logo URL pattern: `https://cdn.auditmation.io/logos/{vendor}-{code}.svg`

#### Parent Relationship Configuration
- **Vendor products**: Set `parentType: "vendor"`, include `vendorId` and `vendorCode`
- **Suite products**: Set `parentType: "suite"`, include `vendorId`, `vendorCode`, `suiteId`, and `suiteCode`

#### Logo Best Practices
- **Use official logos**: Always try to find and use official vendor SVG logos
- **Download approach**: Use curl to download official logos directly:
  ```bash
  curl -o package/{vendor}/{code}/logo.svg "https://official-logo-url.svg"
  ```
- **Verify download**: Check file size with `ls -lh` to ensure complete download
- **Never modify**: Don't edit SVG content - preserve official branding exactly as provided

## Git Workflow

### Branch Strategy
- Main branch: `main`
- Development branch: `dev`
- All PRs should target `dev` branch

### Commit Format
Follow Conventional Commits specification:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes
- `refactor`: Code refactoring
- `perf`: Performance improvements
- `test`: Test additions/changes
- `chore`: Build/tooling changes

### CI/CD Workflows
- **pull_request.yml**: Validates PRs, runs tests
- **lerna_publish.yml**: Publishes changed packages
- **lerna_post_publish.yml**: Post-publish notifications

## Important Notes

- Always run `npm install` in root after cloning to set up Husky hooks
- Never manually bump versions in package.json files
- All commits must follow Conventional Commits format
- Set `ZB_TOKEN` environment variable for npm registry authentication
- Packages are published to GitHub npm registry

### Validation and Testing

#### Validation Script
The `scripts/validate.ts` script checks:
- Proper package.json structure and naming
- Required auditmation configuration
- Dependency configuration (vendor or suite)
- index.yml structure and required fields
- Presence of required files (.npmrc, package.json, index.yml)
- Ensures no template placeholders remain

#### Required Post-Installation Steps
- Always run `npm install && npm shrinkwrap` after creating or modifying a product
- The dependency (vendor or suite) will force installation of the appropriate package
- Run validation from the product directory: `npm run validate`

