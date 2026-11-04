#!/usr/bin/env node
/**
 * T0.4 护栏②：依赖方向检查（§8.1 四条纪律的机器化，零依赖纯 Node）
 *
 * 正源在 core 子仓（tools/），随 core 指针分发到所有项目——不再有各仓副本漂移。
 * 2026-11-04 自各仓 server/tools/ 迁入；自定位改为上爬两级（本文件在 <host>/server/core/tools/，
 * SERVER=<host>/server）。同轮修正⑤腿历史空转：其扫描路径还写着 L3 切核前的
 * `framework/core/...` 旧前缀，statSync 恒失败静默跳过（F8「护栏空转」同款）——现改
 * `core/auth/...`，配反向探针（往 authorization-api 植 @Service 必红）验证复活。
 *
 * 断言（读 pom.xml 依赖图 + 扫 import 面）：
 *   ① authorization-api / admin-api 不得依赖 authorization(-client) / admin（api 禁反向）
 *   ② authorization-client 不得依赖 authorization（client 只依赖 api）
 *   ②b identity-mirror 不得依赖 authorization(-client)/admin(-api)（T2.3d：镜像消费件只吃
 *      kernel+authorization-api，与 provider 只许 HTTP 快照面交互，进程内引用即红）
 *   ③ 新建模块（authorization-api/client/identity-mirror）不得 import authorization 的非 api 包（service/dao/config 全禁）
 *   ⑤ api 模块禁运行时组件（§8.1：api 只放纯类型/纯机制，组件归 server 或 client）
 *   挂闸门：发现违规退出码 1，否则 0（与 fqn-duplicate-check.mjs 同语义）
 *
 * 用法：node core/tools/dep-direction-check.mjs   （以自身位置定位宿主 server/）
 * 范围：server/ 全部 pom.xml（含 core 子模块树），排除 target/
 */
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const SERVER = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');

function walk(dir, namePred, out = []) {
  let entries;
  try { entries = readdirSync(dir, { withFileTypes: true }); } catch { return out; }
  for (const e of entries) {
    const p = join(dir, e.name);
    if (e.isDirectory()) {
      if (e.name === 'target' || e.name === 'node_modules') continue;
      walk(p, namePred, out);
    } else if (namePred(e.name)) {
      out.push(p);
    }
  }
  return out;
}

const rule1 = [
  { module: 'authorization-api', banned: ['authorization', 'authorization-client', 'admin', 'admin-api'] },
  { module: 'admin-api', banned: ['authorization', 'authorization-client', 'admin'] },
];
const rule2 = [{ module: 'authorization-client', banned: ['authorization', 'admin'] }];
// T2.3d rule3：identity-mirror（镜像消费件）pom 面禁四件——与 mirror 侧 IdentityMirrorConventionTest
// 的 import 面白名单（bind/constants 之外全禁）对偶，两侧合起来钉「镜像零 provider 进程内引用」。
const rule3 = [{ module: 'identity-mirror', banned: ['authorization', 'authorization-client', 'admin', 'admin-api'] }];

let violations = 0;
const fail = (msg) => { violations++; console.error(`✗ ${msg}`); };

const poms = [];
for (const root of readdirSync(SERVER, { withFileTypes: true }).filter(e => e.isDirectory() && e.name !== 'tools')) {
  poms.push(...walk(join(SERVER, root.name), n => n === 'pom.xml'));
}

/** 取 <project> 自身坐标 artifactId：跳过 <parent> 块后第一个 artifactId（deps 里出现的引用不算） */
function projectArtifactId(pomText) {
  const noParent = pomText.replace(/<parent>[\s\S]*?<\/parent>/, '');
  const cut = noParent.indexOf('<dependencies>');
  const head = cut > 0 ? noParent.slice(0, cut) : noParent;
  const m = head.match(/<artifactId>\s*([^<\s]+)\s*<\/artifactId>/);
  return m ? m[1] : null;
}

function pomOf(artifactId) {
  for (const p of poms) {
    const t = readFileSync(p, 'utf8');
    if (projectArtifactId(t) === artifactId) return { p, t };
  }
  return null;
}
function depsOf(pomText) {
  const out = [];
  const re = /<dependencies>([\s\S]*?)<\/dependencies>/g;
  let m;
  while ((m = re.exec(pomText))) {
    for (const d of [...m[1].matchAll(/<artifactId>\s*([^<\s]+)\s*<\/artifactId>/g)]) out.push(d[1]);
  }
  return out;
}

for (const rule of [...rule1, ...rule2, ...rule3]) {
  const found = pomOf(rule.module);
  if (!found) continue; // 模块尚未创建（拆分前基线）——不报错，拆分后自动生效
  const deps = depsOf(found.t);
  for (const b of rule.banned) {
    if (deps.includes(b)) fail(`纪律违规: ${rule.module} 依赖了 ${b}（pom=${found.p.replace(SERVER, '')}）`);
  }
}

