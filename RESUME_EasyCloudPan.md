# EasyCloudPan 项目亮点与技术难点（强化长文版）

## 一、项目定位与技术背景（可直接用于简历项目介绍）
- 项目名称：EasyCloudPan（前后端分离的企业级网盘系统）
- 技术栈：Java 21、Spring Boot 3.2、Spring Security、OAuth2、MyBatis-Flex、Flyway、PostgreSQL、Redis、Caffeine、MinIO/S3、Jasypt、Vue 3、Vite、Pinia、Element Plus、Playwright、Docker Compose、Actuator/Micrometer、Prometheus/Grafana
- 项目目标：围绕“文件上传下载、文件预览、分享链路、回收站、管理后台、多租户、安全通信、可观测性、工程化交付”构建一套可持续演进的网盘底座，既支持个人场景，也具备向 B 端组织扩展的架构能力。
- 个人定位：以全栈工程师角色参与后端核心链路、前端体验、通信安全、运维脚本和质量门禁治理，重点处理高并发、性能、兼容性、安全性与可维护性之间的工程平衡。
- 信息校对日期：2026-02-24（已对照 README、关键代码与迁移脚本复核）

---

## 二、项目亮点（每项可直接作为简历亮点展开，含实现细节）

### 亮点 1：构建“分片上传 + 秒传 + 断点续传 + 状态回传”的完整上传主链路，显著提升大文件上传成功率与用户连续体验
**业务背景与目标**
在网盘系统中，上传链路是最核心、最容易暴露稳定性问题的一段流程。用户网络抖动、弱网切换、浏览器中断、上传超时、服务器临时波动，都会直接放大为“文件传不上去”“进度卡死”“重复上传浪费时间”这类用户强感知问题。项目目标不是仅仅“能上传”，而是要在复杂网络环境下给出可恢复、可重试、可感知、可回滚的上传体验。

**核心实现思路**
后端接口在 `backend/src/main/java/com/easypan/controller/FileInfoController.java` 中形成了完整上传协议：`/file/uploadFile` 接收分片，`/file/uploadedChunks` 查询已上传分片，`/file/getUploadProgress` 回传实时进度，`/file/transferStatus` 与 `transferStatusSse` 提供转码状态。业务层通过 `FileInfoServiceImpl`、`ChunkUploadService`、`QuickUploadService` 将“标准分片上传”“秒传命中”“分片并发控制”“上传完成后状态收口”串成统一链路。

**工程细节与可靠性设计**
- 前端侧 `frontend/src/views/main/Uploader.vue` 基于分片索引、上传窗口、失败重试、已上传分片回填实现了断点续传；同时利用 `frontend/src/workers/md5.worker.js` 在 Worker 线程中计算 MD5，避免主线程卡顿。
- 后端侧 `backend/src/main/java/com/easypan/service/ChunkUploadService.java` 通过 Redis 记录分片上传中状态与已完成集合，并限制同一文件并发分片数，避免高并发下单文件挤占上传资源。
- 秒传能力在 `backend/src/main/java/com/easypan/service/QuickUploadService.java` 中实现，通过 `fileMd5 -> fileId` 映射快速复用已有对象，未命中再回落到常规上传流程，实现“快路径优先、慢路径兜底”的分层策略。

**业务价值与面试可表达点**
这套方案的价值是把上传从“单点成功”升级为“过程稳定 + 状态可恢复 + 用户可预期”。即使弱网中断，也可以基于已上传分片继续推进，显著降低重复上传成本。对面试官可重点强调：该链路不是单接口优化，而是“协议设计 + 状态机 + 并发控制 + 兜底策略”的系统化实现。

---

### 亮点 2：引入 SSE 状态推送与受管异步执行，显著降低前端轮询成本并改善转码进度可见性
**业务背景与目标**
传统轮询模式在转码场景下存在两类问题：一是前端需要持续请求，网络和服务端资源消耗高；二是状态延迟不可控，用户看到的进度反馈不稳定，容易误判系统“卡住”。项目需要将“被动查询”改造为“主动推送”，在保证兼容性的前提下提升状态反馈实时性。

