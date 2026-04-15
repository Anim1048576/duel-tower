import { readdirSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const testsDirPath = fileURLToPath(new URL('.', import.meta.url))

function collectTestFiles(dirPath) {
  const entries = readdirSync(dirPath, { withFileTypes: true })
  const files = []

  for (const entry of entries) {
    const nextPath = path.join(dirPath, entry.name)

    if (entry.isDirectory()) {
      files.push(...collectTestFiles(nextPath))
      continue
    }

    if (entry.isFile() && entry.name.endsWith('.test.js')) {
      files.push(nextPath)
    }
  }

  return files
}

const testFiles = collectTestFiles(testsDirPath).sort()

if (!testFiles.length) {
  console.log('No frontend tests found.')
  process.exit(0)
}

for (const testFile of testFiles) {
  await import(pathToFileURL(testFile).href)
}

console.log(`ok - executed ${testFiles.length} frontend test file(s)`)
