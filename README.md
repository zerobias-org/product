# Product monorepo

ZeroBias product artifacts. Each `package/<vendor>/<code>/` (or `package/<vendor>/<suite>/<code>/`) directory is one publishable product package.

## Authentication

Set `ZB_TOKEN` in your environment to authenticate with the npm registry. Get one from [ZeroBias](https://app.zerobias.com).

## Build & validate

This repo is on the gradle + [zbb](https://github.com/zerobias-org/devops) publish pipeline.

```bash
# Validate one product (file-shape checks only):
./gradlew :<vendor>:<code>:validateContent
./gradlew :<vendor>:<suite>:<code>:validateContent      # suite-parented

# Full gate (validate → lint → compile → buildArtifacts → testIntegrationDataloader → writeGateStamp):
./gradlew :<vendor>:<code>:gate
```

`gate` writes `package/<path>/gate-stamp.json`. The publish workflow rejects any product without a committed stamp.

`testIntegrationDataloader` runs the dataloader against an ephemeral Neon Postgres branch. Without `NEON_API_KEY` / `NEON_PROJECT_ID` in env, it's skipped locally; CI runs it on push.

## Validator philosophy

The dataloader is the source of truth for schema rules (UUID format, code regex, status enum, URL parse, `vendorId`/`suiteId` lookup, etc.). The gate validator (`build.gradle.kts`) only enforces things the dataloader CANNOT or DOES NOT see:

1. Filesystem ↔ npm-name ↔ `zerobias.package` triangulation
2. Logo file correctness (magic bytes, size, presence in `files` array)
3. Repo-wide unique `id` UUIDs across all products

This avoids drift when the dataloader tightens.

## Creating a new product

See [`.claude/skills/create-product/SKILL.md`](.claude/skills/create-product/SKILL.md). With Claude Code: `/create-product [task-id]`.

## Publishing

`.github/workflows/publish.yml` invokes `zerobias-org/devops/.github/workflows/zbb-publish-reusable.yml@main` on push to `main` / `qa` / `dev` / `uat`. It auto-detects changed products from the diff, single-writer version-bumps on main, publishes per-product, then runs the bundle refresh.

For pre-release validation on a feature branch:
```bash
gh workflow run publish.yml --ref <branch>
```

## Branches

- `main` — default branch, all PRs target it
- `dev`, `qa`, `uat` — environment branches kept in sync by the `sync` job in the publish workflow

## Commit format

[Conventional Commits](https://www.conventionalcommits.org/). Validated by `commitlint` (config in `.commitlintrc.json`).

```
feat(product-<vendor>-<code>): short subject

Optional body.
```

Common scopes: `product-<v>-<c>`, `product-<v>-<s>-<c>`, `bundle`, `validator`.
