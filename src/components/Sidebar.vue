<script setup>
import { ref, computed } from 'vue'
import { Download, History, Settings, Plus, Film, Magnet, FileArchive, Disc3, FileText, FileAudio, FileImage, File } from 'lucide-vue-next'
import Sheet from './Sheet.vue'
import AppIcon from './AppIcon.vue'
import {
  drawer, view, statusFilter, typeFilter, go, ui, TYPES, statusCounts, typeCounts, totalSpeed, rate
} from '../store.js'

const sheet = ref(null)
let afterClose = null
const ICON = { video: Film, torrent: Magnet, archive: FileArchive, disk: Disc3, doc: FileText, audio: FileAudio, image: FileImage, other: File }
const STATUSES = [
  ['downloading', 'Downloading', 'downloads'], ['queued', 'Queued', 'downloads'], ['paused', 'Paused', 'downloads'],
  ['completed', 'Completed', 'history'], ['failed', 'Failed', 'history']
]

const summary = computed(() => {
  const n = statusCounts.value.downloading
  return n ? `${n} downloading, ${rate(totalSpeed.value)}` : 'Nothing downloading'
})

// every choice navigates, then closes the drawer
function pick(fn) { fn(); sheet.value.close() }
const openAll = () => pick(() => { go('downloads', 'all'); typeFilter.value = '' })
const openHistory = () => pick(() => go('history', 'all'))
const openSettings = () => pick(() => go('settings'))
const openStatus = (s, v) => pick(() => go(v, s))
const openType = k => pick(() => {
  typeFilter.value = typeFilter.value === k ? '' : k
  if (view.value === 'settings') go('downloads', 'all')
})
const add = () => { afterClose = () => (ui.value = { sheet: 'add', detailId: '' }); sheet.value.close() }
const closed = () => { drawer.value = false; afterClose?.(); afterClose = null }
</script>

<template>
  <Sheet ref="sheet" side="left" @close="closed">
    <div class="head">
      <AppIcon :size="48" />
      <div><h2>Downly</h2><p class="num">{{ summary }}</p></div>
    </div>

    <nav aria-label="Sections">
      <button class="item" :aria-current="view === 'downloads' && statusFilter === 'all' && !typeFilter ? 'page' : undefined" @click="openAll">
        <Download :size="20" /><span>All downloads</span><b class="num">{{ statusCounts.all }}</b>
      </button>
      <button class="item" :aria-current="view === 'history' && statusFilter === 'all' ? 'page' : undefined" @click="openHistory">
        <History :size="20" /><span>History</span><b class="num">{{ statusCounts.completed + statusCounts.failed }}</b>
      </button>
      <button class="item" :aria-current="view === 'settings' ? 'page' : undefined" @click="openSettings">
        <Settings :size="20" /><span>Settings</span>
      </button>
    </nav>

    <h3>Status</h3>
    <button v-for="[s, label, v] in STATUSES" :key="s" class="item sub" :aria-pressed="statusFilter === s && view === v" @click="openStatus(s, v)">
      <i class="dot" :class="s"></i><span>{{ label }}</span><b class="num">{{ statusCounts[s] }}</b>
    </button>

    <h3>Type</h3>
    <button v-for="[k, label] in TYPES" :key="k" class="item sub" :aria-pressed="typeFilter === k" @click="openType(k)">
      <component :is="ICON[k]" :size="18" /><span>{{ label }}</span><b class="num">{{ typeCounts[k] || 0 }}</b>
    </button>

    <div class="foot"><button class="btn primary block" @click="add"><Plus :size="18" /> Add download</button></div>
  </Sheet>
</template>

<style scoped>
.head { display: flex; align-items: center; gap: 14px; padding: 4px 8px 14px; }
.head h2 { margin: 0; font: 800 28px/1.1 var(--display); }
.head p { margin: 4px 0 0; font-size: 13px; color: var(--muted); }
h3 { margin: 18px 10px 6px; font-size: 12.5px; font-weight: 700; color: var(--muted); }
.item { display: flex; align-items: center; gap: 12px; width: 100%; min-height: 48px; padding: 0 12px; border-radius: 16px; text-align: left; font-weight: 600; }
.item span { flex: 1; }
.item b { font-weight: 700; font-size: 13px; color: var(--muted); }
.item:active { background: var(--raised); }
.item[aria-current='page'], .item[aria-pressed='true'] { background: var(--pill); color: var(--on-pill); }
.item[aria-current='page'] b, .item[aria-pressed='true'] b { color: var(--on-pill); }
.item.sub { min-height: 44px; font-weight: 500; }
.dot { width: 10px; height: 10px; margin: 0 5px; border-radius: 50%; background: var(--text); }
.dot.paused { background: var(--warn); }
.dot.completed { background: var(--good); }
.dot.failed { background: var(--bad); }
.dot.queued { background: transparent; box-shadow: inset 0 0 0 2px var(--text); }
.foot { position: sticky; bottom: -16px; margin: 16px -16px -16px; padding: 12px 16px 16px; background: var(--surface); }
</style>
