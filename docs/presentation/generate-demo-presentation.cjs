const path = require('path')
const PptxGenJS = require('/tmp/deskflow-ppt-deps/node_modules/pptxgenjs')

const pptx = new PptxGenJS()
pptx.layout = 'LAYOUT_WIDE'
pptx.author = 'Group 5 — Give me five'
pptx.company = 'Equity Trade Booking Engine'
pptx.subject = 'Current product capabilities and live demo'
pptx.title = 'Equity Trade Booking Engine'
pptx.lang = 'en-US'
pptx.theme = {
  headFontFace: 'Arial',
  bodyFontFace: 'Arial',
  lang: 'en-US',
}

const S = pptx.ShapeType
const C = {
  navy: '0F172A',
  blue: '2563EB',
  blue2: '1D4ED8',
  sky: 'DBEAFE',
  pale: 'EFF6FF',
  bg: 'F3F7FC',
  paper: 'FFFFFF',
  text: '0F172A',
  muted: '64748B',
  line: 'CBD5E1',
  green: '15803D',
  greenBg: 'DCFCE7',
  red: 'DC2626',
  redBg: 'FEE2E2',
  amber: 'D97706',
  amberBg: 'FEF3C7',
  violet: '7C3AED',
  violetBg: 'EDE9FE',
}
const assets = path.join(__dirname, 'assets')
const output = path.join(__dirname, 'Equity_Trade_Booking_Engine_English.pptx')

pptx.defineSlideMaster({
  title: 'LIGHT',
  background: { color: C.bg },
  objects: [
    {
      rect: {
        x: 0, y: 0, w: 0.12, h: 7.5,
        fill: { color: C.blue },
        line: { color: C.blue },
      },
    },
    {
      text: {
        text: 'EQUITY TRADE BOOKING ENGINE',
        options: {
          x: 0.55, y: 7.1, w: 4.4, h: 0.18,
          fontFace: 'Arial', fontSize: 7.5, bold: true,
          color: C.muted, charSpacing: 1.25, margin: 0,
        },
      },
    },
  ],
  slideNumber: {
    x: 12.28, y: 7.06, w: 0.42, h: 0.2,
    fontFace: 'Arial', fontSize: 8, bold: true,
    color: C.muted, align: 'right', margin: 0,
  },
})

function addTitle(slide, number, kicker, title) {
  slide.addText(kicker.toUpperCase(), {
    x: 0.65, y: 0.35, w: 4.5, h: 0.2,
    fontFace: 'Arial', fontSize: 9.5, bold: true,
    color: C.blue, charSpacing: 1.65, margin: 0,
  })
  slide.addText(title, {
    x: 0.65, y: 0.68, w: 11.1, h: 0.62,
    fontFace: 'Arial', fontSize: 29, bold: true,
    color: C.text, margin: 0, breakLine: false, fit: 'shrink',
  })
  slide.addText(String(number).padStart(2, '0'), {
    x: 11.82, y: 0.38, w: 0.82, h: 0.34,
    fontFace: 'Arial', fontSize: 18, bold: true,
    color: 'BFDBFE', align: 'right', margin: 0,
  })
}

function addCard(slide, x, y, w, h, options = {}) {
  slide.addShape(S.roundRect, {
    x, y, w, h,
    rectRadius: 0.08,
    fill: { color: options.fill || C.paper },
    line: { color: options.line || 'DCE5F1', width: options.lineWidth || 0.8 },
    shadow: options.shadow === false
      ? undefined
      : {
          type: 'outer', color: '94A3B8', opacity: 0.13,
          blur: 1.5, angle: 45, distance: 1,
        },
  })
}

function addTag(slide, text, x, y, w, fill = C.sky, color = C.blue2) {
  slide.addShape(S.roundRect, {
    x, y, w, h: 0.34,
    rectRadius: 0.07,
    fill: { color: fill },
    line: { color: fill },
  })
  slide.addText(text, {
    x, y: y + 0.045, w, h: 0.18,
    fontFace: 'Arial', fontSize: 9, bold: true,
    color, align: 'center', margin: 0, fit: 'shrink',
  })
}

function addImage(slide, filename, x, y, w, h, alt) {
  slide.addShape(S.roundRect, {
    x: x - 0.05, y: y - 0.05, w: w + 0.1, h: h + 0.1,
    rectRadius: 0.08,
    fill: { color: C.paper },
    line: { color: C.line, width: 0.8 },
    shadow: {
      type: 'outer', color: '64748B', opacity: 0.22,
      blur: 2, angle: 45, distance: 1.4,
    },
  })
  slide.addImage({
    path: path.join(assets, filename),
    x, y, w, h,
    altText: alt,
  })
}