**核心实现与架构选择**
在 `backend/src/main/java/com/easypan/controller/FileInfoController.java` 中，`/file/transferStatusSse` 通过 `SseEmitter` 建立服务端单向推送通道，结合 `virtualThreadExecutor` 执行状态轮询与发送。为了避免无限循环导致资源泄露，流程内设置了 `maxPollCount`、超时和异常关闭回调，确保连接生命周期被显式管理。

**工程细节**
- 在连接结束、超时、报错时统一关闭 `running` 标记，避免后台任务悬挂。
- 推送载荷仅携带核心状态字段，减少带宽与序列化开销。
- 与普通查询接口 `transferStatus` 共存，形成“推送优先 + 查询兜底”的兼容策略，降低接入风险。

**业务价值与面试可表达点**
这项优化的关键不是“用了 SSE”，而是围绕连接生命周期和线程模型做了可控治理，避免从“高轮询成本”变成“长连接泄露”这种新的问题。可突出表达：这是一次通信模式升级，同时关注了可用性、兼容性和资源上界。

---

### 亮点 3：落地请求签名防重放链路（HMAC-SHA256 + timestamp + nonce），并保留灰度兼容能力
**业务背景与目标**
仅依赖 Token 的请求鉴权能够识别“是谁”，但不能充分证明“请求内容没被篡改”以及“请求不是重放”。在上传、删除、分享等敏感接口场景，系统需要对请求完整性和时效性提供更强保证，同时不能直接切断历史客户端。

**核心实现**
后端在 `backend/src/main/java/com/easypan/config/RequestSignatureConfig.java` 中实现统一过滤器：
- 通过 `X-Signature`、`X-Timestamp`、`X-Nonce`、`X-Signature-Version` 进行校验；
- 采用 HMAC-SHA256 计算签名；
- timestamp 进行时效窗口校验；
- nonce 写入 Redis，拦截重复使用；
- 对敏感路径强制签名，对普通路径保留兼容。
前端在 `frontend/src/utils/Request.ts` 中将签名注入请求主链路，并可通过 `requireSignature` 做调用级控制。

**工程权衡与兼容策略**
- 通过 `SKIP_PATHS` 跳过登录、验证码等基础接口，避免影响首登与外部集成场景。
- 签名版本通过 `X-Signature-Version: v2` 控制，支持平滑升级。
- 普通路径保留兼容策略，避免一次性强切造成旧客户端全面失败。

**业务价值与面试可表达点**
这项能力体现了安全治理中的“闭环思维”：不仅做了算法，还做了重放控制、路径策略、版本治理和兼容落地。面试可强调“安全增强不能脱离业务连续性”，因此采用了分层强制与灰度策略，而不是全局硬切。

---

### 亮点 4：构建 JWT 鉴权与 query token 兼容退场机制，兼顾安全演进与旧客户端可用性
**业务背景与目标**
历史系统常出现“URL 带 token”模式，存在日志、代理、浏览器历史泄漏风险；但旧客户端短期内无法全部升级。目标是在提升安全性的同时，给出可运营的迁移窗口，避免业务中断。

**核心实现**
在 `backend/src/main/java/com/easypan/component/JwtAuthenticationFilter.java` 中：
- 优先读取 `Authorization` 头；
- 兼容读取 query token；
- 对 query token 请求写入弃用日志并返回 `Deprecation`、`Sunset` 响应头，明确退场时间窗口；
- 集成黑名单校验、Token 有效性校验、安全审计记录。
前端 `frontend/src/utils/Request.ts` 统一注入 `Authorization: Bearer`，并处理 token 自动刷新。

**工程价值**
这种设计避免了“理想安全方案无法落地”的常见问题，把迁移过程做成可追踪、可观测、可回收的演进路径。安全治理不再是一次性动作，而是带节奏的产品化能力。

---

