# Contributing a product

Anyone can author and validate a product package. There are two lanes,
depending on whether you have a ZeroBias platform account. AI-assisted
contributors: the [`create-product` skill](.claude/skills/create-product/SKILL.md)
encodes both lanes end to end — just say "make product X" in Claude Code.

Products depend on their parent: a **vendor-parented** product
(`package/<vendor>/<code>/`) requires the vendor package to exist; a
**suite-parented** product (`package/<vendor>/<suite>/<code>/`) requires
the suite (which itself requires the vendor). Check/create the chain
first — `vendor → suite → product` — in the `vendor` and `suite` repos.

## Lane 1 — anyone with a GitHub account (no ZeroBias account needed)

You can validate everything except the platform load locally.

1. **Tools**: git, Java 17+, Node 22, the `gh` CLI (and optionally
   `@zerobias-org/zbb`).
2. **GitHub token with `read:packages`** — required even though the packages
   are public (GitHub Packages Maven does not allow anonymous reads; the
   gradle plugin resolves from it). Either export a personal access token
   that has the `read:packages` scope:

   ```bash
   export GITHUB_TOKEN=<your PAT>
   ```

   or reuse your `gh` login:

   ```bash
   gh auth refresh -s read:packages
   export GITHUB_TOKEN=$(gh auth token)
   ```

   (Heads-up: an *invalid* `GITHUB_TOKEN` in your environment silently
   shadows a valid `gh` keyring login — `gh auth status` will show it.)

3. **Scaffold + author**:

   ```bash
   ./scripts/createNewProduct.sh package/<vendor>/<code>          # vendor-parented
   ./scripts/createNewProduct.sh package/<vendor>/<suite>/<code>  # suite-parented
   # fill {name}/{description}/{url} in index.yml + catalog.yml,
   # set {vendorId} (and {suiteId}) from the parent package(s),
   # add the official logo,
   echo 'plugins { id("zb.content") }' > package/<path>/build.gradle.kts
   ```

4. **Validate** (from the repo root):

   ```bash
   ./gradlew :<vendor>:<code>:gate            # vendor-parented
   ./gradlew :<vendor>:<suite>:<code>:gate    # suite-parented
   ```

   The platform dataloader step prints `ZB_TOKEN not set — skipping` — that is
   expected in this lane; everything else must pass. The run writes
   `package/<path>/gate-stamp.json` — commit it.

5. **PR against `main`** (this repo's default and PR base — unlike some
   sibling content repos that use `dev`). Maintainers run the
   platform-side (org-load) verification before merge.

## Lane 2 — ZeroBias platform users (org-first delivery)

Your product is loaded into your own org and verified there **before** any PR.

1. One-time credential setup + session launch (owns all three credential
   homes, verifies your key is an org OWNER, then starts Claude Code with
   everything exported):

   ```bash
   ./scripts/setup-org-credentials.sh --launch
   ```

2. In the session, say **"make product \<name\>"**. The skill runs the full
   SDLC: scaffold → gate → `publishOrg` (org-private load) → you verify the
   org artifact → sign-off → PR against `main`.

Notes for both lanes: commits follow Conventional Commits
(`feat(product-<vendor>-<code>): …`); never commit on `main` directly;
never hand-edit `version` after creation (CI owns bumps); the product
`code` must match `^[\d_a-z]+$` (prefer plain lowercase alphanumeric).
