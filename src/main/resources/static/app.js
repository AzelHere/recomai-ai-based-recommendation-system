import { api } from "./modules/api-client.js";
import { initializeThemeToggle } from "./modules/theme-toggle.js";
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
let niches = [];
let feedbackByItemId = new Map();
let feedbackLoaded = false;
let userCollections = [];
let toastTimer = null;
let previousFocus = null;
let catalogRequestId = 0;
let authRequestInProgress = false;
let logoutInProgress = false;
let sessionCheckInProgress = false;
let pendingHomepageQuery = null;
let onboardingCompleted = false;
let passwordResetToken = null;
let catalogPage = 1;
const catalogPageSize = 12;

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
    document.getElementById("duplicateRegistrationAction").hidden = true;
}

function setAuthRequestState(active) {
    authRequestInProgress = active;
    ["loginForm", "registerForm", "forgotPasswordForm", "resetPasswordForm"].forEach(function (formId) {
        const form = document.getElementById(formId);
        form.setAttribute("aria-busy", String(active));
        form.querySelectorAll("input, button, select, textarea").forEach(function (control) {
            control.disabled = active;
        });
    });
    ["showLogin", "showRegister"].forEach(function (tabId) {
        document.getElementById(tabId).disabled = active;
    });
}

function showSessionCheckFailure(error) {
    authPanel.hidden = true;
    appShell.hidden = true;
    landingPage.hidden = false;
    const status = document.getElementById("connectionStatus");
    const message = document.getElementById("connectionStatusMessage");
    message.textContent = error instanceof TypeError
        ? "We couldn't reach RecomAI to check your sign-in. Your account state is unknown. Check your connection and retry."
        : "RecomAI couldn't confirm your sign-in because the server returned an error. Your account state is unknown. Retry in a moment.";
    status.hidden = false;
}

async function restoreSession() {
    const status = document.getElementById("connectionStatus");
    if (sessionCheckInProgress) return;
    sessionCheckInProgress = true;
    const retry = document.getElementById("retrySessionCheck");
    retry.disabled = true;
    status.hidden = true;
    try {
        const profile = await api("/users/me");
        openDashboard(profile);
    } catch (error) {
        if (error && error.status === 401) {
            authPanel.hidden = true;
            appShell.hidden = true;
            landingPage.hidden = false;
            status.hidden = true;
            return;
        }
        showSessionCheckFailure(error);
    } finally {
        sessionCheckInProgress = false;
        retry.disabled = false;
    }
}

function handleError(error, target) {
    if (error && error.status === 401 && target !== "auth") {
        if (currentUser) {
            currentUser = null;
            openAuth("login");
            showAuthMessage("Your session has expired. Sign in again to continue.", false);
        }
        return;
    }
    const message = error instanceof TypeError
        ? "Unable to reach the server. Check that it is running and try again."
        : error && error.message ? error.message : "Something went wrong. Please try again.";
    if (target === "auth") {
        showAuthMessage(message, false);
    } else {
        showToast(message, true);
    }
}