### 亮点 5：实现多租户上下文贯穿链路，补齐“客户端头部不可完全信任”的服务端约束
**业务背景与目标**
多租户系统最危险的问题是“数据串租户”：客户端随意篡改租户头、服务端只做弱校验、异步线程上下文丢失，都可能造成越权访问。目标是实现“请求入口识别 + 用户绑定校验 + 持久层租户透传”的端到端治理。

**核心实现**
- 前端 `frontend/src/utils/Request.ts` 动态注入 `X-Tenant-Id`，优先读取用户态租户，再回退默认值。
- 后端 `backend/src/main/java/com/easypan/component/TenantInterceptor.java` 负责入口提取租户；`TenantContextHolder` 保存上下文；`TenantConfig` 连接 MyBatis-Flex 租户工厂。
- `backend/src/main/java/com/easypan/aspect/GlobalOperationAspect.java` 在登录校验时做“用户租户与请求头租户”一致性检查，不匹配直接拒绝。

**工程价值**
这套设计把“租户隔离”从单点判断升级为链路约束，尤其强调服务端的最终可信判断，避免把安全边界建立在前端可控输入上。对于 SaaS 类系统，这是从“可用”走向“可商用”的关键一步。

---

### 亮点 6：搭建 L1/L2/L3 多级缓存与防穿透策略，提升热点访问稳定性并保护数据库
**业务背景与目标**
网盘场景中热门文件、热门用户、热点分享会造成突发流量。若缓存策略单薄，会出现缓存击穿、穿透、雪崩，最终把压力转移到数据库。项目需要在读取路径上构建层次化缓冲与降级策略。

**核心实现**
- `backend/src/main/java/com/easypan/service/MultiLevelCacheService.java` 实现 Caffeine（L1）+ Redis（L2）+ DB（L3）读取策略，命中后回写上层缓存。
- 对不存在对象写入 null marker，避免重复穿透。
- `backend/src/main/java/com/easypan/service/CacheProtectionService.java` 进一步引入分布式锁、随机 TTL、锁重试上限与降级直查逻辑，明确冲突场景的上界行为。

**工程价值**
亮点不在“用了缓存”，而在“缓存不命中场景怎么处理”。通过完整的 miss 策略与保护措施，系统在热点和异常流量下仍能维持可用，数据库压力不会被瞬时击穿。

---

### 亮点 7：实现主备存储故障转移与简易熔断，保证存储层波动时业务链路可持续
**业务背景与目标**
网盘系统强依赖存储组件，一旦主存储服务不稳定，上传下载会直接失败，用户体验劣化明显。项目目标是让存储层具备“主路径优先、失败自动回退、持续失败快速绕行”的容灾能力。

**核心实现**
`backend/src/main/java/com/easypan/service/StorageFailoverService.java` 统一代理存储操作：
- 正常情况下走 Primary；
- 单次失败自动回退 Backup；
- 连续失败达到阈值后触发熔断，后续请求直接走 Backup；
- 提供熔断重置入口，便于健康恢复后回切。

**工程价值**
这种设计把“失败重试”升级为“策略化故障转移”。它可以有效降低外部依赖不稳定对用户的直接冲击，也为后续引入更完整的健康探测和自动回切奠定基础。

---

### 亮点 8：通过 S3 预签名直链下载卸载应用带宽，改善大文件分发性能与服务器成本结构
**业务背景与目标**
若所有下载流量都经过应用服务中转，应用层会成为明显带宽瓶颈，同时提高网关与实例成本。目标是在权限可控前提下，把大文件分发能力下沉到对象存储层。

**核心实现**
`backend/src/main/java/com/easypan/component/S3Component.java` 实现预签名 URL 生成；`backend/src/main/java/com/easypan/strategy/impl/OssStorageStrategy.java` 暴露存储策略接口；`backend/src/main/java/com/easypan/controller/CommonFileController.java` 在下载链路中优先返回预签名直链。

**工程价值**
这种方式把应用服务从“流量搬运工”转换为“授权与编排中心”，下载高峰时应用实例压力显著下降，系统扩展能力更接近云原生对象存储的弹性模式。

---

