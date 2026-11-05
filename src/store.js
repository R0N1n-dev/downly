import { ref, shallowRef, computed } from 'vue'
import { registerPlugin, SystemBars, SystemBarsStyle } from '@capacitor/core'
import { Haptics, ImpactStyle } from '@capacitor/haptics'
import { Share } from '@capacitor/share'
import { THEMES, themeById, tokens, cssVars, HUE } from './theme.js'

export { THEMES, HUE }

export const Downly = registerPlugin('Downly', {
  web: () => import('./lib/web.js').then(m => new m.DownlyWeb())
})

// ---------------------------------------------------------------- state

export const items = shallowRef([]) // replaced wholesale each poll: no deep reactivity cost
let lastSig = ''
export const engineError = ref('')
export const notifications = ref(true)
export const folderRaw = ref('')
export const cookies = ref(false)
export const videoEngine = ref('idle') // idle | preparing | ready | error
export const sortOn = ref(true)         // sort finished files into category folders
export const destBase = ref('Downloads/Downly')
export const prefill = ref('') // link shared from another app, shown in the Add sheet
export const toast = ref('')
export const ui = ref({ sheet: '', detailId: '' }) // sheet: '' | 'add' | 'details'

// navigation: shared by the bottom tabs, the sidebar and the list screens
export const view = ref('downloads')   // downloads | history | settings
export const statusFilter = ref('all') // all | downloading | queued | paused | completed | failed
export const typeFilter = ref('')      // '' or a key of TYPES
export const drawer = ref(false)
export const TYPES = [
  ['video', 'Videos'], ['torrent', 'Torrents and magnets'], ['archive', 'Archives'], ['disk', 'Disk images and apps'],
  ['doc', 'Documents'], ['audio', 'Audio'], ['image', 'Images'], ['other', 'Other']
]
export const go = (v, status) => { view.value = v; if (status) statusFilter.value = status }

export const isFinal = it => it.state === 'completed' || it.state === 'failed'
export const active = computed(() => items.value.filter(i => !isFinal(i)))
export const finished = computed(() => items.value.filter(isFinal))
export const totalSpeed = computed(() =>
  items.value.reduce((sum, i) => sum + (i.state === 'downloading' ? i.speed : 0), 0))

export const statusCounts = computed(() => {
  const c = { all: active.value.length, downloading: 0, queued: 0, paused: 0, completed: 0, failed: 0 }
  for (const it of items.value) c[it.state]++
  return c
})
export const typeCounts = computed(() => {
  const c = {}
  for (const it of items.value) { const k = category(it); c[k] = (c[k] || 0) + 1 }
  return c
})
export const matchesType = it => !typeFilter.value || category(it) === typeFilter.value

export const folderLabel = raw => {
  if (!raw) return 'Downloads'
  const clean = raw.replace(/^primary:/, 'Internal storage/').replace(/:/, '/')
  return clean
}

let toastTimer
export function say(message) {
  toast.value = message
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (toast.value = ''), 4500)
}

// ---------------------------------------------------------------- native calls

export async function refresh() {
  try {
    const r = await Downly.list()
    const sorted = r.items.sort((a, b) => b.created - a.created) // created order only: rows never hop around as states change
    const sig = JSON.stringify(sorted)
    if (sig !== lastSig) { lastSig = sig; items.value = sorted }
    videoEngine.value = r.videoEngine
    sortOn.value = r.sort
    destBase.value = r.dest
    engineError.value = r.engineError
    notifications.value = r.notifications
    folderRaw.value = r.folder
    cookies.value = r.cookies
  } catch (e) {
    say(e.message || String(e))
  }
}

// Run a native call, surface its error, refresh the list afterwards.
export async function run(fn) {
  try {
    await fn()
  } catch (e) {
    say(e.message || String(e))
  }
  refresh()
}

export const buzz = () => Haptics.impact({ style: ImpactStyle.Light }).catch(() => {})

// Pages on these sites are not files: yt-dlp has to extract the video from them.
const VIDEO_HOSTS = /(^|\.)(instagram\.com|tiktok\.com|youtube\.com|youtu\.be|facebook\.com|fb\.watch|twitter\.com|x\.com|vimeo\.com|reddit\.com|twitch\.tv|dailymotion\.com|pinterest\.com|pin\.it|snapchat\.com|bilibili\.com)$/i
export function isVideoLink(uri) {
  try { return VIDEO_HOSTS.test(new URL(uri.trim()).hostname) } catch { return false }
}

