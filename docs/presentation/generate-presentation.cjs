const fs = require('fs')
const path = require('path')
const PptxGenJS = require('/tmp/deskflow-ppt-deps/node_modules/pptxgenjs')

const englishOnly = process.argv.includes('--english')
const pptx = new PptxGenJS()
pptx.layout = 'LAYOUT_WIDE'
pptx.author = 'Group 5 — Give me five'
pptx.company = 'Equity Trade Booking Engine'
pptx.subject = englishOnly ? 'English project presentation' : 'Bilingual project presentation'
pptx.title = englishOnly ? 'Equity Trade Booking Engine' : 'Equity Trade Booking Engine / 股票交易记账引擎'
pptx.lang = englishOnly ? 'en-US' : 'zh-CN'
pptx.theme = {
  headFontFace: englishOnly ? 'Arial' : 'Microsoft YaHei',
  bodyFontFace: englishOnly ? 'Arial' : 'Microsoft YaHei',
  lang: englishOnly ? 'en-US' : 'zh-CN',
}
pptx.defineSlideMaster({
  title: 'CONTENT',
  background: { color: 'F3F7FC' },
  objects: [
    { rect: { x: 0, y: 0, w: 0.12, h: 7.5, fill: { color: '2563EB' }, line: { color: '2563EB' } } },
    { text: { text: 'EQUITY TRADE BOOKING ENGINE', options: { x: 0.55, y: 7.08, w: 4.6, h: 0.2, fontFace: 'Arial', fontSize: 7.5, bold: true, color: '64748B', charSpacing: 1.2, margin: 0 } } },
  ],
  slideNumber: { x: 12.35, y: 7.04, w: 0.4, h: 0.22, color: '64748B', fontFace: 'Arial', fontSize: 8, align: 'right', margin: 0 },
})

