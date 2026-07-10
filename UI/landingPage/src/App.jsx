import React, { useState, useEffect, useRef } from 'react';
import './App.css';

// Frame ranges for automatic loop narrative sections
const SEGMENTS = [
  { id: 0, range: [0, 45], name: 'hero' },
  { id: 1, range: [46, 90], name: 'architecture' },
  { id: 2, range: [91, 135], name: 'dashboard' },
  { id: 3, range: [136, 170], name: 'persistence' },
  { id: 4, range: [171, 191], name: 'cta' }
];

const COMMAND_GROUPS = [
  {
    title: 'Strings',
    items: ['SET', 'GET', 'MSET', 'MGET', 'INCR', 'DECR']
  },
  {
    title: 'Collections',
    items: ['LPUSH', 'RPUSH', 'LPOP', 'RPOP', 'LRANGE', 'SADD', 'SMEMBERS', 'SREM', 'HSET', 'HGET', 'HGETALL']
  },
  {
    title: 'Keys & TTL',
    items: ['DEL', 'EXPIRE', 'TTL', 'EXISTS', 'TYPE', 'KEYS']
  },
  {
    title: 'Advanced',
    items: ['MULTI', 'EXEC', 'DISCARD', 'SUBSCRIBE', 'PUBLISH', 'UNSUBSCRIBE', 'REPLICAOF', 'PSYNC', 'CLUSTER', 'ACL', 'AUTH', 'INFO', 'SAVE', 'FLUSHDB', 'PING', 'ECHO']
  }
];

const STACK = [
  { label: 'Language', value: 'Java 25' },
  { label: 'Protocol', value: 'RESP2' },
  { label: 'Engine port', value: '6380' },
  { label: 'Dashboard', value: '8080' },
  { label: 'Persistence', value: 'AOF + RDB JSON' },
  { label: 'Runtime deps', value: 'None' }
];

