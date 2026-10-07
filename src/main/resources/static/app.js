const apiBase = "/api";
const authPanel = document.getElementById("authPanel");
const appShell = document.getElementById("appShell");
const landingPage = document.getElementById("landingPage");
const authMessage = document.getElementById("authMessage");
const dashboardMessage = document.getElementById("dashboardMessage");
const panels = Array.from(document.querySelectorAll(".content-panel"));
const navLinks = Array.from(document.querySelectorAll(".nav-link"));

let currentUser = null;
let categories = [];
let items = [];
let toastTimer = null;
let previousFocus = null;

async function api(path, options) {
    const settings = Object.assign({ credentials: "same-origin" }, options || {});
    settings.headers = Object.assign({}, settings.headers || {});
    if (settings.body !== undefined) {
        settings.headers["Content-Type"] = "application/json";
    }
    const response = await fetch(apiBase + path, settings);
    if (response.status === 204) {
        return null;
    }
    const contentType = response.headers.get("content-type") || "";
    let data = null;
    if (contentType.includes("application/json")) {
        try {
            data = await response.json();
        } catch (parseError) {
            const error = new Error("The server returned an invalid response. Please try again.");
            error.status = response.status;
            throw error;
        }
    }
    if (!response.ok) {
        const error = new Error(data && data.message ? data.message : "The request could not be completed.");
        error.status = response.status;
        throw error;
    }
    return data;
}

function showToast(message, isError) {
    dashboardMessage.textContent = message;
    dashboardMessage.classList.toggle("error", Boolean(isError));
    dashboardMessage.classList.add("visible");
    window.clearTimeout(toastTimer);
    toastTimer = window.setTimeout(function () {
        dashboardMessage.classList.remove("visible");
    }, 3200);
}

function showAuthMessage(message, isSuccess) {
    authMessage.textContent = message || "";
    authMessage.classList.toggle("success", Boolean(isSuccess));
}

function handleError(error, target) {
    const message = error instanceof TypeError
        ? "Unable to reach the server. Check that it is running and try again."
        : error && error.message ? error.message : "Something went wrong. Please try again.";
    if (target === "auth") {
        showAuthMessage(message, false);
    } else {
        showToast(message, true);
    }
}

function showPanel(panelId) {
    panels.forEach(function (panel) {
        panel.classList.toggle("active", panel.id === panelId);
    });
    navLinks.forEach(function (link) {
        link.classList.toggle("active", link.dataset.panel === panelId);
    });
    if (panelId === "activityPanel") {
        loadActivity();
    }
    if (panelId === "recommendationsPanel") {
        loadRecommendations();
    }
    if (panelId === "adminPanel") {
        loadAdminData();
    }
}

function openDashboard(profile) {
    if (!profile || !profile.username) {
        showAuthMessage("Your account could not be loaded. Please sign in again.");
        return;
    }
    currentUser = profile;
    landingPage.hidden = true;
    document.body.classList.remove("dialog-open");
    authPanel.hidden = true;
    appShell.hidden = false;
    document.querySelector(".admin-nav").hidden = profile.role !== "ADMIN";
    document.getElementById("greeting").textContent = "Welcome back, " + (profile.displayName || profile.username);
    document.getElementById("sidebarName").textContent = profile.displayName || profile.username;
    document.getElementById("sidebarRole").textContent = profile.role === "ADMIN" ? "Administrator" : "Member";
    document.getElementById("profileUsername").textContent = "@" + profile.username;
    document.getElementById("profileEmail").textContent = profile.email;
    document.getElementById("displayNameInput").value = profile.displayName || "";
    const initial = (profile.displayName || profile.username || "R").trim().charAt(0).toUpperCase();
    document.getElementById("sidebarAvatar").textContent = initial;
    document.getElementById("headerAvatar").textContent = initial;
    document.getElementById("todayLabel").textContent = new Intl.DateTimeFormat(undefined, { weekday: "long", month: "long", day: "numeric" }).format(new Date()).toUpperCase();
    document.getElementById("greeting").setAttribute("tabindex", "-1");
    document.getElementById("greeting").focus();
    showPanel("discoverPanel");
    loadWorkspace();
}

