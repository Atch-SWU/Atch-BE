"use strict";

const categoryNames = { COLOR: "색상", BACKGROUND: "배경", EYES: "눈", ACCESSORY: "액세서리" };
let csrf;
let selected = [];
let submitting = false;
let page = 0;

function showStatus(id, message, kind = "") {
  const element = document.getElementById(id);
  element.textContent = message;
  element.className = `status ${kind}`;
}

async function refreshCsrf() {
  const response = await fetch("/admin/csrf", { cache: "no-store", credentials: "same-origin" });
  if (!response.ok) throw new Error("로그인 연결을 준비하지 못했습니다. 페이지를 새로고침해 주세요.");
  csrf = await response.json();
  for (const form of document.querySelectorAll("#login-form, #logout-form")) {
    let field = form.querySelector('input[type="hidden"]');
    if (!field) {
      field = document.createElement("input");
      field.type = "hidden";
      form.append(field);
    }
    field.name = csrf.parameterName;
    field.value = csrf.token;
    form.querySelector('button[type="submit"]').disabled = false;
  }
}

async function readResponse(response) {
  if (response.status === 401 || response.redirected) {
    location.assign("/admin/login?expired=1");
    throw new Error("로그인이 만료되었습니다.");
  }
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    const details = body.errors?.map(error => error.message).filter(Boolean).join("\n");
    throw new Error(details || body.message || "요청을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.");
  }
  return body;
}

function updateSelection() {
  document.getElementById("selection-count").textContent = selected.length ? `${selected.length}개 선택` : "";
  document.getElementById("register-button").disabled = !selected.length || submitting || !csrf;
  document.getElementById("clear-selection").disabled = !selected.length || submitting;
}

function clearSelection() {
  for (const item of selected) URL.revokeObjectURL(item.preview);
  selected = [];
  document.getElementById("upload-rows").replaceChildren();
  document.getElementById("image-files").value = "";
  updateSelection();
}

function addFiles(files) {
  if (submitting) return;
  const incoming = Array.from(files);
  const totalBytes = [...selected.map(item => item.file), ...incoming].reduce((sum, file) => sum + file.size, 0);
  if (selected.length + incoming.length > 20 || totalBytes > 20 * 1024 * 1024) {
    showStatus("status", "한 번에 최대 20개, 전체 20MB까지 선택할 수 있습니다.", "error");
    return;
  }
  if (incoming.some(file => !["image/png", "image/jpeg"].includes(file.type) || !file.size || file.size > 2 * 1024 * 1024)) {
    showStatus("status", "PNG·JPEG 파일을 선택해 주세요. 한 장당 최대 2MB입니다.", "error");
    return;
  }
  showStatus("status", "");
  for (const file of incoming) {
    const row = document.getElementById("upload-row-template").content.firstElementChild.cloneNode(true);
    const item = { file, row, code: `item-${crypto.randomUUID()}`, preview: URL.createObjectURL(file) };
    row.querySelector("img").src = item.preview;
    row.querySelector(".filename").textContent = file.name;
    row.querySelector('input[name="name"]').value = file.name.replace(/\.[^.]+$/, "").slice(0, 100);
    row.querySelector(".remove-row").addEventListener("click", () => {
      URL.revokeObjectURL(item.preview);
      selected = selected.filter(current => current !== item);
      row.remove();
      updateSelection();
    });
    selected.push(item);
    document.getElementById("upload-rows").append(row);
  }
  updateSelection();
}

