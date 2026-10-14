const STORAGE_KEY = "recomai-theme";

export function initializeThemeToggle() {
    const saved = localStorage.getItem(STORAGE_KEY);
    const initialTheme = saved === "light" ? "light" : "dark";
    document.documentElement.dataset.theme = initialTheme;
    const button = document.getElementById("themeToggle");
    if (!button) return;

    const update = (theme) => {
        document.documentElement.dataset.theme = theme;
        button.textContent = theme === "dark" ? "Light mode" : "Dark mode";
        button.setAttribute("aria-pressed", String(theme === "light"));
        localStorage.setItem(STORAGE_KEY, theme);
    };
    update(initialTheme);
    button.addEventListener("click", () => update(
        document.documentElement.dataset.theme === "dark" ? "light" : "dark"));
}
