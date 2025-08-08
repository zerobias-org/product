
import fs from 'fs/promises';
import path from 'path';
import yaml from 'yaml';
import { UUID, URL } from '@auditmation/types-core-js';
import { VspStatusEnum } from '@auditmation/module-auditmation-auditmation-portal';

const productTypes: Record<string, number> = {};

function isKnownFileType(filename: string): boolean {
  return (filename.toLowerCase().endsWith('.yml') || filename.toLowerCase().endsWith('.json'));
}

async function readAndParseFile(file: string, fullPathFile: string): Promise<any> {
  if (isKnownFileType(file)) {
    const fileData = (await fs.readFile(fullPathFile)).toString();

    // eslint-disable-next-line no-await-in-loop
    let name = file;

    if (name.indexOf('/') !== -1) {
      name = name.substring(name.lastIndexOf('/') + 1);
    }

    name = name.substring(0, name.lastIndexOf('.'));

    let document = null;

    if (file.endsWith('.yml')) {
      // YAML parser
      document = yaml.parse(fileData);
    } else if (file.endsWith('.json')) {
      // Native JSON parser
      document = JSON.parse(fileData);
    }

    return document;
  }

  throw new Error(`File type not supported: ${file}`);
}

function processPackageJson(packageFile: Record<string, any>, code: string, vendor: string, suite?: string): void {
  let check: any = packageFile.name !== undefined && packageFile.name !== null && packageFile.name === `@zerobias-org/product-${vendor}-${code}`
    ? true : new Error('package.json missing name or not set to @zerobias-org/product-<vendor>-<code>');

  check = packageFile.description !== undefined && packageFile.description !== null
    ? true : new Error('package.json missing description or needs replacement from {name}');
  if (packageFile.description === '{name}') {
    throw new Error('package.json description needs replacement from {name}');
  }

  if (packageFile.auditmation && typeof packageFile.auditmation === 'object') {
    const auditmation = packageFile.auditmation;
    check = auditmation['import-artifact'] !== undefined && auditmation['import-artifact'] !== null && auditmation['import-artifact'] === 'product'
      ? true : new Error('package.json auditmation section missing import-artifact or not set to product');
    check = auditmation.package !== undefined && auditmation.package !== null && auditmation.package === `${vendor}.${code}`
      ? true : new Error('package.json auditmation section missing package or not set to <vendor>.<code>');
    check = auditmation['dataloader-version'] !== undefined && auditmation['dataloader-version'] !== null ? true
      : new Error('package.json auditmation section missing dataloader-version');
  } else {
    throw new Error(`package.json missing auditmation section`);
  }

  if (packageFile.dependencies && typeof packageFile.dependencies === 'object') {
    if (suite) {
      if (!packageFile.dependencies[`@zerobias-org/suite-${vendor}-${suite}`]) {
        throw new Error(`package.json dependencies missing dep for suite @zerobias-org/suite-${vendor}-${suite} artifact`);
      }
    } else {
      if (!packageFile.dependencies[`@zerobias-org/vendor-${vendor}`]) {
        throw new Error(`package.json dependencies missing dep for vendor @zerobias-org/vendor-${vendor} artifact`);
      }
    }
  } else {
    throw new Error(`package.json missing dependency section with vendor ${vendor} artifact`);
  }

}

