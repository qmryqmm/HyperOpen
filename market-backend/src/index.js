import { renderAdminHtml } from "./admin.js";
import {
  MAX_PAYLOAD_BYTES,
  ValidationError,
  compactRiskSummaryForMetadata,
  normalizeShortcuts,
  validateAndNormalizeConfig,
  validateUploadMetadata,
} from "./validator.js";

const REPORT_AUTO_HIDE_THRESHOLD = 5;
const UPLOAD_HOURLY_LIMIT = 5;
const UPLOAD_DAILY_LIMIT = 15;
const REPORT_HOURLY_LIMIT = 10;

class RateLimitError extends Error {
  constructor(message, retryAfterSeconds) {
    super(message);
    this.name = "RateLimitError";
    this.status = 429;
    this.retryAfterSeconds = Math.max(1, retryAfterSeconds || 60);
  }
}

function corsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, DELETE, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Manage-Token, If-None-Match",
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

function formatConfigRow(row) {
  return {
    id: row.id,
    name: row.name,
    description: row.description,
    author: row.author,
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
      ...formatConfigRow(row),
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

async function enforceRateLimit(db, ctx, category, ipHash) {
  const now = Date.now();
  const hourBucket = Math.floor(now / 3600000);
  const hrReset = (hourBucket + 1) * 3600000;

  if (category === "upload") {
    const dayBucket = Math.floor(now / 86400000);
    const dayReset = (dayBucket + 1) * 86400000;
    const hrKey = `upload:hr:${ipHash}:${hourBucket}`;
    const dayKey = `upload:day:${ipHash}:${dayBucket}`;

    const existing = await db
      .prepare("SELECT bucket_key, count, reset_at FROM rate_limits WHERE bucket_key IN (?, ?) AND reset_at > ?")
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

    const upsertSql = `
      INSERT INTO rate_limits (bucket_key, count, reset_at)
      VALUES (?, 1, ?)
      ON CONFLICT(bucket_key) DO UPDATE SET
        count = CASE WHEN rate_limits.reset_at <= ? THEN 1 ELSE rate_limits.count + 1 END,
        reset_at = CASE WHEN rate_limits.reset_at <= ? THEN excluded.reset_at ELSE rate_limits.reset_at END
    `;
    await db.batch([
      db.prepare(upsertSql).bind(hrKey, hrReset, now, now),
      db.prepare(upsertSql).bind(dayKey, dayReset, now, now),
    ]);
  } else if (category === "report") {
    const hrKey = `report:hr:${ipHash}:${hourBucket}`;
    const row = await db
      .prepare("SELECT count, reset_at FROM rate_limits WHERE bucket_key = ? AND reset_at > ?")
      .bind(hrKey, now)
      .first();

    if (row && Number(row.count) >= REPORT_HOURLY_LIMIT) {
      throw new RateLimitError(
        `举报过于频繁，每小时最多 ${REPORT_HOURLY_LIMIT} 次`,
        Math.ceil((hrReset - now) / 1000)
      );
    }

    await db
      .prepare(
        `INSERT INTO rate_limits (bucket_key, count, reset_at)
         VALUES (?, 1, ?)
         ON CONFLICT(bucket_key) DO UPDATE SET
           count = CASE WHEN rate_limits.reset_at <= ? THEN 1 ELSE rate_limits.count + 1 END,
           reset_at = CASE WHEN rate_limits.reset_at <= ? THEN excluded.reset_at ELSE rate_limits.reset_at END`
      )
      .bind(hrKey, hrReset, now, now)
      .run();
  }

  ctx.waitUntil(
    db
      .prepare("DELETE FROM rate_limits WHERE reset_at <= ?")
      .bind(now)
      .run()
      .catch(() => {})
  );
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

function etagMatches(headerVal, etag, sha256) {
  if (!headerVal) return false;
  const parts = headerVal.split(",");
  for (const part of parts) {
    const normalized = part.trim().replace(/^W\//, "");
    if (normalized === "*" || normalized === etag || normalized === sha256) {
      return true;
    }
  }
  return false;
}

async function handleHealth(env) {
  await env.DB.prepare("SELECT 1 AS ok").first();
  return jsonResponse({ ok: true, service: "hyperopen-market", timestamp: Date.now() });
}

async function handleListConfigs(url, env) {
  const { page, pageSize, offset } = parsePagination(url);
  const where = ["status = 'approved'"];
  const params = [];

  const q = (url.searchParams.get("q") || "").trim();
  if (q) {
    const pattern = `%${escapeLike(q)}%`;
    where.push("(name LIKE ? ESCAPE '\\' OR description LIKE ? ESCAPE '\\' OR author LIKE ? ESCAPE '\\' OR tags_json LIKE ? ESCAPE '\\')");
    params.push(pattern, pattern, pattern, pattern);
  }

  const layoutParam = url.searchParams.get("layout");
  if (layoutParam !== null && layoutParam !== "") {
    const layout = Number.parseInt(layoutParam, 10);
    if (!Number.isFinite(layout)) return errorResponse(400, "layout 参数无效");
    where.push("layout_style = ?");
    params.push(layout);
  }

  const materialParam = url.searchParams.get("material");
  if (materialParam !== null && materialParam !== "") {
    const material = Number.parseInt(materialParam, 10);
    if (!Number.isFinite(material)) return errorResponse(400, "material 参数无效");
    where.push("material_style = ?");
    params.push(material);
  }

  const hasShortcutsParam = url.searchParams.get("hasShortcuts");
  if (hasShortcutsParam === "1" || hasShortcutsParam === "true") {
    where.push("has_shortcuts = 1");
  } else if (hasShortcutsParam === "0" || hasShortcutsParam === "false") {
    where.push("has_shortcuts = 0");
  }

  const sort = url.searchParams.get("sort") === "downloads" ? "downloads DESC, created_at DESC" : "created_at DESC";
  const whereSql = where.join(" AND ");

  const [countRes, listRes] = await env.DB.batch([
    env.DB.prepare(`SELECT COUNT(*) AS total FROM configs WHERE ${whereSql}`).bind(...params),
    env.DB
      .prepare(
        `SELECT id, name, description, author, tags_json, layout_style, material_style, key_count,
                shortcut_count, has_shortcuts, custom_image_count, has_root_risk, risk_summary_json,
                review_reasons_json, min_app_version_code, app_version, downloads, reports_count,
                status, sha256, size_bytes, created_at, updated_at
         FROM configs
         WHERE ${whereSql}
         ORDER BY ${sort}
         LIMIT ? OFFSET ?`
      )
      .bind(...params, pageSize, offset),
  ]);

  const total = Number(countRes.results?.[0]?.total || 0);
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const items = (listRes.results || []).map(formatConfigRow);

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
      `SELECT c.id, c.name, c.description, c.author, c.tags_json, c.layout_style, c.material_style,
              c.key_count, c.shortcut_count, c.has_shortcuts, c.custom_image_count, c.has_root_risk,
              c.risk_summary_json, c.review_reasons_json, c.min_app_version_code, c.app_version,
              c.downloads, c.reports_count, c.status, c.sha256, c.size_bytes, c.created_at, c.updated_at,
              b.json_body
       FROM configs c
       JOIN config_bodies b ON b.id = c.id
       WHERE c.id = ? AND c.status = 'approved'`
    )
    .bind(id)
    .first();

  if (!row) {
    return errorResponse(404, "配置不存在或未公开");
  }

  const isDownload = url.searchParams.get("download") === "1";
  const etag = `"${row.sha256}"`;
  const cacheControl = isDownload ? "no-store" : "public, max-age=60";

  if (etagMatches(request.headers.get("If-None-Match"), etag, row.sha256)) {
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
      item: formatConfigRow(row),
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
  const body = await readLimitedJsonBody(request);
  const meta = validateUploadMetadata(body);
  const rawConfig = body.jsonBody !== undefined ? body.jsonBody : body.config;
  if (rawConfig === undefined) {
    throw new ValidationError("缺少 jsonBody 字段");
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

  const ipHash = await getClientIpHash(request);
  await enforceRateLimit(env.DB, ctx, "upload", ipHash);

  const id = crypto.randomUUID();
  const manageToken = generateManageToken();
  const manageTokenHash = await sha256Hex(manageToken);
  const now = Date.now();
  const appVersion = meta.appVersion || normalized.appVersion;
  const compactRisks = compactRiskSummaryForMetadata(normalized.riskSummary);

  await env.DB.batch([
    env.DB
      .prepare(
        `INSERT INTO configs (
          id, name, description, author, tags_json, layout_style, material_style, key_count,
          shortcut_count, has_shortcuts, custom_image_count, has_root_risk, risk_summary_json,
          review_reasons_json, min_app_version_code, app_version, downloads, reports_count,
          status, sha256, size_bytes, manage_token_hash, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?, ?, ?, ?, ?)`
      )
      .bind(
        id,
        meta.name,
        meta.description,
        meta.author,
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

  const createdRow = await env.DB
    .prepare(
      `SELECT id, name, description, author, tags_json, layout_style, material_style, key_count,
              shortcut_count, has_shortcuts, custom_image_count, has_root_risk, risk_summary_json,
              review_reasons_json, min_app_version_code, app_version, downloads, reports_count,
              status, sha256, size_bytes, created_at, updated_at
       FROM configs WHERE id = ?`
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

async function handleDeleteByManageToken(kind, id, request, env) {
  const table = kind === "config" ? "configs" : "shortcut_packs";
  const bodyTable = kind === "config" ? "config_bodies" : "shortcut_pack_bodies";
  const token = (request.headers.get("X-Manage-Token") || "").trim();
  if (!token) {
    return errorResponse(401, "缺少 X-Manage-Token 请求头");
  }

  const row = await env.DB.prepare(`SELECT id, manage_token_hash FROM ${table} WHERE id = ?`).bind(id).first();
  if (!row) {
    return errorResponse(404, "条目不存在");
  }

  const providedHash = await sha256Hex(token);
  const matched = await constantTimeEqual(providedHash, row.manage_token_hash);
  if (!matched) {
    return errorResponse(403, "X-Manage-Token 无效");
  }

  await env.DB.batch([
    env.DB.prepare("DELETE FROM reports WHERE kind = ? AND item_id = ?").bind(kind, id),
    env.DB.prepare(`DELETE FROM ${bodyTable} WHERE id = ?`).bind(id),
    env.DB.prepare(`DELETE FROM ${table} WHERE id = ?`).bind(id),
  ]);

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
  const where = ["status = 'approved'"];
  const params = [];

  const q = (url.searchParams.get("q") || "").trim();
  if (q) {
    const pattern = `%${escapeLike(q)}%`;
    where.push("(name LIKE ? ESCAPE '\\' OR description LIKE ? ESCAPE '\\' OR author LIKE ? ESCAPE '\\' OR tags_json LIKE ? ESCAPE '\\')");
    params.push(pattern, pattern, pattern, pattern);
  }

  const actionType = (url.searchParams.get("actionType") || "").trim().toUpperCase();
  if (actionType) {
    if (!/^[A-Z0-9_]{1,32}$/.test(actionType)) {
      return errorResponse(400, "actionType 参数无效");
    }
    where.push("action_types_json LIKE ? ESCAPE '\\'");
    params.push(`%"${escapeLike(actionType)}"%`);
  }

  const hasRoot = url.searchParams.get("hasRoot");
  if (hasRoot === "1" || hasRoot === "true") {
    where.push("has_root_risk = 1");
  } else if (hasRoot === "0" || hasRoot === "false") {
    where.push("has_root_risk = 0");
  }

  const hasImages = url.searchParams.get("hasImages");
  if (hasImages === "1" || hasImages === "true") {
    where.push("custom_image_count > 0");
  } else if (hasImages === "0" || hasImages === "false") {
    where.push("custom_image_count = 0");
  }

  const sort = url.searchParams.get("sort") === "downloads" ? "downloads DESC, created_at DESC" : "created_at DESC";
  const whereSql = where.join(" AND ");

  const [countRes, listRes] = await env.DB.batch([
    env.DB.prepare(`SELECT COUNT(*) AS total FROM shortcut_packs WHERE ${whereSql}`).bind(...params),
    env.DB
      .prepare(
        `SELECT id, name, description, author, tags_json, shortcut_count, folder_count, action_types_json,
                custom_image_count, has_root_risk, risk_summary_json, review_reasons_json,
                min_app_version_code, app_version, downloads, reports_count, status, sha256,
                size_bytes, created_at, updated_at
         FROM shortcut_packs
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
      `SELECT s.id, s.name, s.description, s.author, s.tags_json, s.shortcut_count, s.folder_count,
              s.action_types_json, s.custom_image_count, s.has_root_risk, s.risk_summary_json,
              s.review_reasons_json, s.min_app_version_code, s.app_version, s.downloads,
              s.reports_count, s.status, s.sha256, s.size_bytes, s.created_at, s.updated_at,
              b.shortcuts_json
       FROM shortcut_packs s
       JOIN shortcut_pack_bodies b ON b.id = s.id
       WHERE s.id = ? AND s.status = 'approved'`
    )
    .bind(id)
    .first();

  if (!row) {
    return errorResponse(404, "快捷包不存在或未公开");
  }

  const isDownload = url.searchParams.get("download") === "1";
  const etag = `"${row.sha256}"`;
  const cacheControl = isDownload ? "no-store" : "public, max-age=60";

  if (etagMatches(request.headers.get("If-None-Match"), etag, row.sha256)) {
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
      item: formatShortcutRow(row),
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
  const body = await readLimitedJsonBody(request);
  const meta = validateUploadMetadata(body);

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

  const ipHash = await getClientIpHash(request);
  await enforceRateLimit(env.DB, ctx, "upload", ipHash);

  const id = crypto.randomUUID();
  const manageToken = generateManageToken();
  const manageTokenHash = await sha256Hex(manageToken);
  const now = Date.now();
  const reviewReasons = [];
  if (normalized.hasRootRisk) reviewReasons.push("root_action");
  if (normalized.customImageCount > 0) reviewReasons.push("custom_image");
  const status = reviewReasons.length > 0 ? "pending" : "approved";
  const compactRisks = compactRiskSummaryForMetadata(normalized.riskSummary);

  await env.DB.batch([
    env.DB
      .prepare(
        `INSERT INTO shortcut_packs (
          id, name, description, author, tags_json, shortcut_count, folder_count, action_types_json,
          custom_image_count, has_root_risk, risk_summary_json, review_reasons_json,
          min_app_version_code, app_version, downloads, reports_count, status, sha256,
          size_bytes, manage_token_hash, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?, ?, ?, ?, ?)`
      )
      .bind(
        id,
        meta.name,
        meta.description,
        meta.author,
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

  const createdRow = await env.DB
    .prepare(
      `SELECT id, name, description, author, tags_json, shortcut_count, folder_count, action_types_json,
              custom_image_count, has_root_risk, risk_summary_json, review_reasons_json,
              min_app_version_code, app_version, downloads, reports_count, status, sha256,
              size_bytes, created_at, updated_at
       FROM shortcut_packs WHERE id = ?`
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

  function buildBranchFilter(alias) {
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
        `(${alias}name LIKE ? ESCAPE '\\' OR ${alias}description LIKE ? ESCAPE '\\' OR ${alias}author LIKE ? ESCAPE '\\' OR ${alias}tags_json LIKE ? ESCAPE '\\')`
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
    const f = buildBranchFilter("");
    branches.push(
      `SELECT 'config' AS kind, id, name, description, author, tags_json, layout_style, material_style,
              key_count, shortcut_count, has_shortcuts, NULL AS folder_count, NULL AS action_types_json,
              custom_image_count, has_root_risk, risk_summary_json, review_reasons_json,
              min_app_version_code, app_version, downloads, reports_count, status, sha256,
              size_bytes, created_at, updated_at
       FROM configs
       WHERE ${f.whereSql}`
    );
    allParams.push(...f.params);
  }

  if (kind === "all" || kind === "shortcut") {
    const f = buildBranchFilter("");
    branches.push(
      `SELECT 'shortcut' AS kind, id, name, description, author, tags_json, NULL AS layout_style, NULL AS material_style,
              NULL AS key_count, shortcut_count, NULL AS has_shortcuts, folder_count, action_types_json,
              custom_image_count, has_root_risk, risk_summary_json, review_reasons_json,
              min_app_version_code, app_version, downloads, reports_count, status, sha256,
              size_bytes, created_at, updated_at
       FROM shortcut_packs
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
        `SELECT c.id, c.name, c.description, c.author, c.tags_json, c.layout_style, c.material_style,
                c.key_count, c.shortcut_count, c.has_shortcuts, c.custom_image_count, c.has_root_risk,
                c.risk_summary_json, c.review_reasons_json, c.min_app_version_code, c.app_version,
                c.downloads, c.reports_count, c.status, c.sha256, c.size_bytes, c.created_at, c.updated_at,
                b.json_body
         FROM configs c
         JOIN config_bodies b ON b.id = c.id
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
        `SELECT s.id, s.name, s.description, s.author, s.tags_json, s.shortcut_count, s.folder_count,
                s.action_types_json, s.custom_image_count, s.has_root_risk, s.risk_summary_json,
                s.review_reasons_json, s.min_app_version_code, s.app_version, s.downloads,
                s.reports_count, s.status, s.sha256, s.size_bytes, s.created_at, s.updated_at,
                b.shortcuts_json
         FROM shortcut_packs s
         JOIN shortcut_pack_bodies b ON b.id = s.id
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
      item: kind === "config" ? formatConfigRow(row) : formatShortcutRow(row),
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
  const bodyTable = kind === "config" ? "config_bodies" : "shortcut_pack_bodies";

  const existing = await env.DB.prepare(`SELECT id FROM ${table} WHERE id = ?`).bind(id).first();
  if (!existing) {
    return errorResponse(404, "条目不存在");
  }

  await env.DB.batch([
    env.DB.prepare("DELETE FROM reports WHERE kind = ? AND item_id = ?").bind(kind, id),
    env.DB.prepare(`DELETE FROM ${bodyTable} WHERE id = ?`).bind(id),
    env.DB.prepare(`DELETE FROM ${table} WHERE id = ?`).bind(id),
  ]);

  return jsonResponse({ ok: true, kind, id, deleted: true }, 200, { "Cache-Control": "no-store" });
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
        if (method === "DELETE") return await handleDeleteByManageToken("config", id, request, env);
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
        if (method === "DELETE") return await handleDeleteByManageToken("shortcut", id, request, env);
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

        return errorResponse(404, "Admin API 路径不存在");
      }

      return errorResponse(404, "Not Found");
    } catch (err) {
      if (err instanceof ValidationError) {
        return errorResponse(err.status, err.message);
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
