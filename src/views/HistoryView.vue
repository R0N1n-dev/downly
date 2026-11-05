<script setup>
import { ref, computed } from 'vue'
import { Search, X, Trash2, Menu } from 'lucide-vue-next'
import DownloadRow from '../components/DownloadRow.vue'
import {
  finished, ui, resume, openFile, clearHistory as wipe, dayGroup,
  statusFilter, typeFilter, matchesType, drawer, TYPES
} from '../store.js'

const FILTERS = ['all', 'completed', 'failed']
const filter = computed({
  get: () => (FILTERS.includes(statusFilter.value) ? statusFilter.value : 'all'),
  set: v => (statusFilter.value = v)
})
const typeLabel = computed(() => TYPES.find(t => t[0] === typeFilter.value)?.[1])
const pool = computed(() => finished.value.filter(matchesType))
const query = ref('')
const searching = ref(false)

const counts = computed(() => ({
  all: pool.value.length,
  completed: pool.value.filter(i => i.state === 'completed').length,
  failed: pool.value.filter(i => i.state === 'failed').length
}))

const groups = computed(() => {
  const q = query.value.trim().toLowerCase()
  const out = []
  for (const it of pool.value) {
    if (filter.value !== 'all' && it.state !== filter.value) continue
    if (q && !it.name.toLowerCase().includes(q)) continue
    const label = dayGroup(it.created)
    let g = out.find(x => x.label === label)
    if (!g) out.push((g = { label, items: [] }))
    g.items.push(it)
  }
  return out
})

const clearHistory = () => { if (confirm('Clear history? Downloaded files are kept.')) wipe() }
const open = it => (ui.value = { sheet: 'details', detailId: it.id })
const primary = it => (it.state === 'completed' ? openFile(it) : resume(it))
function toggleSearch() { searching.value = !searching.value; if (!searching.value) query.value = '' }
</script>

<template>
  <div class="screen">
    <div class="bar">
      <div class="lead">
        <button class="icon-btn" aria-label="Open menu" @click="drawer = true"><Menu :size="22" /></button>
        <h1 class="title">History</h1>
      </div>
      <div class="tools">
        <button class="icon-btn" aria-label="Search history" @click="toggleSearch"><component :is="searching ? X : Search" :size="21" /></button>
        <button v-if="finished.length" class="icon-btn" aria-label="Clear history" @click="clearHistory"><Trash2 :size="20" /></button>
      </div>
    </div>

    <label v-if="searching" class="search">
      <Search :size="18" />
      <input v-model="query" type="search" placeholder="Search history" autofocus />
    </label>

    <p v-if="typeFilter" class="showing">Showing <b>{{ typeLabel }}</b> <button @click="typeFilter = ''">Clear</button></p>

    <div class="chips" role="tablist">
      <button v-for="f in FILTERS" :key="f" class="chip" :class="{ on: filter === f }" role="tab" :aria-selected="filter === f" @click="filter = f">
        {{ f[0].toUpperCase() + f.slice(1) }}<small class="num">{{ counts[f] }}</small>
      </button>
    </div>

    <template v-if="groups.length">
      <section v-for="g in groups" :key="g.label">
        <h2 class="group-title">{{ g.label }}</h2>
        <div class="cards">
          <DownloadRow v-for="it in g.items" :key="it.id" :item="it" @open="open(it)" @primary="primary(it)" />
        </div>
      </section>
    </template>

    <div v-else class="empty">
      <h2>{{ query ? 'No matches' : 'No history yet' }}</h2>
      <p>{{ query ? 'Try a different name.' : 'Finished and failed downloads show up here.' }}</p>
    </div>
  </div>
</template>

<style scoped>
.showing { margin: 0 0 10px; font-size: 14px; color: var(--muted); }
.showing b { color: var(--text); }
.showing button { margin-left: 6px; min-height: 44px; font-weight: 700; color: var(--text); text-decoration: underline; text-underline-offset: 3px; }
</style>
