<script setup>
import icon from '../assets/icon.svg'
defineProps({ size: { type: Number, default: 28 } })
</script>

<template>
  <img class="app-icon" :src="icon" :width="size" :height="size" alt="" />
</template>

<style scoped>
.app-icon { flex: none; display: block; }
</style>
