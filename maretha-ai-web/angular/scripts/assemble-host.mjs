#!/usr/bin/env node
// Compose the add-on into an isolated host build; the source host custom/ and
// node_modules remain untouched. Existing authenticated Maven is used normally.
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { cpSync, existsSync, lstatSync, mkdirSync, readFileSync, readdirSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const angularRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const addonRoot = path.resolve(angularRoot, '../..');
const readJson = file => JSON.parse(readFileSync(file, 'utf8'));
const run = (command, args, cwd) => execFileSync(command, args, { cwd, stdio: 'inherit' });
const hash = file => createHash('sha256').update(readFileSync(file)).digest('hex');
const secretPath = file => file.split('/').some(part => /^\.env(?:\.|$)/i.test(part) || ['.aws', '.ssh', '.codex', '.agents', '.claude', '.git'].includes(part)) || /\.(?:har|pem|p12|pfx|key|keystore)$/i.test(file) || /(?:^|\/)(?:memory\.md|_MANIFEST\.md|HANDOFF[^/]*\.md)$/.test(file);

/** Each package link has its own entry so scoped overrides never alter shared deps. */
export function linkDependencies(source, target) {
  mkdirSync(target, { recursive: true });
  for (const name of readdirSync(source)) {
    if (name.startsWith('@')) {
      mkdirSync(path.join(target, name), { recursive: true });
      for (const child of readdirSync(path.join(source, name))) symlinkSync(path.join(source, name, child), path.join(target, name, child));
    } else symlinkSync(path.join(source, name), path.join(target, name));
  }
}

/** Stage the current Polymer asset used by the Angular glyph regression test. */
export function stageCanonicalIconFixture(sourceAddonRoot, libraryRoot) {
  const fixture = path.join(libraryRoot, 'tests/fixtures/cp-custom-icons.html');
  mkdirSync(path.dirname(fixture), { recursive: true });
  cpSync(path.join(sourceAddonRoot, 'maretha-ai-web/src/main/resources/web/nuxeo.war/ui/cp-custom-icons.html'), fixture);
}

function linkPackage(modules, name, destination) {
  const target = path.join(modules, name);
  mkdirSync(path.dirname(target), { recursive: true });
  rmSync(target, { force: true, recursive: true });
  symlinkSync(destination, target);
}

function stageCore(source, destination) {
  const files = execFileSync('git', ['ls-files', '-z', '--cached', '--others', '--exclude-standard'], { cwd: source, encoding: 'utf8' }).split('\0').filter(Boolean);
  const digest = createHash('sha256');
  for (const file of [...new Set(files)].sort()) {
    if (secretPath(file) || /^(?:node_modules|dist|\.angular|out-tsc)\//.test(file)) continue;
    const input = path.join(source, file);
    if (!existsSync(input) || !lstatSync(input).isFile()) continue;
    const output = path.join(destination, file);
    mkdirSync(path.dirname(output), { recursive: true });
    cpSync(input, output);
    digest.update(`${file}\0${hash(input)}\n`);
  }
  return digest.digest('hex');
}

export function assemble(coreRoot, outputRoot) {
  coreRoot = path.resolve(coreRoot); outputRoot = path.resolve(outputRoot);
  if (existsSync(outputRoot)) throw new Error('Choose a new assembly output directory.');
  const corePackage = readJson(path.join(coreRoot, 'package.json'));
  const cpPackage = readJson(path.join(angularRoot, 'package.json'));
  const sdkPackage = readJson(path.join(coreRoot, 'app/extensions/package.json'));
  if (corePackage.version !== '3.21.4' || cpPackage.version !== '1.0.30') throw new Error('Expected host 3.21.4 and add-on 1.0.30.');
  if (sdkPackage.version !== '0.1.2') throw new Error('Expected WebUI SDK 0.1.2.');
  const sharedModules = path.join(coreRoot, 'node_modules');
  if (!existsSync(sharedModules)) throw new Error('Install the host dependencies before assembly.');
  run('npm', ['run', 'build:sdk'], coreRoot);
  mkdirSync(outputRoot, { recursive: true });
  const libraryRoot = path.join(outputRoot, 'cp-library');
  mkdirSync(libraryRoot);
  for (const name of ['package.json', 'ng-package.json', 'tsconfig.lib.json', 'src', 'tests', 'scripts', 'README.md']) {
    const input = path.join(angularRoot, name);
    if (existsSync(input)) cpSync(input, path.join(libraryRoot, name), { recursive: true });
  }
  stageCanonicalIconFixture(addonRoot, libraryRoot);
  linkDependencies(sharedModules, path.join(libraryRoot, 'node_modules'));
  linkPackage(path.join(libraryRoot, 'node_modules'), '@maretha/webui-sdk', path.join(coreRoot, 'dist/webui-sdk'));
  run('npm', ['test'], libraryRoot);
  // Maven embeds this small distributable in META-INF, never the compiled host app.
  const addonDist = path.join(angularRoot, 'dist');
  rmSync(addonDist, { force: true, recursive: true });
  cpSync(path.join(libraryRoot, 'dist'), addonDist, { recursive: true });

  const hostRoot = path.join(outputRoot, 'host');
  mkdirSync(hostRoot);
  const sourceSha256 = stageCore(coreRoot, hostRoot);
  linkDependencies(sharedModules, path.join(hostRoot, 'node_modules'));
  linkPackage(path.join(hostRoot, 'node_modules'), '@maretha/cp-webui', path.join(libraryRoot, 'dist'));
  mkdirSync(path.join(hostRoot, 'custom'), { recursive: true });
  writeFileSync(path.join(hostRoot, 'custom/index.ts'), "import { provideCpWebUi } from '@maretha/cp-webui';\nexport function provideCustomExtensions() { return provideCpWebUi(); }\n");
  writeFileSync(path.join(hostRoot, 'custom/dependencies.allow.json'), JSON.stringify({ packages: ['@maretha/cp-webui'] }, null, 2) + '\n');
  const stagedPackage = readJson(path.join(hostRoot, 'package.json'));
  stagedPackage.dependencies = { ...stagedPackage.dependencies, '@maretha/cp-webui': '1.0.30' };
  writeFileSync(path.join(hostRoot, 'package.json'), JSON.stringify(stagedPackage, null, 2) + '\n');
  // This composed distribution includes the CP settings, schema forms and search UI.
  // Keep the core's 6 MB ceiling unchanged; cap this add-on assembly at 6.5 MB.
  const angularConfigPath = path.join(hostRoot, 'angular.json');
  const angularConfig = readJson(angularConfigPath);
  const budgets = angularConfig.projects['maretha-angular-webui'].architect.build.configurations.production.budgets;
  const initialBudget = budgets.find(budget => budget.type === 'initial');
  initialBudget.maximumWarning = '6mb'; initialBudget.maximumError = '6.5mb';
  writeFileSync(angularConfigPath, JSON.stringify(angularConfig, null, 2) + '\n');
  run('npm', ['run', 'package:nuxeo'], hostRoot);
  run('mvn', ['-B', 'verify'], addonRoot);
  const hostZip = path.join(hostRoot, 'nuxeo/package/target/maretha-angular-webui-package-3.21.4.zip');
  const addonZip = path.join(addonRoot, 'maretha-ai-package/target/maretha-ai-package-1.0.30.zip');
  if (!existsSync(hostZip) || !existsSync(addonZip)) throw new Error('Expected release packages were not produced.');
  const manifest = {
    createdAt: new Date().toISOString(), coreVersion: corePackage.version, addonVersion: cpPackage.version,
    coreRevision: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: coreRoot, encoding: 'utf8' }).trim(),
    addonRevision: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: addonRoot, encoding: 'utf8' }).trim(),
    coreSourceSha256: sourceSha256, initialBundleBudget: '6.5mb',
    nodeVersion: process.version,
    packages: [{ path: hostZip, sha256: hash(hostZip) }, { path: addonZip, sha256: hash(addonZip) }],
  };
  writeFileSync(path.join(outputRoot, 'release-manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  console.log(JSON.stringify(manifest, null, 2));
  return manifest;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  if (process.argv.length < 3 || process.argv.length > 4) {
    console.error('Usage: node maretha-ai-web/angular/scripts/assemble-host.mjs CORE_CHECKOUT [NEW_OUTPUT_DIRECTORY]');
    process.exit(2);
  }
  assemble(process.argv[2], process.argv[3] || path.join(tmpdir(), `maretha-cp-host-${Date.now()}`));
}