function showPanel(panelId, fromRoute) {
    if (!panels.some(function (panel) { return panel.id === panelId; })) return;
    if (!fromRoute && currentUser && window.location.hash !== "#app/" + panelId) {
        window.history.pushState({ panelId: panelId }, "", "#app/" + panelId);
    }
    panels.forEach(function (panel) {
        panel.classList.toggle("active", panel.id === panelId);
    });
    navLinks.forEach(function (link) {
        link.classList.toggle("active", link.dataset.panel === panelId);
    });
    if (panelId === "activityPanel") {
        loadActivity();
    }
    if (panelId === "progressPanel") {
        loadProgress();
    }
    if (panelId === "howWorksPanel") loadRecommendationInfo();
    if (panelId === "savedPanel") {
        loadSavedItems();
    }
    if (panelId === "recommendationsPanel") {
        loadRecommendations();
    }
    if (panelId === "interestProfilePanel") {
        loadInterestProfile();
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
    const requestedPanel = window.location.hash.startsWith("#app/")
        ? window.location.hash.slice("#app/".length) : "discoverPanel";
    showPanel(requestedPanel === "adminPanel" && profile.role !== "ADMIN" ? "discoverPanel" : requestedPanel, true);
    loadWorkspace();
    loadOnboardingStatus();
}

async function loadWorkspace() {
    try {
        const result = await Promise.all([api("/categories"), api("/items")]);
        categories = Array.isArray(result[0]) ? result[0] : [];
        items = Array.isArray(result[1]) ? result[1] : [];
        try {
            const feedback = await api("/users/me/feedback");
            feedbackByItemId = new Map((Array.isArray(feedback) ? feedback : [])
                .map(function (entry) { return [Number(entry.itemId), entry]; }));
            feedbackLoaded = true;
        } catch (feedbackError) {
            feedbackLoaded = false;
            handleError(feedbackError);
        }
        renderCatalog();
        renderCategoryFilters();
        renderPreferences();
        renderFeaturedItems();
        renderAdminCategoryOptions();
        if (document.getElementById("recommendationsPanel").classList.contains("active")) {
            loadRecommendations();
        }
        if (document.getElementById("progressPanel")?.classList.contains("active")) loadProgress();
    } catch (error) {
        handleError(error);
    }
}

async function loadOnboardingStatus() {
    try {
        const results = await Promise.all([api("/discovery/niches"), api("/users/me/onboarding")]);
        niches = Array.isArray(results[0]) ? results[0] : [];
        renderOnboardingOptions();
        onboardingCompleted = Boolean(results[1] && results[1].completed);
        if (!onboardingCompleted) {
            showPanel("onboardingPanel");
        } else if (pendingHomepageQuery) {
            processPendingHomepageSearch();
        }
    } catch (error) {
        handleError(error);
    }
}

function renderOnboardingOptions() {
    const select = document.getElementById("onboardingNiche");
    if (!select) return;
    const selected = select.value;
    select.replaceChildren(new Option("Choose an area", ""));
    niches.forEach(function (niche) {
        select.appendChild(new Option(niche.name, String(niche.categoryId)));
    });
    if (selected && niches.some(function (niche) { return String(niche.categoryId) === selected; })) {
        select.value = selected;
    }
    renderOnboardingTopics();
}

function renderOnboardingTopics() {
    const selectedNiche = niches.find(function (niche) {
        return String(niche.categoryId) === document.getElementById("onboardingNiche").value;
    });
    const container = document.getElementById("onboardingTopics");
    container.replaceChildren();
    if (!selectedNiche || !Array.isArray(selectedNiche.topics)) return;
    selectedNiche.topics.forEach(function (topic) {
        const label = createElement("label", "topic-choice");
        const checkbox = document.createElement("input");
        checkbox.type = "checkbox";
        checkbox.value = String(topic.interestId);
        checkbox.name = "onboardingTopic";
        checkbox.addEventListener("change", function () {
            const selected = Array.from(container.querySelectorAll("input:checked"));
            container.querySelectorAll("input:not(:checked)").forEach(function (entry) {
                entry.disabled = selected.length >= 3;
            });
        });
        label.append(checkbox, document.createTextNode(topic.name));
        container.appendChild(label);
    });
}

async function loadInterestProfile() {
    if (!currentUser) return;
    try {
        const profile = await api("/users/me/interests/insights");
        renderInterestGroup("currentInterests", profile.current, true);
        renderInterestGroup("growingInterests", profile.growing, true);
        renderInterestGroup("decliningInterests", profile.declining, true);
        renderInterestGroup("exploreInterests", profile.exploreNext);
        renderEmergingInterest(profile.growing);
    } catch (error) {
        handleError(error);
    }
}

function renderEmergingInterest(insights) {
    const callout = document.getElementById("emergingInterestCallout");
    const message = document.getElementById("emergingInterestMessage");
    const confidence = document.getElementById("emergingInterestConfidence");
    const candidates = Array.isArray(insights) ? insights.slice(0, 3) : [];
    callout.hidden = candidates.length === 0;
    if (!candidates.length) return;

    const topics = candidates.map(function (insight) { return insight.topic; });
    message.textContent = "Your recent activity suggests growing interest in " + topics.join(", ") + ". We’re still learning.";
    const labels = Array.from(new Set(candidates.map(function (insight) { return insight.confidence; })));
    confidence.textContent = "Signal confidence: " + labels.join(" · ").toLowerCase() + ".";
}

function renderInterestGroup(elementId, insights, showStrength) {
    const container = document.getElementById(elementId);
    if (!container) return;
    container.replaceChildren();
    const list = Array.isArray(insights) ? insights : [];
    if (!list.length) {
        container.appendChild(createElement("p", "insight-empty", "Nothing to show yet. Explore a few resources and this space will adapt."));
        return;
    }
    list.forEach(function (insight) {
        const row = createElement("div", "insight-row");
        const copy = createElement("div", "insight-copy");
        const trend = insight.trend === "UP" ? " ↑" : insight.trend === "DOWN" ? " ↓" : "";
        copy.appendChild(createElement("strong", "", insight.topic + trend));
        copy.appendChild(createElement("small", "", insight.niche + " · " + (insight.confidence || "confidence unavailable")));
        if (insight.evidence) {
            copy.appendChild(createElement("small", "insight-evidence", insight.evidence));
        }
        row.appendChild(copy);
        if (showStrength) {
            const meter = document.createElement("meter");
            meter.min = "0";
            meter.max = "100";
            meter.value = String(insight.strengthPercent || 0);
            meter.setAttribute("aria-label", insight.topic + " interest estimate");
            row.appendChild(meter);
            row.appendChild(createElement("span", "insight-strength", (insight.strengthPercent || 0) + "%"));
        }
        if (elementId === "exploreInterests") {
            const button = createElement("button", "small-action", "Explore");
            button.type = "button";
            button.addEventListener("click", function () {
                showPanel("catalogPanel");
                const niche = niches.find(function (entry) { return entry.name === insight.niche; });
                if (niche) {
                    document.getElementById("categoryFilter").value = String(niche.categoryId);
                    renderCatalog();
                }
            });
            row.appendChild(button);
        }
        container.appendChild(row);
    });
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
    if (item.editorPick) meta.appendChild(createElement("span", "editor-pick-badge", "Editor's pick"));
    meta.appendChild(createElement("span", "", item.price === null ? "" : "Price " + Number(item.price).toFixed(2)));
    content.appendChild(meta);
    content.appendChild(createElement("h3", "", item.title));
    content.appendChild(createElement("p", "", item.description || "A new find from the RecomAI collection."));
    const resourceMeta = [item.sourceName, item.difficulty && item.difficulty.replaceAll("_", " "),
        item.estimatedMinutes ? item.estimatedMinutes + " min" : null].filter(Boolean).join(" · ");
    if (resourceMeta) content.appendChild(createElement("small", "resource-meta", resourceMeta));
    if (Array.isArray(item.tags) && item.tags.length) {
        const tags = createElement("div", "item-tags");
        item.tags.forEach(function (tag) { tags.appendChild(createElement("span", "topic-tag", tag)); });
        content.appendChild(tags);
    }
    const actions = createElement("div", "item-actions");
    actions.appendChild(interactionButton(item.itemId, "VIEW", "View"));
    if (item.externalUrl) {
        const open = createElement("a", "small-action", "Open resource ↗");
        open.href = item.externalUrl;
        open.target = "_blank";
        open.rel = "noopener noreferrer";
        open.addEventListener("click", function () { recordInteraction(item.itemId, "CLICK"); });
        actions.appendChild(open);
    }
    actions.appendChild(interactionButton(item.itemId, "LIKE", "Like"));
    actions.appendChild(interactionButton(item.itemId, "SAVE", "Save"));
    actions.appendChild(interactionButton(item.itemId, "READ_PROGRESS", "In progress"));
    actions.appendChild(interactionButton(item.itemId, "COMPLETE", "Mark complete"));
    actions.appendChild(interactionButton(item.itemId, "MORE_LIKE", "More like this"));
    actions.appendChild(interactionButton(item.itemId, "DISLIKE", "Not for me", true));
    content.appendChild(actions);
    content.appendChild(createRatingControl(item.itemId));
    card.appendChild(art);
    card.appendChild(content);
    return card;
}

function createRatingControl(itemId) {
    const wrapper = createElement("div", "rating-control");
    const label = createElement("label", "", "Your rating");
    const select = document.createElement("select");
    select.setAttribute("aria-label", "Your rating for this resource");
    select.appendChild(new Option("Choose a rating", ""));
    [1, 2, 3, 4, 5].forEach(function (rating) {
        select.appendChild(new Option(rating + " out of 5", String(rating)));
    });
    const existing = feedbackByItemId.get(Number(itemId));
    if (existing) select.value = String(existing.rating);
    const button = createElement("button", "small-action", existing ? "Update rating" : "Save rating");
    button.type = "button";
    const status = createElement("span", "rating-status");
    status.setAttribute("role", "status");
    status.setAttribute("aria-live", "polite");
    if (!feedbackLoaded) {
        select.disabled = true;
        button.disabled = true;
        status.textContent = "Your saved rating could not be loaded.";
    }
    label.appendChild(select);
    wrapper.appendChild(label);
    wrapper.appendChild(button);
    wrapper.appendChild(status);
    button.addEventListener("click", function () {
        saveRating(itemId, select, button, status);
    });
    return wrapper;
}

async function saveRating(itemId, select, button, status) {
    const rating = Number(select.value);
    if (!rating || rating < 1 || rating > 5) {
        status.textContent = "Choose a rating from 1 to 5 first.";
        return;
    }
    const existing = feedbackByItemId.get(Number(itemId));
    button.disabled = true;
    select.disabled = true;
    status.textContent = "Saving your rating…";
    try {
        const payload = { itemId: Number(itemId), rating: rating, comment: null };
        const saved = await api(existing ? "/users/me/feedback/" + itemId : "/users/me/feedback", {
            method: existing ? "PUT" : "POST",
            body: JSON.stringify(payload)
        });
        feedbackByItemId.set(Number(itemId), saved);
        button.textContent = "Update rating";
        status.textContent = "Rating saved. Refresh your picks to use it.";
        showToast("Your rating has been saved.");
    } catch (error) {
        status.textContent = error && error.message ? error.message : "Your rating could not be saved.";
        handleError(error);
    } finally {
        button.disabled = false;
        select.disabled = false;
    }
}

function interactionButton(itemId, type, label, negative) {
    const button = createElement("button", negative ? "small-action negative" : "small-action", label);
    button.type = "button";
    button.addEventListener("click", function () {
        recordInteraction(itemId, type, button);
    });
    return button;
}

function newInteractionRequestId() {
    if (window.crypto && typeof window.crypto.randomUUID === "function") return window.crypto.randomUUID();
    return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function (character) {
        const random = Math.floor(Math.random() * 16);
        return (character === "x" ? random : (random & 3) | 8).toString(16);
    });
}

async function recordInteraction(itemId, type, button) {
    if (button && button.disabled) return false;
    if (button) button.disabled = true;
    try {
        await api("/users/me/interactions", {
            method: "POST",
            body: JSON.stringify({ itemId: itemId, type: type, requestId: newInteractionRequestId() })
        });
        const message = type === "DISLIKE" || type === "HIDE" ? "Got it. We’ll show you less of this."
            : type === "SAVE" ? "Saved to your reading list."
                : type === "COMPLETE" ? "Marked complete. Nice progress."
                    : type === "READ_PROGRESS" ? "Added to your in-progress list."
                : type === "MORE_LIKE" ? "Got it. Refresh your picks to see more like this."
                    : "Activity saved. Your picks will adapt over time.";
        showToast(message);
        loadInterestProfile();
        if (type === "SAVE" && document.getElementById("savedPanel").classList.contains("active")) {
            loadSavedItems();
        }
        if (document.getElementById("progressPanel")?.classList.contains("active")) loadProgress();
        if (document.getElementById("recommendationsPanel").classList.contains("active")
                && ["LIKE", "SAVE", "DISLIKE", "HIDE", "MORE_LIKE", "COMPLETE"].includes(type)) {
            generateRecommendations();
        }
        return true;
    } catch (error) {
        handleError(error);
        return false;
    } finally {
        if (button) button.disabled = false;
    }
}

