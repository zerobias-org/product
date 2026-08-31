# Product monorepo

ZeroBias product artifacts. Each `package/<vendor>/<code>/` (or `package/<vendor>/<suite>/<code>/`) directory is one publishable product package.

## Authentication

Two separate credentials. **Both are required before any `zbb` / gradle
command** — compile, validation, tests, `gate`, publish. Nothing here is
optional or implied, and neither one substitutes for the other.

| Credential | What it unlocks | Needed by |
|---|---|---|
| `GITHUB_TOKEN` with **`read:packages`** | the `zb.*` gradle plugins (`zb.workspace`, `zb.base`, `zb.content`) from GitHub Packages Maven | **everyone**, every zbb/gradle command |
| `ZB_TOKEN` | the `@zerobias-org` npm registry + the gate's dataloader step | package installs; org loads |

### 1. GitHub token — `read:packages`

`com.zerobias.build-tools` is a **public** package, but GitHub Packages Maven
refuses **anonymous** reads. So this is a registry requirement, not a
permission one: nothing needs to be granted to you, and you do not need
membership of any organisation.

**Being logged in to `gh` is not enough — the scope is separate.** Check the
scope, not the login:

```bash
gh auth status 2>&1 | grep -q 'read:packages' && echo OK || echo 'MISSING read:packages'
```

If it says MISSING:

```bash
gh auth refresh -s read:packages
export GITHUB_TOKEN=$(gh auth token)
```

or export a personal access token that already carries the scope:

```bash
export GITHUB_TOKEN=<your PAT>
```

Verify it actually reads (200 = ready, 401 = scope still missing):

```bash
curl -s -o /dev/null -w '%{http_code}\n' -u "x:$GITHUB_TOKEN" \
  https://maven.pkg.github.com/zerobias-org/util/zb/workspace/zb.workspace.gradle.plugin/maven-metadata.xml
```

Without it the build fails on its very first request — the plugins pin
`1.+`, so gradle must fetch `maven-metadata.xml` before anything else, and a
401 there surfaces as `Plugin [id: 'zb.workspace'] was not found` or
`Could not resolve com.zerobias.build-tools`, long before any product file is
read.

> ⚠ An **invalid** `GITHUB_TOKEN` in your environment silently shadows a
> valid `gh` keyring login — `gh auth status` exposes it.

> ⚠ Machines that have previously run `publishToMavenLocal` on build-tools are
> silently exempt: `mavenLocal()` is first in the resolution order, so the
> token is never exercised there. A clean or containerised environment has no
> `~/.m2` and always needs the scope — never conclude it is unnecessary
> because it worked on a developer machine.

### 2. `ZB_TOKEN`

Set `ZB_TOKEN` in your environment to authenticate with the npm registry. Get
one from [ZeroBias](https://app.zerobias.com). Without it the gate's
dataloader step prints `ZB_TOKEN not set — skipping`, which is expected for
contributors without a ZeroBias account; everything else must still pass.

Full contributor walkthrough, both lanes: **[CONTRIBUTING.md](CONTRIBUTING.md)**.

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