### 亮点 9：前端侧落地“虚拟列表 + 异步预览 + Worker 计算池”，显著改善大数据量页面交互
**业务背景与目标**
文件管理页面在数据量大时容易出现 DOM 过多、主线程阻塞、滚动掉帧、上传期间页面卡顿等问题。目标是在不牺牲功能完整性的前提下提升渲染稳定性和交互响应性。

**核心实现**
- `frontend/src/main.ts` 引入 `vue-virtual-scroller`，面向大列表降低 DOM 数量。
- `frontend/src/views/FilePreview.vue` 与 `frontend/src/components/preview/Preview.vue` 采用 `defineAsyncComponent` 延迟加载预览组件，降低首屏资源压力。
- `frontend/src/views/main/Uploader.vue` 结合 Worker 池执行 MD5 计算与上传队列调度，避免主线程重计算阻塞。

**工程价值**
这类优化关注的是“真实用户体感”而不是单点跑分。通过渲染、加载、计算三条链路协同治理，页面在大文件夹和连续上传场景下更稳定，交互的可预测性更强。

---

### 亮点 10：构建“本地一键启动 + 健康检查 + 日志分层 + 监控指标”的工程化交付基线
**业务背景与目标**
仅有代码功能不等于可交付。项目要支撑多人协作与持续迭代，必须具备可复制的环境搭建、可验证的健康检查、可读可检索的日志体系、可观测的关键业务指标。

**核心实现**
- 一键脚本：`ops/local/setup.ps1`（环境检查、依赖安装、目录初始化）、`ops/local/startup.ps1`（基础设施 + 后端 + 前端联动启动）。
- 健康检查：`ops/tools/health_check.ps1` 对 PostgreSQL、Redis、MinIO、后端健康端点做统一探测。
- 日志体系：`backend/src/main/resources/logback-spring.xml` 支持应用日志、错误日志、访问日志、JSON 日志多通道分离，按时间与大小滚动归档。
- 监控指标：Actuator + Prometheus 采集，Grafana 可视化；并在 `BusinessMetricsConfig`、`CustomMetrics` 中扩展业务指标；前端 `webVitals.ts` 上报 LCP/INP 等指标，后端 `AnalyticsController` + Flyway `V9__Add_Web_Vitals_Metrics_Table.sql` 完成指标入库。

**工程价值**
这部分能力让项目从“开发可跑”走向“团队可运维、问题可定位、发布可回滚”。对简历表达而言，这是从功能开发者向工程交付者升级的重要信号。

---

### 亮点 11：建立前后端质量门禁与测试基线，保障高频迭代下的回归稳定性
**业务背景与目标**
功能迭代速度提升后，最常见风险是“修一个点、坏一片面”。项目需要可执行、可自动化、可持续的质量保障体系。

**核心实现**
- 前端：ESLint + Playwright E2E，约束代码风格并覆盖关键页面行为。
- 后端：JUnit + jqwik 属性测试、Checkstyle、SpotBugs、JaCoCo 等工具形成“规范 + 静态分析 + 测试”多层门禁。
- 关键配置集中在 `frontend/package.json`、`backend/pom.xml`、`backend/checkstyle.xml`、`backend/spotbugs-exclude.xml`。

**工程价值**
这使项目具备“持续演进能力”而不是一次性交付。质量门禁越标准化，后续优化和重构的风险越可控，团队协作成本越低。

---

### 亮点 12：补齐 OAuth 多提供方登录与注册收敛链路，降低外部身份接入成本
**业务背景与目标**
仅支持单一登录方式会提高接入门槛，且新用户首登与本地账号体系衔接成本高。项目需要在保证安全的前提下，形成“第三方登录 -> 本地会话/令牌 -> 新用户补注册”的闭环。

**核心实现**
- `backend/src/main/java/com/easypan/controller/OAuthController.java` 提供 `/oauth/login/{provider}`、`/oauth/callback/{provider}`、`/oauth/register` 三段式流程，区分“已有账号直接登录”与“新用户补注册”两类路径。
- `backend/src/main/java/com/easypan/service/oauth/OAuthLoginService.java` 统一编排 provider 策略，并对接 `GitHubOAuthStrategy`、`GoogleOAuthStrategy`、`MicrosoftOAuthStrategy`；与既有 QQ 登录兼容共存。
- 新用户在回调阶段先写入 Redis 临时态（`oauth:temp:*`），设置有效期后再完成注册，避免把第三方回调流程与主业务注册流程硬耦合。

