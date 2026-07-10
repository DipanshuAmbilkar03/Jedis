import React, { useState, useEffect, useRef, useCallback } from 'react';
import './App.css';

// ── Background Frame Animation Canvas ──
function BackgroundAnimation() {
  const canvasRef = useRef(null);
  const imagesRef = useRef([]);
  const frameCount = 192;
  const currentFrameRef = useRef(0);
  const animationFrameIdRef = useRef(null);

  useEffect(() => {
    // Preload image elements progressively
    const loadedImages = [];
    for (let i = 1; i <= frameCount; i++) {
      const img = new Image();
      const num = String(i).padStart(3, '0');
      img.src = `/UI/Frames/ezgif-frame-${num}.jpg`;
      img.onload = () => {
        loadedImages[i - 1] = img;
      };
      imagesRef.current.push(img);
    }

    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');

    const resizeCanvas = () => {
      canvas.width = window.innerWidth;
      canvas.height = window.innerHeight;
    };
    window.addEventListener('resize', resizeCanvas);
    resizeCanvas();

    let lastTime = 0;
    const fps = 24;
    const interval = 1000 / fps;

    const render = (timestamp) => {
      if (!lastTime) lastTime = timestamp;
      const elapsed = timestamp - lastTime;

      if (elapsed > interval) {
        lastTime = timestamp - (elapsed % interval);
        ctx.clearRect(0, 0, canvas.width, canvas.height);

        const frameIdx = currentFrameRef.current;
        const currentImg = loadedImages[frameIdx] || imagesRef.current[frameIdx];

        if (currentImg && currentImg.complete && currentImg.naturalWidth > 0) {
          const canvasAspect = canvas.width / canvas.height;
          const imgAspect = currentImg.width / currentImg.height;
          let drawWidth, drawHeight, drawX, drawY;

          if (canvasAspect > imgAspect) {
            drawWidth = canvas.width;
            drawHeight = canvas.width / imgAspect;
            drawX = 0;
            drawY = (canvas.height - drawHeight) / 2;
          } else {
            drawWidth = canvas.height * imgAspect;
            drawHeight = canvas.height;
            drawX = (canvas.width - drawWidth) / 2;
            drawY = 0;
          }

          ctx.drawImage(currentImg, drawX, drawY, drawWidth, drawHeight);
        }
        currentFrameRef.current = (frameIdx + 1) % frameCount;
      }
      animationFrameIdRef.current = requestAnimationFrame(render);
    };

    animationFrameIdRef.current = requestAnimationFrame(render);

    return () => {
      window.removeEventListener('resize', resizeCanvas);
      if (animationFrameIdRef.current) {
        cancelAnimationFrame(animationFrameIdRef.current);
      }
    };
  }, []);

  return (
    <canvas
      ref={canvasRef}
      className="bg-animation-canvas"
    />
  );
}

