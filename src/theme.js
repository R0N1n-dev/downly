// Colour themes. Sorbet is hand-tuned. The nine from app-theme.txt are generated from their palette
// roles, then nudged until text, buttons and tab pills pass 4.5:1 (checked by theme.check.mjs).

const toRgb = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16))
const toHex = rgb => '#' + rgb.map(v => Math.round(Math.max(0, Math.min(255, v))).toString(16).padStart(2, '0')).join('')
export const mix = (a, b, t) => { const x = toRgb(a), y = toRgb(b); return toHex(x.map((v, i) => v * (1 - t) + y[i] * t)) } // t=0 -> a, t=1 -> b
const lin = v => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4 }
export const lum = h => { const [r, g, b] = toRgb(h).map(lin); return 0.2126 * r + 0.7152 * g + 0.0722 * b }
export const contrast = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05) }
const rgba = (h, a) => `rgba(${toRgb(h).join(', ')}, ${a})`

// move `color` toward `target` until it reaches `min` contrast against every colour in `against`
function reach(color, target, against, min) {
  let c = color
  for (let i = 0; i < 20 && Math.min(...against.map(b => contrast(c, b))) < min; i++) c = mix(c, target, 0.1)
  return c
}
// best text colour on `bg`: the darker or the white, whichever reads better
const on = (bg, dark) => (contrast(dark, bg) >= contrast('#ffffff', bg) ? dark : '#ffffff')

export const THEMES = [
  {
    id: 'sorbet', name: 'Sorbet', kind: 'Default', use: 'Pastel cards on mint paper', dots: ['#f4f9f1', '#f9d96b', '#ffa692', '#9ed2f2'],
    fixed: {
      light: { bg: '#f4f9f1', surface: '#ffffff', raised: '#e8f0e4', line: 'rgba(23, 38, 42, 0.14)', text: '#17262a', muted: '#2f4448',
        cta: '#17262a', onCta: '#f4f9f1', pill: '#f9d96b', onPill: '#17262a', cardMix: 100, fill: '#17262a' },
      dark: { bg: '#0f1b18', surface: '#182824', raised: '#223630', line: 'rgba(234, 243, 238, 0.16)', text: '#eaf3ee', muted: '#a6bcb2',
        cta: '#f9d96b', onCta: '#0f1b18', pill: '#f9d96b', onPill: '#0f1b18', cardMix: 24 }
    }
  },
  // dual: 80% primary, 20% accent
  { id: 'classic', name: 'Classic Corporate', kind: 'Dual 80/20', use: 'Fintech, SaaS, crypto', dots: ['#1E2761', '#408EC6'],
    ground: '#1E2761', ink: '#1E2761', cta: '#1E2761', pill: '#408EC6', ctaD: '#408EC6' },
  { id: 'retail', name: 'Energetic Retail', kind: 'Dual 80/20', use: 'E-commerce, delivery, travel', dots: ['#2C3E50', '#E67E22'],
    ground: '#2C3E50', ink: '#2C3E50', cta: '#2C3E50', pill: '#E67E22', ctaD: '#E67E22' },
  { id: 'eco', name: 'Eco-Organic', kind: 'Dual 80/20', use: 'Sustainability, gardening, wellness', dots: ['#2C5F2D', '#97BC62'],
    ground: '#2C5F2D', ink: '#2C5F2D', cta: '#2C5F2D', pill: '#97BC62', ctaD: '#97BC62' },
  { id: 'cyber', name: 'Cyber High-Contrast', kind: 'Dual 80/20', use: 'Productivity, developer tools, fitness', dots: ['#1A1A1A', '#C1DD13'],
    ground: '#1A1A1A', ink: '#1A1A1A', cta: '#1A1A1A', pill: '#C1DD13', ctaD: '#C1DD13' },
  { id: 'editorial', name: 'Minimalist Editorial', kind: 'Dual 80/20', use: 'Fashion, portfolio, lifestyle', dots: ['#CC313D', '#F7C5CC'],
    ground: '#F7C5CC', groundD: '#CC313D', bgW: 0.55, ink: '#CC313D', cta: '#CC313D', pill: '#F7C5CC' },
  // triple: 60% dominant, 30% structural, 10% accent
  { id: 'hightrio', name: 'The High-End Trio', kind: 'Triple 60/30/10', use: 'Luxury retail, portfolios, fine dining', dots: ['#1E2761', '#408EC6', '#7A2048'],
    ground: '#1E2761', ink: '#1E2761', cta: '#7A2048', pill: '#408EC6' },
  { id: 'brighttech', name: 'The Bright Tech Vibe', kind: 'Triple 60/30/10', use: 'SaaS dashboards, finance, data tracking', dots: ['#2D3047', '#2EC4B6', '#FF7A59'],
    ground: '#2D3047', ink: '#2D3047', cta: '#FF7A59', pill: '#2EC4B6' },
  { id: 'pastel', name: 'The Playful Pastel', kind: 'Triple 60/30/10', use: 'Casual social, mindfulness, education', dots: ['#B298E7', '#B8E3E9', '#F5B8D5'],
    ground: '#B298E7', ink: '#B298E7', cta: '#F5B8D5', pill: '#B8E3E9' },
  { id: 'earthy', name: 'The Earthy Muted', kind: 'Triple 60/30/10', use: 'Home decor, plants, architecture', dots: ['#4A5842', '#EFEBE4', '#D98A6C'],
    ground: '#EFEBE4', groundD: '#4A5842', bgW: 0.5, ink: '#4A5842', cta: '#D98A6C', pill: '#4A5842' }
]