async function loadWorkspace() {
    try {
        const result = await Promise.all([api("/categories"), api("/items")]);
        categories = Array.isArray(result[0]) ? result[0] : [];
        items = Array.isArray(result[1]) ? result[1] : [];
        renderCatalog();
        renderCategoryFilters();
        renderPreferences();
        renderFeaturedItems();
        renderAdminCategoryOptions();
    } catch (error) {
        handleError(error);
    }
}

function categoryName(categoryId) {
    const category = categories.find(function (entry) { return Number(entry.categoryId) === Number(categoryId); });
    return category ? category.name : "General";
}

function createElement(tag, className, text) {
    const element = document.createElement(tag);
    if (className) {
        element.className = className;
    }
    if (text !== undefined && text !== null) {
        element.textContent = text;
    }
    return element;
}

function itemCard(item) {
    const card = createElement("article", "item-card");
    const art = createElement("div", "item-art");
    const color = ["", "violet", "mint", "peach"][item.itemId % 4];
    if (color) {
        art.classList.add(color);
    }
    art.appendChild(createElement("span", "item-type", item.type));
    art.appendChild(createElement("span", "art-symbol", item.type === "PRODUCT" ? "◇" : "✳"));
    const content = createElement("div", "item-content");
    const meta = createElement("div", "item-meta");
    meta.appendChild(createElement("span", "", categoryName(item.categoryId)));
    meta.appendChild(createElement("span", "", item.price === null ? "" : "Price " + Number(item.price).toFixed(2)));
    content.appendChild(meta);
    content.appendChild(createElement("h3", "", item.title));
    content.appendChild(createElement("p", "", item.description || "A new find from the RecomAI collection."));
    const actions = createElement("div", "item-actions");
    actions.appendChild(interactionButton(item.itemId, "VIEW", "View"));
    actions.appendChild(interactionButton(item.itemId, "SAVE", "Save"));
    actions.appendChild(interactionButton(item.itemId, "DISLIKE", "Not for me", true));
    content.appendChild(actions);
    card.appendChild(art);
    card.appendChild(content);
    return card;
}

function interactionButton(itemId, type, label, negative) {
    const button = createElement("button", negative ? "small-action negative" : "small-action", label);
    button.type = "button";
    button.addEventListener("click", function () {
        recordInteraction(itemId, type);
    });
    return button;
}

async function recordInteraction(itemId, type) {
    try {
        await api("/users/me/interactions", {
            method: "POST",
            body: JSON.stringify({ itemId: itemId, type: type })
        });
        showToast(type === "DISLIKE" ? "Got it. We’ll learn from that." : "Activity saved. Your picks will get smarter.");
    } catch (error) {
        handleError(error);
    }
}

function renderCatalog() {
    const container = document.getElementById("catalogItems");
    const filter = document.getElementById("categoryFilter").value;
    const visibleItems = filter ? items.filter(function (item) { return String(item.categoryId) === filter; }) : items;
    container.replaceChildren();
    visibleItems.forEach(function (item) {
        container.appendChild(itemCard(item));
    });
    document.getElementById("catalogEmpty").hidden = visibleItems.length > 0;
}

function renderFeaturedItems() {
    const container = document.getElementById("featuredItems");
    container.replaceChildren();
    items.slice(0, 3).forEach(function (item) {
        container.appendChild(itemCard(item));
    });
}

function renderCategoryFilters() {
    const select = document.getElementById("categoryFilter");
    const selected = select.value;
    select.replaceChildren();
    select.appendChild(new Option("All categories", ""));
    categories.forEach(function (category) {
        select.appendChild(new Option(category.name, String(category.categoryId)));
    });
    select.value = selected;
}

