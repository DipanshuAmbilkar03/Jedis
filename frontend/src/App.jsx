import React, { useState, useEffect, useRef } from 'react';
import './App.css';

export default function App() {
  const [view, setView] = useState('landing'); // 'landing' or 'dashboard'
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
  
  // New Key Form State
  const [newKey, setNewKey] = useState('');
  const [newVal, setNewVal] = useState('');
  const [newTtl, setNewTtl] = useState('-1');

  // Terminal States
  const [terminalLines, setTerminalLines] = useState([
    { text: 'Jedis Web CLI version 1.0.0. Enter commands below.', type: 'secondary' }
  ]);
  const [terminalInput, setTerminalInput] = useState('');
  const [commandHistory, setCommandHistory] = useState([]);
  const [historyIndex, setHistoryIndex] = useState(-1);
  const terminalEndRef = useRef(null);

  // Mock database for offline/demo mode
  const [demoDb, setDemoDb] = useState({
    'greeting': 'Hello from Jedis Demo Mode!',
    'counter': '10',
    'sample:key': 'value'
  });

  // Check connection to Java backend
  const checkConnection = async () => {
    try {
      const res = await fetch('/api/info');
      if (res.ok) {
        const data = await res.json();
        setServerInfo(data);
        setIsLive(true);
      } else {
        setIsLive(false);
      }
    } catch (e) {
      setIsLive(false);
    }
  };

  // Fetch keys list from backend
  const fetchKeys = async () => {
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
  };

  // Polling for live mode
  useEffect(() => {
    checkConnection();
    const interval = setInterval(() => {
      checkConnection();
      if (isLive) {
        fetchKeys();
      }
    }, 3000);
    return () => clearInterval(interval);
  }, [isLive]);

  // Initial fetch when switching to dashboard
  useEffect(() => {
    if (view === 'dashboard') {
      checkConnection();
      fetchKeys();
    }
  }, [view]);

  // Scroll terminal to bottom
  useEffect(() => {
    if (terminalEndRef.current) {
      terminalEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [terminalLines]);

  // Format memory bytes
  const formatBytes = (bytes) => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  // Format uptime seconds
  const formatUptime = (seconds) => {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    return `${h > 0 ? h + 'h ' : ''}${m > 0 ? m + 'm ' : ''}${s}s`;
  };

  // Execute terminal command
  const executeCommand = async (cmdString) => {
    const trimmedCmd = cmdString.trim();
    if (!trimmedCmd) return;

    // Save to history
    const newHistory = [...commandHistory, trimmedCmd];
    setCommandHistory(newHistory);
    setHistoryIndex(newHistory.length);

    setTerminalLines(prev => [...prev, { text: `jedis> ${trimmedCmd}`, type: 'input' }]);

    const parts = parseCommandText(trimmedCmd);
    const cmdName = parts[0]?.toUpperCase();

    if (cmdName === 'CLEAR') {
      setTerminalLines([{ text: 'Terminal cleared. Ready.', type: 'secondary' }]);
      return;
    }

    if (cmdName === 'HELP') {
      setTerminalLines(prev => [
        ...prev,
        { text: 'Supported Commands:', type: 'success' },
        { text: '  - PING [message]', type: 'output' },
        { text: '  - SET key value [EX seconds]', type: 'output' },
        { text: '  - GET key', type: 'output' },
        { text: '  - DEL key', type: 'output' },
        { text: '  - EXISTS key', type: 'output' },
        { text: '  - KEYS pattern', type: 'output' },
        { text: '  - INCR key / DECR key', type: 'output' },
        { text: '  - INFO', type: 'output' },
        { text: '  - CLEAR (clear screen)', type: 'output' }
      ]);
      return;
    }

    if (isLive) {
      // Execute live against Java backend
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
      // Mock execution in Demo Mode
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

  // Local Mock Command Executor for offline Demo Mode
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
      case 'INCR':
        if (parts.length < 2) return '(error) ERR wrong number of arguments for \'incr\' command';
        const curVal = parseInt(demoDb[parts[1]] || '0', 10);
        if (isNaN(curVal)) return '(error) ERR value is not an integer or out of range';
        const incVal = curVal + 1;
        setDemoDb(prev => ({ ...prev, [parts[1]]: String(incVal) }));
        return `(integer) ${incVal}`;
      case 'DECR':
        if (parts.length < 2) return '(error) ERR wrong number of arguments for \'decr\' command';
        const curValDec = parseInt(demoDb[parts[1]] || '0', 10);
        if (isNaN(curValDec)) return '(error) ERR value is not an integer or out of range';
        const decVal = curValDec - 1;
        setDemoDb(prev => ({ ...prev, [parts[1]]: String(decVal) }));
        return `(integer) ${decVal}`;
      case 'KEYS':
        if (parts.length < 2) return '(error) ERR wrong number of arguments for \'keys\' command';
        const pattern = parts[1];
        const matching = Object.keys(demoDb).filter(k => {
          if (pattern === '*') return true;
          // Simple glob-to-regex
          const regex = new RegExp('^' + pattern.replace(/\*/g, '.*') + '$');
          return regex.test(k);
        });
        if (matching.length === 0) return '(empty array)';
        return matching.map((k, i) => `${i + 1}) "${k}"`).join('\n');
      case 'INFO':
        return `# Server\nredis_version:1.0.0\nprocess_id:${Math.floor(Math.random() * 20000)}\nuptime_in_seconds:3600\nconnected_clients:1\nused_memory:${formatBytes(Object.keys(demoDb).length * 150 + 20480)}\nrole:master\ndemo_mode:active`;
      default:
        return `(error) ERR unknown command '${cmdName?.toLowerCase()}'`;
    }
  };

  // Helper to parse double quotes in commands
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

  // Add key via dashboard form
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

  // Delete key
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

  // Filter keys list in dashboard
  const getFilteredKeys = () => {
    if (isLive) {
      return keysList.filter(k => k.key.toLowerCase().includes(searchQuery.toLowerCase()));
    } else {
      return Object.entries(demoDb)
        .filter(([k]) => k.toLowerCase().includes(searchQuery.toLowerCase()))
        .map(([k, v]) => ({ key: k, type: 'STRING', value: v, ttl: -1 }));
    }
  };

  // Handle key down in terminal input (History scrubbing)
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

  return (
    <div className="app-container">
      {/* NAVBAR */}
      <nav className="navbar">
        <div className="container nav-container">
          <a href="#" className="nav-logo" onClick={() => setView('landing')}>
            <span className="logo-icon">⬡</span>
            <span className="logo-text">Jedis</span>
          </a>
          <div className="nav-links-wrapper">
            <ul className="nav-links">
              <li>
                <a 
                  className={`nav-link ${view === 'landing' ? 'active' : ''}`} 
                  onClick={() => setView('landing')}
                >
                  Home
                </a>
              </li>
              <li>
                <a 
                  className={`nav-link ${view === 'dashboard' ? 'active' : ''}`} 
                  onClick={() => setView('dashboard')}
                >
                  Admin Console
                </a>
              </li>
            </ul>
            <a 
              className="nav-cta" 
              onClick={() => setView(view === 'landing' ? 'dashboard' : 'landing')}
            >
              <span>{view === 'landing' ? 'Open Dashboard' : 'View Docs'}</span>
            </a>
          </div>
        </div>
      </nav>

      {/* ======================================================== */}
      {/* LANDING PAGE VIEW */}
      {/* ======================================================== */}
      {view === 'landing' && (
        <div>
          {/* HERO */}
          <section className="hero-section">
            <div className="hero-glow"></div>
            <div className="container hero-content">
              <div className="hero-badge">
                <span className="badge-dot"></span>
                <span>Pure Java • RESP2 Compatible</span>
              </div>
              <h1 className="hero-title">
                Build Your Own <br />
                <span className="text-gradient">Redis Clone in Java</span>
              </h1>
              <p className="hero-subtitle">
                A high-performance in-memory database built from scratch using the JDK, supporting 
                persistence, NIO event loops, pub/sub channels, off-heap memory, and ACL configurations.
              </p>
              <div className="hero-actions">
                <button className="btn btn-primary" onClick={() => setView('dashboard')}>
                  Open Admin Dashboard
                </button>
                <a href="#quickstart" className="btn btn-ghost">
                  Quickstart Guides
                </a>
              </div>

              {/* Stats */}
              <div className="hero-stats">
                <div className="stat">
                  <span className="stat-number">
                    {isLive ? serverInfo.keys : Object.keys(demoDb).length}
                  </span>
                  <span className="stat-label">Total Keys</span>
                </div>
                <div className="stat-divider"></div>
                <div className="stat">
                  <span className="stat-number">25</span>
                  <span className="stat-label">Redis Commands</span>
                </div>
                <div className="stat-divider"></div>
                <div className="stat">
                  <span className="stat-number">{isLive ? formatBytes(serverInfo.memoryUsedBytes) : '15 KB'}</span>
                  <span className="stat-label">Memory Usage</span>
                </div>
                <div className="stat-divider"></div>
                <div className="stat">
                  <span className="stat-number">{isLive ? serverInfo.connectedClients : 1}</span>
                  <span className="stat-label">Live Clients</span>
                </div>
              </div>
            </div>
          </section>

          {/* FEATURES SECTION */}
          <section className="section-wrapper" id="features">
            <div className="container">
              <div className="section-header">
                <span class="section-tag">Core Capabilities</span>
                <h2 className="section-title">Built From First Principles</h2>
                <p className="section-desc">Hand-written TCP socket layers, protocol parser, and off-heap memory allocators.</p>
              </div>

              <div className="features-grid">
                <div className="feature-card">
                  <div className="feature-icon">🗝️</div>
                  <h3 className="feature-title">Off-Heap Storage</h3>
                  <p className="feature-desc">Utilizes direct buffers to bypass JVM garbage collection, yielding predictable low latency under high load.</p>
                  <span className="feature-tag">Memory</span>
                </div>

                <div className="feature-card">
                  <div className="feature-icon">⚡</div>
                  <h3 className="feature-title">NIO Event Loop</h3>
                  <p className="feature-desc">Non-blocking selector multiplexing allowing a single thread to handle tens of thousands of concurrent TCP sockets.</p>
                  <span className="feature-tag">Networking</span>
                </div>

                <div className="feature-card">
                  <div className="feature-icon">💾</div>
                  <h3 className="feature-title">AOF & RDB Persistence</h3>
                  <p className="feature-desc">Provides durable write-ahead logging (Append Only File) and periodic snapshotting (Redis Database) for zero data loss.</p>
                  <span className="feature-tag">Storage</span>
                </div>

                <div className="feature-card">
                  <div className="feature-icon">📣</div>
                  <h3 className="feature-title">Pub/Sub Broadcasting</h3>
                  <p className="feature-desc">Real-time messaging channels with instant fire-and-forget subscription delivery across connections.</p>
                  <span className="feature-tag">Messaging</span>
                </div>

                <div className="feature-card">
                  <div className="feature-icon">🛡️</div>
                  <h3 className="feature-title">Security & ACL</h3>
                  <p className="feature-desc">Built-in TLS certificate verification, password hashing, and Access Control List command-level restrictions.</p>
                  <span className="feature-tag">Security</span>
                </div>

                <div className="feature-card">
                  <div className="feature-icon">🔗</div>
                  <h3 className="feature-title">Replication & Clustering</h3>
                  <p className="feature-desc">Master-Replica configuration with backlog recovery plus CRC16 slot-based cluster sharding.</p>
                  <span className="feature-tag">High Availability</span>
                </div>
              </div>
            </div>
          </section>

          {/* INTERACTIVE CLI DEMO */}
          <section className="section-wrapper">
            <div className="container">
              <div className="section-header">
                <span class="section-tag">Interactive CLI</span>
                <h2 className="section-title">Test the Command Console</h2>
                <p className="section-desc">
                  {isLive 
                    ? "Interactive terminal connected directly to your local Jedis database."
                    : "Interact with Jedis locally. Click commands on the right to auto-execute in the console."}
                </p>
              </div>

              <div className="terminal-showcase">
                <div className="terminal">
                  <div className="terminal-header">
                    <div className="terminal-dots">
                      <span className="dot dot-red"></span>
                      <span className="dot dot-yellow"></span>
                      <span className="dot dot-green"></span>
                    </div>
                    <span className="terminal-title">jedis-cli -p {isLive ? serverInfo.port : 6380} {isLive ? '[LIVE]' : '[DEMO]'}</span>
                  </div>
                  <div className="terminal-body">
                    {terminalLines.map((line, idx) => (
                      <div 
                        key={idx} 
                        className={`term-line ${
                          line.type === 'input' ? 'term-cmd' : 
                          line.type === 'error' ? 'terminal-error-line' :
                          line.type === 'success' ? 'term-success' : 'term-output'
                        }`}
                        style={{ whiteSpace: 'pre-wrap' }}
                      >
                        {line.text}
                      </div>
                    ))}
                    <div ref={terminalEndRef} />
                  </div>
                  <div className="terminal-input-container">
                    <span className="terminal-prompt">jedis&gt;</span>
                    <input 
                      type="text" 
                      className="terminal-control"
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
                      placeholder="Type PING, HELP, SET, GET..."
                    />
                  </div>
                </div>

                <div className="commands-grid">
                  <div className="cmd-category">
                    <h4>Strings</h4>
                    <div className="cmd-list">
                      <span className="cmd" onClick={() => executeCommand('PING')}>PING</span>
                      <span className="cmd" onClick={() => executeCommand('SET counter 100')}>SET</span>
                      <span className="cmd" onClick={() => executeCommand('GET counter')}>GET</span>
                      <span className="cmd" onClick={() => executeCommand('INCR counter')}>INCR</span>
                    </div>
                  </div>
                  <div className="cmd-category">
                    <h4>Keys</h4>
                    <div className="cmd-list">
                      <span className="cmd" onClick={() => executeCommand('KEYS *')}>KEYS *</span>
                      <span className="cmd" onClick={() => executeCommand('EXISTS counter')}>EXISTS</span>
                      <span className="cmd" onClick={() => executeCommand('DEL counter')}>DEL</span>
                    </div>
                  </div>
                  <div className="cmd-category">
                    <h4>Database Info</h4>
                    <div className="cmd-list">
                      <span className="cmd" onClick={() => executeCommand('INFO')}>INFO</span>
                      <span className="cmd" onClick={() => executeCommand('HELP')}>HELP</span>
                    </div>
                  </div>
                  <div className="cmd-category">
                    <h4>Config Status</h4>
                    <div className="cmd-list">
                      <span className="cmd" style={{ background: isLive ? 'rgba(46, 204, 113, 0.2)' : 'rgba(231, 76, 60, 0.2)', color: isLive ? '#2ecc71' : '#e74c3c', cursor: 'default' }}>
                        {isLive ? 'Online Backend' : 'Offline Sandbox'}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </section>

          {/* QUICKSTART / CTA */}
          <section className="section-wrapper" id="quickstart">
            <div className="container">
              <div className="cta-card">
                <div className="cta-glow"></div>
                <h2>Get Started in 30 Seconds</h2>
                <p>Run Jedis with Maven and connect with redis-cli.</p>
                <div className="cta-terminal">
                  <div className="terminal-header">
                    <div className="terminal-dots">
                      <span className="dot dot-red"></span>
                      <span className="dot dot-yellow"></span>
                      <span className="dot dot-green"></span>
                    </div>
                    <span className="terminal-title">Quickstart Console</span>
                  </div>
                  <div className="terminal-body cta-terminal-body">
                    <div className="term-line"><span className="term-prompt">$</span> git clone https://github.com/DipanshuAmbilkar03/Jedis.git</div>
                    <div className="term-line"><span className="term-prompt">$</span> cd mini-redis && mvn clean package</div>
                    <div className="term-line"><span className="term-prompt">$</span> java -jar target/mini-redis-1.0.jar --port 6380</div>
                    <div className="term-line term-output"><span className="term-success">✓</span> Server started. Open http://localhost:8080</div>
                  </div>
                </div>
              </div>
            </div>
          </section>

          {/* FOOTER */}
          <footer className="footer">
            <div className="container">
              <div className="footer-content">
                <div className="footer-brand">
                  <span className="logo-icon">⬡</span>
                  <span className="logo-text">Jedis</span>
                  <p>A production-ready database engine implementation demonstrating deep systems engineering principles.</p>
                </div>
                <div className="footer-links">
                  <div className="footer-col">
                    <h4>Navigation</h4>
                    <a onClick={() => window.scrollTo({top: 0, behavior: 'smooth'})}>Home</a>
                    <a onClick={() => setView('dashboard')}>Dashboard</a>
                  </div>
                  <div className="footer-col">
                    <h4>Specs</h4>
                    <a href="https://redis.io/docs/reference/protocol-spec/" target="_blank" rel="noreferrer">RESP2 Protocol</a>
                    <a href="https://redis.io/commands/" target="_blank" rel="noreferrer">Redis Commands Reference</a>
                  </div>
                </div>
              </div>
              <div className="footer-bottom">
                <p>&copy; 2026 Jedis DB — Designed for Production Deployment</p>
              </div>
            </div>
          </footer>
        </div>
      )}

      {/* ======================================================== */}
      {/* ADMIN DASHBOARD VIEW */}
      {/* ======================================================== */}
      {view === 'dashboard' && (
        <div className="container dashboard-wrapper">
          {/* Dashboard Header */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h2>⚡ Jedis Database Panel</h2>
            <div className={`status-badge ${!isLive ? 'disconnected' : ''}`}>
              <div className="status-dot"></div>
              <span>{isLive ? 'CONNECTED' : 'SANDBOX MODE (OFFLINE)'}</span>
            </div>
          </div>

          {/* Metrics Grid */}
          <div className="metrics-grid">
            <div className="metric-card keys-card">
              <span className="metric-label">Total Keys</span>
              <span className="metric-value">
                {isLive ? serverInfo.keys : Object.keys(demoDb).length}
              </span>
            </div>
            <div className="metric-card">
              <span className="metric-label">Memory Usage</span>
              <span className="metric-value">
                {isLive ? formatBytes(serverInfo.memoryUsedBytes) : '15 KB'}
              </span>
            </div>
            <div className="metric-card clients-card">
              <span className="metric-label">Connected Clients</span>
              <span className="metric-value">
                {isLive ? serverInfo.connectedClients : 1}
              </span>
            </div>
            <div className="metric-card uptime-card">
              <span className="metric-label">Server Uptime</span>
              <span className="metric-value">
                {isLive ? formatUptime(serverInfo.uptimeSeconds) : '48m 20s'}
              </span>
            </div>
          </div>

          {/* Main Console Areas */}
          <div className="terminal-showcase">
            {/* Left: Key Explorer */}
            <div className="section-card">
              <div className="section-header">
                <span className="section-title-dash">🗝️ Key Explorer ({getFilteredKeys().length})</span>
                <button 
                  onClick={() => {
                    checkConnection();
                    fetchKeys();
                  }} 
                  className="btn btn-ghost btn-sm"
                >
                  ↻ Refresh
                </button>
              </div>

              {/* Add Key Form */}
              <form onSubmit={handleAddKey} className="inline-form">
                <div className="input-group">
                  <label>Key</label>
                  <input 
                    type="text" 
                    placeholder="e.g. user:id" 
                    className="form-control" 
                    value={newKey}
                    onChange={(e) => setNewKey(e.target.value)}
                  />
                </div>
                <div className="input-group">
                  <label>Value</label>
                  <input 
                    type="text" 
                    placeholder="e.g. data_payload" 
                    className="form-control" 
                    value={newVal}
                    onChange={(e) => setNewVal(e.target.value)}
                  />
                </div>
                <div className="input-group">
                  <label>TTL (sec)</label>
                  <input 
                    type="number" 
                    placeholder="-1" 
                    className="form-control" 
                    value={newTtl}
                    onChange={(e) => setNewTtl(e.target.value)}
                  />
                </div>
                <button type="submit" className="btn btn-primary">＋ Set Key</button>
              </form>

              {/* Search Bar */}
              <input 
                type="text" 
                placeholder="Search keys..." 
                className="form-control" 
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />

              {/* Table */}
              <div className="table-container">
                <table>
                  <thead>
                    <tr>
                      <th>Key Name</th>
                      <th>Type</th>
                      <th>Value Preview</th>
                      <th>TTL (s)</th>
                      <th style={{ width: '80px' }}>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {getFilteredKeys().length === 0 ? (
                      <tr>
                        <td colSpan="5" style={{ textAlign: 'center', color: 'var(--text-secondary)' }}>
                          No keys match search criteria
                        </td>
                      </tr>
                    ) : (
                      getFilteredKeys().map((k) => (
                        <tr key={k.key}>
                          <td><strong>{k.key}</strong></td>
                          <td><span className={`badge badge-${k.type.toLowerCase()}`}>{k.type}</span></td>
                          <td><code style={{ fontFamily: 'var(--font-mono)', fontSize: '0.85rem' }}>{k.value}</code></td>
                          <td style={{ color: k.ttl > 0 ? 'var(--accent-blue)' : 'var(--text-secondary)' }}>
                            {k.ttl === -1 ? 'persistent' : `${k.ttl}s`}
                          </td>
                          <td>
                            <button 
                              onClick={() => handleDeleteKey(k.key)} 
                              className="btn btn-danger btn-sm"
                            >
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

            {/* Right: CLI Terminal */}
            <div className="section-card terminal-card-dash">
              <div className="section-header" style={{ borderColor: 'rgba(255, 51, 75, 0.2)' }}>
                <span className="section-title-dash" style={{ color: 'var(--accent-red)' }}>⚡ Live Web CLI</span>
                <button 
                  onClick={() => setTerminalLines([{ text: 'Console cleared.', type: 'secondary' }])} 
                  className="btn btn-ghost btn-sm"
                >
                  Clear
                </button>
              </div>

              <div className="terminal-output-dash">
                {terminalLines.map((line, idx) => (
                  <div 
                    key={idx} 
                    className={`terminal-line ${
                      line.type === 'input' ? 'terminal-input-line' : 
                      line.type === 'error' ? 'terminal-error-line' : 
                      line.type === 'secondary' ? 'term-output' : 'terminal-output-line'
                    }`}
                  >
                    {line.text}
                  </div>
                ))}
                <div ref={terminalEndRef} />
              </div>

              <div className="terminal-input-container">
                <span className="terminal-prompt">jedis&gt;</span>
                <input 
                  type="text" 
                  className="terminal-control" 
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
                  placeholder="Execute Redis commands..."
                  autoFocus
                />
              </div>
            </div>
          </div>

          {/* Footer Server Config Details */}
          <div className="dashboard-footer">
            <div className="dashboard-footer-item">
              <span>TCP Port:</span>
              <strong>{isLive ? serverInfo.port : 6380}</strong>
            </div>
            <div className="dashboard-footer-item">
              <span>AOF Engine:</span>
              <strong>{isLive ? (serverInfo.aofEnabled ? 'Active' : 'Disabled') : 'Active'}</strong>
            </div>
            <div className="dashboard-footer-item">
              <span>RDB Engine:</span>
              <strong>{isLive ? (serverInfo.rdbEnabled ? 'Active' : 'Disabled') : 'Active'}</strong>
            </div>
            <div className="dashboard-footer-item">
              <span>Eviction:</span>
              <strong>{isLive ? serverInfo.evictionPolicy : 'noeviction'}</strong>
            </div>
            <div className="dashboard-footer-item">
              <span>Max Memory:</span>
              <strong>{isLive && serverInfo.memoryMaxBytes > 0 ? formatBytes(serverInfo.memoryMaxBytes) : 'unlimited'}</strong>
            </div>
            <div className="dashboard-footer-item">
              <span>Replica Mode:</span>
              <strong>{isLive ? (serverInfo.replicaOf ? `Replica of ${serverInfo.replicaOf}` : 'Master') : 'Master'}</strong>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