async function processIndexYml(indexFile: Record<string, any>): Promise<{ code: string, vendor: string, suite?: string }> {
  const code = indexFile.code !== undefined && indexFile.code !== null && indexFile.code !== '{code}' ? indexFile.code
    : new Error('code not found in index.yml');
  if (typeof code !== 'string') {
    throw new Error('code in index.yml needs replacement from {code}');
  }

  let check: any;
  check = indexFile.id !== undefined && indexFile.id !== null && indexFile.id !== '{id}' ? new UUID(indexFile.id)
    : new Error('id not found in index.yml');
  check = indexFile.name !== undefined && indexFile.name !== null && indexFile.name !== '{name}' ? indexFile.name
    : new Error('id not found in index.yml');
  if (typeof check !== 'string') {
    throw new Error('name in index.yml needs replacement from {name}');
  }

  check = indexFile.description !== undefined && indexFile.description !== null && indexFile.description !== '{description}'
    ? indexFile.description : new Error('description not found in index.yml');
  if (typeof check !== 'string') {
    throw new Error('description in index.yml needs replacement from {description}');
  }

  check = indexFile.url !== undefined && indexFile.url !== null && indexFile.url !== '{url}'
    ? indexFile.url : new Error('url not found in index.yml');
  if (typeof check !== 'string') {
    throw new Error('url in index.yml needs replacement from {url}');
  }

  check = indexFile.imageUrl !== undefined && indexFile.imageUrl !== null ? new URL(indexFile.imageUrl) : true;
  check = indexFile.status !== undefined && indexFile.status !== null ? VspStatusEnum.from(indexFile.status)
    : new Error('status not found in index.yml');

  check = indexFile.aliases !== undefined && indexFile.aliases !== null ? indexFile.aliases : [];
  for (const alias of check) {
    if (typeof alias !== 'string') {
      throw new Error('aliases in index.yml needs to be a string[]');
    }
  }

  const parentType = indexFile.parentType !== undefined && indexFile.parentType !== null ? indexFile.parentType
    : new Error('parentType not found in index.yml');

  let vendor: string;
  let suite: string | undefined;
  if (parentType === 'vendor') {
    check = indexFile.vendorId !== undefined && indexFile.vendorId !== null ? indexFile.vendorId : new Error('vendorId not found in index.yml');
    vendor = indexFile.vendorCode !== undefined && indexFile.vendorCode !== null ? indexFile.vendorCode
      : new Error('vendorCode not found in index.yml');
  } else if (parentType === 'suite') {
    check = indexFile.vendorId !== undefined && indexFile.vendorId !== null ? indexFile.vendorId : new Error('vendorId not found in index.yml');
    vendor = indexFile.vendorCode !== undefined && indexFile.vendorCode !== null ? indexFile.vendorCode
      : new Error('vendorCode not found in index.yml');
    check = indexFile.suiteId !== undefined && indexFile.suiteId !== null ? indexFile.suiteId : new Error('suiteId not found in index.yml');
    suite = indexFile.suiteCode !== undefined && indexFile.suiteCode !== null ? indexFile.suiteCode
      : new Error('suiteCode not found in index.yml');
  } else {
    throw new Error('parentType in index.yml must be either vendor or suite.');
  }
 
  return { code, vendor, suite };
}

async function processArtifact(directory: string) {
  const checkDir = await fs.lstat(directory)
    .catch(() => undefined);

  if (!checkDir || !checkDir.isDirectory()) {
    throw new Error(`Path given is not found or not a directory: ${directory}`);
  }

  const checkIndexYml = await fs.lstat(path.join(directory, 'index.yml'))
    .catch(() => undefined);

  if (!checkIndexYml || !checkIndexYml.isFile()) {
    throw new Error(`index.yml file not found or not file in directory: ${directory}`);
  }

  const indexYml = await readAndParseFile('index.yml', path.join(directory, 'index.yml'));
  if (!indexYml) {
    throw new Error('Unable to parse index.yml');
  }

  const { code, vendor, suite } = await processIndexYml(indexYml);
  console.log('Validated index.yml');
  const checkPackageJson = await fs.lstat(path.join(directory, 'package.json'))
    .catch(() => undefined);

  if (!checkPackageJson || !checkPackageJson.isFile()) {
    throw new Error(`package.json file not found or is not file in directory: ${directory}`);
  }

  const packageJson = await readAndParseFile('package.json', path.join(directory, 'package.json'));
  if (!packageJson) {
    throw new Error('Unable to parse package.json');
  }

  processPackageJson(packageJson, code, vendor, suite);
  console.log('Validated package.json');
  const checkNpmrc = await fs.lstat(path.join(directory, '.npmrc'))
    .catch(() => undefined);

  if (!checkNpmrc || !checkNpmrc.isFile()) {
    throw new Error(`.npmrc file not found or is not file in directory: ${directory}`);
  }

  console.log('Validated .npmrc');
}

(async () => {
  try {
    const directory = './';
    await processArtifact(directory);
    console.log('Validation of artifact completed successfully.');
    process.exit(0);
  } catch (error: any) {
    console.error(`Validation failed \n${error.message}\n${JSON.stringify(error.stack)}`);
    process.exit(1);
  }
})();
