#!/usr/bin/env node
/**
 * T0.4 护栏①：一个类全平台只住一个模块——FQN 防重扫描
 *
 * 正源在 core 子仓（tools/），随 core 指针分发到所有项目——不再有各仓副本漂移。
 * 2026-11-04 自各仓 server/tools/ 迁入；自定位改为上爬两级（本文件在 <host>/server/core/tools/，
 * SERVER=<host>/server），扫描面仍是宿主仓全 server/（业务模块抄 core FQN 才是最大风险面，
 * 不能只扫 core 自己）。单独 clone core 仓时没有宿主业务树，walk 容忍目录缺席＝零违规通过。
 *
 * 背景：core 拆分采用"同包名跨 jar"形态（authorization-api 同包名搬类）。
 * classpath 下同包跨 jar 合法，但同一个 FQN 出现在两个 jar 会被类路径顺序静默裁决——
 * 这是该拆法唯一的风险点，本脚本在 CI/本地把它变成显式失败。
 *
 * 用法：node core/tools/fqn-duplicate-check.mjs   （cwd 任意，脚本以自身位置定位宿主 server/）
 * 退出码：0=无重复；1=发现重复（打印全部冲突对）
 * 范围：server/ 下全部 *src/main/java（含 core 子模块树），排除 target/
 */
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const SERVER = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');

function walkJava(dir, out = []) {
  let entries;
  try { entries = readdirSync(dir, { withFileTypes: true }); } catch { return out; }
  for (const e of entries) {
    const p = join(dir, e.name);
    if (e.isDirectory()) {
      if (e.name === 'target' || e.name === 'node_modules') continue;
      walkJava(p, out);
    } else if (e.name.endsWith('.java')) {
      out.push(p);
    }
  }
  return out;
}

function fqnOf(file) {
  const head = readFileSync(file, 'utf8').split(/\r?\n/, 20).join('\n');
  const m = head.match(/^\s*package\s+([\w.]+)\s*;/m);
  const cls = file.split(/[\\/]/).pop().replace(/\.java$/, '');
  return m ? `${m[1]}.${cls}` : `(default)${cls}`;
}

const roots = readdirSync(SERVER, { withFileTypes: true })
  .filter(e => e.isDirectory() && e.name !== 'target' && e.name !== 'tools' && e.name !== 'build-cache')
  .map(e => join(SERVER, e.name));

const files = [];
for (const r of roots) files.push(...walkJava(r));

const byFqn = new Map();
for (const f of files) {
  const fqn = fqnOf(f);
  if (!byFqn.has(fqn)) byFqn.set(fqn, []);
  byFqn.get(fqn).push(f.replace(SERVER, ''));
}

let bad = 0;
for (const [fqn, locs] of [...byFqn].sort()) {
  if (locs.length > 1) {
    bad++;
    console.error(`✗ FQN 重复: ${fqn}`);
    for (const l of locs) console.error(`    ${l}`);
  }
}
console.log(`扫描 ${files.length} 个 .java｜FQN 重复: ${bad}`);
process.exit(bad ? 1 : 0);
