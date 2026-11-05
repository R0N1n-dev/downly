<script setup>
import { ref, computed } from 'vue'
import { Search, Plus, X, Menu } from 'lucide-vue-next'
import DownloadRow from '../components/DownloadRow.vue'
import AppIcon from '../components/AppIcon.vue'
import {
  active, totalSpeed, engineError, ui, retryEngine, pause, resume, rate, statusCounts, go,
  statusFilter, typeFilter, matchesType, drawer, TYPES
} from '../store.js'

const FILTERS = ['all', 'downloading', 'queued', 'paused']
// a status picked for History (completed, failed) means "all" here
const filter = computed({
  get: () => (FILTERS.includes(statusFilter.value) ? statusFilter.value : 'all'),
  set: v => (statusFilter.value = v)
})
const typeLabel = computed(() => TYPES.find(t => t[0] === typeFilter.value)?.[1])
const pool = computed(() => active.value.filter(matchesType))
const query = ref('')
const searching = ref(false)

const counts = computed(() => ({
  all: pool.value.length,
  downloading: pool.value.filter(i => i.state === 'downloading').length,
  queued: pool.value.filter(i => i.state === 'queued').length,
  paused: pool.value.filter(i => i.state === 'paused').length
}))

const shown = computed(() => {
  const q = query.value.trim().toLowerCase()
  return pool.value.filter(i => (filter.value === 'all' || i.state === filter.value) && (!q || i.name.toLowerCase().includes(q)))
})

const summary = computed(() => {
  const c = statusCounts.value
  if (!c.all) return 'Nothing in progress'
  return [c.downloading && `${c.downloading} downloading`, c.queued && `${c.queued} waiting`, c.paused && `${c.paused} paused`]
    .filter(Boolean).join(', ')
})

function open(it) { ui.value = { sheet: 'details', detailId: it.id } }
function primary(it) { return it.state === 'downloading' || it.state === 'queued' ? pause(it) : resume(it) }
function toggleSearch() { searching.value = !searching.value; if (!searching.value) query.value = '' }
</script>

<template>
  <div class="screen">
    <div class="bar">
      <div class="lead">
        <button class="icon-btn" aria-label="Open menu" @click="drawer = true"><Menu :size="22" /></button>
        <AppIcon :size="30" />
        <h1 class="title">Downly</h1>
      </div>
      <div class="tools">
        <button class="icon-btn" aria-label="Search downloads" @click="toggleSearch"><component :is="searching ? X : Search" :size="21" /></button>
      </div>
    </div>

    <label v-if="searching" class="search">
      <Search :size="18" />
      <input v-model="query" type="search" placeholder="Search downloads" autofocus />
    </label>

    <p class="sub num">{{ summary }}<template v-if="totalSpeed"> · {{ rate(totalSpeed) }}</template></p>

    <div v-if="engineError" class="banner">
      <span>Download engine problem: {{ engineError }}</span>
      <button @click="retryEngine">Retry</button>
    </div>

    <p v-if="typeFilter" class="showing">Showing <b>{{ typeLabel }}</b> <button @click="typeFilter = ''">Clear</button></p>

    <div class="chips" role="tablist">
      <button v-for="f in FILTERS" :key="f" class="chip" :class="{ on: filter === f }" role="tab" :aria-selected="filter === f" @click="filter = f">
        {{ f === 'all' ? 'All' : f[0].toUpperCase() + f.slice(1) }}<small class="num">{{ counts[f] }}</small>
      </button>
    </div>

    <div v-if="shown.length" class="cards" style="margin-top: 14px">
      <DownloadRow v-for="it in shown" :key="it.id" :item="it" @open="open(it)" @primary="primary(it)" />
    </div>

    <div v-else class="empty">
      <h2>{{ query ? 'No matches' : typeFilter ? `No ${typeLabel.toLowerCase()} downloading` : filter === 'all' ? 'Nothing downloading' : 'Nothing here' }}</h2>
      <p>{{ query ? 'Try a different name.' : typeFilter ? 'Finished ones are in History.' : filter === 'all' ? 'Paste a link or magnet to start a download.' : 'No downloads in this state.' }}</p>
      <button v-if="typeFilter && !query" class="btn primary" @click="go('history', 'all')">Open History</button>
      <button v-else-if="!query && filter === 'all'" class="btn primary" @click="ui = { sheet: 'add', detailId: '' }"><Plus :size="18" /> Add download</button>
    </div>
  </div>
</template>

<style scoped>
.showing { margin: 0 0 10px; font-size: 14px; color: var(--muted); }
.showing b { color: var(--text); }
.showing button { margin-left: 6px; min-height: 44px; font-weight: 700; color: var(--text); text-decoration: underline; text-underline-offset: 3px; }
.sub { margin: 0 0 12px; font-size: 14px; color: var(--muted); }
</style>
