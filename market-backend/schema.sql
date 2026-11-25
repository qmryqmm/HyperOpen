-- HyperOpen Market D1 Schema (SQLite)

CREATE TABLE IF NOT EXISTS authors (
  id TEXT PRIMARY KEY,
  secret_hash TEXT NOT NULL UNIQUE,
  display_name TEXT NOT NULL,
  normalized_name TEXT NOT NULL UNIQUE,
  is_official INTEGER NOT NULL DEFAULT 0,
  badge_label TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS shortcut_packs (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  author TEXT NOT NULL,
  author_id TEXT REFERENCES authors(id) ON DELETE SET NULL,
  tags_json TEXT NOT NULL DEFAULT '[]',
  shortcut_count INTEGER NOT NULL DEFAULT 0,
  folder_count INTEGER NOT NULL DEFAULT 0,
  action_types_json TEXT NOT NULL DEFAULT '[]',
  custom_image_count INTEGER NOT NULL DEFAULT 0,
  has_root_risk INTEGER NOT NULL DEFAULT 0,
  risk_summary_json TEXT NOT NULL DEFAULT '[]',
  review_reasons_json TEXT NOT NULL DEFAULT '[]',
  min_app_version_code INTEGER NOT NULL DEFAULT 0,
  app_version TEXT NOT NULL DEFAULT '',
  downloads INTEGER NOT NULL DEFAULT 0,
  reports_count INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL CHECK (status IN ('pending', 'approved', 'hidden')),
  sha256 TEXT NOT NULL,
  size_bytes INTEGER NOT NULL DEFAULT 0,
  manage_token_hash TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS shortcut_pack_bodies (
  id TEXT PRIMARY KEY REFERENCES shortcut_packs(id) ON DELETE CASCADE,
  shortcuts_json TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS configs (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  author TEXT NOT NULL,
  author_id TEXT REFERENCES authors(id) ON DELETE SET NULL,
  linked_shortcut_pack_id TEXT DEFAULT NULL REFERENCES shortcut_packs(id) ON DELETE SET NULL,
  tags_json TEXT NOT NULL DEFAULT '[]',
  layout_style INTEGER NOT NULL DEFAULT 0,
  material_style INTEGER NOT NULL DEFAULT 2,
  key_count INTEGER NOT NULL DEFAULT 0,
  shortcut_count INTEGER NOT NULL DEFAULT 0,
  has_shortcuts INTEGER NOT NULL DEFAULT 0,
  custom_image_count INTEGER NOT NULL DEFAULT 0,
  has_root_risk INTEGER NOT NULL DEFAULT 0,
  risk_summary_json TEXT NOT NULL DEFAULT '[]',
  review_reasons_json TEXT NOT NULL DEFAULT '[]',
  min_app_version_code INTEGER NOT NULL DEFAULT 0,
  app_version TEXT NOT NULL DEFAULT '',
  downloads INTEGER NOT NULL DEFAULT 0,
  reports_count INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL CHECK (status IN ('pending', 'approved', 'hidden')),
  sha256 TEXT NOT NULL,
  size_bytes INTEGER NOT NULL DEFAULT 0,
  manage_token_hash TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS config_bodies (
  id TEXT PRIMARY KEY REFERENCES configs(id) ON DELETE CASCADE,
  json_body TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS reports (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  kind TEXT NOT NULL CHECK (kind IN ('config', 'shortcut')),
  item_id TEXT NOT NULL,
  ip_hash TEXT NOT NULL,
  reason TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL,
  UNIQUE(kind, item_id, ip_hash)
);

CREATE TABLE IF NOT EXISTS rate_limits (
  bucket_key TEXT PRIMARY KEY,
  count INTEGER NOT NULL,
  reset_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_authors_official ON authors(is_official DESC, updated_at DESC, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_configs_status_created ON configs(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_configs_status_downloads ON configs(status, downloads DESC, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_configs_sha256 ON configs(sha256);
CREATE INDEX IF NOT EXISTS idx_configs_author_id ON configs(author_id);
CREATE INDEX IF NOT EXISTS idx_configs_linked_pack ON configs(linked_shortcut_pack_id);

CREATE INDEX IF NOT EXISTS idx_shortcuts_status_created ON shortcut_packs(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_shortcuts_status_downloads ON shortcut_packs(status, downloads DESC, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_shortcuts_sha256 ON shortcut_packs(sha256);
CREATE INDEX IF NOT EXISTS idx_shortcuts_author_id ON shortcut_packs(author_id);

CREATE INDEX IF NOT EXISTS idx_reports_item ON reports(kind, item_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_rate_limits_reset ON rate_limits(reset_at);
