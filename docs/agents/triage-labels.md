# Triage Labels

五个角色串，label 字符串等于角色名：

| 角色串 | 含义 | 本仓库用法 |
|---|---|---|
| `needs-triage` | 刚记下，还没判断 | 新票默认不用它（拆票时已经判断过） |
| `needs-info` | 缺关键信息，做不动 | 例：接口返回形态未知、需要真机再抓一次 |
| `ready-for-agent` | 可以派给子代理 | 拆票产出时默认状态 |
| `ready-for-human` | 代码已改，等人上真机验证 | 代理完工后把 `Status:` 改成这个 |
| `wontfix` | 明确不做 | 例：写操作类接口（选课/退课/报名/评教提交） |

工单是本地 markdown 文件，所以状态记录在票里的 `Status:` 行，不是 GitHub label。
