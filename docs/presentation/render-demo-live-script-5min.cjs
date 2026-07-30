const path = require('path')

const directory = __dirname
process.env.DEMO_GUIDE_SOURCE = path.join(
  directory,
  'demo-live-script-5min.md',
)
process.env.DEMO_GUIDE_OUTPUT = path.join(
  directory,
  'demo-live-script-5min.pdf',
)
process.env.DEMO_GUIDE_TITLE = 'TradeFlow 5-Minute Demo Script'

require('./render-demo-click-guide.cjs')