function addStep(slide, number, title, detail, x, y, w, options = {}) {
  addCard(slide, x, y, w, options.h || 0.92, {
    fill: options.fill || C.paper,
    line: options.line || 'DCE5F1',
    shadow: false,
  })
  slide.addShape(S.ellipse, {
    x: x + 0.16, y: y + 0.2, w: 0.5, h: 0.5,
    fill: { color: options.numberFill || C.blue },
    line: { color: options.numberFill || C.blue },
  })
  slide.addText(String(number), {
    x: x + 0.16, y: y + 0.335, w: 0.5, h: 0.16,
    fontFace: 'Arial', fontSize: 11, bold: true,
    color: C.paper, align: 'center', margin: 0,
  })
  slide.addText(title, {
    x: x + 0.82, y: y + 0.15, w: w - 1.0, h: 0.28,
    fontFace: 'Arial', fontSize: options.titleSize || 17, bold: true,
    color: C.text, margin: 0, fit: 'shrink',
  })
  if (detail) {
    slide.addText(detail, {
      x: x + 0.82, y: y + 0.49, w: w - 1.0, h: 0.24,
      fontFace: 'Arial', fontSize: options.detailSize || 13.5,
      color: C.muted, margin: 0, fit: 'shrink',
    })
  }
}

function addMetric(slide, x, y, w, label, value, color = C.text) {
  addCard(slide, x, y, w, 1.02)
  slide.addText(label.toUpperCase(), {
    x: x + 0.2, y: y + 0.16, w: w - 0.4, h: 0.18,
    fontFace: 'Arial', fontSize: 9, bold: true,
    color: C.muted, charSpacing: 0.8, margin: 0,
  })
  slide.addText(value, {
    x: x + 0.2, y: y + 0.47, w: w - 0.4, h: 0.36,
    fontFace: 'Arial', fontSize: 22, bold: true,
    color, margin: 0, fit: 'shrink',
  })
}

function addNotes(slide, english, chinese, time) {
  slide.addNotes(
    `Suggested time: ${time}. Choose one language.\n\n` +
    `ENGLISH\n${english}\n\n中文\n${chinese}`,
  )
}

// 01 — Cover
{
  const slide = pptx.addSlide()
  slide.background = { color: C.bg }
  slide.addShape(S.rect, {
    x: 0, y: 0, w: 0.16, h: 7.5,
    fill: { color: C.blue }, line: { color: C.blue },
  })
  slide.addShape(S.ellipse, {
    x: 9.5, y: -1.7, w: 5.2, h: 5.2,
    fill: { color: C.sky, transparency: 5 },
    line: { color: C.sky, transparency: 100 },
  })
  slide.addText('GROUP 5  /  GIVE ME FIVE', {
    x: 0.75, y: 0.6, w: 4.5, h: 0.22,
    fontFace: 'Arial', fontSize: 10, bold: true,
    color: C.blue, charSpacing: 1.7, margin: 0,
  })
  slide.addText('Equity Trade\nBooking Engine', {
    x: 0.75, y: 1.22, w: 4.4, h: 1.55,
    fontFace: 'Arial', fontSize: 38, bold: true,
    color: C.navy, margin: 0, breakLine: false, fit: 'shrink',
  })
  slide.addText('Trade facts → trusted valuation', {
    x: 0.77, y: 3.15, w: 4.35, h: 0.42,
    fontFace: 'Arial', fontSize: 19, bold: true,
    color: C.blue2, margin: 0,
  })
  slide.addText('Current product capabilities · live demo', {
    x: 0.77, y: 3.76, w: 4.5, h: 0.3,
    fontFace: 'Arial', fontSize: 15.5,
    color: C.muted, margin: 0,
  })
  addTag(slide, 'AUDITABLE', 0.77, 4.55, 1.25)
  addTag(slide, 'REPLAYABLE', 2.15, 4.55, 1.35)
  addTag(slide, 'RESILIENT', 3.63, 4.55, 1.25)
  addImage(
    slide, 'dashboard.png',
    5.45, 1.28, 7.15, 4.47,
    'Portfolio dashboard showing positions and unrealized profit and loss',
  )
  slide.addText('JAVA 21  ·  SPRING BOOT  ·  REACT 19  ·  MYSQL  ·  REDIS', {
    x: 5.55, y: 6.16, w: 6.95, h: 0.25,
    fontFace: 'Arial', fontSize: 10.5, bold: true,
    color: C.muted, align: 'center', charSpacing: 0.55, margin: 0,
  })
  addNotes(
    slide,
    'Hello, we are Group 5. This presentation follows the current TradeFlow product from trade booking to auditable positions, market valuation, and resilient quote handling.',
    '大家好，我们是第五组。这份演示会基于当前 TradeFlow 产品，从交易登记开始，展示可审计持仓、市场估值和具有韧性的行情处理。',
    '00:15',
  )
}

