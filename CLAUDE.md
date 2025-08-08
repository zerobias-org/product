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
```bash
# Create new product structure
sh scripts/createNewproduct.sh <folder_path>

# Then navigate to the new product directory
cd <folder_path>
npm install
npm shrinkwrap
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
- `catalog.yml`: Service catalog information
- `package.json`: Dependencies and scripts
- `npm-shrinkwrap.json`: Locked dependency versions
- `logo.svg/png`: Product branding

### Versioning Strategy
- Independent versioning per package
- Starting version: `0.0.0`
- Version bumps handled automatically by Lerna in CI/CD
- Pre-major releases use release candidates (e.g., `1.0.0-rc.1`)

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