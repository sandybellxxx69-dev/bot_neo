const { default: makeWASocket, useMultiFileAuthState, Browsers, DisconnectReason } = require('@whiskeysockets/baileys');
const pino = require('pino');
const readline = require('readline');
const fs = require('fs');

async function startBot() {
    console.log("Iniciando bot...");
    const authDir = `${process.cwd()}/auth_info_baileys`;
    if (!fs.existsSync(authDir)) {
        fs.mkdirSync(authDir, { recursive: true });
    }
    const { state, saveCreds } = await useMultiFileAuthState(authDir);

    const sock = makeWASocket({
        auth: state,
        printQRInTerminal: false,
        logger: pino({ level: "silent" }),
        browser: Browsers.macOS('Desktop'),
        syncFullHistory: false
    });

    sock.ev.on('creds.update', saveCreds);

    sock.ev.on('connection.update', (update) => {
        const { connection, lastDisconnect } = update;
        if (connection === 'close') {
            const shouldReconnect = lastDisconnect?.error?.output?.statusCode !== DisconnectReason.loggedOut;
            console.log('connection closed due to ', lastDisconnect?.error, ', reconnecting ', shouldReconnect);
            
            if (!shouldReconnect) {
                console.log("WA_STATUS:REQUIERE_CODIGO");
            } else {
                console.log("WA_STATUS:DESCONECTADO");
                startBot();
            }
        } else if (connection === 'open') {
            console.log("WA_STATUS:CONECTADO");
        }
    });

    if (!sock.authState.creds.registered) {
        console.log("WA_STATUS:REQUIERE_CODIGO");
        
        const rl = readline.createInterface({
            input: process.stdin,
            output: process.stdout,
            terminal: false
        });

        rl.on('line', async (line) => {
            if (line.startsWith('PAIRING_NUMBER:')) {
                const phoneNumber = line.split(':')[1].trim();
                console.log(`Solicitando código para: ${phoneNumber}`);
                try {
                    setTimeout(async () => {
                        const code = await sock.requestPairingCode(phoneNumber);
                        console.log(`PAIRING_CODE:${code}`);
                    }, 2000); // small delay
                } catch (err) {
                    console.error("Error al solicitar código:", err);
                }
            }
        });
    } else {
        console.log("Sesión existente. Esperando conexión...");
    }
}

startBot();
