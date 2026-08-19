# Product Package Templates

The scaffold script (`./scripts/createNewProduct.sh`) generates all of
these from `templates/` and rewrites them for the parent type — this file
is the reference for what the RESULT must look like (and for hand-fixing a
package that drifted).

## .npmrc

```
@zerobias-org:registry=https://pkg.zerobias.org
//pkg.zerobias.org/:_authToken=${ZB_TOKEN}
```

## package.json — vendor-parented

```json
{
  "name": "@zerobias-org/product-{vendor}-{code}",
  "version": "1.0.0",
  "description": "Product package for {Product Name}",
  "author": "team@zerobias.com",
  "license": "ISC",
  "repository": {
    "type": "git",
    "url": "git@github.com:zerobias-org/product.git",
    "directory": "package/{vendor}/{code}/"
  },
  "scripts": {
    "correct:deps": "tsx ../../../scripts/correctDeps.ts"
  },
  "publishConfig": {
    "registry": "https://pkg.zerobias.org/"
  },
  "files": ["catalog.yml", "index.yml", "logo.*"],
  "dependencies": {
    "@zerobias-org/vendor-{vendor}": "latest"
  },
  "zerobias": {
    "dataloader-version": "1.0.0",
    "import-artifact": "product",
    "package": "{vendor}.{code}",
    "orgId": "{target-org-uuid}"
  }
}
```

- `version` starts at `1.0.0` and is never hand-edited afterwards — CI
  owns bumps; org-private rc versions are computed by `zbb publishOrg`.
- `zerobias.orgId` is set during the org-first flow (before the first
  gate) and **deleted in the PR phase** — see the skill's Phase 3/7.
- Legacy `auditmation` metadata key is still accepted in old packages;
  new packages use `zerobias`.

## package.json — suite-parented (differences only)

```jsonc
{
  "name": "@zerobias-org/product-{vendor}-{suite}-{code}",
  "repository": { "directory": "package/{vendor}/{suite}/{code}/" },
  "scripts": {
    "correct:deps": "tsx ../../../../scripts/correctDeps.ts"   // one level deeper
  },
  "dependencies": {
    "@zerobias-org/suite-{vendor}-{suite}": "latest"           // suite, not vendor
  },
  "zerobias": { "package": "{vendor}.{suite}.{code}" }
}
```

## index.yml

```yaml
id: {fresh-uuid-v4-lowercase}
name: {Product Display Name}
type: product
ownerId: 00000000-0000-0000-0000-000000000000
code: {code}
status: active
description: >-
  {What the product is and does.}
aliases: []
logo: https://cdn.auditmation.io/logos/{vendor}-{code}.svg
imageUrl: https://cdn.auditmation.io/logos/{vendor}-{code}.svg
url: {official-product-url}
vendorId: {vendor-uuid}
vendorCode: {vendor}
parentType: vendor
tags: []
segments: []
cpeProducts: []
factoryTypes:
  - software
hostingTypes: []
```

Suite-parented products additionally carry (and the scaffold inserts):

```yaml
suiteId: {suite-uuid}
suiteCode: {suite}
parentType: suite
```

- No `created`/`updated` fields — the dataloader stamps them server-side.
- `logo`/`imageUrl` extension must match the actual logo file; the CDN
  name is `{vendor}-{code}.<ext>` (vendor-parented) or
  `{vendor}-{suite}-{code}.<ext>` (suite-parented).
- `hostingTypes`: fill when known (e.g. `- saas`); `factoryTypes:
  [software]` is the typical default.

**Getting vendor/suite IDs** — from the Phase 1 `store.*.get` results
(`id` field):

```javascript
zerobias_execute("store.Vendor.get", { vendorCode: "{vendor}" })          // → vendorId
zerobias_execute("store.Suite.get",  { vendorCode: "{vendor}",
                                       suiteCode: "{suite}" })            // → suiteId
```

(The `portal.*.search` ops found in older docs do NOT exist. Legacy
fallback: `npm install` in the package dir and read `id` from
`node_modules/@zerobias-org/vendor-{vendor}/index.yml`.)

## catalog.yml

```yaml
Product:
  name: {Product Display Name}
  versions:
    - 0.0.0
  package: {vendor}.{code}
  description: |-
    {Brief product description}
  link: {official-product-url}
  contentType: json
Operations:
```

(`package` uses the same dot-joined path as `zerobias.package` —
`{vendor}.{suite}.{code}` for suite-parented.)

## Logo

Download the official asset (vendor press-kit / brand pages); prefer SVG,
never modify SVG content. Exactly one `logo.{svg|png|jpg}`, magic bytes
matching the extension, present in the `files` array.
