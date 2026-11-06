// node src/lib/photos.check.mjs -> the photo extractor's rules, checked without a phone.
// Each fixture is a saved public post page (or the interesting slice of one).
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'
import { photosFromHtml, mediaName, postStem, isPhotoPage } from './photos.js'

const here = dirname(fileURLToPath(import.meta.url))
const fixture = name => readFileSync(join(here, '__fixtures__', name), 'utf8')

let failures = 0
function ok(name, condition, detail = '') {
  if (condition) return
  failures++
  console.log(`FAIL ${name}${detail ? `\n     ${detail}` : ''}`)
}
function eq(name, actual, expected) {
  ok(name, Object.is(actual, expected), `expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`)
}
const page = (file, url) => photosFromHtml(fixture(file), url)

// 1. a public single-photo post: one row, JSON-LD's 1080px URL, a still and no muxing
{
  const post = 'https://www.instagram.com/p/SINGLE1/'
  const media = page('instagram-single.html', post)
  eq('single: row count', media.length, 1)
  eq('single: url', media[0]?.url,
    'https://scontent.cdninstagram.com/v/t51.2885-15/333333333_444444444_n.jpg?stp=dst-jpg_s1080x1080&oe=65A1B2C3')
  eq('single: kind', media[0]?.kind, 'image')
  eq('single: ext', media[0]?.ext, 'jpg')
  eq('single: name', mediaName(media[0], 0, post), 'SINGLE1-1.jpg')
  ok('single: not a clip', media[0]?.kind !== 'video')
}

// 2. a three-photo carousel keeps page order and names image0..image2
{
  const post = 'https://www.instagram.com/p/CARO123/'
  const media = page('instagram-carousel.html', post)
  eq('carousel: row count', media.length, 3)
  eq('carousel: order', media.map(m => m.url.split('/').pop().split('?')[0]).join(','),
    '100000001_200000001_n.jpg,100000002_200000002_n.jpg,100000003_200000003_n.jpg')
  eq('carousel: names', media.map((m, i) => mediaName(m, i, post)).join(','),
    'CARO123-1.jpg,CARO123-2.jpg,CARO123-3.jpg')
  ok('carousel: all stills', media.every(m => m.kind === 'image' && m.ext === 'jpg'))
}

// 3. no JSON-LD at all: og:image / og:video carry the post, largest Pinterest copy wins
{
  const post = 'https://www.pinterest.com/pin/1234567890/'
  const media = page('og-only.html', post)
  eq('og: row count', media.length, 2)
  eq('og: image kind', media[0]?.kind, 'image')
  ok('og: image url', /\/originals\//.test(media[0]?.url || ''), media[0]?.url)
  eq('og: image ext', media[0]?.ext, 'jpg')
  eq('og: video kind', media[1]?.kind, 'video')
  eq('og: video ext', media[1]?.ext, 'mp4')
  eq('og: name', mediaName(media[0], 0, post), '1234567890-1.jpg')
}

// 4. one clip and two stills survive side by side, each classified by its own extension
{
  const media = page('mixed.html', 'https://www.instagram.com/p/MIXED1/')
  eq('mixed: row count', media.length, 3)
  eq('mixed: kinds', media.map(m => m.kind).join(','), 'image,image,video')
  eq('mixed: exts', media.map(m => m.ext).join(','), 'jpg,jpg,mp4')
  ok('mixed: video url', /\.mp4\?/.test(media[2]?.url || ''), media[2]?.url)
}

// 5. a WebPage JSON-LD block is the site shell (logo), not the post: fall through to og:image
{
  const media = page('webpage.html', 'https://www.instagram.com/p/WEBPAGECODE/')
  eq('webpage: row count', media.length, 1)
  ok('webpage: post media, not the logo', /scontent\.cdninstagram\.com/.test(media[0]?.url || ''), media[0]?.url)
  eq('webpage: ext', media[0]?.ext, 'jpg')
}

// 6. Instagram carousel without og tags: per-child parse, clips are clips, best copy of each still
{
  const media = page('instagram-sidecar.html', 'https://www.instagram.com/p/SIDECAR1/')
  eq('sidecar: row count', media.length, 3)
  eq('sidecar: kinds', media.map(m => m.kind).join(','), 'image,image,video')
  ok('sidecar: picked the 1080 copy', media[0]?.url.includes('/s1080x1080/'), media[0]?.url)
  eq('sidecar: no poster frame row for the clip', media.filter(m => m.kind === 'image').length, 2)
  ok('sidecar: video url', (media[2]?.url || '').includes('_n.mp4'), media[2]?.url)
}

// 7. helpers: host gate, stems, malformed input
ok('hosts: instagram', isPhotoPage('https://www.instagram.com/p/abc/'))
ok('hosts: pinterest', isPhotoPage('https://pinterest.com/pin/1/'))
ok('hosts: plain site is not a photo page', !isPhotoPage('https://example.com/a.jpg'))
eq('stem: reel', postStem('https://www.instagram.com/reel/Cx1Yz2/'), 'Cx1Yz2')
eq('stem: fallback', postStem('https://example.com/some-file.jpg'), 'some-file-jpg')
eq('empty html', photosFromHtml('', 'https://www.instagram.com/p/x/').length, 0)
eq('broken json-ld does not throw', photosFromHtml('<script type="application/ld+json">{oops</script>', 'https://x.com/a').length, 0)

console.log(failures ? `${failures} failures` : 'photo extractor: all checks pass')
process.exit(failures ? 1 : 0)
