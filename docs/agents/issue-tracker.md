# Issue tracker: Local Markdown

Issues and specs for this repo live as markdown files in `.scratch/`.

## Conventions

- One feature per directory: `.scratch/<feature-slug>/`
- The spec is `.scratch/<feature-slug>/spec.md`
- Implementation issues are one file per ticket at `.scratch/<feature-slug>/issues/<NN>-<slug>.md`, numbered from `01`, never a single combined tickets file
- Triage state is recorded as a `Status:` line near the top of each issue file (see `triage-labels.md` for the role strings)
- Comments and conversation history append to the bottom of the file under a `## Comments` heading

## 本仓库补充

- 当前 feature slug：`教务扩展`（`.scratch/教务扩展/issues/…`）
- 票里的验收标准要能**单独演示或验证**；涉及校内接口时写明「需校园网」，并给出移动数据下的预期失败表现
- 代理完工后：在票底 `## Comments` 追加「改了什么 / 怎么验证 / 遗留」，并把 `Status:` 改为 `ready-for-human`
- 数字与结论必须能追溯到 `docs/_probe/` 或 `docs/URP-学生端接口清单.md` 的实测证据

## When a skill says "publish to the issue tracker"

Create a new file under `.scratch/<feature-slug>/`（不存在就建目录）。

## When a skill says "fetch the relevant ticket"

Read the file at the referenced path（用户会直接给路径或票号）。

## Wayfinding operations

Used by `/wayfinder`. The **map** is a file with one **child** file per ticket.

- **Map**: `.scratch/<effort>/map.md`
- **Child ticket**: `.scratch/<effort>/issues/NN-<slug>.md`，带 `Type:`（research/prototype/grilling/task）与 `Status:`（claimed/resolved）
- **Blocking**: 文件顶部 `Blocked by: NN, NN`；列出的都 `resolved` 才算解锁
- **Frontier**: 扫 `.scratch/<effort>/issues/` 里 open + unblocked + unclaimed 的票，号小的先做
- **Claim**: 开工前写 `Status: claimed`
- **Resolve**: 答案写在 `## Answer` 下，置 `Status: resolved`，并在 `map.md` 的 Decisions-so-far 追加一行指针