// ── Main App Component ──
export default function App() {
  // refs to prevent scrolling to console on initial mount
  const isTerminalInitial = useRef(true);
  const isPlayInitial = useRef(true);

  // Login Form States
  const [usernameInput, setUsernameInput] = useState('');
  const [passwordInput, setPasswordInput] = useState('');
  const [loginError, setLoginError] = useState('');

  // Routing State: 'user' | 'login' | 'admin'
  const [currentMode, setCurrentMode] = useState(() => {
    const path = window.location.pathname;
    if (path.startsWith('/admin')) {
      return localStorage.getItem('jedis_admin_logged_in') === 'true' ? 'admin' : 'login';
    }
    return 'user';
  });

  // Admin View Submenu View
  const [adminView, setAdminView] = useState('overview'); // 'overview' | 'explorer' | 'cli' | 'config'

  // Live Server States
  const [isLive, setIsLive] = useState(false);
  const [serverInfo, setServerInfo] = useState({
    keys: 0,
    memoryUsedBytes: 0,
    memoryMaxBytes: 0,
    connectedClients: 0,
    uptimeSeconds: 0,
    evictionPolicy: 'noeviction',
    aofEnabled: true,
    rdbEnabled: true,
    port: 6380,
    clusterEnabled: false,
    replicaOf: null
  });
  const [keysList, setKeysList] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');

  // New Key Form State (Admin Control Panel)
  const [newKey, setNewKey] = useState('');
  const [newVal, setNewVal] = useState('');
  const [newTtl, setNewTtl] = useState('-1');

  // CLI Terminal Console States (Admin Dashboard)
  const [terminalLines, setTerminalLines] = useState([
    { text: 'Jedis Web CLI version 1.0.0. Enter commands below.', type: 'secondary' }
  ]);
  const [terminalInput, setTerminalInput] = useState('');
  const [commandHistory, setCommandHistory] = useState([]);
  const [historyIndex, setHistoryIndex] = useState(-1);
  const terminalEndRef = useRef(null);

  // Landing Page Interactive Playground States
  const [playLines, setPlayLines] = useState([
    { text: 'Jedis Playground Terminal. Try database commands directly here!', type: 'secondary' },
    { text: 'Tip: Type "HELP" to see available options.', type: 'success' }
  ]);
  const [playInput, setPlayInput] = useState('');
  const playEndRef = useRef(null);

  // Demo Database for offline sandbox mode
  const [demoDb, setDemoDb] = useState({
    'greeting': 'Hello from Jedis Demo Mode!',
    'counter': '10',
    'sample:key': 'value'
  });

  // Routing navigation helper
  const navigateTo = (mode, path) => {
    window.history.pushState(null, '', path);
    setCurrentMode(mode);
    setLoginError('');
    if (mode === 'admin') {
      setAdminView('overview');
    }
  };

  // Sync state with browser back/forward buttons
  useEffect(() => {
    const handlePopState = () => {
      const path = window.location.pathname;
      const authed = localStorage.getItem('jedis_admin_logged_in') === 'true';
      if (path.startsWith('/admin')) {
        setCurrentMode(authed ? 'admin' : 'login');
      } else {
        setCurrentMode('user');
      }
    };
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  // Check connection to Java backend
  const checkConnection = useCallback(async () => {
    try {
      const res = await fetch('/api/info');
      if (res.ok) {
        const data = await res.json();
        setServerInfo(data);
        setIsLive(true);
      } else {
        setIsLive(false);
      }
    } catch {
      setIsLive(false);
    }
  }, []);

  // Fetch keys list from backend
  const fetchKeys = useCallback(async () => {
    if (!isLive) return;
    try {
      const res = await fetch('/api/keys');
      if (res.ok) {
        const data = await res.json();
        setKeysList(data.keys || []);
      }
    } catch (e) {
      console.error('Error fetching keys:', e);
    }
  }, [isLive]);

  // Polling for live status
  useEffect(() => {
    checkConnection();
    const interval = setInterval(() => {
      checkConnection();
      if (isLive) {
        fetchKeys();
      }
    }, 3000);
    return () => clearInterval(interval);
  }, [isLive, checkConnection, fetchKeys]);

  // Initial fetch when switching to admin views
  useEffect(() => {
    if (currentMode === 'admin') {
      checkConnection();
      fetchKeys();
    }
  }, [currentMode, adminView, checkConnection, fetchKeys]);

  // Scroll CLI terminal to bottom
  useEffect(() => {
    if (isTerminalInitial.current) {
      isTerminalInitial.current = false;
      return;
    }
    if (terminalEndRef.current) {
      terminalEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [terminalLines]);

  // Scroll playground terminal to bottom
  useEffect(() => {
    if (isPlayInitial.current) {
      isPlayInitial.current = false;
      return;
    }
    if (playEndRef.current) {
      playEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [playLines]);

  // Format bytes
  const formatBytes = (bytes) => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  // Format uptime
  const formatUptime = (seconds) => {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    return `${h > 0 ? h + 'h ' : ''}${m > 0 ? m + 'm ' : ''}${s}s`;
  };

  // Helper to parse double quotes in command parsing
  const parseCommandText = (commandText) => {
    const list = [];
    let current = '';
    let inQuotes = false;
    for (let i = 0; i < commandText.length; i++) {
      const c = commandText.charAt(i);
      if (c === '"') {
        inQuotes = !inQuotes;
      } else if (c === ' ' && !inQuotes) {
        if (current.length > 0) {
          list.push(current);
          current = '';
        }
      } else {
        current += c;
      }
    }
    if (current.length > 0) {
      list.push(current);
    }
    return list;
  };

  // Mock Command Runner for demo/offline mode
  const runMockCommand = (cmdName, parts) => {
    switch (cmdName) {
      case 'PING':
        return parts[1] ? `"${parts[1]}"` : 'PONG';
      case 'SET':
        if (parts.length < 3) return '(error) ERR wrong number of arguments for \'set\' command';
        const key = parts[1];
        const val = parts[2];
        setDemoDb(prev => ({ ...prev, [key]: val }));
        return 'OK';
      case 'GET':
        if (parts.length < 2) return '(error) ERR wrong number of arguments for \'get\' command';
        return demoDb[parts[1]] !== undefined ? `"${demoDb[parts[1]]}"` : '(nil)';
      case 'DEL':
        if (parts.length < 2) return '(error) ERR wrong number of arguments for \'del\' command';
        let deleted = 0;
        const newDb = { ...demoDb };
        for (let i = 1; i < parts.length; i++) {
          if (newDb[parts[i]] !== undefined) {
            delete newDb[parts[i]];
            deleted++;
          }
        }
        setDemoDb(newDb);
        return `(integer) ${deleted}`;
      case 'EXISTS':
        if (parts.length < 2) return '(error) ERR wrong number of arguments for \'exists\' command';
        return demoDb[parts[1]] !== undefined ? '(integer) 1' : '(integer) 0';
      case 'KEYS':
        if (parts.length < 2) return '(error) ERR wrong number of arguments for \'keys\' command';
        const pattern = parts[1];
        const matching = Object.keys(demoDb).filter(k => {
          if (pattern === '*') return true;
          const regex = new RegExp('^' + pattern.replace(/\*/g, '.*') + '$');
          return regex.test(k);
        });
        if (matching.length === 0) return '(empty array)';
        return matching.map((k, i) => `${i + 1}) "${k}"`).join('\n');
      case 'INCR':
        if (parts.length < 2) return '(error) ERR wrong number of arguments for \'incr\' command';
        const curVal = parseInt(demoDb[parts[1]] || '0', 10);
        if (isNaN(curVal)) return '(error) ERR value is not an integer';
        const incVal = curVal + 1;
        setDemoDb(prev => ({ ...prev, [parts[1]]: String(incVal) }));
        return `(integer) ${incVal}`;
      case 'DECR':
        if (parts.length < 2) return '(error) ERR wrong number of arguments for \'decr\' command';
        const curValDec = parseInt(demoDb[parts[1]] || '0', 10);
        if (isNaN(curValDec)) return '(error) ERR value is not an integer';
        const decVal = curValDec - 1;
        setDemoDb(prev => ({ ...prev, [parts[1]]: String(decVal) }));
        return `(integer) ${decVal}`;
      case 'INFO':
        return `# Server\nredis_version:1.0.0\nprocess_id:23812\nuptime_in_seconds:3600\nconnected_clients:1\nused_memory:${formatBytes(Object.keys(demoDb).length * 150 + 20480)}\nrole:master\ndemo_mode:active`;
      default:
        return `(error) ERR unknown command '${cmdName?.toLowerCase()}'`;
    }
  };

  // Execute terminal CLI commands (Admin Dashboard CLI view)
  const executeCommand = async (cmdString) => {
    const trimmedCmd = cmdString.trim();
    if (!trimmedCmd) return;

    const newHistory = [...commandHistory, trimmedCmd];
    setCommandHistory(newHistory);
    setHistoryIndex(newHistory.length);

    setTerminalLines(prev => [...prev, { text: `jedis> ${trimmedCmd}`, type: 'input' }]);

    const parts = parseCommandText(trimmedCmd);
    const cmdName = parts[0]?.toUpperCase();

    if (cmdName === 'CLEAR') {
      setTerminalLines([{ text: 'Terminal cleared.', type: 'secondary' }]);
      return;
    }

    if (cmdName === 'HELP') {
      setTerminalLines(prev => [
        ...prev,
        { text: 'Supported Commands:', type: 'success' },
        { text: '  - PING [msg]', type: 'output' },
        { text: '  - SET key value', type: 'output' },
        { text: '  - GET key', type: 'output' },
        { text: '  - DEL key', type: 'output' },
        { text: '  - EXISTS key', type: 'output' },
        { text: '  - KEYS pattern', type: 'output' },
        { text: '  - INCR key / DECR key', type: 'output' },
        { text: '  - INFO', type: 'output' }
      ]);
      return;
    }

    if (isLive) {
      try {
        const res = await fetch('/api/command', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ command: trimmedCmd })
        });
        const data = await res.json();
        if (data.error) {
          setTerminalLines(prev => [...prev, { text: data.error, type: 'error' }]);
        } else {
          setTerminalLines(prev => [...prev, { text: data.response, type: 'output' }]);
        }
        fetchKeys();
      } catch (e) {
        setTerminalLines(prev => [...prev, { text: `(error) Connection failure: ${e.message}`, type: 'error' }]);
      }
    } else {
      setTimeout(() => {
        const response = runMockCommand(cmdName, parts);
        if (response.startsWith('(error)')) {
          setTerminalLines(prev => [...prev, { text: response, type: 'error' }]);
        } else {
          setTerminalLines(prev => [...prev, { text: response, type: 'output' }]);
        }
      }, 50);
    }
  };

  // Run database commands in the Landing Page playground CLI
  const executePlaygroundCommand = async (cmdString) => {
    const trimmedCmd = cmdString.trim();
    if (!trimmedCmd) return;

    setPlayLines(prev => [...prev, { text: `jedis> ${trimmedCmd}`, type: 'input' }]);

    const parts = parseCommandText(trimmedCmd);
    const cmdName = parts[0]?.toUpperCase();

    if (cmdName === 'CLEAR') {
      setPlayLines([{ text: 'Console cleared.', type: 'secondary' }]);
      return;
    }

    if (cmdName === 'HELP') {
      setPlayLines(prev => [
        ...prev,
        { text: 'Supported Playground Commands:', type: 'success' },
        { text: '  - PING [message]    -> returns PONG or the message', type: 'output' },
        { text: '  - SET key value    -> sets key to value', type: 'output' },
        { text: '  - GET key          -> retrieves value of key', type: 'output' },
        { text: '  - DEL key          -> deletes the key', type: 'output' },
        { text: '  - KEYS *           -> lists all keys', type: 'output' },
        { text: '  - INFO             -> server diagnostic metrics', type: 'output' },
        { text: '  - CLEAR            -> clears terminal output', type: 'output' }
      ]);
      return;
    }

    if (isLive) {
      try {
        const res = await fetch('/api/command', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ command: trimmedCmd })
        });
        const data = await res.json();
        if (data.error) {
          setPlayLines(prev => [...prev, { text: data.error, type: 'error' }]);
        } else {
          setPlayLines(prev => [...prev, { text: data.response, type: 'output' }]);
        }
      } catch (e) {
        setPlayLines(prev => [...prev, { text: `(error) Connection failure: ${e.message}`, type: 'error' }]);
      }
    } else {
      setTimeout(() => {
        const response = runMockCommand(cmdName, parts);
        if (response.startsWith('(error)')) {
          setPlayLines(prev => [...prev, { text: response, type: 'error' }]);
        } else {
          setPlayLines(prev => [...prev, { text: response, type: 'output' }]);
        }
      }, 50);
    }
  };

  // Add Key Form (Admin Dashboard)
  const handleAddKey = async (e) => {
    e.preventDefault();
    if (!newKey.trim() || !newVal.trim()) {
      alert('Key and Value are required!');
      return;
    }

    if (isLive) {
      try {
        const res = await fetch('/api/key', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ key: newKey, value: newVal, ttl: parseInt(newTtl) })
        });
        if (res.ok) {
          setNewKey('');
          setNewVal('');
          setNewTtl('-1');
          fetchKeys();
          checkConnection();
        } else {
          const data = await res.json();
          alert('Failed to add key: ' + (data.error || 'Unknown error'));
        }
      } catch (err) {
        alert('Connection error: ' + err.message);
      }
    } else {
      setDemoDb(prev => ({ ...prev, [newKey]: newVal }));
      setNewKey('');
      setNewVal('');
      setNewTtl('-1');
    }
  };

  // Delete Key (Admin Dashboard)
  const handleDeleteKey = async (key) => {
    if (!confirm(`Are you sure you want to delete "${key}"?`)) return;

    if (isLive) {
      try {
        const res = await fetch(`/api/key?key=${encodeURIComponent(key)}`, {
          method: 'DELETE'
        });
        if (res.ok) {
          fetchKeys();
          checkConnection();
        } else {
          alert('Failed to delete key');
        }
      } catch (err) {
        alert('Connection error: ' + err.message);
      }
    } else {
      const newDb = { ...demoDb };
      delete newDb[key];
      setDemoDb(newDb);
    }
  };

  // Filter keys list in Admin
  const getFilteredKeys = () => {
    if (isLive) {
      return keysList.filter(k => k.key.toLowerCase().includes(searchQuery.toLowerCase()));
    } else {
      return Object.entries(demoDb)
        .filter(([k]) => k.toLowerCase().includes(searchQuery.toLowerCase()))
        .map(([k, v]) => ({ key: k, type: 'STRING', value: v, ttl: -1 }));
    }
  };

  // Handle up/down keys for Admin CLI terminal
  const handleTerminalKeyDown = (e) => {
    if (e.key === 'ArrowUp') {
      e.preventDefault();
      if (historyIndex > 0) {
        const nextIndex = historyIndex - 1;
        setHistoryIndex(nextIndex);
        setTerminalInput(commandHistory[nextIndex]);
      }
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (historyIndex < commandHistory.length - 1) {
        const nextIndex = historyIndex + 1;
        setHistoryIndex(nextIndex);
        setTerminalInput(commandHistory[nextIndex]);
      } else {
        setHistoryIndex(commandHistory.length);
        setTerminalInput('');
      }
    }
  };

  // Handle Admin Authentication Login
  const handleLoginSubmit = (e) => {
    e.preventDefault();
    if (usernameInput.trim() === 'admin' && passwordInput === 'admin') {
      localStorage.setItem('jedis_admin_logged_in', 'true');
      navigateTo('admin', '/admin');
      setUsernameInput('');
      setPasswordInput('');
      setLoginError('');
    } else {
      setLoginError('Invalid username or password credentials. Hint: use admin/admin');
    }
  };

  // Handle Admin Log Out
  const handleLogout = () => {
    localStorage.removeItem('jedis_admin_logged_in');
    navigateTo('user', '/');
  };

  return (
    <div className={`app-container ${currentMode === 'admin' ? 'admin-theme' : 'user-theme'}`}>
      {/* Background Mesh Glow Blobs */}
      <div className="glow-blob blob-purple"></div>
      <div className="glow-blob blob-blue"></div>

      {/* Background Animation Canvas */}
      <BackgroundAnimation />

      {/* ── 1. PUBLIC USER WEBSITE (Single Beautiful Product Page) ── */}
      {currentMode === 'user' && (
        <>
          {/* Public Navbar (Anchor based links) */}
          <nav className="navbar">
            <div className="nav-container">
              <div className="nav-left">
                <div className="brand-logo-container" onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}>
                  <div className="brand-logo-box">J</div>
                  <span className="brand-logo-text">Jedis DB</span>
                </div>
                <ul className="nav-links">
                  <li><a href="#features" className="nav-link">Features</a></li>
                  <li><a href="#installation" className="nav-link">Installation</a></li>
                  <li><a href="#ui-playground" className="nav-link">UI Console</a></li>
                  <li><a href="#usage" className="nav-link">Usage</a></li>
                  <li><a href="#api-access" className="nav-link">API Access</a></li>
                </ul>
              </div>
              <div className="nav-right">
                <a href="https://github.com/DipanshuAmbilkar03/Jedis" target="_blank" rel="noopener noreferrer" className="nav-github-btn">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/></svg>
                  GitHub
                </a>
              </div>
            </div>
          </nav>

          <main className="landing-main-wrapper">
            
            {/* HERO SECTION */}
            <header className="hero-section">
              <div className="hero-badge">
                <span className="badge-dot"></span>
                <span>Pure Java • RESP2 Native Compatibility</span>
              </div>
              <h1 className="hero-title">
                High-Performance <br />
                <span className="text-gradient">In-Memory Database</span>
              </h1>
              <p className="hero-subtitle">
                A ground-up implementation of a Redis-like store built in Java. Accepts connections, 
                parses RESP2 protocol streams, schedules dual persistence mechanisms, and manages 
                multi-type data structures with zero external dependencies.
              </p>
              <div className="hero-actions">
                <a href="#installation" className="btn-primary-link">Get Started</a>
                <a href="#ui-playground" className="btn-secondary-link">Try Live Console</a>
              </div>

              {/* Status Indicator */}
              <div className="hero-status-row">
                <div className="status-indicator">
                  <span className={`indicator-lamp ${isLive ? 'online' : 'offline'}`}></span>
                  <span>Local Server: <strong>{isLive ? 'CONNECTED (Port ' + serverInfo.port + ')' : 'OFFLINE (Demo Mode)'}</strong></span>
                </div>
              </div>
            </header>

            {/* FEATURES SECTION */}
            <section id="features" className="landing-section">
              <div className="section-head">
                <span className="tagline">System Engine</span>
                <h2>Built From First Principles</h2>
                <p>Hand-written socket communication loops, wire parsers, and custom scheduling algorithms.</p>
              </div>

              <div className="features-grid">
                <div className="feature-card">
                  <span className="card-icon">⚡</span>
                  <h3>RESP2 Wire Protocol</h3>
                  <p>Speaks the native Redis Serialization Protocol. Fully compatible with `redis-cli`, standard client libraries, and desktop managers.</p>
                </div>
                <div className="feature-card">
                  <span className="card-icon">🔌</span>
                  <h3>NIO Socket Event Loop</h3>
                  <p>Handles concurrent TCP client socket multiplexing utilizing Java non-blocking selector loops for low latency under load.</p>
                </div>
                <div className="feature-card">
                  <span className="card-icon">💾</span>
                  <h3>Dual persistence</h3>
                  <p>Guarantees data durability through write-ahead logging (Append Only File) and periodic memory state snapshots (RDB).</p>
                </div>
                <div className="feature-card">
                  <span className="card-icon">⏰</span>
                  <h3>Active Expiry Manager</h3>
                  <p>Locks and deletes expired keys using passive checks on access combined with periodic active background sampling loops.</p>
                </div>
                <div className="feature-card">
                  <span className="card-icon">💬</span>
                  <h3>Pub/Sub Messaging</h3>
                  <p>Real-time messaging channels allowing fire-and-forget subscription delivery across concurrent client sockets.</p>
                </div>
                <div className="feature-card">
                  <span className="card-icon">🔄</span>
                  <h3>ACID Transactions</h3>
                  <p>Queues commands sequentially via `MULTI` and executes them atomically using block locks via `EXEC` or discards via `DISCARD`.</p>
                </div>
              </div>
            </section>

            {/* INSTALLATION SECTION */}
            <section id="installation" className="landing-section">
              <div className="section-head">
                <span className="tagline">Get Started</span>
                <h2>Simple 30-Second Startup</h2>
                <p>Compile the lightweight codebase using Apache Maven and execute the runnable JAR file.</p>
              </div>

              <div className="installation-box-wrapper">
                <div className="mockup-window">
                  <div className="mockup-header" style={{ paddingLeft: '1rem' }}>
                    <div className="mockup-dots">
                      <span className="mockup-dot dot-red"></span>
                      <span className="mockup-dot dot-yellow"></span>
                      <span className="mockup-dot dot-green"></span>
                    </div>
                    <span className="mock-title">Terminal</span>
                  </div>
                  <div className="mockup-body code-install-body">
                    <div className="term-line"><span className="term-prompt">$</span> git clone https://github.com/DipanshuAmbilkar03/Jedis.git</div>
                    <div className="term-line"><span className="term-prompt">$</span> cd mini-redis</div>
                    <div className="term-line"><span className="term-prompt">$</span> mvn clean package</div>
                    <div className="term-line"><span className="term-prompt">$</span> java -jar target/mini-redis-1.0.0.jar --port 6380</div>
                    <br />
                    <div className="term-line output-text"><span className="term-success">✓</span> Jedis Database engine initialized on port 6380.</div>
                    <div className="term-line output-text"><span className="term-success">✓</span> Web Dashboard Console active on port 8080.</div>
                  </div>
                </div>
              </div>
            </section>

            {/* UI PLAYGROUND SECTION */}
            <section id="ui-playground" className="landing-section">
              <div className="section-head">
                <span className="tagline">Interactive Shell</span>
                <h2>Embedded Database Playground</h2>
                <p>Run commands directly inside this page to insert, query, or delete keys from the data store.</p>
              </div>

              <div className="terminal-playground-wrapper">
                <div className="mockup-window" style={{ maxWidth: '800px', margin: '0 auto' }}>
                  <div className="mockup-header" style={{ paddingLeft: '1rem' }}>
                    <div className="mockup-dots">
                      <span className="mockup-dot dot-red"></span>
                      <span className="mockup-dot dot-yellow"></span>
                      <span className="mockup-dot dot-green"></span>
                    </div>
                    <span className="mock-title">jedis-cli -p {isLive ? serverInfo.port : 6380} {isLive ? '[LIVE]' : '[DEMO]'}</span>
                  </div>
                  <div className="mockup-body playground-body" style={{ minHeight: '300px', display: 'flex', flexDirection: 'column' }}>
                    
                    <div className="playground-output-scroll" style={{ flex: 1, overflowY: 'auto' }}>
                      {playLines.map((line, idx) => (
                        <div
                          key={idx}
                          className={`t-line ${
                            line.type === 'input' ? 't-input-line' :
                            line.type === 'error' ? 't-error-line' :
                            line.type === 'success' ? 't-success-line' : 't-output-line'
                          }`}
                        >
                          {line.text}
                        </div>
                      ))}
                      <div ref={playEndRef} />
                    </div>

                    <div className="terminal-prompt-box" style={{ marginTop: '1rem', background: 'rgba(0,0,0,0.3)' }}>
                      <span className="prompt-label">jedis&gt;</span>
                      <input
                        type="text"
                        className="terminal-input"
                        value={playInput}
                        onChange={(e) => setPlayInput(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') {
                            executePlaygroundCommand(playInput);
                            setPlayInput('');
                          }
                        }}
                        placeholder="Type database command e.g. SET user 'Alice', GET user, KEYS *..."
                      />
                    </div>
                  </div>
                </div>
              </div>
            </section>

            {/* USAGE SECTION */}
            <section id="usage" className="landing-section">
              <div className="section-head">
                <span className="tagline">Client Integration</span>
                <h2>Simple Client API Integration</h2>
                <p>Connect using standard libraries in Java (such as Redis Jedis client) or the official `redis-cli` tool.</p>
              </div>

              <div className="usage-grid">
                <div className="usage-card">
                  <h3>Java Client Integration Example:</h3>
                  <pre className="docs-code-block" style={{ fontSize: '0.85rem' }}>
{`import redis.clients.jedis.Jedis;

public class App {
    public static void main(String[] args) {
        // Connect to Jedis database on port 6380
        try (Jedis jedis = new Jedis("localhost", 6380)) {
            // Set Key
            jedis.set("user:id", "john_doe");
            
            // Fetch Key
            String value = jedis.get("user:id");
            System.out.println("Fetched: " + value);
        }
    }
}`}
                  </pre>
                </div>

                <div className="usage-card" style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
                  <h3>Connecting via Official Terminal Client:</h3>
                  <p className="card-desc">Since Jedis database natively speaks the RESP2 serialization protocol, you can use the official Redis CLI client seamlessly.</p>
                  <pre className="docs-code-block" style={{ fontSize: '0.85rem' }}>
{`$ redis-cli -p 6380
127.0.0.1:6380> PING
PONG
127.0.0.1:6380> SET counter 100
OK
127.0.0.1:6380> INCR counter
(integer) 101`}
                  </pre>
                </div>
              </div>
            </section>

            {/* HOW ACCESS / API ACCESS SECTION */}
            <section id="api-access" className="landing-section" style={{ borderBottom: 'none' }}>
              <div className="section-head">
                <span className="tagline">REST Integration</span>
                <h2>REST API Endpoint Specifications</h2>
                <p>Integrate with external dashboards or scripts using the built-in HTTP server REST endpoints.</p>
              </div>

              <div className="api-specs-container">
                <div className="admin-table-container">
                  <table className="admin-table">
                    <thead>
                      <tr>
                        <th>Endpoint Path</th>
                        <th>HTTP Method</th>
                        <th>Description</th>
                        <th>Sample Return JSON</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td className="bold-key">`/api/info`</td>
                        <td><span className="badge-string">GET</span></td>
                        <td>Uptime, connected clients, key stats, eviction policy details.</td>
                        <td className="mono-val"><code>{"{\"keys\": 10, \"uptimeSeconds\": 120, ...}"}</code></td>
                      </tr>
                      <tr>
                        <td className="bold-key">`/api/keys`</td>
                        <td><span className="badge-string">GET</span></td>
                        <td>List of all database keys in store with preview details.</td>
                        <td className="mono-val"><code>{"{\"keys\": [{\"key\": \"user\", \"type\": \"STRING\"}]}"}</code></td>
                      </tr>
                      <tr>
                        <td className="bold-key">`/api/command`</td>
                        <td><span className="badge-string" style={{ backgroundColor: 'rgba(46,204,113,0.15)', color: '#2ecc71', borderColor: 'rgba(46,204,113,0.2)' }}>POST</span></td>
                        <td>Execute raw RESP2 commands against Jedis data storage.</td>
                        <td className="mono-val"><code>{"{\"response\": \"PONG\"}"}</code></td>
                      </tr>
                      <tr>
                        <td className="bold-key">`/api/key`</td>
                        <td><span className="badge-string" style={{ backgroundColor: 'rgba(46,204,113,0.15)', color: '#2ecc71', borderColor: 'rgba(46,204,113,0.2)' }}>POST</span></td>
                        <td>Insert or set a standard string key-value pair.</td>
                        <td className="mono-val"><code>{"{\"success\": true}"}</code></td>
                      </tr>
                      <tr>
                        <td className="bold-key">`/api/key`</td>
                        <td><span className="badge-string" style={{ backgroundColor: 'rgba(255,42,68,0.15)', color: '#ff2a44', borderColor: 'rgba(255,42,68,0.2)' }}>DELETE</span></td>
                        <td>Delete a key name from the database.</td>
                        <td className="mono-val"><code>{"{\"success\": true}"}</code></td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </section>

          </main>

          {/* Public Footer */}
          <footer className="landing-footer">
            <div className="footer-content-row">
              <div className="brand-logo-container">
                <div className="brand-logo-box">J</div>
                <span className="brand-logo-text">Jedis DB</span>
              </div>
              <p className="copyright">&copy; 2026 Jedis In-Memory Database Platform. All rights reserved.</p>
            </div>
          </footer>
        </>
      )}

      {/* ── 2. GLOSHY LOGIN CARD SCREEN ── */}
      {currentMode === 'login' && (
        <div className="login-screen-wrapper">
          <div className="login-glass-card">
            <div className="login-brand" onClick={() => navigateTo('user', '/')}>
              <div className="brand-logo-box">J</div>
              <span className="brand-logo-text">Jedis Console</span>
            </div>
            
            <p className="login-desc">Authenticate to access the server administration console.</p>

            <form onSubmit={handleLoginSubmit} className="login-form">
              {loginError && <div className="login-error-msg">⚠️ {loginError}</div>}
              
              <div className="form-group">
                <label>Username</label>
                <input
                  type="text"
                  className="login-control-input"
                  placeholder="Enter your username"
                  value={usernameInput}
                  onChange={(e) => setUsernameInput(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label>Password</label>
                <input
                  type="password"
                  className="login-control-input"
                  placeholder="Enter your password"
                  value={passwordInput}
                  onChange={(e) => setPasswordInput(e.target.value)}
                  required
                />
              </div>

              <button type="submit" className="login-submit-btn">Sign In</button>
            </form>

            <button 
              className="back-to-landing-btn"
              onClick={() => navigateTo('user', '/')}
            >
              ← Back to Product Page
            </button>
          </div>
        </div>
      )}

      {/* ── 3. PRIVATE DEVELOPER ADMIN DASHBOARD VIEW ── */}
      {currentMode === 'admin' && (
        <div className="admin-layout-container">
          
          {/* Admin Sidebar */}
          <aside className="admin-sidebar">
            <div className="sidebar-brand" onClick={() => navigateTo('admin', '/admin')}>
              <div className="brand-logo-box">J</div>
              <div className="brand-details">
                <span className="brand-title">Jedis DB</span>
                <span className="brand-subtitle">Console Panel</span>
              </div>
            </div>

            <ul className="sidebar-links">
              <li>
                <button 
                  className={`sidebar-btn ${adminView === 'overview' ? 'active' : ''}`}
                  onClick={() => setAdminView('overview')}
                >
                  <span className="sb-icon">📊</span> Overview
                </button>
              </li>
              <li>
                <button 
                  className={`sidebar-btn ${adminView === 'explorer' ? 'active' : ''}`}
                  onClick={() => setAdminView('explorer')}
                >
                  <span className="sb-icon">🗝️</span> Key Explorer
                </button>
              </li>
              <li>
                <button 
                  className={`sidebar-btn ${adminView === 'cli' ? 'active' : ''}`}
                  onClick={() => setAdminView('cli')}
                >
                  <span className="sb-icon">🐚</span> Live CLI
                </button>
              </li>
              <li>
                <button 
                  className={`sidebar-btn ${adminView === 'config' ? 'active' : ''}`}
                  onClick={() => setAdminView('config')}
                >
                  <span className="sb-icon">⚙️</span> Persistence Config
                </button>
              </li>
            </ul>

            <div className="sidebar-footer">
              <button 
                className="exit-to-app-btn"
                style={{ marginBottom: '0.5rem' }}
                onClick={() => navigateTo('user', '/')}
              >
                ← Exit to User App
              </button>
              <button 
                className="exit-to-app-btn"
                style={{ backgroundColor: 'rgba(255, 42, 68, 0.1)', color: 'var(--accent-red)', borderColor: 'rgba(255, 42, 68, 0.2)' }}
                onClick={handleLogout}
              >
                🔒 Sign Out
              </button>
            </div>
          </aside>

          {/* Admin Workspace Content */}
          <section className="admin-workspace">
            
            {/* Top Workspace status bar */}
            <header className="workspace-header">
              <div className="ws-title-area">
                <h2>
                  {adminView === 'overview' && 'Database Overview'}
                  {adminView === 'explorer' && 'Key Explorer'}
                  {adminView === 'cli' && 'Interactive Live CLI'}
                  {adminView === 'config' && 'Persistence & Engine Configuration'}
                </h2>
              </div>
              <div className={`status-badge-new ${!isLive ? 'offline' : ''}`}>
                <div className="status-new-dot"></div>
                <span>{isLive ? 'CONNECTED' : 'SANDBOX MODE (OFFLINE)'}</span>
              </div>
            </header>

            <div className="workspace-content-body">

              {/* OVERVIEW PANEL */}
              {adminView === 'overview' && (
                <div className="overview-tab-content">
                  
                  {/* Stats Grid */}
                  <div className="admin-metrics-grid">
                    <div className="admin-metric-card">
                      <div className="m-icon-wrap m-icon-keys">🗂️</div>
                      <div className="m-text">
                        <span className="m-label">Total Keys</span>
                        <span className="m-value">{isLive ? serverInfo.keys : Object.keys(demoDb).length}</span>
                      </div>
                    </div>
                    <div className="admin-metric-card">
                      <div className="m-icon-wrap m-icon-memory">💾</div>
                      <div className="m-text">
                        <span className="m-label">Memory Usage</span>
                        <span className="m-value">{isLive ? formatBytes(serverInfo.memoryUsedBytes) : '15 KB'}</span>
                      </div>
                    </div>
                    <div className="admin-metric-card">
                      <div className="m-icon-wrap m-icon-clients">🔌</div>
                      <div className="m-text">
                        <span className="m-label">Connected Clients</span>
                        <span className="m-value">{isLive ? serverInfo.connectedClients : 1}</span>
                      </div>
                    </div>
                    <div className="admin-metric-card">
                      <div className="m-icon-wrap m-icon-uptime">⏱️</div>
                      <div className="m-text">
                        <span className="m-label">Server Uptime</span>
                        <span className="m-value">{isLive ? formatUptime(serverInfo.uptimeSeconds) : '48m 20s'}</span>
                      </div>
                    </div>
                  </div>

                  {/* Quick Actions — What You Can Do */}
                  <div className="quick-actions-section">
                    <h3 className="section-label">Quick Actions</h3>
                    <div className="quick-actions-grid">
                      <button className="action-card" onClick={() => setAdminView('explorer')}>
                        <div className="action-icon action-icon-explorer">🗝️</div>
                        <div className="action-info">
                          <h4>Key Explorer</h4>
                          <p>Browse, search, add, and delete keys stored in the database.</p>
                        </div>
                        <span className="action-arrow">→</span>
                      </button>
                      <button className="action-card" onClick={() => setAdminView('cli')}>
                        <div className="action-icon action-icon-cli">⚡</div>
                        <div className="action-info">
                          <h4>Live CLI Console</h4>
                          <p>Execute raw RESP2 commands directly from your browser.</p>
                        </div>
                        <span className="action-arrow">→</span>
                      </button>
                      <button className="action-card" onClick={() => setAdminView('config')}>
                        <div className="action-icon action-icon-config">⚙️</div>
                        <div className="action-info">
                          <h4>Persistence Config</h4>
                          <p>View AOF write-ahead logging and RDB snapshot settings.</p>
                        </div>
                        <span className="action-arrow">→</span>
                      </button>
                      <button className="action-card" onClick={() => { checkConnection(); fetchKeys(); }}>
                        <div className="action-icon action-icon-refresh">🔄</div>
                        <div className="action-info">
                          <h4>Refresh Status</h4>
                          <p>Re-check server connection and reload key data.</p>
                        </div>
                        <span className="action-arrow">→</span>
                      </button>
                    </div>
                  </div>

                  {/* Diagnostics */}
                  <div className="diagnostics-section">
                    <h3 className="section-label">Server Diagnostics</h3>
                    <div className="diagnostics-row-grid">
                      <div className="diagnostic-glass-card">
                        <h3>⚡ Active Connection Protocol</h3>
                        <p className="card-desc">Monitoring active TCP client sockets and protocol handlers in real time.</p>
                        <div className="diagnostic-list">
                          <div className="diag-item">
                            <span>Connection Eviction Status:</span>
                            <span className="status-green">ACTIVE</span>
                          </div>
                          <div className="diag-item">
                            <span>Eviction Algorithm:</span>
                            <strong>{isLive ? serverInfo.evictionPolicy : 'noeviction'}</strong>
                          </div>
                          <div className="diag-item">
                            <span>Max Network Threads:</span>
                            <strong>4 IO Loops</strong>
                          </div>
                        </div>
                      </div>

                      <div className="diagnostic-glass-card">
                        <h3>💾 Storage Snapshots & WAL</h3>
                        <p className="card-desc">Write-ahead logging and periodic memory snapshot engine status.</p>
                        <div className="diagnostic-list">
                          <div className="diag-item">
                            <span>AOF Logging (Append-Only):</span>
                            <span className={isLive ? (serverInfo.aofEnabled ? 'status-green' : 'status-red') : 'status-green'}>
                              {isLive ? (serverInfo.aofEnabled ? 'RUNNING' : 'DISABLED') : 'RUNNING'}
                            </span>
                          </div>
                          <div className="diag-item">
                            <span>RDB Snapshot Engine:</span>
                            <span className={isLive ? (serverInfo.rdbEnabled ? 'status-green' : 'status-red') : 'status-green'}>
                              {isLive ? (serverInfo.rdbEnabled ? 'ACTIVE' : 'DISABLED') : 'ACTIVE'}
                            </span>
                          </div>
                          <div className="diag-item">
                            <span>Snapshot File Path:</span>
                            <code>./dump.rdb</code>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {/* KEY EXPLORER PANEL */}
              {adminView === 'explorer' && (
                <div className="admin-panel-card panel-explorer">
                  <div className="card-header-bar">
                    <h3>🗝️ Database Keys ({getFilteredKeys().length})</h3>
                    <button onClick={() => { checkConnection(); fetchKeys(); }} className="refresh-btn">
                      ↻ Refresh Table
                    </button>
                  </div>

                  <form onSubmit={handleAddKey} className="inline-add-key-form">
                    <input
                      type="text"
                      placeholder="Key name (e.g. session:id)"
                      className="admin-input"
                      value={newKey}
                      onChange={(e) => setNewKey(e.target.value)}
                    />
                    <input
                      type="text"
                      placeholder="Value"
                      className="admin-input"
                      value={newVal}
                      onChange={(e) => setNewVal(e.target.value)}
                    />
                    <input
                      type="number"
                      placeholder="TTL (sec) e.g. 60"
                      className="admin-input"
                      value={newTtl}
                      onChange={(e) => setNewTtl(e.target.value)}
                    />
                    <button type="submit" className="add-key-btn">＋ Set Key</button>
                  </form>

                  <input
                    type="text"
                    placeholder="Filter keys by name..."
                    className="search-input"
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                  />

                  <div className="admin-table-container">
                    <table className="admin-table">
                      <thead>
                        <tr>
                          <th>Key Name</th>
                          <th>Data Type</th>
                          <th>Value Preview</th>
                          <th>TTL</th>
                          <th>Actions</th>
                        </tr>
                      </thead>
                      <tbody>
                        {getFilteredKeys().length === 0 ? (
                          <tr>
                            <td colSpan="5" className="empty-row">No keys found in data store</td>
                          </tr>
                        ) : (
                          getFilteredKeys().map((k) => (
                            <tr key={k.key}>
                              <td className="bold-key">{k.key}</td>
                              <td><span className="badge-string">{k.type}</span></td>
                              <td className="mono-val"><code>{k.value}</code></td>
                              <td className="ttl-col">{k.ttl === -1 ? 'persistent' : `${k.ttl}s`}</td>
                              <td>
                                <button onClick={() => handleDeleteKey(k.key)} className="delete-btn">
                                  🗑️
                                </button>
                              </td>
                            </tr>
                          ))
                        )}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}

              {/* CLI TERMINAL PANEL */}
              {adminView === 'cli' && (
                <div className="admin-panel-card panel-cli">
                  <div className="card-header-bar">
                    <h3>⚡ Live Web Shell Console</h3>
                    <button onClick={() => setTerminalLines([{ text: 'Console cleared.', type: 'secondary' }])} className="refresh-btn">
                      Clear Screen
                    </button>
                  </div>

                  <div className="terminal-output-panel">
                    {terminalLines.map((line, idx) => (
                      <div
                        key={idx}
                        className={`t-line ${
                          line.type === 'input' ? 't-input-line' :
                          line.type === 'error' ? 't-error-line' :
                          line.type === 'success' ? 't-success-line' : 't-output-line'
                        }`}
                      >
                        {line.text}
                      </div>
                    ))}
                    <div ref={terminalEndRef} />
                  </div>

                  <div className="terminal-prompt-box">
                    <span className="prompt-label">jedis&gt;</span>
                    <input
                      type="text"
                      className="terminal-input"
                      value={terminalInput}
                      onChange={(e) => setTerminalInput(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          executeCommand(terminalInput);
                          setTerminalInput('');
                        } else {
                          handleTerminalKeyDown(e);
                        }
                      }}
                      placeholder="Type PING, HELP, SET key value, GET key..."
                      autoFocus
                    />
                  </div>
                </div>
              )}

              {/* PERSISTENCE CONFIG PANEL */}
              {adminView === 'config' && (
                <div className="config-tab-content">
                  <div className="diagnostics-row-grid">
                    <div className="diagnostic-glass-card">
                      <h3>📂 Append Only File (AOF)</h3>
                      <p className="card-desc">AOF commits write operations to an incremental log on disk. Underneath, a background thread handles auto-rewriting when log limits exceed thresholds.</p>
                      <div className="diagnostic-list">
                        <div className="diag-item">
                          <span>AOF Enabled:</span>
                          <span className={isLive ? (serverInfo.aofEnabled ? 'status-green' : 'status-red') : 'status-green'}>
                            {isLive ? (serverInfo.aofEnabled ? 'Active' : 'Disabled') : 'Active'}
                          </span>
                        </div>
                        <div className="diag-item">
                          <span>AOF Auto Rewrite:</span>
                          <span>Every 64MB / 100% Growth</span>
                        </div>
                        <div className="diag-item">
                          <span>Fsync Policy:</span>
                          <code>everysec</code>
                        </div>
                      </div>
                    </div>

                    <div className="diagnostic-glass-card">
                      <h3>📸 Redis Database Snapshot (RDB)</h3>
                      <p className="card-desc">RDB snapshots dump the raw key-value store to disk at configured time boundaries, making database backups cheap and easy.</p>
                      <div className="diagnostic-list">
                        <div className="diag-item">
                          <span>RDB Enabled:</span>
                          <span className={isLive ? (serverInfo.rdbEnabled ? 'status-green' : 'status-red') : 'status-green'}>
                            {isLive ? (serverInfo.rdbEnabled ? 'Active' : 'Disabled') : 'Active'}
                          </span>
                        </div>
                        <div className="diag-item">
                          <span>Save Rules:</span>
                          <span>900s (1 change), 300s (10 changes)</span>
                        </div>
                        <div className="diag-item">
                          <span>RDB Checksum:</span>
                          <span>CRC64 Enabled</span>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div className="server-settings-bar">
                    <div>TCP Bind Port: <strong>{isLive ? serverInfo.port : 6380}</strong></div>
                    <div>Active Eviction Policy: <strong>{isLive ? serverInfo.evictionPolicy : 'noeviction'}</strong></div>
                    <div>Max Allocator Memory: <strong>{isLive && serverInfo.memoryMaxBytes > 0 ? formatBytes(serverInfo.memoryMaxBytes) : 'unlimited'}</strong></div>
                    <div>Replication Role: <strong>{isLive ? (serverInfo.replicaOf ? `Replica of ${serverInfo.replicaOf}` : 'Master') : 'Master'}</strong></div>
                  </div>
                </div>
              )}

            </div>
          </section>
        </div>
      )}
    </div>
  );
}
