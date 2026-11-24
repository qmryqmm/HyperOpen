# HyperOpen Market 后端服务（Cloudflare Workers + D1）

面向 HyperOpen（HyperOS 指纹面板扩展）的配置市场与快捷方式市场后端，采用纯 ESM Cloudflare Worker + Cloudflare D1（SQLite）构建。

## 目录结构

- `wrangler.jsonc`：Worker 配置与 D1 数据库绑定（不含任何密钥）
- `schema.sql`：D1 数据库表结构与索引定义（6 张表 + 8 个索引）
- `src/index.js`：Worker HTTP 路由、鉴权、限流、去重与 D1 读写入口
- `src/validator.js`：配置白名单（119 键，对齐 `ConfigBackup.java`）、快捷项规范化、`base64:` 图片魔数校验与高危动作检测
- `src/admin.js`：`/admin` 单页审核台 HTML/CSS/JS

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
  - 所有上传均由服务端全权解析与重算，**不信任客户端自报标签**。
  - 若递归检测到任意高危动作（`SHELL`、`BROADCAST`、`ACTIVITY` 且 `useRoot=true`、`userId > 0`、或高风险 `INTENT_URI`）→ 标记 `reviewReasons` 含 `root_action`，状态进入 `pending`。
  - 若递归检测到任意有效 `base64:` 自定义图片（`iconPackage` / `iconGlyph`）→ 标记 `reviewReasons` 含 `custom_image`，状态进入 `pending`。
  - 未命中上述两类风险 → 自动设为 `approved` 直接公开上架。
- **审核视图与操作**：
  - 支持筛选：**待审配置**、**待审快捷包**、**已上架**、**已隐藏**、**被举报**、**全部条目**，支持关键词搜索与分页。
  - 点开任意条目详情时，页面会：
    1. 将提取出的所有 `base64:` 自定义图标渲染为**缩略图网格**（显示路径、字段、MIME 与体积）；
    2. **明文完整展示**高危动作类型、原因、`shellUseRoot` / `userId` 与完整载荷；
    3. 展示举报记录与规范化后的完整 JSON；
    4. 提供「通过上架」「退回待审」「隐藏下架」「永久删除」操作。
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
| `GET /api/configs` | 获取已上架配置列表。查询参数：`q`, `layout` (`0..3`), `material` (`1..2`), `hasShortcuts` (`1`/`0`), `sort` (`latest`\|`downloads`), `page`, `pageSize` (1..50) |
| `GET /api/configs/:id` | 获取单个已上架配置详情与 `jsonBody`（含 `ETag` / `If-None-Match` 304 支持；传 `?download=1` 异步递增下载次数） |
| `POST /api/configs` | 上传配置。Body: `{ name, description?, author, tags?, minAppVersionCode?, appVersion?, jsonBody }`。成功返回 `201` 与一次性 `manageToken` |
| `DELETE /api/configs/:id` | 凭 `X-Manage-Token` 请求头自删配置 |
| `POST /api/configs/:id/report` | 举报公开配置。Body: `{ reason? }` |
| `GET /api/shortcuts` | 获取已上架快捷包列表。查询参数：`q`, `actionType`, `hasRoot` (`1`/`0`), `hasImages` (`1`/`0`), `sort` (`latest`\|`downloads`), `page`, `pageSize` (1..50) |
| `GET /api/shortcuts/:id` | 获取单个已上架快捷包详情与 `shortcutsJson`（含 `ETag` / `If-None-Match` 304 与 `?download=1`） |
| `POST /api/shortcuts` | 上传快捷包。Body: `{ name, description?, author, tags?, minAppVersionCode?, appVersion?, shortcutsJson }`。成功返回 `201` 与 `manageToken` |
| `DELETE /api/shortcuts/:id` | 凭 `X-Manage-Token` 请求头自删快捷包 |
| `POST /api/shortcuts/:id/report` | 举报公开快捷包。Body: `{ reason? }` |

### 服务端校验与限额

- **单次请求体上限**：`512 KB`（超出返回 `413`）
- **配置白名单**：严格对齐客户端 `ConfigBackup.ALLOWED_KEY_TYPES` 119 键，剥离 `safety_*`、`hook_*`、`last_real_fod_*`、`__de_migrated_v1` 及本机 App 界面偏好键，拒绝 `NaN`/`Infinity` 并按客户端边界钳制数值
- **快捷方式规范化**：
  - 快捷包上传强制把所有项及子项 `customOffsetX = 0`、`customOffsetY = 0`
  - 顶层 `<= 50` 项，含文件夹子项总数 `<= 100` 项，禁止文件夹嵌套文件夹
- **自定义图标校验**：仅允许标准 Base64 编码的 `PNG` / `JPEG` / `WEBP`（校验魔数），单张解码体积 `<= 64 KB`，单次上传总数 `<= 24` 张
- **SHA-256 去重**：同市场存在相同规范化内容的未隐藏条目时返回 `409 Conflict`
- **IP 限流**：上传每小时 ≤ `5` 次、每日 ≤ `15` 次；举报每小时 ≤ `10` 次（超出返回 `429` + `Retry-After`）

### 管理员接口（均需 `Authorization: Bearer <ADMIN_TOKEN>`）

- `GET /api/admin/items?kind=all|config|shortcut&status=all|pending|approved|hidden|reported&q=...&page=1&pageSize=20`
- `GET /api/admin/items/:kind/:id`（返回元数据、`rawBody`、`extractedImages`、`riskSummary`、`reports`）
- `POST /api/admin/items/:kind/:id/status`（Body: `{ "status": "approved" | "pending" | "hidden" }`）
- `DELETE /api/admin/items/:kind/:id`

---

## Cloudflare 免费套餐限额注意事项

- **Workers Free**：每日 `100,000` 次请求、单次请求 CPU 时间上限 `10 ms`。本服务限制单请求 `512 KB`、单图 `64 KB` 且最多 `24` 张，避免 Base64 解码与 JSON 序列化超出 CPU 限制。
- **D1 Free**：每日读行 `5,000,000` 行、写行 `100,000` 行、总容量 `5 GB`。
  - 公开列表接口返回 `Cache-Control: public, max-age=30, stale-while-revalidate=60`，详情接口支持 `ETag` 304 协商，减少重复 D1 读取；
  - 过期 `rate_limits` 记录会在写操作后通过 `ctx.waitUntil` 异步清理，避免长期膨胀；
  - 若后续分享规模快速增长，建议为列表查询接入 Cloudflare Cache API 或迁移自定义图片到 R2。
