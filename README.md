# RepoReady：Git 仓库接入体检

RepoReady 面向团队接入外部仓库前的初步评估。输入公开 GitHub 仓库链接，系统针对默认分支当前指向的**一个固定 commit**，检查项目说明、依赖与启动入口、平台专用脚本、硬编码路径和疑似敏感信息，并展示 0–100 就绪度评分、风险位置及扫描覆盖情况。它帮助确定接入前需要优先处理的问题，不能替代人工审查、代码执行验证或专业安全扫描。

## 项目结构与技术栈

| 目录 | 内容 | 技术栈 |
| --- | --- | --- |
| `RepoReady/` | 扫描 API、GitHub API 客户端、规则与测试 | Java 8、Spring Boot 2.3.12、Maven |
| `RepoReady-fe/` | 仓库输入、扫描状态、评分与风险报告 | Vue 3、Vite、Element Plus 2.11.8 |

后端扫描规则位于 `ScanRules`，扫描编排位于 `ScanService`。当前没有数据库、用户账户、历史报告或 AI 模型调用；报告由每次请求同步生成。AI 建议是题目加分项，因当前 Java 8 / Spring Boot 2.3 与计划使用的 Spring AI Alibaba 版本不兼容，首版保持可复现的规则评分。

## 本地启动

需要 JDK 8 或更高版本、Maven 3、Node.js 20 与 npm。分别启动后端和前端：

```powershell
cd D:\proIDEA\AIcoding\RepoReady
mvn spring-boot:run
```

```powershell
cd D:\proIDEA\AIcoding\RepoReady-fe
npm ci
npm run dev
```

打开 Vite 输出的本地地址。后端默认端口为 `8084`，前端开发代理将 `/api` 转发至该端口。若修改后端端口，应同步调整 `RepoReady-fe/vite.config.js` 的代理目标。

公开仓库可不配置令牌，但 GitHub 对匿名请求限流更严格。建议在启动后端的**同一终端或 IDE Run Configuration** 中设置 `GITHUB_TOKEN` 环境变量，随后重启后端。不要将令牌写入 `application.yml`、命令示例或 Git 提交。启动日志仅显示“匿名”或“令牌认证”模式，不显示令牌内容。

```powershell
$env:GITHUB_TOKEN = "<从 GitHub 申请的只读令牌>"
mvn spring-boot:run
```

## 使用与接口

页面输入 `https://github.com/owner/repo` 后点击“开始体检”。可先用 `https://github.com/octocat/Hello-World` 验证基础流程，再用 `https://github.com/nextlevelbuilder/ui-ux-pro-max-skill` 验证较大仓库和部分扫描提示。仓库内容会变化，评分和 commit 不宜作为固定断言。

| 接口 | 作用 | 主要结果 |
| --- | --- | --- |
| `POST /api/reports/scan` | 同步扫描公开 GitHub 仓库；JSON 入参为 `{"repositoryUrl":"https://github.com/owner/repo"}` | 仓库、分支、完整 commit SHA、扫描时间、总分、分项得分、风险、覆盖情况 |
| `GET /api/health` | 检查 HTTP 服务 | 文本 `ok` |

扫描报告的 `categoryScores` 包含 `documentation`、`dependencies`、`startup`、`portability`、`security`。`risks` 中每项包含严重程度、类别、标题、仓库相对路径、适用时的行号和整改建议。`partialScan=true` 表示文件树截断、文件未读取或读取失败；`scannedFiles` 只统计成功读取内容的文件，不能把未扫描文件视为无风险。

无效仓库地址返回 `400`；不存在或无法公开访问的仓库返回 `404`；GitHub 限流返回 `429`，其他拒绝访问返回 `403`；上游或网络故障返回 `502/504`。失败不会生成“零风险”报告。

## 检测与评分边界

扫描先用 GitHub API 解析默认分支及 commit，再读取该 commit 的 tree 和候选 blob。只接受标准 `https://github.com/owner/repo` 地址，不克隆、执行代码或安装依赖。单文件上限为 64 KiB，最多读取 100 个文件、累计 2 MiB；二进制及不符合候选类型的文件不读取。文件树截断、预算耗尽或读取失败会标记部分扫描。疑似凭据命中只返回风险位置，不返回匹配原值。

| 维度 | 当前规则 | 最高分 |
| --- | --- | ---: |
| README | 文件名匹配 README | 25 |
| 依赖清单 | 存在 `pom.xml`、`package.json`、`requirements.txt` 等识别的清单 | 20 |
| 启动入口 | 存在 Dockerfile、Makefile、`mvnw`、`gradlew`、`start*.sh` 等识别的入口 | 20 |
| 可移植性 | 初始 15；每个 Windows 脚本或疑似本机绝对路径命中扣 5，最低 0 | 15 |
| 敏感信息 | 初始 20；每个 `.env` 入库或疑似凭据/私钥命中扣 10，最低 0 | 20 |

前三项主要按文件名判断存在性，不能证明 README 内容完整、依赖可安装或项目能启动。绝对路径和敏感信息使用有限的规则匹配，可能漏报或误报。部分扫描不额外扣分，应同时查看覆盖数、风险清单和总分。

## 验证

```powershell
cd D:\proIDEA\AIcoding\RepoReady
mvn verify

cd D:\proIDEA\AIcoding\RepoReady-fe
npm run build
```

后端测试覆盖 URL 校验、扫描编排、规则与评分、敏感信息不回显及异常映射；JaCoCo 报告生成于 `RepoReady/target/site/jacoco/`。前端已通过真实公开仓库完成输入、请求和报告展示联调。开发过程及人工复核记录见 [AI_USAGE.md](AI_USAGE.md)。

## 更新跟踪设计（尚未实现）

**发现更新。**为每个仓库保存最近一次成功报告的默认分支、完整 commit SHA、扫描时间和规则版本。定时查询默认分支头部 SHA；后续可用 GitHub Webhook 缩短发现延迟。SHA 未变且规则版本未变时直接复用报告。默认分支名称改变时重新建立基准。

**避免全量重算。**SHA 变化后，优先通过 GitHub Compare API 获取新增、修改、删除文件；按文件路径与 blob SHA 保存逐文件检测结果。只读取和扫描新增或内容变化的 blob，复用未变化文件的检测结果，删除旧文件的结果，再按新文件清单重算完整度、风险排序和总分。规则版本变化时，重新执行受影响的规则；若差异列表分页、截断或无法完整获取，退回到两个固定 commit 的 tree 对比，不能默默视为“无变化”。

**报告依据。**每份报告明确展示仓库、分支、完整 commit SHA、扫描时间、规则版本及覆盖范围。新报告只有在差异和汇总都成功完成后才替换最近一次成功报告；前端不把旧报告称为最新报告。

**失败与限流。**遇到 `403/429` 时参考 `Retry-After`、`X-RateLimit-Reset` 和退避策略重试，避免并发重复刷新。网络故障、差异不完整或超出扫描预算时保留上一次成功报告，并标注“历史报告，最新状态未确认”、其 commit 与成功时间；必要时生成明确标记的部分扫描报告，不把失败解释成零风险。该方案需要增加报告与逐文件检测结果的持久化，当前同步无状态接口尚未实现这些能力。
