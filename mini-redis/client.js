const net = require('net');
const readline = require('readline');

// Connect to the Mini Redis TCP port
const client = net.createConnection({ port: 6380 }, () => {
    console.log('\n🔴 Connected to Mini Redis Client (Port 6380)');
    console.log('Type your commands (e.g., PING, SET key value, GET key). Type exit to quit.\n');
    rl.prompt();
});

client.on('data', (data) => {
    // Show raw RESP protocol output so you can see exactly what the server sends!
    console.log(data.toString().replace(/\r\n/g, '\\r\\n\n').trim());
    rl.prompt();
});

client.on('end', () => {
    console.log('\n🔴 Disconnected from Mini Redis');
    process.exit();
});

client.on('error', (err) => {
    console.error('\n❌ Connection error: ' + err.message);
    console.log('Make sure your Java server is running on port 6380 first.');
    process.exit(1);
});

const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    prompt: 'mini-redis> '
});

rl.on('line', (line) => {
    const trimmed = line.trim();
    if (trimmed.toLowerCase() === 'exit' || trimmed.toLowerCase() === 'quit') {
        client.end();
        return;
    }
    if (trimmed.length > 0) {
        client.write(trimmed + '\r\n');
    } else {
        rl.prompt();
    }
});