export default function App() {
  const [prefersReducedMotion, setPrefersReducedMotion] = useState(false);
  const [loadedCount, setLoadedCount] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [activeSection, setActiveSection] = useState(0);
  const [scrolled, setScrolled] = useState(false);

  const [cliInput, setCliInput] = useState('');
  const [cliLogs, setCliLogs] = useState([
    { text: 'Jedis playground · browser simulation', type: 'meta' },
    { text: 'Type HELP · live server: redis-cli -p 6380', type: 'success' }
  ]);
  const [dbState, setDbState] = useState({});
  const terminalEndRef = useRef(null);
  const terminalScreenRef = useRef(null);
  const hasMountedCliRef = useRef(false);

  const canvasRef = useRef(null);
  const imagesRef = useRef([]);
  const frameIndexRef = useRef(0);
  const directionRef = useRef(1);
  const pauseTicksRef = useRef(0);
  const rafIdRef = useRef(null);
  const resizeTimeoutRef = useRef(null);
  const isLoadedRef = useRef(false);

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    setPrefersReducedMotion(mediaQuery.matches);
    const listener = (e) => setPrefersReducedMotion(e.matches);
    mediaQuery.addEventListener('change', listener);
    return () => mediaQuery.removeEventListener('change', listener);
  }, []);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 24);
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  useEffect(() => {
    if (prefersReducedMotion) {
      setIsLoading(false);
      return;
    }

    const totalFrames = 192;
    let loadedCounter = 0;

    const handleImageLoad = () => {
      loadedCounter++;
      setLoadedCount(loadedCounter);
      if (loadedCounter === totalFrames) {
        isLoadedRef.current = true;
        setTimeout(() => setIsLoading(false), 300);
      }
    };

    for (let i = 1; i <= totalFrames; i++) {
      const img = new Image();
      const num = String(i).padStart(3, '0');
      img.src = `/frames/frame_${num}.webp`;
      img.onload = () => {
        imagesRef.current[i - 1] = img;
        handleImageLoad();
      };
      img.onerror = handleImageLoad;
    }

    return () => {
      imagesRef.current = [];
    };
  }, [prefersReducedMotion]);

  const drawImageCover = (ctx, img, canvas) => {
    if (!img || !img.complete || img.naturalWidth === 0) return;

    const cw = canvas.width;
    const ch = canvas.height;
    const iw = img.naturalWidth;
    const ih = img.naturalHeight;
    const canvasAspect = cw / ch;
    const imgAspect = iw / ih;

    let drawWidth, drawHeight, drawX, drawY;

    if (canvasAspect > imgAspect) {
      drawWidth = cw;
      drawHeight = cw / imgAspect;
      drawX = 0;
      drawY = (ch - drawHeight) / 2;
    } else {
      drawWidth = ch * imgAspect;
      drawHeight = ch;
      drawX = (cw - drawWidth) / 2;
      drawY = 0;
    }

    ctx.clearRect(0, 0, cw, ch);
    ctx.drawImage(img, drawX, drawY, drawWidth, drawHeight);
  };

  useEffect(() => {
    if (isLoading || prefersReducedMotion) return;

    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');

    const handleResize = () => {
      canvas.width = window.innerWidth;
      canvas.height = window.innerHeight;
      const currentImg = imagesRef.current[frameIndexRef.current];
      if (currentImg) drawImageCover(ctx, currentImg, canvas);
    };

    const debouncedResize = () => {
      if (resizeTimeoutRef.current) clearTimeout(resizeTimeoutRef.current);
      resizeTimeoutRef.current = setTimeout(handleResize, 150);
    };

    window.addEventListener('resize', debouncedResize);
    handleResize();

    let lastTime = 0;
    const fps = 24;
    const interval = 1000 / fps;

    const playLoop = (timestamp) => {
      if (!lastTime) lastTime = timestamp;
      const elapsed = timestamp - lastTime;

      if (elapsed >= interval) {
        lastTime = timestamp - (elapsed % interval);

        if (pauseTicksRef.current > 0) {
          pauseTicksRef.current--;
        } else {
          const nextFrame = frameIndexRef.current + directionRef.current;

          if (nextFrame >= 192) {
            directionRef.current = -1;
            frameIndexRef.current = 191;
            pauseTicksRef.current = 60;
          } else if (nextFrame < 0) {
            directionRef.current = 1;
            frameIndexRef.current = 0;
            pauseTicksRef.current = 60;
          } else {
            frameIndexRef.current = nextFrame;
          }

          const currentFrame = frameIndexRef.current;
          const img = imagesRef.current[currentFrame];
          if (img) drawImageCover(ctx, img, canvas);

          let newSection = 0;
          for (const segment of SEGMENTS) {
            if (currentFrame >= segment.range[0] && currentFrame <= segment.range[1]) {
              newSection = segment.id;
              break;
            }
          }
          setActiveSection((prev) => (prev !== newSection ? newSection : prev));
        }
      }

      rafIdRef.current = requestAnimationFrame(playLoop);
    };

    rafIdRef.current = requestAnimationFrame(playLoop);

    return () => {
      window.removeEventListener('resize', debouncedResize);
      if (rafIdRef.current) cancelAnimationFrame(rafIdRef.current);
      if (resizeTimeoutRef.current) clearTimeout(resizeTimeoutRef.current);
    };
  }, [isLoading, prefersReducedMotion]);

  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-visible');
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.12 }
    );

    document.querySelectorAll('.reveal').forEach((el) => observer.observe(el));
    return () => observer.disconnect();
  }, [isLoading]);

  // Keep the landing page at the top on first open (ignore browser scroll restoration / hash).
  useEffect(() => {
    if ('scrollRestoration' in window.history) {
      window.history.scrollRestoration = 'manual';
    }
    if (window.location.hash) {
      window.history.replaceState(null, '', window.location.pathname + window.location.search);
    }
    window.scrollTo(0, 0);
  }, []);

  // After preloader unmounts, layout expands — pin viewport to the hero again.
  useEffect(() => {
    if (isLoading) return;
    window.scrollTo(0, 0);
    const id = window.requestAnimationFrame(() => window.scrollTo(0, 0));
    return () => window.cancelAnimationFrame(id);
  }, [isLoading]);

  // Scroll only inside the terminal panel — never the document.
  useEffect(() => {
    if (!hasMountedCliRef.current) {
      hasMountedCliRef.current = true;
      return;
    }
    const screen = terminalScreenRef.current;
    if (!screen) return;
    screen.scrollTop = screen.scrollHeight;
  }, [cliLogs]);

  const handleCliSubmit = (e) => {
    e.preventDefault();
    if (!cliInput.trim()) return;

    const command = cliInput.trim();
    const parts = command.split(/\s+/);
    const cmdType = parts[0].toUpperCase();
    const key = parts[1];
    const val = parts.slice(2).join(' ');

    const newLogs = [...cliLogs, { text: `> ${command}`, type: 'input' }];
    let response = '';
    let responseType = 'output';

    switch (cmdType) {
      case 'PING':
        response = val ? `"${val}"` : 'PONG';
        break;
      case 'ECHO':
        if (!key) {
          response = "(error) ERR wrong number of arguments for 'echo' command";
          responseType = 'error';
        } else {
          response = `"${parts.slice(1).join(' ')}"`;
        }
        break;
      case 'HELP':
        response = [
          'Playground (simulation):',
          '  PING [msg]  SET key val  GET key',
          '  DEL key  KEYS *  INCR key  ECHO msg',
          '',
          'Full protocol on the Java server (port 6380):',
          'lists, sets, hashes, TTL, MULTI/EXEC,',
          'pub/sub, AOF/RDB, ACL, replication, CLUSTER.'
        ].join('\n');
        break;
      case 'SET':
        if (!key || !val) {
          response = "(error) ERR wrong number of arguments for 'set' command";
          responseType = 'error';
        } else {
          setDbState((prev) => ({ ...prev, [key]: val }));
          response = 'OK';
        }
        break;
      case 'GET':
        if (!key) {
          response = "(error) ERR wrong number of arguments for 'get' command";
          responseType = 'error';
        } else {
          const fetched = dbState[key];
          response = fetched !== undefined ? `"${fetched}"` : '(nil)';
        }
        break;
      case 'DEL':
        if (!key) {
          response = "(error) ERR wrong number of arguments for 'del' command";
          responseType = 'error';
        } else if (dbState[key] !== undefined) {
          setDbState((prev) => {
            const copy = { ...prev };
            delete copy[key];
            return copy;
          });
          response = '(integer) 1';
        } else {
          response = '(integer) 0';
        }
        break;
      case 'INCR':
        if (!key) {
          response = "(error) ERR wrong number of arguments for 'incr' command";
          responseType = 'error';
        } else {
          const current = dbState[key];
          if (current === undefined) {
            setDbState((prev) => ({ ...prev, [key]: '1' }));
            response = '(integer) 1';
          } else if (!/^-?\d+$/.test(String(current))) {
            response = '(error) ERR value is not an integer or out of range';
            responseType = 'error';
          } else {
            const next = String(Number(current) + 1);
            setDbState((prev) => ({ ...prev, [key]: next }));
            response = `(integer) ${next}`;
          }
        }
        break;
      case 'KEYS':
        if (key === '*') {
          const keys = Object.keys(dbState);
          response =
            keys.length === 0
              ? '(empty array)'
              : keys.map((k, idx) => `${idx + 1}) "${k}"`).join('\n');
        } else {
          response = '(error) ERR only KEYS * is simulated here';
          responseType = 'error';
        }
        break;
      default:
        response = `(error) ERR unknown command '${parts[0]}'. Type HELP.`;
        responseType = 'error';
    }

    setCliLogs([...newLogs, { text: response, type: responseType }]);
    setCliInput('');
  };

  const overlayCopy = [
    {
      kicker: 'Jedis',
      title: (
        <>
          Redis-compatible.
          <br />
          Written in pure Java.
        </>
      ),
      body: 'In-memory key-value engine with a real RESP2 wire protocol, concurrent storage, and an embedded admin console — zero runtime dependencies.'
    },
    {
      kicker: 'TCP · 6380',
      title: (
        <>
          Speaks redis-cli.
          <br />
          No adapters.
        </>
      ),
      body: 'Hand-rolled RESP2 parser, ConcurrentHashMap store, thread-per-client by default, optional NIO mode. Point Jedis, Lettuce, or redis-cli straight at the socket.'
    },
    {
      kicker: 'Dashboard · 8080',
      title: (
        <>
          Console in
          <br />
          the same JAR.
        </>
      ),
      body: 'React admin UI is packaged with the engine. Open localhost:8080 for keys, telemetry, and a live web CLI after startup.'
    },
    {
      kicker: 'AOF + RDB',
      title: (
        <>
          Durable when
          <br />
          you need it.
        </>
      ),
      body: 'Append-only command logs, periodic JSON snapshots, and dual TTL expiry — lazy on access, active in the background.'
    },
    {
      kicker: 'Ship it',
      title: (
        <>
          One process.
          <br />
          Database + UI.
        </>
      ),
      body: 'mvn package, run the JAR, connect on 6380, open the dashboard on 8080.',
      cta: true
    }
  ];

  return (
    <div className="site">
      <nav className={`nav ${scrolled ? 'nav-scrolled' : ''}`}>
        <a href="#" className="nav-brand">
          <span className="nav-mark" aria-hidden="true" />
          Jedis
        </a>
        <div className="nav-links">
          <a href="#engine">Engine</a>
          <a href="#commands">Commands</a>
          <a href="#playground">Playground</a>
          <a href="#compare">Compare</a>
        </div>
        <a
          className="nav-cta"
          href="https://github.com/DipanshuAmbilkar03/Jedis"
          target="_blank"
          rel="noreferrer"
        >
          GitHub
        </a>
      </nav>

      {!prefersReducedMotion && (
        <div className={`preloader ${!isLoading ? 'fade-out' : ''}`}>
          <div className="preloader-inner">
            <div className="preloader-mark" />
            <p className="preloader-label">Jedis</p>
            <div className="preloader-track">
              <div
                className="preloader-fill"
                style={{ width: `${(loadedCount / 192) * 100}%` }}
              />
            </div>
            <p className="preloader-meta">
              {Math.round((loadedCount / 192) * 100)}%
            </p>
          </div>
        </div>
      )}

      {!prefersReducedMotion ? (
        <header className="hero">
          <div className="canvas-wrap">
            <canvas ref={canvasRef} className="hero-canvas" />
          </div>

          <div className="hero-overlays">
            {overlayCopy.map((slide, idx) => (
              <div
                key={idx}
                className={`hero-slide ${activeSection === idx ? 'active' : ''}`}
              >
                <div className="hero-copy">
                  <p className="kicker">{slide.kicker}</p>
                  <h1>{slide.title}</h1>
                  <p className="lede">{slide.body}</p>
                  {idx === 0 && (
                    <div className="hero-actions">
                      <a href="#playground" className="btn btn-solid">
                        Open playground
                      </a>
                      <a href="#engine" className="btn btn-ghost">
                        Explore engine
                      </a>
                    </div>
                  )}
                  {slide.cta && (
                    <div className="hero-actions">
                      <a href="#run" className="btn btn-solid">
                        Build &amp; run
                      </a>
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>

          <div className="hero-rail" aria-hidden="true">
            {SEGMENTS.map((seg) => (
              <span
                key={seg.id}
                className={`rail-dot ${activeSection === seg.id ? 'on' : ''}`}
              />
            ))}
          </div>
        </header>
      ) : (
        <header className="hero-static">
          <p className="kicker">Jedis · pure Java</p>
          <h1>
            Redis-compatible.
            <br />
            Written in pure Java.
          </h1>
          <p className="lede">
            Concurrent in-memory store, RESP2 protocol, dual persistence, ACL,
            replication, cluster slots, and an embedded React dashboard.
          </p>
          <div className="hero-actions">
            <a href="#playground" className="btn btn-solid">
              Open playground
            </a>
            <a href="#engine" className="btn btn-ghost">
              Explore engine
            </a>
          </div>
        </header>
      )}

      <main>
        <section className="band stack-band reveal" id="engine">
          <div className="shell">
            <div className="band-head">
              <p className="kicker">What this repo is</p>
              <h2>A ground-up Redis-like server — not a wrapper.</h2>
              <p className="band-sub">
                TCP on 6380, HTTP dashboard on 8080, one JAR. Every subsystem below
                lives under <code>mini-redis</code>.
              </p>
            </div>

            <div className="spec-grid">
              {STACK.map((item) => (
                <div key={item.label} className="spec-cell">
                  <span className="spec-label">{item.label}</span>
                  <span className="spec-value">{item.value}</span>
                </div>
              ))}
            </div>

            <div className="split">
              <article className="split-card">
                <h3>Storage &amp; concurrency</h3>
                <p>
                  ConcurrentHashMap-backed data store for strings, lists, sets, and
                  hashes. Thread-per-client pool by default; optional NIO event-loop
                  with <code>--nio</code>.
                </p>
              </article>
              <article className="split-card">
                <h3>Persistence &amp; expiry</h3>
                <p>
                  AOF write-ahead logging and JSON RDB snapshots. EXPIRE/TTL with
                  lazy eviction on access plus an active background sweeper.
                </p>
              </article>
              <article className="split-card">
                <h3>Replication &amp; cluster</h3>
                <p>
                  REPLICAOF / PSYNC with a replication backlog. CRC16 slot map
                  across 16,384 slots with CLUSTER MEET, ADDSLOTS, NODES, SLOTS.
                </p>
              </article>
              <article className="split-card">
                <h3>Security &amp; console</h3>
                <p>
                  AUTH + ACL user rules via AclManager. Embedded React console for
                  key browsing, command execution, and server stats on port 8080.
                </p>
              </article>
            </div>
          </div>
        </section>

        <section className="band reveal" id="commands">
          <div className="shell">
            <div className="band-head narrow">
              <p className="kicker">Command surface</p>
              <h2>What clients can send today</h2>
              <p className="band-sub">
                Implemented handlers in the Java engine — the same surface
                redis-cli uses against a real Redis node.
              </p>
            </div>

            <div className="cmd-panels">
              {COMMAND_GROUPS.map((group) => (
                <div key={group.title} className="cmd-panel">
                  <h3>{group.title}</h3>
                  <div className="cmd-list">
                    {group.items.map((cmd) => (
                      <code key={cmd}>{cmd}</code>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>

        <section className="band playground-band reveal" id="playground">
          <div className="shell playground-shell">
            <div className="playground-intro">
              <p className="kicker">Playground</p>
              <h2>Talk to the protocol.</h2>
              <p className="band-sub">
                Browser-side simulation for a quick feel. For the real engine:
                start the JAR, then <code>redis-cli -p 6380</code> or the web CLI
                at <code>localhost:8080</code>.
              </p>
              <ul className="hint-list">
                <li>
                  <code>SET session active</code>
                </li>
                <li>
                  <code>GET session</code>
                </li>
                <li>
                  <code>INCR visits</code>
                </li>
                <li>
                  <code>KEYS *</code>
                </li>
              </ul>
            </div>

            <div className="terminal">
              <div className="terminal-bar">
                <div className="terminal-dots">
                  <span />
                  <span />
                  <span />
                </div>
                <span className="terminal-path">jedis · simulated</span>
              </div>
              <div className="terminal-screen" ref={terminalScreenRef}>
                {cliLogs.map((log, idx) => (
                  <div key={idx} className={`t-line t-${log.type}`}>
                    {log.text}
                  </div>
                ))}
                <form className="t-form" onSubmit={handleCliSubmit}>
                  <span className="t-prompt">jedis&gt;</span>
                  <input
                    value={cliInput}
                    onChange={(e) => setCliInput(e.target.value)}
                    placeholder="HELP"
                    spellCheck={false}
                    autoComplete="off"
                  />
                </form>
                <div ref={terminalEndRef} />
              </div>
            </div>
          </div>
        </section>

        <section className="band reveal" id="compare">
          <div className="shell">
            <div className="band-head narrow">
              <p className="kicker">Honest comparison</p>
              <h2>Jedis vs Redis</h2>
              <p className="band-sub">
                Based on what this repository implements — not aspirational
                feature lists.
              </p>
            </div>

            <div className="compare-list">
              {[
                {
                  aspect: 'Runtime',
                  jedis: 'Java 25, pure JDK core',
                  redis: 'C binary'
                },
                {
                  aspect: 'Threading',
                  jedis: 'Thread-per-client; optional NIO',
                  redis: 'Event loop (+ I/O threads in newer versions)'
                },
                {
                  aspect: 'Protocol',
                  jedis: 'RESP2 (redis-cli / Jedis / Lettuce)',
                  redis: 'RESP2 and RESP3'
                },
                {
                  aspect: 'Data types',
                  jedis: 'String, List, Set, Hash',
                  redis: 'Those + Sorted Set, Stream, Bitmap, HLL, …'
                },
                {
                  aspect: 'Persistence',
                  jedis: 'AOF + JSON snapshots',
                  redis: 'AOF + binary RDB'
                },
                {
                  aspect: 'Replication',
                  jedis: 'REPLICAOF / PSYNC + backlog',
                  redis: 'Replication + Sentinel failover'
                },
                {
                  aspect: 'Cluster',
                  jedis: 'CRC16 slots (16,384) when enabled',
                  redis: 'Redis Cluster + gossip / failover'
                },
                {
                  aspect: 'Admin UI',
                  jedis: 'Embedded React dashboard :8080',
                  redis: 'External tools (RedisInsight, etc.)'
                },
                {
                  aspect: 'License',
                  jedis: 'MIT (project docs)',
                  redis: 'RSALv2 / SSPL or older BSD'
                }
              ].map((row) => (
                <div key={row.aspect} className="compare-row">
                  <div className="compare-aspect">{row.aspect}</div>
                  <div className="compare-jedis">
                    <span className="compare-tag">Jedis</span>
                    {row.jedis}
                  </div>
                  <div className="compare-redis">
                    <span className="compare-tag muted">Redis</span>
                    {row.redis}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>

        <section className="band run-band reveal" id="run">
          <div className="shell run-shell">
            <div>
              <p className="kicker">Build &amp; run</p>
              <h2>Start the engine in a few steps.</h2>
              <p className="band-sub">
                Package the dashboard into the JAR, compile mini-redis, then hit
                the TCP port or open the console.
              </p>
              <a
                className="btn btn-solid"
                href="https://github.com/DipanshuAmbilkar03/Jedis"
                target="_blank"
                rel="noreferrer"
              >
                View on GitHub
              </a>
            </div>

            <pre className="code-panel" tabIndex={0}>
              <code>{`# Frontend assets
cd frontend
npm install && npm run build
node update_dashboard_html.js

# Engine
cd ../mini-redis
mvn clean package
java -jar target/mini-redis-1.0.0.jar

# Clients
redis-cli -p 6380
# dashboard → http://localhost:8080`}</code>
            </pre>
          </div>
        </section>
      </main>

      <footer className="footer">
        <div className="shell footer-inner">
          <div className="footer-brand">
            <span className="nav-mark" aria-hidden="true" />
            Jedis
          </div>
          <p className="footer-note">
            Pure Java in-memory key-value engine · RESP2 · embedded dashboard
          </p>
          <div className="footer-links">
            <a href="#engine">Engine</a>
            <a href="#playground">Playground</a>
            <a
              href="https://github.com/DipanshuAmbilkar03/Jedis"
              target="_blank"
              rel="noreferrer"
            >
              GitHub
            </a>
          </div>
        </div>
      </footer>
    </div>
  );
}