async function renderPreferences() {
    const container = document.getElementById("preferenceList");
    container.replaceChildren();
    const empty = document.getElementById("preferencesEmpty");
    if (!categories.length) {
        empty.hidden = false;
        return;
    }
    empty.hidden = true;
    let savedPreferences = [];
    try {
        const result = await api("/users/me/preferences");
        savedPreferences = Array.isArray(result) ? result : [];
    } catch (error) {
        handleError(error);
        return;
    }
    const weights = new Map((savedPreferences || []).map(function (preference) {
        return [preference.categoryId, Number(preference.weight)];
    }));
    categories.forEach(function (category) {
        const row = createElement("div", "preference-row");
        const name = createElement("div", "preference-name");
        name.appendChild(document.createTextNode(category.name));
        name.appendChild(createElement("span", "preference-description", category.description || "Choose how often you would like to see this category."));
        const range = document.createElement("input");
        range.className = "preference-range";
        range.type = "range";
        range.setAttribute("aria-label", "Preference weight for " + category.name);
        range.min = "0";
        range.max = "1";
        range.step = "0.05";
        range.value = String(weights.has(category.categoryId) ? weights.get(category.categoryId) : 0.5);
        const value = createElement("span", "preference-value", Number(range.value).toFixed(2));
        range.addEventListener("input", function () { value.textContent = Number(range.value).toFixed(2); });
        const save = createElement("button", "small-action", "Save");
        save.type = "button";
        save.addEventListener("click", async function () {
            try {
                await api("/users/me/preferences/" + category.categoryId, {
                    method: "PUT",
                    body: JSON.stringify({ weight: Number(range.value) })
                });
                showToast("Interest updated for " + category.name + ".");
            } catch (error) {
                handleError(error);
            }
        });
        row.appendChild(name);
        row.appendChild(range);
        row.appendChild(value);
        row.appendChild(save);
        container.appendChild(row);
    });
}

async function loadRecommendations() {
    const container = document.getElementById("recommendationList");
    try {
        const recommendations = await api("/users/me/recommendations?limit=10");
        renderRecommendations(recommendations || []);
    } catch (error) {
        handleError(error);
        container.replaceChildren();
        const empty = document.getElementById("recommendationsEmpty");
        empty.querySelector("h3").textContent = "Couldn't load your picks";
        empty.querySelector("p").textContent = "Check your connection and try loading recommendations again.";
        empty.hidden = false;
    }
}

function renderRecommendations(recommendations) {
    const container = document.getElementById("recommendationList");
    const empty = document.getElementById("recommendationsEmpty");
    container.replaceChildren();
    const results = Array.isArray(recommendations) ? recommendations : [];
    empty.hidden = results.length > 0;
    if (results.length === 0) {
        empty.querySelector("h3").textContent = "Your picks start here";
        empty.querySelector("p").textContent = "Choose “Refresh my picks” to generate a personalized list from your saved signals.";
    }
    results.forEach(function (recommendation, index) {
        const card = createElement("article", "recommendation-card");
        card.appendChild(createElement("div", "recommendation-rank", String(index + 1).padStart(2, "0")));
        const copy = createElement("div", "");
        copy.appendChild(createElement("h3", "", recommendation.itemTitle));
        const item = items.find(function (entry) { return Number(entry.itemId) === Number(recommendation.itemId); });
        copy.appendChild(createElement("p", "recommendation-category", categoryName(item ? item.categoryId : 0)));
        copy.appendChild(createElement("p", "recommendation-reason", "Why this was recommended: " + (recommendation.reason || "This item is a match for your current profile.")));
        card.appendChild(copy);
        const numericScore = Number(recommendation.score);
        const score = createElement("div", "recommendation-score", Number.isFinite(numericScore) ? Math.round(numericScore) + "%" : "—");
        score.appendChild(createElement("small", "", "MATCH"));
        card.appendChild(score);
        const save = createElement("button", "small-action recommendation-save", "Save");
        save.type = "button";
        save.setAttribute("aria-label", "Save " + (recommendation.itemTitle || "recommendation"));
        save.addEventListener("click", function () { recordInteraction(recommendation.itemId, "SAVE"); });
        card.appendChild(save);
        container.appendChild(card);
    });
}