async function renderCatalog(trackSearch) {
    const container = document.getElementById("catalogItems");
    const filter = document.getElementById("categoryFilter").value;
    const query = document.getElementById("catalogSearchQuery").value.trim();
    const requestId = ++catalogRequestId;
    const params = new URLSearchParams({ page: String(catalogPage), pageSize: String(catalogPageSize), sort: "NEWEST" });
    if (query) params.set("query", query);
    if (filter) params.set("categoryId", filter);
    try {
        const page = await api("/items/search?" + params.toString());
        if (requestId !== catalogRequestId) return;
        let visibleItems = Array.isArray(page.items) ? page.items.slice() : [];
        const type = document.getElementById("typeFilter").value;
        const difficulty = document.getElementById("difficultyFilter").value;
        const maximumTime = Number(document.getElementById("timeFilter").value || 0);
        if (type) visibleItems = visibleItems.filter(function (item) { return item.type === type; });
        if (difficulty) visibleItems = visibleItems.filter(function (item) { return item.difficulty === difficulty; });
        if (maximumTime) visibleItems = visibleItems.filter(function (item) { return item.estimatedMinutes && Number(item.estimatedMinutes) <= maximumTime; });
        const sort = document.getElementById("sortFilter").value;
        if (sort === "title") visibleItems.sort(function (a, b) { return a.title.localeCompare(b.title); });
        if (sort === "time") visibleItems.sort(function (a, b) { return (a.estimatedMinutes || Infinity) - (b.estimatedMinutes || Infinity); });
        container.replaceChildren();
        visibleItems.forEach(function (item) { container.appendChild(itemCard(item)); });
        document.getElementById("catalogEmpty").hidden = visibleItems.length > 0;
        document.getElementById("catalogResultsSummary").textContent =
            (page.totalItems || 0) + ((page.totalItems || 0) === 1 ? " resource found" : " resources found")
            + "; " + visibleItems.length + " shown on this page with the selected filters";
        const pagination = document.getElementById("catalogPagination");
        const previousPage = document.getElementById("catalogPreviousPage");
        const nextPage = document.getElementById("catalogNextPage");
        const totalPages = Number(page.totalPages || 0);
        pagination.hidden = totalPages <= 1;
        previousPage.disabled = catalogPage <= 1;
        nextPage.disabled = catalogPage >= totalPages;
        document.getElementById("catalogPageSummary").textContent = totalPages > 0
            ? "Page " + catalogPage + " of " + totalPages : "No pages";
        let tracked = false;
        if (trackSearch && currentUser && query.length >= 2) {
            await api("/users/me/interactions/search", {
                method: "POST",
                body: JSON.stringify({ query: query, requestId: newInteractionRequestId() })
            });
            tracked = true;
            await Promise.all([loadInterestProfile(), loadActivity()]);
        }
        return { ok: true, tracked: tracked };
    } catch (error) {
        handleError(error);
        return { ok: false, tracked: false };
    }
}

async function processPendingHomepageSearch() {
    if (!pendingHomepageQuery || !currentUser || !onboardingCompleted) return;
    const query = pendingHomepageQuery;
    document.getElementById("catalogSearchQuery").value = query;
    catalogPage = 1;
    showPanel("catalogPanel");
    const result = await renderCatalog(true);
    if (!result || !result.ok) return;
    if (query.length >= 2 && !result.tracked) return;
    pendingHomepageQuery = null;
    await generateRecommendations();
    if (query.length < 2) {
        showToast("Search results loaded. Add another character to save this as a recommendation signal.", true);
    }
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
        const [recommendations, profile] = await Promise.all([
            api("/users/me/recommendations?limit=10"),
            api("/users/me/recommendation-profile").catch(function () { return null; })
        ]);
        if (recommendations && recommendations.length) renderRecommendations(recommendations, profile);
        else renderCatalogDiscovery(await api("/users/me/recommendations/discovery?limit=10"));
    } catch (error) {
        handleError(error);
        container.replaceChildren();
        const empty = document.getElementById("recommendationsEmpty");
        empty.querySelector("h3").textContent = "Couldn't load your picks";
        empty.querySelector("p").textContent = "Check your connection and try loading recommendations again.";
        empty.hidden = false;
    }
}

function renderRecommendations(recommendations, profile) {
    const container = document.getElementById("recommendationList");
    const empty = document.getElementById("recommendationsEmpty");
    const evidenceNote = document.getElementById("recommendationEvidenceNote");
    container.replaceChildren();
    const results = Array.isArray(recommendations) ? recommendations : [];
    empty.hidden = results.length > 0;
    const noBehaviorYet = profile && Number(profile.interactionCount) === 0 && Number(profile.feedbackCount) === 0;
    const exploratoryCount = results.filter(function (entry) { return entry.reasonType === "COLD_START"; }).length;
    evidenceNote.hidden = !noBehaviorYet && exploratoryCount === 0;
    evidenceNote.textContent = noBehaviorYet
        ? "You have no interaction or rating history yet. These starting picks use your onboarding choices and editor-selected resources; your activity and feedback can help personalize future picks."
        : exploratoryCount > 0
            ? "" + exploratoryCount + (exploratoryCount === 1 ? " pick is" : " picks are")
                + " exploratory because there isn't enough evidence to personalize those items yet. Your feedback helps shape later picks."
            : "";
    if (results.length === 0) {
        empty.querySelector("h3").textContent = "Your picks start here";
        empty.querySelector("p").textContent = "Choose “Refresh my picks” to generate a personalized list from your saved signals.";
    }
    const bucketLists = new Map();
    ["FAMILIAR", "RELATED", "DISCOVERY"].forEach(function (bucket) {
        if (!results.some(function (entry) { return (entry.bucketType || "FAMILIAR") === bucket; })) return;
        const section = createElement("section", "feed-bucket");
        const label = bucket === "FAMILIAR" ? "Based on your interests"
            : bucket === "RELATED" ? "Explore something related" : "Something new for you";
        section.appendChild(createElement("h3", "feed-bucket-title", label));
        const list = createElement("div", "recommendation-list");
        section.appendChild(list);
        container.appendChild(section);
        bucketLists.set(bucket, list);
    });
    results.forEach(function (recommendation, index) {
        const bucket = recommendation.bucketType || "FAMILIAR";
        const card = createElement("article", "recommendation-card");
        card.appendChild(createElement("div", "recommendation-rank", String(index + 1).padStart(2, "0")));
        const copy = createElement("div", "");
        copy.appendChild(createElement("h3", "", recommendation.itemTitle));
        const item = items.find(function (entry) { return Number(entry.itemId) === Number(recommendation.itemId); });
        copy.appendChild(createElement("p", "recommendation-category", categoryName(item ? item.categoryId : 0)));
        if (item && item.description) {
            copy.appendChild(createElement("p", "recommendation-description", item.description));
        }
        if (item) {
            const details = [item.type, item.sourceName,
                item.estimatedMinutes ? item.estimatedMinutes + " min" : null].filter(Boolean).join(" · ");
            if (details) copy.appendChild(createElement("p", "recommendation-meta", details));
            if (Array.isArray(item.tags) && item.tags.length) {
                copy.appendChild(createElement("p", "recommendation-meta", "Topics: " + item.tags.join(", ")));
            }
        }
        const bucketLabel = recommendation.reasonType === "EDITOR_PICK" ? "Editor's pick"
            : recommendation.reasonType === "POPULARITY" ? "Popular in the catalog"
            : recommendation.bucketType === "RELATED" ? "Related discovery"
                : recommendation.bucketType === "DISCOVERY" ? "Explore"
                    : "Based on your interests";
        copy.appendChild(createElement("span", "recommendation-bucket", bucketLabel));
        copy.appendChild(createElement("p", "recommendation-reason", "Why this was recommended: " + (recommendation.reason || "This item is a match for your current profile.")));
        const breakdown = recommendation.scoreBreakdown && typeof recommendation.scoreBreakdown === "object"
            ? Object.entries(recommendation.scoreBreakdown).filter(function (entry) { return Number(entry[1]) !== 0; }) : [];
        if (breakdown.length) {
            const disclosure = createElement("details", "recommendation-explanation");
            disclosure.appendChild(createElement("summary", "", "See the signals used"));
            const list = createElement("ul", "recommendation-signals");
            breakdown.forEach(function (entry) {
                const labels = {
                    category_preference: "Selected area match",
                    selected_topic_match: "Topics you chose",
                    related_topic_match: "Related topics",
                    metadata_similarity: "Similar descriptions and topics",
                    positive_interaction: "Your positive activity",
                    rating_affinity: "Your ratings",
                    catalog_popularity: "Recent activity from other users",
                    collaborative_evidence: "Similar users' activity",
                    onboarding_goal: "Your discovery goal",
                    negative_feedback_penalty: "Negative feedback",
                    editor_pick: "Editor's selection"
                };
                const label = labels[entry[0]] || entry[0].replaceAll("_", " ");
                const value = Number(entry[1]);
                list.appendChild(createElement("li", "", label + ": " + (value < 0 ? "−" : "+")
                    + Math.abs(value).toFixed(2) + " ranking points"));
            });
            disclosure.appendChild(list);
            disclosure.appendChild(createElement("p", "recommendation-score-note",
                "Ranking points show which signals raised or lowered this score. The feed also balances familiar, related, and exploratory picks. Points are not a probability of liking this item."));
            copy.appendChild(disclosure);
        }
        copy.appendChild(createRatingControl(recommendation.itemId));
        if (item && item.externalUrl) {
            const open = createElement("a", "small-action recommendation-open", "Open resource ↗");
            open.href = item.externalUrl;
            open.target = "_blank";
            open.rel = "noopener noreferrer";
            open.addEventListener("click", function () { recordInteraction(item.itemId, "CLICK"); });
            copy.appendChild(open);
        }
        card.appendChild(copy);
        const actions = createElement("div", "recommendation-actions");
        const like = createElement("button", "small-action", "Like");
        like.type = "button";
        like.setAttribute("aria-label", "Like " + (recommendation.itemTitle || "recommendation"));
        like.addEventListener("click", function () { recordInteraction(recommendation.itemId, "LIKE", like); });
        actions.appendChild(like);
        const save = createElement("button", "small-action recommendation-save", "Save");
        save.type = "button";
        save.setAttribute("aria-label", "Save " + (recommendation.itemTitle || "recommendation"));
        save.addEventListener("click", function () { recordInteraction(recommendation.itemId, "SAVE", save); });
        actions.appendChild(save);
        const more = createElement("button", "small-action recommendation-more", "More like this");
        more.type = "button";
        more.setAttribute("aria-label", "Show me more like " + (recommendation.itemTitle || "this"));
        more.addEventListener("click", function () { recordInteraction(recommendation.itemId, "MORE_LIKE", more); });
        actions.appendChild(more);
        const dismiss = createElement("button", "small-action negative recommendation-dismiss", "Not interested");
        dismiss.type = "button";
        dismiss.setAttribute("aria-label", "Not interested in " + (recommendation.itemTitle || "this item"));
        dismiss.addEventListener("click", async function () {
            if (await recordInteraction(recommendation.itemId, "DISLIKE", dismiss)) card.remove();
        });
        actions.appendChild(dismiss);
        const hide = createElement("button", "small-action negative recommendation-hide", "Hide");
        hide.type = "button";
        hide.setAttribute("aria-label", "Hide " + (recommendation.itemTitle || "this item"));
        hide.addEventListener("click", async function () {
            if (await recordInteraction(recommendation.itemId, "HIDE", hide)) card.remove();
        });
        actions.appendChild(hide);
        card.appendChild(actions);
        (bucketLists.get(bucket) || container).appendChild(card);
    });
}

