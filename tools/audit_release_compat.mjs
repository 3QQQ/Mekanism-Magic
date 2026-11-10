// Verify the hotfix against the exact published 1.0.7 artifact, not a development build.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {createRequire} from 'node:module';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const read = file => fs.readFileSync(path.join(root, file));
const sha = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
const check = (ok, message) => { if (!ok) throw new Error(message); };
const baselineCommit = 'f66df66ab443dbf9f0272a2019f726cb2eeeab95';
const baselineSha = 'b4ff6af25f13216031415b33604a009d9c7cbe71ec1b40497b47b8d7aa392232';
const owner = file => file.endsWith('.class') ? file.slice(0, -6).split('$')[0] : null;
const oldOwner = 'com/example/mekanismmagic/integration/ModCompatibility';
const newOwner = 'com/example/mekanismmagic/integration/IntegrationAbiVerifier';
const debugOnlyClass = 'com/example/mekanismmagic/integration/arsnouveau/MagicSourceTransmitter$1.class';
const report = {status: 'FAIL', baseline: {version: '1.0.7', commit: baselineCommit, sha256: baselineSha},
  source: {changed: [], added: []}, jar: {changed: [], added: [], compilerDebugOnly: [], unchangedClasses: 0, unchangedResources: 0}, errors: []};