async function generateRecommendations() {
    const button = document.getElementById("generateRecommendations");
    button.disabled = true;
    button.textContent = "Finding your picks...";
    try {
        const recommendations = await api("/users/me/recommendations?limit=10", { method: "POST" });
        renderRecommendations(recommendations || []);
        showToast(recommendations && recommendations.length ? "Your new picks are ready." : "Add interests or activity to get more personalized picks.");
    } catch (error) {
        handleError(error);
    } finally {
        button.disabled = false;
        button.textContent = "Find my picks ✳";
    }
}

async function loadActivity() {
    const container = document.getElementById("activityList");
    try {
        const response = await api("/users/me/interactions?limit=50");
        const activity = Array.isArray(response) ? response : [];
        container.replaceChildren();
        document.getElementById("activityEmpty").hidden = activity.length > 0;
        activity.forEach(function (event) {
            const item = items.find(function (entry) { return entry.itemId === event.itemId; });
            const row = createElement("div", "activity-row");
            row.appendChild(createElement("span", "activity-symbol", interactionSymbol(event.type)));
            const copy = createElement("div", "");
            copy.appendChild(createElement("strong", "", activityLabel(event.type) + (item ? " · " + item.title : "")));
            copy.appendChild(createElement("small", "", categoryName(item ? item.categoryId : 0)));
            row.appendChild(copy);
            row.appendChild(createElement("time", "activity-date", formatDate(event.occurredAt)));
            container.appendChild(row);
        });
    } catch (error) {
        handleError(error);
    }
}

function interactionSymbol(type) {
    const symbols = { VIEW: "◉", CLICK: "↗", SAVE: "♡", PURCHASE: "✓", DISLIKE: "−", HIDE: "×" };
    return symbols[type] || "•";
}

function activityLabel(type) {
    const labels = { VIEW: "Viewed", CLICK: "Opened", SAVE: "Saved", PURCHASE: "Purchased", DISLIKE: "Disliked", HIDE: "Hidden" };
    return labels[type] || "Interacted with";
}

function formatDate(value) {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "" : new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric" }).format(date);
}

async function loadAdminData() {
    if (!currentUser || currentUser.role !== "ADMIN") {
        return;
    }
    try {
        const result = await Promise.all([api("/admin/categories"), api("/admin/items")]);
        renderAdminCategories(result[0] || []);
        renderAdminItems(result[1] || []);
        renderAdminCategoryOptions(result[0] || []);
    } catch (error) {
        handleError(error);
    }
}

function renderAdminCategoryOptions(categoryList) {
    const select = document.getElementById("adminCategorySelect");
    if (!select) {
        return;
    }
    const list = categoryList || categories;
    const selected = select.value;
    select.replaceChildren();
    select.appendChild(new Option("Choose a category", ""));
    list.filter(function (category) { return category.active; }).forEach(function (category) {
        select.appendChild(new Option(category.name, String(category.categoryId)));
    });
    select.value = selected;
}

function renderAdminCategories(categoryList) {
    const container = document.getElementById("adminCategories");
    container.replaceChildren();
    categoryList.forEach(function (category) {
        const row = createElement("div", "admin-list-row");
        const copy = createElement("div", "admin-list-copy");
        copy.appendChild(createElement("strong", "", category.name));
        copy.appendChild(createElement("small", "", category.description || "No description"));
        row.appendChild(copy);
        row.appendChild(createElement("span", category.active ? "status-badge" : "status-badge inactive", category.active ? "ACTIVE" : "PAUSED"));
        const edit = createElement("button", "small-action", "Edit");
        edit.type = "button";
        edit.addEventListener("click", function () { editCategory(category); });
        row.appendChild(edit);
        const toggle = createElement("button", "small-action", category.active ? "Pause" : "Enable");
        toggle.type = "button";
        toggle.addEventListener("click", function () { toggleCategory(category); });
        row.appendChild(toggle);
        container.appendChild(row);
    });
}