function renderCatalogDiscovery(items) {
    const container = document.getElementById("recommendationList");
    const empty = document.getElementById("recommendationsEmpty");
    const note = document.getElementById("recommendationEvidenceNote");
    container.replaceChildren();
    note.hidden = false;
    note.textContent = "Your personalized feed is empty. These are general catalog discovery items, selected by editor curation and catalog quality; they are not trending or personalized.";
    if (!Array.isArray(items) || !items.length) {
        empty.querySelector("h3").textContent = "No eligible catalog items right now";
        empty.querySelector("p").textContent = "Hidden and recently opened items remain excluded. Check back after the catalog changes.";
        empty.hidden = false;
        return;
    }
    empty.hidden = true;
    const section = createElement("section", "feed-bucket");
    section.appendChild(createElement("h3", "feed-bucket-title", "Catalog discovery · not personalized"));
    const list = createElement("div", "recommendation-list");
    items.forEach(function (item) { list.appendChild(itemCard(item)); });
    section.appendChild(list);
    container.appendChild(section);
}

async function generateRecommendations() {
    const button = document.getElementById("generateRecommendations");
    button.disabled = true;
    button.textContent = "Finding your picks...";
    try {
        const [recommendations, profile] = await Promise.all([
            api("/users/me/recommendations?limit=10", { method: "POST" }),
            api("/users/me/recommendation-profile").catch(function () { return null; })
        ]);
        if (recommendations && recommendations.length) {
            renderRecommendations(recommendations, profile);
            showToast("Your new picks are ready.");
        } else {
            renderCatalogDiscovery(await api("/users/me/recommendations/discovery?limit=10"));
            showToast("No personalized matches yet. Here are general catalog discovery items.");
        }
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
            const detail = event.type === "SEARCH" ? event.searchQuery : item ? item.title : "";
            copy.appendChild(createElement("strong", "", activityLabel(event.type) + (detail ? " · " + detail : "")));
            copy.appendChild(createElement("small", "", item ? categoryName(item.categoryId) : event.type === "SEARCH" ? "Search intent" : ""));
            row.appendChild(copy);
            row.appendChild(createElement("time", "activity-date", formatDate(event.occurredAt)));
            container.appendChild(row);
        });
    } catch (error) {
        handleError(error);
    }
}

function installCollectionControls() {
    const savedPanel = document.getElementById("savedPanel");
    const controls = createElement("form", "collection-controls");
    controls.id = "collectionForm";
    const name = document.createElement("input");
    name.id = "collectionName"; name.maxLength = 80; name.required = true;
    name.placeholder = "Collection name"; name.setAttribute("aria-label", "New collection name");
    const create = createElement("button", "button button-secondary", "Create collection");
    create.type = "submit";
    const select = document.createElement("select");
    select.id = "collectionSelect"; select.setAttribute("aria-label", "Filter saved resources by collection");
    select.appendChild(new Option("All saved resources", ""));
    const remove = createElement("button", "button button-quiet", "Delete collection");
    remove.id = "deleteCollection"; remove.type = "button"; remove.hidden = true;
    controls.append(name, create, select, remove);
    const heading = savedPanel.querySelector(".section-heading");
    heading.insertAdjacentElement("afterend", controls);
    controls.addEventListener("submit", async function (event) {
        event.preventDefault();
        create.disabled = true;
        try {
            await api("/users/me/collections", { method: "POST", body: JSON.stringify({ name: name.value }) });
            name.value = "";
            await loadSavedItems();
            showToast("Collection created.");
        } catch (error) { handleError(error); }
        finally { create.disabled = false; }
    });
    select.addEventListener("change", loadSavedItems);
    remove.addEventListener("click", async function () {
        const collectionId = Number(select.value);
        if (!collectionId || !window.confirm("Delete this collection? Your saved resources will remain saved.")) return;
        remove.disabled = true;
        try { await api("/users/me/collections/" + collectionId, { method: "DELETE" }); await loadSavedItems(); }
        catch (error) { handleError(error); }
        finally { remove.disabled = false; }
    });
}

function renderCollectionControls() {
    const select = document.getElementById("collectionSelect");
    if (!select) return;
    const selected = select.value;
    select.replaceChildren(new Option("All saved resources", ""));
    userCollections.forEach(function (collection) { select.appendChild(new Option(collection.name, String(collection.collectionId))); });
    select.value = userCollections.some(function (entry) { return String(entry.collectionId) === selected; }) ? selected : "";
    document.getElementById("deleteCollection").hidden = !select.value;
}

async function loadSavedItems() {
    const container = document.getElementById("savedItems");
    const empty = document.getElementById("savedItemsEmpty");
    const unavailable = document.getElementById("savedItemsUnavailable");
    try {
        const results = await Promise.all([api("/users/me/interactions?limit=100"), api("/users/me/collections")]);
        const response = results[0];
        userCollections = Array.isArray(results[1]) ? results[1] : [];
        renderCollectionControls();
        const selectedCollection = userCollections.find(function (entry) {
            return String(entry.collectionId) === document.getElementById("collectionSelect").value;
        });
        const savedIds = new Set();
        (Array.isArray(response) ? response : []).forEach(function (event) {
            if (event.type === "SAVE" && event.itemId !== null && event.itemId !== undefined) {
                savedIds.add(Number(event.itemId));
            }
        });
        if (selectedCollection) {
            const memberIds = new Set(selectedCollection.itemIds.map(Number));
            Array.from(savedIds).forEach(function (id) { if (!memberIds.has(id)) savedIds.delete(id); });
        }
        const savedItems = Array.from(savedIds)
            .map(function (itemId) { return items.find(function (item) { return Number(item.itemId) === itemId; }); })
            .filter(Boolean);
        container.replaceChildren();
        savedItems.forEach(function (item) {
            const card = itemCard(item);
            if (userCollections.length) {
                const collectionAction = createElement("div", "collection-item-action");
                const target = document.createElement("select");
                target.setAttribute("aria-label", "Collection for " + item.title);
                userCollections.forEach(function (collection) { target.appendChild(new Option(collection.name, String(collection.collectionId))); });
                const isMember = selectedCollection && selectedCollection.itemIds.map(Number).includes(Number(item.itemId));
                const add = createElement("button", "small-action", isMember ? "Remove from collection" : "Add to collection");
                add.type = "button";
                add.addEventListener("click", async function () {
                    const targetId = selectedCollection ? selectedCollection.collectionId : Number(target.value);
                    if (!targetId) return;
                    try {
                        await api("/users/me/collections/" + targetId + "/items/" + item.itemId,
                            isMember ? { method: "DELETE" } : { method: "PUT" });
                        await loadSavedItems();
                    } catch (error) { handleError(error); }
                });
                collectionAction.append(target, add);
                card.querySelector(".item-content").appendChild(collectionAction);
            }
            container.appendChild(card);
        });
        empty.hidden = savedIds.size > 0;
        const unavailableCount = savedIds.size - savedItems.length;
        unavailable.hidden = unavailableCount === 0;
        if (unavailableCount > 0) {
            unavailable.textContent = unavailableCount + (unavailableCount === 1
                ? " saved resource is no longer available in the catalog."
                : " saved resources are no longer available in the catalog.");
        }
    } catch (error) {
        handleError(error);
    }
}

