import { renderAdminHtml } from "./admin.js";
import {
  MAX_PAYLOAD_BYTES,
  ValidationError,
  compactRiskSummaryForMetadata,
  normalizeShortcuts,
  validateAndNormalizeConfig,
  validateAuthorDisplayName,
  validateBadgeLabel,
  validateUploadMetadata,
} from "./validator.js";

const REPORT_AUTO_HIDE_THRESHOLD = 5;
const UPLOAD_HOURLY_LIMIT = 5;
const UPLOAD_DAILY_LIMIT = 15;
const REPORT_HOURLY_LIMIT = 10;
const AUTHOR_RENAME_HOURLY_LIMIT = 3;

class RateLimitError extends Error {
  constructor(message, retryAfterSeconds) {
    super(message);
    this.name = "RateLimitError";
    this.status = 429;
    this.retryAfterSeconds = Math.max(1, retryAfterSeconds || 60);
  }
}

class ConflictError extends Error {
  constructor(message, extra = {}) {
    super(message);
    this.name = "ConflictError";
    this.status = 409;
    this.extra = extra;
  }
}

function corsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, PUT, PATCH, DELETE, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Manage-Token, X-Author-Secret, If-None-Match",
    "Access-Control-Expose-Headers": "ETag",
  };
}

function jsonResponse(data, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      ...corsHeaders(),
      ...extraHeaders,
    },
  });
}

function errorResponse(status, message, extra = {}, extraHeaders = {}) {
  return jsonResponse({ ok: false, error: message, ...extra }, status, extraHeaders);
}

function safeParseJsonArray(raw) {
  if (!raw || typeof raw !== "string") return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function formatPublicAuthor(row) {
  if (!row) return null;
  return {
    id: row.id,
    displayName: row.display_name,
    isOfficial: Boolean(row.is_official),
    badgeLabel: row.badge_label || "",
    createdAt: Number(row.created_at),
    updatedAt: Number(row.updated_at),
  };
}

function formatAdminAuthor(row) {
  return {
    ...formatPublicAuthor(row),
    normalizedName: row.normalized_name,
    configCount: Number(row.config_count || 0),
    shortcutCount: Number(row.shortcut_count || 0),
  };
}

function formatConfigRow(row, options = { includeLinkedStatus: false }) {
  const hasLinkedPack = Boolean(row.linked_pack_id);
  return {
    id: row.id,
    name: row.name,
    description: row.description,
    author: row.author,
    authorId: row.author_id || null,
    authorIsOfficial: Boolean(row.author_is_official),
    authorBadgeLabel: row.author_badge_label || "",
    linkedShortcutPackId: hasLinkedPack ? row.linked_pack_id : null,
    linkedShortcutPack: hasLinkedPack
      ? {
          id: row.linked_pack_id,
          name: row.linked_pack_name || "",
          shortcutCount: Number(row.linked_pack_shortcut_count || 0),
          folderCount: Number(row.linked_pack_folder_count || 0),
          hasRootRisk: Boolean(row.linked_pack_has_root_risk),
          customImageCount: Number(row.linked_pack_custom_image_count || 0),
          ...(options.includeLinkedStatus ? { status: row.linked_pack_status || "" } : {}),
        }
      : null,
    tags: safeParseJsonArray(row.tags_json),
    layoutStyle: Number(row.layout_style),
    materialStyle: Number(row.material_style),
    keyCount: Number(row.key_count),
    shortcutCount: Number(row.shortcut_count),
    hasShortcuts: Boolean(row.has_shortcuts),
    customImageCount: Number(row.custom_image_count),
    hasRootRisk: Boolean(row.has_root_risk),
    riskSummary: safeParseJsonArray(row.risk_summary_json),
    reviewReasons: safeParseJsonArray(row.review_reasons_json),
    minAppVersionCode: Number(row.min_app_version_code),
    appVersion: row.app_version || "",
    downloads: Number(row.downloads),
    reportsCount: Number(row.reports_count),
    status: row.status,
    sha256: row.sha256,
    sizeBytes: Number(row.size_bytes),
    createdAt: Number(row.created_at),
    updatedAt: Number(row.updated_at),
  };
}

function formatShortcutRow(row) {
  return {
    id: row.id,
    name: row.name,
    description: row.description,
    author: row.author,
    authorId: row.author_id || null,
    authorIsOfficial: Boolean(row.author_is_official),
    authorBadgeLabel: row.author_badge_label || "",
    tags: safeParseJsonArray(row.tags_json),
    shortcutCount: Number(row.shortcut_count),
    folderCount: Number(row.folder_count),
    actionTypes: safeParseJsonArray(row.action_types_json),
    customImageCount: Number(row.custom_image_count),
    hasRootRisk: Boolean(row.has_root_risk),
    riskSummary: safeParseJsonArray(row.risk_summary_json),
    reviewReasons: safeParseJsonArray(row.review_reasons_json),
    minAppVersionCode: Number(row.min_app_version_code),
    appVersion: row.app_version || "",
    downloads: Number(row.downloads),
    reportsCount: Number(row.reports_count),
    status: row.status,
    sha256: row.sha256,
    sizeBytes: Number(row.size_bytes),
    createdAt: Number(row.created_at),
    updatedAt: Number(row.updated_at),
  };
}

function formatUnifiedAdminRow(row) {
  if (row.kind === "config") {
    return {
      kind: "config",
      ...formatConfigRow(row, { includeLinkedStatus: true }),
    };
  }
  return {
    kind: "shortcut",
    ...formatShortcutRow(row),
  };
}

async function sha256Buffer(text) {
  const bytes = new TextEncoder().encode(text);
  return crypto.subtle.digest("SHA-256", bytes);
}

async function sha256Hex(text) {
  const buf = await sha256Buffer(text);
  const view = new Uint8Array(buf);
  let out = "";
  for (let i = 0; i < view.length; i++) {
    out += view[i].toString(16).padStart(2, "0");
  }
  return out;
}

async function constantTimeEqual(a, b) {
  const [ha, hb] = await Promise.all([sha256Buffer(String(a)), sha256Buffer(String(b))]);
  return crypto.subtle.timingSafeEqual(ha, hb);
}

function generateManageToken() {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  let binary = "";
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function getClientIpHash(request) {
  const cfIp = request.headers.get("CF-Connecting-IP");
  const xff = request.headers.get("X-Forwarded-For");
  const rawIp = (cfIp || (xff ? xff.split(",")[0] : "") || "0.0.0.0").trim();
  return sha256Hex(`hyperopen-market-ip-v1:${rawIp}`);
}

async function readAuthorSecret(request, { required = true } = {}) {
  const raw = request.headers.get("X-Author-Secret");
  const secret = typeof raw === "string" ? raw.trim() : "";
  if (!secret) {
    if (!required) return null;
    throw new ValidationError("缺少 X-Author-Secret 请求头", 400);
  }
  if (secret.length < 16 || secret.length > 256) {
    throw new ValidationError("X-Author-Secret 长度必须在 16 到 256 个字符之间", 400);
  }
  const secretHash = await sha256Hex(secret);
  return { secret, secretHash };
}

async function readLimitedJsonBody(request, allowEmpty = false) {
  const contentLenHeader = request.headers.get("Content-Length");
  if (contentLenHeader !== null) {
    const declared = Number(contentLenHeader);
    if (Number.isFinite(declared) && declared > MAX_PAYLOAD_BYTES) {
      throw new ValidationError("请求体超过 512 KB 上限", 413);
    }
  }

  if (!request.body) {
    if (allowEmpty) return {};
    throw new ValidationError("请求体不能为空");
  }

  const reader = request.body.getReader();
  const chunks = [];
  let totalBytes = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      if (value) {
        totalBytes += value.byteLength;
        if (totalBytes > MAX_PAYLOAD_BYTES) {
          await reader.cancel().catch(() => {});
          throw new ValidationError("请求体超过 512 KB 上限", 413);
        }
        chunks.push(value);
      }
    }
  } finally {
    reader.releaseLock();
  }

  if (totalBytes === 0) {
    if (allowEmpty) return {};
    throw new ValidationError("请求体不能为空");
  }

  const merged = new Uint8Array(totalBytes);
  let offset = 0;
  for (const chunk of chunks) {
    merged.set(chunk, offset);
    offset += chunk.byteLength;
  }

  const text = new TextDecoder().decode(merged);
  if (!text.trim()) {
    if (allowEmpty) return {};
    throw new ValidationError("请求体不能为空");
  }

  try {
    return JSON.parse(text);
  } catch {
    throw new ValidationError("请求体不是合法的 JSON 格式");
  }
}