const roots = ['src/main', 'src/ae2', 'src/mekenergistics', 'src/jade', 'src/arsDev', 'src/generated'];
function walk(dir) {
  if (!fs.existsSync(path.join(root, dir))) return [];
  return fs.readdirSync(path.join(root, dir), {withFileTypes: true}).flatMap(entry => {
    check(!entry.isSymbolicLink(), `Unexpected runtime source link: ${dir}/${entry.name}`);
    const file = `${dir}/${entry.name}`; return entry.isDirectory() ? walk(file) : [file];
  });
}
// javac inherits constructor local-variable debug names from dependency metadata.
// Verify this one known compiler-only difference by restoring only its three unused UTF8 names;
// every other constant-pool entry and every byte of fields/methods/attributes must then match.
function canonicalDebugNames(bytes) {
  check(bytes.readUInt32BE(0) === 0xCAFEBABE, 'Invalid class file');
  const names = new Map([['capacity', 'arg0'], ['maxReceive', 'arg1'], ['maxExtract', 'arg2']]);
  const chunks = [bytes.subarray(0, 10)], changed = new Set(), referenced = new Set();
  let at = 10;
  for (let i = 1; i < bytes.readUInt16BE(8); i++) {
    const start = at, tag = bytes[at++];
    if (tag === 1) {
      const length = bytes.readUInt16BE(at); at += 2;
      const value = bytes.subarray(at, at + length).toString('utf8'); at += length;
      if (names.has(value)) {
        const replacement = Buffer.from(names.get(value)), header = Buffer.alloc(3);
        header[0] = 1; header.writeUInt16BE(replacement.length, 1);
        chunks.push(header, replacement); changed.add(i); continue;
      }
    } else if ([3, 4, 9, 10, 11, 17, 18].includes(tag)) at += 4;
    else if ([5, 6].includes(tag)) { at += 8; i++; }
    else if ([7, 8, 16, 19, 20].includes(tag)) { referenced.add(bytes.readUInt16BE(at)); at += 2; }
    else if (tag === 12) { referenced.add(bytes.readUInt16BE(at)); referenced.add(bytes.readUInt16BE(at + 2)); at += 4; }
    else if (tag === 15) at += 3;
    else throw new Error(`Unsupported constant tag: ${tag}`);
    chunks.push(bytes.subarray(start, at));
  }
  check(changed.size === 3 && [...changed].every(index => !referenced.has(index)), 'Debug-name normalization touched runtime symbol data');
  chunks.push(bytes.subarray(at)); return Buffer.concat(chunks);
}
async function main() {
  const version = read('gradle.properties').toString().match(/^mod_version=(\S+)\s*$/m)?.[1];
  check(version === '1.0.7fix', 'Release must use the user-requested version');
  const git = args => execFileSync('git', args, {cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe']}).trim().split(/\r?\n/).filter(Boolean);
  const tracked = new Set(git(['ls-tree', '-r', '--name-only', baselineCommit, '--', ...roots]));
  report.source.changed = git(['diff', '--name-only', baselineCommit, '--', ...roots]).filter(file => tracked.has(file));
  check(report.source.changed.length === 1 && report.source.changed[0] === `src/main/java/${oldOwner}.java`,
    `Unexpected production source change: ${report.source.changed.join(', ')}`);
  report.source.added = roots.flatMap(walk).filter(file => !tracked.has(file));
  check(report.source.added.length === 1 && report.source.added[0] === `src/main/java/${newOwner}.java`, 'Unreleased source/resource content leaked into the hotfix');
  const beforeBytes = read('build/release-baseline/mekanism_magic-1.0.7-neoforge-1.21.1.jar');
  check(sha(beforeBytes) === baselineSha, 'Published baseline artifact changed');
  const jar = `build/libs/mekanism_magic-${version}-neoforge-1.21.1.jar`, bytes = read(jar);
  let JSZip; try { JSZip = createRequire(import.meta.url)('jszip'); }
  catch { JSZip = createRequire(path.join(path.dirname(process.execPath), '../node_modules/_codex_loader.cjs'))('jszip'); }
  const before = await JSZip.loadAsync(beforeBytes, {checkCRC32: true}), after = await JSZip.loadAsync(bytes, {checkCRC32: true});
  for (const [name, entry] of Object.entries(before.files)) if (!entry.dir) {
    check(after.file(name), `Removed original release entry: ${name}`);
    const oldBytes = await entry.async('nodebuffer'), newBytes = await after.file(name).async('nodebuffer');
    if (!oldBytes.equals(newBytes)) {
      if (name === debugOnlyClass) {
        check(canonicalDebugNames(newBytes).equals(oldBytes), 'Anonymous constructor differs beyond local-variable debug names');
        report.jar.compilerDebugOnly.push({name, before: sha(oldBytes), after: sha(newBytes), canonical: sha(oldBytes)});
        continue;
      }
      report.jar.changed.push(name);
      check(name === oldOwner + '.class' || name === 'META-INF/neoforge.mods.toml', `Original machine/UI/resource changed: ${name}`);
    } else if (owner(name)) report.jar.unchangedClasses++; else report.jar.unchangedResources++;
  }
  const abiPath = 'META-INF/mekanism_magic/compatibility-abi.json';
  let classes = 0;
  for (const [name, entry] of Object.entries(after.files)) if (!entry.dir) {
    if (!before.file(name)) {
      report.jar.added.push(name);
      check(owner(name) === newOwner || name === abiPath, `Unexpected hotfix addition: ${name}`);
    }
    if (owner(name)) {
      check(!/(?:GameTests?|SelfTest|Smoke|releasetest|build\/CompatibilityAbi)/.test(name), `Test/generator in runtime artifact: ${name}`);
      check(read(`build/classes/java/main/${name}`).equals(await entry.async('nodebuffer')), `Stale compiled class: ${name}`); classes++;
    }
  }
  report.jar.preservedBehaviorClasses = report.jar.unchangedClasses + report.jar.compilerDebugOnly.length;
  check(report.jar.preservedBehaviorClasses === 785, 'The original 786-class release was not preserved');
  const normalize = bytes => bytes.toString('utf8').replace(/^version="[^"]+"$/gm, 'version="<version>"');
  const metadata = await after.file('META-INF/neoforge.mods.toml').async('nodebuffer');
  check(normalize(await before.file('META-INF/neoforge.mods.toml').async('nodebuffer')) === normalize(metadata), 'Unrelated loader/dependency metadata changed');
  check(metadata.toString().includes(`version="${version}"`), 'Packaged version mismatch');
  const abiBytes = await after.file(abiPath).async('nodebuffer');
  check(abiBytes.equals(read(`build/generated/compatibility-abi/${abiPath}`)), 'Stale API contracts');
  const abi = JSON.parse(abiBytes);
  check(abi.schemaVersion === 1 && Object.keys(abi.integrations).join() === 'forbidden_arcanus', 'Development integrations leaked into validation data');
  const group = abi.integrations.forbidden_arcanus;
  check(group.classes.length > 0 && group.fields.length > 0 && group.methods.length > 0, 'Empty ABI contracts');
  report.apiRequirements = {classes: group.classes.length, fields: group.fields.length, methods: group.methods.length, sha256: sha(abiBytes)};
  report.target = {version, jar, bytes: bytes.length, sha256: sha(bytes), sha1: crypto.createHash('sha1').update(bytes).digest('hex'), classes};
  report.status = 'PASS';
}
try { await main(); } catch (error) { report.errors.push(error.stack ?? String(error)); process.exitCode = 1; }
console.log(JSON.stringify(report, null, 2));