**工程价值**
该方案把“第三方授权成功”稳定转化为“系统可用身份”，并把注册补全、令牌签发、状态过期等边界收口在统一服务层，降低后续扩展新 provider 的接入成本。

---

## 三、技术难点与攻关（深度拆解版）

### 难点 1：大文件上传链路中的多状态一致性与中断恢复
**难点本质**
上传不是单操作，而是跨多次请求的长事务过程。任何环节中断都可能导致状态不一致，例如“分片已落盘但数据库未收口”“前端进度已完成但后端状态未切换”“用户重复点击触发并发写入冲突”。

**攻关方式**
通过“分片状态记录 + 上传进度查询 + 已上传分片回填 + 最终状态收口”的协议化流程，将隐式状态转为显式状态机。后端将中间状态写入 Redis/临时目录，前端重连后按 `uploadedChunks` 恢复上传位点；最终完成后统一更新业务状态并触发后续流程。

**复盘与可迁移经验**
对这种长链路问题，关键不在“失败重试次数”，而在“状态可恢复、过程可观测、终态可收敛”。这类方法可以迁移到导入任务、异步审核、媒体处理等多阶段流程。

---

### 难点 2：安全链路升级中的“强安全诉求”与“兼容存量客户端”冲突
**难点本质**
安全治理往往希望立刻收敛风险，但业务系统又存在存量客户端。若强推新规则，容易造成老版本全量失败；若长期放任旧规则，风险窗口难以关闭。

**攻关方式**
采用分层策略：敏感接口强制签名，普通接口兼容；Authorization 头为新主链路，query token 兼容读取但输出弃用信号和退场时间；在日志与指标中保留可观测数据，推动运营和版本升级。

**复盘与可迁移经验**
安全改造最怕“要么全开要么全关”。实践证明，带版本、带指标、带退场节奏的演进方式更符合真实生产环境。

---

### 难点 3：多租户上下文在同步与异步混合执行中的一致性保障
**难点本质**
多租户风险不只在 controller 层。异步任务、缓存键、数据库查询、线程切换都可能丢失租户上下文，导致“入口正确、中间串租户”的隐蔽故障。

**攻关方式**
入口通过 TenantInterceptor 统一注入上下文；GlobalOperationAspect 做用户租户与请求头一致性校验；持久层通过租户工厂透传；请求结束后清理上下文，防止线程复用污染。前端租户头动态注入，减少固定值导致的隔离弱化。

**复盘与可迁移经验**
多租户治理必须是“链路型设计”，不能停留在某一层的 if 判断。把租户校验做成基建能力后，业务开发可以在默认安全边界内迭代。

---

### 难点 4：热点访问下缓存穿透与击穿并存时的数据库保护
**难点本质**
大量不存在 key 的恶意访问会穿透缓存，热点 key 到期又会击穿数据库，这两类问题常常同时出现。单一策略很难覆盖全部场景。

**攻关方式**
将缓存策略拆解为组合拳：null marker 抗穿透、分布式锁抗击穿、随机 TTL 抗雪崩、多级缓存抗波峰。并设置锁重试上限与降级直查，确保系统在高竞争场景有明确行为边界。

**复盘与可迁移经验**
缓存稳定性要关注 miss 场景而不是 hit 场景。真正体现工程能力的是“缓存失效后系统如何不崩”。

---

### 难点 5：存储依赖不稳定时如何保障上传下载的业务连续性
**难点本质**
存储系统抖动会直接反映为上传失败、下载失败，且影响范围大。单纯重试可能放大故障窗口，甚至拖垮调用链。

**攻关方式**
在 StorageFailoverService 中实现主备容灾和简易熔断：短期失败自动 fallback，连续失败达到阈值后快速绕过主存储，避免无效重试。恢复后可人工或健康触发重置，形成可控回切。