// quality: best | 720 | audio (video only). video: force the video engine for sites not in the list.
// mode: video | audio. quality: best | 1080 | 720 | 480. audioFormat: mp3 | m4a | opus | flac | wav. bitrate: 128K | 192K | 320K
export const addLink = (uri, { video = isVideoLink(uri), mode = 'video', quality = 'best', audioFormat = 'mp3', bitrate = '192K' } = {}) =>
  run(async () => {
    await Downly.ensureNotifications()
    await Downly.add({ uri: uri.trim(), video, mode, quality, audioFormat, bitrate })
    buzz()
  })

// what is this link? { kind: video | file | page | torrent | magnet | unknown, name, size, category }
export const probe = uri => Downly.probe({ uri: uri.trim() }).catch(() => ({ kind: 'unknown' }))
export const setSort = enabled => run(() => Downly.setSort({ enabled }))
export const ensureFolders = () => run(() => Downly.ensureFolders())

// folder shown for a category, e.g. "Downloads/Downly/Video"
export const destFor = category => (sortOn.value && category ? `${destBase.value}/${category}` : destBase.value)

export const addTorrentFile = () =>
  run(async () => {
    await Downly.ensureNotifications()
    await Downly.pickTorrent()
  })

export const pause = it => { buzz(); return run(() => Downly.pause({ id: it.id })) }
export const resume = it => { buzz(); return run(() => Downly.resume({ id: it.id })) }
export const remove = it => run(() => Downly.remove({ id: it.id }))
export const openFile = it => run(() => Downly.open({ id: it.id }))
export const clearHistory = () => run(() => Downly.clearHistory())
export const retryEngine = () => run(() => Downly.retryEngine())
export const pickFolder = () => run(() => Downly.pickFolder())
export const importCookies = () => run(() => Downly.pickCookies())
export const clearCookies = () => run(() => Downly.clearCookies())
export const clearFolder = () => run(() => Downly.clearFolder())
export const setNotifications = enabled => run(() => Downly.setNotifications({ enabled }))
export const share = it => Share.share({ title: it.name, text: it.uri || it.name }).catch(() => {})

async function checkLaunch() {
  const { uri } = await Downly.getLaunch().catch(() => ({ uri: '' }))
  if (!uri) return
  if (uri.startsWith('magnet:')) addLink(uri)
  else if (/^https?:/i.test(uri)) { // a shared page link: let the user pick quality first
    prefill.value = uri
    ui.value = { sheet: 'add', detailId: '' }
  } else run(async () => { await Downly.ensureNotifications(); await Downly.addTorrentUri({ uri }) })
}

let timer
export function start() {
  refresh()
  checkLaunch()
  timer = setInterval(() => { if (!document.hidden) refresh() }, 1000)
  document.addEventListener('visibilitychange', () => { if (!document.hidden) { refresh(); checkLaunch() } })
}
export const stop = () => clearInterval(timer)

// ---------------------------------------------------------------- theme

export const theme = ref(localStorage.getItem('downly-theme') || 'system')     // appearance: system | light | dark
export const palette = ref(localStorage.getItem('downly-palette') || 'sorbet')  // colour theme id, see theme.js

export function applyTheme() {
  const mode = theme.value === 'system' ? (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light') : theme.value
  const root = document.documentElement
  for (const [name, value] of Object.entries(cssVars(tokens(themeById(palette.value), mode)))) {
    if (value === undefined) root.style.removeProperty(name)
    else root.style.setProperty(name, value)
  }
  root.style.colorScheme = mode
  Downly.setWindowColor({ color: tokens(themeById(palette.value), mode).bg }).catch(() => {})
  SystemBars.setStyle({ style: mode === 'dark' ? SystemBarsStyle.Dark : SystemBarsStyle.Light }).catch(() => {})
}

export function setTheme(value) {
  theme.value = value
  localStorage.setItem('downly-theme', value)
  applyTheme()
}

export function setPalette(id) {
  palette.value = id
  localStorage.setItem('downly-palette', id)
  applyTheme()
}

// follow the phone's light/dark switch while appearance is "system"
matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => { if (theme.value === 'system') applyTheme() })

// ---------------------------------------------------------------- fonts