async function loadProgress() {
    const panel = document.getElementById("progressPanel");
    const status = panel.querySelector("[data-progress-status]");
    const chart = panel.querySelector("[data-week-chart]");
    const inProgress = panel.querySelector("[data-in-progress]");
    const completed = panel.querySelector("[data-completed]");
    try {
        const [history, currentState] = await Promise.all([
            api("/users/me/interactions?limit=100"),
            api("/users/me/interactions/progress")
        ]);
        const events = Array.isArray(history) ? history : [];
        const currentEvents = Array.isArray(currentState) ? currentState : [];
        const continueEvents = currentEvents.filter(function (entry) { return entry.type === "READ_PROGRESS"; });
        const completeEvents = currentEvents.filter(function (entry) { return entry.type === "COMPLETE"; });
        inProgress.replaceChildren(); completed.replaceChildren();
        continueEvents.forEach(function (entry) {
            const item = items.find(function (candidate) { return Number(candidate.itemId) === Number(entry.itemId); });
            if (item && !completeEvents.some(function (done) { return Number(done.itemId) === Number(entry.itemId); })) inProgress.appendChild(itemCard(item));
        });
        completeEvents.forEach(function (entry) {
            const item = items.find(function (candidate) { return Number(candidate.itemId) === Number(entry.itemId); });
            if (item) completed.appendChild(itemCard(item));
        });
        status.textContent = events.length >= 100
            ? "Current progress uses your latest progress or completion event for each item. The weekly chart summarizes your latest 100 activity events."
            : "Current progress uses your latest progress or completion event for each item. The weekly chart uses recorded activity; RecomAI does not infer completion from a click.";
        const counts = Array.from({ length: 7 }, function (_, index) {
            const date = new Date(); date.setHours(0, 0, 0, 0); date.setDate(date.getDate() - (6 - index));
            const key = date.toDateString();
            return { date: date, count: events.filter(function (entry) {
                const occurred = new Date(entry.occurredAt); return !Number.isNaN(occurred.getTime()) && occurred.toDateString() === key;
            }).length };
        });
        const max = Math.max(1, ...counts.map(function (entry) { return entry.count; }));
        chart.replaceChildren();
        counts.forEach(function (entry) {
            const bar = createElement("div", "weekly-bar");
            const fill = createElement("span", "weekly-bar-fill");
            fill.style.height = Math.max(3, entry.count / max * 100) + "%";
            fill.title = entry.count + " recorded activities";
            bar.append(fill, createElement("small", "", new Intl.DateTimeFormat(undefined, { weekday: "short" }).format(entry.date)));
            chart.appendChild(bar);
        });
    } catch (error) { handleError(error); }
}

function interactionSymbol(type) {
    const symbols = { VIEW: "◉", CLICK: "↗", READ_PROGRESS: "◷", COMPLETE: "✓", SAVE: "♡", PURCHASE: "✓", LIKE: "♥", MORE_LIKE: "✦", SHARE: "↗", SKIP: "→", DISLIKE: "−", HIDE: "×", SEARCH: "⌕" };
    return symbols[type] || "•";
}

function activityLabel(type) {
    const labels = { VIEW: "Viewed", CLICK: "Opened", READ_PROGRESS: "In progress", COMPLETE: "Completed", SAVE: "Saved", PURCHASE: "Purchased", LIKE: "Liked", MORE_LIKE: "More like this", SHARE: "Shared", SKIP: "Skipped", DISLIKE: "Disliked", HIDE: "Hidden", SEARCH: "Searched" };
    return labels[type] || "Interacted with";
}

function formatDate(value) {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "" : new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric" }).format(date);
}

function installPhaseTwoControls() {
    const nav = document.querySelector(".main-nav");
    const forYou = document.querySelector('[data-panel="recommendationsPanel"]');
    forYou.lastChild.textContent = " For You";
    document.querySelector('[data-panel="catalogPanel"]').lastChild.textContent = " Explore";
    document.querySelector('[data-panel="interestProfilePanel"]').lastChild.textContent = " My Taste";
    const link = createElement("button", "nav-link", "◷ Progress");
    link.type = "button"; link.dataset.panel = "progressPanel";
    nav.insertBefore(link, document.querySelector('[data-panel="activityPanel"]'));
    navLinks.push(link);
    const howLink = createElement("button", "nav-link", "ⓘ How it works");
    howLink.type = "button"; howLink.dataset.panel = "howWorksPanel";
    nav.appendChild(howLink); navLinks.push(howLink);
    const howPanel = createElement("section", "content-panel");
    howPanel.id = "howWorksPanel";
    howPanel.innerHTML = '<div class="section-heading page-title"><div><p class="eyebrow">TRANSPARENT BY DESIGN</p><h2>How recommendations work</h2><p class="section-subtitle">These live settings describe the signals the current ranker uses. Evidence can be sparse, and suggestions may be exploratory.</p></div></div><div class="insight-grid"><section class="admin-card liquid-glass"><h3>Feed balance</h3><div data-feed-mix></div></section><section class="admin-card liquid-glass"><h3>Interaction weights</h3><div data-signal-weights></div></section></div><p class="privacy-note" data-score-meaning></p>';
    const dashboardMain = document.querySelector(".dashboard-main");
    dashboardMain.insertBefore(howPanel, dashboardMain.querySelector(".dashboard-footer")); panels.push(howPanel);
    link.addEventListener("click", function () { showPanel("progressPanel"); });
    const section = createElement("section", "content-panel");
    section.id = "progressPanel";
    section.innerHTML = '<div class="section-heading page-title"><div><p class="eyebrow">YOUR LEARNING JOURNEY</p><h2>Progress</h2><p class="section-subtitle">Continue resources you marked in progress and review items you marked complete.</p></div></div><p class="privacy-note" data-progress-status></p><section class="admin-card liquid-glass"><h3>Activity this week</h3><p>Counts from your latest activity history only.</p><div class="weekly-activity-chart" data-week-chart aria-label="Recorded activity by day"></div></section><div class="section-heading"><div><h3>Continue where you left off</h3></div></div><div class="item-grid" data-in-progress></div><div class="section-heading"><div><h3>Completed</h3></div></div><div class="item-grid" data-completed></div>';
    document.querySelector(".dashboard-main").insertBefore(section, document.getElementById("activityPanel"));
    panels.push(section);
    installCollectionControls();
    installAdminSettingsControls();
    const privacy = document.querySelector(".privacy-controls");
    const exportButton = createElement("button", "button button-secondary", "Download my data (JSON)");
    exportButton.type = "button"; exportButton.id = "exportMyData";
    const resetButton = createElement("button", "button button-quiet", "Reset personalization");
    resetButton.type = "button"; resetButton.id = "resetPersonalization";
    privacy.append(exportButton, resetButton);
    exportButton.addEventListener("click", async function () {
        exportButton.disabled = true;
        try {
            const data = await api("/users/me/export");
            const link = document.createElement("a");
            link.href = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }));
            link.download = "recomai-my-data.json"; link.click(); URL.revokeObjectURL(link.href);
            showToast("Your data export is ready.");
        } catch (error) { handleError(error); }
        finally { exportButton.disabled = false; }
    });
    resetButton.addEventListener("click", async function () {
        if (!window.confirm("Reset your personalization? This removes preferences, onboarding answers, ratings, activity, inferred interests, and recommendation history. Your account and saved collections remain.")) return;
        resetButton.disabled = true;
        try {
            await api("/users/me/personalization", { method: "DELETE" });
            feedbackByItemId.clear(); feedbackLoaded = true; onboardingCompleted = false;
            await loadWorkspace(); await loadOnboardingStatus();
            showToast("Your personalization data was reset.");
        } catch (error) { handleError(error); }
        finally { resetButton.disabled = false; }
    });
    document.getElementById("catalogSearchForm").insertAdjacentHTML("afterend", '<div class="catalog-filter-row"><label>Type<select id="typeFilter"><option value="">Any type</option></select></label><label>Difficulty<select id="difficultyFilter"><option value="">Any level</option><option>BEGINNER</option><option>INTERMEDIATE</option><option>ADVANCED</option><option>ALL_LEVELS</option></select></label><label>Maximum time<select id="timeFilter"><option value="">Any length</option><option value="15">15 minutes</option><option value="30">30 minutes</option><option value="60">60 minutes</option></select></label><label>Sort<select id="sortFilter"><option value="relevance">Current order</option><option value="title">Title A–Z</option><option value="time">Shortest first</option></select></label></div>');
    const typeFilter = document.getElementById("typeFilter");
    ["ARTICLE", "VIDEO", "COURSE", "DOCUMENTATION", "GUIDE", "RESOURCE", "TOOL", "BOOK"].forEach(function (type) {
        typeFilter.appendChild(new Option(type.replaceAll("_", " "), type));
    });
    ["typeFilter", "difficultyFilter", "timeFilter", "sortFilter"].forEach(function (id) {
        document.getElementById(id).addEventListener("change", function () { catalogPage = 1; renderCatalog(); });
    });
}