async function verifyAdmin(request, env) {
  const expected = env.ADMIN_TOKEN;
  if (typeof expected !== "string" || expected.length === 0) {
    return { ok: false, status: 500, error: "服务端未配置 ADMIN_TOKEN" };
  }
  const auth = request.headers.get("Authorization") || "";
  if (!auth.toLowerCase().startsWith("bearer ")) {
    return { ok: false, status: 401, error: "缺少管理员认证凭据" };
  }
  const provided = auth.slice(7).trim();
  if (!provided) {
    return { ok: false, status: 401, error: "管理员凭据为空" };
  }
  const matched = await constantTimeEqual(provided, expected);
  if (!matched) {
    return { ok: false, status: 401, error: "管理员凭据无效" };
  }
  return { ok: true };
}

const RATE_UPSERT_SQL = `
  INSERT INTO rate_limits (bucket_key, count, reset_at)
  VALUES (?, 1, ?)
  ON CONFLICT(bucket_key) DO UPDATE SET
    count = CASE WHEN rate_limits.reset_at <= ? THEN 1 ELSE rate_limits.count + 1 END,
    reset_at = CASE WHEN rate_limits.reset_at <= ? THEN excluded.reset_at ELSE rate_limits.reset_at END
`;

async function planRateLimitStatements(db, checks) {
  if (!checks || checks.length === 0) return [];
  const now = Date.now();
  const hourBucket = Math.floor(now / 3600000);
  const hrReset = (hourBucket + 1) * 3600000;
  const dayBucket = Math.floor(now / 86400000);
  const dayReset = (dayBucket + 1) * 86400000;

  const statements = [];
  for (const { category, key } of checks) {
    if (category === "upload") {
      const hrKey = `upload:hr:${key}:${hourBucket}`;
      const dayKey = `upload:day:${key}:${dayBucket}`;
      const existing = await db
        .prepare("SELECT bucket_key, count FROM rate_limits WHERE bucket_key IN (?, ?) AND reset_at > ?")
        .bind(hrKey, dayKey, now)
        .all();

      let hrCount = 0;
      let dayCount = 0;
      for (const row of existing.results || []) {
        if (row.bucket_key === hrKey) hrCount = Number(row.count);
        if (row.bucket_key === dayKey) dayCount = Number(row.count);
      }

      if (hrCount >= UPLOAD_HOURLY_LIMIT) {
        throw new RateLimitError(
          `上传过于频繁，每小时最多 ${UPLOAD_HOURLY_LIMIT} 次`,
          Math.ceil((hrReset - now) / 1000)
        );
      }
      if (dayCount >= UPLOAD_DAILY_LIMIT) {
        throw new RateLimitError(
          `今日上传次数已达上限（每日最多 ${UPLOAD_DAILY_LIMIT} 次）`,
          Math.ceil((dayReset - now) / 1000)
        );
      }

      statements.push(db.prepare(RATE_UPSERT_SQL).bind(hrKey, hrReset, now, now));
      statements.push(db.prepare(RATE_UPSERT_SQL).bind(dayKey, dayReset, now, now));
    } else if (category === "report") {
      const hrKey = `report:hr:${key}:${hourBucket}`;
      const row = await db
        .prepare("SELECT count FROM rate_limits WHERE bucket_key = ? AND reset_at > ?")
        .bind(hrKey, now)
        .first();

      if (row && Number(row.count) >= REPORT_HOURLY_LIMIT) {
        throw new RateLimitError(
          `举报过于频繁，每小时最多 ${REPORT_HOURLY_LIMIT} 次`,
          Math.ceil((hrReset - now) / 1000)
        );
      }
      statements.push(db.prepare(RATE_UPSERT_SQL).bind(hrKey, hrReset, now, now));
    } else if (category === "rename") {
      const hrKey = `rename:hr:${key}:${hourBucket}`;
      const row = await db
        .prepare("SELECT count FROM rate_limits WHERE bucket_key = ? AND reset_at > ?")
        .bind(hrKey, now)
        .first();

      if (row && Number(row.count) >= AUTHOR_RENAME_HOURLY_LIMIT) {
        throw new RateLimitError(
          `修改作者昵称过于频繁，每小时最多 ${AUTHOR_RENAME_HOURLY_LIMIT} 次`,
          Math.ceil((hrReset - now) / 1000)
        );
      }
      statements.push(db.prepare(RATE_UPSERT_SQL).bind(hrKey, hrReset, now, now));
    }
  }
  return statements;
}

function scheduleRateLimitCleanup(db, ctx) {
  const now = Date.now();
  ctx.waitUntil(
    db
      .prepare("DELETE FROM rate_limits WHERE reset_at <= ?")
      .bind(now)
      .run()
      .catch(() => {})
  );
}

async function enforceRateLimit(db, ctx, category, key) {
  const stmts = await planRateLimitStatements(db, [{ category, key }]);
  if (stmts.length > 0) {
    await db.batch(stmts);
    scheduleRateLimitCleanup(db, ctx);
  }
}

function escapeLike(q) {
  return q.replace(/[\\%_]/g, "\\$&");
}

function parsePagination(url) {
  const rawPage = Number.parseInt(url.searchParams.get("page") || "1", 10);
  const rawSize = Number.parseInt(url.searchParams.get("pageSize") || "20", 10);
  const page = Number.isFinite(rawPage) && rawPage >= 1 ? rawPage : 1;
  const pageSize = Number.isFinite(rawSize) ? Math.max(1, Math.min(50, rawSize)) : 20;
  return { page, pageSize, offset: (page - 1) * pageSize };
}