// 02 — Why it matters
{
  const slide = pptx.addSlide('LIGHT')
  addTitle(slide, 2, 'Why this system', 'Three promises behind every number')
  const cards = [
    ['01', 'AUDITABLE', 'Changes never erase history', C.blue, C.pale],
    ['02', 'REPLAYABLE', 'Positions come from BOOKED trades', C.green, C.greenBg],
    ['03', 'EXPLAINABLE', 'Quote source and age stay visible', C.amber, C.amberBg],
  ]
  cards.forEach((card, index) => {
    const x = 0.72 + index * 4.12
    addCard(slide, x, 1.75, 3.72, 3.7, {
      fill: C.paper,
      line: index === 0 ? 'BFDBFE' : index === 1 ? 'BBF7D0' : 'FDE68A',
    })
    slide.addShape(S.ellipse, {
      x: x + 0.32, y: 2.08, w: 0.7, h: 0.7,
      fill: { color: card[4] }, line: { color: card[4] },
    })
    slide.addText(card[0], {
      x: x + 0.32, y: 2.29, w: 0.7, h: 0.18,
      fontFace: 'Arial', fontSize: 12, bold: true,
      color: card[3], align: 'center', margin: 0,
    })
    slide.addText(card[1], {
      x: x + 0.32, y: 3.02, w: 3.08, h: 0.45,
      fontFace: 'Arial', fontSize: 22, bold: true,
      color: C.text, margin: 0, fit: 'shrink',
    })
    slide.addText(card[2], {
      x: x + 0.32, y: 3.83, w: 3.08, h: 0.78,
      fontFace: 'Arial', fontSize: 17, bold: true,
      color: C.muted, margin: 0, breakLine: false, fit: 'shrink',
    })
  })
  slide.addShape(S.roundRect, {
    x: 1.38, y: 5.9, w: 10.55, h: 0.62,
    rectRadius: 0.08,
    fill: { color: C.navy },
    line: { color: C.navy },
  })
  slide.addText('Trades are facts. Positions are replayed. Quotes value.', {
    x: 1.68, y: 6.08, w: 9.95, h: 0.23,
    fontFace: 'Arial', fontSize: 17, bold: true,
    color: C.paper, align: 'center', margin: 0,
  })
  addNotes(
    slide,
    'TradeFlow makes three promises. Changes remain auditable, positions are recalculated from BOOKED trade facts, and every quote exposes its source and freshness. These promises connect the entire demo.',
    'TradeFlow 有三个核心承诺：所有修改都可审计；持仓从 BOOKED 交易事实重新计算；每个行情都明确展示来源和新鲜度。这三个承诺连接了整个演示。',
    '00:15',
  )
}

// 03 — Demo route
{
  const slide = pptx.addSlide('LIGHT')
  addTitle(slide, 3, 'Demo route', 'One business loop, four live steps')
  const steps = [
    ['DASHBOARD', 'Baseline', 'AAPL 8'],
    ['ACTIVITY', 'Book', 'BUY 1'],
    ['POSITIONS', 'Replay', '8 → 9 → 8'],
    ['MARKET DATA', 'Explain', 'MOCK / LIVE / STALE'],
  ]
  steps.forEach((step, index) => {
    const x = 0.62 + index * 3.14
    addCard(slide, x, 1.9, 2.68, 3.42, {
      fill: index === 3 ? C.pale : C.paper,
      line: index === 3 ? '93C5FD' : 'DCE5F1',
    })
    slide.addShape(S.ellipse, {
      x: x + 0.89, y: 2.25, w: 0.9, h: 0.9,
      fill: { color: index === 3 ? C.blue : C.sky },
      line: { color: index === 3 ? C.blue : C.sky },
    })
    slide.addText(String(index + 1).padStart(2, '0'), {
      x: x + 0.89, y: 2.53, w: 0.9, h: 0.2,
      fontFace: 'Arial', fontSize: 14, bold: true,
      color: index === 3 ? C.paper : C.blue2,
      align: 'center', margin: 0,
    })
    slide.addText(step[0], {
      x: x + 0.22, y: 3.48, w: 2.24, h: 0.28,
      fontFace: 'Arial', fontSize: 12, bold: true,
      color: C.blue, align: 'center', charSpacing: 0.7,
      margin: 0, fit: 'shrink',
    })
    slide.addText(step[1], {
      x: x + 0.22, y: 3.92, w: 2.24, h: 0.42,
      fontFace: 'Arial', fontSize: 23, bold: true,
      color: C.text, align: 'center', margin: 0,
    })
    slide.addText(step[2], {
      x: x + 0.22, y: 4.56, w: 2.24, h: 0.3,
      fontFace: 'Arial', fontSize: 15, bold: true,
      color: C.muted, align: 'center', margin: 0, fit: 'shrink',
    })
    if (index < 3) {
      slide.addShape(S.line, {
        x: x + 2.72, y: 3.6, w: 0.38, h: 0,
        line: { color: '93C5FD', width: 2, endArrowType: 'triangle' },
      })
    }
  })
  slide.addText('The next slides follow this exact click path.', {
    x: 3.55, y: 5.95, w: 6.25, h: 0.32,
    fontFace: 'Arial', fontSize: 17, bold: true,
    color: C.blue2, align: 'center', margin: 0,
  })
  addNotes(
    slide,
    'The live path has four steps: establish the Dashboard baseline, book one AAPL share, replay the position through deletion, and finish with explicit market-data status.',
    '现场路径只有四步：先建立仪表盘基线；登记一股 AAPL；通过删除演示持仓重算；最后展示明确的市场数据状态。',
    '00:15',
  )
}