function renderAdminItems(itemList) {
    const container = document.getElementById("adminItems");
    container.replaceChildren();
    itemList.forEach(function (item) {
        const row = createElement("div", "admin-list-row");
        const copy = createElement("div", "admin-list-copy");
        copy.appendChild(createElement("strong", "", item.title));
        copy.appendChild(createElement("small", "", categoryName(item.categoryId) + " · " + item.type));
        row.appendChild(copy);
        row.appendChild(createElement("span", item.active ? "status-badge" : "status-badge inactive", item.active ? "ACTIVE" : "PAUSED"));
        const edit = createElement("button", "small-action", "Edit");
        edit.type = "button";
        edit.addEventListener("click", function () { editItem(item); });
        row.appendChild(edit);
        const toggle = createElement("button", "small-action", item.active ? "Pause" : "Enable");
        toggle.type = "button";
        toggle.addEventListener("click", function () { toggleItem(item); });
        row.appendChild(toggle);
        container.appendChild(row);
    });
}

function editCategory(category) {
    const form = document.getElementById("categoryForm");
    form.elements.categoryId.value = category.categoryId;
    form.elements.name.value = category.name;
    form.elements.description.value = category.description || "";
    document.getElementById("categoryFormTitle").textContent = "Edit category";
    form.querySelector(".cancel-edit").hidden = false;
    form.scrollIntoView({ behavior: "smooth", block: "center" });
}

function editItem(item) {
    const form = document.getElementById("itemForm");
    form.elements.itemId.value = item.itemId;
    form.elements.categoryId.value = item.categoryId;
    form.elements.title.value = item.title;
    form.elements.description.value = item.description || "";
    form.elements.type.value = item.type;
    form.elements.price.value = item.price === null ? "" : item.price;
    form.elements.externalUrl.value = item.externalUrl || "";
    document.getElementById("itemFormTitle").textContent = "Edit catalog item";
    form.querySelector(".cancel-edit").hidden = false;
    form.scrollIntoView({ behavior: "smooth", block: "center" });
}

function resetForm(formId) {
    const form = document.getElementById(formId);
    form.reset();
    if (form.elements.categoryId && formId === "categoryForm") {
        form.elements.categoryId.value = "";
        document.getElementById("categoryFormTitle").textContent = "Add a category";
    }
    if (formId === "itemForm") {
        form.elements.itemId.value = "";
        document.getElementById("itemFormTitle").textContent = "Add an item";
    }
    form.querySelector(".cancel-edit").hidden = true;
}

async function toggleCategory(category) {
    try {
        await api("/admin/categories/" + category.categoryId + "/active", {
            method: "PATCH",
            body: JSON.stringify({ active: !category.active })
        });
        await refreshCatalogData();
        await loadAdminData();
        showToast(category.active ? "Category paused." : "Category enabled.");
    } catch (error) {
        handleError(error);
    }
}

async function toggleItem(item) {
    try {
        await api("/admin/items/" + item.itemId + "/active", {
            method: "PATCH",
            body: JSON.stringify({ active: !item.active })
        });
        await refreshCatalogData();
        await loadAdminData();
        showToast(item.active ? "Item paused." : "Item enabled.");
    } catch (error) {
        handleError(error);
    }
}

async function refreshCatalogData() {
    const result = await Promise.all([api("/categories"), api("/items")]);
    categories = Array.isArray(result[0]) ? result[0] : [];
    items = Array.isArray(result[1]) ? result[1] : [];
    renderCatalog();
    renderCategoryFilters();
    renderFeaturedItems();
    renderAdminCategoryOptions();
}

function setAuthMode(mode) {
    const register = mode === "register";
    document.getElementById("loginForm").hidden = register;
    document.getElementById("registerForm").hidden = !register;
    document.getElementById("showLogin").classList.toggle("active", !register);
    document.getElementById("showLogin").setAttribute("aria-selected", String(!register));
    document.getElementById("showRegister").classList.toggle("active", register);
    document.getElementById("showRegister").setAttribute("aria-selected", String(register));
    document.getElementById("authTitle").textContent = register ? "Start your RecomAI account" : "Good to see you again";
    document.getElementById("authSubtitle").textContent = register
        ? "Create your profile and start discovering what fits."
        : "Sign in to pick up where your curiosity left off.";
    showAuthMessage("");
}