async function loadCatalog() {
  showStatus("catalog-status", "목록을 불러오고 있습니다.");
  try {
    const response = await fetch(`/api/v1/admin/character-items?page=${page}&size=20`, { cache: "no-store", credentials: "same-origin" });
    const result = await readResponse(response);
    const catalog = document.getElementById("catalog");
    catalog.replaceChildren();
    for (const entry of result.items) {
      const item = entry.item;
      const card = document.createElement("article");
      card.className = "catalog-card";
      if (item.assetUrl) {
        const image = document.createElement("img");
        image.src = item.assetUrl;
        image.alt = item.name;
        image.loading = "lazy";
        image.addEventListener("error", () => {
          const placeholder = document.createElement("div");
          placeholder.className = "image-placeholder";
          placeholder.textContent = "이미지 확인 필요";
          placeholder.setAttribute("role", "img");
          placeholder.setAttribute("aria-label", `${item.name} 이미지를 불러오지 못했습니다`);
          image.replaceWith(placeholder);
        });
        card.append(image);
      }
      const name = document.createElement("h3");
      name.textContent = item.name;
      const detail = document.createElement("p");
      detail.textContent = `${item.grade}등급 · ${categoryNames[item.category]}${item.active ? "" : " · 지급 중지"}`;
      card.append(name, detail);
      catalog.append(card);
    }
    showStatus("catalog-status", result.totalElements ? `전체 ${result.totalElements}개` : "등록된 아이템이 없습니다.");
    document.getElementById("page-label").textContent = `${page + 1}페이지`;
    document.getElementById("previous-page").disabled = page === 0;
    document.getElementById("next-page").disabled = !result.hasNext;
  } catch (error) {
    showStatus("catalog-status", error.message, "error");
  }
}

async function registerItems(event) {
  event.preventDefault();
  if (submitting || !selected.length || !csrf) return;
  const metadata = { items: selected.map((item, index) => ({
    itemCode: item.code,
    name: item.row.querySelector('[name="name"]').value.trim(),
    grade: item.row.querySelector('[name="grade"]').value,
    category: item.row.querySelector('[name="category"]').value,
    fileIndex: index
  })) };
  const payload = new FormData();
  payload.append("metadata", new Blob([JSON.stringify(metadata)], { type: "application/json" }), "metadata.json");
  for (const item of selected) payload.append("files", item.file, item.file.name);
  submitting = true;
  document.querySelectorAll(".upload-row input, .upload-row select, .remove-row, #image-files").forEach(element => { element.disabled = true; });
  updateSelection();
  showStatus("status", "이미지를 업로드하고 아이템을 등록하고 있습니다.");
  try {
    const response = await fetch("/api/v1/admin/character-items/batch", {
      method: "POST", body: payload, credentials: "same-origin", headers: { [csrf.headerName]: csrf.token }
    });
    const result = await readResponse(response);
    clearSelection();
    showStatus("status", `${result.items.length}개 아이템을 등록했습니다.`, "success");
    page = 0;
    await loadCatalog();
  } catch (error) {
    showStatus("status", `${error.message}\n등록 결과를 확인하려면 목록을 새로고침하세요. 재시도할 때는 선택한 항목을 유지해 주세요.`, "error");
    await refreshCsrf().catch(() => {});
  } finally {
    submitting = false;
    document.querySelectorAll(".upload-row input, .upload-row select, .remove-row, #image-files").forEach(element => { element.disabled = false; });
    updateSelection();
  }
}

async function initialize() {
  try {
    await refreshCsrf();
    if (document.getElementById("login-form")) {
      const query = new URLSearchParams(location.search);
      if (query.has("error")) showStatus("status", "이메일·비밀번호와 운영진 등록 여부를 확인해 주세요.", "error");
      if (query.has("expired")) showStatus("status", "로그인이 만료되었습니다. 다시 로그인해 주세요.");
      return;
    }
    updateSelection();
    document.getElementById("image-files").addEventListener("change", event => addFiles(event.target.files));
    document.getElementById("clear-selection").addEventListener("click", clearSelection);
    document.getElementById("registration-form").addEventListener("submit", registerItems);
    document.getElementById("refresh-catalog").addEventListener("click", loadCatalog);
    document.getElementById("previous-page").addEventListener("click", () => { page -= 1; loadCatalog(); });
    document.getElementById("next-page").addEventListener("click", () => { page += 1; loadCatalog(); });
    const dropzone = document.getElementById("dropzone");
    dropzone.addEventListener("dragover", event => { event.preventDefault(); dropzone.classList.add("dragging"); });
    dropzone.addEventListener("dragleave", () => dropzone.classList.remove("dragging"));
    dropzone.addEventListener("drop", event => {
      event.preventDefault();
      dropzone.classList.remove("dragging");
      addFiles(event.dataTransfer.files);
    });
    await loadCatalog();
  } catch (error) {
    showStatus("status", error.message, "error");
  }
}

initialize();