// 04 — Dashboard
{
  const slide = pptx.addSlide('LIGHT')
  addTitle(slide, 4, 'Demo · Dashboard', 'Start with a trusted baseline')
  addMetric(slide, 0.72, 1.63, 1.85, 'AAPL quantity', '8', C.blue2)
  addMetric(slide, 2.75, 1.63, 1.85, 'MSFT quantity', '3', C.blue2)
  addMetric(slide, 0.72, 2.91, 3.88, 'Quote status', 'MOCK · FRESH', C.green)
  addCard(slide, 0.72, 4.23, 3.88, 1.42, {
    fill: C.pale, line: 'BFDBFE',
  })
  slide.addText('ONE GAIN  +  ONE LOSS', {
    x: 0.98, y: 4.58, w: 3.35, h: 0.32,
    fontFace: 'Arial', fontSize: 19, bold: true,
    color: C.blue2, align: 'center', margin: 0,
  })
  slide.addText('Valuation history is persisted locally', {
    x: 0.98, y: 5.08, w: 3.35, h: 0.24,
    fontFace: 'Arial', fontSize: 14.5, bold: true,
    color: C.muted, align: 'center', margin: 0,
  })
  addImage(
    slide, 'dashboard.png',
    4.98, 1.55, 7.62, 4.76,
    'Dashboard baseline for Demo Growth',
  )
  addTag(slide, 'REMEMBER AAPL = 8', 8.35, 6.52, 2.25, C.sky, C.blue2)
  addNotes(
    slide,
    'Select Demo Growth and refresh. The baseline is AAPL quantity 8 and MSFT quantity 3, with both a gain and a loss. The source flags say MOCK and FRESH, and the valuation history contains only persisted local snapshots.',
    '选择 Demo Growth 并刷新。基线是 AAPL 数量 8、MSFT 数量 3，同时包含盈利和亏损示例。行情标签明确显示 MOCK 和 FRESH，估值历史只展示本地持久化快照。',
    '00:20',
  )
}

// 05 — Accounts
{
  const slide = pptx.addSlide('LIGHT')
  addTitle(slide, 5, 'Demo · Accounts', 'Multiple accounts, one clear boundary')
  addImage(
    slide, 'accounts.png',
    0.72, 1.56, 7.45, 4.66,
    'Accounts page with Demo Growth and Demo Income',
  )
  const items = [
    ['MULTI-ACCOUNT', 'Demo Growth + Demo Income', C.blue, C.pale],
    ['ACTIVE / INACTIVE', 'Deactivate without deleting history', C.green, C.greenBg],
    ['USD ONLY', 'Explicit current product boundary', C.amber, C.amberBg],
  ]
  items.forEach((item, index) => {
    const y = 1.62 + index * 1.5
    addCard(slide, 8.52, y, 4.08, 1.18, {
      fill: item[3], line: item[3],
      shadow: false,
    })
    slide.addText(item[0], {
      x: 8.82, y: y + 0.2, w: 3.48, h: 0.22,
      fontFace: 'Arial', fontSize: 12, bold: true,
      color: item[2], charSpacing: 0.8, margin: 0,
    })
    slide.addText(item[1], {
      x: 8.82, y: y + 0.57, w: 3.48, h: 0.3,
      fontFace: 'Arial', fontSize: 16.5, bold: true,
      color: C.text, margin: 0, fit: 'shrink',
    })
  })
  addTag(slide, 'VIEW POSITIONS', 9.72, 6.32, 1.75, C.blue, C.paper)
  addNotes(
    slide,
    'Accounts separate securities activity. Demo Growth and Demo Income each keep their own positions. An account can be deactivated without deleting history, and the current product boundary is USD only.',
    '账户用于隔离证券交易活动。Demo Growth 和 Demo Income 各自维护独立持仓。账户可以停用，但不会删除历史；当前产品边界是仅支持 USD。',
    '00:20',
  )
}

