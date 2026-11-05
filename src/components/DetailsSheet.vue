<script setup>
import { ref, computed, watch } from 'vue'
import { Pause, Play, RotateCw, ExternalLink, Share2, Trash2, Check, X, Copy } from 'lucide-vue-next'
import Sheet from './Sheet.vue'
import Ring from './Ring.vue'
import FileIcon from './FileIcon.vue'
import {
  items, ui, pause, resume, remove, openFile, share, say,
  size, rate, duration, percent, fullDate, folderLabel
} from '../store.js'

const emit = defineEmits(['close'])
const sheet = ref(null)
const it = computed(() => items.value.find(i => i.id === ui.value.detailId))

// If the download disappears (deleted), close the sheet.
watch(it, v => { if (!v) sheet.value?.close() })

const statusText = computed(() => ({
  downloading: 'Downloading', queued: 'Waiting', paused: 'Paused', completed: 'Completed', failed: 'Failed'
}[it.value?.state] || ''))

const tone = computed(() => ({ paused: 'var(--warn)', completed: 'var(--good)', failed: 'var(--bad)' }[it.value?.state] || 'var(--text)'))
const source = computed(() => it.value?.uri || (it.value?.type === 'torrent' ? 'Torrent file' : ''))

async function copy(text) {
  try { await navigator.clipboard.writeText(text); say('Copied') } catch { /* ignore */ }
}

async function del() {
  const kept = ['completed', 'failed'].includes(it.value.state)
  if (!confirm(kept ? 'Remove from history? The file stays where it was saved.' : 'Cancel and delete this download?')) return
  await remove(it.value)
  sheet.value?.close()
}
</script>

<template>
  <Sheet ref="sheet" @close="emit('close')">
    <template v-if="it">
      <header class="head">
        <FileIcon :item="it" :size="52" />
        <div class="who">
          <h2>{{ it.name }}</h2>
          <span class="state" :style="{ color: it.state === 'downloading' || it.state === 'queued' ? 'var(--text)' : tone }">{{ statusText }}</span>
        </div>
      </header>

      <div class="main">
        <Ring :value="percent(it)" :tone="tone">
          <Check v-if="it.state === 'completed'" :size="40" :stroke-width="2.2" :color="'var(--good)'" />
          <X v-else-if="it.state === 'failed'" :size="40" :stroke-width="2.2" :color="'var(--bad)'" />
          <span v-else class="pct num">{{ percent(it) }}<small>%</small></span>
        </Ring>
        <dl class="stats num">
          <div><dt>Downloaded</dt><dd>{{ size(it.done) }}</dd></div>
          <div><dt>Total</dt><dd>{{ it.total > 0 ? size(it.total) : 'Unknown' }}</dd></div>
          <div>
            <dt>Speed</dt>
            <dd>{{ it.state === 'downloading' ? rate(it.speed) : '–' }}<small v-if="it.state === 'downloading' && it.connections">{{ it.connections }} connections</small></dd>
          </div>
          <div><dt>Time left</dt><dd>{{ it.state === 'downloading' && it.eta >= 0 ? duration(it.eta) : '–' }}</dd></div>
        </dl>
      </div>

      <p v-if="it.state === 'failed'" class="err">{{ it.error || 'Download failed' }}</p>

      <div class="actions">
        <button v-if="it.state === 'downloading' || it.state === 'queued'" class="btn primary" @click="pause(it)"><Pause :size="18" /> Pause</button>
        <button v-else-if="it.state === 'paused'" class="btn primary" @click="resume(it)"><Play :size="18" /> Resume</button>
        <button v-else-if="it.state === 'failed'" class="btn primary" @click="resume(it)"><RotateCw :size="18" /> Try again</button>
        <button v-else class="btn primary" @click="openFile(it)"><ExternalLink :size="18" /> Open</button>
        <button class="btn sq" aria-label="Share" @click="share(it)"><Share2 :size="19" /></button>
        <button class="btn sq danger" aria-label="Delete" @click="del"><Trash2 :size="19" /></button>
      </div>

      <dl class="info">
        <div v-if="source" class="tap" @click="copy(source)">
          <dt>Source</dt><dd class="clip">{{ source }}<Copy :size="14" /></dd>
        </div>
        <div><dt>Saves to</dt><dd>{{ folderLabel(it.folder) }}</dd></div>
        <div><dt>Added</dt><dd>{{ fullDate(it.created) }}</dd></div>
      </dl>
    </template>
  </Sheet>
</template>

<style scoped>
.head { display: flex; align-items: center; gap: 14px; }
.who { min-width: 0; }
.who h2 { margin: 0; font: 500 20px/1.25 var(--display); word-break: break-word; }
.state { font-size: 14px; font-weight: 600; }

.main { display: flex; align-items: center; gap: 22px; margin: 22px 0 18px; }
.pct { font: 400 38px/1 var(--display); letter-spacing: -0.02em; }
.pct small { font-size: 18px; color: var(--muted); }
.stats { flex: 1; display: grid; grid-template-columns: 1fr 1fr; gap: 14px 12px; margin: 0; }
.stats dt { font-size: 12.5px; color: var(--muted); }
.stats dd { margin: 2px 0 0; font-weight: 600; font-size: 16px; }
.stats dd small { display: block; font-weight: 400; font-size: 12px; color: var(--muted); }

.err { margin: 0 0 14px; padding: 12px 14px; border-radius: 14px; font-size: 14px; color: var(--bad); background: color-mix(in srgb, var(--bad) 11%, var(--surface)); }

.actions { display: flex; gap: 10px; }
.actions .primary { flex: 1; }
.btn.sq { width: 52px; padding: 0; }

.info { margin: 22px 0 0; border-top: 1px solid var(--line); }
.info > div { padding: 13px 0; border-bottom: 1px solid var(--line); }
.info > div:last-child { border-bottom: 0; }
.info dt { font-size: 12.5px; color: var(--muted); }
.info dd { margin: 3px 0 0; font-size: 14.5px; word-break: break-all; user-select: text; }
.clip { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; }
.clip svg { flex: none; margin-top: 3px; color: var(--faint); }
.tap:active { opacity: 0.6; }
</style>