**复盘与可迁移经验**
高可用不是“保证不失败”，而是“失败时可持续服务、可快速恢复、可明确回切”。

---

### 难点 6：前端大列表与重计算任务并发触发导致主线程阻塞
**难点本质**
文件系统页面天然具备“大列表 + 高频交互 + 上传计算”特征，任何一个环节阻塞都会造成整页卡顿。尤其 MD5 计算和大规模 DOM 更新容易导致输入延迟显著升高。

**攻关方式**
采用“渲染减量 + 计算隔离 + 组件延迟加载”三步：虚拟列表减少 DOM 数、Worker 池隔离重计算、预览组件按需加载降低首屏负担。上传队列中统一管理状态切换和错误提示，减少 UI 抖动。

**复盘与可迁移经验**
前端性能不是某个函数优化，而是把“渲染、网络、计算、状态管理”合并治理。该思路可迁移到消息列表、订单列表、监控看板等重数据页面。

---

### 难点 7：SSE 长连接模型中的资源上界控制
**难点本质**
SSE 可以解决轮询，但若连接生命周期管理不当，会产生线程悬挂、连接堆积、状态任务长时间占用的问题。

**攻关方式**
在 SseEmitter 流程中增加 completion/timeout/error 回调统一停机；对轮询次数设置上限；异步执行器使用受管线程池并保持可观测日志。兼容保留普通查询接口，异常时可快速切回轮询兜底。

**复盘与可迁移经验**
通信模式升级不能只看“更实时”，还要看“资源模型是否可控”。长连接方案必须先定义退出机制。

---

### 难点 8：日志可读性、检索性、存储成本之间的平衡
**难点本质**
日志过少难排查，日志过多难检索且占用磁盘。文本日志便于人工阅读，JSON 日志便于平台聚合，二者要兼容并存。

**攻关方式**
通过 logback 多 appender 设计，将应用日志、错误日志、访问日志、JSON 日志按用途拆分；异步写盘降低主流程影响；按大小和时间滚动归档，控制磁盘上界。控制台与文件采用不同 pattern，提高开发和运维场景的可读性。

**复盘与可迁移经验**
日志设计本质是“信息架构问题”，而非简单打印。先定义读者角色（开发、运维、审计），再定义日志结构和保留策略，才能做到长期可维护。

---

### 难点 9：前后端通信契约稳定性与迭代速度冲突
**难点本质**
文件系统业务接口多、状态复杂，前后端若频繁无序变更字段和行为，会导致联调成本飙升，问题定位困难。

**攻关方式**
保持 `ResponseVO<T>` 统一响应契约；在请求层统一处理鉴权、租户头、签名、错误提示；服务层集中封装接口调用，避免散落调用；通过类型定义约束关键数据结构，提升变更可发现性。

**复盘与可迁移经验**
契约稳定性是规模化协作的前提。统一协议并不降低迭代速度，反而减少反复返工。

---

### 难点 10：本地环境一致性与团队快速上手问题
**难点本质**
没有统一脚本时，新人搭环境成本高、依赖版本不一致、服务启动顺序混乱，最终导致“代码没问题但环境跑不起来”。

**攻关方式**
通过 setup/startup/health_check 三类脚本形成闭环：先检查工具链版本，再初始化依赖与目录，再一键拉起基础设施和应用，最后做自动健康验证。关键配置通过 `.env` 管理，降低手工配置错误。

**复盘与可迁移经验**
可交付项目必须做到“换机器也能快速跑起来”。本地自动化脚本是团队效率和质量的一部分，而不是附属品。

---

## 四、可直接用于简历的 STAR 高强度表达（长条目版）

