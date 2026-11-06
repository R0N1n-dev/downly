// Photo extraction for social posts (Instagram carousels, Facebook/Pinterest/X posts, ...).
//
// The page HTML is fetched on the native side (Downly.page) because a Capacitor WebView cannot read a
// cross-origin page. Everything below is plain string work, so a selector change ships with the web bundle
// instead of a new APK, and the rules are covered by `npm run check:photos` without a phone.
//
// Order of sources, strongest first:
//   1. <script type="application/ld+json"> - image[].url and video[].contentUrl (a WebPage block only
//      carries the site logo, so it is skipped);
//   2. Instagram's own carousel JSON (edge_sidecar_to_children);
//   3. og:image / og:video (the same media is published in the page head of public posts);
//   4. Instagram's single display_url.

// Sites where a link points at a post that carries media. og:image on an ordinary web page is a logo,
// not a download, so the picker only reads these hosts.
const PHOTO_HOSTS = /(^|\.)(instagram\.com|facebook\.com|fb\.watch|pinterest\.com|pin\.it|twitter\.com|x\.com|reddit\.com|tumblr\.com)$/i

const IMAGE_EXT = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'heic', 'heif', 'avif']
const VIDEO_EXT = ['mp4', 'mov', 'm4v', 'webm']

// Instagram/Facebook list the same picture at several sizes (path /s150x150/, /s1080x1080/, ...
// or a ?stp=dst-jpg_s640x640 hint). Pinterest uses /736x/ and /originals/.
const SIZE_IN_URL = /[/_](?:s|p|e)?(\d+)x(\d+)(?=[/_.?&])/
const ORIGINAL_DIR = /\/(?:originals?|orig)\//i

export function isPhotoPage(uri) {
  try { return PHOTO_HOSTS.test(new URL(String(uri).trim()).hostname) } catch { return false }
}

function sizeScore(url) {
  if (ORIGINAL_DIR.test(url)) return Number.MAX_SAFE_INTEGER
  const m = SIZE_IN_URL.exec(url)
  return m ? Number(m[1]) * Number(m[2]) : 0
}

// The same media at two sizes shares its host and file name, whatever folder the size lives in.
function mediaKey(url) {
  try {
    const u = new URL(url)
    return u.host + '/' + (u.pathname.split('/').pop() || '')
  } catch { return url }
}