function openAuth(mode) {
    previousFocus = document.activeElement;
    appShell.hidden = true;
    landingPage.hidden = false;
    authPanel.hidden = false;
    document.body.classList.add("dialog-open");
    setAuthMode(mode || "login");
    const input = document.querySelector(mode === "register" ? "#registerForm input" : "#loginForm input");
    if (input) {
        window.setTimeout(function () { input.focus(); }, 0);
    }
}

function closeAuth() {
    authPanel.hidden = true;
    document.body.classList.remove("dialog-open");
    if (previousFocus && typeof previousFocus.focus === "function") {
        previousFocus.focus();
    }
}

document.querySelectorAll("[data-auth]").forEach(function (button) {
    button.addEventListener("click", function () { openAuth(button.dataset.auth); });
});
document.querySelectorAll("[data-close-auth]").forEach(function (button) {
    button.addEventListener("click", closeAuth);
});
document.addEventListener("keydown", function (event) {
    if (event.key === "Escape" && !authPanel.hidden) {
        closeAuth();
        return;
    }
    if (event.key === "Tab" && !authPanel.hidden) {
        const focusable = Array.from(authPanel.querySelectorAll("button:not([disabled]), input:not([disabled]), a[href], select:not([disabled]), textarea:not([disabled])"))
            .filter(function (element) { return !element.closest("[hidden]"); });
        if (!focusable.length) {
            return;
        }
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    }
});
document.getElementById("discoveryForm").addEventListener("submit", function (event) {
    event.preventDefault();
    openAuth("register");
    showAuthMessage("Create an account to get recommendations based on your interests.");
});

document.getElementById("showLogin").addEventListener("click", function () {
    setAuthMode("login");
});

document.getElementById("showRegister").addEventListener("click", function () {
    setAuthMode("register");
});

document.getElementById("loginForm").addEventListener("submit", async function (event) {
    event.preventDefault();
    showAuthMessage("");
    const submitButton = event.currentTarget.querySelector("[type=submit]");
    submitButton.disabled = true;
    const data = new FormData(event.currentTarget);
    try {
        const profile = await api("/auth/login", {
            method: "POST",
            body: JSON.stringify({ email: data.get("email"), password: data.get("password") })
        });
        openDashboard(profile);
    } catch (error) {
        handleError(error, "auth");
    } finally {
        submitButton.disabled = false;
    }
});

document.getElementById("registerForm").addEventListener("submit", async function (event) {
    event.preventDefault();
    showAuthMessage("");
    const data = new FormData(event.currentTarget);
    const registration = {
        displayName: data.get("displayName"),
        username: data.get("username"),
        email: data.get("email"),
        password: data.get("password")
    };
    const submitButton = event.currentTarget.querySelector("[type=submit]");
    submitButton.disabled = true;
    try {
        await api("/auth/register", { method: "POST", body: JSON.stringify(registration) });
        event.currentTarget.reset();
        setAuthMode("login");
        showAuthMessage("Your account is ready. Sign in to continue.", true);
        document.querySelector("#loginForm input").focus();
    } catch (error) {
        if (error instanceof TypeError) {
            setAuthMode("login");
            showAuthMessage("We couldn't confirm account creation. Try signing in; if your account isn't recognized, create it again.", false);
        } else {
            handleError(error, "auth");
        }
    } finally {
        submitButton.disabled = false;
    }
});

document.getElementById("logoutButton").addEventListener("click", async function () {
    try {
        await api("/auth/logout", { method: "POST" });
    } catch (error) {
        // Leave the dashboard even if the session already expired on the server.
    }
    currentUser = null;
    appShell.hidden = true;
    landingPage.hidden = false;
    authPanel.hidden = false;
    document.body.classList.add("dialog-open");
    setAuthMode("login");
    document.getElementById("loginForm").reset();
    showAuthMessage("You are signed out.", true);
    document.querySelector("#loginForm input").focus();
});
document.querySelectorAll("[data-logout]").forEach(function (button) {
    button.addEventListener("click", function () {
        document.getElementById("logoutButton").click();
    });
});

