<script setup>
import { onMounted, onUnmounted } from 'vue'
import { Download, History, Settings, Plus } from 'lucide-vue-next'
import DownloadsView from './views/DownloadsView.vue'
import HistoryView from './views/HistoryView.vue'
import SettingsView from './views/SettingsView.vue'
import AddSheet from './components/AddSheet.vue'
import DetailsSheet from './components/DetailsSheet.vue'
import Sidebar from './components/Sidebar.vue'
import { active, ui, toast, start, stop, view, drawer } from './store.js'

const tabs = [
  ['downloads', 'Downloads', Download],
  ['history', 'History', History],
  ['settings', 'Settings', Settings]
]

onMounted(start)
onUnmounted(stop)
</script>

<template>
  <DownloadsView v-if="view === 'downloads'" />
  <HistoryView v-else-if="view === 'history'" />
  <SettingsView v-else />

  <button v-if="view !== 'settings'" class="fab" aria-label="Add download" @click="ui = { sheet: 'add', detailId: '' }">
    <Plus :size="26" :stroke-width="2.2" />
  </button>

  <nav class="tabbar" aria-label="Main">
    <button v-for="[id, label, icon] in tabs" :key="id" :class="{ on: view === id }" :aria-current="view === id ? 'page' : undefined" @click="view = id">
      <span class="pill">
        <component :is="icon" :size="21" :stroke-width="view === id ? 2.2 : 1.8" />
        <i v-if="id === 'downloads' && active.length" class="badge num">{{ active.length }}</i>
      </span>
      {{ label }}
    </button>
  </nav>

  <Sidebar v-if="drawer" />
  <AddSheet v-if="ui.sheet === 'add'" @close="ui = { sheet: '', detailId: '' }" />
  <DetailsSheet v-if="ui.sheet === 'details'" @close="ui = { sheet: '', detailId: '' }" />

  <div v-if="toast" class="toast" role="status">{{ toast }}</div>
</template>

<style>
.tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 10;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  max-width: 560px;
  margin: 0 auto;
  padding: 8px 8px calc(var(--bottom) + 8px);
  background: var(--bg);
  border-top: 1px solid var(--line);
}
.tabbar button { display: flex; flex-direction: column; align-items: center; gap: 4px; font-size: 12px; font-weight: 500; color: var(--muted); }
.tabbar button.on { color: var(--text); font-weight: 600; }
.pill { position: relative; display: grid; place-items: center; width: 64px; height: 34px; border-radius: 16px; transition: background 0.2s; }
.on .pill { background: var(--pill); color: var(--on-pill); }
.badge { position: absolute; top: -3px; right: 6px; min-width: 18px; height: 18px; padding: 0 5px; border-radius: 9px; font-style: normal; font-size: 11px; font-weight: 700; line-height: 18px; text-align: center; background: var(--accent); color: var(--on-accent); }

.fab {
  position: fixed;
  right: max(16px, calc(50% - 264px));
  bottom: calc(var(--bottom) + 88px);
  z-index: 9;
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  border-radius: 20px;
  background: var(--accent);
  color: var(--on-accent);
  box-shadow: inset 0 0 0 2px var(--cta-ring), 0 10px 24px -10px color-mix(in srgb, var(--text) 45%, transparent);
}
.fab:active { transform: scale(0.95); }
</style>
