import { createApp } from 'vue'
import '@fontsource-variable/bricolage-grotesque'
import '@fontsource-variable/dm-sans'
import './styles.css'
import App from './App.vue'
import { applyTheme, applyFont } from './store.js'

applyTheme()
applyFont()
createApp(App).mount('#app')
