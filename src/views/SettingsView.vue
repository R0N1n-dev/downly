<script setup>
import { Folder, FolderTree, Bell, Sun, Moon, Smartphone, KeyRound, Menu, Check, Cpu } from 'lucide-vue-next'
import { onMounted } from 'vue'
import {
  folderRaw, folderLabel, notifications, theme, setTheme, setNotifications, pickFolder, clearFolder,
  cookies, importCookies, clearCookies, drawer, palette, setPalette, THEMES,
  sortOn, setSort, ensureFolders, destBase, videoEngine, FONTS, font, setFont, fontFiles, checkFontFiles
} from '../store.js'

onMounted(() => { checkFontFiles(); FONTS.forEach(f => f.load?.()) }) // previews need the fonts loaded
const face = f => f.display + ', system-ui, sans-serif'
const ENGINE = { idle: 'Starts when needed', preparing: 'Getting ready (first run only)', ready: 'Ready', error: 'Could not start. Check storage space and try again.' }

const themes = [
  ['system', 'System', Smartphone],
  ['light', 'Light', Sun],
  ['dark', 'Dark', Moon]
]
</script>

<template>
  <div class="screen">
    <div class="bar"><div class="lead"><button class="icon-btn" aria-label="Open menu" @click="drawer = true"><Menu :size="22" /></button><h1 class="title">Settings</h1></div></div>

    <h2 class="group-title">Storage</h2>
    <div class="group">
      <button class="line" @click="pickFolder">
        <span class="ico"><Folder :size="20" /></span>
        <span class="t"><b>Save downloads to</b><small>{{ folderLabel(folderRaw) }}</small></span>
        <span class="go">Change</span>
      </button>
      <button v-if="folderRaw" class="line" @click="clearFolder">
        <span class="ico"></span>
        <span class="t"><b>Use the Downloads folder</b></span>
        <span class="go">Reset</span>
      </button>
    </div>

    <div class="group" style="margin-top: 10px">
      <label class="line">
        <span class="ico"><FolderTree :size="20" /></span>
        <span class="t"><b>Sort into category folders</b><small>{{ destBase }}/Video, Music, Documents, Compressed, Programs, Images, Torrents</small></span>
        <input class="switch" type="checkbox" role="switch" :checked="sortOn" @change="setSort($event.target.checked)" />
      </label>
      <button class="line" @click="ensureFolders">
        <span class="ico"></span>
        <span class="t"><b>Create the folders now</b><small>They are also created when the app first starts</small></span>
        <span class="go">Create</span>
      </button>
    </div>

    <h2 class="group-title">Video</h2>
    <div class="group">
      <div class="line">
        <span class="ico"><Cpu :size="20" /></span>
        <span class="t"><b>Video engine</b><small>{{ ENGINE[videoEngine] || videoEngine }}</small></span>
      </div>
      <button class="line" @click="cookies ? clearCookies() : importCookies()">
        <span class="ico"><KeyRound :size="20" /></span>
        <span class="t"><b>Login cookies</b><small>{{ cookies ? 'cookies.txt imported' : 'Instagram needs one. Export cookies.txt from a desktop browser, then import it.' }}</small></span>
        <span class="go">{{ cookies ? 'Remove' : 'Import' }}</span>
      </button>
    </div>

    <h2 class="group-title">Notifications</h2>
    <div class="group">
      <label class="line">
        <span class="ico"><Bell :size="20" /></span>
        <span class="t"><b>Alerts</b><small>When a download finishes or fails</small></span>
        <input class="switch" type="checkbox" role="switch" :checked="notifications" @change="setNotifications($event.target.checked)" />
      </label>
    </div>

    <h2 class="group-title">Font</h2>
    <div class="group" role="radiogroup" aria-label="Font">
      <button v-for="f in FONTS" :key="f.id" class="line" role="radio" :aria-checked="font === f.id" :disabled="f.file && fontFiles[f.id] === false" @click="setFont(f.id)">
        <span class="aa" :style="{ fontFamily: face(f) }">Aa</span>
        <span class="t"><b :style="{ fontFamily: face(f) }">{{ f.name }}</b><small>{{ f.file && fontFiles[f.id] === false ? 'Add ' + f.file + ' to public/fonts' : f.note }}</small></span>
        <Check v-if="font === f.id" :size="20" />
      </button>
    </div>

    <h2 class="group-title">Colour theme</h2>
    <div class="group" role="radiogroup" aria-label="Colour theme">
      <button v-for="t in THEMES" :key="t.id" class="line" role="radio" :aria-checked="palette === t.id" @click="setPalette(t.id)">
        <span class="dots"><i v-for="c in t.dots" :key="c" :style="{ background: c }"></i></span>
        <span class="t"><b>{{ t.name }}</b><small>{{ t.kind }} · {{ t.use }}</small></span>
        <Check v-if="palette === t.id" :size="20" />
      </button>
    </div>

    <h2 class="group-title">Appearance</h2>
    <div class="seg" role="radiogroup" aria-label="Theme">
      <button v-for="[value, label, icon] in themes" :key="value" role="radio" :aria-checked="theme === value" :class="{ on: theme === value }" @click="setTheme(value)">
        <component :is="icon" :size="17" /> {{ label }}
      </button>
    </div>

    <p class="foot">Downly downloads with aria2. Finished files are copied to your chosen folder.</p>
  </div>
</template>

<style scoped>
.aa { flex: none; width: 56px; font-size: 26px; font-weight: 700; }
.line:disabled { opacity: 0.55; }
.dots { flex: none; display: flex; width: 56px; }
.dots i { width: 22px; height: 22px; margin-right: -8px; border-radius: 50%; box-shadow: 0 0 0 2px var(--surface), inset 0 0 0 1px color-mix(in srgb, var(--text) 18%, transparent); }
.line { display: flex; align-items: center; gap: 14px; width: 100%; padding: 14px 16px; text-align: left; }
.line:active { background: var(--raised); }
.ico { flex: none; display: grid; place-items: center; width: 40px; height: 40px; border-radius: 50%; background: var(--accent-soft); color: var(--text); }
.ico:empty { background: none; }
.t { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.t b { font-weight: 600; font-size: 15.5px; }
.t small { color: var(--muted); font-size: 13px; overflow-wrap: anywhere; }
.go { font-size: 14px; font-weight: 700; color: var(--text); text-decoration: underline; text-underline-offset: 3px; }

.switch { appearance: none; flex: none; width: 50px; height: 30px; border-radius: 15px; background: var(--line); position: relative; transition: background 0.2s; cursor: pointer; }
.switch::after { content: ''; position: absolute; top: 3px; left: 3px; width: 24px; height: 24px; border-radius: 50%; background: #fff; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.3); transition: transform 0.2s; }
.switch:checked { background: var(--accent); }
.switch:checked::after { transform: translateX(20px); }

.seg { display: grid; grid-template-columns: repeat(3, 1fr); gap: 4px; padding: 4px; border-radius: 18px; background: var(--surface); box-shadow: var(--ring); }
.seg button { display: flex; align-items: center; justify-content: center; gap: 7px; height: 44px; border-radius: 14px; font-weight: 600; font-size: 14px; color: var(--muted); }
.seg button.on { background: var(--text); color: var(--bg); }

.foot { margin: 24px 8px 0; font-size: 13px; color: var(--faint); text-align: center; }
</style>