function installAdminSettingsControls() {
    const panel = document.getElementById("adminPanel");
    const card = createElement("section", "admin-card liquid-glass settings-card");
    card.id = "adminRecommendationSettings";
    card.innerHTML = '<div class="admin-card-title"><span class="admin-icon" aria-hidden="true">⚖</span><div><p class="eyebrow">RANKER SETTINGS</p><h3>Feed balance</h3></div></div><p>Choose the share for familiar, related, and exploratory results. Shares must total 100%. Preview uses your own account as the sample and does not save a recommendation batch.</p><div class="setting-sliders"><label>Familiar <output data-share-output="familiar">70%</output><input type="range" min="0" max="100" value="70" data-share="familiar"></label><label>Related <output data-share-output="related">20%</output><input type="range" min="0" max="100" value="20" data-share="related"></label><label>Discovery <output data-share-output="discovery">10%</output><input type="range" min="0" max="100" value="10" data-share="discovery"></label></div><p data-share-total role="status" aria-live="polite">Total: 100%</p><div class="form-actions"><button type="button" class="button button-secondary" data-settings-preview>Preview top 10</button><button type="button" class="button button-primary" data-settings-save>Save settings</button><button type="button" class="button button-quiet" data-settings-reset>Reset defaults</button></div><div class="admin-list" data-settings-preview-results aria-live="polite"></div><div><h4>Recent settings changes</h4><div class="admin-list" data-settings-audit></div></div>';
    panel.querySelector(".section-heading").insertAdjacentElement("afterend", card);
    const sliders = Array.from(card.querySelectorAll("input[data-share]"));
    sliders.forEach(function (slider) {
        slider.addEventListener("input", function () {
            const value = Number(slider.value);
            card.querySelector('[data-share-output="' + slider.dataset.share + '"]').textContent = value + "%";
            const total = sliders.reduce(function (sum, input) { return sum + Number(input.value); }, 0);
            const message = card.querySelector("[data-share-total]");
            message.textContent = "Total: " + total + "%" + (total === 100 ? "" : " — adjust shares to 100% before saving");
            message.classList.toggle("message-error", total !== 100);
        });
    });
    card.querySelector("[data-settings-preview]").addEventListener("click", previewAdminSettings);
    card.querySelector("[data-settings-save]").addEventListener("click", saveAdminSettings);
    card.querySelector("[data-settings-reset]").addEventListener("click", async function () {
        setAdminMixSliders({ familiarShare: 0.70, relatedShare: 0.20, discoveryShare: 0.10 });
        await saveAdminSettings();
    });
}

function setAdminMixSliders(settings) {
    [["familiar", settings.familiarShare], ["related", settings.relatedShare], ["discovery", settings.discoveryShare]]
        .forEach(function ([name, value]) {
            const slider = document.querySelector('#adminRecommendationSettings [data-share="' + name + '"]');
            slider.value = Math.round(Number(value) * 100);
            slider.dispatchEvent(new Event("input", { bubbles: true }));
        });
}

function currentAdminMix() {
    const share = function (key) { return Number(document.querySelector('#adminRecommendationSettings [data-share="' + key + '"]').value) / 100; };
    return { familiarShare: share("familiar"), relatedShare: share("related"), discoveryShare: share("discovery") };
}

async function loadAdminSettings() {
    try {
        const [settings, audit] = await Promise.all([api("/admin/settings"), api("/admin/settings/audit?limit=10")]);
        setAdminMixSliders(settings);
        const output = document.querySelector("[data-settings-audit]"); output.replaceChildren();
        (audit || []).forEach(function (entry) {
            output.appendChild(createElement("p", "admin-list-row", entry.settingKey + ": "
                + Number(entry.oldValue).toFixed(2) + " → " + Number(entry.newValue).toFixed(2)
                + " by " + (entry.actorUsername || "deleted admin") + " · " + formatDate(entry.changedAt)));
        });
    } catch (error) { handleError(error); }
}

async function previewAdminSettings() {
    const button = document.querySelector("[data-settings-preview]");
    const output = document.querySelector("[data-settings-preview-results]");
    button.disabled = true; output.replaceChildren();
    try {
        const mix = currentAdminMix();
        const results = await api("/admin/settings/preview", { method: "POST", body: JSON.stringify({
            sampleUserId: currentUser.userId, familiarShare: mix.familiarShare,
            relatedShare: mix.relatedShare, discoveryShare: mix.discoveryShare
        }) });
        (results || []).forEach(function (entry, index) {
            output.appendChild(createElement("p", "admin-list-row", (index + 1) + ". " + entry.itemTitle + " · " + entry.bucketType));
        });
        showToast("Preview generated without saving a recommendation batch.");
    } catch (error) { handleError(error); }
    finally { button.disabled = false; }
}

async function saveAdminSettings() {
    const button = document.querySelector("[data-settings-save]");
    const mix = currentAdminMix();
    if (Math.abs(mix.familiarShare + mix.relatedShare + mix.discoveryShare - 1) > 0.0001) {
        showToast("Feed shares must add up to 100% before saving.", true); return;
    }
    button.disabled = true;
    try {
        await api("/admin/settings", { method: "PUT", body: JSON.stringify(mix) });
        await loadAdminSettings(); showToast("Recommendation feed settings saved.");
    } catch (error) { handleError(error); }
    finally { button.disabled = false; }
}

