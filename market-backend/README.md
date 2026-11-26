# HyperOpen Market 后端服务（Cloudflare Workers + D1）

面向 HyperOpen（HyperOS 指纹面板扩展）的配置市场与快捷方式市场后端，采用纯 ESM Cloudflare Worker + Cloudflare D1（SQLite）构建。

## 目录结构

- `wrangler.jsonc`：Worker 配置与 D1 数据库绑定（不含任何密钥）
- `schema.sql`：D1 数据库表结构与索引定义（7 张表 + 13 个索引）
- `src/index.js`：Worker HTTP 路由、鉴权、作者唯一绑定、限流、去重与 D1 读写入口
- `src/validator.js`：配置白名单（119 键，纯外观化校验并剥离快捷方式）、快捷项规范化、`base64:` 图片魔数校验与高危动作检测
- `src/admin.js`：`/admin` 单页审核台与作者管理 HTML/CSS/JS

---

## 本地开发与部署

> **安全提示**：严禁将 `ADMIN_TOKEN` 或任何账号凭据写入 `wrangler.jsonc` 或仓库内任意文件。

1. 安装依赖（如通过 Wrangler CLI 本地调试）：
   ```bash
   cd market-backend
   npm install -D wrangler
   ```
2. 初始化 D1 表结构（远程生产库）：
   ```bash
   npx wrangler d1 execute hyperopen-market --remote --file=./schema.sql
   ```
3. 设置管理员密钥 `ADMIN_TOKEN`：
   ```bash
   npx wrangler secret put ADMIN_TOKEN
   ```
   本地调试时可创建 `market-backend/.dev.vars`（已被 `.gitignore` 忽略）：
   ```env
   ADMIN_TOKEN=your-local-dev-token
   ```
4. 部署 Worker：
   ```bash
   npx wrangler deploy
   ```

---

## 自定义域名绑定教程

`*.workers.dev` 子域在部分网络环境下可能受限，生产环境默认使用已绑定的自有域名 `https://market.qmmry.cfd`（审核台：`https://market.qmmry.cfd/admin`）。如需绑定其他自有域名：

1. 将域名托管到 Cloudflare DNS（确保对应域名所在 Zone 为 Active 状态）。
2. 登录 Cloudflare Dashboard → **Workers & Pages** → 选择 `hyperopen-market`。
3. 进入 **Settings** → **Domains & Routes** → 点击 **Add** → 选择 **Custom Domain**。
4. 输入子域名（例如 `market.qmmry.cfd`）并确认。Cloudflare 会自动签发 TLS 证书并配置路由，无需手动创建 DNS A/CNAME 记录。
5. 绑定完成后：
   - 客户端默认 API 基地址：`https://market.qmmry.cfd`
   - 审核台地址：`https://market.qmmry.cfd/admin`
   - 可按需在 **Domains & Routes** 中决定是否保留默认 `workers.dev` 子域。

---

## `/admin` 审核台说明

访问 `https://market.qmmry.cfd/admin`（或你自建实例的 `https://<worker-domain>/admin`）即可打开响应式单页审核台：

- **登录鉴权**：输入通过 `ADMIN_TOKEN` Secret 配置的管理员口令，验证通过后仅缓存在当前浏览器标签页的 `sessionStorage` 中（关闭标签页自动清除）。
- **自动审核分流规则**：
  - **配置市场纯外观化**：服务端强制剥离顶层 `shortcuts` 与 `values.shortcuts_json`，仅校验保存纯外观白名单参数 JSON，校验通过后一律直接设为 `approved` 上架。
  - **快捷方式包**：所有上传均由服务端全权解析与重算，**不信任客户端自报标签**。
    - 若递归检测到任意高危动作（`SHELL`、`BROADCAST`、`ACTIVITY` 且 `useRoot=true`、`userId > 0`、或高风险 `INTENT_URI`）→ 标记 `reviewReasons` 含 `root_action`，状态进入 `pending`。
    - 若递归检测到任意有效 `base64:` 自定义图片（`iconPackage` / `iconGlyph`）→ 标记 `reviewReasons` 含 `custom_image`，状态进入 `pending`。
    - 未命中上述两类风险 → 自动设为 `approved` 直接公开上架。
- **审核视图与操作**：
  - 支持筛选：**待审快捷包**、**待审配置**、**已上架**、**已隐藏**、**被举报**、**全部条目**、**作者管理**，支持关键词搜索与分页。
  - 列表与详情展示作者官方徽章以及配置关联的快捷包状态（含快捷跳转）。
  - 点开快捷包详情时，页面会将提取出的所有 `base64:` 自定义图标渲染为**缩略图网格**，**明文完整展示**高危动作与完整载荷，展示举报记录与规范化 JSON，并提供「通过上架」「退回待审」「隐藏下架」「永久删除」操作。
  - **作者管理**：支持按关键字和是否官方筛选作者，一键开启/取消官方徽章、自定义徽章文案（如 `官方`、`认证创作者`）、管理员改名，以及删除占位作者以释放昵称（已发作品通过 `ON DELETE SET NULL` 保留）。