// 06 — Book a trade
{
  const slide = pptx.addSlide('LIGHT')
  addTitle(slide, 6, 'Demo · Activity', 'Book one verified AAPL trade')
  const steps = [
    ['Demo Growth', 'Select account'],
    ['BUY', 'Choose side'],
    ['AAPL', 'Select search result'],
    ['1 × $100', 'Enter quantity and price'],
    ['BOOKED', 'Submit'],
  ]
  steps.forEach((step, index) => {
    addStep(
      slide, index + 1, step[0], step[1],
      0.7, 1.52 + index * 1.02, 4.2,
      {
        fill: index === 4 ? C.greenBg : C.paper,
        line: index === 4 ? '86EFAC' : 'DCE5F1',
        numberFill: index === 4 ? C.green : C.blue,
        titleSize: 18,
        detailSize: 13.5,
      },
    )
  })
  addImage(
    slide, 'activity.png',
    5.25, 1.56, 7.35, 4.59,
    'Trade booking form and activity ledger',
  )
  addTag(slide, 'VERIFIED: AAPL', 7.5, 6.38, 1.65, C.sky, C.blue2)
  addTag(slide, 'NEW BOOKED ROW', 9.35, 6.38, 1.8, C.greenBg, C.green)
  addNotes(
    slide,
    'Open Activity, select Demo Growth and BUY, then type AAPL and choose the search result. The form requires Verified: AAPL, so the ticker is not arbitrary. Enter quantity 1 and price 100, keep the execution time, and book the trade. A new BOOKED row appears.',
    '进入交易活动，选择 Demo Growth 和 BUY，然后输入 AAPL 并选择搜索结果。表单要求显示 Verified: AAPL，所以股票代码不是任意字符串。输入数量 1、价格 100，保留成交时间并提交。账本会新增一条 BOOKED 记录。',
    '00:25',
  )
}

// 07 — Lifecycle
{
  const slide = pptx.addSlide('LIGHT')
  addTitle(slide, 7, 'Trade lifecycle', 'Numbers change. Evidence stays.')
  addCard(slide, 0.72, 1.7, 3.0, 1.25, {
    fill: C.pale, line: '93C5FD',
  })
  slide.addText('BOOKED', {
    x: 1.02, y: 2.03, w: 2.4, h: 0.4,
    fontFace: 'Arial', fontSize: 24, bold: true,
    color: C.blue2, align: 'center', margin: 0,
  })
  slide.addShape(S.line, {
    x: 3.9, y: 2.32, w: 1.1, h: 0,
    line: { color: C.blue, width: 2.5, endArrowType: 'triangle' },
  })
  addCard(slide, 5.2, 1.7, 3.0, 1.25, {
    fill: C.amberBg, line: 'FCD34D',
  })
  slide.addText('CANCELLED', {
    x: 5.5, y: 2.03, w: 2.4, h: 0.4,
    fontFace: 'Arial', fontSize: 24, bold: true,
    color: C.amber, align: 'center', margin: 0,
  })
  addCard(slide, 8.65, 1.7, 3.95, 1.25)
  slide.addText('CANCELLED  ·  DELETED  ·  AMENDED', {
    x: 8.95, y: 2.08, w: 3.35, h: 0.32,
    fontFace: 'Arial', fontSize: 15.5, bold: true,
    color: C.text, align: 'center', margin: 0, fit: 'shrink',
  })
  slide.addText('Audit reason is always explicit', {
    x: 8.95, y: 2.49, w: 3.35, h: 0.23,
    fontFace: 'Arial', fontSize: 13.5, bold: true,
    color: C.muted, align: 'center', margin: 0,
  })
  const values = [
    ['8', 'BASELINE', C.blue2],
    ['9', 'AFTER BUY', C.green],
    ['8', 'AFTER DELETE', C.amber],
  ]
  values.forEach((value, index) => {
    const x = 1.25 + index * 3.75
    addCard(slide, x, 3.65, 3.1, 1.72, {
      fill: index === 1 ? C.greenBg : C.paper,
      line: index === 1 ? '86EFAC' : 'DCE5F1',
    })
    slide.addText(value[0], {
      x, y: 3.92, w: 3.1, h: 0.62,
      fontFace: 'Arial', fontSize: 37, bold: true,
      color: value[2], align: 'center', margin: 0,
    })
    slide.addText(value[1], {
      x: x + 0.25, y: 4.78, w: 2.6, h: 0.23,
      fontFace: 'Arial', fontSize: 11, bold: true,
      color: C.muted, align: 'center', charSpacing: 0.8, margin: 0,
    })
    if (index < 2) {
      slide.addText('→', {
        x: x + 3.15, y: 4.22, w: 0.5, h: 0.28,
        fontFace: 'Arial', fontSize: 20, bold: true,
        color: C.blue, align: 'center', margin: 0,
      })
    }
  })
  slide.addText('Delete recalculates the position — it never erases the record.', {
    x: 2.15, y: 5.96, w: 8.95, h: 0.38,
    fontFace: 'Arial', fontSize: 18, bold: true,
    color: C.blue2, align: 'center', margin: 0,
  })
  addNotes(
    slide,
    'The new BUY moves AAPL from 8 to 9. Delete that row and confirm: the record remains as CANCELLED with reason DELETED, while the position returns to 8. Amendment follows the same audit-preserving idea by creating a linked replacement.',
    '新的 BUY 使 AAPL 从 8 变成 9。删除该记录并确认后，原记录会保留为 CANCELLED，原因是 DELETED，同时持仓恢复为 8。修改交易也采用相同的审计保留思路，通过创建关联的替代交易完成。',
    '00:25',
  )
}

