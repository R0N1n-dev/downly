<script setup>
import { computed } from 'vue'
import { FileArchive, FileVideo, FileAudio, FileImage, FileText, Disc3, Magnet, File } from 'lucide-vue-next'
import { category, HUE } from '../store.js'

const props = defineProps({ item: { type: Object, required: true }, size: { type: Number, default: 46 }, onCard: Boolean })

const MAP = {
  archive: FileArchive, video: FileVideo, audio: FileAudio, image: FileImage,
  doc: FileText, disk: Disc3, torrent: Magnet, other: File
}
const cat = computed(() => category(props.item))
</script>

<template>
   <span class="tile" :class="{ plain: onCard }" :style="{ '--tint': HUE[cat], width: size + 'px', height: size + 'px' }">
    <component :is="MAP[cat]" :size="size * 0.46" :stroke-width="1.9" />
  </span>
</template>

<style scoped>
.tile {
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: var(--text);
  background: color-mix(in srgb, var(--tint) var(--card-mix), var(--surface));
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--text) 8%, transparent);
}
.tile.plain { background: color-mix(in srgb, var(--surface) 72%, transparent); box-shadow: none; }
</style>