document.querySelectorAll(".nav-link").forEach(function (link) {
    link.addEventListener("click", function () { showPanel(link.dataset.panel); });
});
document.querySelectorAll("[data-go]").forEach(function (button) {
    button.addEventListener("click", function () { showPanel(button.dataset.go); });
});
document.getElementById("categoryFilter").addEventListener("change", renderCatalog);
document.getElementById("generateRecommendations").addEventListener("click", generateRecommendations);
document.getElementById("refreshActivity").addEventListener("click", loadActivity);

document.getElementById("profileForm").addEventListener("submit", async function (event) {
    event.preventDefault();
    const displayName = document.getElementById("displayNameInput").value;
    try {
        const profile = await api("/users/me/profile", {
            method: "PATCH",
            body: JSON.stringify({ displayName: displayName })
        });
        currentUser = profile;
        const displayName = (profile.displayName || profile.username || "RecomAI").trim();
        document.getElementById("greeting").textContent = "Welcome back, " + displayName;
        document.getElementById("sidebarName").textContent = displayName;
        document.getElementById("sidebarAvatar").textContent = displayName.charAt(0).toUpperCase();
        document.getElementById("headerAvatar").textContent = displayName.charAt(0).toUpperCase();
        document.getElementById("profileUsername").textContent = "@" + (profile.username || "member");
        document.getElementById("displayNameInput").value = profile.displayName || "";
        showToast("Your profile has been updated.");
    } catch (error) {
        handleError(error);
    }
});

document.querySelectorAll(".cancel-edit").forEach(function (button) {
    button.addEventListener("click", function () { resetForm(button.dataset.form); });
});

document.getElementById("categoryForm").addEventListener("submit", async function (event) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const categoryId = data.get("categoryId");
    const request = { name: data.get("name"), description: data.get("description") || null };
    try {
        await api(categoryId ? "/admin/categories/" + categoryId : "/admin/categories", {
            method: categoryId ? "PUT" : "POST",
            body: JSON.stringify(request)
        });
        resetForm("categoryForm");
        await refreshCatalogData();
        await loadAdminData();
        showToast(categoryId ? "Category updated." : "Category created.");
    } catch (error) {
        handleError(error);
    }
});

document.getElementById("itemForm").addEventListener("submit", async function (event) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const itemId = data.get("itemId");
    const price = data.get("price");
    const request = {
        categoryId: Number(data.get("categoryId")),
        title: data.get("title"),
        description: data.get("description") || null,
        type: data.get("type"),
        externalUrl: data.get("externalUrl") || null,
        price: price === "" ? null : Number(price)
    };
    try {
        await api(itemId ? "/admin/items/" + itemId : "/admin/items", {
            method: itemId ? "PUT" : "POST",
            body: JSON.stringify(request)
        });
        resetForm("itemForm");
        await refreshCatalogData();
        await loadAdminData();
        showToast(itemId ? "Catalog item updated." : "Catalog item created.");
    } catch (error) {
        handleError(error);
    }
});

api("/users/me").then(function (profile) {
    openDashboard(profile);
}).catch(function () {
    authPanel.hidden = true;
    appShell.hidden = true;
    landingPage.hidden = false;
});

const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
const heroVideo = document.querySelector(".hero-video");
if (heroVideo && reducedMotion.matches) {
    heroVideo.pause();
}
if ("IntersectionObserver" in window && !reducedMotion.matches) {
    const revealObserver = new IntersectionObserver(function (entries, observer) {
        entries.forEach(function (entry) {
            if (entry.isIntersecting) {
                entry.target.classList.add("in-view");
                observer.unobserve(entry.target);
            }
        });
    }, { threshold: 0.12, rootMargin: "0px 0px -35px 0px" });
    document.querySelectorAll(".editorial-section, .final-cta").forEach(function (section) {
        section.classList.add("reveal");
        revealObserver.observe(section);
    });
}
