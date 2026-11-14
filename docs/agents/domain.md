# Domain Docs

单上下文仓库：根目录一份 `GLOSSARY.md` + `docs/adr/`。

## Before exploring, read these

- `GLOSSARY.md`（仓库根）
- `docs/adr/` 里与本次改动区域相关的 ADR

**当前两者都还不存在** —— 缺失时不要声张、不要建议先建，直接干活。
真正出现术语分歧或决策落定时，由 `/domain-modeling`（经 `/grill-with-docs`、`/improve-codebase-architecture`）懒生成。

## 已有的事实来源（代替 GLOSSARY 先用）

- `docs/URP-学生端接口清单.md` —— 接口路径、字段名、会话粘滞等实测结论
- `docs/SYAU-校内平台清单与可嵌入性评估.md` —— 校内系统拓扑
- 代码里的既有词汇：`大节 BigSession`、`小节 session(1..12)`、`节次`、`侵蚀 eatProgress`、
  `频道 channel`、`红点三级`、`风格 StyleSet`、`作息 SchoolClock`
  —— 票与注释沿用这些说法，不要另造同义词。

## Flag ADR conflicts

若结论与既有 ADR 冲突，显式写出来（例如「与 ADR-0007 冲突，但值得重开，因为…」），不要静默覆盖。
