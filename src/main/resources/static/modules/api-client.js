const API_BASE = "/api";

/** Shared same-origin JSON client for the session-authenticated application. */
export async function api(path, options = {}) {
    const settings = { credentials: "same-origin", ...options };
    settings.headers = { ...(settings.headers || {}) };
    if (settings.body !== undefined) settings.headers["Content-Type"] = "application/json";

    const response = await fetch(API_BASE + path, settings);
    if (response.status === 204) return null;

    const contentType = response.headers.get("content-type") || "";
    let data = null;
    if (contentType.includes("application/json")) {
        try {
            data = await response.json();
        } catch {
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
