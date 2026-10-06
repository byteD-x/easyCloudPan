/* eslint-disable no-console */
import sharp from 'sharp'
import path from 'path'
import fs from 'fs'
import { fileURLToPath } from 'url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const src = path.resolve(__dirname, '../src/assets/music_cover.png')
const dest = path.resolve(__dirname, '../src/assets/music_cover.webp')

console.log(`Converting ${src} to ${dest}...`)

if (fs.existsSync(src)) {
  sharp(src)
    .webp({ quality: 80 })
    .toFile(dest)
    .then((info) => {
      console.log('Successfully converted music_cover.png to WebP')
      console.log(`Original size: ${fs.statSync(src).size} bytes`)
      console.log(`New size: ${info.size} bytes`)
    })
    .catch((err) => console.error('Error converting image:', err))
} else {
  console.error('Source file not found:', src)
}
