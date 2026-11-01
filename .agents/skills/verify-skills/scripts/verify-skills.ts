/**
 * Verify skills: one SKILL.md per skill dir, complete frontmatter, and every
 * referenced path resolves. Prints findings for a model to group and explain.
 * Run from the repo root: npm run verify-skills [-- --json]
 */
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { join, resolve } from "node:path";

type Severity = "error" | "warning";

type Finding = {
  severity: Severity;
  code: string;
  skill: string;
  file: string;
  line?: number;
  target?: string;
  message: string;
};

const ROOT = process.cwd();
const SKILLS_DIR = resolve(ROOT, ".agents/skills");
const AS_JSON = process.argv.includes("--json");

// Repo-absolute reference: `.agents/skills/how/SKILL.md`
const ABS_PATTERN = /`(\.agents\/[^`\s]+)`/g;
// Skill-relative reference: `references/epistemics.md`
const REL_PATTERN = /`((?:references|scripts|assets|templates|playbooks)\/[^`\s]+\.[a-z0-9]+)`/g;
// Placeholders like {lifecycle} or 2026-…-….md are not checkable
const PLACEHOLDER = /[{*}<>?…]/;
const HAS_EXTENSION = /\/[^/\s]*\.[A-Za-z0-9]+$/;

const findings: Finding[] = [];
let checkedPaths = 0;

const skillNames = readdirSync(SKILLS_DIR, { withFileTypes: true })
  .filter((entry) => entry.isDirectory())
  .map((entry) => entry.name)
  .sort();

for (const name of skillNames) {
  const dir = join(SKILLS_DIR, name);
  const skillFile = join(dir, "SKILL.md");
  const relFile = `.agents/skills/${name}/SKILL.md`;

  if (!existsSync(skillFile)) {
    findings.push({
      severity: "error",
      code: "missing-skill-file",
      skill: name,
      file: `.agents/skills/${name}/`,
      message: "目录里没有 SKILL.md，技能不会被发现",
    });
    continue;
  }

  const raw = readFileSync(skillFile, "utf8");

  const frontmatter = /^---\r?\n([\s\S]*?)\r?\n---/.exec(raw);
  if (!frontmatter) {
    findings.push({
      severity: "error",
      code: "no-frontmatter",
      skill: name,
      file: relFile,
      line: 1,
      message: "开头没有 --- 包围的 frontmatter",
    });
  } else {
    const block = frontmatter[1] ?? "";
    const declared = /^name:\s*(.+)$/m.exec(block)?.[1]?.trim();
    const description = /^description:\s*(.+)$/m.exec(block)?.[1]?.trim();

    if (!declared) {
      findings.push({
        severity: "error",
        code: "missing-name",
        skill: name,
        file: relFile,
        line: 2,
        message: "frontmatter 缺 name",
      });
    } else if (declared !== name) {
      findings.push({
        severity: "warning",
        code: "name-mismatch",
        skill: name,
        file: relFile,
        line: 2,
        target: declared,
        message: `name 是 "${declared}"，与目录名 "${name}" 不一致`,
      });
    }

    if (!description) {
      findings.push({
        severity: "error",
        code: "missing-description",
        skill: name,
        file: relFile,
        line: 3,
        message: "frontmatter 缺 description，技能不会被触发",
      });
    }
  }

  // Blank out fenced blocks but keep their newlines, so line numbers stay true.
  const body = raw.replace(/```[\s\S]*?```/g, (block) => block.replace(/[^\n]/g, ""));

  body.split(/\r?\n/).forEach((line, index) => {
    for (const match of line.matchAll(ABS_PATTERN)) {
      const target = match[1] ?? "";
      if (PLACEHOLDER.test(target) || !HAS_EXTENSION.test(target)) continue;
      checkedPaths++;
      if (!existsSync(resolve(ROOT, target))) {
        findings.push({
          severity: "error",
          code: "broken-path",
          skill: name,
          file: relFile,
          line: index + 1,
          target,
          message: "引用的仓库路径不存在",
        });
      }
    }

    for (const match of line.matchAll(REL_PATTERN)) {
      const target = match[1] ?? "";
      if (PLACEHOLDER.test(target)) continue;
      checkedPaths++;
      if (!existsSync(resolve(dir, target))) {
        findings.push({
          severity: "error",
          code: "broken-relative-path",
          skill: name,
          file: relFile,
          line: index + 1,
          target,
          message: "引用的技能内路径不存在",
        });
      }
    }
  });
}

const errors = findings.filter((f) => f.severity === "error");
const warnings = findings.filter((f) => f.severity === "warning");

if (AS_JSON) {
  console.log(
    JSON.stringify(
      { skills: skillNames.length, checkedPaths, errors: errors.length, warnings: warnings.length, findings },
      null,
      2,
    ),
  );
} else {
  for (const finding of findings) {
    const where = finding.line ? `${finding.file}:${finding.line}` : finding.file;
    const label = finding.severity === "error" ? "error" : "warn ";
    const target = finding.code.endsWith("-path") && finding.target ? ` -> ${finding.target}` : "";
    console.log(`${label} [${finding.code}] ${where}${target} ${finding.message}`);
  }
  console.log(
    `checked: ${skillNames.length} skill(s), ${checkedPaths} path reference(s), ${errors.length} error(s), ${warnings.length} warning(s)`,
  );
}

if (errors.length) {
  process.exitCode = 1;
}
