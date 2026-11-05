<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'

// Bottom sheet. It owns one history entry so the Android back button closes it.
const props = defineProps({ side: { type: String, default: 'bottom' } })
const emit = defineEmits(['close'])
const shown = ref(false)
let popped = false
let closing = false

function finish() {
  if (closing) return
  closing = true
  shown.value = false
  setTimeout(() => emit('close'), 240)
}
function onPop() { popped = true; finish() }
function close() { if (!closing) history.back() }

defineExpose({ close })

onMounted(() => {
  history.pushState({ sheet: true }, '')
  window.addEventListener('popstate', onPop)
  requestAnimationFrame(() => requestAnimationFrame(() => (shown.value = true)))
})
onBeforeUnmount(() => {
  window.removeEventListener('popstate', onPop)
  if (!popped) history.back()
})
</script>

<template>
  <div class="root" :class="{ open: shown }">
    <div class="scrim" @click="close"></div>
    <section class="sheet" :class="{ left: side === 'left' }" role="dialog" aria-modal="true">
      <div v-if="side !== 'left'" class="grip"></div>
      <slot :close="close" />
    </section>
  </div>
</template>

<style scoped>
.root { position: fixed; inset: 0; z-index: 20; pointer-events: none; }
.root.open { pointer-events: auto; }
.scrim { position: absolute; inset: 0; background: var(--scrim); opacity: 0; transition: opacity 0.24s ease; }
.open .scrim { opacity: 1; }
.sheet {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  max-width: 560px;
  margin: 0 auto;
  max-height: 92dvh;
  overflow-y: auto;
  padding: 10px 20px calc(var(--bottom) + 20px);
  border-radius: 28px 28px 0 0;
  background: var(--surface);
  box-shadow: 0 -1px 0 var(--line);
  transform: translateY(100%);
  transition: transform 0.2s ease-in;
}
.open .sheet { transform: translateY(0); transition: transform 0.32s cubic-bezier(0.2, 0.8, 0.2, 1); }
.sheet.left { top: 0; right: auto; bottom: 0; width: min(320px, 86vw); max-width: none; max-height: none; margin: 0; padding: calc(var(--top) + 16px) 16px calc(var(--bottom) + 16px); border-radius: 0 28px 28px 0; box-shadow: none; transform: translateX(-100%); }
.open .sheet.left { transform: translateX(0); }
.grip { width: 40px; height: 4px; margin: 0 auto 14px; border-radius: 2px; background: var(--line); }
</style>