// Page-supplied strings arrive HTML-escaped (&amp;) or JSON-escaped (\/, \u0026). Undo both.
function clean(raw, base) {
  if (raw == null) return ''
  let s = String(raw).trim()
  if (!s) return ''
  s = s.replace(/\\u([0-9a-f]{4})/gi, (_, h) => String.fromCharCode(parseInt(h, 16)))
  s = s.replace(/\\(["'\\/])/g, '$1')
  s = s.replace(/&quot;/gi, '"').replace(/&#0*39;|&apos;/gi, "'")
    .replace(/&lt;/gi, '<').replace(/&gt;/gi, '>').replace(/&amp;/gi, '&')
  if (s.startsWith('//')) s = 'https:' + s
  try { s = new URL(s, base || undefined).href } catch { return '' }
  return /^https?:/i.test(s) ? s : ''
}

function extOf(url, kind) {
  let path = ''
  try { path = new URL(url).pathname } catch { path = String(url).split(/[?#]/)[0] }
  const m = /\.([a-z0-9]{2,5})$/i.exec(path)
  const ext = m ? m[1].toLowerCase() : ''
  if (kind === 'video') return VIDEO_EXT.includes(ext) ? ext : 'mp4'
  return IMAGE_EXT.includes(ext) ? (ext === 'jpeg' ? 'jpg' : ext) : 'jpg'
}

// Collects media in page order, one row per picture/clip. A later, larger copy of the same media
// replaces the earlier one in place, so a carousel stays image0, image1, video2...
function collector(base) {
  const list = []
  const seen = new Map()
  return {
    list,
    add(raw, kind) {
      const url = clean(raw, base)
      if (!url) return false
      const key = mediaKey(url)
      const at = seen.get(key)
      if (at == null) {
        seen.set(key, list.length)
        list.push({ url, kind, ext: extOf(url, kind) })
      } else if (sizeScore(url) > sizeScore(list[at].url)) {
        list[at] = { url, kind, ext: extOf(url, kind) }
      }
      return true
    }
  }
}

const asArray = value => (value == null ? [] : Array.isArray(value) ? value : [value])

const PAGE_TYPES = new Set(['webpage', 'website', 'collectionpage', 'profilepage', 'aboutpage', 'contactpage', 'faqpage'])
const MEDIA_TYPES = new Set([
  'socialmediaposting', 'imageobject', 'videoobject', 'photograph', 'article', 'newsarticle',
  'blogposting', 'product', 'itempage', 'recipe', 'creativework'
])

function jsonLdBlocks(html) {
  const out = []
  const re = /<script\b[^>]*type\s*=\s*["']?application\/ld\+json["']?[^>]*>([\s\S]*?)<\/script>/gi
  let m
  while ((m = re.exec(html))) out.push(m[1])
  return out
}

function urlOf(node) {
  if (typeof node === 'string') return node
  if (!node || typeof node !== 'object') return ''
  return node.contentUrl || node.url || node['@id'] || ''
}

// Walk a JSON-LD tree. Returns how many media rows it contributed, so the caller can tell an empty
// (or WebPage-only) block from a useful one and fall through to the next source.
function fromJsonLd(text, add) {
  let data
  try { data = JSON.parse(text) } catch { return 0 }
  let found = 0
  const visited = new Set()

  const visit = node => {
    for (const value of asArray(node)) {
      if (!value || typeof value !== 'object' || visited.has(value)) continue
      visited.add(value)
      const types = asArray(value['@type']).map(t => String(t).toLowerCase())
      if (types.some(t => MEDIA_TYPES.has(t))) {
        for (const img of asArray(value.image)) if (add(urlOf(img), 'image')) found++
        for (const vid of asArray(value.video)) if (add(urlOf(vid), 'video')) found++
        if (types.includes('imageobject') && add(urlOf(value), 'image')) found++
        if (types.includes('videoobject') && add(urlOf(value), 'video')) found++
      }
      // A WebPage's image is the site's own preview, not the post media: do not descend into it.
      if (!types.some(t => PAGE_TYPES.has(t))) {
        for (const key of ['@graph', 'mainEntity', 'mainEntityOfPage', 'itemListElement', 'hasPart', 'item']) {
          if (value[key]) visit(value[key])
        }
      }
    }
  }

  visit(data)
  return found
}

// The JSON object that starts at/after `at`, brace-balanced and string-aware.
function objectAt(html, at) {
  const open = html.indexOf('{', at)
  if (open < 0) return ''
  let depth = 0, quote = false, escaped = false
  for (let i = open; i < html.length; i++) {
    const ch = html[i]
    if (quote) {
      if (escaped) escaped = false
      else if (ch === '\\') escaped = true
      else if (ch === '"') quote = false
    } else if (ch === '"') quote = true
    else if (ch === '{') depth++
    else if (ch === '}' && --depth === 0) return html.slice(open, i + 1)
  }
  return html.slice(open, Math.min(html.length, open + 400000))
}

function fromInstagramCarousel(html, add) {
  const at = html.indexOf('"edge_sidecar_to_children"')
  if (at < 0) return 0
  const block = objectAt(html, at)
  let found = 0
  const nodes = /"node"\s*:/g
  let m
  while ((m = nodes.exec(block))) {
    const node = objectAt(block, nodes.lastIndex)
    if (!node) continue
    const video = /"video_url"\s*:\s*"((?:[^"\\]|\\.)*)"/.exec(node)
    if (video) { // a clip: its display_url is only the poster frame
      if (add(video[1], 'video')) found++
      continue
    }
    // a still: display_url is 640px, display_resources carries the 1080px copy (largest wins)
    for (const src of node.matchAll(/"(?:display_url|src)"\s*:\s*"((?:[^"\\]|\\.)*)"/g)) {
      if (add(src[1], 'image')) found++
    }
  }
  return found
}


function metaTags(html) {
  const out = []
  const re = /<meta\b[^>]*>/gi
  let tag
  while ((tag = re.exec(html))) {
    const attrs = {}
    const ar = /([a-zA-Z:-]+)\s*=\s*("([^"]*)"|'([^']*)'|([^\s"'>]+))/g
    let a
    while ((a = ar.exec(tag[0]))) attrs[a[1].toLowerCase()] = a[3] ?? a[4] ?? a[5] ?? ''
    const name = (attrs.property || attrs.name || attrs.itemprop || '').toLowerCase()
    if (name && attrs.content) out.push([name, attrs.content])
  }
  return out
}

const IMAGE_META = new Set(['og:image', 'og:image:url', 'og:image:secure_url', 'twitter:image', 'twitter:image:src'])
const VIDEO_META = new Set(['og:video', 'og:video:url', 'og:video:secure_url', 'twitter:player:stream'])

function fromMeta(html, add) {
  let found = 0
  for (const [name, content] of metaTags(html)) {
    if (IMAGE_META.has(name) && add(content, 'image')) found++
    else if (VIDEO_META.has(name) && add(content, 'video')) found++
  }
  return found
}

function fromInstagramSingle(html, add) {
  const m = /"display_url"\s*:\s*"((?:[^"\\]|\\.)*)"/.exec(html)
  return m && add(m[1], 'image') ? 1 : 0
}

// html: the page source. pageUrl: its final URL (after redirects), used to resolve relative links.
// -> [{ url, kind: 'image' | 'video', ext }] in page order.
export function photosFromHtml(html, pageUrl) {
  const text = String(html || '')
  const base = clean(pageUrl, pageUrl)
  const { list, add } = collector(base)

  for (const block of jsonLdBlocks(text)) if (fromJsonLd(block, add) > 0) break
  if (!list.length) fromInstagramCarousel(text, add)
  if (!list.length) fromMeta(text, add)
  if (!list.length) fromInstagramSingle(text, add)
  return list
}

// Short, safe file stem from the post URL: instagram.com/p/CxYz.../ -> "CxYz..."
export function postStem(pageUrl) {
  try {
    const u = new URL(pageUrl)
    const m = /\/(?:p|reel|reels|tv|status|video|watch)\/([A-Za-z0-9_-]{3,})/.exec(u.pathname)
    const seg = m ? m[1] : (u.pathname.split('/').filter(Boolean).pop() || '')
    const stem = decodeURIComponent(seg).replace(/[^\w-]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 24)
    return stem || 'photo'
  } catch { return 'photo' }
}

// Stable, readable file name: CxYz-1.jpg, CxYz-2.jpg, CxYz-3.mp4...
export function mediaName(media, index, pageUrl) {
  const ext = media.ext || (media.kind === 'video' ? 'mp4' : 'jpg')
  return `${postStem(pageUrl)}-${index + 1}.${ext}`
}
