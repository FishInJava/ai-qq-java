# 按需阅读：校验脚本

> SKILL §6 的展开。接入 CI 或排查红叉时对照；日常轻量使用时只需记住 `npm run verify-notes`。

## 本仓库的运行方式

根目录 `package.json` 已配好入口，Node ≥ 24 用类型剥离直接执行 `.ts`：不装 `tsx`、不产生 `node_modules`、不需要 `npm install`。所有命令都在**仓库根**执行。

```sh
npm run verify-notes          # 三线串跑（提交前、CI 用这个）
npm run verify-note-tree      # 目录 + 链接
npm run verify-note-format    # 头块 + 骨架
npm run verify-archived       # 归档封印
npm run verify-archived-write # 为历史归档补封印
```

对应关系：上游文档里的 `npx tsx <skill-dir>/scripts/xxx.ts` 在本仓库一律换成上表的 `npm run xxx`；脚本本身仍在 `.agents/skills/write-notes-like-deepseek/scripts/`。

环境变量（脚本直接读取）：

- `AGENT_NOTE_ROOT` — 笔记根，默认 `<cwd>/.agents/notes`。
- `AGENT_NOTE_ARCHIVE_BASE_REF` — 归档 append-only 的 git 基线，默认 `HEAD`；CI 里必须指向变更前的 commit。
- `AGENT_NOTE_CODE_ROOT` — `check-anchors` 的源码扫描根，默认 `<cwd>`。

## 检查脚本

1. **`verify-agent-note-tree`**（`scripts/agent-note-tree.ts` + `scripts/verify-agent-note-tree.ts`）
   - 校验 lifecycle 封闭集 `proposed/implemented/rejected` + `archived`、class 封闭集 6 个、路径深度 `{lifecycle}/{class}/file.md`、文件名 `yyyy-mm-dd-topic.md`、禁止 `INDEX.md`、活跃笔记内部相对 Markdown 链接有效性。
   - 笔记根不存在时静默通过（0 篇），不会因为还没建目录就变红。

2. **`verify-agent-note-format`**（`scripts/verify-agent-note-format.ts`）
   - 头部：第 1 行 `# Agent Note:` 标题（半角/全角冒号都收）、第 2/4 行空行、第 3 行 `Status:` 与 lifecycle 一致且全篇唯一。
   - 骨架：首节必须 `## Problem`/`## 问题`；per-lifecycle 必需节匹配中英别名（`## Decision`/`## 决策`、`## Consequences`/`## 后果` 等；`## Decision（说明）` 这类括号后缀会先剥掉再匹配）；`implemented` 禁用提案式标题（`## Proposal`/`## Plan`/`## Migration plan`/`## Acceptance criteria` 及其中文别名）。现在时是散文纪律，不扫正文。
   - 备选方案：`## Alternatives considered` / `## 备选方案` / `## 已考虑的替代方案` 等别名必写。脚本不检查「不做/复用」档，也不接受占位注释豁免。
   - 兼容：CRLF/BOM 自动归一（本仓库 `.gitattributes` 统一 LF，但 Windows 编辑器仍可能写出 CRLF）。

3. **`verify-archived`**（`scripts/verify-archived-agent-notes.ts`）
   - 每篇归档：头部布局逐行校验（L3 `Status: implemented` / L4 `Archived: YYYY-MM-DD` 紧邻 / L5 空行）。不承认 `Superseded-by:` 字段。
   - `archived/manifest.json`：每个文件有封印条目、每条目有对应文件、sha256 与磁盘内容一致（冻结 = 不可篡改的机械含义）。
   - append-only：有 git 时与基线 ref 的 manifest 逐条对比。已封印条目被改/删即报错；**无 git 自动降级**为哈希自校验并打印提示——不用 git 一样能跑，只是少了版本基线对照。
   - `--write`（`npm run verify-archived-write`）：先证明既有封印未变，再为未封印文件追加封印（历史语料补齐用这个）。

4. **`check-note-anchors`**（`scripts/check-note-anchors.ts`，软报告，退出码恒 0，**不进 CI**）
   - 扫描 `// Note:` / `# Note:` 物理锚点：报告悬空锚点、没有锚点指向的 implemented 笔记、缺路径的锚点行。
   - **本仓库不写源码锚点**（`RULES.md` 限定注释只写方法功能），所以这个脚本日常不用跑；留着是为了将来规则放宽时可直接启用。

## 接入建议

- 轻量（个人 + git）：`npm run verify-notes` 作为提交前手跑；归档封印在第一次归档后自然生效。
- 团队（要 CI）：仓库当前没有 `.github/workflows/`。真要接，把 `templates/verify-notes.yml` 复制过去，命令改为 `npm run verify-notes`，并核对 `AGENT_NOTE_ARCHIVE_BASE_REF` 指向变更前 commit。给纯 Java 构建加 Node 步骤前先确认收益，别为了门禁引入第二套工具链。

三个校验脚本方法上来自 [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness)，按通用中文单语宿主裁过：无双语三件套、无 DSH 迁仓豁免。锚点体检是可选软报告，不是 DSH 门禁。