// File-type colours (and the failed colour). Sorbet shows them as solid cards, other themes as a tint.
export const HUE = {
  archive: '#f9d96b', video: '#ffa692', audio: '#f5a9c0', image: '#8ed8cf',
  doc: '#a8e1b8', disk: '#9ed2f2', torrent: '#ffc89b', other: '#d3dcd7', failed: '#ff9d9d'
}

export const themeById = id => THEMES.find(t => t.id === id) || THEMES[0]

const STATUS = {
  light: { good: '#0b7f58', warn: '#8f5c00', bad: '#c42f48', scrim: 'rgba(23, 31, 36, 0.45)' },
  dark: { good: '#4fdcae', warn: '#f4b440', bad: '#ff8aa0', scrim: 'rgba(2, 5, 8, 0.66)' }
}

// -> every token the CSS needs, for one theme in one mode ('light' | 'dark')
export function tokens(theme, mode) {
  const dark = mode === 'dark'
  let t
  if (theme.fixed) {
    t = { ...theme.fixed[mode] }
  } else {
    const bg = dark ? mix(theme.groundD || theme.ground, '#000000', 0.84) : mix(theme.ground, '#ffffff', theme.bgW ?? 0.92)
    const surface = dark ? mix(bg, '#ffffff', 0.07) : '#ffffff'
    const text = dark ? reach(mix(theme.ground, '#ffffff', 0.92), '#ffffff', [bg, surface], 10) : reach(theme.ink, '#000000', [bg, surface], 10)
    const raised = dark ? mix(bg, '#ffffff', 0.13) : mix(bg, text, 0.06)
    const muted = reach(mix(text, bg, 0.3), text, [bg, surface, raised], 5)
    let cta = dark ? (theme.ctaD || reach(theme.cta, '#ffffff', [bg], 4.5)) : theme.cta
    let pill = dark ? (theme.pillD || reach(theme.pill, '#ffffff', [bg], 3)) : theme.pill
    const inkOn = dark ? mix(bg, '#000000', 0.5) : text
    let onCta = on(cta, inkOn), onPill = on(pill, inkOn)
    for (let i = 0; i < 20 && contrast(onCta, cta) < 4.5; i++) cta = mix(cta, onCta === '#ffffff' ? '#000000' : '#ffffff', 0.06)
    for (let i = 0; i < 20 && contrast(onPill, pill) < 4.5; i++) pill = mix(pill, onPill === '#ffffff' ? '#000000' : '#ffffff', 0.06)
    t = { bg, surface, raised, line: rgba(text, 0.14), text, muted, cta, onCta, pill, onPill, cardMix: dark ? 24 : 16, fill: dark ? undefined : text }
  }
  // secondary text must stay readable on the tinted cards too
  const cards = Object.values(HUE).map(h => mix(t.surface, h, t.cardMix / 100))
  t.muted = reach(t.muted, t.text, [t.bg, t.surface, t.raised, ...cards], 4.6)
  return {
    ...t,
    ctaRing: contrast(t.cta, t.bg) < 3 ? t.text : 'transparent', // a pale button on a pale page gets an outline
    accentSoft: mix(t.pill, t.surface, 0.55),
    ...STATUS[mode]
  }
}

// CSS custom properties for a token set
export function cssVars(k) {
  return {
    '--bg': k.bg, '--surface': k.surface, '--raised': k.raised, '--line': k.line, '--text': k.text, '--muted': k.muted,
    '--accent': k.cta, '--on-accent': k.onCta, '--pill': k.pill, '--on-pill': k.onPill, '--accent-soft': k.accentSoft,
    '--card-mix': k.cardMix + '%', '--cta-ring': k.ctaRing, '--good': k.good, '--warn': k.warn, '--bad': k.bad, '--scrim': k.scrim,
    '--fill': k.fill
  }
}