// ---- ⑤ api 模块禁运行时组件（§8.1：api 只放纯类型/纯机制，组件归 server 或 client） ----
// 2026-11-04 复活：路径原为 `framework/core/auth/...`（L3 切核前布局），切核后恒 statSync
// 失败静默跳过＝长期空转；本腿已配反向探针（植 @Service 进 authorization-api 必红）。
// 同轮裁定（对齐 0bd5d14a「T2.3c 前半——注解契约与客户端运行时上提 api/client」）：auth 注解族
// 的 AuthRole 默认实现按设计住 api（AuthNone/AuthToken），client 侧 starter 同形态
// （AuthLogin/AuthAdmin/AuthPerms 住 client，不在本腿扫描面）——「api 禁组件」对
// annotation/auth 包豁免；豁免只认包前缀，护栏防扩散能力保留（其余包的运行时组件照红）。
const API_MODULES_SRC = ['core/auth/authorization-api/src/main/java', 'core/auth/admin-api/src/main/java'];
const API_COMPONENT_EXEMPT_PACKAGES = ['com.bidr.authorization.annotation.auth'];
for (const rel of API_MODULES_SRC) {
  const abs = join(SERVER, rel);
  try { statSync(abs); } catch { continue; }
  for (const f of walk(abs, n => n.endsWith('.java'))) {
    const t = readFileSync(f, 'utf8');
    const pkgM = t.match(/^\s*package\s+([\w.]+)\s*;/m);
    const pkg = pkgM ? pkgM[1] : '';
    if (API_COMPONENT_EXEMPT_PACKAGES.some(p => pkg === p || pkg.startsWith(p + '.'))) continue;
    const m = t.match(/^@(Component|Service|Configuration|Aspect|RestController|Controller|Repository)([\s(]|$)/gm) || [];
    for (const line of m) fail(`纪律违规: ${f.replace(SERVER, '')} 是 api 模块却含运行时组件注解 ${line.trim()}（应移入 server/client；annotation/auth 包按 T2.3c 豁免）`);
  }
}

// ---- ③ 新建模块 import 面（T2.3c 后 FQN 级判定） ----
// 语义不变：api/client 不得 import authorization(server) 独占的类型。旧实现按
// service/config/utils 等包前缀判——T2.3c 起这些子包按类型面上提（service.token/utils.token/
// utils.jwt/config.security 等迁入 api/client，同 FQN 跨 jar），包前缀会误伤共享子包；
// 改为现算 server main 源码的类清单，import 命中 server 实际持有的类（含嵌套成员、
// 通配包）才算违规。
function fqnOf(file) {
  const head = readFileSync(file, 'utf8').split(/\r?\n/, 20).join('\n');
  const m = head.match(/^\s*package\s+([\w.]+)\s*;/m);
  const cls = file.split(/[\\/]/).pop().replace(/\.java$/, '');
  return m ? `${m[1]}.${cls}` : `(default)${cls}`;
}

const NEW_MODULE_SRC = [
  'core/auth/authorization-api/src/main/java',
  'core/auth/authorization-client/src/main/java',
  // T2.3d：identity-mirror 的 import 面同样按 server FQN 清单判（反向探针形态：
  // 植 import com.bidr.authorization.service.permit.PermitService 进 mirror 必须红）
  'core/auth/identity-mirror/src/main/java',
];
const serverFqns = new Set();
try {
  for (const f of walk(join(SERVER, 'core/auth/authorization/src/main/java'), n => n.endsWith('.java'))) {
    serverFqns.add(fqnOf(f));
  }
} catch { /* server 模块缺席（拆分前基线）——空清单即零违规 */ }

const javaFiles = [];
for (const rel of NEW_MODULE_SRC) {
  const abs = join(SERVER, rel);
  try { statSync(abs); } catch { continue; }
  javaFiles.push(...walk(abs, n => n.endsWith('.java')));
}
for (const f of javaFiles) {
  const t = readFileSync(f, 'utf8');
  const imports = t.match(/^import\s+(?:static\s+)?com\.bidr\.authorization\.[\w.*]+\s*;/gm) || [];
  for (const line of imports) {
    const target = line.replace(/^import\s+(?:static\s+)?/, '').replace(/\s*;$/, '');
    if (target.endsWith('.*')) {
      const pkg = target.slice(0, -2);
      if ([...serverFqns].some(s => s.startsWith(pkg + '.'))) {
        fail(`纪律违规: ${f.replace(SERVER, '')} 通配 import authorization(server) 包 → ${line.trim()}`);
      }
      continue;
    }
    // static import / 嵌套成员 → 外围类 FQN 判定
    const outer = target.replace(/\.\w+$/, '');
    if (serverFqns.has(target) || serverFqns.has(outer)) {
      fail(`纪律违规: ${f.replace(SERVER, '')} import authorization(server) 独占类 → ${line.trim()}`);
    }
  }
}

console.log(`pom 扫描 ${poms.length}｜新模块源文件 ${javaFiles.length}｜违规: ${violations}`);
process.exit(violations ? 1 : 0);
