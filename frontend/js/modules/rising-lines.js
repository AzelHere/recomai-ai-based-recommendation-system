const TAU = Math.PI * 2;

function random(seed) {
    const value = Math.sin(seed * 127.1 + 311.7) * 43758.5453123;
    return value - Math.floor(value);
}

function initializeCanvas(canvas) {
    const context = canvas.getContext("2d", { alpha: true });
    if (!context) return;

    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
    const interactionTarget = canvas.parentElement || canvas;
    const state = { width: 0, height: 0, dpr: 1, time: 0, pointerX: 0.5, pointerY: 0.5, pulse: 0, visible: false, frame: 0 };
    let lines = [];

    function resize() {
        const bounds = canvas.getBoundingClientRect();
        state.width = Math.max(1, bounds.width);
        state.height = Math.max(1, bounds.height);
        state.dpr = Math.min(window.devicePixelRatio || 1, 1.5);
        canvas.width = Math.round(state.width * state.dpr);
        canvas.height = Math.round(state.height * state.dpr);
        context.setTransform(state.dpr, 0, 0, state.dpr, 0, 0);
        const count = Math.max(26, Math.min(76, Math.round(state.width / 23)));
        lines = Array.from({ length: count }, (_, index) => ({
            origin: (index + random(index + 1) * 0.8) / count,
            depth: random(index + 91),
            phase: random(index + 211) * TAU,
            speed: 0.055 + random(index + 401) * 0.09,
            sway: 0.004 + random(index + 611) * 0.013,
            width: 0.45 + random(index + 731) * 0.75
        }));
        draw(0);
    }

    function draw(delta) {
        const { width, height, time } = state;
        if (!width || !height) return;
        context.clearRect(0, 0, width, height);
        const horizonY = height * 0.7;
        const glow = context.createRadialGradient(width * 0.52, horizonY, 0, width * 0.52, horizonY, width * 0.52);
        glow.addColorStop(0, "rgba(255,120,73,0.18)");
        glow.addColorStop(0.35, "rgba(255,120,73,0.055)");
        glow.addColorStop(1, "rgba(255,120,73,0)");
        context.fillStyle = glow;
        context.fillRect(0, horizonY - height * 0.28, width, height * 0.58);

        context.save();
        context.translate(width * (0.5 + (state.pointerX - 0.5) * 0.018), 0);
        context.strokeStyle = "rgba(255,147,108,0.28)";
        context.lineWidth = 1;
        context.beginPath();
        context.moveTo(-width * 0.03, horizonY);
        context.quadraticCurveTo(0, horizonY - 2, width * 0.5, horizonY);
        context.quadraticCurveTo(width, horizonY + 2, width * 1.03, horizonY);
        context.stroke();

        for (let strand = 0; strand < 3; strand += 1) {
            const depth = 0.28 + strand * 0.085;
            const drift = state.pointerX * (strand + 1) * 2;
            const gradient = context.createLinearGradient(0, height * depth, width, height * depth);
            gradient.addColorStop(0, "rgba(255,120,73,0)");
            gradient.addColorStop(0.24, `rgba(255,147,108,${0.045 + strand * 0.012})`);
            gradient.addColorStop(0.53, `rgba(255,226,208,${0.12 - strand * 0.02})`);
            gradient.addColorStop(0.82, `rgba(255,120,73,${0.04 + strand * 0.01})`);
            gradient.addColorStop(1, "rgba(255,120,73,0)");
            context.beginPath();
            context.moveTo(-width * 0.04, height * (depth + 0.03));
            context.bezierCurveTo(width * 0.22, height * (depth - 0.065 + drift * 0.002), width * 0.42, height * (depth + 0.08), width * 0.68, height * (depth - 0.035));
            context.bezierCurveTo(width * 0.81, height * (depth - 0.09), width * 0.92, height * (depth + 0.035), width * 1.04, height * (depth - 0.02));
            context.strokeStyle = gradient;
            context.lineWidth = strand === 1 ? 1.2 : 0.8;
            context.shadowColor = "rgba(255,120,73,0.55)";
            context.shadowBlur = 8;
            context.stroke();
        }
        context.shadowBlur = 0;

        for (const line of lines) {
            const progress = (line.depth + time * line.speed) % 1;
            const heightFactor = 0.08 + progress * 0.83;
            const perspective = 0.08 + progress * 1.32;
            const lane = (line.origin - 0.5) * width;
            const influence = Math.exp(-Math.pow((line.origin - state.pointerX) / 0.12, 2)) * (1 - state.pointerY * 0.4);
            const sway = Math.sin(time * 0.8 + line.phase) * line.sway * width * perspective;
            const x = lane * perspective + sway;
            const y = horizonY - height * heightFactor;
            const tail = 17 + progress * Math.min(84, height * 0.13);
            const alpha = (0.1 + progress * 0.48) * (0.72 + influence * 0.55);

            const gradient = context.createLinearGradient(x, y, x - sway * 1.7, y + tail);
            gradient.addColorStop(0, `rgba(255,242,232,${Math.min(0.88, alpha + 0.14)})`);
            gradient.addColorStop(0.19, `rgba(255,147,108,${alpha})`);
            gradient.addColorStop(1, "rgba(255,120,73,0)");
            context.beginPath();
            context.moveTo(x, y);
            context.bezierCurveTo(x - sway, y + tail * 0.35, x + sway * 1.4, y + tail * 0.7, x + sway, y + tail);
            context.strokeStyle = gradient;
            context.lineWidth = line.width * (0.45 + progress * 0.75);
            context.shadowColor = "rgba(255,120,73,0.62)";
            context.shadowBlur = 3 + influence * 5;
            context.stroke();
            context.shadowBlur = 0;
            if (line.depth > 0.8 && progress > 0.84) {
                context.fillStyle = `rgba(255,207,184,${(progress - 0.84) * 1.5})`;
                context.beginPath();
                context.arc(x, y, 0.7 + influence, 0, TAU);
                context.fill();
            }
        }
        context.restore();

        if (state.pulse > 0) {
            const pulseRadius = (1 - state.pulse) * width * 0.28;
            context.strokeStyle = `rgba(255,147,108,${state.pulse * 0.2})`;
            context.lineWidth = 1;
            context.beginPath();
            context.ellipse(width * state.pointerX, horizonY, pulseRadius, pulseRadius * 0.13, 0, 0, TAU);
            context.stroke();
            state.pulse = Math.max(0, state.pulse - delta * 0.45);
        }
    }

    function tick(now) {
        state.frame = 0;
        if (!state.visible || document.hidden) return;
        const delta = Math.min(0.05, state.last ? (now - state.last) / 1000 : 1 / 60);
        state.last = now;
        if (!reducedMotion.matches) state.time += delta;
        draw(delta);
        if (!reducedMotion.matches || state.pulse > 0) state.frame = requestAnimationFrame(tick);
    }

    function wake() {
        if (state.visible && !document.hidden && !state.frame) state.frame = requestAnimationFrame(tick);
    }

    const observer = new IntersectionObserver(entries => {
        state.visible = entries.some(entry => entry.isIntersecting);
        state.last = 0;
        if (state.visible) wake();
        else if (state.frame) cancelAnimationFrame(state.frame);
        state.frame = 0;
    }, { threshold: 0.01 });
    observer.observe(canvas);
    const resizeObserver = new ResizeObserver(resize);
    resizeObserver.observe(canvas);
    interactionTarget.addEventListener("pointermove", event => {
        const bounds = canvas.getBoundingClientRect();
        state.pointerX = Math.max(0, Math.min(1, (event.clientX - bounds.left) / bounds.width));
        state.pointerY = Math.max(0, Math.min(1, (event.clientY - bounds.top) / bounds.height));
        wake();
    }, { passive: true });
    interactionTarget.addEventListener("pointerleave", () => { state.pointerX = 0.5; state.pointerY = 0.5; wake(); });
    interactionTarget.addEventListener("pointerdown", event => {
        if (event.target instanceof Element && event.target.closest("a,button,input,select,textarea,[role='button']")) return;
        state.pulse = 1;
        wake();
    });
    document.addEventListener("visibilitychange", wake);
    reducedMotion.addEventListener("change", () => { state.last = 0; wake(); });
    resize();
}

export function initializeRisingLines() {
    document.querySelectorAll("canvas[data-rising-lines]").forEach(initializeCanvas);
}