// 08 — Position and P&L
{
  const slide = pptx.addSlide('LIGHT')
  addTitle(slide, 8, 'Position and P&L', 'One calculation model, everywhere')
  const formulas = [
    ['MARKET VALUE', 'quantity × market price'],
    ['UNREALIZED P&L', 'market value − cost basis'],
    ['RETURN', 'P&L ÷ cost basis × 100'],
  ]
  formulas.forEach((formula, index) => {
    const y = 1.55 + index * 1.42
    addCard(slide, 0.72, y, 4.9, 1.12, {
      fill: index === 1 ? C.greenBg : C.paper,
      line: index === 1 ? '86EFAC' : 'DCE5F1',
    })
    slide.addText(formula[0], {
      x: 1.0, y: y + 0.18, w: 1.65, h: 0.22,
      fontFace: 'Arial', fontSize: 11, bold: true,
      color: index === 1 ? C.green : C.blue,
      charSpacing: 0.75, margin: 0,
    })
    slide.addText(formula[1], {
      x: 2.72, y: y + 0.32, w: 2.55, h: 0.3,
      fontFace: 'Arial', fontSize: 17, bold: true,
      color: C.text, align: 'right', margin: 0, fit: 'shrink',
    })
  })
  addCard(slide, 0.72, 5.9, 4.9, 0.58, {
    fill: C.navy, line: C.navy,
  })
  slide.addText('WEIGHTED-AVERAGE COST  ·  DECIMAL ARITHMETIC', {
    x: 0.98, y: 6.08, w: 4.38, h: 0.2,
    fontFace: 'Arial', fontSize: 11, bold: true,
    color: C.paper, align: 'center', charSpacing: 0.55, margin: 0,
  })
  addImage(
    slide, 'dashboard-full.png',
    6.02, 1.55, 6.58, 4.11,
    'Dashboard showing position P&L and valuation history',
  )
  addMetric(slide, 6.02, 5.9, 2.03, 'Quantity', '8')
  addMetric(slide, 8.25, 5.9, 2.03, 'Average cost', '$100')
  addMetric(slide, 10.48, 5.9, 2.12, 'Missing quote', 'NULL', C.amber)
  addNotes(
    slide,
    'Positions use weighted-average cost. Market value, unrealized P&L, and return are calculated by the backend with decimal arithmetic. A missing quote remains null; the interface never substitutes a zero market price.',
    '持仓采用加权平均成本。市场价值、未实现盈亏和收益率都由后端使用十进制运算统一计算。如果行情缺失，值保持为 null，界面不会用零价格代替。',
    '00:20',
  )
}