### STAR 1：上传链路可靠性重构
- **S（Situation）**：网盘上传场景存在大文件、弱网、上传中断和重复提交，传统单次上传模式导致失败率高、体验差、支持成本高。
- **T（Task）**：在不破坏既有接口契约前提下，重构上传流程，实现秒传、断点续传、进度可见和失败可恢复。
- **A（Action）**：设计分片上传协议（上传、已传分片查询、进度查询、状态推送），后端实现分片并发控制与状态收口，前端落地 Worker MD5 计算与上传窗口调度，统一上传状态机和错误处理。
- **R（Result）**：上传流程由“单次成功导向”升级为“过程可恢复导向”，弱网下可继续上传，重复上传成本明显下降，用户对上传结果可预期性显著提升。

### STAR 2：通信安全链路升级与兼容治理
- **S（Situation）**：仅靠 Token 鉴权难以防重放和篡改，且历史客户端仍存在 query token 使用。
- **T（Task）**：建立请求签名闭环与重放防护，同时保留旧客户端过渡窗口，避免业务中断。
- **A（Action）**：后端新增签名过滤器，校验 HMAC-SHA256、timestamp、nonce 与签名版本；前端请求链路统一签名注入；JWT 过滤器保留 query token 兼容并输出 Deprecation/Sunset 信号。
- **R（Result）**：敏感接口具备更强安全保证，旧客户端可平滑迁移，安全治理具备可观测、可运营、可退场路径。

### STAR 3：多租户隔离从“标识透传”升级到“服务端强约束”
- **S（Situation）**：多租户系统若仅依赖客户端传参，存在被伪造导致越权访问的风险。
- **T（Task）**：将租户隔离能力固化到网关、鉴权、业务、持久层全链路。
- **A（Action）**：实现租户拦截器与上下文管理，登录切面补充用户租户绑定校验，持久层接入租户工厂，前端请求层动态注入租户头并统一管理。
- **R（Result）**：租户数据边界更清晰，串租户风险显著降低，系统具备 SaaS 化扩展基础。

### STAR 4：缓存与数据库保护体系建设
- **S（Situation）**：热点流量和恶意访问并存时，数据库容易被缓存 miss 流量击穿。
- **T（Task）**：建立多层缓存与防护策略，保障高峰期系统稳定。
- **A（Action）**：实现 L1/L2/L3 多级缓存、null marker、防击穿分布式锁、随机 TTL、失败降级路径，并统一缓存失效策略。
- **R（Result）**：高并发下数据库压力更平稳，热点请求响应一致性提升，系统抗流量波动能力增强。

### STAR 5：存储容灾与下载性能治理
- **S（Situation）**：存储服务抖动和应用层下载中转导致可用性与性能双重压力。
- **T（Task）**：保障存储异常下的业务连续性，并降低应用层下载带宽负担。
- **A（Action）**：实现主备存储故障转移与熔断策略；引入 S3 预签名直链下载，应用仅负责授权与审计，不做大流量中转。
- **R（Result）**：存储层故障对用户影响可控，下载链路扩展性更好，应用层资源利用效率提升。

### STAR 6：工程化交付基线落地
- **S（Situation）**：环境依赖复杂、启动步骤分散、日志结构不统一，团队协作成本高。
- **T（Task）**：打造可复制的一键启动、健康检查、日志治理和质量门禁体系。
- **A（Action）**：提供 setup/startup/health_check 脚本闭环；日志按应用/错误/访问/JSON 分层并滚动归档；建立前后端 lint、静态分析、测试门禁。
- **R（Result）**：新环境可快速启动，问题可快速定位，发布和回归流程更稳定；README 还给出了 API P95 < 500ms、P99 < 1s、缓存命中率 > 90%、上传成功率 > 99.5% 的目标/自述口径，但当前未找到独立压测或监控报告，暂不作为已验证成果。

---

