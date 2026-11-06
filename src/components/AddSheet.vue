<script setup>
import { ref, computed, watch, onUnmounted } from 'vue'
import { ClipboardPaste, FileDown, Folder, Film, Magnet, File, Globe, Check } from 'lucide-vue-next'
import Sheet from './Sheet.vue'
import {
  addLink, addPhotos, findPhotos, canHavePhotos, addTorrentFile, isVideoLink, probe, destFor, videoEngine, prefill, say
} from '../store.js'

// One smart sheet: it looks at the link and shows only what matters (file, magnet, or the video options).
// Public social posts also offer their photos: the page is read natively, parsed in src/lib/photos.js,
// and each picked picture becomes an ordinary http download.
const emit = defineEmits(['close'])
const sheet = ref(null)
const link = ref(prefill.value)
prefill.value = ''

const forceVideo = ref(false)
const mode = ref('video') // photos | video | audio
const quality = ref('best')
const audioFormat = ref('mp3')
const bitrate = ref('192K')
const info = ref(null) // answer from the native probe
const media = ref([])  // photos/videos found in a post, in page order
const picked = ref([]) // one flag per media row
const findingPhotos = ref(false)


const QUALITIES = [['best', 'Best'], ['1080', '1080p'], ['720', '720p'], ['480', '480p']]
const FORMATS = [['mp3', 'MP3'], ['m4a', 'M4A'], ['opus', 'Opus'], ['flac', 'FLAC'], ['wav', 'WAV']]
const BITRATES = [['128K', '128 kbps'], ['192K', '192 kbps'], ['320K', '320 kbps']]

const url = computed(() => link.value.trim())
const valid = computed(() => /^(https?|ftp):\/\/\S+$|^magnet:\?\S+$/i.test(url.value))
const isMagnet = computed(() => /^magnet:/i.test(url.value))
const known = computed(() => valid.value && isVideoLink(url.value))
const kind = computed(() => (!valid.value ? 'none' : isMagnet.value ? 'magnet' : known.value ? 'video' : info.value?.kind || 'unknown'))
const video = computed(() => kind.value === 'video' || (valid.value && !isMagnet.value && forceVideo.value))
const photoMode = computed(() => media.value.length > 0 && mode.value === 'photos')
const lossy = computed(() => !['flac', 'wav'].includes(audioFormat.value))
const selectedCount = computed(() => picked.value.filter(Boolean).length)
const allSelected = computed(() => media.value.length > 0 && selectedCount.value === media.value.length)

const category = computed(() => {
  if (photoMode.value) return 'Images'
  if (video.value) return mode.value === 'audio' ? 'Music' : 'Video'
  if (kind.value === 'magnet' || kind.value === 'torrent') return 'Torrents'
  return info.value?.category || 'Other'
})
const dest = computed(() => destFor(category.value))

const ICON = { video: Film, magnet: Magnet, torrent: Magnet, file: File, page: Globe, unknown: File }

function togglePick(i) { picked.value = picked.value.map((on, n) => (n === i ? !on : on)) }
function toggleAll() { picked.value = media.value.map(() => !allSelected.value) }

const title = computed(() => {
  if (photoMode.value) {
    const n = media.value.length
    return `${n} ${n === 1 ? 'photo' : 'photos'} in this post`
  }
  return ({
    none: 'Files, magnets and video pages all work',
    magnet: 'Magnet link',
    torrent: 'Torrent file',
    video: findingPhotos.value ? 'Looking for photos' : forceVideo.value && !known.value ? 'Using the video engine' : 'Video link detected',
    file: info.value?.name || 'File',
    page: 'This looks like a web page',
    unknown: 'Checking the link'
  }[kind.value])
})
const sub = computed(() => {
  if (photoMode.value) return 'Tap one to leave it out. They land in Photos and your gallery.'
  if (kind.value === 'file' && info.value?.size > 0) return size(info.value.size)
  if (kind.value === 'page') return 'Not a file. If it holds a video, switch the video engine on below.'
  return ''
})

const size = b => {
  const u = ['B', 'KB', 'MB', 'GB']; let i = 0
  while (b >= 1024 && i < 3) { b /= 1024; i++ }
  return (b >= 100 || !i ? b.toFixed(0) : b.toFixed(1)) + ' ' + u[i]
}

// ask the native side what the link is, once typing pauses; social posts also get their photos read
let timer, photoTimer, seq = 0
watch(url, v => {
  info.value = null
  forceVideo.value = false
  media.value = []
  picked.value = []
  findingPhotos.value = false
  mode.value = 'video'
  clearTimeout(timer)
  clearTimeout(photoTimer)
  const mine = ++seq
  if (!valid.value || isMagnet.value) return
  if (canHavePhotos(v)) {
    findingPhotos.value = true
    photoTimer = setTimeout(async () => {
      const found = await findPhotos(v)
      if (mine !== seq) return
      findingPhotos.value = false
      if (!found.length) return
      media.value = found
      picked.value = found.map(() => true)
      mode.value = 'photos'
    }, 500)
  }
  if (isVideoLink(v)) return
  timer = setTimeout(async () => { const r = await probe(v); if (mine === seq) info.value = r }, 450)
}, { immediate: true })
onUnmounted(() => { clearTimeout(timer); clearTimeout(photoTimer) })