// 09 — CSV safety
{
  const slide = pptx.addSlide('LIGHT')
  addTitle(slide, 9, 'Bulk booking', 'CSV import with a duplicate safety net')
  const pipeline = [
    ['CSV', 'Validate'],
    ['NORMALISE', 'Table content'],
    ['FINGERPRINT', 'Stable UUID'],
    ['WARN', 'Import again?'],
  ]
  pipeline.forEach((item, index) => {
    const x = 0.72 + index * 3.13
    addCard(slide, x, 1.85, 2.65, 2.12, {
      fill: index === 3 ? C.amberBg : C.paper,
      line: index === 3 ? 'FCD34D' : 'DCE5F1',
    })
    slide.addText(item[0], {
      x: x + 0.22, y: 2.25, w: 2.21, h: 0.38,
      fontFace: 'Arial', fontSize: index === 2 ? 19 : 22,
      bold: true, color: index === 3 ? C.amber : C.blue2,
      align: 'center', margin: 0, fit: 'shrink',
    })
    slide.addText(item[1], {
      x: x + 0.22, y: 3.0, w: 2.21, h: 0.3,
      fontFace: 'Arial', fontSize: 15, bold: true,
      color: C.muted, align: 'center', margin: 0,
    })
    if (index < 3) {
      slide.addShape(S.line, {
        x: x + 2.68, y: 2.9, w: 0.4, h: 0,
        line: { color: C.blue, width: 2, endArrowType: 'triangle' },
      })
    }
  })
  const protections = [
    ['Filename changes', 'do not bypass'],
    ['Row order changes', 'do not bypass'],
    ['1 vs 1.0', 'does not bypass'],
  ]
  protections.forEach((item, index) => {
    const x = 1.0 + index * 4.05
    addCard(slide, x, 4.65, 3.62, 1.05, {
      fill: C.pale, line: 'BFDBFE', shadow: false,
    })
    slide.addText(item[0], {
      x: x + 0.22, y: 4.88, w: 3.18, h: 0.24,
      fontFace: 'Arial', fontSize: 15, bold: true,
      color: C.text, align: 'center', margin: 0,
    })
    slide.addText(item[1], {
      x: x + 0.22, y: 5.25, w: 3.18, h: 0.2,
      fontFace: 'Arial', fontSize: 13, bold: true,
      color: C.blue2, align: 'center', margin: 0,
    })
  })
  slide.addText('The user must explicitly confirm a second full import.', {
    x: 2.25, y: 6.18, w: 8.85, h: 0.32,
    fontFace: 'Arial', fontSize: 17, bold: true,
    color: C.blue2, align: 'center', margin: 0,
  })
  addNotes(
    slide,
    'CSV bulk booking validates every row and registers a stable fingerprint of normalized table content. Renaming the file, reordering rows, or using equivalent number formats does not bypass the duplicate warning. A second full import requires explicit confirmation.',
    'CSV 批量登记会校验每一行，并为规范化后的整张表生成稳定指纹。修改文件名、调整行顺序或使用等价数字格式，都无法绕过重复提醒。再次完整导入必须由用户明确确认。',
    '00:20',
  )
}

