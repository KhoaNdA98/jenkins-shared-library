#!/usr/bin/env node
/**
 * Tải file .dmg macOS mới nhất (đã ký+notarize+staple sẵn) từ GitHub Release
 * của clickai-mcp-filesystem về, copy vào backend/public/download/ — nối
 * tiếp đúng chỗ Windows/Linux đang làm (Docker COPY backend ./backend ở
 * stage 'Build Image' ngay sau sẽ tự đóng gói theo, không cần sửa gì thêm).
 *
 * Vì sao KHÔNG tự build .dmg ở đây: hdiutil (tạo file UDIF chuẩn Apple) chỉ
 * có trên macOS thật — job "Build Agent Binaries" chạy trong container
 * node:22-slim (Linux, Docker-outside-of-Docker, xem ghi chú đầu stage) nên
 * KHÔNG THỂ tự build .dmg được, dù cross-build được Win/Linux/backend macOS.
 * File .dmg thật được build+ký+notarize+staple ở nơi khác — GitHub Actions
 * job "macos-dmg" (repo clickai-mcp-filesystem, .github/workflows/ci.yml,
 * chạy trên runner macos-latest — máy Apple thật do GitHub cấp) — chạy KHI
 * PUSH TAG vX.Y.Z, đính kết quả vào GitHub Release. Script này chỉ đóng vai
 * "người đi lấy hàng đã làm sẵn", không tự làm lại việc đó.
 *
 * Tách ra file riêng (thay vì nhúng `node -e "..."` thẳng vào shell script
 * CI) — bài học cũ trong chính CI này: lồng 2 lớp shell (agent Jenkins gọi
 * `docker run ... sh -c "..."` gọi tiếp `node -e "..."`) rất dễ escape sai
 * ký tự đặc biệt (dấu nháy, xuống dòng trong JS) — xem lịch sử
 * scripts/download-ldid.mjs bên repo clickai-mcp-filesystem, cùng bài học.
 *
 * Biến môi trường cần có:
 *   GIT_TOKEN     GitHub PAT (đã có sẵn credential 'github-pat', dùng lại
 *                 đúng token này — cùng quyền đọc repo private đã dùng để
 *                 checkout code, không cần credential mới).
 *   GITHUB_REPO   "owner/repo", VD "clickaivn/clickai-mcp-filesystem".
 *   OUT_DIR       Thư mục đích, VD "backend/public/download".
 *
 * Lỗi ở bất kỳ bước nào -> exit 1 -> Jenkins bắt ở tầng gọi, đánh dấu
 * UNSTABLE (không chặn Build Image/Deploy) — giống hệt cách xử lý lỗi build
 * Win/Linux binary ở stage này, KHÔNG tự ý xử lý khác biệt ở đây.
 */

const TOKEN = process.env.GIT_TOKEN;
const REPO = process.env.GITHUB_REPO;
const OUT_DIR = process.env.OUT_DIR;

if (!TOKEN || !REPO || !OUT_DIR) {
  console.error('❌ Thiếu biến môi trường GIT_TOKEN/GITHUB_REPO/OUT_DIR.');
  process.exit(1);
}

const headers = {
  Authorization: `Bearer ${TOKEN}`,
  Accept: 'application/vnd.github+json',
  'User-Agent': 'clickai-jenkins-ci',
  'X-GitHub-Api-Version': '2022-11-28',
};

async function main() {
  const relRes = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`, { headers });
  if (!relRes.ok) {
    throw new Error(`GitHub releases/latest trả về HTTP ${relRes.status}: ${await relRes.text()}`);
  }
  const release = await relRes.json();

  const asset = (release.assets || []).find((a) => a.name.endsWith('.dmg'));
  if (!asset) {
    throw new Error(`Release "${release.tag_name}" không có asset .dmg nào — job macos-dmg đã chạy xong cho tag này chưa?`);
  }

  console.log(`▶ Tải "${asset.name}" (${asset.size} bytes) từ release "${release.tag_name}"...`);

  // Asset private repo PHẢI tải qua API asset endpoint (asset.url, không
  // phải browser_download_url) kèm Accept: application/octet-stream —
  // browser_download_url chỉ dùng được sau khi đã có redirect đã ký sẵn,
  // không tự xác thực bằng Bearer token của ta được.
  const dlRes = await fetch(asset.url, {
    headers: { ...headers, Accept: 'application/octet-stream' },
  });
  if (!dlRes.ok) {
    throw new Error(`Tải asset thất bại — HTTP ${dlRes.status}: ${await dlRes.text()}`);
  }
  const buf = Buffer.from(await dlRes.arrayBuffer());
  if (buf.length !== asset.size) {
    throw new Error(`Tải thiếu — nhận ${buf.length} bytes, kỳ vọng ${asset.size} bytes.`);
  }

  const fs = await import('node:fs');
  const path = await import('node:path');
  fs.mkdirSync(OUT_DIR, { recursive: true });

  // Dọn .dmg CŨ trong OUT_DIR trước khi ghi — GET /api/mcp/download/mac
  // (backend/src/routes/mcp.js) tự chọn file .dmg "mới nhất theo tên sort()",
  // để lẫn nhiều bản cũ dễ phục vụ nhầm nếu tên file không tăng dần đúng ý.
  for (const f of fs.readdirSync(OUT_DIR)) {
    if (f.endsWith('.dmg')) fs.rmSync(path.join(OUT_DIR, f));
  }

  const destPath = path.join(OUT_DIR, asset.name);
  fs.writeFileSync(destPath, buf);
  console.log(`✅ Đã lưu ${destPath} (${buf.length} bytes, release ${release.tag_name}).`);
}

main().catch((err) => {
  console.error('❌', err.message || err);
  process.exit(1);
});
