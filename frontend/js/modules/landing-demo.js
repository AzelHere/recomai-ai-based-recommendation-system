import { createResourceArtwork } from "./resource-artwork.js";

const samples = {
    programming: [
        { title: "Build your first Java API", description: "Practice routing, validation, and clean service boundaries.", category: "Programming", type: "COURSE", tags: ["Java", "Backend"] },
        { title: "Python, one useful project at a time", description: "Turn core language concepts into small working tools.", category: "Programming", type: "GUIDE", tags: ["Python", "Projects"] },
        { title: "Understand relational data", description: "Model tables and query information with confidence.", category: "Programming", type: "DOCUMENTATION", tags: ["SQL", "Databases"] }
    ],
    data: [
        { title: "Ask better questions with SQL", description: "Explore joins, grouping, and useful analytical queries.", category: "Data", type: "GUIDE", tags: ["SQL", "Analysis"] },
        { title: "Explore a dataset with Python", description: "Move from a raw table to a clear, useful finding.", category: "Data", type: "COURSE", tags: ["Python", "Visualization"] },
        { title: "Make metrics easier to read", description: "Choose charts that make patterns and changes visible.", category: "Data", type: "RESOURCE", tags: ["Charts", "Communication"] }
    ],
    design: [
        { title: "Map a clearer user journey", description: "Find friction and make each step easier to understand.", category: "Design", type: "GUIDE", tags: ["UX", "Research"] },
        { title: "Build a flexible interface system", description: "Use reusable patterns to keep product screens consistent.", category: "Design", type: "COURSE", tags: ["UI", "Components"] },
        { title: "Design for readable contrast", description: "Make interfaces easier to scan for more people.", category: "Design", type: "RESOURCE", tags: ["Accessibility", "Visual design"] }
    ]
};

function renderCard(item, index) {
    const card = document.createElement("article");
    card.className = "demo-resource-card";
    const artwork = createResourceArtwork(item, item.category);
    const visual = document.createElement("div");
    visual.className = `demo-resource-art art-${artwork.key}`;
    visual.setAttribute("aria-hidden", "true");
    visual.appendChild(artwork.element);
    const copy = document.createElement("div");
    copy.className = "demo-resource-copy";
    const number = document.createElement("span");
    number.className = "demo-resource-number";
    number.textContent = `0${index + 1} / EXAMPLE`;
    const title = document.createElement("h4");
    title.textContent = item.title;
    const description = document.createElement("p");
    description.textContent = item.description;
    const tags = document.createElement("div");
    tags.className = "demo-resource-tags";
    item.tags.forEach(tag => {
        const chip = document.createElement("span");
        chip.textContent = tag;
        tags.appendChild(chip);
    });
    copy.append(number, title, description, tags);
    card.append(visual, copy);
    return card;
}

export function initializeLandingDemo() {
    const buttons = Array.from(document.querySelectorAll("[data-demo-interest]"));
    const grid = document.getElementById("demoResourceGrid");
    const title = document.getElementById("demoFeedTitle");
    const previewArt = document.querySelector("[data-demo-preview-art]");
    const previewTitle = document.querySelector("[data-demo-preview-title]");
    const previewCopy = document.querySelector("[data-demo-preview-copy]");
    const previewMatch = document.querySelector("[data-demo-preview-match]");
    const previewTopic = document.querySelector("[data-demo-preview-topic]");
    if (!buttons.length || !grid || !title) return;
    let featuredInterest = buttons[0].dataset.demoInterest;

    function update() {
        const selected = buttons.filter(button => button.getAttribute("aria-pressed") === "true");
        if (!selected.length) {
            buttons[0].setAttribute("aria-pressed", "true");
            selected.push(buttons[0]);
        }
        const interests = selected.map(button => button.dataset.demoInterest);
        const labels = selected.map(button => button.textContent.trim());
        if (!interests.includes(featuredInterest)) featuredInterest = interests[0];
        const orderedInterests = [featuredInterest, ...interests.filter(interest => interest !== featuredInterest)];
        const groups = orderedInterests.map(interest => samples[interest] || []);
        const featured = groups[0]?.[0];
        const cards = [];
        for (let index = 0; index < 3 && cards.length < 3; index += 1) {
            groups.forEach(group => { if (group[index] && cards.length < 3) cards.push(group[index]); });
        }
        title.textContent = labels.length === 1
            ? `A starting point for ${labels[0].toLowerCase()}`
            : `A mix shaped by ${labels.join(" + ")}`;
        if (featured && previewArt && previewTitle && previewCopy && previewMatch && previewTopic) {
            previewTitle.textContent = featured.title;
            previewCopy.textContent = featured.description;
            previewMatch.textContent = `SAMPLE MATCH · ${featured.category.toUpperCase()}`;
            previewTopic.textContent = `${featured.tags.slice(0, 2).join(" · ").toUpperCase()}`;
            const artwork = createResourceArtwork(featured, featured.category);
            const topic = previewTopic;
            previewArt.replaceChildren(artwork.element, topic);
        }
        grid.replaceChildren(...cards.map(renderCard));
    }

    buttons.forEach(button => button.addEventListener("click", () => {
        const selected = button.getAttribute("aria-pressed") === "true";
        button.setAttribute("aria-pressed", String(!selected));
        if (!selected) featuredInterest = button.dataset.demoInterest;
        else if (featuredInterest === button.dataset.demoInterest) {
            featuredInterest = buttons.find(candidate => candidate !== button && candidate.getAttribute("aria-pressed") === "true")?.dataset.demoInterest || "";
        }
        update();
    }));
    update();
}