function etagMatches(headerVal, etag) {
  if (!headerVal) return false;
  const bare = etag.startsWith('"') && etag.endsWith('"') ? etag.slice(1, -1) : etag;
  const parts = headerVal.split(",");
  for (const part of parts) {
    const normalized = part.trim().replace(/^W\//, "");
    if (normalized === "*" || normalized === etag || normalized === bare) {
      return true;
    }
  }
  return false;
}

function isUniqueConstraintError(err) {
  const msg = err instanceof Error ? err.message : String(err || "");
  return /UNIQUE constraint failed/i.test(msg);
}

async function findAuthorBySecretHash(db, secretHash) {
  return db
    .prepare(
      "SELECT id, secret_hash, display_name, normalized_name, is_official, badge_label, created_at, updated_at FROM authors WHERE secret_hash = ?"
    )
    .bind(secretHash)
    .first();
}

async function prepareAuthorUpsert(db, existingAuthor, secretHash, displayName, normalizedName, now) {
  if (existingAuthor) {
    const renamed = existingAuthor.display_name !== displayName;
    if (!renamed) {
      return { author: existingAuthor, renamed: false, statements: [] };
    }
    const conflict = await db
      .prepare("SELECT id FROM authors WHERE normalized_name = ? AND id != ? LIMIT 1")
      .bind(normalizedName, existingAuthor.id)
      .first();
    if (conflict) {
      throw new ConflictError("该作者昵称已被其他设备占用");
    }
    const updatedAuthor = {
      ...existingAuthor,
      display_name: displayName,
      normalized_name: normalizedName,
      updated_at: now,
    };
    const stmt = db
      .prepare("UPDATE authors SET display_name = ?, normalized_name = ?, updated_at = ? WHERE id = ?")
      .bind(displayName, normalizedName, now, existingAuthor.id);
    return { author: updatedAuthor, renamed: true, statements: [stmt] };
  }

  const conflict = await db
    .prepare("SELECT id FROM authors WHERE normalized_name = ? LIMIT 1")
    .bind(normalizedName)
    .first();
  if (conflict) {
    throw new ConflictError("该作者昵称已被其他设备占用");
  }

  const newAuthor = {
    id: crypto.randomUUID(),
    secret_hash: secretHash,
    display_name: displayName,
    normalized_name: normalizedName,
    is_official: 0,
    badge_label: "",
    created_at: now,
    updated_at: now,
  };
  const stmt = db
    .prepare(
      `INSERT INTO authors (id, secret_hash, display_name, normalized_name, is_official, badge_label, created_at, updated_at)
       VALUES (?, ?, ?, ?, 0, '', ?, ?)`
    )
    .bind(newAuthor.id, secretHash, displayName, normalizedName, now, now);
  return { author: newAuthor, renamed: false, statements: [stmt] };
}

async function handleHealth(env) {
  await env.DB.prepare("SELECT 1 AS ok").first();
  return jsonResponse({ ok: true, service: "hyperopen-market", timestamp: Date.now() });
}

async function handleGetAuthorMe(request, env) {
  const auth = await readAuthorSecret(request, { required: false });
  if (!auth) {
    return jsonResponse({ ok: true, author: null }, 200, { "Cache-Control": "no-store" });
  }
  const row = await findAuthorBySecretHash(env.DB, auth.secretHash);
  return jsonResponse(
    { ok: true, author: row ? formatPublicAuthor(row) : null },
    200,
    { "Cache-Control": "no-store" }
  );
}

async function handlePutAuthorMe(request, env, ctx) {
  const { secretHash } = await readAuthorSecret(request, { required: true });
  const body = await readLimitedJsonBody(request);
  const { displayName, normalizedName } = validateAuthorDisplayName(body.displayName);
  const existingAuthor = await findAuthorBySecretHash(env.DB, secretHash);
  const now = Date.now();

  const plan = await prepareAuthorUpsert(env.DB, existingAuthor, secretHash, displayName, normalizedName, now);
  if (plan.statements.length === 0) {
    return jsonResponse({ ok: true, author: formatPublicAuthor(plan.author) }, 200, { "Cache-Control": "no-store" });
  }

  const rateStmts = plan.renamed
    ? await planRateLimitStatements(env.DB, [{ category: "rename", key: secretHash }])
    : [];

  try {
    await env.DB.batch([...rateStmts, ...plan.statements]);
  } catch (err) {
    if (isUniqueConstraintError(err)) {
      return errorResponse(409, "该作者昵称已被其他设备占用", {}, { "Cache-Control": "no-store" });
    }
    throw err;
  }

  if (rateStmts.length > 0) {
    scheduleRateLimitCleanup(env.DB, ctx);
  }

  const saved = await findAuthorBySecretHash(env.DB, secretHash);
  return jsonResponse(
    { ok: true, author: formatPublicAuthor(saved || plan.author) },
    200,
    { "Cache-Control": "no-store" }
  );
}

async function handleListConfigs(url, env) {
  const { page, pageSize, offset } = parsePagination(url);
  const where = ["c.status = 'approved'"];
  const params = [];

  const q = (url.searchParams.get("q") || "").trim();
  if (q) {
    const pattern = `%${escapeLike(q)}%`;
    where.push(
      "(c.name LIKE ? ESCAPE '\\' OR c.description LIKE ? ESCAPE '\\' OR COALESCE(a.display_name, c.author) LIKE ? ESCAPE '\\' OR c.tags_json LIKE ? ESCAPE '\\')"
    );
    params.push(pattern, pattern, pattern, pattern);
  }

  const layoutParam = url.searchParams.get("layout");
  if (layoutParam !== null && layoutParam !== "") {
    const layout = Number.parseInt(layoutParam, 10);
    if (!Number.isFinite(layout)) return errorResponse(400, "layout 参数无效");
    where.push("c.layout_style = ?");
    params.push(layout);
  }

  const materialParam = url.searchParams.get("material");
  if (materialParam !== null && materialParam !== "") {
    const material = Number.parseInt(materialParam, 10);
    if (!Number.isFinite(material)) return errorResponse(400, "material 参数无效");
    where.push("c.material_style = ?");
    params.push(material);
  }

  const hasShortcutsParam = url.searchParams.get("hasShortcuts");
  if (hasShortcutsParam === "1" || hasShortcutsParam === "true") {
    where.push("c.has_shortcuts = 1");
  } else if (hasShortcutsParam === "0" || hasShortcutsParam === "false") {
    where.push("c.has_shortcuts = 0");
  }

  const sort =
    url.searchParams.get("sort") === "downloads"
      ? "c.downloads DESC, c.created_at DESC"
      : "c.created_at DESC";
  const whereSql = where.join(" AND ");

  const [countRes, listRes] = await env.DB.batch([
    env.DB
      .prepare(
        `SELECT COUNT(*) AS total
         FROM configs c
         LEFT JOIN authors a ON c.author_id = a.id
         WHERE ${whereSql}`
      )
      .bind(...params),
    env.DB
      .prepare(
        `SELECT c.id, c.name, c.description,
                COALESCE(a.display_name, c.author) AS author,
                c.author_id,
                COALESCE(a.is_official, 0) AS author_is_official,
                COALESCE(a.badge_label, '') AS author_badge_label,
                sp.id AS linked_pack_id,
                sp.name AS linked_pack_name,
                sp.shortcut_count AS linked_pack_shortcut_count,
                sp.folder_count AS linked_pack_folder_count,
                sp.has_root_risk AS linked_pack_has_root_risk,
                sp.custom_image_count AS linked_pack_custom_image_count,
                c.tags_json, c.layout_style, c.material_style, c.key_count,
                c.shortcut_count, c.has_shortcuts, c.custom_image_count, c.has_root_risk, c.risk_summary_json,
                c.review_reasons_json, c.min_app_version_code, c.app_version, c.downloads, c.reports_count,
                c.status, c.sha256, c.size_bytes, c.created_at, c.updated_at
         FROM configs c
         LEFT JOIN authors a ON c.author_id = a.id
         LEFT JOIN shortcut_packs sp ON c.linked_shortcut_pack_id = sp.id AND sp.status = 'approved'
         WHERE ${whereSql}
         ORDER BY ${sort}
         LIMIT ? OFFSET ?`
      )
      .bind(...params, pageSize, offset),
  ]);

  const total = Number(countRes.results?.[0]?.total || 0);
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const items = (listRes.results || []).map((r) => formatConfigRow(r));

  return jsonResponse(
    {
      ok: true,
      items,
      pagination: {
        page,
        pageSize,
        total,
        totalPages,
        hasMore: page < totalPages,
      },
    },
    200,
    { "Cache-Control": "public, max-age=30, stale-while-revalidate=60" }
  );
}

async function handleGetConfigDetail(id, url, request, env, ctx) {
  const row = await env.DB
    .prepare(
      `SELECT c.id, c.name, c.description,
              COALESCE(a.display_name, c.author) AS author,
              c.author_id,
              COALESCE(a.is_official, 0) AS author_is_official,
              COALESCE(a.badge_label, '') AS author_badge_label,
              sp.id AS linked_pack_id,
              sp.name AS linked_pack_name,
              sp.shortcut_count AS linked_pack_shortcut_count,
              sp.folder_count AS linked_pack_folder_count,
              sp.has_root_risk AS linked_pack_has_root_risk,
              sp.custom_image_count AS linked_pack_custom_image_count,
              sp.updated_at AS linked_pack_updated_at,
              c.tags_json, c.layout_style, c.material_style,
              c.key_count, c.shortcut_count, c.has_shortcuts, c.custom_image_count, c.has_root_risk,
              c.risk_summary_json, c.review_reasons_json, c.min_app_version_code, c.app_version,
              c.downloads, c.reports_count, c.status, c.sha256, c.size_bytes, c.created_at, c.updated_at,
              b.json_body
       FROM configs c
       JOIN config_bodies b ON b.id = c.id
       LEFT JOIN authors a ON c.author_id = a.id
       LEFT JOIN shortcut_packs sp ON c.linked_shortcut_pack_id = sp.id AND sp.status = 'approved'
       WHERE c.id = ? AND c.status = 'approved'`
    )
    .bind(id)
    .first();

  if (!row) {
    return errorResponse(404, "配置不存在或未公开");
  }

  const item = formatConfigRow(row);
  const etagDigest = await sha256Hex(
    JSON.stringify([
      row.sha256,
      item.updatedAt,
      item.authorId,
      item.author,
      item.authorIsOfficial,
      item.authorBadgeLabel,
      item.linkedShortcutPackId,
      item.linkedShortcutPack ? item.linkedShortcutPack.name : null,
      Number(row.linked_pack_updated_at || 0),
    ])
  );
  const etag = `"${row.sha256.slice(0, 16)}-${etagDigest.slice(0, 16)}"`;
  const isDownload = url.searchParams.get("download") === "1";
  const cacheControl = isDownload ? "no-store" : "public, max-age=60";

  if (etagMatches(request.headers.get("If-None-Match"), etag)) {
    return new Response(null, {
      status: 304,
      headers: {
        ...corsHeaders(),
        ETag: etag,
        "Cache-Control": cacheControl,
      },
    });
  }

  if (isDownload) {
    ctx.waitUntil(
      env.DB.prepare("UPDATE configs SET downloads = downloads + 1 WHERE id = ? AND status = 'approved'")
        .bind(id)
        .run()
        .catch(() => {})
    );
  }

  return jsonResponse(
    {
      ok: true,
      item,
      jsonBody: row.json_body,
      sha256: row.sha256,
    },
    200,
    {
      ETag: etag,
      "Cache-Control": cacheControl,
    }
  );
}

async function handleCreateConfig(request, env, ctx) {
  const { secretHash } = await readAuthorSecret(request, { required: true });
  const body = await readLimitedJsonBody(request);
  const existingAuthor = await findAuthorBySecretHash(env.DB, secretHash);
  const meta = validateUploadMetadata(body, { allowEmptyAuthor: Boolean(existingAuthor) });
  const displayName = meta.author || existingAuthor.display_name;
  const normalizedName = displayName.toLowerCase();

  const rawConfig = body.jsonBody !== undefined ? body.jsonBody : body.config;
  if (rawConfig === undefined) {
    throw new ValidationError("缺少 jsonBody 字段");
  }

  let linkedShortcutPackId = null;
  if (body.linkedShortcutPackId !== undefined && body.linkedShortcutPackId !== null) {
    if (typeof body.linkedShortcutPackId !== "string") {
      throw new ValidationError("linkedShortcutPackId 必须是字符串");
    }
    const trimmedPackId = body.linkedShortcutPackId.trim();
    if (trimmedPackId) {
      if (trimmedPackId.length > 128) {
        throw new ValidationError("linkedShortcutPackId 长度超出限制");
      }
      const pack = await env.DB
        .prepare("SELECT id, status, author_id FROM shortcut_packs WHERE id = ?")
        .bind(trimmedPackId)
        .first();
      if (!pack) {
        throw new ValidationError("关联的快捷包不存在");
      }
      const isApproved = pack.status === "approved";
      const isOwnPending =
        pack.status !== "hidden" && Boolean(existingAuthor && pack.author_id && pack.author_id === existingAuthor.id);
      if (!isApproved && !isOwnPending) {
        throw new ValidationError("只能关联已上架或本人上传的待审快捷包");
      }
      linkedShortcutPackId = pack.id;
    }
  }

  const normalized = validateAndNormalizeConfig(rawConfig);
  const sizeBytes = new TextEncoder().encode(normalized.jsonBody).byteLength;
  if (sizeBytes > MAX_PAYLOAD_BYTES) {
    throw new ValidationError("规范化后的配置正文超过 512 KB 上限", 413);
  }

  const sha256 = await sha256Hex(normalized.jsonBody);
  const dup = await env.DB
    .prepare("SELECT id, status FROM configs WHERE sha256 = ? AND status IN ('pending', 'approved') LIMIT 1")
    .bind(sha256)
    .first();
  if (dup) {
    return errorResponse(409, "配置市场中已存在相同内容", {
      existingId: dup.id,
      existingStatus: dup.status,
    });
  }

  const now = Date.now();
  const authorPlan = await prepareAuthorUpsert(
    env.DB,
    existingAuthor,
    secretHash,
    displayName,
    normalizedName,
    now
  );

  const ipHash = await getClientIpHash(request);
  const rateChecks = [{ category: "upload", key: ipHash }];
  if (authorPlan.renamed) {
    rateChecks.push({ category: "rename", key: secretHash });
  }
  const rateStmts = await planRateLimitStatements(env.DB, rateChecks);

  const id = crypto.randomUUID();
  const manageToken = generateManageToken();
  const manageTokenHash = await sha256Hex(manageToken);
  const appVersion = meta.appVersion || normalized.appVersion;
  const compactRisks = compactRiskSummaryForMetadata(normalized.riskSummary);

  try {
    await env.DB.batch([
      ...rateStmts,
      ...authorPlan.statements,
      env.DB
        .prepare(
          `INSERT INTO configs (
            id, name, description, author, author_id, linked_shortcut_pack_id, tags_json,
            layout_style, material_style, key_count, shortcut_count, has_shortcuts,
            custom_image_count, has_root_risk, risk_summary_json, review_reasons_json,
            min_app_version_code, app_version, downloads, reports_count,
            status, sha256, size_bytes, manage_token_hash, created_at, updated_at
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?, ?, ?, ?, ?)`
        )
        .bind(
          id,
          meta.name,
          meta.description,
          displayName,
          authorPlan.author.id,
          linkedShortcutPackId,
          JSON.stringify(meta.tags),
          normalized.layoutStyle,
          normalized.materialStyle,
          normalized.keyCount,
          normalized.shortcutCount,
          normalized.hasShortcuts ? 1 : 0,
          normalized.customImageCount,
          normalized.hasRootRisk ? 1 : 0,
          JSON.stringify(compactRisks),
          JSON.stringify(normalized.reviewReasons),
          meta.minAppVersionCode,
          appVersion,
          normalized.status,
          sha256,
          sizeBytes,
          manageTokenHash,
          now,
          now
        ),
      env.DB.prepare("INSERT INTO config_bodies (id, json_body) VALUES (?, ?)").bind(id, normalized.jsonBody),
    ]);
  } catch (err) {
    if (isUniqueConstraintError(err)) {
      return errorResponse(409, "该作者昵称已被其他设备占用");
    }
    throw err;
  }

  scheduleRateLimitCleanup(env.DB, ctx);

  const createdRow = await env.DB
    .prepare(
      `SELECT c.id, c.name, c.description,
              COALESCE(a.display_name, c.author) AS author,
              c.author_id,
              COALESCE(a.is_official, 0) AS author_is_official,
              COALESCE(a.badge_label, '') AS author_badge_label,
              sp.id AS linked_pack_id,
              sp.name AS linked_pack_name,
              sp.shortcut_count AS linked_pack_shortcut_count,
              sp.folder_count AS linked_pack_folder_count,
              sp.has_root_risk AS linked_pack_has_root_risk,
              sp.custom_image_count AS linked_pack_custom_image_count,
              c.tags_json, c.layout_style, c.material_style, c.key_count,
              c.shortcut_count, c.has_shortcuts, c.custom_image_count, c.has_root_risk, c.risk_summary_json,
              c.review_reasons_json, c.min_app_version_code, c.app_version, c.downloads, c.reports_count,
              c.status, c.sha256, c.size_bytes, c.created_at, c.updated_at
       FROM configs c
       LEFT JOIN authors a ON c.author_id = a.id
       LEFT JOIN shortcut_packs sp ON c.linked_shortcut_pack_id = sp.id AND sp.status = 'approved'
       WHERE c.id = ?`
    )
    .bind(id)
    .first();

  return jsonResponse(
    {
      ok: true,
      id,
      status: normalized.status,
      reviewReasons: normalized.reviewReasons,
      manageToken,
      item: formatConfigRow(createdRow),
    },
    201
  );
}

async function executeDeleteItemBatch(db, kind, id) {
  if (kind === "shortcut") {
    const now = Date.now();
    await db.batch([
      db
        .prepare("UPDATE configs SET linked_shortcut_pack_id = NULL, updated_at = ? WHERE linked_shortcut_pack_id = ?")
        .bind(now, id),
      db.prepare("DELETE FROM reports WHERE kind = 'shortcut' AND item_id = ?").bind(id),
      db.prepare("DELETE FROM shortcut_pack_bodies WHERE id = ?").bind(id),
      db.prepare("DELETE FROM shortcut_packs WHERE id = ?").bind(id),
    ]);
  } else {
    await db.batch([
      db.prepare("DELETE FROM reports WHERE kind = 'config' AND item_id = ?").bind(id),
      db.prepare("DELETE FROM config_bodies WHERE id = ?").bind(id),
      db.prepare("DELETE FROM configs WHERE id = ?").bind(id),
    ]);
  }
}

async function handleDeleteItem(kind, id, request, env) {
  const table = kind === "config" ? "configs" : "shortcut_packs";
  const authorSecret = (request.headers.get("X-Author-Secret") || "").trim();
  const manageToken = (request.headers.get("X-Manage-Token") || "").trim();

  if (!authorSecret && !manageToken) {
    return errorResponse(401, "缺少 X-Author-Secret 或 X-Manage-Token 请求头");
  }

  const row = await env.DB
    .prepare(`SELECT id, author_id, manage_token_hash FROM ${table} WHERE id = ?`)
    .bind(id)
    .first();
  if (!row) {
    return errorResponse(404, "条目不存在");
  }

  let authorized = false;
  if (authorSecret && authorSecret.length >= 16 && authorSecret.length <= 256 && row.author_id) {
    const secretHash = await sha256Hex(authorSecret);
    const authorRow = await env.DB
      .prepare("SELECT id FROM authors WHERE secret_hash = ?")
      .bind(secretHash)
      .first();
    if (authorRow && authorRow.id === row.author_id) {
      authorized = true;
    }
  }

  if (!authorized && manageToken) {
    const providedHash = await sha256Hex(manageToken);
    if (await constantTimeEqual(providedHash, row.manage_token_hash)) {
      authorized = true;
    }
  }

  if (!authorized) {
    return errorResponse(403, "X-Author-Secret 或 X-Manage-Token 无效");
  }

  await executeDeleteItemBatch(env.DB, kind, id);
  return jsonResponse({ ok: true, id, deleted: true });
}

async function handleReportItem(kind, id, request, env, ctx) {
  const table = kind === "config" ? "configs" : "shortcut_packs";
  const target = await env.DB
    .prepare(`SELECT id, status, reports_count FROM ${table} WHERE id = ? AND status = 'approved'`)
    .bind(id)
    .first();
  if (!target) {
    return errorResponse(404, "条目不存在或未公开");
  }

  const ipHash = await getClientIpHash(request);
  await enforceRateLimit(env.DB, ctx, "report", ipHash);

  const body = await readLimitedJsonBody(request, true);
  let reason = "";
  if (body && body.reason !== undefined && body.reason !== null) {
    if (typeof body.reason !== "string") {
      throw new ValidationError("reason 必须是字符串");
    }
    reason = body.reason.trim().slice(0, 300);
  }

  const now = Date.now();
  const insertRes = await env.DB
    .prepare(
      `INSERT OR IGNORE INTO reports (kind, item_id, ip_hash, reason, created_at)
       VALUES (?, ?, ?, ?, ?)`
    )
    .bind(kind, id, ipHash, reason, now)
    .run();

  if (!insertRes.meta || Number(insertRes.meta.changes || 0) === 0) {
    return errorResponse(409, "你已经举报过该条目");
  }

  const countRow = await env.DB
    .prepare("SELECT COUNT(*) AS cnt FROM reports WHERE kind = ? AND item_id = ?")
    .bind(kind, id)
    .first();
  const reportsCount = Number(countRow?.cnt || 1);
  const nextStatus = reportsCount >= REPORT_AUTO_HIDE_THRESHOLD ? "hidden" : "approved";

  await env.DB
    .prepare(`UPDATE ${table} SET reports_count = ?, status = ?, updated_at = ? WHERE id = ?`)
    .bind(reportsCount, nextStatus, now, id)
    .run();

  return jsonResponse({
    ok: true,
    id,
    reportsCount,
    hidden: nextStatus === "hidden",
  });
}

async function handleListShortcuts(url, env) {
  const { page, pageSize, offset } = parsePagination(url);
  const where = ["s.status = 'approved'"];
  const params = [];

  const q = (url.searchParams.get("q") || "").trim();
  if (q) {
    const pattern = `%${escapeLike(q)}%`;
    where.push(
      "(s.name LIKE ? ESCAPE '\\' OR s.description LIKE ? ESCAPE '\\' OR COALESCE(a.display_name, s.author) LIKE ? ESCAPE '\\' OR s.tags_json LIKE ? ESCAPE '\\')"
    );
    params.push(pattern, pattern, pattern, pattern);
  }

  const actionType = (url.searchParams.get("actionType") || "").trim().toUpperCase();
  if (actionType) {
    if (!/^[A-Z0-9_]{1,32}$/.test(actionType)) {
      return errorResponse(400, "actionType 参数无效");
    }
    where.push("s.action_types_json LIKE ? ESCAPE '\\'");
    params.push(`%"${escapeLike(actionType)}"%`);
  }

  const hasRoot = url.searchParams.get("hasRoot");
  if (hasRoot === "1" || hasRoot === "true") {
    where.push("s.has_root_risk = 1");
  } else if (hasRoot === "0" || hasRoot === "false") {
    where.push("s.has_root_risk = 0");
  }

  const hasImages = url.searchParams.get("hasImages");
  if (hasImages === "1" || hasImages === "true") {
    where.push("s.custom_image_count > 0");
  } else if (hasImages === "0" || hasImages === "false") {
    where.push("s.custom_image_count = 0");
  }

  const sort =
    url.searchParams.get("sort") === "downloads"
      ? "s.downloads DESC, s.created_at DESC"
      : "s.created_at DESC";
  const whereSql = where.join(" AND ");

  const [countRes, listRes] = await env.DB.batch([
    env.DB
      .prepare(
        `SELECT COUNT(*) AS total
         FROM shortcut_packs s
         LEFT JOIN authors a ON s.author_id = a.id
         WHERE ${whereSql}`
      )
      .bind(...params),
    env.DB
      .prepare(
        `SELECT s.id, s.name, s.description,
                COALESCE(a.display_name, s.author) AS author,
                s.author_id,
                COALESCE(a.is_official, 0) AS author_is_official,
                COALESCE(a.badge_label, '') AS author_badge_label,
                s.tags_json, s.shortcut_count, s.folder_count, s.action_types_json,
                s.custom_image_count, s.has_root_risk, s.risk_summary_json, s.review_reasons_json,
                s.min_app_version_code, s.app_version, s.downloads, s.reports_count, s.status, s.sha256,
                s.size_bytes, s.created_at, s.updated_at
         FROM shortcut_packs s
         LEFT JOIN authors a ON s.author_id = a.id
         WHERE ${whereSql}
         ORDER BY ${sort}
         LIMIT ? OFFSET ?`
      )
      .bind(...params, pageSize, offset),
  ]);

  const total = Number(countRes.results?.[0]?.total || 0);
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const items = (listRes.results || []).map(formatShortcutRow);

  return jsonResponse(
    {
      ok: true,
      items,
      pagination: {
        page,
        pageSize,
        total,
        totalPages,
        hasMore: page < totalPages,
      },
    },
    200,
    { "Cache-Control": "public, max-age=30, stale-while-revalidate=60" }
  );
}

async function handleGetShortcutDetail(id, url, request, env, ctx) {
  const row = await env.DB
    .prepare(
      `SELECT s.id, s.name, s.description,
              COALESCE(a.display_name, s.author) AS author,
              s.author_id,
              COALESCE(a.is_official, 0) AS author_is_official,
              COALESCE(a.badge_label, '') AS author_badge_label,
              s.tags_json, s.shortcut_count, s.folder_count,
              s.action_types_json, s.custom_image_count, s.has_root_risk, s.risk_summary_json,
              s.review_reasons_json, s.min_app_version_code, s.app_version, s.downloads,
              s.reports_count, s.status, s.sha256, s.size_bytes, s.created_at, s.updated_at,
              b.shortcuts_json
       FROM shortcut_packs s
       JOIN shortcut_pack_bodies b ON b.id = s.id
       LEFT JOIN authors a ON s.author_id = a.id
       WHERE s.id = ? AND s.status = 'approved'`
    )
    .bind(id)
    .first();

  if (!row) {
    return errorResponse(404, "快捷包不存在或未公开");
  }

  const item = formatShortcutRow(row);
  const etagDigest = await sha256Hex(
    JSON.stringify([
      row.sha256,
      item.updatedAt,
      item.authorId,
      item.author,
      item.authorIsOfficial,
      item.authorBadgeLabel,
    ])
  );
  const etag = `"${row.sha256.slice(0, 16)}-${etagDigest.slice(0, 16)}"`;
  const isDownload = url.searchParams.get("download") === "1";
  const cacheControl = isDownload ? "no-store" : "public, max-age=60";

  if (etagMatches(request.headers.get("If-None-Match"), etag)) {
    return new Response(null, {
      status: 304,
      headers: {
        ...corsHeaders(),
        ETag: etag,
        "Cache-Control": cacheControl,
      },
    });
  }

  if (isDownload) {
    ctx.waitUntil(
      env.DB.prepare("UPDATE shortcut_packs SET downloads = downloads + 1 WHERE id = ? AND status = 'approved'")
        .bind(id)
        .run()
        .catch(() => {})
    );
  }

  return jsonResponse(
    {
      ok: true,
      item,
      shortcutsJson: row.shortcuts_json,
      sha256: row.sha256,
    },
    200,
    {
      ETag: etag,
      "Cache-Control": cacheControl,
    }
  );
}

async function handleCreateShortcutPack(request, env, ctx) {
  const { secretHash } = await readAuthorSecret(request, { required: true });
  const body = await readLimitedJsonBody(request);
  const existingAuthor = await findAuthorBySecretHash(env.DB, secretHash);
  const meta = validateUploadMetadata(body, { allowEmptyAuthor: Boolean(existingAuthor) });
  const displayName = meta.author || existingAuthor.display_name;
  const normalizedName = displayName.toLowerCase();

  let rawShortcuts;
  if (body.shortcutsJson !== undefined) {
    if (typeof body.shortcutsJson !== "string") {
      throw new ValidationError("shortcutsJson 必须是 JSON 数组字符串");
    }
    try {
      rawShortcuts = JSON.parse(body.shortcutsJson);
    } catch {
      throw new ValidationError("shortcutsJson 不是合法的 JSON");
    }
  } else if (body.shortcuts !== undefined) {
    rawShortcuts = body.shortcuts;
  } else {
    throw new ValidationError("缺少 shortcutsJson 字段");
  }

  const normalized = normalizeShortcuts(rawShortcuts, { forceZeroOffsets: true });
  if (normalized.shortcutCount === 0) {
    throw new ValidationError("快捷方式包至少需要包含 1 个快捷方式");
  }

  const sizeBytes = new TextEncoder().encode(normalized.shortcutsJson).byteLength;
  if (sizeBytes > MAX_PAYLOAD_BYTES) {
    throw new ValidationError("快捷包正文超过 512 KB 上限", 413);
  }

  const sha256 = await sha256Hex(normalized.shortcutsJson);
  const dup = await env.DB
    .prepare("SELECT id, status FROM shortcut_packs WHERE sha256 = ? AND status IN ('pending', 'approved') LIMIT 1")
    .bind(sha256)
    .first();
  if (dup) {
    return errorResponse(409, "快捷方式市场中已存在相同内容", {
      existingId: dup.id,
      existingStatus: dup.status,
    });
  }

  const now = Date.now();
  const authorPlan = await prepareAuthorUpsert(
    env.DB,
    existingAuthor,
    secretHash,
    displayName,
    normalizedName,
    now
  );

  const ipHash = await getClientIpHash(request);
  const rateChecks = [{ category: "upload", key: ipHash }];
  if (authorPlan.renamed) {
    rateChecks.push({ category: "rename", key: secretHash });
  }
  const rateStmts = await planRateLimitStatements(env.DB, rateChecks);

  const id = crypto.randomUUID();
  const manageToken = generateManageToken();
  const manageTokenHash = await sha256Hex(manageToken);
  const reviewReasons = [];
  if (normalized.hasRootRisk) reviewReasons.push("root_action");
  if (normalized.customImageCount > 0) reviewReasons.push("custom_image");
  const status = reviewReasons.length > 0 ? "pending" : "approved";
  const compactRisks = compactRiskSummaryForMetadata(normalized.riskSummary);

  try {
    await env.DB.batch([
      ...rateStmts,
      ...authorPlan.statements,
      env.DB
        .prepare(
          `INSERT INTO shortcut_packs (
            id, name, description, author, author_id, tags_json, shortcut_count, folder_count, action_types_json,
            custom_image_count, has_root_risk, risk_summary_json, review_reasons_json,
            min_app_version_code, app_version, downloads, reports_count, status, sha256,
            size_bytes, manage_token_hash, created_at, updated_at
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?, ?, ?, ?, ?)`
        )
        .bind(
          id,
          meta.name,
          meta.description,
          displayName,
          authorPlan.author.id,
          JSON.stringify(meta.tags),
          normalized.shortcutCount,
          normalized.folderCount,
          JSON.stringify(normalized.actionTypes),
          normalized.customImageCount,
          normalized.hasRootRisk ? 1 : 0,
          JSON.stringify(compactRisks),
          JSON.stringify(reviewReasons),
          meta.minAppVersionCode,
          meta.appVersion,
          status,
          sha256,
          sizeBytes,
          manageTokenHash,
          now,
          now
        ),
      env.DB
        .prepare("INSERT INTO shortcut_pack_bodies (id, shortcuts_json) VALUES (?, ?)")
        .bind(id, normalized.shortcutsJson),
    ]);
  } catch (err) {
    if (isUniqueConstraintError(err)) {
      return errorResponse(409, "该作者昵称已被其他设备占用");
    }
    throw err;
  }

  scheduleRateLimitCleanup(env.DB, ctx);

  const createdRow = await env.DB
    .prepare(
      `SELECT s.id, s.name, s.description,
              COALESCE(a.display_name, s.author) AS author,
              s.author_id,
              COALESCE(a.is_official, 0) AS author_is_official,
              COALESCE(a.badge_label, '') AS author_badge_label,
              s.tags_json, s.shortcut_count, s.folder_count, s.action_types_json,
              s.custom_image_count, s.has_root_risk, s.risk_summary_json, s.review_reasons_json,
              s.min_app_version_code, s.app_version, s.downloads, s.reports_count, s.status, s.sha256,
              s.size_bytes, s.created_at, s.updated_at
       FROM shortcut_packs s
       LEFT JOIN authors a ON s.author_id = a.id
       WHERE s.id = ?`
    )
    .bind(id)
    .first();

  return jsonResponse(
    {
      ok: true,
      id,
      status,
      reviewReasons,
      manageToken,
      item: formatShortcutRow(createdRow),
    },
    201
  );
}

async function handleAdminListItems(url, env) {
  const { page, pageSize, offset } = parsePagination(url);
  const kind = url.searchParams.get("kind") || "all";
  if (!["all", "config", "shortcut"].includes(kind)) {
    return errorResponse(400, "kind 参数仅支持 all / config / shortcut");
  }

  const status = url.searchParams.get("status") || "all";
  if (!["all", "pending", "approved", "hidden", "reported"].includes(status)) {
    return errorResponse(400, "status 参数仅支持 all / pending / approved / hidden / reported");
  }

  const q = (url.searchParams.get("q") || "").trim();

  function buildBranchFilter(alias, authorAlias) {
    const conds = [];
    const params = [];
    if (status === "reported") {
      conds.push(`${alias}reports_count > 0`);
    } else if (status !== "all") {
      conds.push(`${alias}status = ?`);
      params.push(status);
    }
    if (q) {
      const pattern = `%${escapeLike(q)}%`;
      conds.push(
        `(${alias}name LIKE ? ESCAPE '\\' OR ${alias}description LIKE ? ESCAPE '\\' OR COALESCE(${authorAlias}display_name, ${alias}author) LIKE ? ESCAPE '\\' OR ${alias}tags_json LIKE ? ESCAPE '\\')`
      );
      params.push(pattern, pattern, pattern, pattern);
    }
    return {
      whereSql: conds.length ? conds.join(" AND ") : "1=1",
      params,
    };
  }

  const branches = [];
  const allParams = [];

  if (kind === "all" || kind === "config") {
    const f = buildBranchFilter("c.", "a.");
    branches.push(
      `SELECT 'config' AS kind, c.id, c.name, c.description,
              COALESCE(a.display_name, c.author) AS author,
              c.author_id,
              COALESCE(a.is_official, 0) AS author_is_official,
              COALESCE(a.badge_label, '') AS author_badge_label,
              sp.id AS linked_pack_id,
              sp.name AS linked_pack_name,
              sp.status AS linked_pack_status,
              sp.shortcut_count AS linked_pack_shortcut_count,
              sp.folder_count AS linked_pack_folder_count,
              sp.has_root_risk AS linked_pack_has_root_risk,
              sp.custom_image_count AS linked_pack_custom_image_count,
              c.tags_json, c.layout_style, c.material_style,
              c.key_count, c.shortcut_count, c.has_shortcuts, NULL AS folder_count, NULL AS action_types_json,
              c.custom_image_count, c.has_root_risk, c.risk_summary_json, c.review_reasons_json,
              c.min_app_version_code, c.app_version, c.downloads, c.reports_count, c.status, c.sha256,
              c.size_bytes, c.created_at, c.updated_at
       FROM configs c
       LEFT JOIN authors a ON c.author_id = a.id
       LEFT JOIN shortcut_packs sp ON c.linked_shortcut_pack_id = sp.id
       WHERE ${f.whereSql}`
    );
    allParams.push(...f.params);
  }

  if (kind === "all" || kind === "shortcut") {
    const f = buildBranchFilter("s.", "a.");
    branches.push(
      `SELECT 'shortcut' AS kind, s.id, s.name, s.description,
              COALESCE(a.display_name, s.author) AS author,
              s.author_id,
              COALESCE(a.is_official, 0) AS author_is_official,
              COALESCE(a.badge_label, '') AS author_badge_label,
              NULL AS linked_pack_id,
              NULL AS linked_pack_name,
              NULL AS linked_pack_status,
              NULL AS linked_pack_shortcut_count,
              NULL AS linked_pack_folder_count,
              NULL AS linked_pack_has_root_risk,
              NULL AS linked_pack_custom_image_count,
              s.tags_json, NULL AS layout_style, NULL AS material_style,
              NULL AS key_count, s.shortcut_count, NULL AS has_shortcuts, s.folder_count, s.action_types_json,
              s.custom_image_count, s.has_root_risk, s.risk_summary_json, s.review_reasons_json,
              s.min_app_version_code, s.app_version, s.downloads, s.reports_count, s.status, s.sha256,
              s.size_bytes, s.created_at, s.updated_at
       FROM shortcut_packs s
       LEFT JOIN authors a ON s.author_id = a.id
       WHERE ${f.whereSql}`
    );
    allParams.push(...f.params);
  }

  const unionSql = branches.join(" UNION ALL ");
  const [countRes, listRes] = await env.DB.batch([
    env.DB.prepare(`SELECT COUNT(*) AS total FROM (${unionSql})`).bind(...allParams),
    env.DB
      .prepare(`SELECT * FROM (${unionSql}) ORDER BY created_at DESC LIMIT ? OFFSET ?`)
      .bind(...allParams, pageSize, offset),
  ]);

  const total = Number(countRes.results?.[0]?.total || 0);
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const items = (listRes.results || []).map(formatUnifiedAdminRow);

  return jsonResponse(
    {
      ok: true,
      items,
      pagination: {
        page,
        pageSize,
        total,
        totalPages,
        hasMore: page < totalPages,
      },
    },
    200,
    { "Cache-Control": "no-store" }
  );
}

async function handleAdminGetItemDetail(kind, id, env) {
  if (kind !== "config" && kind !== "shortcut") {
    return errorResponse(400, "kind 必须是 config 或 shortcut");
  }

  let row;
  let rawBody;
  let extractedImages = [];
  let fullRiskSummary = [];

  if (kind === "config") {
    row = await env.DB
      .prepare(
        `SELECT c.id, c.name, c.description,
                COALESCE(a.display_name, c.author) AS author,
                c.author_id,
                COALESCE(a.is_official, 0) AS author_is_official,
                COALESCE(a.badge_label, '') AS author_badge_label,
                sp.id AS linked_pack_id,
                sp.name AS linked_pack_name,
                sp.status AS linked_pack_status,
                sp.shortcut_count AS linked_pack_shortcut_count,
                sp.folder_count AS linked_pack_folder_count,
                sp.has_root_risk AS linked_pack_has_root_risk,
                sp.custom_image_count AS linked_pack_custom_image_count,
                c.tags_json, c.layout_style, c.material_style,
                c.key_count, c.shortcut_count, c.has_shortcuts, c.custom_image_count, c.has_root_risk,
                c.risk_summary_json, c.review_reasons_json, c.min_app_version_code, c.app_version,
                c.downloads, c.reports_count, c.status, c.sha256, c.size_bytes, c.created_at, c.updated_at,
                b.json_body
         FROM configs c
         JOIN config_bodies b ON b.id = c.id
         LEFT JOIN authors a ON c.author_id = a.id
         LEFT JOIN shortcut_packs sp ON c.linked_shortcut_pack_id = sp.id
         WHERE c.id = ?`
      )
      .bind(id)
      .first();
    if (!row) return errorResponse(404, "配置不存在");
    rawBody = row.json_body;
    try {
      const inspected = validateAndNormalizeConfig(rawBody);
      extractedImages = inspected.extractedImages;
      fullRiskSummary = inspected.riskSummary;
    } catch {
      fullRiskSummary = safeParseJsonArray(row.risk_summary_json);
    }
  } else {
    row = await env.DB
      .prepare(
        `SELECT s.id, s.name, s.description,
                COALESCE(a.display_name, s.author) AS author,
                s.author_id,
                COALESCE(a.is_official, 0) AS author_is_official,
                COALESCE(a.badge_label, '') AS author_badge_label,
                s.tags_json, s.shortcut_count, s.folder_count,
                s.action_types_json, s.custom_image_count, s.has_root_risk, s.risk_summary_json,
                s.review_reasons_json, s.min_app_version_code, s.app_version, s.downloads,
                s.reports_count, s.status, s.sha256, s.size_bytes, s.created_at, s.updated_at,
                b.shortcuts_json
         FROM shortcut_packs s
         JOIN shortcut_pack_bodies b ON b.id = s.id
         LEFT JOIN authors a ON s.author_id = a.id
         WHERE s.id = ?`
      )
      .bind(id)
      .first();
    if (!row) return errorResponse(404, "快捷包不存在");
    rawBody = row.shortcuts_json;
    try {
      const inspected = normalizeShortcuts(JSON.parse(rawBody), { forceZeroOffsets: true });
      extractedImages = inspected.extractedImages;
      fullRiskSummary = inspected.riskSummary;
    } catch {
      fullRiskSummary = safeParseJsonArray(row.risk_summary_json);
    }
  }

  const reportRows = await env.DB
    .prepare(
      `SELECT id, ip_hash, reason, created_at
       FROM reports
       WHERE kind = ? AND item_id = ?
       ORDER BY created_at DESC
       LIMIT 50`
    )
    .bind(kind, id)
    .all();

  const reports = (reportRows.results || []).map((r) => ({
    id: Number(r.id),
    ipHashPrefix: String(r.ip_hash || "").slice(0, 12),
    reason: r.reason || "",
    createdAt: Number(r.created_at),
  }));

  return jsonResponse(
    {
      ok: true,
      kind,
      item: kind === "config" ? formatConfigRow(row, { includeLinkedStatus: true }) : formatShortcutRow(row),
      rawBody,
      extractedImages: extractedImages.map((img) => ({
        path: img.path,
        field: img.field,
        mimeType: img.mimeType,
        sizeBytes: img.sizeBytes,
        dataUri: img.dataUri,
      })),
      riskSummary: fullRiskSummary,
      reports,
    },
    200,
    { "Cache-Control": "no-store" }
  );
}

async function handleAdminSetStatus(kind, id, request, env) {
  if (kind !== "config" && kind !== "shortcut") {
    return errorResponse(400, "kind 必须是 config 或 shortcut");
  }
  const body = await readLimitedJsonBody(request);
  const status = typeof body.status === "string" ? body.status.trim() : "";
  if (!["approved", "pending", "hidden"].includes(status)) {
    return errorResponse(400, "status 必须是 approved / pending / hidden");
  }

  const table = kind === "config" ? "configs" : "shortcut_packs";
  const now = Date.now();
  const res = await env.DB
    .prepare(`UPDATE ${table} SET status = ?, updated_at = ? WHERE id = ?`)
    .bind(status, now, id)
    .run();

  if (!res.meta || Number(res.meta.changes || 0) === 0) {
    return errorResponse(404, "条目不存在");
  }

  return jsonResponse({ ok: true, kind, id, status }, 200, { "Cache-Control": "no-store" });
}

async function handleAdminDeleteItem(kind, id, env) {
  if (kind !== "config" && kind !== "shortcut") {
    return errorResponse(400, "kind 必须是 config 或 shortcut");
  }
  const table = kind === "config" ? "configs" : "shortcut_packs";
  const existing = await env.DB.prepare(`SELECT id FROM ${table} WHERE id = ?`).bind(id).first();
  if (!existing) {
    return errorResponse(404, "条目不存在");
  }

  await executeDeleteItemBatch(env.DB, kind, id);
  return jsonResponse({ ok: true, kind, id, deleted: true }, 200, { "Cache-Control": "no-store" });
}

async function handleAdminListAuthors(url, env) {
  const { page, pageSize, offset } = parsePagination(url);
  const where = [];
  const params = [];

  const q = (url.searchParams.get("q") || "").trim();
  if (q) {
    const pattern = `%${escapeLike(q)}%`;
    where.push(
      "(a.display_name LIKE ? ESCAPE '\\' OR a.normalized_name LIKE ? ESCAPE '\\' OR a.badge_label LIKE ? ESCAPE '\\' OR a.id LIKE ? ESCAPE '\\')"
    );
    params.push(pattern, pattern, pattern, pattern);
  }

  const official = (url.searchParams.get("official") || "").trim().toLowerCase();
  if (official === "1" || official === "true") {
    where.push("a.is_official = 1");
  } else if (official === "0" || official === "false") {
    where.push("a.is_official = 0");
  } else if (official && official !== "all") {
    return errorResponse(400, "official 参数仅支持 all / 1 / 0 / true / false", {}, { "Cache-Control": "no-store" });
  }

  const whereSql = where.length ? where.join(" AND ") : "1=1";

  const [countRes, listRes] = await env.DB.batch([
    env.DB.prepare(`SELECT COUNT(*) AS total FROM authors a WHERE ${whereSql}`).bind(...params),
    env.DB
      .prepare(
        `SELECT a.id, a.display_name, a.normalized_name, a.is_official, a.badge_label, a.created_at, a.updated_at,
                (SELECT COUNT(*) FROM configs c WHERE c.author_id = a.id) AS config_count,
                (SELECT COUNT(*) FROM shortcut_packs s WHERE s.author_id = a.id) AS shortcut_count
         FROM authors a
         WHERE ${whereSql}
         ORDER BY a.is_official DESC, a.updated_at DESC, a.created_at DESC
         LIMIT ? OFFSET ?`
      )
      .bind(...params, pageSize, offset),
  ]);

  const total = Number(countRes.results?.[0]?.total || 0);
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const items = (listRes.results || []).map(formatAdminAuthor);

  return jsonResponse(
    {
      ok: true,
      items,
      pagination: {
        page,
        pageSize,
        total,
        totalPages,
        hasMore: page < totalPages,
      },
    },
    200,
    { "Cache-Control": "no-store" }
  );
}

async function handleAdminPatchAuthor(id, request, env) {
  const existing = await env.DB
    .prepare("SELECT id, display_name, normalized_name, is_official, badge_label, created_at, updated_at FROM authors WHERE id = ?")
    .bind(id)
    .first();
  if (!existing) {
    return errorResponse(404, "作者不存在", {}, { "Cache-Control": "no-store" });
  }

  const body = await readLimitedJsonBody(request);
  const hasIsOfficial = Object.prototype.hasOwnProperty.call(body, "isOfficial");
  const hasBadgeLabel = Object.prototype.hasOwnProperty.call(body, "badgeLabel");
  const hasDisplayName = Object.prototype.hasOwnProperty.call(body, "displayName");

  if (!hasIsOfficial && !hasBadgeLabel && !hasDisplayName) {
    return errorResponse(400, "缺少可更新字段（isOfficial / badgeLabel / displayName）", {}, { "Cache-Control": "no-store" });
  }

  let nextIsOfficial = Number(existing.is_official);
  if (hasIsOfficial) {
    if (typeof body.isOfficial !== "boolean") {
      throw new ValidationError("isOfficial 必须是布尔值");
    }
    nextIsOfficial = body.isOfficial ? 1 : 0;
  }

  let nextBadgeLabel = existing.badge_label || "";
  if (hasBadgeLabel) {
    nextBadgeLabel = validateBadgeLabel(body.badgeLabel);
  }

  let nextDisplayName = existing.display_name;
  let nextNormalizedName = existing.normalized_name;
  if (hasDisplayName) {
    const parsed = validateAuthorDisplayName(body.displayName);
    const conflict = await env.DB
      .prepare("SELECT id FROM authors WHERE normalized_name = ? AND id != ? LIMIT 1")
      .bind(parsed.normalizedName, id)
      .first();
    if (conflict) {
      return errorResponse(409, "该作者昵称已被其他设备占用", {}, { "Cache-Control": "no-store" });
    }
    nextDisplayName = parsed.displayName;
    nextNormalizedName = parsed.normalizedName;
  }

  const now = Date.now();
  try {
    await env.DB
      .prepare(
        `UPDATE authors
         SET display_name = ?, normalized_name = ?, is_official = ?, badge_label = ?, updated_at = ?
         WHERE id = ?`
      )
      .bind(nextDisplayName, nextNormalizedName, nextIsOfficial, nextBadgeLabel, now, id)
      .run();
  } catch (err) {
    if (isUniqueConstraintError(err)) {
      return errorResponse(409, "该作者昵称已被其他设备占用", {}, { "Cache-Control": "no-store" });
    }
    throw err;
  }

  const updated = await env.DB
    .prepare(
      `SELECT a.id, a.display_name, a.normalized_name, a.is_official, a.badge_label, a.created_at, a.updated_at,
              (SELECT COUNT(*) FROM configs c WHERE c.author_id = a.id) AS config_count,
              (SELECT COUNT(*) FROM shortcut_packs s WHERE s.author_id = a.id) AS shortcut_count
       FROM authors a
       WHERE a.id = ?`
    )
    .bind(id)
    .first();

  return jsonResponse({ ok: true, author: formatAdminAuthor(updated) }, 200, { "Cache-Control": "no-store" });
}

async function handleAdminDeleteAuthor(id, env) {
  const existing = await env.DB
    .prepare("SELECT id, display_name FROM authors WHERE id = ?")
    .bind(id)
    .first();
  if (!existing) {
    return errorResponse(404, "作者不存在", {}, { "Cache-Control": "no-store" });
  }

  const now = Date.now();
  await env.DB.batch([
    env.DB
      .prepare("UPDATE configs SET author = ?, author_id = NULL, updated_at = ? WHERE author_id = ?")
      .bind(existing.display_name, now, id),
    env.DB
      .prepare("UPDATE shortcut_packs SET author = ?, author_id = NULL, updated_at = ? WHERE author_id = ?")
      .bind(existing.display_name, now, id),
    env.DB.prepare("DELETE FROM authors WHERE id = ?").bind(id),
  ]);

  return jsonResponse({ ok: true, id, deleted: true }, 200, { "Cache-Control": "no-store" });
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const pathname = url.pathname;
    const method = request.method.toUpperCase();

    if (method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders() });
    }

    try {
      if (method === "GET" && (pathname === "/admin" || pathname === "/admin/")) {
        return new Response(renderAdminHtml(), {
          status: 200,
          headers: {
            "Content-Type": "text/html; charset=utf-8",
            "Cache-Control": "no-store",
            "X-Content-Type-Options": "nosniff",
            "X-Frame-Options": "DENY",
            "Referrer-Policy": "no-referrer",
          },
        });
      }

      if (method === "GET" && pathname === "/api/health") {
        return await handleHealth(env);
      }

      if (pathname === "/api/author/me") {
        if (method === "GET") return await handleGetAuthorMe(request, env);
        if (method === "PUT") return await handlePutAuthorMe(request, env, ctx);
        return errorResponse(405, "Method Not Allowed");
      }

      if (pathname === "/api/configs") {
        if (method === "GET") return await handleListConfigs(url, env);
        if (method === "POST") return await handleCreateConfig(request, env, ctx);
        return errorResponse(405, "Method Not Allowed");
      }

      const configReportMatch = /^\/api\/configs\/([^/]+)\/report$/.exec(pathname);
      if (configReportMatch) {
        if (method === "POST") {
          return await handleReportItem("config", decodeURIComponent(configReportMatch[1]), request, env, ctx);
        }
        return errorResponse(405, "Method Not Allowed");
      }

      const configItemMatch = /^\/api\/configs\/([^/]+)$/.exec(pathname);
      if (configItemMatch) {
        const id = decodeURIComponent(configItemMatch[1]);
        if (method === "GET") return await handleGetConfigDetail(id, url, request, env, ctx);
        if (method === "DELETE") return await handleDeleteItem("config", id, request, env);
        return errorResponse(405, "Method Not Allowed");
      }

      if (pathname === "/api/shortcuts") {
        if (method === "GET") return await handleListShortcuts(url, env);
        if (method === "POST") return await handleCreateShortcutPack(request, env, ctx);
        return errorResponse(405, "Method Not Allowed");
      }

      const shortcutReportMatch = /^\/api\/shortcuts\/([^/]+)\/report$/.exec(pathname);
      if (shortcutReportMatch) {
        if (method === "POST") {
          return await handleReportItem("shortcut", decodeURIComponent(shortcutReportMatch[1]), request, env, ctx);
        }
        return errorResponse(405, "Method Not Allowed");
      }

      const shortcutItemMatch = /^\/api\/shortcuts\/([^/]+)$/.exec(pathname);
      if (shortcutItemMatch) {
        const id = decodeURIComponent(shortcutItemMatch[1]);
        if (method === "GET") return await handleGetShortcutDetail(id, url, request, env, ctx);
        if (method === "DELETE") return await handleDeleteItem("shortcut", id, request, env);
        return errorResponse(405, "Method Not Allowed");
      }

      if (pathname.startsWith("/api/admin/")) {
        const auth = await verifyAdmin(request, env);
        if (!auth.ok) {
          return errorResponse(auth.status, auth.error, {}, { "Cache-Control": "no-store" });
        }

        if (pathname === "/api/admin/items" && method === "GET") {
          return await handleAdminListItems(url, env);
        }

        const adminStatusMatch = /^\/api\/admin\/items\/(config|shortcut)\/([^/]+)\/status$/.exec(pathname);
        if (adminStatusMatch) {
          if (method === "POST") {
            return await handleAdminSetStatus(
              adminStatusMatch[1],
              decodeURIComponent(adminStatusMatch[2]),
              request,
              env
            );
          }
          return errorResponse(405, "Method Not Allowed");
        }

        const adminItemMatch = /^\/api\/admin\/items\/(config|shortcut)\/([^/]+)$/.exec(pathname);
        if (adminItemMatch) {
          const kind = adminItemMatch[1];
          const id = decodeURIComponent(adminItemMatch[2]);
          if (method === "GET") return await handleAdminGetItemDetail(kind, id, env);
          if (method === "DELETE") return await handleAdminDeleteItem(kind, id, env);
          return errorResponse(405, "Method Not Allowed");
        }

        if (pathname === "/api/admin/authors") {
          if (method === "GET") return await handleAdminListAuthors(url, env);
          return errorResponse(405, "Method Not Allowed");
        }

        const adminAuthorMatch = /^\/api\/admin\/authors\/([^/]+)$/.exec(pathname);
        if (adminAuthorMatch) {
          const id = decodeURIComponent(adminAuthorMatch[1]);
          if (method === "PATCH") return await handleAdminPatchAuthor(id, request, env);
          if (method === "DELETE") return await handleAdminDeleteAuthor(id, env);
          return errorResponse(405, "Method Not Allowed");
        }

        return errorResponse(404, "Admin API 路径不存在");
      }

      return errorResponse(404, "Not Found");
    } catch (err) {
      if (err instanceof ValidationError) {
        return errorResponse(err.status, err.message);
      }
      if (err instanceof ConflictError) {
        return errorResponse(err.status, err.message, err.extra || {});
      }
      if (err instanceof RateLimitError) {
        return errorResponse(
          429,
          err.message,
          { retryAfterSeconds: err.retryAfterSeconds },
          { "Retry-After": String(err.retryAfterSeconds) }
        );
      }
      console.error(
        JSON.stringify({
          message: "unhandled request error",
          method,
          path: pathname,
          error: err instanceof Error ? err.message : String(err),
        })
      );
      return errorResponse(500, "服务器内部错误");
    }
  },
};
