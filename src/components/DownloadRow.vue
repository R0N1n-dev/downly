<script setup>
import { computed } from 'vue'
import { Pause, Play, RotateCw, ExternalLink } from 'lucide-vue-next'
import FileIcon from './FileIcon.vue'
import { size, rate, duration, percent, clock, pair, category, HUE } from '../store.js'

const props = defineProps({ item: { type: Object, required: true } })
const emit = defineEmits(['open', 'primary'])

const it = computed(() => props.item)
const pct = computed(() => percent(it.value))
const unknown = computed(() => it.value.total <= 0 && !['completed', 'failed'].includes(it.value.state))
const hue = computed(() => (it.value.state === 'failed' ? HUE.failed : HUE[category(it.value)]))

const left = computed(() => {
  const i = it.value
  if (i.state === 'completed') return `${size(i.total)} · ${clock(i.created)}`
  if (i.state === 'failed') return i.error || 'Download failed'
  if (unknown.value) return i.type === 'magnet' ? 'Fetching metadata' : i.type === 'video' ? 'Preparing video' : 'Starting'
  return pair(i.done, i.total)
})

const right = computed(() => {
  const i = it.value
  if (i.state === 'downloading') return [rate(i.speed), duration(i.eta)].filter(Boolean).join(' · ')
  if (i.state === 'paused') return 'Paused'
  if (i.state === 'queued') return 'Waiting'
  return ''
})

const showBar = computed(() => !['completed', 'failed'].includes(it.value.state))
const action = computed(() => ({
  downloading: ['Pause', Pause], queued: ['Pause', Pause], paused: ['Resume', Play],
  failed: ['Retry', RotateCw], completed: ['Open file', ExternalLink]
}[it.value.state]))
</script>

<template>
  <div class="row" :style="{ '--h': hue }" role="button" tabindex="0" @click="emit('open')" @keydown.enter="emit('open')">
    <FileIcon :item="it" on-card />
    <div class="text">
      <div class="name">{{ it.name }}</div>
      <div class="sub num" :class="{ bad: it.state === 'failed' }">
        <span class="l">{{ left }}</span>
        <span class="r" :class="{ warn: it.state === 'paused' }">{{ right }}</span>
      </div>
      <div v-if="showBar" class="track" :class="{ wait: unknown, paused: it.state === 'paused' }"
        role="progressbar" :aria-label="it.name + ' progress'" aria-valuemin="0" aria-valuemax="100"
        :aria-valuenow="unknown ? undefined : pct">
        <i :style="{ '--p': pct / 100 }"></i>
      </div>
    </div>
    <button class="act" :aria-label="action[0]" @click.stop="emit('primary')"><component :is="action[1]" :size="19" /></button>
  </div>
</template>

<style scoped>
.row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px;
  border-radius: 26px;
  cursor: pointer;
  content-visibility: auto; /* off-screen rows cost nothing */
  contain-intrinsic-size: auto 92px;
  background: color-mix(in srgb, var(--h) var(--card-mix), var(--surface));
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--text) 8%, transparent);
}
.row:active { transform: scale(0.99); }
.text { flex: 1; min-width: 0; }
.name { font-weight: 700; font-size: 15.5px; line-height: 1.25; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sub { display: flex; justify-content: space-between; gap: 10px; margin-top: 3px; font-size: 13px; color: var(--muted); }
.sub .l { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sub .r { flex: none; }
.sub.bad .l, .warn { color: var(--text); font-weight: 700; }
.track { height: 8px; margin-top: 10px; border-radius: 4px; background: color-mix(in srgb, var(--text) 16%, transparent); overflow: hidden; }
.track i { display: block; width: 100%; height: 100%; border-radius: 4px; background: var(--fill, color-mix(in srgb, var(--h) 78%, var(--text))); transform: scaleX(var(--p)); transform-origin: left; transition: transform 0.8s linear, opacity 0.4s ease; }
.track.paused i { opacity: 0.5; }
.track.wait i { transform: none; opacity: 0.35; animation: breathe 2.4s ease-in-out infinite; } /* a slow fade, not a fast slide that reads as flicker */
@keyframes breathe { 50% { opacity: 0.75; } }
.act { flex: none; display: grid; place-items: center; width: 44px; height: 44px; border-radius: 50%; background: var(--surface); color: var(--text); box-shadow: var(--ring); }
.act:active { transform: scale(0.94); }
</style>