async function loadRecommendationInfo() {
    try {
        const info = await api("/recommendation-info");
        const mix = document.querySelector("[data-feed-mix]");
        const weights = document.querySelector("[data-signal-weights]");
        mix.replaceChildren(); weights.replaceChildren();
        Object.entries(info.feedMix || {}).forEach(function ([name, value]) {
            mix.appendChild(createElement("p", "", name + ": " + (Number(value) * 100).toFixed(0) + "%"));
        });
        Object.entries(info.interactionWeights || {}).forEach(function ([name, value]) {
            weights.appendChild(createElement("p", "", name.toLowerCase().replaceAll("_", " ") + ": " + Number(value).toFixed(2)));
        });
        document.querySelector("[data-score-meaning]").textContent = info.scoreMeaning
            + " Popularity and collaborative signals require at least " + info.popularityMinimumUsers
            + " other users before they contribute.";
    } catch (error) { handleError(error); }
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
        loadAdminSettings();
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
    list.filter(function (category) { return category.active && category.nicheCode; }).forEach(function (category) {
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
        const select = document.createElement("input");
        select.type = "checkbox";
        select.className = "admin-item-select";
        select.value = String(item.itemId);
        select.setAttribute("aria-label", "Select " + item.title);
        row.appendChild(select);
        const copy = createElement("div", "admin-list-copy");
        copy.appendChild(createElement("strong", "", item.title));
        copy.appendChild(createElement("small", "", categoryName(item.categoryId) + " · " + item.type));
        if (item.editorPick) copy.appendChild(createElement("small", "editor-pick-label", "EDITOR PICK"));
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
    form.elements.difficulty.value = item.difficulty || "ALL_LEVELS";
    form.elements.estimatedMinutes.value = item.estimatedMinutes || "";
    form.elements.sourceName.value = item.sourceName || "";
    form.elements.externalUrl.value = item.externalUrl || "";
    form.elements.editorPick.checked = Boolean(item.editorPick);
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
    const forms = { login: "loginForm", register: "registerForm", forgot: "forgotPasswordForm", reset: "resetPasswordForm" };
    if (!forms[mode]) mode = "login";
    Object.values(forms).forEach(function (id) { document.getElementById(id).hidden = id !== forms[mode]; });
    const register = mode === "register";
    const recovery = mode === "forgot" || mode === "reset";
    document.querySelector(".auth-switch").hidden = recovery;
    document.getElementById("showLogin").classList.toggle("active", !register);
    document.getElementById("showLogin").setAttribute("aria-selected", String(!register));
    document.getElementById("showLogin").tabIndex = register ? -1 : 0;
    document.getElementById("showRegister").classList.toggle("active", register);
    document.getElementById("showRegister").setAttribute("aria-selected", String(register));
    document.getElementById("showRegister").tabIndex = register ? 0 : -1;
    const copy = {
        login: ["Good to see you again", "Sign in to pick up where your curiosity left off."],
        register: ["Start your RecomAI account", "Create your profile and start discovering what fits."],
        forgot: ["Reset your password", "Enter your email and we’ll send reset instructions if an account matches."],
        reset: ["Choose a new password", "Use a new password you have not used here before."]
    }[mode];
    document.getElementById("authTitle").textContent = copy[0];
    document.getElementById("authSubtitle").textContent = copy[1];
    showAuthMessage("");
}

function openAuth(mode) {
    previousFocus = document.activeElement;
    appShell.hidden = true;
    landingPage.hidden = false;
    authPanel.hidden = false;
    document.body.classList.add("dialog-open");
    setAuthMode(mode || "login");
    const formId = { login: "loginForm", register: "registerForm", forgot: "forgotPasswordForm", reset: "resetPasswordForm" }[mode || "login"];
    const input = document.querySelector("#" + formId + " input");
    if (input) {
        window.setTimeout(function () { input.focus(); }, 0);
    }
}

function closeAuth() {
    if (authRequestInProgress) return;
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
    const input = document.getElementById("discoveryQuery");
    const query = input.value.trim();
    const message = document.getElementById("discoveryMessage");
    message.textContent = "";
    if (!query) {
        message.textContent = "Enter a topic or keyword to search the resource catalog.";
        input.focus();
        return;
    }
    if (query.length > 120) {
        message.textContent = "Search text must be 120 characters or fewer.";
        input.focus();
        return;
    }
    pendingHomepageQuery = query;
    if (currentUser && onboardingCompleted) {
        processPendingHomepageSearch();
        return;
    }
    if (currentUser) {
        showPanel("onboardingPanel");
        showToast("Your search is kept until you finish the quick-start profile.");
        return;
    }
    openAuth("register");
    showAuthMessage("We’ll keep “" + query + "” while you create your account and finish the quick start.");
});

document.getElementById("showLogin").addEventListener("click", function () {
    setAuthMode("login");
});

document.getElementById("showRegister").addEventListener("click", function () {
    setAuthMode("register");
});

document.getElementById("duplicateRegistrationAction").addEventListener("click", function () {
    setAuthMode("login");
    const loginEmail = document.querySelector("#loginForm [name=email]");
    loginEmail.value = document.querySelector("#registerForm [name=email]").value.trim();
    loginEmail.focus();
});
document.getElementById("forgotPasswordLink").addEventListener("click", function () {
    const email = document.querySelector("#loginForm [name=email]").value.trim();
    setAuthMode("forgot");
    document.querySelector("#forgotPasswordForm [name=email]").value = email;
    document.querySelector("#forgotPasswordForm [name=email]").focus();
});
document.getElementById("backToSignInFromForgot").addEventListener("click", function () { setAuthMode("login"); });
document.getElementById("backToSignInFromReset").addEventListener("click", function () { passwordResetToken = null; setAuthMode("login"); });
document.getElementById("requestAnotherReset").addEventListener("click", function () {
    passwordResetToken = null;
    setAuthMode("forgot");
});
document.querySelector(".auth-switch").addEventListener("keydown", function (event) {
    if (authRequestInProgress) return;
    const tabs = [document.getElementById("showLogin"), document.getElementById("showRegister")];
    const current = tabs.indexOf(document.activeElement);
    if (current < 0) return;
    let next = current;
    if (event.key === "ArrowRight" || event.key === "ArrowDown") next = (current + 1) % tabs.length;
    else if (event.key === "ArrowLeft" || event.key === "ArrowUp") next = (current + tabs.length - 1) % tabs.length;
    else if (event.key === "Home") next = 0;
    else if (event.key === "End") next = tabs.length - 1;
    else return;
    event.preventDefault();
    setAuthMode(next === 0 ? "login" : "register");
    tabs[next].focus();
});

document.getElementById("loginForm").addEventListener("submit", async function (event) {
    event.preventDefault();
    if (authRequestInProgress) return;
    showAuthMessage("");
    const submitButton = event.currentTarget.querySelector("[type=submit]");
    const data = new FormData(event.currentTarget);
    const credentials = { email: data.get("email"), password: data.get("password") };
    setAuthRequestState(true);
    try {
        const profile = await api("/auth/login", {
            method: "POST",
            body: JSON.stringify(credentials)
        });
        openDashboard(profile);
    } catch (error) {
        handleError(error, "auth");
    } finally {
        setAuthRequestState(false);
    }
});

document.getElementById("registerForm").addEventListener("submit", async function (event) {
    event.preventDefault();
    if (authRequestInProgress) return;
    showAuthMessage("");
    const form = event.currentTarget;
    const data = new FormData(form);
    const registration = {
        displayName: data.get("displayName"),
        username: data.get("username"),
        email: data.get("email"),
        password: data.get("password")
    };
    setAuthRequestState(true);
    let accountCreated = false;
    try {
        const profile = await api("/auth/register", { method: "POST", body: JSON.stringify(registration) });
        accountCreated = true;
        form.reset();
        if (!profile || !profile.username) {
            throw new Error("The server created your account but did not return its profile.");
        }
        openDashboard(profile);
    } catch (error) {
        if (accountCreated) {
            setAuthMode("login");
            const loginEmail = document.querySelector("#loginForm [name=email]");
            loginEmail.value = registration.email;
            showAuthMessage("Your account was created, but we couldn't finish signing you in. Please sign in to continue.", false);
            loginEmail.focus();
        } else if (error instanceof TypeError) {
            showAuthMessage("We couldn't confirm your registration. Check your connection and try again.", false);
        } else if (error && error.status === 409
                && error.message === "That username or email address is already registered.") {
            showAuthMessage("Those signup details are already in use. Sign in with your existing account, or try different details.", false);
            document.getElementById("duplicateRegistrationAction").hidden = false;
        } else {
            handleError(error, "auth");
        }
    } finally {
        setAuthRequestState(false);
    }
});

document.getElementById("forgotPasswordForm").addEventListener("submit", async function (event) {
    event.preventDefault();
    if (authRequestInProgress) return;
    showAuthMessage("");
    const email = new FormData(event.currentTarget).get("email");
    setAuthRequestState(true);
    try {
        const result = await api("/auth/password-reset/request", { method: "POST", body: JSON.stringify({ email }) });
        showAuthMessage(result.message, true);
    } catch (error) {
        handleError(error, "auth");
    } finally {
        setAuthRequestState(false);
    }
});

document.getElementById("resetPasswordForm").addEventListener("submit", async function (event) {
    event.preventDefault();
    if (authRequestInProgress) return;
    showAuthMessage("");
    const form = event.currentTarget;
    const data = new FormData(form);
    const password = data.get("password");
    const confirmPassword = data.get("confirmPassword");
    if (password !== confirmPassword) {
        showAuthMessage("The passwords do not match.");
        return;
    }
    if (!passwordResetToken) {
        showAuthMessage("This reset link is invalid or expired. Request a new password reset link.");
        return;
    }
    setAuthRequestState(true);
    try {
        const result = await api("/auth/password-reset/complete", {
            method: "POST", body: JSON.stringify({ token: passwordResetToken, password, confirmPassword })
        });
        passwordResetToken = null;
        form.reset();
        setAuthMode("login");
        showAuthMessage(result.message, true);
    } catch (error) {
        passwordResetToken = null;
        handleError(error, "auth");
    } finally {
        setAuthRequestState(false);
    }
});

document.getElementById("logoutButton").addEventListener("click", async function () {
    const button = document.getElementById("logoutButton");
    if (logoutInProgress) return;
    logoutInProgress = true;
    button.disabled = true;
    try {
        await api("/auth/logout", { method: "POST" });
    } catch (error) {
        showToast(error instanceof TypeError
            ? "We couldn't reach the server to confirm sign out. Your session status is unknown; retry when your connection returns."
            : "The server couldn't confirm sign out. Your session status is unknown; please retry.", true);
        return;
    } finally {
        logoutInProgress = false;
        button.disabled = false;
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

installPhaseTwoControls();
document.querySelectorAll(".nav-link").forEach(function (link) {
    link.addEventListener("click", function () { showPanel(link.dataset.panel); });
});
document.querySelectorAll("[data-go]").forEach(function (button) {
    button.addEventListener("click", function () { showPanel(button.dataset.go); });
});
document.getElementById("categoryFilter").addEventListener("change", function () {
    catalogPage = 1;
    renderCatalog(false);
});
document.getElementById("catalogSearchForm").addEventListener("submit", function (event) {
    event.preventDefault();
    catalogPage = 1;
    renderCatalog(true);
});
window.addEventListener("popstate", function () {
    if (!currentUser || !window.location.hash.startsWith("#app/")) return;
    showPanel(window.location.hash.slice("#app/".length), true);
});
document.getElementById("clearCatalogSearch").addEventListener("click", function () {
    document.getElementById("catalogSearchQuery").value = "";
    catalogPage = 1;
    renderCatalog(false);
});
document.getElementById("catalogPreviousPage").addEventListener("click", function () {
    if (catalogPage > 1) {
        catalogPage -= 1;
        renderCatalog(false);
    }
});
document.getElementById("catalogNextPage").addEventListener("click", function () {
    catalogPage += 1;
    renderCatalog(false);
});
document.getElementById("onboardingNiche").addEventListener("change", renderOnboardingTopics);
document.getElementById("refreshInterestProfile").addEventListener("click", loadInterestProfile);
document.getElementById("generateRecommendations").addEventListener("click", generateRecommendations);
document.getElementById("refreshActivity").addEventListener("click", loadActivity);
document.getElementById("clearHistoryButton").addEventListener("click", async function (event) {
    const button = event.currentTarget;
    if (!window.confirm("Clear your activity, searches, inferred topic signals, and saved recommendation batches? Your ratings and explicit preferences will remain.")) return;
    button.disabled = true;
    try {
        await api("/users/me/interactions", { method: "DELETE" });
        document.getElementById("activityList").replaceChildren();
        document.getElementById("activityEmpty").hidden = false;
        document.getElementById("recommendationList").replaceChildren();
        document.getElementById("recommendationsEmpty").hidden = false;
        await loadInterestProfile();
        showToast("Activity and search history cleared. Ratings and explicit preferences were kept.");
    } catch (error) {
        handleError(error);
    } finally {
        button.disabled = false;
    }
});

document.getElementById("deleteAccountButton").addEventListener("click", async function (event) {
    if (window.prompt("This permanently deletes your account and associated data. Type DELETE to confirm.") !== "DELETE") return;
    const button = event.currentTarget;
    button.disabled = true;
    try {
        await api("/users/me", { method: "DELETE" });
        currentUser = null;
        onboardingCompleted = false;
        feedbackByItemId.clear();
        feedbackLoaded = false;
        appShell.hidden = true;
        landingPage.hidden = false;
        authPanel.hidden = true;
        showToast("Your account and associated data were deleted.");
    } catch (error) {
        handleError(error);
    } finally {
        button.disabled = false;
    }
});
document.getElementById("refreshSaved").addEventListener("click", loadSavedItems);

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

document.getElementById("onboardingForm").addEventListener("submit", async function (event) {
    event.preventDefault();
    const selectedTopics = Array.from(document.querySelectorAll("#onboardingTopics input:checked"))
        .map(function (input) { return Number(input.value); });
    const button = event.currentTarget.querySelector("[type=submit]");
    button.disabled = true;
    try {
        await api("/users/me/onboarding", {
            method: "POST",
            body: JSON.stringify({
                purpose: document.getElementById("onboardingPurpose").value,
                categoryId: Number(document.getElementById("onboardingNiche").value),
                interestIds: selectedTopics
            })
        });
        onboardingCompleted = true;
        showToast("Your Explore feed is ready. We’ll learn from your activity over time.");
        await loadWorkspace();
        if (pendingHomepageQuery) {
            await processPendingHomepageSearch();
        } else {
            showPanel("recommendationsPanel");
            await generateRecommendations();
        }
    } catch (error) {
        handleError(error);
    } finally {
        button.disabled = false;
    }
});

document.querySelectorAll(".cancel-edit").forEach(function (button) {
    button.addEventListener("click", function () { resetForm(button.dataset.form); });
});

document.getElementById("catalogCsvFile").addEventListener("change", async function (event) {
    const file = event.currentTarget.files && event.currentTarget.files[0];
    if (!file) return;
    if (file.size > 1_000_000) {
        showCatalogImportMessage("CSV must be no larger than 1 MB.", true);
        event.currentTarget.value = "";
        return;
    }
    document.getElementById("catalogCsvText").value = await file.text();
    showCatalogImportMessage("File loaded. Preview it to validate every row.");
});

document.getElementById("previewCatalogCsv").addEventListener("click", previewCatalogCsv);
document.getElementById("commitCatalogCsv").addEventListener("click", commitCatalogCsv);
document.getElementById("bulkCatalogActive").addEventListener("click", bulkCatalogActive);

async function previewCatalogCsv() {
    const button = document.getElementById("previewCatalogCsv");
    button.disabled = true;
    document.getElementById("commitCatalogCsv").disabled = true;
    const results = document.getElementById("catalogCsvPreview");
    results.replaceChildren();
    try {
        const preview = await api("/admin/items/import/preview", {
            method: "POST", body: JSON.stringify({ csv: document.getElementById("catalogCsvText").value })
        });
        preview.rows.forEach(function (row) {
            const line = createElement("p", row.valid ? "csv-row-valid" : "csv-row-invalid");
            line.textContent = "Row " + row.line + " · " + (row.title || "Untitled") + ": "
                + (row.valid ? "ready" : row.errors.join(" "));
            results.appendChild(line);
        });
        showCatalogImportMessage(preview.validRows + " valid, " + preview.invalidRows + " invalid rows.", !preview.canImport);
        document.getElementById("commitCatalogCsv").disabled = !preview.canImport;
    } catch (error) {
        showCatalogImportMessage(error.message || "Could not validate this CSV.", true);
    } finally { button.disabled = false; }
}

async function commitCatalogCsv() {
    const button = document.getElementById("commitCatalogCsv");
    button.disabled = true;
    try {
        const result = await api("/admin/items/import/commit", {
            method: "POST", body: JSON.stringify({ csv: document.getElementById("catalogCsvText").value })
        });
        showCatalogImportMessage(result.imported + " resources imported; " + result.skippedExisting + " existing URLs skipped.");
        document.getElementById("catalogCsvPreview").replaceChildren();
        await refreshCatalogData();
        await loadAdminData();
    } catch (error) {
        showCatalogImportMessage(error.message || "Import failed. Review the CSV and try again.", true);
        button.disabled = false;
    }
}

function showCatalogImportMessage(message, isError) {
    const output = document.getElementById("catalogCsvMessage");
    output.textContent = message;
    output.classList.toggle("message-error", Boolean(isError));
}

async function bulkCatalogActive() {
    const ids = Array.from(document.querySelectorAll(".admin-item-select:checked"))
        .map(function (input) { return Number(input.value); });
    const active = document.getElementById("bulkCatalogState").value === "active";
    if (!ids.length) { showToast("Select at least one catalog item."); return; }
    const button = document.getElementById("bulkCatalogActive");
    button.disabled = true;
    try {
        const result = await api("/admin/items/active", {
            method: "PATCH", body: JSON.stringify({ itemIds: ids, active: active })
        });
        await refreshCatalogData();
        await loadAdminData();
        showToast(result.updated + " catalog items updated.");
    } catch (error) { handleError(error); }
    finally { button.disabled = false; }
}

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
        price: price === null || price === "" ? null : Number(price),
        sourceName: data.get("sourceName") || null,
        difficulty: data.get("difficulty"),
        estimatedMinutes: data.get("estimatedMinutes") === "" ? null : Number(data.get("estimatedMinutes")),
        qualityScore: 0.5,
        editorPick: Boolean(data.get("editorPick"))
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

function activateResetLink(fragment) {
    if (!fragment.startsWith("#reset-password?")) return false;
    const fragmentQuery = fragment.slice(fragment.indexOf("?") + 1);
    passwordResetToken = new URLSearchParams(fragmentQuery).get("token");
    window.history.replaceState(null, "", window.location.pathname + window.location.search + "#home");
    openAuth("reset");
    if (!passwordResetToken) showAuthMessage("This reset link is invalid or expired. Request a new password reset link.");
    return true;
}

document.getElementById("retrySessionCheck").addEventListener("click", restoreSession);
window.addEventListener("hashchange", function () {
    activateResetLink(window.location.hash);
});
if (!activateResetLink(window.location.hash)) {
    restoreSession();
}
initializeThemeToggle();

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
