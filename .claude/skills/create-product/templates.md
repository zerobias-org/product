# Product Package Templates

## .npmrc

```
@auditmation:registry=https://pkg.zerobias.org
@auditlogic:registry=https://pkg.zerobias.org
@zerobias-org:registry=https://pkg.zerobias.org
//pkg.zerobias.org/:always-auth=true
//pkg.zerobias.org/:_authToken=${ZB_TOKEN}
```

## package.json — Vendor Product

```json
{
  "name": "@zerobias-org/product-{vendor}-{code}",
  "version": "1.0.0-rc.1",
  "description": "Product package for {Product Name}",
  "author": "team@zerobias.com",
  "license": "ISC",
  "repository": {
    "type": "git",
    "url": "git@github.com:zerobias-org/product.git",
    "directory": "package/{vendor}/{code}/"
  },
  "scripts": {
    "correct:deps": "tsx ../../../scripts/correctDeps.ts",
    "validate": "tsx ../../../scripts/validate.ts"
  },
  "publishConfig": {
    "registry": "https://npm.pkg.github.com/"
  },
  "files": ["catalog.yml", "index.yml", "logo.*"],
  "dependencies": {
    "@zerobias-org/vendor-{vendor}": "latest"
  },
  "zerobias": {
    "dataloader-version": "1.0.0",
    "import-artifact": "product",
    "package": "{vendor}.{code}"
  }
}
```

## package.json — Suite Product

```json
{
  "name": "@zerobias-org/product-{vendor}-{suite}-{code}",
  "version": "1.0.0-rc.1",
  "description": "Product package for {Product Name}",
  "author": "team@zerobias.com",
  "license": "ISC",
  "repository": {
    "type": "git",
    "url": "git@github.com:zerobias-org/product.git",
    "directory": "package/{vendor}/{suite}/{code}/"
  },
  "scripts": {
    "correct:deps": "tsx ../../../../scripts/correctDeps.ts",
    "validate": "tsx ../../../../scripts/validate.ts"
  },
  "publishConfig": {
    "registry": "https://npm.pkg.github.com/"
  },
  "files": ["catalog.yml", "index.yml", "logo.*"],
  "dependencies": {
    "@zerobias-org/suite-{vendor}-{suite}": "latest"
  },
  "zerobias": {
    "dataloader-version": "1.0.0",
    "import-artifact": "product",
    "package": "{vendor}.{suite}.{code}"
  }
}
```

## index.yml

```yaml
id: {generate-uuid}
name: {Product Display Name}
type: product
ownerId: 00000000-0000-0000-0000-000000000000
created: '{ISO-8601-timestamp}'
updated: '{ISO-8601-timestamp}'
code: {code}
status: verified
description: >-
  {Multi-line product description}
aliases: []
logo: https://cdn.auditmation.io/logos/{vendor}-{code}.svg
imageUrl: https://cdn.auditmation.io/logos/{vendor}-{code}.svg
url: {official-product-url}
vendorId: {vendor-uuid}
vendorCode: {vendor}
# Suite products also need:
# suiteId: {suite-uuid}
# suiteCode: {suite}
parentType: vendor  # or 'suite' for suite products
tags: []
cpeProducts: []
factoryTypes:
  - software
hostingTypes: []
```

**Getting vendor/suite IDs:**
```bash
# From installed dependency
cat node_modules/@zerobias-org/vendor-{vendor}/index.yml | grep "^id:"
cat node_modules/@zerobias-org/suite-{vendor}-{suite}/index.yml | grep "^id:"
```

Or via API:
```javascript
zerobias_execute("portal.Vendor.search", { searchVendorBody: { search: vendorCode }})
zerobias_execute("portal.Suite.search", { searchSuiteBody: { search: suiteCode }})
```

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

## Logo

```bash
# Download official logo (prefer SVG)
curl -o package/{path}/logo.svg "https://official-logo-url.svg"
ls -lh package/{path}/logo.svg
```

Logo URL pattern: `https://cdn.auditmation.io/logos/{vendor}-{code}.svg`
For suite products: `https://cdn.auditmation.io/logos/{vendor}-{suite}-{code}.svg`