// 10 — Market data
{
  const slide = pptx.addSlide('LIGHT')
  addTitle(slide, 10, 'Market data', 'Resilience without hiding the truth')
  addCard(slide, 0.72, 1.58, 4.05, 1.45, {
    fill: C.pale, line: 'BFDBFE',
  })
  slide.addText('DEFAULT', {
    x: 0.98, y: 1.83, w: 1.2, h: 0.2,
    fontFace: 'Arial', fontSize: 10, bold: true,
    color: C.blue, charSpacing: 0.9, margin: 0,
  })
  slide.addText('MOCK', {
    x: 0.98, y: 2.2, w: 1.45, h: 0.4,
    fontFace: 'Arial', fontSize: 26, bold: true,
    color: C.blue2, margin: 0,
  })
  slide.addText('Generated locally\nNever shown as live', {
    x: 2.35, y: 2.02, w: 2.0, h: 0.65,
    fontFace: 'Arial', fontSize: 15.5, bold: true,
    color: C.muted, margin: 0, breakLine: false, fit: 'shrink',
  })
  addCard(slide, 0.72, 3.38, 4.05, 2.35, {
    fill: C.paper, line: 'DCE5F1',
  })
  slide.addText('FINNHUB DEMO', {
    x: 0.98, y: 3.66, w: 1.75, h: 0.2,
    fontFace: 'Arial', fontSize: 10, bold: true,
    color: C.blue, charSpacing: 0.9, margin: 0,
  })
  addTag(slide, 'LIVE', 0.98, 4.14, 0.85, C.greenBg, C.green)
  slide.addText('→', {
    x: 1.97, y: 4.19, w: 0.35, h: 0.2,
    fontFace: 'Arial', fontSize: 16, bold: true,
    color: C.muted, align: 'center', margin: 0,
  })
  addTag(slide, 'CACHED', 2.45, 4.14, 1.0, C.amberBg, C.amber)
  addTag(slide, 'STALE', 3.57, 4.14, 0.9, C.redBg, C.red)
  slide.addText('No cache → clear 503', {
    x: 0.98, y: 4.87, w: 3.45, h: 0.3,
    fontFace: 'Arial', fontSize: 17, bold: true,
    color: C.text, margin: 0,
  })
  slide.addText('No silent Mock fallback. No zero price.', {
    x: 0.98, y: 5.28, w: 3.45, h: 0.24,
    fontFace: 'Arial', fontSize: 14.5, bold: true,
    color: C.muted, margin: 0,
  })
  addImage(
    slide, 'market-data.png',
    5.14, 1.55, 7.48, 4.68,
    'Market Data page with source and cache labels',
  )
  addTag(slide, 'SOURCE', 7.2, 6.47, 1.0)
  addTag(slide, 'CACHE', 8.38, 6.47, 0.95)
  addTag(slide, 'STALE?', 9.51, 6.47, 0.95)
  addTag(slide, 'COMPLETE?', 10.64, 6.47, 1.2)
  addNotes(
    slide,
    'The default provider is clearly labelled MOCK. In Finnhub demo mode, a successful quote is LIVE. During an outage, a retained Redis quote becomes CACHED and STALE. Without cache, the API returns a clear 503. It never silently switches to Mock or invents a zero price.',
    '默认行情源会明确标记为 MOCK。在 Finnhub 演示模式下，成功报价显示为 LIVE。数据源故障时，Redis 中保留的报价会标记为 CACHED 和 STALE。如果没有缓存，接口会返回清晰的 503，绝不会偷偷切换到 Mock 或编造零价格。',
    '00:25',
  )
}

// 11 — Closing
{
  const slide = pptx.addSlide('LIGHT')
  addTitle(slide, 11, 'Takeaway', 'A trusted loop from booking to valuation')
  const outcomes = [
    ['BOOK', 'Verified securities\nand explicit trade facts', C.blue, C.pale],
    ['REPLAY', 'Positions recalculate\nwithout erasing history', C.green, C.greenBg],
    ['VALUE', 'Quotes expose source,\ncache, and staleness', C.amber, C.amberBg],
  ]
  outcomes.forEach((outcome, index) => {
    const x = 0.72 + index * 4.12
    addCard(slide, x, 1.72, 3.72, 3.25, {
      fill: outcome[3], line: outcome[3],
      shadow: false,
    })
    slide.addText(outcome[0], {
      x: x + 0.3, y: 2.12, w: 3.12, h: 0.48,
      fontFace: 'Arial', fontSize: 25, bold: true,
      color: outcome[2], align: 'center', margin: 0,
    })
    slide.addText(outcome[1], {
      x: x + 0.35, y: 3.13, w: 3.02, h: 0.88,
      fontFace: 'Arial', fontSize: 17, bold: true,
      color: C.text, align: 'center', margin: 0,
      breakLine: false, fit: 'shrink',
    })
  })
  slide.addShape(S.roundRect, {
    x: 1.28, y: 5.55, w: 10.78, h: 0.78,
    rectRadius: 0.08,
    fill: { color: C.navy },
    line: { color: C.navy },
  })
  slide.addText('TRADES ARE FACTS.', {
    x: 1.62, y: 5.77, w: 3.2, h: 0.32,
    fontFace: 'Arial', fontSize: 20, bold: true,
    color: C.paper, margin: 0,
  })
  slide.addText('Trust comes from explainable change.', {
    x: 4.65, y: 5.77, w: 6.7, h: 0.32,
    fontFace: 'Arial', fontSize: 20, bold: true,
    color: '93C5FD', align: 'right', margin: 0,
  })
  addTag(slide, 'Q & A', 11.18, 6.62, 0.95, C.blue, C.paper)
  addNotes(
    slide,
    'TradeFlow connects verified booking, replayable positions, audit-preserving corrections, and transparent valuation in one trusted loop. Trades are facts, and trust comes from explainable change. Thank you.',
    'TradeFlow 把已验证交易登记、可重算持仓、保留审计的纠错以及透明估值连接成一个可信闭环。交易是事实，信任来自可解释的变化。谢谢大家。',
    '00:10',
  )
}

pptx.writeFile({ fileName: output, compression: true })
  .then(() => {
    console.log(`Wrote ${output}`)
  })
  .catch((error) => {
    console.error(error)
    process.exitCode = 1
  })
