// Browser stand-in for the Android plugin (npm run dev). It holds no sample data and downloads nothing:
// links you add just sit in the list as "queued". Real downloading only happens on Android.
export class DownlyWeb {
  items = []
  settings = { notifications: true, folder: '', cookies: false, sort: true }

  async list() {
    return { items: JSON.parse(JSON.stringify(this.items)), engineError: '', videoEngine: 'ready', dest: 'Downloads/Downly', ...this.settings }
  }
  async probe({ uri }) {
    const host = (() => { try { return new URL(uri).hostname } catch { return '' } })()
    if (uri.startsWith('magnet:')) return { kind: 'magnet', category: 'Torrents' }
    if (/(^|\.)(instagram|tiktok|youtube|youtu|facebook|vimeo|reddit|twitch|dailymotion)\.(com|be|tv)$/.test(host)) return { kind: 'video', category: 'Video' }
    const name = decodeURIComponent(uri.split('?')[0].split('/').pop() || '')
    const ext = (name.split('.').pop() || '').toLowerCase()
    return /^[a-z0-9]{2,5}$/.test(ext) && name.includes('.') ? { kind: 'file', name, size: -1, category: 'Other' } : { kind: 'page', name, size: -1, category: 'Other' }
  }
  async setSort({ enabled }) { this.settings.sort = enabled }
  async ensureFolders() {}
  async setWindowColor() {}
  async getLaunch() { return { uri: '' } }
  async add({ uri, video = false }) {
    const host = (() => { try { return new URL(uri).hostname } catch { return uri } })()
    this.items.push({
      id: String(Math.random()).slice(2, 12), gid: '', name: video ? `Video from ${host}` : uri.split('/').pop() || uri,
      type: uri.startsWith('magnet:') ? 'magnet' : video ? 'video' : 'http', uri, folder: '', want: 'run', state: 'queued',
      total: 0, done: 0, speed: 0, eta: -1, connections: 0, error: '', openUri: '', created: Date.now()
    })
  }
  async pause({ id }) { this.state(id, 'paused') }
  async resume({ id }) { this.state(id, 'queued') }
  async remove({ id }) { this.items = this.items.filter(i => i.id !== id) }
  async clearHistory() { this.items = this.items.filter(i => i.state !== 'completed' && i.state !== 'failed') }
  async setNotifications({ enabled }) { this.settings.notifications = enabled }
  async pickFolder() { this.settings.folder = 'primary:Download' }
  async clearFolder() { this.settings.folder = '' }
  async pickCookies() { this.settings.cookies = true }
  async clearCookies() { this.settings.cookies = false }
  async pickTorrent() {}
  async addTorrentUri() {}
  async retryEngine() {}
  async open() {}
  async ensureNotifications() {}
  state(id, s) { const it = this.items.find(i => i.id === id); if (it) it.state = s }
}