- **自动隐藏**：同一公开条目被 **5 个不同 IP** 举报后，自动转为 `hidden` 状态。

---

## API 文档

所有 `/api/*` 响应均为 UTF-8 JSON，失败时统一返回：
```json
{ "ok": false, "error": "错误说明" }
```

### 公开接口

| 方法与路径 | 说明 |
| --- | --- |
| `GET /api/health` | 健康检查与 D1 连通性探测 |
| `GET /api/author/me` | 凭 `X-Author-Secret` 查询当前设备绑定的作者信息；缺失或未注册返回 `{ ok: true, author: null }` |
| `PUT /api/author/me` | 凭 `X-Author-Secret`（长度 ≥ 16）注册或改名。Body: `{ displayName }`（1..40 字，禁止控制字符/换行，无保留词黑名单，不区分大小写全站唯一；改名每小时 ≤ 3 次，撞名返回 `409`）。改名后历史作品实时同步显示新昵称 |
| `GET /api/configs` | 获取已上架配置列表。查询参数：`q`, `layout` (`0..3`), `material` (`1..2`), `hasShortcuts` (`1`/`0`), `sort` (`latest`\|`downloads`), `page`, `pageSize` (1..50)。返回含 `authorId`、`authorIsOfficial`、`authorBadgeLabel` 与 `linkedShortcutPack`（仅关联快捷包为 `approved` 时返回） |
| `GET /api/configs/:id` | 获取单个已上架配置详情与 `jsonBody`（含动态 `ETag` / `If-None-Match` 304 支持；传 `?download=1` 异步递增下载次数） |
| `POST /api/configs` | 上传配置（需 `X-Author-Secret`）。Body: `{ name, description?, author?, tags?, minAppVersionCode?, appVersion?, linkedShortcutPackId?, jsonBody }`。自动剥离 `shortcuts` / `values.shortcuts_json` 并直接 `approved`，成功返回 `201` 与一次性 `manageToken` |
| `DELETE /api/configs/:id` | 凭 `X-Author-Secret`（匹配 `author_id`）或 `X-Manage-Token` 自删配置 |
| `POST /api/configs/:id/report` | 举报公开配置。Body: `{ reason? }` |
| `GET /api/shortcuts` | 获取已上架快捷方式列表（含单个快捷方式与快捷包）。查询参数：`q`（同时匹配名称、简介、作者、标签与包内项目/文件夹子项索引 `items_search_text`）, `entryType` (`all`\|`single`\|`pack`), `actionType`, `hasRoot` (`1`/`0`), `hasImages` (`1`/`0`), `sort` (`latest`\|`downloads`), `page`, `pageSize` (1..50)。返回含 `entryType`、`previewItem`（`single` 返回规范化单项对象，`pack` 为 `null`）、`authorId`、`authorIsOfficial`、`authorBadgeLabel` |
| `GET /api/shortcuts/:id` | 获取单个已上架快捷方式/快捷包详情与 `shortcutsJson`（含 `entryType`、`previewItem`，动态 `ETag` 已纳入 `entryType` 支持 `If-None-Match` 304 与 `?download=1`） |
| `POST /api/shortcuts` | 上传单个快捷方式或快捷包（需 `X-Author-Secret`）。Body: `{ entryType?, name?, description?, author?, tags?, minAppVersionCode?, appVersion?, shortcutsJson }`。`entryType` 支持 `single` / `pack`（省略或为空默认 `pack`）；`single` 仅允许 1 个快捷项，省略 `name` 时自动取该快捷项 `title` 兜底，并生成 `previewItem`。成功返回 `201` 与 `manageToken` |
| `DELETE /api/shortcuts/:id` | 凭 `X-Author-Secret`（匹配 `author_id`）或 `X-Manage-Token` 自删快捷包，并自动解绑引用它的配置 (`linked_shortcut_pack_id = NULL`) |
| `POST /api/shortcuts/:id/report` | 举报公开快捷包。Body: `{ reason? }` |

### 服务端校验与限额

