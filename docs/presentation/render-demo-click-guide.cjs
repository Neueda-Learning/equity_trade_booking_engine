const fs = require('fs')
const path = require('path')
const { chromium } = require('../../frontend/node_modules/playwright')

const directory = __dirname
const source = process.env.DEMO_GUIDE_SOURCE
  ? path.resolve(process.env.DEMO_GUIDE_SOURCE)
  : path.join(directory, 'demo-click-guide.md')
const output = process.env.DEMO_GUIDE_OUTPUT
  ? path.resolve(process.env.DEMO_GUIDE_OUTPUT)
  : path.join(directory, 'demo-click-guide.pdf')
const footerTitle =
  process.env.DEMO_GUIDE_TITLE || 'TradeFlow Demo Click Guide'
const font = fs.readFileSync(
  path.join(directory, 'assets', 'fonts', 'wqy-zenhei-script.ttf'),
).toString('base64')

function inline(value) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/&lt;(https?:\/\/[^&]+)&gt;/g, '<a href="$1">$1</a>')
}

function markdownToHtml(markdown) {
  const output = []
  let ordered = false
  let unordered = false
  let listItem = false

  function closeLists() {
    if (unordered) {
      output.push('</ul>')
      unordered = false
    }
    if (listItem) {
      output.push('</li>')
      listItem = false
    }
    if (ordered) {
      output.push('</ol>')
      ordered = false
    }
  }

  for (const line of markdown.split(/\r?\n/)) {
    const heading = line.match(/^(#{1,3})\s+(.+)$/)
    const orderedItem = line.match(/^(\d+)\.\s+(.+)$/)
    const nestedBullet = line.match(/^\s+-\s+(.+)$/)

    if (heading) {
      closeLists()
      const level = heading[1].length
      output.push(`<h${level}>${inline(heading[2])}</h${level}>`)
      continue
    }

    if (orderedItem) {
      if (!ordered) {
        closeLists()
        output.push('<ol>')
        ordered = true
      } else {
        if (unordered) {
          output.push('</ul>')
          unordered = false
        }
        if (listItem) output.push('</li>')
      }
      output.push(`<li>${inline(orderedItem[2])}`)
      listItem = true
      continue
    }

    if (nestedBullet) {
      if (!unordered) {
        output.push('<ul>')
        unordered = true
      }
      output.push(`<li>${inline(nestedBullet[1])}</li>`)
      continue
    }

    if (!line.trim()) {
      continue
    }

    closeLists()
    output.push(`<p>${inline(line)}</p>`)
  }

  closeLists()
  return output.join('\n')
}

async function main() {
  const body = markdownToHtml(fs.readFileSync(source, 'utf8'))
  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage()
  await page.setContent(`<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <style>
    @font-face {
      font-family: "Guide Sans";
      src: url(data:font/ttf;base64,${font}) format("truetype");
    }
    @page { size: A4; margin: 15mm 14mm 16mm; }
    * { box-sizing: border-box; }
    html { color: #172033; font-family: "Guide Sans", Arial, sans-serif; }
    body { margin: 0; font-size: 10.5pt; line-height: 1.48; }
    h1 {
      margin: 0 0 6mm;
      color: #0f172a;
      font-size: 24pt;
      line-height: 1.2;
      border-bottom: 3px solid #2563eb;
      padding-bottom: 3mm;
    }
    h2 {
      margin: 7mm 0 3mm;
      color: #1d4ed8;
      font-size: 16pt;
      line-height: 1.25;
      break-after: avoid;
    }
    h3 {
      margin: 5mm 0 2mm;
      color: #334155;
      font-size: 12.5pt;
      line-height: 1.3;
      break-after: avoid;
    }
    p { margin: 0 0 3mm; }
    ol, ul { margin: 0 0 3mm; padding-left: 7mm; }
    li { margin: 0 0 1.4mm; padding-left: 1.2mm; break-inside: avoid; }
    li::marker { color: #2563eb; font-weight: 700; }
    ul { margin-top: 1.5mm; }
    code {
      padding: .25mm 1.1mm;
      color: #1d4ed8;
      background: #eff6ff;
      border: 1px solid #bfdbfe;
      border-radius: 3px;
      font-family: "Guide Sans", Arial, sans-serif;
      font-weight: 700;
      white-space: nowrap;
    }
    strong { color: #0f172a; }
    a { color: #2563eb; text-decoration: none; }
  </style>
</head>
<body>${body}</body>
</html>`, { waitUntil: 'load' })
  await page.emulateMedia({ media: 'print' })
  await page.pdf({
    path: output,
    format: 'A4',
    printBackground: true,
    displayHeaderFooter: true,
    headerTemplate: '<span></span>',
    footerTemplate: `
      <div style="width:100%;font:8px Arial;color:#64748b;
                  text-align:center;padding:0 14mm;">
        ${inline(footerTitle)} ·
        <span class="pageNumber"></span> / <span class="totalPages"></span>
      </div>`,
    margin: {
      top: '15mm',
      right: '14mm',
      bottom: '16mm',
      left: '14mm',
    },
  })
  await browser.close()
  console.log(`Wrote ${output}`)
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
