export function renderAdminHtml() {
  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>HyperOpen Market 审核台</title>
  <style>
    :root {
      color-scheme: light dark;
      --bg: #f5f7fb;
      --card: #ffffff;
      --card-2: #f0f3f9;
      --text: #16181d;
      --muted: #5c6578;
      --border: #dfe4ee;
      --primary: #1a73e8;
      --primary-fg: #ffffff;
      --danger: #d93025;
      --warn: #e37400;
      --ok: #188038;
      --warn-bg: rgba(227, 116, 0, 0.12);
      --danger-bg: rgba(217, 48, 37, 0.12);
      --ok-bg: rgba(24, 128, 56, 0.12);
      --info-bg: rgba(26, 115, 232, 0.12);
    }
    @media (prefers-color-scheme: dark) {
      :root {
        --bg: #0e1116;
        --card: #161b22;
        --card-2: #1f2630;
        --text: #e6edf3;
        --muted: #939ead;
        --border: #2b3441;
        --primary: #4c9aff;
        --primary-fg: #0b1220;
        --danger: #f87171;
        --warn: #fbbf24;
        --ok: #34d399;
        --warn-bg: rgba(251, 191, 36, 0.14);
        --danger-bg: rgba(248, 113, 113, 0.14);
        --ok-bg: rgba(52, 211, 153, 0.14);
        --info-bg: rgba(76, 154, 255, 0.14);
      }
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "PingFang SC", "Microsoft YaHei", sans-serif;
      background: var(--bg);
      color: var(--text);
      line-height: 1.5;
    }
    .container { max-width: 1120px; margin: 0 auto; padding: 20px 16px 48px; }
    .topbar {
      display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between;
      gap: 12px; margin-bottom: 18px;
    }
    .brand { font-size: 20px; font-weight: 700; display: flex; align-items: center; gap: 10px; }
    .badge {
      display: inline-flex; align-items: center; padding: 2px 8px; border-radius: 999px;
      font-size: 12px; font-weight: 600; background: var(--info-bg); color: var(--primary);
    }
    .badge.ok { background: var(--ok-bg); color: var(--ok); }
    .badge.warn { background: var(--warn-bg); color: var(--warn); }
    .badge.danger { background: var(--danger-bg); color: var(--danger); }
    .card {
      background: var(--card); border: 1px solid var(--border); border-radius: 14px;
      padding: 16px;
    }
    .login-box { max-width: 420px; margin: 80px auto; }
    input[type="text"], input[type="password"], input[type="search"] {
      width: 100%; padding: 10px 12px; border-radius: 10px; border: 1px solid var(--border);
      background: var(--card-2); color: var(--text); font-size: 14px; outline: none;
    }
    input:focus { border-color: var(--primary); }
    button {
      border: 1px solid var(--border); background: var(--card-2); color: var(--text);
      padding: 8px 14px; border-radius: 10px; font-size: 13px; font-weight: 600;
      cursor: pointer; transition: opacity .15s ease;
    }
    button:hover { opacity: 0.88; }
    button:disabled { opacity: 0.45; cursor: not-allowed; }
    button.primary { background: var(--primary); color: var(--primary-fg); border-color: transparent; }
    button.ok { background: var(--ok); color: #fff; border-color: transparent; }
    button.warn { background: var(--warn); color: #111; border-color: transparent; }
    button.danger { background: var(--danger); color: #fff; border-color: transparent; }
    .tabs { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; }
    .tab {
      padding: 7px 14px; border-radius: 999px; border: 1px solid var(--border);
      background: var(--card); color: var(--muted); font-size: 13px; font-weight: 600; cursor: pointer;
    }
    .tab.active { background: var(--primary); color: var(--primary-fg); border-color: transparent; }
    .toolbar { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 16px; align-items: center; }
    .toolbar input { flex: 1; min-width: 200px; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 12px; }
    .item-card {
      background: var(--card); border: 1px solid var(--border); border-radius: 14px;
      padding: 14px; display: flex; flex-direction: column; gap: 8px; cursor: pointer;
    }
    .item-card:hover { border-color: var(--primary); }
    .row-between { display: flex; justify-content: space-between; align-items: flex-start; gap: 8px; }
    .title { font-weight: 700; font-size: 15px; word-break: break-word; }
    .muted { color: var(--muted); font-size: 12px; }
    .desc { font-size: 13px; color: var(--muted); display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
    .tags { display: flex; flex-wrap: wrap; gap: 6px; }
    .pagination { display: flex; justify-content: center; align-items: center; gap: 12px; margin-top: 20px; }
    .modal-backdrop {
      position: fixed; inset: 0; background: rgba(0, 0, 0, 0.56);
      display: none; align-items: center; justify-content: center; padding: 16px; z-index: 50;
    }
    .modal-backdrop.open { display: flex; }
    .modal {
      width: min(860px, 100%); max-height: 90vh; overflow-y: auto;
      background: var(--card); border: 1px solid var(--border); border-radius: 16px; padding: 20px;
      display: flex; flex-direction: column; gap: 16px;
    }
    .section-title { font-size: 14px; font-weight: 700; margin: 0 0 8px; }
    .img-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 10px; }
    .img-card {
      background: var(--card-2); border: 1px solid var(--border); border-radius: 12px;
      padding: 10px; text-align: center; display: flex; flex-direction: column; gap: 6px; align-items: center;
    }
    .img-card img {
      width: 64px; height: 64px; object-fit: contain; border-radius: 12px;
      background: repeating-conic-gradient(#8882 0% 25%, transparent 0% 50%) 50% / 12px 12px;
    }
    .risk-item {
      background: var(--danger-bg); border: 1px solid var(--danger); border-radius: 12px;
      padding: 12px; margin-bottom: 8px;
    }
    pre {
      margin: 6px 0 0; padding: 10px; border-radius: 8px; background: var(--card-2);
      border: 1px solid var(--border); overflow-x: auto; font-size: 12px; white-space: pre-wrap; word-break: break-all;
    }
    .actions { display: flex; flex-wrap: wrap; gap: 8px; justify-content: flex-end; }
    .error-msg { color: var(--danger); font-size: 13px; margin-top: 8px; }
    .empty { text-align: center; padding: 48px 16px; color: var(--muted); }
  </style>
</head>
<body>
  <div class="container">
    <div id="loginView" class="card login-box" style="display:none">
      <h2 style="margin:0 0 6px">HyperOpen Market 审核台</h2>
      <p class="muted" style="margin:0 0 14px">请输入管理员 ADMIN_TOKEN 进行身份验证（仅保存在当前标签页 sessionStorage）。</p>
      <form id="loginForm">
        <input id="tokenInput" type="password" placeholder="ADMIN_TOKEN" autocomplete="off" required />
        <div id="loginError" class="error-msg"></div>
        <div style="margin-top:12px; display:flex; justify-content:flex-end;">
          <button type="submit" class="primary">登录审核台</button>
        </div>
      </form>
    </div>

    <div id="appView" style="display:none">
      <div class="topbar">
        <div class="brand">
          <span>HyperOpen Market 审核台</span>
          <span class="badge" id="totalBadge">0 条</span>
        </div>
        <button id="logoutBtn">退出登录</button>
      </div>

      <div class="tabs" id="tabBar">
        <button class="tab active" data-kind="config" data-status="pending">待审配置</button>
        <button class="tab" data-kind="shortcut" data-status="pending">待审快捷包</button>
        <button class="tab" data-kind="all" data-status="approved">已上架</button>
        <button class="tab" data-kind="all" data-status="hidden">已隐藏</button>
        <button class="tab" data-kind="all" data-status="reported">被举报</button>
        <button class="tab" data-kind="all" data-status="all">全部条目</button>
      </div>

      <div class="toolbar">
        <input id="searchInput" type="search" placeholder="搜索名称、作者、标签或简介..." />
        <button id="searchBtn" class="primary">搜索</button>
        <button id="refreshBtn">刷新</button>
      </div>

      <div id="listError" class="error-msg"></div>
      <div id="itemGrid" class="grid"></div>
      <div id="emptyState" class="empty" style="display:none">当前筛选条件下暂无条目</div>

      <div class="pagination">
        <button id="prevBtn">上一页</button>
        <span id="pageInfo" class="muted">第 1 / 1 页</span>
        <button id="nextBtn">下一页</button>
      </div>
    </div>
  </div>

  <div id="detailBackdrop" class="modal-backdrop">
    <div class="modal">
      <div class="row-between">
        <div>
          <div id="detailTitle" class="title" style="font-size:18px"></div>
          <div id="detailMeta" class="muted"></div>
        </div>
        <button id="closeModalBtn">关闭</button>
      </div>
      <div id="detailBadges" class="tags"></div>
      <div id="detailDesc" style="font-size:14px"></div>
      <div id="detailImagesSection" style="display:none">
        <h4 class="section-title">自定义 base64 图标预览</h4>
        <div id="detailImagesGrid" class="img-grid"></div>
      </div>
      <div id="detailRiskSection" style="display:none">
        <h4 class="section-title">高危动作与载荷明文审查</h4>
        <div id="detailRiskList"></div>
      </div>
      <div id="detailReportsSection" style="display:none">
        <h4 class="section-title">用户举报记录</h4>
        <div id="detailReportsList"></div>
      </div>
      <details>
        <summary style="cursor:pointer; font-size:13px; font-weight:600">查看完整规范化 JSON 正文</summary>
        <pre id="detailRawBody"></pre>
      </details>
      <div id="detailError" class="error-msg"></div>
      <div class="actions">
        <button id="btnApprove" class="ok">通过上架</button>
        <button id="btnPending" class="warn">退回待审</button>
        <button id="btnHide">隐藏下架</button>
        <button id="btnDelete" class="danger">永久删除</button>
      </div>
    </div>
  </div>

  <script>
    const STORAGE_KEY = "hyperopen_admin_token";
    const state = {
      token: sessionStorage.getItem(STORAGE_KEY) || "",
      kind: "config",
      status: "pending",
      q: "",
      page: 1,
      pageSize: 20,
      totalPages: 1,
      currentDetail: null,
    };

    const $ = (id) => document.getElementById(id);

    function el(tag, className, text) {
      const node = document.createElement(tag);
      if (className) node.className = className;
      if (text !== undefined && text !== null) node.textContent = String(text);
      return node;
    }

    function formatTime(ms) {
      if (!ms) return "-";
      return new Date(ms).toLocaleString("zh-CN", { hour12: false });
    }

    function statusBadge(status) {
      if (status === "approved") return el("span", "badge ok", "已上架");
      if (status === "hidden") return el("span", "badge danger", "已隐藏");
      return el("span", "badge warn", "待审核");
    }

    async function apiFetch(path, options = {}) {
      const headers = new Headers(options.headers || {});
      headers.set("Authorization", "Bearer " + state.token);
      if (options.body && !headers.has("Content-Type")) {
        headers.set("Content-Type", "application/json");
      }
      const res = await fetch(path, { ...options, headers });
      const data = await res.json().catch(() => ({}));
      if (res.status === 401) {
        logout("管理员凭据无效或已过期，请重新登录");
        throw new Error("Unauthorized");
      }
      if (!res.ok || data.ok === false) {
        throw new Error(data.error || ("HTTP " + res.status));
      }
      return data;
    }

    function showLogin(msg = "") {
      $("loginView").style.display = "block";
      $("appView").style.display = "none";
      $("loginError").textContent = msg;
    }

    function showApp() {
      $("loginView").style.display = "none";
      $("appView").style.display = "block";
    }

    function logout(msg = "") {
      state.token = "";
      sessionStorage.removeItem(STORAGE_KEY);
      closeModal();
      showLogin(msg);
    }

    async function loadItems() {
      $("listError").textContent = "";
      const params = new URLSearchParams({
        kind: state.kind,
        status: state.status,
        page: String(state.page),
        pageSize: String(state.pageSize),
      });
      if (state.q.trim()) params.set("q", state.q.trim());
      try {
        const data = await apiFetch("/api/admin/items?" + params.toString());
        renderItems(data.items || [], data.pagination || { page: 1, totalPages: 1, total: 0 });
      } catch (err) {
        if (err.message !== "Unauthorized") {
          $("listError").textContent = "加载失败: " + err.message;
        }
      }
    }

    function renderItems(items, pag) {
      const grid = $("itemGrid");
      grid.innerHTML = "";
      $("totalBadge").textContent = (pag.total ?? items.length) + " 条";
      state.totalPages = Math.max(1, pag.totalPages || 1);
      $("pageInfo").textContent = "第 " + (pag.page || 1) + " / " + state.totalPages + " 页";
      $("prevBtn").disabled = (pag.page || 1) <= 1;
      $("nextBtn").disabled = (pag.page || 1) >= state.totalPages;
      $("emptyState").style.display = items.length === 0 ? "block" : "none";

      for (const item of items) {
        const card = el("div", "item-card");
        card.addEventListener("click", () => openDetail(item.kind, item.id));

        const top = el("div", "row-between");
        top.appendChild(el("div", "title", item.name));
        const rightBadges = el("div", "tags");
        rightBadges.appendChild(el("span", "badge", item.kind === "config" ? "配置" : "快捷包"));
        rightBadges.appendChild(statusBadge(item.status));
        top.appendChild(rightBadges);
        card.appendChild(top);

        card.appendChild(el("div", "muted", "作者: " + item.author + " · 下载: " + item.downloads + " · " + formatTime(item.createdAt)));
        if (item.description) {
          card.appendChild(el("div", "desc", item.description));
        }

        const flags = el("div", "tags");
        if (item.hasRootRisk) flags.appendChild(el("span", "badge danger", "高危动作"));
        if (item.customImageCount > 0) flags.appendChild(el("span", "badge warn", "自定义图 " + item.customImageCount + " 张"));
        if (item.reportsCount > 0) flags.appendChild(el("span", "badge danger", "举报 " + item.reportsCount));
        if (item.kind === "config") {
          flags.appendChild(el("span", "badge", item.keyCount + " 键"));
          if (item.hasShortcuts) flags.appendChild(el("span", "badge", "含 " + item.shortcutCount + " 快捷项"));
        } else {
          flags.appendChild(el("span", "badge", item.shortcutCount + " 项 / " + item.folderCount + " 文件夹"));
        }
        card.appendChild(flags);
        grid.appendChild(card);
      }
    }

    async function openDetail(kind, id) {
      $("detailError").textContent = "";
      $("detailBackdrop").classList.add("open");
      $("detailTitle").textContent = "加载中...";
      $("detailImagesSection").style.display = "none";
      $("detailRiskSection").style.display = "none";
      $("detailReportsSection").style.display = "none";
      $("detailRawBody").textContent = "";

      try {
        const data = await apiFetch("/api/admin/items/" + encodeURIComponent(kind) + "/" + encodeURIComponent(id));
        const item = data.item;
        state.currentDetail = { kind, id };
        $("detailTitle").textContent = item.name;
        $("detailMeta").textContent =
          (kind === "config" ? "配置" : "快捷包") +
          " · ID: " + item.id +
          " · 作者: " + item.author +
          " · 大小: " + item.sizeBytes + " B" +
          " · SHA256: " + item.sha256.slice(0, 12) + "..." +
          " · 创建: " + formatTime(item.createdAt);

        const badges = $("detailBadges");
        badges.innerHTML = "";
        badges.appendChild(statusBadge(item.status));
        for (const r of (item.reviewReasons || [])) {
          badges.appendChild(el("span", "badge warn", "触发审核: " + r));
        }
        for (const t of (item.tags || [])) {
          badges.appendChild(el("span", "badge", "#" + t));
        }
        $("detailDesc").textContent = item.description || "（无简介）";

        const imgs = data.extractedImages || [];
        if (imgs.length > 0) {
          const grid = $("detailImagesGrid");
          grid.innerHTML = "";
          for (const img of imgs) {
            const box = el("div", "img-card");
            const imageEl = document.createElement("img");
            imageEl.src = img.dataUri;
            imageEl.alt = img.path;
            box.appendChild(imageEl);
            box.appendChild(el("div", "muted", img.path + " (" + img.field + ")"));
            box.appendChild(el("div", "muted", img.mimeType + " · " + Math.round((img.sizeBytes / 1024) * 10) / 10 + " KB"));
            grid.appendChild(box);
          }
          $("detailImagesSection").style.display = "block";
        }

        const risks = data.riskSummary || [];
        if (risks.length > 0) {
          const list = $("detailRiskList");
          list.innerHTML = "";
          for (const r of risks) {
            const box = el("div", "risk-item");
            const head = el("div", "row-between");
            head.appendChild(el("strong", "", r.path + " [" + r.actionType + "]"));
            head.appendChild(el("span", "badge danger", r.reason));
            box.appendChild(head);
            box.appendChild(el("div", "muted", "shellUseRoot=" + Boolean(r.shellUseRoot) + ", userId=" + (r.userId || 0)));
            const pre = el("pre", "", r.payload || "(空载荷)");
            box.appendChild(pre);
            list.appendChild(box);
          }
          $("detailRiskSection").style.display = "block";
        }

        const reports = data.reports || [];
        if (reports.length > 0) {
          const rList = $("detailReportsList");
          rList.innerHTML = "";
          for (const rep of reports) {
            const row = el("div", "card");
            row.style.marginBottom = "6px";
            row.appendChild(el("div", "muted", formatTime(rep.createdAt) + " · IP#" + rep.ipHashPrefix));
            row.appendChild(el("div", "", rep.reason || "（未填写理由）"));
            rList.appendChild(row);
          }
          $("detailReportsSection").style.display = "block";
        }

        try {
          $("detailRawBody").textContent = JSON.stringify(JSON.parse(data.rawBody), null, 2);
        } catch {
          $("detailRawBody").textContent = data.rawBody || "";
        }
      } catch (err) {
        $("detailError").textContent = "加载详情失败: " + err.message;
      }
    }

    function closeModal() {
      $("detailBackdrop").classList.remove("open");
      state.currentDetail = null;
    }

    async function updateStatus(status) {
      if (!state.currentDetail) return;
      const { kind, id } = state.currentDetail;
      $("detailError").textContent = "";
      try {
        await apiFetch("/api/admin/items/" + encodeURIComponent(kind) + "/" + encodeURIComponent(id) + "/status", {
          method: "POST",
          body: JSON.stringify({ status }),
        });
        closeModal();
        await loadItems();
      } catch (err) {
        $("detailError").textContent = "更新状态失败: " + err.message;
      }
    }

    async function deleteCurrentItem() {
      if (!state.currentDetail) return;
      if (!confirm("确定永久删除该条目及其正文与举报记录？此操作不可恢复。")) return;
      const { kind, id } = state.currentDetail;
      $("detailError").textContent = "";
      try {
        await apiFetch("/api/admin/items/" + encodeURIComponent(kind) + "/" + encodeURIComponent(id), {
          method: "DELETE",
        });
        closeModal();
        await loadItems();
      } catch (err) {
        $("detailError").textContent = "删除失败: " + err.message;
      }
    }

    $("loginForm").addEventListener("submit", async (e) => {
      e.preventDefault();
      const token = $("tokenInput").value.trim();
      if (!token) return;
      state.token = token;
      $("loginError").textContent = "";
      try {
        await apiFetch("/api/admin/items?pageSize=1");
        sessionStorage.setItem(STORAGE_KEY, token);
        $("tokenInput").value = "";
        showApp();
        await loadItems();
      } catch (err) {
        state.token = "";
        sessionStorage.removeItem(STORAGE_KEY);
        $("loginError").textContent = err.message === "Unauthorized" ? "ADMIN_TOKEN 不正确" : err.message;
      }
    });

    $("logoutBtn").addEventListener("click", () => logout());
    $("closeModalBtn").addEventListener("click", closeModal);
    $("detailBackdrop").addEventListener("click", (e) => {
      if (e.target === $("detailBackdrop")) closeModal();
    });
    $("btnApprove").addEventListener("click", () => updateStatus("approved"));
    $("btnPending").addEventListener("click", () => updateStatus("pending"));
    $("btnHide").addEventListener("click", () => updateStatus("hidden"));
    $("btnDelete").addEventListener("click", deleteCurrentItem);

    $("tabBar").addEventListener("click", (e) => {
      const btn = e.target.closest(".tab");
      if (!btn) return;
      for (const node of $("tabBar").querySelectorAll(".tab")) node.classList.remove("active");
      btn.classList.add("active");
      state.kind = btn.dataset.kind;
      state.status = btn.dataset.status;
      state.page = 1;
      loadItems();
    });

    $("searchBtn").addEventListener("click", () => {
      state.q = $("searchInput").value;
      state.page = 1;
      loadItems();
    });
    $("searchInput").addEventListener("keydown", (e) => {
      if (e.key === "Enter") {
        state.q = $("searchInput").value;
        state.page = 1;
        loadItems();
      }
    });
    $("refreshBtn").addEventListener("click", loadItems);
    $("prevBtn").addEventListener("click", () => {
      if (state.page > 1) {
        state.page -= 1;
        loadItems();
      }
    });
    $("nextBtn").addEventListener("click", () => {
      if (state.page < state.totalPages) {
        state.page += 1;
        loadItems();
      }
    });

    if (state.token) {
      showApp();
      loadItems();
    } else {
      showLogin();
    }
  </script>
</body>
</html>`;
}
