<script setup>
import { computed } from 'vue'

const props = defineProps({
  value: { type: Number, default: 0 },
  tone: { type: String, default: 'var(--text)' },
  size: { type: Number, default: 124 }
})
const R = 52
const C = 2 * Math.PI * R
const offset = computed(() => C * (1 - Math.min(100, Math.max(0, props.value)) / 100))
</script>

<template>
  <div class="ring" :style="{ width: size + 'px', height: size + 'px' }">
    <svg viewBox="0 0 120 120" aria-hidden="true">
      <circle cx="60" cy="60" :r="R" fill="none" stroke="var(--line)" stroke-width="8" />
      <circle cx="60" cy="60" :r="R" fill="none" :stroke="tone" stroke-width="8" stroke-linecap="round"
        :stroke-dasharray="C" :stroke-dashoffset="offset" transform="rotate(-90 60 60)" />
    </svg>
    <div class="inside"><slot /></div>
  </div>
</template>

<style scoped>
.ring { position: relative; flex: none; }
svg { width: 100%; height: 100%; }
circle:last-child { transition: stroke-dashoffset 0.6s ease; }
.inside { position: absolute; inset: 0; display: grid; place-items: center; }
</style>