async function paste() {
  try { link.value = (await navigator.clipboard.readText()).trim() } catch { say('Long-press the field to paste') }
}

async function submit() {
  if (!valid.value) return
  if (photoMode.value) {
    const chosen = media.value.filter((_, i) => picked.value[i])
    if (!chosen.length) { say('Choose at least one photo'); return }
    await addPhotos(chosen)
  } else {
    await addLink(url.value, { video: video.value, mode: mode.value === 'photos' ? 'video' : mode.value, quality: quality.value, audioFormat: audioFormat.value, bitrate: bitrate.value })
  }
  sheet.value.close()
}


async function torrent() {
  await addTorrentFile()
  sheet.value.close()
}

const action = computed(() => photoMode.value
  ? `Download ${selectedCount.value} ${selectedCount.value === 1 ? 'photo' : 'photos'}`
  : 'Start download')
const blocked = computed(() => !valid.value || (photoMode.value && selectedCount.value === 0))

</script>

<template>
  <Sheet ref="sheet" @close="emit('close')">
    <h2 class="h">Add download</h2>

    <form @submit.prevent="submit">
      <label class="lab" for="link">Link or magnet</label>
      <label class="field">
        <input id="link" v-model="link" type="url" inputmode="url" placeholder="https://… or magnet:?…" autocomplete="off" autofocus />
        <button type="button" class="paste" aria-label="Paste from clipboard" @click="paste"><ClipboardPaste :size="19" /></button>
      </label>

      <div class="card" :class="{ idle: kind === 'none' }" aria-live="polite">
        <span class="kindicon"><component :is="ICON[kind] || File" :size="20" /></span>
        <div class="txt"><b>{{ title }}</b><small v-if="sub">{{ sub }}</small></div>
      </div>

      <label v-if="valid && !isMagnet && !known && kind !== 'file' && kind !== 'torrent' && !media.length" class="force">
        <input type="checkbox" v-model="forceVideo" /> This page holds a video (use yt-dlp)
      </label>

      <p v-if="findingPhotos" class="hint">Reading the post for photos…</p>

      <div v-if="media.length && video" class="seg three" role="radiogroup" aria-label="What to download">
        <button type="button" role="radio" :aria-checked="mode === 'photos'" :class="{ on: mode === 'photos' }" @click="mode = 'photos'">Photos <small class="n num">{{ media.length }}</small></button>
        <button type="button" role="radio" :aria-checked="mode === 'video'" :class="{ on: mode === 'video' }" @click="mode = 'video'">Video</button>
        <button type="button" role="radio" :aria-checked="mode === 'audio'" :class="{ on: mode === 'audio' }" @click="mode = 'audio'">Audio</button>
      </div>

      <div v-if="photoMode" class="grid" role="group" aria-label="Photos in this post">
        <button v-for="(m, i) in media" :key="m.url" type="button" class="thumb" :class="{ off: !picked[i], clip: m.kind === 'video' }"
          :aria-pressed="!!picked[i]" :aria-label="m.name" @click="togglePick(i)">
          <img v-if="m.kind === 'image'" :src="m.url" alt="" loading="lazy" referrerpolicy="no-referrer" />
          <Film v-else :size="24" />
          <span class="tick" aria-hidden="true"><Check :size="14" :stroke-width="3" /></span>
        </button>
      </div>
      <div v-if="photoMode" class="pick">
        <span class="num">{{ selectedCount }} of {{ media.length }} selected</span>
        <button type="button" class="all" @click="toggleAll">{{ allSelected ? 'Clear all' : 'Select all' }}</button>
      </div>

      <template v-if="video && !photoMode">
        <p v-if="videoEngine === 'preparing'" class="hint">Getting the video engine ready. First run only, then it is instant.</p>
        <div class="seg two" role="radiogroup" aria-label="Save as">
          <button type="button" role="radio" :aria-checked="mode === 'video'" :class="{ on: mode === 'video' }" @click="mode = 'video'">Video</button>
          <button type="button" role="radio" :aria-checked="mode === 'audio'" :class="{ on: mode === 'audio' }" @click="mode = 'audio'">Audio</button>
        </div>
        <div v-if="mode === 'video'" class="seg four" role="radiogroup" aria-label="Video quality">
          <button v-for="[v, l] in QUALITIES" :key="v" type="button" role="radio" :aria-checked="quality === v" :class="{ on: quality === v }" @click="quality = v">{{ l }}</button>
        </div>
        <template v-else>
          <div class="seg five" role="radiogroup" aria-label="Audio format">
            <button v-for="[v, l] in FORMATS" :key="v" type="button" role="radio" :aria-checked="audioFormat === v" :class="{ on: audioFormat === v }" @click="audioFormat = v">{{ l }}</button>
          </div>
          <div v-if="lossy" class="seg three" role="radiogroup" aria-label="Audio bitrate">
            <button v-for="[v, l] in BITRATES" :key="v" type="button" role="radio" :aria-checked="bitrate === v" :class="{ on: bitrate === v }" @click="bitrate = v">{{ l }}</button>
          </div>
        </template>
      </template>

      <div class="dest"><Folder :size="18" /><span>Saves to <b>{{ dest }}</b></span></div>

      <button class="btn primary block" type="submit" :disabled="blocked">{{ action }}</button>

    </form>

    <button class="btn block alt" type="button" @click="torrent"><FileDown :size="19" /> Choose a .torrent file</button>
  </Sheet>
