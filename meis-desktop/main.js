const { app, BrowserWindow } = require('electron');
const path = require('path');

const isDev = process.argv.includes('--dev');
const webUrl = isDev ? 'http://localhost:5173' : `file://${path.join(__dirname, '../meis-web/dist/index.html')}`;

function createWindow() {
  const win = new BrowserWindow({ width: 1280, height: 800, webPreferences: { nodeIntegration: false } });
  win.loadURL(webUrl);
}

app.whenReady().then(createWindow);
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
