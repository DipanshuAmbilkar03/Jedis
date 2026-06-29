/**
 * ============================================
 * JEDIS LANDING PAGE — INTERACTIVE SCRIPT
 * ============================================
 * 
 * Features:
 * 1. Scroll-driven frame animation (240 JPEG frames)
 * 2. Terminal typing animation
 * 3. Scroll-triggered reveal animations
 * 4. Animated stat counters
 * 5. Navbar scroll behavior
 */

(() => {
  'use strict';

  // ─────────────────────────────────────────
  // CONFIG
  // ─────────────────────────────────────────
  const FRAME_COUNT = 240;
  const FRAME_PATH = '../UI/Frames/ezgif-frame-';
  const CONCURRENCY = 6;  // concurrent frame loads

  // ─────────────────────────────────────────
  // FRAME ANIMATION ENGINE
  // ─────────────────────────────────────────
  const canvas = document.getElementById('frameCanvas');
  const ctx = canvas.getContext('2d');
  const heroSection = document.getElementById('hero');
  const heroOverlay = document.getElementById('heroOverlay');
  const scrollIndicator = document.getElementById('scrollIndicator');

  const frames = new Array(FRAME_COUNT);
  let currentFrame = -1;
  let loadedCount = 0;
  let firstFrameLoaded = false;

  // Pad frame number to 3 digits
  function padNumber(n) {
    return String(n).padStart(3, '0');
  }

  // Load a single frame
  function loadFrame(index) {
    return new Promise((resolve) => {
      const img = new Image();
      img.onload = () => {
        frames[index] = img;
        loadedCount++;
        if (index === 0 && !firstFrameLoaded) {
          firstFrameLoaded = true;
          resizeCanvas();
          drawFrame(0);
        }
        resolve();
      };
      img.onerror = () => {
        console.warn(`Failed to load frame ${index}`);
        resolve();
      };
      img.src = `${FRAME_PATH}${padNumber(index + 1)}.jpg`;
    });
  }

  // Load frames with a concurrency pool
  async function loadAllFrames() {
    const queue = Array.from({ length: FRAME_COUNT }, (_, i) => i);
    
    // Prioritize: first, last, then interleaved
    const priority = [0];
    const step = Math.floor(FRAME_COUNT / 20);
    for (let i = step; i < FRAME_COUNT; i += step) {
      priority.push(i);
    }
    priority.push(FRAME_COUNT - 1);
    
    // Move priority frames to front
    const prioritySet = new Set(priority);
    const reordered = [
      ...priority,
      ...queue.filter(i => !prioritySet.has(i))
    ];

    let cursor = 0;
    async function next() {
      while (cursor < reordered.length) {
        const idx = reordered[cursor++];
        await loadFrame(idx);
      }
    }

    const workers = [];
    for (let i = 0; i < CONCURRENCY; i++) {
      workers.push(next());
    }
    await Promise.all(workers);
  }

  // Resize canvas to match viewport at full physical resolution
  function resizeCanvas() {
    const dpr = window.devicePixelRatio || 1;
    canvas.width = Math.round(window.innerWidth * dpr);
    canvas.height = Math.round(window.innerHeight * dpr);
    canvas.style.width = `${window.innerWidth}px`;
    canvas.style.height = `${window.innerHeight}px`;
    // Reset transform — we draw in raw pixel space for maximum sharpness
    ctx.setTransform(1, 0, 0, 1, 0, 0);

    // Redraw current frame
    if (currentFrame >= 0 && frames[currentFrame]) {
      drawFrame(currentFrame);
    }
  }

  // Draw a frame with cover logic — operates in physical pixel space
  function drawFrame(index) {
    const img = frames[index];
    if (!img) return;

    currentFrame = index;

    // Use full physical pixel dimensions for crisp rendering
    const cw = canvas.width;
    const ch = canvas.height;

    const imgRatio = img.naturalWidth / img.naturalHeight;
    const canvasRatio = cw / ch;

    let drawW, drawH, dx, dy;
    if (canvasRatio > imgRatio) {
      drawW = cw;
      drawH = cw / imgRatio;
      dx = 0;
      dy = (ch - drawH) / 2;
    } else {
      drawH = ch;
      drawW = ch * imgRatio;
      dx = (cw - drawW) / 2;
      dy = 0;
    }

    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'high';
    ctx.clearRect(0, 0, cw, ch);
    ctx.drawImage(img, dx, dy, drawW, drawH);
  }

  // Scroll handler for frame scrubbing
  function onScrollFrames() {
    const rect = heroSection.getBoundingClientRect();
    const scrollableHeight = heroSection.offsetHeight - window.innerHeight;
    
    if (scrollableHeight <= 0) return;
    
    const scrolled = -rect.top;
    const progress = Math.max(0, Math.min(1, scrolled / scrollableHeight));

    const frameIndex = Math.min(
      FRAME_COUNT - 1,
      Math.max(0, Math.floor(progress * (FRAME_COUNT - 1)))
    );

    if (frameIndex !== currentFrame && frames[frameIndex]) {
      drawFrame(frameIndex);
    }

    // Fade hero overlay based on scroll
    if (heroOverlay) {
      // Show text for first 15% of scroll, then fade out
      const overlayProgress = Math.min(1, progress / 0.15);
      if (overlayProgress < 1) {
        heroOverlay.style.opacity = 1 - overlayProgress;
        heroOverlay.classList.remove('hidden');
      } else {
        heroOverlay.classList.add('hidden');
      }
    }

    // Hide scroll indicator after small scroll
    if (scrollIndicator) {
      if (progress > 0.02) {
        scrollIndicator.classList.add('hidden');
      } else {
        scrollIndicator.classList.remove('hidden');
      }
    }
  }

  // ─────────────────────────────────────────
  // TERMINAL TYPING ANIMATION
  // ─────────────────────────────────────────
  const terminalBody = document.getElementById('terminalBody');
  const terminalCommands = [
    { type: 'cmd', text: '127.0.0.1:6380> PING' },
    { type: 'output', text: '<span class="term-success">PONG</span>' },
    { type: 'spacer' },
    { type: 'cmd', text: '127.0.0.1:6380> SET greeting "Hello from Jedis!"' },
    { type: 'output', text: '<span class="term-success">OK</span>' },
    { type: 'spacer' },
    { type: 'cmd', text: '127.0.0.1:6380> GET greeting' },
    { type: 'output', text: '<span class="term-value">"Hello from Jedis!"</span>' },
    { type: 'spacer' },
    { type: 'cmd', text: '127.0.0.1:6380> SET counter 0' },
    { type: 'output', text: '<span class="term-success">OK</span>' },
    { type: 'spacer' },
    { type: 'cmd', text: '127.0.0.1:6380> INCR counter' },
    { type: 'output', text: '<span class="term-int">(integer) 1</span>' },
    { type: 'spacer' },
    { type: 'cmd', text: '127.0.0.1:6380> INCR counter' },
    { type: 'output', text: '<span class="term-int">(integer) 2</span>' },
    { type: 'spacer' },
    { type: 'cmd', text: '127.0.0.1:6380> LPUSH tasks "build" "test" "deploy"' },
    { type: 'output', text: '<span class="term-int">(integer) 3</span>' },
    { type: 'spacer' },
    { type: 'cmd', text: '127.0.0.1:6380> LRANGE tasks 0 -1' },
    { type: 'output', text: '1) <span class="term-value">"deploy"</span>' },
    { type: 'output', text: '2) <span class="term-value">"test"</span>' },
    { type: 'output', text: '3) <span class="term-value">"build"</span>' },
    { type: 'spacer' },
    { type: 'cmd', text: '127.0.0.1:6380> HSET user:1 name "John" age "25"' },
    { type: 'output', text: '<span class="term-int">(integer) 2</span>' },
    { type: 'spacer' },
    { type: 'cmd', text: '127.0.0.1:6380> HGETALL user:1' },
    { type: 'output', text: '1) <span class="term-key">"name"</span>' },
    { type: 'output', text: '2) <span class="term-value">"John"</span>' },
    { type: 'output', text: '3) <span class="term-key">"age"</span>' },
    { type: 'output', text: '4) <span class="term-value">"25"</span>' },
  ];

  let terminalAnimated = false;
  let terminalLineIndex = 0;

  function typeTerminalLine(lineDiv, text, speed = 20) {
    return new Promise((resolve) => {
      let i = 0;
      // Strip HTML tags for typing, then insert final HTML
      const plainText = text.replace(/<[^>]+>/g, '');
      
      function type() {
        if (i < plainText.length) {
          lineDiv.textContent += plainText[i];
          i++;
          setTimeout(type, speed);
        } else {
          // Replace with rich HTML
          lineDiv.innerHTML = text;
          resolve();
        }
      }
      type();
    });
  }

  async function animateTerminal() {
    if (terminalAnimated || !terminalBody) return;
    terminalAnimated = true;

    for (const cmd of terminalCommands) {
      if (cmd.type === 'spacer') {
        const spacer = document.createElement('div');
        spacer.style.height = '6px';
        terminalBody.appendChild(spacer);
        continue;
      }

      const line = document.createElement('div');
      line.className = 'term-line';

      if (cmd.type === 'cmd') {
        line.classList.add('term-cmd');
        terminalBody.appendChild(line);
        await typeTerminalLine(line, cmd.text, 15);
        await sleep(150);
      } else {
        line.classList.add('term-output');
        line.innerHTML = cmd.text;
        terminalBody.appendChild(line);
        await sleep(60);
      }

      // Auto-scroll terminal
      terminalBody.scrollTop = terminalBody.scrollHeight;
    }

    // Add cursor at end
    const cursorLine = document.createElement('div');
    cursorLine.className = 'term-line';
    cursorLine.innerHTML = '127.0.0.1:6380> <span class="term-cursor"></span>';
    terminalBody.appendChild(cursorLine);
    terminalBody.scrollTop = terminalBody.scrollHeight;
  }

  function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  // ─────────────────────────────────────────
  // SCROLL-TRIGGERED ANIMATIONS
  // ─────────────────────────────────────────
  function setupScrollAnimations() {
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('animate-in');
            
            // Trigger terminal animation when commands section is visible
            if (entry.target.closest('#commands') || entry.target.id === 'terminal') {
              animateTerminal();
            }
          }
        });
      },
      {
        threshold: 0.1,
        rootMargin: '0px 0px -60px 0px',
      }
    );

    document.querySelectorAll('[data-animate]').forEach((el) => {
      observer.observe(el);
    });

    // Also observe the terminal itself
    const terminal = document.getElementById('terminal');
    if (terminal) {
      terminal.setAttribute('data-animate', '');
      observer.observe(terminal);
    }
  }

  // ─────────────────────────────────────────
  // STAT COUNTER ANIMATION
  // ─────────────────────────────────────────
  function animateCounters() {
    const counters = document.querySelectorAll('.stat-number[data-count]');
    
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting && !entry.target.dataset.animated) {
            entry.target.dataset.animated = 'true';
            const target = parseInt(entry.target.dataset.count, 10);
            animateCounter(entry.target, target);
          }
        });
      },
      { threshold: 0.5 }
    );

    counters.forEach((el) => observer.observe(el));
  }

  function animateCounter(element, target) {
    const duration = 1800;
    const start = performance.now();
    const startVal = 0;

    function easeOutExpo(t) {
      return t === 1 ? 1 : 1 - Math.pow(2, -10 * t);
    }

    function update(now) {
      const elapsed = now - start;
      const progress = Math.min(elapsed / duration, 1);
      const easedProgress = easeOutExpo(progress);
      const current = Math.round(startVal + (target - startVal) * easedProgress);
      
      element.textContent = current.toLocaleString();
      
      if (progress < 1) {
        requestAnimationFrame(update);
      }
    }

    requestAnimationFrame(update);
  }

  // ─────────────────────────────────────────
  // NAVBAR SCROLL BEHAVIOR
  // ─────────────────────────────────────────
  function setupNavbar() {
    const navbar = document.getElementById('navbar');
    const navToggle = document.getElementById('navToggle');

    window.addEventListener('scroll', () => {
      if (window.scrollY > 80) {
        navbar.classList.add('scrolled');
      } else {
        navbar.classList.remove('scrolled');
      }
    }, { passive: true });

    if (navToggle) {
      navToggle.addEventListener('click', () => {
        navbar.classList.toggle('mobile-open');
      });
    }

    // Smooth scroll for nav links
    document.querySelectorAll('.nav-link, .hero-actions a[href^="#"]').forEach((link) => {
      link.addEventListener('click', (e) => {
        const href = link.getAttribute('href');
        if (href && href.startsWith('#')) {
          e.preventDefault();
          const target = document.querySelector(href);
          if (target) {
            target.scrollIntoView({ behavior: 'smooth', block: 'start' });
            navbar.classList.remove('mobile-open');
          }
        }
      });
    });
  }

  // ─────────────────────────────────────────
  // INIT
  // ─────────────────────────────────────────
  function init() {
    // Start loading frames
    loadAllFrames();

    // Resize handler
    window.addEventListener('resize', resizeCanvas, { passive: true });
    resizeCanvas();

    // Scroll handler for frame animation (use rAF throttling)
    let ticking = false;
    window.addEventListener('scroll', () => {
      if (!ticking) {
        requestAnimationFrame(() => {
          onScrollFrames();
          ticking = false;
        });
        ticking = true;
      }
    }, { passive: true });

    // Setup other features
    setupNavbar();
    setupScrollAnimations();
    animateCounters();

    // Draw first frame if already loaded
    if (frames[0]) {
      drawFrame(0);
    }
  }

  // Run on DOMContentLoaded
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
