import * as THREE from "../vendor/three.module.js";

const palette = ["#c98765", "#b7c3cf", "#d8c7b7", "#c2c9d3", "#8e9eaf"];

function makeLabel(text, color) {
    const canvas = document.createElement("canvas");
    canvas.width = 512;
    canvas.height = 96;
    const context = canvas.getContext("2d");
    context.font = "500 27px Arial, sans-serif";
    context.fillStyle = color;
    context.textAlign = "center";
    context.fillText(text.toUpperCase(), 256, 58);
    const texture = new THREE.CanvasTexture(canvas);
    const material = new THREE.SpriteMaterial({ map: texture, transparent: true, depthWrite: false });
    const sprite = new THREE.Sprite(material);
    sprite.scale.set(2.2, 0.42, 1);
    return sprite;
}

function createFallback(canvas) {
    canvas.hidden = true;
    canvas.parentElement.classList.add("constellation-static");
}

function initialize(canvas) {
    const host = canvas.parentElement;
    const lifecycle = new AbortController();
    const listen = (target,type,handler) => target.addEventListener(type,handler,{signal:lifecycle.signal});
    let disposed = false;
    const reducedMotion = matchMedia("(prefers-reduced-motion: reduce)");
    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(34, 1, 0.1, 100);
    camera.position.set(0, 0, 9.5);
    let renderer;
    try {
        renderer = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: true, powerPreference: "low-power" });
    } catch {
        createFallback(canvas);
        return;
    }
    renderer.setPixelRatio(Math.min(devicePixelRatio || 1, 1.5));
    renderer.setClearColor(0x000000, 0);

    const root = new THREE.Group();
    scene.add(root);
    const core = new THREE.Mesh(new THREE.SphereGeometry(1.95, 36, 24), new THREE.MeshBasicMaterial({
        color: 0xc98765, wireframe: true, transparent: true, opacity: 0.14
    }));
    root.add(core);
    const halo = new THREE.Mesh(new THREE.TorusGeometry(2.55, 0.008, 5, 120), new THREE.MeshBasicMaterial({ color: 0xb7c3cf, transparent: true, opacity: 0.42 }));
    halo.rotation.set(0.88, 0.28, 0.3);
    root.add(halo);

    const fields = [
        ["Technology", -2.25, 0.9, 0.12],
        ["Business & Finance", 1.9, 1.52, -0.1],
        ["Learning & Career", 2.15, -0.92, 0.24],
        ["Health & Fitness", -0.08, -2.02, -0.15],
        ["Creative & Lifestyle", -2.13, -0.88, 0.14]
    ];
    const nodes = [];
    fields.forEach(([name, x, y, z], index) => {
        const node = new THREE.Group();
        node.position.set(x, y, z);
        const radius = index === 0 ? 0.105 : 0.078;
        const sphere = new THREE.Mesh(new THREE.SphereGeometry(radius, 16, 12), new THREE.MeshBasicMaterial({ color: palette[index] }));
        node.add(sphere);
        const ring = new THREE.Mesh(new THREE.TorusGeometry(radius * 2.05, 0.007, 4, 28), new THREE.MeshBasicMaterial({ color: palette[index], transparent: true, opacity: 0.72 }));
        ring.rotation.x = 0.6;
        node.add(ring);
        const label = makeLabel(name, palette[index]);
        label.position.set(0, 0.28, 0);
        node.add(label);
        root.add(node);
        nodes.push(node);
    });
    const linkMaterial = new THREE.LineBasicMaterial({ color: 0xb7c3cf, transparent: true, opacity: 0.24 });
    const linkPairs = [[0, 1], [1, 2], [2, 3], [3, 4], [4, 0], [0, 2], [1, 3]];
    linkPairs.forEach(([a, b]) => {
        const points = [nodes[a].position, nodes[b].position];
        root.add(new THREE.Line(new THREE.BufferGeometry().setFromPoints(points), linkMaterial));
    });
    const starPositions = new Float32Array(180 * 3);
    for (let index = 0; index < 180; index += 1) {
        const angle = index * 2.399;
        const radius = 2.8 + (index % 11) * 0.18;
        starPositions[index * 3] = Math.cos(angle) * radius;
        starPositions[index * 3 + 1] = Math.sin(angle) * radius * 0.72;
        starPositions[index * 3 + 2] = (index % 7 - 3) * 0.11;
    }
    const starsGeometry = new THREE.BufferGeometry();
    starsGeometry.setAttribute("position", new THREE.BufferAttribute(starPositions, 3));
    root.add(new THREE.Points(starsGeometry, new THREE.PointsMaterial({ color: 0xd8c7b7, size: 0.018, transparent: true, opacity: 0.7 })));

    let width = 0;
    let height = 0;
    let pointerX = 0;
    let pointerY = 0;
    let dragging = false;
    let lastX = 0;
    let frame = 0;
    let visible = false;
    let hoveredNode = null;
    const raycaster = new THREE.Raycaster();
    const pointer = new THREE.Vector2();
    function resize() {
        if(disposed)return;
        renderer.setPixelRatio(Math.min(devicePixelRatio || 1, 1.5));
        const bounds = host.getBoundingClientRect();
        width = Math.max(1, bounds.width);
        height = Math.max(1, bounds.height);
        camera.aspect = width / height;
        camera.position.z = width < 700 ? 14 : 12.6;
        camera.updateProjectionMatrix();
        renderer.setSize(width, height, false);
        draw();
    }
    function draw() {
        if(!disposed)renderer.render(scene, camera);
    }
    function animate() {
        frame = 0;
        if (!visible || document.hidden || reducedMotion.matches) return;
        root.rotation.y += 0.0016;
        root.rotation.x += (pointerY * 0.12 - root.rotation.x) * 0.018;
        root.rotation.y += (pointerX * 0.16 - root.rotation.y) * 0.002;
        draw();
        frame = requestAnimationFrame(animate);
    }
    function wake() {
        if(disposed)return;
        if(!visible || document.hidden || reducedMotion.matches){
            if(frame)cancelAnimationFrame(frame);frame=0;
            if(visible && !document.hidden)draw();
            return;
        }
        if(!frame)frame=requestAnimationFrame(animate);
    }
    const observer = new IntersectionObserver(entries => {
        visible = entries.some(entry => entry.isIntersecting);
        if (!visible && frame) cancelAnimationFrame(frame);
        frame = 0;
        if (visible) wake();
    }, { threshold: 0.01 });
    observer.observe(host);
    listen(host,"pointerdown", event => {
        if (event.pointerType === "touch" || event.target.closest("button,a,input")) return;
        dragging = true;
        lastX = event.clientX;
        host.setPointerCapture?.(event.pointerId);
    });
    listen(host,"pointermove", event => {
        const bounds = host.getBoundingClientRect();
        pointerX = (event.clientX - bounds.left) / bounds.width - 0.5;
        pointerY = (event.clientY - bounds.top) / bounds.height - 0.5;
        pointer.set(pointerX * 2, -pointerY * 2);
        raycaster.setFromCamera(pointer, camera);
        const hit = raycaster.intersectObjects(nodes.map(node => node.children[0]))[0]?.object.parent || null;
        if (hoveredNode !== hit) {
            if (hoveredNode) hoveredNode.scale.setScalar(1);
            hoveredNode = hit;
            if (hoveredNode) hoveredNode.scale.setScalar(1.22);
            host.style.cursor = hoveredNode ? "pointer" : dragging ? "grabbing" : "grab";
        }
        if (dragging) {
            root.rotation.y += (event.clientX - lastX) * 0.004;
            lastX = event.clientX;
        }
        wake();
    });
    listen(host,"pointerup", () => { dragging = false; host.style.cursor = hoveredNode ? "pointer" : "grab"; });
    listen(host,"pointerleave", () => {
        dragging = false; pointerX = pointerY = 0;
        if (hoveredNode) hoveredNode.scale.setScalar(1);
        hoveredNode = null; host.style.cursor = ""; wake();
    });
    listen(document,"visibilitychange",wake);
    listen(reducedMotion,"change",wake);
    const cleanup = () => {
        if(disposed)return;disposed=true;lifecycle.abort();
        if (frame) cancelAnimationFrame(frame);
        observer.disconnect();
        resizeObserver.disconnect();
        document.removeEventListener("visibilitychange", wake);
        reducedMotion.removeEventListener("change", wake);
        const released=new Set();
        scene.traverse(object => {
            object.geometry?.dispose();
            const materials = Array.isArray(object.material) ? object.material : [object.material];
            materials.filter(Boolean).forEach(material => { if(!released.has(material)){material.map?.dispose();material.dispose();released.add(material);} });
        });
        renderer.dispose();
    };
    const resizeObserver = new ResizeObserver(resize);
    resizeObserver.observe(host);
    listen(window,"pagehide",event=>{if(!event.persisted)cleanup();else{if(frame)cancelAnimationFrame(frame);frame=0;}});
    listen(window,"pageshow",()=>{resize();wake();});
    listen(canvas,"webglcontextlost",event=>{event.preventDefault();cleanup();createFallback(canvas);});
    resize();
}

export function initializeLearningConstellation() {
    document.querySelectorAll("canvas[data-learning-constellation]").forEach(initialize);
}