</template>

<style scoped>
.h { margin: 0 0 16px; font: 800 24px/1.2 var(--display); }
.lab { display: block; margin: 0 4px 8px; font-size: 13px; font-weight: 600; color: var(--muted); }
.field { display: flex; align-items: center; height: 54px; padding: 0 6px 0 16px; border-radius: 16px; background: var(--raised); box-shadow: var(--ring); }
.field:focus-within { box-shadow: 0 0 0 2px var(--text); }
.field input { flex: 1; min-width: 0; border: 0; outline: 0; background: none; user-select: text; }
.field input::placeholder { color: var(--muted); opacity: 0.8; }
.paste { display: grid; place-items: center; width: 44px; height: 44px; border-radius: 12px; color: var(--text); }

.card { display: flex; align-items: center; gap: 12px; margin-top: 12px; padding: 12px; border-radius: 18px; background: var(--raised); }
.card.idle { background: transparent; box-shadow: var(--ring); }
.kindicon { flex: none; display: grid; place-items: center; width: 40px; height: 40px; border-radius: 50%; background: var(--pill); color: var(--on-pill); }
.txt { min-width: 0; display: flex; flex-direction: column; }
.txt b { font-weight: 700; overflow-wrap: anywhere; }
.txt small { color: var(--muted); font-size: 13px; }
.force { display: flex; align-items: center; gap: 10px; min-height: 44px; margin: 6px 4px 0; font-size: 14px; color: var(--muted); }
.force input { width: 20px; height: 20px; accent-color: var(--text); }
.hint { margin: 10px 4px 0; font-size: 13px; color: var(--muted); }

.seg { display: grid; gap: 4px; padding: 4px; margin-top: 10px; border-radius: 16px; background: var(--raised); box-shadow: var(--ring); }
.seg.two { grid-template-columns: repeat(2, 1fr); }
.seg.three { grid-template-columns: repeat(3, 1fr); }
.seg.four { grid-template-columns: repeat(4, 1fr); }
.seg.five { grid-template-columns: repeat(5, 1fr); }
.seg button { min-height: 44px; border-radius: 12px; font-weight: 700; font-size: 13.5px; color: var(--muted); }
.seg button.on { background: var(--text); color: var(--bg); }
.seg .n { margin-left: 4px; font-size: 11.5px; opacity: 0.75; }

/* photo picker: one square per picture/clip in the post, tap to leave it out */
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(84px, 1fr)); gap: 8px; margin-top: 12px; }
.thumb { position: relative; display: grid; place-items: center; aspect-ratio: 1; overflow: hidden; border-radius: 16px; background: var(--raised); box-shadow: var(--ring); color: var(--muted); }
.thumb img { width: 100%; height: 100%; object-fit: cover; display: block; }
.thumb.off { box-shadow: inset 0 0 0 1px var(--line); }
.thumb.off img, .thumb.off svg { opacity: 0.35; }
.tick { position: absolute; top: 6px; right: 6px; display: grid; place-items: center; width: 22px; height: 22px; border-radius: 50%; background: var(--accent); color: var(--on-accent); }
.thumb.off .tick { background: color-mix(in srgb, var(--surface) 90%, transparent); color: var(--muted); box-shadow: inset 0 0 0 2px var(--line); }
.pick { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 10px 4px 0; font-size: 13.5px; color: var(--muted); }
.pick .all { min-height: 44px; padding: 0 4px; font-weight: 700; color: var(--text); text-decoration: underline; text-underline-offset: 3px; }


.dest { display: flex; align-items: center; gap: 10px; margin: 16px 4px 18px; font-size: 14px; color: var(--muted); }
.dest b { color: var(--text); font-weight: 700; overflow-wrap: anywhere; }
.btn:disabled { opacity: 0.45; }
.alt { margin-top: 10px; }
</style>
