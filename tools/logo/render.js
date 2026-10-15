const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const b = await chromium.launch({executablePath: '/opt/pw-browsers/chromium'});
  const p = await b.newPage();
  for (const [svg, out, w, h, bg] of JSON.parse(process.argv[2])) {
    const s = fs.readFileSync(svg, 'utf8');
    await p.setViewportSize({ width: w, height: h });
    await p.setContent(`<html><body style="margin:0;background:${bg}">${s.replace(/width="[^"]*" height="[^"]*"/, `width="${w}" height="${h}"`)}</body></html>`);
    await p.screenshot({ path: out, omitBackground: bg === 'transparent' });
  }
  await b.close();
})();
