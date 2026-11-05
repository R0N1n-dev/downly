// node src/theme.check.mjs  ->  fails if any theme/mode has text that is hard to read
import { THEMES, tokens, HUE, mix, contrast } from './theme.js'
const HUES = Object.values(HUE)
let bad = 0
const need = (name, mode, label, value, min) => { if (value < min) { bad++; console.log(`FAIL ${name}/${mode} ${label} ${value.toFixed(2)} < ${min}`) } }
for (const th of THEMES) for (const mode of ['light', 'dark']) {
  const k = tokens(th, mode)
  need(th.id, mode, 'text/bg', contrast(k.text, k.bg), 7)
  need(th.id, mode, 'muted/bg', contrast(k.muted, k.bg), 4.5)
  need(th.id, mode, 'muted/surface', contrast(k.muted, k.surface), 4.5)
  need(th.id, mode, 'onCta/cta', contrast(k.onCta, k.cta), 4.5)
  need(th.id, mode, 'onPill/pill', contrast(k.onPill, k.pill), 4.5)
  need(th.id, mode, 'bad/surface', contrast(k.bad, k.surface), 4.5)
  need(th.id, mode, 'bad/bg', contrast(k.bad, k.bg), 4.5)
  for (const h of HUES) {
    const card = mix(k.surface, h, k.cardMix / 100)
    need(th.id, mode, `text/card(${h})`, contrast(k.text, card), 7)
    need(th.id, mode, `muted/card(${h})`, contrast(k.muted, card), 4.5)
  }
}
console.log(bad ? `${bad} failures` : `all ${THEMES.length} themes x 2 modes pass`)
process.exit(bad ? 1 : 0)