const C = {
  navy: '0F172A',
  navy2: '172554',
  blue: '2563EB',
  blue2: '1D4ED8',
  sky: 'DBEAFE',
  pale: 'EFF6FF',
  paper: 'FFFFFF',
  bg: 'F3F7FC',
  text: '0F172A',
  muted: '64748B',
  line: 'CBD5E1',
  green: '15803D',
  greenBg: 'DCFCE7',
  red: 'DC2626',
  redBg: 'FEE2E2',
  amber: 'D97706',
  amberBg: 'FEF3C7',
  slate: '334155',
}
const S = pptx.ShapeType
const assets = path.join(__dirname, 'assets')
const notesMarkdown = fs.readFileSync(path.join(__dirname, 'speaker-notes-bilingual.md'), 'utf8')
const noteBlocks = notesMarkdown.split(/^## Slide /m).slice(1)
const notes = new Map(noteBlocks.map((block) => [Number(block.match(/^(\d+)/)[1]), `Slide ${block}`]))

function addNotes(slide, number) {
  const block = notes.get(number) || ''
  if (!englishOnly) {
    slide.addNotes(block)
    return
  }
  const english = block.match(/### English\s*\n+([\s\S]*)$/)
  slide.addNotes(english ? english[1].trim() : block)
}

function addTitle(slide, number, cn, en, kicker = 'PROJECT STORY') {
  slide.addText(kicker, { x: 0.62, y: 0.35, w: 3.2, h: 0.2, fontFace: 'Arial', fontSize: 8, bold: true, color: C.blue, charSpacing: 1.5, margin: 0 })
  if (englishOnly) {
    slide.addText(en, { x: 0.62, y: 0.64, w: 9.4, h: 0.58, fontFace: 'Arial', fontSize: 24, bold: true, color: C.text, margin: 0, breakLine: false, fit: 'shrink' })
  } else {
    slide.addText(cn, { x: 0.62, y: 0.62, w: 8.4, h: 0.45, fontSize: 24, bold: true, color: C.text, margin: 0, breakLine: false })
    slide.addText(en, { x: 0.62, y: 1.08, w: 8.8, h: 0.28, fontFace: 'Arial', fontSize: 10.5, bold: true, color: C.muted, charSpacing: 0.3, margin: 0 })
  }
  slide.addText(String(number).padStart(2, '0'), { x: 11.75, y: 0.42, w: 0.9, h: 0.36, fontFace: 'Arial', fontSize: 18, bold: true, color: 'C7D2FE', align: 'right', margin: 0 })
}

function addCard(slide, x, y, w, h, options = {}) {
  slide.addShape(S.roundRect, {
    x, y, w, h,
    rectRadius: 0.08,
    fill: { color: options.fill || C.paper, transparency: options.transparency || 0 },
    line: { color: options.line || 'DCE5F1', width: options.lineWidth || 0.8 },
    shadow: options.shadow === false ? undefined : { type: 'outer', color: '94A3B8', opacity: 0.13, blur: 1.5, angle: 45, distance: 1 },
  })
}

function addBilingual(slide, cn, en, x, y, w, h, options = {}) {
  const cnSize = options.cnSize || 15
  const enSize = options.enSize || 8.5
  if (englishOnly) {
    slide.addText(en, {
      x, y, w, h,
      fontFace: 'Arial',
      fontSize: options.enMainSize || cnSize,
      bold: options.bold !== false,
      color: options.color || C.text,
      valign: options.valign || 'mid',
      align: options.align || 'left',
      margin: options.margin ?? 0.08,
      breakLine: false,
      fit: 'shrink',
    })
    return
  }
  slide.addText([
    { text: cn, options: { bold: options.bold !== false, fontSize: cnSize, color: options.color || C.text, breakLine: true } },
    { text: en, options: { fontFace: 'Arial', fontSize: enSize, color: options.enColor || C.muted, bold: options.enBold || false } },
  ], { x, y, w, h, valign: options.valign || 'mid', align: options.align || 'left', margin: options.margin ?? 0.08, breakLine: false, fit: 'shrink' })
}

function addTag(slide, text, x, y, w, fill = C.sky, color = C.blue2) {
  slide.addShape(S.roundRect, { x, y, w, h: 0.32, rectRadius: 0.08, fill: { color: fill }, line: { color: fill } })
  slide.addText(text, { x, y: y + 0.02, w, h: 0.22, fontFace: 'Arial', fontSize: 7.5, bold: true, color, align: 'center', margin: 0, fit: 'shrink' })
}

function addArrow(slide, x, y, w, color = C.blue) {
  slide.addShape(S.line, { x, y, w, h: 0, line: { color, width: 2, beginArrowType: 'none', endArrowType: 'triangle' } })
}

function addImageFrame(slide, file, x, y, w, h, alt) {
  slide.addShape(S.roundRect, { x: x - 0.04, y: y - 0.04, w: w + 0.08, h: h + 0.08, rectRadius: 0.08, fill: { color: C.paper }, line: { color: 'CBD5E1', width: 0.8 }, shadow: { type: 'outer', color: '64748B', opacity: 0.22, blur: 2, angle: 45, distance: 1.5 } })
  slide.addImage({ path: path.join(assets, file), x, y, w, h, altText: alt })
}

function addMetric(slide, x, y, w, labelCn, labelEn, value, color = C.text) {
  addCard(slide, x, y, w, 1.15)
  addBilingual(slide, labelCn, labelEn, x + 0.18, y + 0.13, w - 0.36, 0.38, { cnSize: 10, enSize: 7.3, bold: false })
  slide.addText(value, { x: x + 0.18, y: y + 0.59, w: w - 0.36, h: 0.35, fontFace: 'Arial', fontSize: 19, bold: true, color, margin: 0, fit: 'shrink' })
}

// 1 — Cover
{
  const slide = pptx.addSlide()
  slide.background = { color: C.navy }
  slide.addShape(S.rect, { x: 0, y: 0, w: 13.333, h: 7.5, fill: { color: C.navy }, line: { color: C.navy } })
  slide.addShape(S.ellipse, { x: -1.2, y: -1.6, w: 5, h: 5, fill: { color: C.blue, transparency: 72 }, line: { color: C.blue, transparency: 100 } })
  slide.addText('GROUP 5  /  GIVE ME FIVE', { x: 0.75, y: 0.58, w: 4.2, h: 0.25, fontFace: 'Arial', fontSize: 9, bold: true, color: '93C5FD', charSpacing: 1.8, margin: 0 })
  slide.addText(englishOnly ? 'Equity Trade\nBooking Engine' : '股票交易\n记账引擎', { x: 0.75, y: 1.35, w: 4.6, h: 1.6, fontFace: englishOnly ? 'Arial' : undefined, fontSize: 34, bold: true, color: C.paper, margin: 0, breakLine: false, fit: 'shrink' })
  if (!englishOnly) slide.addText('Equity Trade\nBooking Engine', { x: 0.75, y: 3.12, w: 4.45, h: 1.08, fontFace: 'Arial', fontSize: 23, bold: true, color: 'BFDBFE', margin: 0, breakLine: false, fit: 'shrink' })
  slide.addText(englishOnly ? 'Trade facts · Replayable positions · Explainable quotes · Consistent P&L' : '交易事实 · 可重算持仓 · 可解释行情 · 一致盈亏\nTrade facts · Replayable positions · Explainable quotes · Consistent P&L', { x: 0.75, y: englishOnly ? 3.5 : 4.58, w: 4.45, h: 0.75, fontFace: englishOnly ? 'Arial' : undefined, fontSize: englishOnly ? 13 : 11, color: 'CBD5E1', breakLine: false, margin: 0, fit: 'shrink' })
  addTag(slide, 'JAVA 21', 0.75, 5.82, 0.92, '1E3A8A', 'DBEAFE')
  addTag(slide, 'SPRING BOOT', 1.8, 5.82, 1.28, '1E3A8A', 'DBEAFE')
  addTag(slide, 'REACT 19', 3.22, 5.82, 1.02, '1E3A8A', 'DBEAFE')
  addTag(slide, 'MYSQL + REDIS', 0.75, 6.25, 1.58, '1E3A8A', 'DBEAFE')
  addTag(slide, 'DOCKER COMPOSE', 2.47, 6.25, 1.7, '1E3A8A', 'DBEAFE')
  addImageFrame(slide, 'dashboard.png', 5.48, 1.13, 7.25, 4.53, 'Dashboard showing portfolio metrics and position P&L')
  slide.addShape(S.roundRect, { x: 7.72, y: 5.38, w: 4.22, h: 0.75, rectRadius: 0.08, fill: { color: C.blue }, line: { color: C.blue } })
  addBilingual(slide, '从交易录入到组合估值的可信闭环', 'A trusted loop from booking to valuation', 7.9, 5.49, 3.86, 0.5, { cnSize: 13, enSize: 8, color: C.paper, enColor: 'DBEAFE', align: 'center' })
  slide.addText('2026.07', { x: 11.75, y: 6.88, w: 0.9, h: 0.2, fontFace: 'Arial', fontSize: 8, color: '64748B', align: 'right', margin: 0 })
  addNotes(slide, 1)
}

// 2 — Problem and outcome
{
  const slide = pptx.addSlide('CONTENT')
  addTitle(slide, 2, '从碎片操作到可信闭环', 'From Fragmented Tasks to a Trusted Loop', 'WHY THIS SYSTEM')
  const rows = [
    ['修改覆盖原记录', 'Mutation erases history', '审计保留的生命周期', 'Audit-preserving lifecycle'],
    ['卖出与撤单易破坏持仓', 'Position drift after changes', '按时间重放交易事实', 'Chronological trade replay'],
    ['行情故障可能误导估值', 'Quote failure can mislead', '显式来源与陈旧状态', 'Explicit source and staleness'],
  ]
  slide.addText(englishOnly ? 'COMMON RISKS' : '常见风险 / COMMON RISKS', { x: 0.8, y: 1.63, w: 4.4, h: 0.28, fontFace: 'Arial', fontSize: 10, bold: true, color: C.red, margin: 0 })
  slide.addText(englishOnly ? 'OUR RESPONSE' : '本项目的回答 / OUR RESPONSE', { x: 7.9, y: 1.63, w: 4.35, h: 0.28, fontFace: 'Arial', fontSize: 10, bold: true, color: C.blue, margin: 0 })
  rows.forEach((r, i) => {
    const y = 2.02 + i * 1.28
    addCard(slide, 0.8, y, 4.42, 0.93, { fill: i === 0 ? 'FFF7F7' : C.paper, line: 'FECACA' })
    slide.addShape(S.ellipse, { x: 1.03, y: y + 0.28, w: 0.34, h: 0.34, fill: { color: C.redBg }, line: { color: C.redBg } })
    slide.addText('!', { x: 1.03, y: y + 0.29, w: 0.34, h: 0.22, fontFace: 'Arial', fontSize: 10, bold: true, color: C.red, align: 'center', margin: 0 })
    addBilingual(slide, r[0], r[1], 1.52, y + 0.15, 3.4, 0.58, { cnSize: 13, enSize: 8 })
    addArrow(slide, 5.62, y + 0.47, 1.45, '94A3B8')
    addCard(slide, 7.75, y, 4.58, 0.93, { fill: i === 2 ? C.pale : C.paper, line: 'BFDBFE' })
    slide.addShape(S.ellipse, { x: 7.98, y: y + 0.28, w: 0.34, h: 0.34, fill: { color: C.sky }, line: { color: C.sky } })
    slide.addText('✓', { x: 7.98, y: y + 0.28, w: 0.34, h: 0.22, fontFace: 'Arial', fontSize: 10, bold: true, color: C.blue2, align: 'center', margin: 0 })
    addBilingual(slide, r[2], r[3], 8.48, y + 0.15, 3.5, 0.58, { cnSize: 13, enSize: 8 })
  })
  slide.addShape(S.roundRect, { x: 2.18, y: 6.2, w: 8.98, h: 0.55, rectRadius: 0.08, fill: { color: C.navy }, line: { color: C.navy } })
  addBilingual(slide, '交易是事实，持仓可重算，行情只估值，盈亏统一算', 'Trades are facts. Positions are replayed. Quotes value. The backend calculates.', 2.45, 6.28, 8.44, 0.35, { cnSize: 14, enSize: 8.5, color: C.paper, enColor: 'BFDBFE', align: 'center' })
  addNotes(slide, 2)
}

// 3 — Business loop
{
  const slide = pptx.addSlide('CONTENT')
  addTitle(slide, 3, '端到端业务闭环', 'End-to-End Business Loop', 'BUSINESS FLOW')
  const steps = [
    ['01', '账户', 'Account', 'ACTIVE / INACTIVE'],
    ['02', '交易', 'Activity', 'BUY / SELL'],
    ['03', '持仓', 'Position', 'Replay BOOKED'],
    ['04', '行情', 'Market Data', 'MOCK / LIVE'],
    ['05', '估值', 'Dashboard', 'P&L + History'],
  ]
  steps.forEach((s, i) => {
    const x = 0.62 + i * 2.54
    addCard(slide, x, 2.05, 2.02, 2.55, { fill: i === 4 ? C.pale : C.paper, line: i === 4 ? '93C5FD' : 'DCE5F1' })
    slide.addShape(S.ellipse, { x: x + 0.65, y: 2.3, w: 0.72, h: 0.72, fill: { color: i === 4 ? C.blue : C.sky }, line: { color: i === 4 ? C.blue : C.sky } })
    slide.addText(s[0], { x: x + 0.65, y: 2.48, w: 0.72, h: 0.22, fontFace: 'Arial', fontSize: 12, bold: true, color: i === 4 ? C.paper : C.blue2, align: 'center', margin: 0 })
    addBilingual(slide, s[1], s[2], x + 0.23, 3.18, 1.56, 0.62, { cnSize: 17, enSize: 9, align: 'center' })
    slide.addText(s[3], { x: x + 0.23, y: 4.03, w: 1.56, h: 0.22, fontFace: 'Arial', fontSize: 7.5, bold: true, color: C.muted, align: 'center', margin: 0, fit: 'shrink' })
    if (i < steps.length - 1) addArrow(slide, x + 2.08, 3.33, 0.37, '94A3B8')
  })
  slide.addShape(S.line, { x: 10.1, y: 4.92, w: -7.85, h: 0, line: { color: C.blue, width: 1.5, dash: 'dash', beginArrowType: 'none', endArrowType: 'triangle' } })
  addBilingual(slide, '取消 / 删除 / 修改会重新影响持仓与盈亏，但保留原始审计证据', 'Cancel, delete, or amend recalculates downstream values without erasing evidence.', 2.45, 5.18, 7.55, 0.72, { cnSize: 13, enSize: 8.5, align: 'center' })
  addTag(slide, 'MYSQL = SYSTEM OF RECORD', 4.62, 6.05, 2.3, 'E2E8F0', C.slate)
  addNotes(slide, 3)
}

// 4 — Lifecycle
{
  const slide = pptx.addSlide('CONTENT')
  addTitle(slide, 4, '交易生命周期与业务不变量', 'Lifecycle and Business Invariants', 'DOMAIN INTEGRITY')
  const y = 2.05
  addCard(slide, 0.75, y, 2.35, 1.18, { fill: C.pale, line: '93C5FD' })
  addBilingual(slide, 'BOOKED', englishOnly ? 'BOOKED\nValid business fact' : 'Valid business fact', 1.05, y + 0.25, 1.75, 0.65, { cnSize: 17, enSize: 8.5, align: 'center', color: C.blue2 })
  addArrow(slide, 3.25, y + 0.58, 1.0)
  addCard(slide, 4.4, y, 2.35, 1.18, { fill: 'FFF7ED', line: 'FED7AA' })
  addBilingual(slide, 'CANCELLED', englishOnly ? 'CANCELLED\nReason is recorded' : 'Reason is recorded', 4.68, y + 0.25, 1.8, 0.65, { cnSize: 17, enSize: 8.5, align: 'center', color: C.amber })
  addArrow(slide, 6.9, y + 0.58, 1.0, C.amber)
  addCard(slide, 8.05, y, 4.4, 1.18, { fill: C.paper, line: 'CBD5E1' })
  addBilingual(slide, '原因：CANCELLED / DELETED / AMENDED', englishOnly ? 'Reasons: CANCELLED / DELETED / AMENDED\nAmendment links a replacement via supersedesTradeId' : 'Amendment creates a linked replacement via supersedesTradeId', 8.35, y + 0.18, 3.8, 0.78, { cnSize: 13, enSize: 8.5, align: 'center' })
  slide.addText('3', { x: 0.77, y: 4.08, w: 0.65, h: 0.65, fontFace: 'Arial', fontSize: 33, bold: true, color: C.blue, align: 'center', margin: 0 })
  addBilingual(slide, '条不可破坏的不变量', 'Non-negotiable invariants', 1.55, 4.05, 2.65, 0.62, { cnSize: 16, enSize: 9 })
  const invariants = [
    ['取消幂等', 'Idempotent cancellation'],
    ['时间线上禁止负持仓', 'No negative point in time'],
    ['仅 BOOKED 参与持仓', 'Only BOOKED trades count'],
  ]
  invariants.forEach((item, i) => {
    const x = 0.78 + i * 4.13
    addCard(slide, x, 4.92, 3.76, 1.08, { fill: i === 1 ? C.pale : C.paper, line: i === 1 ? '93C5FD' : 'DCE5F1' })
    slide.addShape(S.ellipse, { x: x + 0.25, y: 5.23, w: 0.4, h: 0.4, fill: { color: C.greenBg }, line: { color: C.greenBg } })
    slide.addText('✓', { x: x + 0.25, y: 5.3, w: 0.4, h: 0.18, fontFace: 'Arial', fontSize: 9, bold: true, color: C.green, align: 'center', margin: 0 })
    addBilingual(slide, item[0], item[1], x + 0.78, 5.08, 2.7, 0.68, { cnSize: 13, enSize: 8 })
  })
  addNotes(slide, 4)
}

// 5 — Calculations
{
  const slide = pptx.addSlide('CONTENT')
  addTitle(slide, 5, '持仓与盈亏计算', 'Position and P&L Calculation', 'CALCULATION MODEL')
  addCard(slide, 0.72, 1.65, 5.2, 4.85, { fill: C.navy, line: C.navy })
  slide.addText('WEIGHTED-AVERAGE COST', { x: 1.05, y: 1.98, w: 3.6, h: 0.25, fontFace: 'Arial', fontSize: 9, bold: true, color: '93C5FD', charSpacing: 1.2, margin: 0 })
  addBilingual(slide, '买入增加数量与成本基础', 'BUY increases quantity and cost basis', 1.05, 2.42, 4.5, 0.65, { cnSize: 15, enSize: 9, color: C.paper, enColor: 'CBD5E1' })
  addBilingual(slide, '卖出按当前平均成本减少成本基础', 'SELL reduces cost basis at the current average cost', 1.05, 3.25, 4.5, 0.72, { cnSize: 15, enSize: 9, color: C.paper, enColor: 'CBD5E1' })
  slide.addShape(S.line, { x: 1.05, y: 4.18, w: 4.42, h: 0, line: { color: '334155', width: 1 } })
  slide.addText('marketValue = quantity × marketPrice', { x: 1.05, y: 4.48, w: 4.5, h: 0.35, fontFace: 'Arial', fontSize: 14, bold: true, color: 'BFDBFE', margin: 0 })
  slide.addText('unrealizedPnl = marketValue − costBasis', { x: 1.05, y: 5.05, w: 4.5, h: 0.35, fontFace: 'Arial', fontSize: 14, bold: true, color: 'BFDBFE', margin: 0 })
  slide.addText('pnlPercent = unrealizedPnl ÷ costBasis × 100', { x: 1.05, y: 5.62, w: 4.5, h: 0.35, fontFace: 'Arial', fontSize: 13, bold: true, color: 'BFDBFE', margin: 0, fit: 'shrink' })
  slide.addText('AAPL  ·  DEMO GROWTH', { x: 6.45, y: 1.7, w: 3.6, h: 0.28, fontFace: 'Arial', fontSize: 10, bold: true, color: C.blue, charSpacing: 1.1, margin: 0 })
  addMetric(slide, 6.43, 2.15, 1.8, '数量', 'Quantity', '8')
  addMetric(slide, 8.42, 2.15, 1.8, '平均成本', 'Avg cost', '$100.00')
  addMetric(slide, 10.41, 2.15, 1.8, '市场价', 'Market price', '$195.25')
  addMetric(slide, 6.43, 3.65, 2.79, '成本基础', 'Cost basis', '$800.00')
  addMetric(slide, 9.42, 3.65, 2.79, '市值', 'Market value', '$1,562.00')
  addCard(slide, 6.43, 5.17, 5.78, 1.28, { fill: C.greenBg, line: '86EFAC' })
  addBilingual(slide, '未实现盈亏 / 收益率', 'Unrealized P&L / Return', 6.72, 5.38, 2.7, 0.65, { cnSize: 13, enSize: 8.5, color: C.green })
  slide.addText('+$762.00  /  +95.25%', { x: 9.2, y: 5.54, w: 2.65, h: 0.38, fontFace: 'Arial', fontSize: 18, bold: true, color: C.green, align: 'right', margin: 0, fit: 'shrink' })
  slide.addText('Decimal arithmetic on the backend · Missing quotes remain null', { x: 6.45, y: 6.7, w: 5.75, h: 0.22, fontFace: 'Arial', fontSize: 8, color: C.muted, margin: 0 })
  addNotes(slide, 5)
}

// 6 — Architecture
{
  const slide = pptx.addSlide('CONTENT')
  addTitle(slide, 6, '模块化单体架构', 'Modular Monolith Architecture', 'SYSTEM DESIGN')
  addCard(slide, 0.72, 1.72, 2.05, 4.95, { fill: C.navy, line: C.navy })
  addBilingual(slide, '用户界面', 'USER EXPERIENCE', 1.05, 2.02, 1.4, 0.62, { cnSize: 15, enSize: 8, color: C.paper, enColor: '93C5FD', align: 'center' })
  addCard(slide, 1.05, 2.92, 1.4, 0.85, { fill: '1E3A8A', line: '1E3A8A', shadow: false })
  addBilingual(slide, 'React 19', englishOnly ? 'React 19\nTypeScript + Vite' : 'TypeScript + Vite', 1.18, 3.08, 1.14, 0.52, { cnSize: 12, enSize: 7.5, color: C.paper, enColor: 'BFDBFE', align: 'center' })
  slide.addShape(S.line, { x: 1.75, y: 3.82, w: 0, h: 0.38, line: { color: '93C5FD', width: 2, beginArrowType: 'none', endArrowType: 'triangle' } })
  addCard(slide, 1.05, 4.28, 1.4, 0.85, { fill: '1E3A8A', line: '1E3A8A', shadow: false })
  addBilingual(slide, 'Nginx', englishOnly ? 'Nginx\nReverse proxy' : 'Reverse proxy', 1.18, 4.45, 1.14, 0.48, { cnSize: 12, enSize: 7.5, color: C.paper, enColor: 'BFDBFE', align: 'center' })
  addBilingual(slide, '桌面 + 移动端', 'Desktop + mobile', 1.02, 5.66, 1.45, 0.52, { cnSize: 10.5, enSize: 7.5, color: 'CBD5E1', enColor: '94A3B8', align: 'center' })
  addArrow(slide, 2.95, 4.22, 0.65, '94A3B8')

  addCard(slide, 3.72, 1.72, 5.6, 4.95, { fill: C.paper, line: 'BFDBFE' })
  slide.addText('SPRING BOOT 3.5 · JAVA 21', { x: 4.05, y: 1.98, w: 3.5, h: 0.25, fontFace: 'Arial', fontSize: 9, bold: true, color: C.blue, charSpacing: 1.1, margin: 0 })
  const modules = [
    ['Account', '账户'], ['Trade', '交易'], ['Position', '持仓'], ['Market Data', '行情'], ['P&L', '盈亏'],
  ]
  modules.forEach((m, i) => {
    const x = 4.02 + (i % 3) * 1.65
    const y2 = 2.48 + Math.floor(i / 3) * 0.94
    addCard(slide, x, y2, 1.42, 0.68, { fill: i === 1 ? C.pale : 'F8FAFC', line: i === 1 ? '93C5FD' : 'CBD5E1', shadow: false })
    addBilingual(slide, m[1], m[0], x + 0.12, y2 + 0.08, 1.18, 0.5, { cnSize: 11, enSize: 7.3, align: 'center' })
  })
  slide.addShape(S.line, { x: 4.02, y: 4.55, w: 4.98, h: 0, line: { color: C.line, width: 1 } })
  const layers = [
    ['HTTP API', '请求与契约'], ['Application', '用例编排'], ['Domain', '业务规则'], ['Infrastructure', '端口适配'],
  ]
  layers.forEach((l, i) => {
    const x = 4.02 + i * 1.23
    const fill = i === 2 ? C.blue : i === 3 ? 'E2E8F0' : C.sky
    const color = i === 2 ? C.paper : C.slate
    slide.addShape(S.chevron, { x, y: 4.93, w: 1.35, h: 0.88, fill: { color: fill }, line: { color: C.paper, width: 1 } })
    addBilingual(slide, l[1], l[0], x + 0.05, 5.08, 1.05, 0.53, { cnSize: 9.5, enSize: 6.7, align: 'center', color, enColor: color })
  })
  slide.addText('API → Application → Domain ← Infrastructure', { x: 4.06, y: 6.05, w: 4.85, h: 0.25, fontFace: 'Arial', fontSize: 9.5, bold: true, color: C.slate, align: 'center', margin: 0 })
  addArrow(slide, 9.48, 4.22, 0.65, '94A3B8')

  addCard(slide, 10.25, 1.72, 2.35, 4.95, { fill: 'F8FAFC', line: 'CBD5E1' })
  addBilingual(slide, '数据与外部服务', 'DATA & EXTERNALS', 10.6, 1.98, 1.65, 0.6, { cnSize: 14, enSize: 8, align: 'center' })
  const stores = [
    ['事实来源', 'MySQL 8.4 · System of record', C.blue, C.pale],
    ['可丢弃行情缓存', 'Redis 7.4 · Disposable quote cache', C.amber, C.amberBg],
    ['行情 Provider', 'Mock / Finnhub provider', C.green, C.greenBg],
  ]
  stores.forEach((st, i) => {
    const y2 = 2.9 + i * 1.05
    addCard(slide, 10.55, y2, 1.75, 0.78, { fill: st[3], line: st[3], shadow: false })
    addBilingual(slide, st[0], st[1], 10.68, y2 + 0.09, 1.49, 0.55, { cnSize: 11.5, enSize: 7.4, color: st[2], enColor: C.slate, align: 'center' })
  })
  addTag(slide, 'REDIS ≠ SYSTEM OF RECORD', 10.46, 6.0, 1.94, 'E2E8F0', C.slate)
  addNotes(slide, 6)
}

// 7 — Market data resilience
{
  const slide = pptx.addSlide('CONTENT')
  addTitle(slide, 7, '行情韧性与诚实降级', 'Market-Data Resilience and Honest Degradation', 'RESILIENCE')
  const nodes = [
    [0.72, '请求行情', 'Request quote', C.paper, C.blue],
    [3.15, 'Provider', englishOnly ? 'Provider\nMock or Finnhub' : 'Mock or Finnhub', C.pale, C.blue2],
    [5.58, 'Redis', englishOnly ? 'Redis\nFresh + retained' : 'Fresh + retained', C.amberBg, C.amber],
    [8.01, '估值结果', 'Valuation result', C.greenBg, C.green],
  ]
  nodes.forEach((n, i) => {
    addCard(slide, n[0], 2.05, 1.85, 1.3, { fill: n[3], line: n[3] === C.paper ? 'CBD5E1' : n[3], shadow: false })
    addBilingual(slide, n[1], n[2], n[0] + 0.18, 2.28, 1.49, 0.76, { cnSize: 14, enSize: 8, color: n[4], enColor: C.slate, align: 'center' })
    if (i < nodes.length - 1) addArrow(slide, n[0] + 1.92, 2.69, 0.42, '94A3B8')
  })
  addArrow(slide, 9.96, 2.69, 0.42, '94A3B8')
  addCard(slide, 10.52, 1.82, 2.08, 1.77, { fill: C.navy, line: C.navy })
  addBilingual(slide, '状态必须可见', 'STATUS MUST BE VISIBLE', 10.8, 2.02, 1.52, 0.56, { cnSize: 13, enSize: 7.5, color: C.paper, enColor: '93C5FD', align: 'center' })
  addTag(slide, 'MOCK', 10.8, 2.76, 0.62, '1E3A8A', 'DBEAFE')
  addTag(slide, 'LIVE', 11.51, 2.76, 0.62, '14532D', 'DCFCE7')
  addTag(slide, 'STALE', 10.8, 3.12, 0.62, '78350F', 'FEF3C7')
  addTag(slide, 'CACHED', 11.51, 3.12, 0.62, '334155', 'E2E8F0')

  slide.addText('CACHE WINDOWS', { x: 0.82, y: 4.12, w: 2.0, h: 0.25, fontFace: 'Arial', fontSize: 9, bold: true, color: C.blue, charSpacing: 1.2, margin: 0 })
  slide.addShape(S.line, { x: 0.82, y: 4.88, w: 10.86, h: 0, line: { color: C.line, width: 4 } })
  slide.addShape(S.line, { x: 0.82, y: 4.88, w: 4.0, h: 0, line: { color: C.green, width: 7 } })
  slide.addShape(S.line, { x: 4.82, y: 4.88, w: 5.85, h: 0, line: { color: C.amber, width: 7 } })
  slide.addShape(S.line, { x: 10.67, y: 4.88, w: 1.01, h: 0, line: { color: C.red, width: 7 } })
  addBilingual(slide, '新鲜期', 'Fresh TTL · default 60s', 0.82, 5.08, 3.55, 0.62, { cnSize: 13, enSize: 8, color: C.green })
  addBilingual(slide, '保留期：仅故障时陈旧回退', 'Retention TTL · stale fallback on failure only', 4.83, 5.08, 5.25, 0.62, { cnSize: 13, enSize: 8, color: C.amber })
  addBilingual(slide, '无可用行情', 'Unavailable', 10.68, 5.08, 1.05, 0.62, { cnSize: 11, enSize: 7, color: C.red, align: 'center' })
  slide.addShape(S.roundRect, { x: 2.2, y: 6.08, w: 8.9, h: 0.52, rectRadius: 0.08, fill: { color: C.redBg }, line: { color: 'FCA5A5' } })
  addBilingual(slide, '真实 Provider 失败时绝不自动伪装成 Mock', 'A failed real provider never silently falls back to generated Mock data.', 2.45, 6.15, 8.4, 0.32, { cnSize: 13, enSize: 8.2, color: C.red, enColor: '991B1B', align: 'center' })
  addNotes(slide, 7)
}

// 8 — Dashboard screenshot
{
  const slide = pptx.addSlide('CONTENT')
  addTitle(slide, 8, 'Dashboard：从交易到组合洞察', 'From Trades to Portfolio Insight', 'PRODUCT EXPERIENCE')
  addImageFrame(slide, 'dashboard.png', 4.05, 1.55, 8.55, 5.34, 'Demo Growth dashboard with portfolio valuation and position P&L')
  const items = [
    ['统一计算', 'One backend model'],
    ['状态透明', 'Source + freshness'],
    ['历史真实', 'Persisted snapshots'],
  ]
  items.forEach((item, i) => {
    const y = 1.75 + i * 1.42
    slide.addShape(S.ellipse, { x: 0.74, y, w: 0.54, h: 0.54, fill: { color: i === 1 ? C.amberBg : C.sky }, line: { color: i === 1 ? C.amberBg : C.sky } })
    slide.addText(String(i + 1), { x: 0.74, y: y + 0.13, w: 0.54, h: 0.2, fontFace: 'Arial', fontSize: 10, bold: true, color: i === 1 ? C.amber : C.blue2, align: 'center', margin: 0 })
    addBilingual(slide, item[0], item[1], 1.48, y - 0.03, 2.15, 0.68, { cnSize: 15, enSize: 8.3 })
  })
  addCard(slide, 0.74, 5.97, 2.88, 0.88, { fill: C.navy, line: C.navy })
  addBilingual(slide, '不伪造零价格或估值历史', 'No fake zero prices or fabricated history', 0.95, 6.1, 2.45, 0.55, { cnSize: 11.5, enSize: 7.7, color: C.paper, enColor: 'BFDBFE', align: 'center' })
  addNotes(slide, 8)
}

// 9 — Product workbench
{
  const slide = pptx.addSlide('CONTENT')
  addTitle(slide, 9, '产品工作台', 'Product Workbench', 'OPERATIONS')
  const panels = [
    ['accounts.png', '账户', 'Accounts', '账户状态 + 账户级持仓', 'Account status + account-level positions'],
    ['activity.png', '交易活动', 'Activity', '录入 + 查询 + 审计操作', 'Booking + search + audit actions'],
    ['market-data.png', '市场行情', 'Market Data', '搜索 + 刷新 + 故障演练', 'Search + refresh + outage simulation'],
  ]
  panels.forEach((p, i) => {
    const x = 0.72 + i * 4.16
    addImageFrame(slide, p[0], x, 1.76, 3.72, 2.325, `${p[2]} product page`)
    addCard(slide, x, 4.28, 3.72, 1.58, { fill: i === 1 ? C.pale : C.paper, line: i === 1 ? '93C5FD' : 'DCE5F1' })
    addBilingual(slide, p[1], p[2], x + 0.25, 4.48, 3.22, 0.55, { cnSize: 16, enSize: 9, align: 'center' })
    slide.addText(englishOnly ? p[4] : p[3], { x: x + 0.28, y: 5.18, w: 3.16, h: 0.28, fontFace: englishOnly ? 'Arial' : undefined, fontSize: 10.5, color: C.muted, align: 'center', margin: 0, fit: 'shrink' })
  })
  slide.addShape(S.roundRect, { x: 2.05, y: 6.25, w: 9.25, h: 0.5, rectRadius: 0.08, fill: { color: 'E2E8F0' }, line: { color: 'E2E8F0' } })
  addBilingual(slide, '同一信息架构覆盖桌面与移动端 · 状态不只依赖颜色', 'One information architecture for desktop and mobile · Text labels reinforce color', 2.33, 6.32, 8.69, 0.3, { cnSize: 12.5, enSize: 8, color: C.slate, enColor: C.muted, align: 'center' })
  addNotes(slide, 9)
}

// 10 — Quality
{
  const slide = pptx.addSlide('CONTENT')
  addTitle(slide, 10, '工程质量与交付', 'Engineering Quality and Delivery', 'QUALITY GATE')
  const jobs = [
    ['BACKEND', 'Unit + API +\nTestcontainers'],
    ['FRONTEND', 'Vitest + ESLint +\nProduction build'],
    ['COMPOSE', 'Lifecycle + P&L +\nStale fallback'],
    ['E2E', 'Playwright desktop +\nmobile journey'],
  ]
  jobs.forEach((j, i) => {
    const x = 0.75 + i * 2.63
    addCard(slide, x, 1.87, 2.25, 1.55, { fill: i === 2 ? C.pale : C.paper, line: i === 2 ? '93C5FD' : 'CBD5E1' })
    slide.addText(j[0], { x: x + 0.22, y: 2.11, w: 1.81, h: 0.24, fontFace: 'Arial', fontSize: 10, bold: true, color: C.blue, charSpacing: 0.8, align: 'center', margin: 0 })
    slide.addText(j[1], { x: x + 0.24, y: 2.55, w: 1.77, h: 0.52, fontFace: 'Arial', fontSize: 8.5, color: C.slate, align: 'center', valign: 'mid', margin: 0, breakLine: false, fit: 'shrink' })
    addArrow(slide, x + 2.33, 2.65, 0.2, '94A3B8')
  })
  addCard(slide, 11.25, 1.78, 1.35, 1.73, { fill: C.greenBg, line: '86EFAC' })
  slide.addShape(S.ellipse, { x: 11.67, y: 2.03, w: 0.52, h: 0.52, fill: { color: C.green }, line: { color: C.green } })
  slide.addText('✓', { x: 11.67, y: 2.14, w: 0.52, h: 0.22, fontFace: 'Arial', fontSize: 12, bold: true, color: C.paper, align: 'center', margin: 0 })
  addBilingual(slide, '统一门禁', 'QUALITY GATE', 11.45, 2.72, 0.95, 0.52, { cnSize: 11.5, enSize: 7, color: C.green, enColor: C.green, align: 'center' })

  slide.addText('COVERAGE BY RISK', { x: 0.78, y: 4.1, w: 2.8, h: 0.25, fontFace: 'Arial', fontSize: 9, bold: true, color: C.blue, charSpacing: 1.2, margin: 0 })
  const coverage = [
    ['领域规则', 'Domain rules', 'Trade / Position / P&L'],
    ['架构边界', 'Architecture', 'ArchUnit dependency checks'],
    ['真实依赖', 'Real dependencies', 'MySQL 8.4 + Redis 7.4'],
    ['用户旅程', 'User journey', 'Booking → outage → recovery'],
  ]
  coverage.forEach((c, i) => {
    const y = 4.55 + i * 0.57
    slide.addShape(S.ellipse, { x: 0.82, y: y + 0.06, w: 0.24, h: 0.24, fill: { color: i < 2 ? C.blue : C.green }, line: { color: i < 2 ? C.blue : C.green } })
    addBilingual(slide, c[0], c[1], 1.22, y - 0.03, 2.28, 0.38, { cnSize: 10.5, enSize: 7.2 })
    slide.addText(c[2], { x: 3.62, y: y + 0.02, w: 4.55, h: 0.25, fontFace: 'Arial', fontSize: 8.5, color: C.muted, margin: 0 })
  })
  addCard(slide, 8.4, 4.32, 4.15, 2.17, { fill: C.navy, line: C.navy })
  addBilingual(slide, '每个失败都留下可诊断证据', 'Every failure produces useful diagnostics', 8.78, 4.67, 3.4, 0.7, { cnSize: 16, enSize: 9, color: C.paper, enColor: 'BFDBFE', align: 'center' })
  slide.addText('Reports · traces · screenshots · videos · isolated Compose logs', { x: 8.86, y: 5.67, w: 3.24, h: 0.35, fontFace: 'Arial', fontSize: 8.5, color: '94A3B8', align: 'center', margin: 0, fit: 'shrink' })
  addNotes(slide, 10)
}

// 11 — Roadmap
{
  const slide = pptx.addSlide('CONTENT')
  addTitle(slide, 11, '已知边界与演进路线', 'Boundaries and Roadmap', 'WHAT COMES NEXT')
  slide.addText(englishOnly ? 'CURRENT BOUNDARIES' : '当前边界 / CURRENT BOUNDARIES', { x: 0.78, y: 1.65, w: 4.7, h: 0.3, fontFace: 'Arial', fontSize: 10, bold: true, color: C.amber, margin: 0 })
  const limits = [
    ['单用户', 'Single user'], ['仅 USD', 'USD only'], ['无现金账本', 'No cash ledger'],
    ['禁止做空', 'No shorting'], ['仅未实现盈亏', 'Unrealized P&L only'], ['仅加权平均成本', 'Weighted average only'],
  ]
  limits.forEach((l, i) => {
    const x = 0.78 + (i % 2) * 2.35
    const y = 2.1 + Math.floor(i / 2) * 1.05
    addCard(slide, x, y, 2.08, 0.78, { fill: 'FFFBEB', line: 'FDE68A', shadow: false })
    addBilingual(slide, l[0], l[1], x + 0.17, y + 0.1, 1.74, 0.54, { cnSize: 11.5, enSize: 7.5, color: C.amber, enColor: '92400E', align: 'center' })
  })
  slide.addShape(S.line, { x: 5.72, y: 1.72, w: 0, h: 4.78, line: { color: C.line, width: 1.2 } })
  slide.addText(englishOnly ? 'ROADMAP' : '演进路线 / ROADMAP', { x: 6.15, y: 1.65, w: 5.9, h: 0.3, fontFace: 'Arial', fontSize: 10, bold: true, color: C.blue, margin: 0 })
  const roadmap = [
    ['01', '身份与归属', 'Auth · authorization · ownership'],
    ['02', '完整会计能力', 'Cash · FX · realized P&L · tax lots'],
    ['03', '实时数据能力', 'Providers · WebSocket · historical candles'],
    ['04', '生产运行能力', 'Secrets · observability · deployment'],
  ]
  roadmap.forEach((r, i) => {
    const y = 2.12 + i * 1.03
    slide.addShape(S.ellipse, { x: 6.18, y, w: 0.55, h: 0.55, fill: { color: i === 0 ? C.blue : C.sky }, line: { color: i === 0 ? C.blue : C.sky } })
    slide.addText(r[0], { x: 6.18, y: y + 0.13, w: 0.55, h: 0.2, fontFace: 'Arial', fontSize: 9, bold: true, color: i === 0 ? C.paper : C.blue2, align: 'center', margin: 0 })
    addBilingual(slide, r[1], r[2], 7.0, y - 0.05, 5.35, 0.66, { cnSize: 13, enSize: 8.5 })
    if (i < roadmap.length - 1) slide.addShape(S.line, { x: 6.455, y: y + 0.56, w: 0, h: 0.47, line: { color: '93C5FD', width: 2 } })
  })
  slide.addShape(S.roundRect, { x: 1.25, y: 5.74, w: 10.85, h: 0.78, rectRadius: 0.08, fill: { color: C.pale }, line: { color: 'BFDBFE' } })
  addBilingual(slide, '清晰边界不是缺点：它让下一步扩展有可验证的起点', 'Explicit boundaries create a testable starting point for the next increment.', 1.55, 5.9, 10.25, 0.45, { cnSize: 14, enSize: 8.5, color: C.blue2, enColor: C.slate, align: 'center' })
  addNotes(slide, 11)
}

// 12 — Closing
{
  const slide = pptx.addSlide()
  slide.background = { color: C.navy }
  slide.addShape(S.rect, { x: 0, y: 0, w: 13.333, h: 7.5, fill: { color: C.navy }, line: { color: C.navy } })
  slide.addShape(S.ellipse, { x: 9.7, y: -1.5, w: 5.2, h: 5.2, fill: { color: C.blue, transparency: 72 }, line: { color: C.blue, transparency: 100 } })
  slide.addText('TAKEAWAY', { x: 0.78, y: 0.65, w: 2.2, h: 0.25, fontFace: 'Arial', fontSize: 9, bold: true, color: '93C5FD', charSpacing: 1.8, margin: 0 })
  slide.addText(englishOnly ? 'Trade facts → Trusted valuation' : '交易事实 → 可信估值', { x: 0.78, y: 1.18, w: 6.7, h: 0.58, fontFace: englishOnly ? 'Arial' : undefined, fontSize: 28, bold: true, color: C.paper, margin: 0 })
  if (!englishOnly) slide.addText('Trade facts → Trusted valuation', { x: 0.78, y: 1.88, w: 6.7, h: 0.42, fontFace: 'Arial', fontSize: 17, bold: true, color: 'BFDBFE', margin: 0 })
  const values = [
    ['可审计', 'AUDITABLE'], ['可降级', 'RESILIENT'], ['可验证', 'VERIFIABLE'],
  ]
  values.forEach((v, i) => {
    const x = 0.78 + i * 2.35
    slide.addShape(S.roundRect, { x, y: 2.82, w: 2.05, h: 1.03, rectRadius: 0.08, fill: { color: i === 1 ? '1D4ED8' : '1E293B' }, line: { color: i === 1 ? '3B82F6' : '334155' } })
    addBilingual(slide, v[0], v[1], x + 0.18, 3.02, 1.69, 0.55, { cnSize: 15, enSize: 8, color: C.paper, enColor: '93C5FD', align: 'center' })
  })
  slide.addText('LIVE DEMO PATH', { x: 0.82, y: 4.52, w: 2.6, h: 0.25, fontFace: 'Arial', fontSize: 9, bold: true, color: '93C5FD', charSpacing: 1.4, margin: 0 })
  const demo = ['Accounts', 'Activity', 'Dashboard', 'Outage', 'STALE']
  demo.forEach((d, i) => {
    const x = 0.82 + i * 1.43
    addTag(slide, d.toUpperCase(), x, 5.04, 1.12, i === 3 ? '78350F' : i === 4 ? '7F1D1D' : '1E3A8A', i === 3 ? 'FEF3C7' : i === 4 ? 'FEE2E2' : 'DBEAFE')
    if (i < demo.length - 1) addArrow(slide, x + 1.17, 5.2, 0.2, '64748B')
  })
  addCard(slide, 8.25, 2.04, 4.15, 3.9, { fill: '111C32', line: '334155', shadow: false })
  addBilingual(slide, '谢谢', 'THANK YOU', 8.78, 2.65, 3.1, 0.85, { cnSize: 30, enSize: 13, color: C.paper, enColor: '93C5FD', align: 'center' })
  slide.addShape(S.line, { x: 8.85, y: 3.82, w: 2.95, h: 0, line: { color: '334155', width: 1 } })
  addBilingual(slide, '欢迎提问', 'Questions & discussion', 8.78, 4.25, 3.1, 0.72, { cnSize: 18, enSize: 10, color: C.paper, enColor: '94A3B8', align: 'center' })
  slide.addText('Group 5 · Give me five', { x: 8.78, y: 5.34, w: 3.1, h: 0.24, fontFace: 'Arial', fontSize: 8.5, color: '64748B', align: 'center', margin: 0 })
  addNotes(slide, 12)
}

pptx.writeFile({
  fileName: path.join(__dirname, englishOnly ? 'Equity_Trade_Booking_Engine_English.pptx' : 'Equity_Trade_Booking_Engine_Bilingual.pptx'),
  compression: true,
}).catch((error) => {
  console.error(error)
  process.exitCode = 1
})