## 五、证据索引（面试时可引用的代码锚点）
- 上传与转码状态链路：`backend/src/main/java/com/easypan/controller/FileInfoController.java`
- 上传核心实现：`backend/src/main/java/com/easypan/service/impl/FileInfoServiceImpl.java`
- 分片并发与合并：`backend/src/main/java/com/easypan/service/ChunkUploadService.java`
- 秒传能力：`backend/src/main/java/com/easypan/service/QuickUploadService.java`
- 请求签名：`backend/src/main/java/com/easypan/config/RequestSignatureConfig.java`
- JWT 与兼容退场：`backend/src/main/java/com/easypan/component/JwtAuthenticationFilter.java`
- 多租户校验：`backend/src/main/java/com/easypan/aspect/GlobalOperationAspect.java`
- 租户拦截：`backend/src/main/java/com/easypan/component/TenantInterceptor.java`
- 多级缓存：`backend/src/main/java/com/easypan/service/MultiLevelCacheService.java`
- 缓存防护：`backend/src/main/java/com/easypan/service/CacheProtectionService.java`
- 存储故障转移：`backend/src/main/java/com/easypan/service/StorageFailoverService.java`
- 预签名直链：`backend/src/main/java/com/easypan/component/S3Component.java`
- OAuth 登录编排：`backend/src/main/java/com/easypan/controller/OAuthController.java`
- OAuth 策略实现：`backend/src/main/java/com/easypan/service/oauth/OAuthLoginService.java`、`backend/src/main/java/com/easypan/service/oauth/GitHubOAuthStrategy.java`、`backend/src/main/java/com/easypan/service/oauth/GoogleOAuthStrategy.java`、`backend/src/main/java/com/easypan/service/oauth/MicrosoftOAuthStrategy.java`
- 前端请求主链路：`frontend/src/utils/Request.ts`
- 前端上传队列：`frontend/src/views/main/Uploader.vue`
- 前端预览异步化：`frontend/src/components/preview/Preview.vue`
- 前端独立预览页：`frontend/src/views/FilePreview.vue`
- Web Vitals 前端采集：`frontend/src/utils/webVitals.ts`
- Web Vitals 后端接收：`backend/src/main/java/com/easypan/controller/AnalyticsController.java`
- Web Vitals 迁移脚本：`backend/src/main/resources/db/migration/V9__Add_Web_Vitals_Metrics_Table.sql`
- 一键脚本：`ops/local/setup.ps1`、`ops/local/startup.ps1`
- 健康检查：`ops/tools/health_check.ps1`
- 日志体系：`backend/src/main/resources/logback-spring.xml`
- 监控与告警配置：`ops/monitoring/prometheus.yml`、`ops/monitoring/alert_rules.yml`

---

## 六、投递前指标建议（含当前 README 基线）
### README 自述口径（待外部佐证，校对日期：2026-02-24）
- README 自述 API 响应 P95 < 500ms、P99 < 1s，数据库查询 P95 < 100ms、慢查询减少 80%。
- README 自述 Caffeine + Redis 多级缓存命中率 > 90%，支持 1000+ 并发上传、上传成功率 > 99.5%。
- 以上属于项目资料中的目标/自述数据，当前未见压测脚本、监控截图或线上运行报告；简历和 STAR 结果不按已验证指标表述。

### 进一步建议补全（用于把亮点从“可信”提升到“有说服力”）
- 上传链路：平均上传成功率、失败重试后成功率、秒传命中率、断点续传恢复率。
- 性能体验：首屏加载时间、关键页面 LCP/INP、大目录滚动帧率、上传进度更新延迟。
- 稳定性：核心接口 P50/P95/P99、错误率、SSE 连接峰值与异常中断率。
- 资源成本：应用层下载流量占比（改造前后）、存储层带宽利用率、数据库峰值连接数。
- 安全治理：签名失败率、nonce 重放拦截次数、query token 使用比例与退场进度。

> 说明：本文件已按“可面试讲述 + 可工程追溯”的标准整理，最近校对日期为 2026-02-24；后续可根据具体投递岗位（后端/全栈/架构）再裁剪为 1 页版与 3 页版两个版本。

## 七、简历资料维护口径

- 本文同时作为 EasyCloudPan 的项目亮点文档和技术难题与解决方案文档入口。
- 当前可复用口径包括分片上传/秒传/断点续传、SSE 状态推送、请求签名防重放、多级缓存、对象存储与工程化交付。
- README 口径中的性能和并发数据仅作为待补证据线索；正式投递前需补充压测报告、监控截图或测试脚本，否则不写成确定成果。
