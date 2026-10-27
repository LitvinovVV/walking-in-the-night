function switchPage(targetId) {
    document.querySelectorAll('.page-section').forEach(sec => {
        sec.classList.remove('active');
    });
    document.getElementById(targetId).classList.add('active');
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

const canvas = document.getElementById('nightSkyCanvas');
const ctx = canvas.getContext('2d');
let stars = [];
let shootingStars = [];

function resizeSky() {
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
    initStars();
}

function initStars() {
    stars = [];
    const count = Math.floor((canvas.width * canvas.height) / 5500);
    for (let i = 0; i < count; i++) {
        stars.push({
            x: Math.random() * canvas.width,
            y: Math.random() * canvas.height,
            r: Math.random() * 1.6 + 0.3,
            alpha: Math.random(),
            speed: (Math.random() * 0.015) + 0.004,
            hue: Math.random() > 0.85 ? '#90caf9' : (Math.random() > 0.9 ? '#ef9a9a' : '#f5ebe6')
        });
    }
}

function spawnShootingStar() {
    if (Math.random() < 0.018 && shootingStars.length < 2) {
        shootingStars.push({
            x: Math.random() * canvas.width * 0.8 + canvas.width * 0.1,
            y: Math.random() * canvas.height * 0.35,
            len: Math.random() * 70 + 50,
            speed: Math.random() * 6 + 6,
            alpha: 1
        });
    }
}

function animateSky() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    const grad = ctx.createRadialGradient(
        canvas.width / 2, canvas.height * 0.25, 40,
        canvas.width / 2, canvas.height * 0.5, Math.max(canvas.width, canvas.height)
    );
    grad.addColorStop(0, '#171214');
    grad.addColorStop(0.55, '#0b090a');
    grad.addColorStop(1, '#050404');
    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    stars.forEach(s => {
        s.alpha += s.speed;
        if (s.alpha > 1 || s.alpha < 0.15) s.speed = -s.speed;
        ctx.save();
        ctx.globalAlpha = Math.max(0.1, Math.min(1, s.alpha));
        ctx.fillStyle = s.hue;
        ctx.beginPath();
        ctx.arc(s.x, s.y, s.r, 0, Math.PI * 2);
        ctx.fill();
        ctx.restore();
    });

    spawnShootingStar();
    for (let i = shootingStars.length - 1; i >= 0; i--) {
        const st = shootingStars[i];
        ctx.save();
        ctx.strokeStyle = `rgba(245, 235, 230, ${st.alpha})`;
        ctx.lineWidth = 1.6;
        ctx.beginPath();
        ctx.moveTo(st.x, st.y);
        ctx.lineTo(st.x - st.len, st.y + st.len * 0.6);
        ctx.stroke();
        ctx.restore();

        st.x += st.speed;
        st.y -= st.speed * 0.6;
        st.alpha -= 0.022;
        if (st.alpha <= 0) shootingStars.splice(i, 1);
    }

    requestAnimationFrame(animateSky);
}

window.addEventListener('resize', resizeSky);
resizeSky();
animateSky();