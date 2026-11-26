import { createResourceArtwork } from "./resource-artwork.js";

let selectedItemId = null;

function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
}

function itemFor(recommendation, items) {
    return items.find(item => Number(item.itemId) === Number(recommendation.itemId));
}

export function renderRecommendationWorkspace({ stage, recommendations, items, categories, user, evidenceNote, impressionObserver, onNavigate, onRefresh, onAction, onOpen, onRatingControl, onReturnHome }) {
    stage.replaceChildren();
    stage.hidden = recommendations.length === 0;
    if (!recommendations.length) return;

    const workspace = element("div", "recommendation-workspace");
    const home = element("button", "workspace-home", "×");
    home.type = "button";
    home.setAttribute("aria-label", "Return to home");
    home.title = "Return to home";
    home.addEventListener("click", onReturnHome);
    workspace.appendChild(home);
    const left = element("aside", "workspace-left");
    const brand = element("div", "workspace-brand", "✳  Recom AI");
    left.appendChild(brand);
    left.appendChild(element("p", "workspace-label", "WORKSPACE"));
    const navigation = element("nav", "workspace-nav");
    navigation.setAttribute("aria-label", "Workspace navigation");
    const destinations = [
        ["recommendationsPanel", "✦", "For You"], ["catalogPanel", "⌕", "Explore"],
        ["savedPanel", "♡", "Saved"], ["interestProfilePanel", "◈", "My Taste"],
        ["preferencesPanel", "☷", "Category preferences"], ["progressPanel", "◷", "Progress"],
        ["activityPanel", "↗", "Activity"], ["howWorksPanel", "ⓘ", "How it works"],
        ["discoverPanel", "◉", "Profile"]
    ];
    if (user?.role === "ADMIN") destinations.push(["adminPanel", "⚙", "Admin"]);
    destinations.forEach(([panel, icon, label]) => {
        const button = element("button", "workspace-nav-link" + (panel === "recommendationsPanel" ? " active" : ""), icon + "  " + label);
        button.type = "button";
        if (panel === "recommendationsPanel") button.setAttribute("aria-current", "page");
        button.addEventListener("click", () => onNavigate(panel));
        navigation.appendChild(button);
    });
    left.appendChild(navigation);
    const filterSection = element("section", "workspace-filters");
    filterSection.appendChild(element("p", "workspace-label", "REFINE PICKS"));
    const searchLabel = element("label", "workspace-search-label", "Search recommendations");
    const search = element("input", "workspace-search");
    search.id = "workspaceSearch";
    search.type = "search";
    search.placeholder = "Title, topic, source…";
    searchLabel.appendChild(search);
    filterSection.appendChild(searchLabel);
    const categoryList = element("div", "workspace-filter-list");
    categoryList.setAttribute("aria-label", "Interest filters");
    filterSection.appendChild(categoryList);
    left.appendChild(filterSection);
    const refresh = element("button", "workspace-refresh", "↻  Refresh recommendations");
    refresh.type = "button";
    refresh.addEventListener("click", onRefresh);
    left.appendChild(refresh);

    const center = element("section", "workspace-center");
    const centerHeader = element("div", "workspace-center-header");
    const titleGroup = element("div", "");
    titleGroup.append(element("p", "workspace-label", "PERSONALIZED SELECTION"), element("h2", "", "For You"));
    const carouselControls = element("div", "workspace-carousel-controls");
    const previous = element("button", "workspace-arrow", "←");
    previous.type = "button";
    previous.setAttribute("aria-label", "Previous recommendation");
    const count = element("span", "workspace-count");
    count.setAttribute("aria-live", "polite");
    const next = element("button", "workspace-arrow", "→");
    next.type = "button";
    next.setAttribute("aria-label", "Next recommendation");
    carouselControls.append(previous, count, next);
    centerHeader.append(titleGroup, carouselControls);
    center.appendChild(centerHeader);
    const orbit = element("div", "workspace-orbit");
    orbit.tabIndex = 0;
    orbit.setAttribute("aria-label", "Recommendation carousel. Use left and right arrow keys to browse.");
    center.appendChild(orbit);
    center.appendChild(element("p", "workspace-hint", "Drag or use ← → to browse · Select a card for details"));

    const right = element("aside", "workspace-right");
    right.setAttribute("aria-label", "Selected resource details");
    const strip = element("section", "workspace-strip");
    const stripHeading = element("div", "workspace-strip-heading");
    stripHeading.appendChild(element("span", "", "MORE TO DISCOVER"));
    const stripControls = element("div", "workspace-strip-controls");
    const stripPrevious = element("button", "workspace-strip-arrow", "←");
    stripPrevious.type = "button";
    stripPrevious.setAttribute("aria-label", "Browse earlier resources");
    const stripNext = element("button", "workspace-strip-arrow", "→");
    stripNext.type = "button";
    stripNext.setAttribute("aria-label", "Browse more resources");
    stripControls.append(stripPrevious, stripNext);
    stripHeading.appendChild(stripControls);
    strip.appendChild(stripHeading);
    const stripList = element("div", "workspace-strip-list");
    stripList.setAttribute("aria-label", "Additional recommendations");
    strip.appendChild(stripList);
    const stripScrollBehavior = window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth";
    stripPrevious.addEventListener("click", () => stripList.scrollBy({ left: -380, behavior: stripScrollBehavior }));
    stripNext.addEventListener("click", () => stripList.scrollBy({ left: 380, behavior: stripScrollBehavior }));
    workspace.append(left, center, right, strip);
    stage.appendChild(workspace);

    let activeCategory = null;
    let visible = recommendations.slice();
    let selected = 0;
    let suppressCardClick = false;
    const cards = new Map();

    function select(index) {
        if (!visible.length) return;
        selected = (index + visible.length) % visible.length;
        selectedItemId = visible[selected].itemId;
        cards.forEach(card => {
            if (impressionObserver) impressionObserver.unobserve(card);
        });
        visible.forEach((recommendation, index) => {
            const card = cards.get(recommendation);
            const distance = (index - selected + visible.length) % visible.length;
            const position = index === selected ? "active" : distance === 1 ? "next"
                : distance === visible.length - 1 ? "previous" : "hidden";
            card.dataset.position = position;
            card.setAttribute("aria-hidden", String(position === "hidden"));
            card.tabIndex = position === "hidden" ? -1 : 0;
            if (position === "active" && impressionObserver) impressionObserver.observe(card);
        });
        stripList.querySelectorAll("button").forEach((button, index) => {
            button.classList.toggle("active", index === selected);
            button.setAttribute("aria-pressed", String(index === selected));
        });
        count.textContent = `${selected + 1} / ${visible.length}`;
        previous.disabled = next.disabled = visible.length < 2;
        showDetails(visible[selected]);
    }

    function showDetails(recommendation) {
        right.replaceChildren();
        const body = element("div", "workspace-detail-body");
        const item = itemFor(recommendation, items);
        const category = categories.find(entry => Number(entry.categoryId) === Number(item?.categoryId));
        body.appendChild(element("p", "workspace-label", "SELECTED RESOURCE"));
        body.appendChild(element("h3", "workspace-detail-title", recommendation.itemTitle));
        body.appendChild(element("p", "workspace-detail-meta", [category?.name, item?.type,
            item?.sourceName, item?.estimatedMinutes ? `${item.estimatedMinutes} min` : null].filter(Boolean).join(" · ")));
        if (item?.description) body.appendChild(element("p", "workspace-detail-description", item.description));
        if (item?.tags?.length) body.appendChild(element("p", "workspace-detail-tags", "Topics: " + item.tags.join(", ")));
        const explanation = element("section", "workspace-explanation");
        explanation.append(element("h4", "", "Why this pick"),
            element("p", "", recommendation.reason || "Selected from your current interests and activity."));
        const signals = Object.entries(recommendation.scoreBreakdown || {}).filter(([, points]) => Number(points) !== 0);
        if (signals.length) {
            const details = element("details", "workspace-signals");
            details.open = true;
            details.appendChild(element("summary", "", "View ranking signals"));
            const list = element("ul", "");
            signals.forEach(([name, points]) => list.appendChild(element("li", "", `${name.replaceAll("_", " ")}: ${Number(points) > 0 ? "+" : ""}${Number(points).toFixed(2)}`)));
            details.appendChild(list);
            explanation.appendChild(details);
        }
        body.appendChild(explanation);
        if (evidenceNote) body.appendChild(element("p", "workspace-evidence", evidenceNote));
        body.appendChild(onRatingControl(recommendation.itemId));
        const actions = element("div", "workspace-detail-actions");
        if (item?.externalUrl) {
            const open = element("a", "workspace-open", item.type === "ARTICLE" ? "Read Article ↗" : "Open resource ↗");
            open.href = item.externalUrl;
            open.target = "_blank";
            open.rel = "noopener noreferrer";
            open.addEventListener("click", () => onOpen(recommendation, item));
            actions.appendChild(open);
        }
        [["SAVE", "Save"], ["LIKE", "Like"], ["DISLIKE", "Not interested"],
            ["MORE_LIKE", "More like this"], ["HIDE", "Hide"]].forEach(([kind, label]) => {
            const button = element("button", "workspace-action", label);
            button.type = "button";
            button.addEventListener("click", () => onAction(recommendation.itemId, kind, button));
            actions.appendChild(button);
        });
        right.append(body, actions);
    }

    function rebuild() {
        const query = search.value.trim().toLowerCase();
        visible = recommendations.filter(recommendation => {
            const item = itemFor(recommendation, items);
            if (activeCategory !== null && Number(item?.categoryId) !== activeCategory) return false;
            return !query || [recommendation.itemTitle, item?.description, item?.sourceName,
                ...(item?.tags || [])].filter(Boolean).join(" ").toLowerCase().includes(query);
        });
        cards.forEach(card => { if (impressionObserver) impressionObserver.unobserve(card); });
        orbit.replaceChildren();
        stripList.replaceChildren();
        cards.clear();
        if (!visible.length) {
            orbit.appendChild(element("p", "workspace-no-results", "No picks match these filters. Try another interest or search."));
            right.replaceChildren(element("p", "workspace-no-results", "Choose another filter to inspect a resource."));
            count.textContent = "0 / 0";
            previous.disabled = next.disabled = true;
            return;
        }
        visible.forEach((recommendation, index) => {
            const item = itemFor(recommendation, items);
            const category = categories.find(entry => Number(entry.categoryId) === Number(item?.categoryId));
            const card = element("button", "workspace-card");
            card.type = "button";
            if (recommendation.recommendationId) card.dataset.recommendationId = recommendation.recommendationId;
            const artwork = createResourceArtwork(item, category?.name || "Resource");
            const art = element("span", "workspace-card-art art-" + artwork.key);
            art.appendChild(artwork.element);
            card.append(element("span", "workspace-card-index", `${String(index + 1).padStart(2, "0")} / ${String(visible.length).padStart(2, "0")}`),
                element("span", "workspace-card-category", [category?.name, item?.type].filter(Boolean).join(" · ")),
                art,
                element("strong", "workspace-card-title", recommendation.itemTitle),
                element("span", "workspace-card-description", item?.description || "Open the details panel to learn more about this resource."),
                element("span", "workspace-card-reason", recommendation.reason || "Chosen for your profile."));
            card.addEventListener("click", () => {
                if (suppressCardClick) { suppressCardClick = false; return; }
                select(index);
            });
            orbit.appendChild(card);
            cards.set(recommendation, card);
            const thumbnail = element("button", "workspace-strip-item");
            thumbnail.type = "button";
            thumbnail.append(element("small", "", `${String(index + 1).padStart(2, "0")}  ${category?.name || "Resource"}`),
                element("strong", "", recommendation.itemTitle));
            thumbnail.addEventListener("click", () => select(index));
            stripList.appendChild(thumbnail);
        });
        const remembered = visible.findIndex(entry => Number(entry.itemId) === Number(selectedItemId));
        select(remembered >= 0 ? remembered : 0);
    }

    const filterOptions = [{ id: null, name: "All interests" }, ...categories.filter(category =>
        recommendations.some(recommendation => Number(itemFor(recommendation, items)?.categoryId) === Number(category.categoryId)))
        .map(category => ({ id: Number(category.categoryId), name: category.name }))];
    filterOptions.forEach(option => {
        const button = element("button", "workspace-filter", option.name);
        button.type = "button";
        button.setAttribute("aria-pressed", String(option.id === activeCategory));
        button.addEventListener("click", () => {
            activeCategory = option.id;
            categoryList.querySelectorAll("button").forEach(entry => entry.setAttribute("aria-pressed", String(entry === button)));
            rebuild();
        });
        categoryList.appendChild(button);
    });
    search.addEventListener("input", rebuild);
    previous.addEventListener("click", () => select(selected - 1));
    next.addEventListener("click", () => select(selected + 1));
    orbit.addEventListener("keydown", event => {
        if (event.key === "ArrowLeft" || event.key === "ArrowRight") {
            event.preventDefault();
            select(selected + (event.key === "ArrowRight" ? 1 : -1));
        }
    });
    let dragX = null;
    orbit.addEventListener("pointerdown", event => { if (event.pointerType === "mouse" && event.button === 0) dragX = event.clientX; });
    orbit.addEventListener("pointerup", event => {
        if (dragX === null) return;
        const delta = event.clientX - dragX;
        dragX = null;
        if (Math.abs(delta) > 45) {
            suppressCardClick = true;
            select(selected + (delta < 0 ? 1 : -1));
            setTimeout(() => { suppressCardClick = false; }, 0);
        }
    });
    orbit.addEventListener("pointercancel", () => { dragX = null; });
    if (!window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
        let frame = 0;
        orbit.addEventListener("pointermove", event => {
            if (frame) return;
            frame = requestAnimationFrame(() => {
                const bounds = orbit.getBoundingClientRect();
                orbit.style.setProperty("--pointer-x", `${event.clientX - bounds.left}px`);
                orbit.style.setProperty("--pointer-y", `${event.clientY - bounds.top}px`);
                const active = orbit.querySelector('.workspace-card[data-position="active"]');
                if (active) {
                    active.style.setProperty("--rotate-y", `${((event.clientX - bounds.left) / bounds.width - .5) * 8}deg`);
                    active.style.setProperty("--rotate-x", `${(.5 - (event.clientY - bounds.top) / bounds.height) * 6}deg`);
                    const cardBounds = active.getBoundingClientRect();
                    active.style.setProperty("--spot-x", `${event.clientX - cardBounds.left}px`);
                    active.style.setProperty("--spot-y", `${event.clientY - cardBounds.top}px`);
                }
                frame = 0;
            });
        });
        orbit.addEventListener("pointerleave", () => orbit.querySelectorAll(".workspace-card").forEach(card => {
            card.style.removeProperty("--rotate-x");
            card.style.removeProperty("--rotate-y");
            card.style.removeProperty("--spot-x");
            card.style.removeProperty("--spot-y");
        }));
    }
    rebuild();
}