- **单次请求体上限**：`512 KB`（超出返回 `413`）
- **配置白名单**：严格对齐客户端 `ConfigBackup.ALLOWED_KEY_TYPES` 119 键，强制剥离顶层 `shortcuts`、`values.shortcuts_json`、`safety_*`、`hook_*`、`last_real_fod_*`、`__de_migrated_v1` 及本机 App 界面偏好键，拒绝 `NaN`/`Infinity` 并按客户端边界钳制数值
- **作者唯一绑定与防重名**：
  - 每台设备通过 `X-Author-Secret`（SHA-256 存为 `secret_hash`）唯一绑定 1 个作者记录
  - 昵称去除首尾空格后 `1..40` 字，禁止控制字符/换行，不做保留词屏蔽，`normalized_name = displayName.toLowerCase()` 全站唯一（撞名返回 `409 Conflict`）
  - 同一设备修改昵称每小时最多 `3` 次（超出返回 `429` + `Retry-After`）
- **关联配套快捷包**：
  - `POST /api/configs` 可传入 `linkedShortcutPackId`（要求目标快捷包存在且为 `approved`，或属于当前作者自己的非隐藏包）
  - 公开接口仅当关联快捷包状态为 `approved` 时返回 `linkedShortcutPackId` 与 `linkedShortcutPack`；若处于 `pending`/`hidden` 或已删除则返回 `null`
  - 删除快捷包时服务端自动将关联配置的 `linked_shortcut_pack_id` 置为 `NULL` 并刷新 `updated_at`
- **快捷方式规范化与形态区分（`single` / `pack`）**：
  - 支持 `entryType` 为 `single`（单个快捷方式）或 `pack`（快捷包，默认）
  - `single` 模式下要求顶层快捷项数量严格为 `1`，可省略 `name` 并回退取首项 `title`（最多 60 字），同时在列表与详情返回 `previewItem` 对象；去重哈希采用 `single:` 前缀隔离，不与单条 `pack` 冲突
  - 列表关键字搜索 `q` 匹配 `name` / `description` / `author` / `tags_json` 与冗余纯文本检索列 `items_search_text`（在上传时由服务端递归提取顶层项及 `FOLDER` 子项的 `title`、`subtitle`、`actionType`、非 `base64:` 的 `iconPackage` 及动作目标包名/组件/参数，总长上限 `8192` 字符且绝不含 `base64:` 图片），不扫描 `preview_item_json` 与 `shortcuts_json`
  - 快捷方式上传强制把所有项及子项 `customOffsetX = 0`、`customOffsetY = 0`
  - 顶层 `<= 50` 项，含文件夹子项总数 `<= 100` 项，禁止文件夹嵌套文件夹
- **自定义图标校验**：仅允许标准 Base64 编码的 `PNG` / `JPEG` / `WEBP`（校验魔数），单张解码体积 `<= 64 KB`，单次上传总数 `<= 24` 张
- **SHA-256 去重**：同市场存在相同规范化内容的未隐藏条目时返回 `409 Conflict`
- **IP 限流**：上传每小时 ≤ `5` 次、每日 ≤ `15` 次；举报每小时 ≤ `10` 次（超出返回 `429` + `Retry-After`）

### 管理员接口（均需 `Authorization: Bearer <ADMIN_TOKEN>`）

- `GET /api/admin/items?kind=all|config|shortcut&status=all|pending|approved|hidden|reported&entryType=all|single|pack&q=...&page=1&pageSize=20`
- `GET /api/admin/items/:kind/:id`（返回元数据、`rawBody`、`extractedImages`、`riskSummary`、`reports`）
- `POST /api/admin/items/:kind/:id/status`（Body: `{ "status": "approved" | "pending" | "hidden" }`）
- `DELETE /api/admin/items/:kind/:id`（删除快捷包时同步解绑关联配置）
- `GET /api/admin/authors?q=&official=all|1|0&page=1&pageSize=20`（含每位作者 `configCount` 与 `shortcutCount`）
- `PATCH /api/admin/authors/:id`（Body 可含 `isOfficial`, `badgeLabel`, `displayName`）
- `DELETE /api/admin/authors/:id`（释放占位昵称，已发作品保留并置 `author_id = NULL`）

---

## Cloudflare 免费套餐限额注意事项

- **Workers Free**：每日 `100,000` 次请求、单次请求 CPU 时间上限 `10 ms`。本服务限制单请求 `512 KB`、单图 `64 KB` 且最多 `24` 张，避免 Base64 解码与 JSON 序列化超出 CPU 限制。
- **D1 Free**：每日读行 `5,000,000` 行、写行 `100,000` 行、总容量 `5 GB`。
  - 公开列表接口返回 `Cache-Control: public, max-age=30, stale-while-revalidate=60`，详情接口采用包含作者信息与关联快捷包状态的动态 `ETag` 支持 `304` 协商，既减少重复 D1 读取又避免改名/徽章/解绑后返回过期缓存；
  - 过期 `rate_limits` 记录会在写操作后通过 `ctx.waitUntil` 异步清理，避免长期膨胀；
  - 若后续分享规模快速增长，建议为列表查询接入 Cloudflare Cache API 或迁移自定义图片到 R2。