const STACK = ', system-ui, -apple-system, sans-serif'
export const FONTS = [
  { id: 'sorbet', name: 'Sorbet', note: 'Bricolage Grotesque and DM Sans', display: "'Bricolage Grotesque Variable'", ui: "'DM Sans Variable'" },
  { id: 'jakarta', name: 'Plus Jakarta Sans', note: 'Friendly and professional', display: "'Plus Jakarta Sans Variable'", ui: "'Plus Jakarta Sans Variable'", load: () => import('@fontsource-variable/plus-jakarta-sans') },
  { id: 'satoshi', name: 'Satoshi', note: 'Premium and sharp', display: "'Satoshi'", ui: "'Satoshi'", file: 'Satoshi-Variable.woff2' },
  { id: 'outfit', name: 'Outfit', note: 'Warm and welcoming', display: "'Outfit Variable'", ui: "'Outfit Variable'", load: () => import('@fontsource-variable/outfit') },
  { id: 'geist', name: 'Geist Sans', note: 'Precise and minimal', display: "'Geist Variable'", ui: "'Geist Variable'", load: () => import('@fontsource-variable/geist') },
  { id: 'cabinet', name: 'Cabinet Grotesk', note: 'Bold headings, Jakarta body', display: "'Cabinet Grotesk'", ui: "'Plus Jakarta Sans Variable'", file: 'CabinetGrotesk-Variable.woff2', load: () => import('@fontsource-variable/plus-jakarta-sans') }
]
export const font = ref(localStorage.getItem('downly-font') || 'sorbet')
export const fontFiles = ref({}) // Satoshi and Cabinet Grotesk are loaded from public/fonts when you add them

export async function applyFont() {
  const f = FONTS.find(x => x.id === font.value) || FONTS[0]
  try { await f.load?.() } catch { /* keep the previous font */ }
  const root = document.documentElement
  root.style.setProperty('--display', f.display + STACK)
  root.style.setProperty('--ui', f.ui + STACK)
}

export function setFont(id) {
  font.value = id
  localStorage.setItem('downly-font', id)
  return applyFont()
}

export async function checkFontFiles() {
  const found = {}
  for (const f of FONTS.filter(x => x.file)) {
    found[f.id] = await fetch('/fonts/' + f.file, { method: 'HEAD' }).then(r => r.ok && !(r.headers.get('content-type') || '').includes('html')).catch(() => false)
  }
  fontFiles.value = found
}

// ---------------------------------------------------------------- formatting

export function size(bytes) {
  if (!bytes || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let i = 0
  while (bytes >= 1024 && i < units.length - 1) { bytes /= 1024; i++ }
  return (bytes >= 100 || i === 0 ? bytes.toFixed(0) : bytes.toFixed(1)) + ' ' + units[i]
}

// "1.8 of 2.6 GB" (unit shown once when both sides share it)
export function pair(done, total) {
  const [a, ua] = size(done).split(' ')
  const b = size(total)
  return ua === b.split(' ')[1] ? `${a} of ${b}` : `${size(done)} of ${b}`
}

export const rate = bytesPerSecond => size(bytesPerSecond) + '/s'

export function duration(s) {
  if (s == null || s < 0) return ''
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60)
  return h ? `${h}h ${m}m` : m ? `${m}m ${s % 60}s` : `${s}s`
}

export const percent = it =>
  it.state === 'completed' ? 100 : it.total > 0 ? Math.min(100, Math.floor((it.done / it.total) * 100)) : 0

export function clock(ms) {
  return new Date(ms).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
}

export function fullDate(ms) {
  return new Date(ms).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })
}

export function dayGroup(ms) {
  const d = new Date(ms), t = new Date()
  const same = (a, b) => a.toDateString() === b.toDateString()
  if (same(d, t)) return 'Today'
  t.setDate(t.getDate() - 1)
  return same(d, t) ? 'Yesterday' : 'Earlier'
}

// File category drives the icon and its tint.
const EXT = {
  archive: ['zip', 'rar', '7z', 'tar', 'gz', 'xz', 'bz2', 'tgz'],
  video: ['mkv', 'mp4', 'avi', 'mov', 'webm', 'm4v'],
  audio: ['mp3', 'flac', 'wav', 'm4a', 'ogg', 'aac', 'opus'],
  image: ['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg', 'heic'],
  doc: ['pdf', 'doc', 'docx', 'txt', 'epub', 'xls', 'xlsx', 'ppt', 'pptx', 'csv'],
  disk: ['iso', 'img', 'dmg', 'apk', 'exe', 'msi', 'deb', 'rpm']
}

export function category(it) {
  const ext = (it.name.split('.').pop() || '').toLowerCase()
  for (const [cat, list] of Object.entries(EXT)) if (list.includes(ext)) return cat
  if (it.type === 'video') return 'video'
  return it.type === 'http' ? 'other' : 'torrent'
}
