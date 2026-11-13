/* Original vector studies. Run with Node.js and sharp on NODE_PATH. */
const fs = require('node:fs');
const path = require('node:path');
const sharp = require('sharp');
const ROOT = __dirname;

const concepts = [
  {
    id: '01-huiwen', name: '洄温', roman: 'HUIWEN', phonetic: 'huí wēn',
    tag: '回旋水纹 · 深青与杏金', bg: '#153F3E', ink: '#F6F0DF', accent: '#E8AF72',
    idea: '把回旋的水流收成一笔，暖色圆点像留在水中的温度。',
    naming: '“洄”是水流回旋，“温”点出热水；也借“回温”的读音，表达忙碌后回到舒适。',
    positioning: '偏艺术、安静而有辨识度；推荐作为主品牌方向。',
    mark: '<path d="M354 181C330 150 298 134 261 134C192 134 139 188 139 257C139 328 190 379 260 379C321 379 368 338 368 287C368 249 342 224 310 224C278 224 257 246 260 274C270 254 290 248 306 257C323 266 325 288 316 307C302 334 272 344 244 336C205 325 184 292 190 253C197 210 229 183 269 185C290 186 309 194 324 209Z" fill="#F6F0DF"/><circle cx="355" cy="147" r="17" fill="#E8AF72"/>',
  },
  {
    id: '02-mubai', name: '沐白', roman: 'MUBAI', phonetic: 'mù bái',
    tag: '陶色水滴 · 一道留白', bg: '#F2EBDD', ink: '#253E40', accent: '#B96D4E',
    idea: '用一道有呼吸感的留白切开水滴，让水的清澈与陶器的温润相遇。',
    naming: '“沐”联系沐浴，“白”是留白与清爽；呼应项目把操作变简单的气质。',
    positioning: '清爽、柔和、容易读记；推荐作为易传播的名称方向。',
    mark: '<path d="M256 126C224 169 160 238 160 291C190 271 210 270 235 280C277 298 308 301 352 273C346 221 282 160 256 126Z" fill="#253E40"/><path d="M163 319C192 298 213 298 239 309C281 328 315 324 356 297C352 349 312 386 257 386C212 386 176 361 163 319Z" fill="#B96D4E"/>',
  },
  {
    id: '03-tingxu', name: '汀序', roman: 'TINGXU', phonetic: 'tīng xù',
    tag: '层叠波形 · 清透群青', bg: '#3655D6', ink: '#F5F0DF', accent: '#B8DFEA',
    idea: '三条波形有相同的节奏，用几何秩序表达流动；像水纹，也像折叠的缎带。',
    naming: '“汀”是水边，“序”是有序；适合强调顺手、清晰的校园生活工具。',
    positioning: '更现代、醒目；适合希望保留蓝色科技感的方向。',
    mark: '<g fill="none" stroke="#F5F0DF" stroke-width="32" stroke-linecap="round"><path d="M151 190C186 161 219 161 254 190C289 219 324 219 359 190"/><path d="M151 255C186 226 219 226 254 255C289 284 324 284 359 255"/><path d="M151 320C186 291 219 291 254 320C289 349 324 349 359 320"/></g>',
  },
  {
    id: '04-wenyu', name: '温隅', roman: 'WENYU', phonetic: 'wēn yú',
    tag: '暖阳倒影 · 朱陶与米白', bg: '#B9513D', ink: '#FBEDDA', accent: '#E3AD82',
    idea: '把一轮暖阳和水面倒影压缩成两个色块，保留版画般的安静与温度。',
    naming: '“温隅”是日常里温暖的一角；比功能型名称更有陪伴感。',
    positioning: '温暖、有生活气息；适合向校园日常服务延展。',
    mark: '<path d="M145 246C145 184 195 137 256 137C317 137 367 184 367 246C302 270 216 220 145 246Z" fill="#FBEDDA"/><path d="M145 281C213 255 298 310 367 282L367 317C298 345 213 290 145 317Z" fill="#FBEDDA"/>',
  },
];

function svg(c, { transparent = false, mono = false, rounded = false } = {}) {
  const mark = mono ? c.mark.replace(/#[0-9a-fA-F]{6}/g, '#243C39') : c.mark;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 512 512"><title>${c.name} · 原创图标概念</title>${!transparent ? `<rect width="512" height="512"${rounded ? ' rx="112"' : ''} fill="${c.bg}"/>` : ''}${mark}</svg>\n`;
}

async function main() {
  for (const c of concepts) {
    const full = svg(c);
    fs.writeFileSync(path.join(ROOT, c.id + '.svg'), full);
    fs.writeFileSync(path.join(ROOT, c.id + '-foreground.svg'), svg(c, { transparent: true }));
    fs.writeFileSync(path.join(ROOT, c.id + '-monochrome.svg'), svg(c, { transparent: true, mono: true }));
    await sharp(Buffer.from(full)).png().toFile(path.join(ROOT, c.id + '.png'));
    await sharp(Buffer.from(svg(c, { rounded: true }))).png().toFile(path.join(ROOT, c.id + '-preview.png'));
    await sharp(Buffer.from(svg(c, { transparent: true, mono: true }))).png().toFile(path.join(ROOT, c.id + '-monochrome.png'));
    for (const size of [512, 64, 48, 32]) {
      await sharp(Buffer.from(svg(c, { rounded: true }))).resize(size, size).png().toFile(path.join(ROOT, `${c.id}-${size}.png`));
    }
  }
  const metadata = concepts.map(({ mark, ...c }) => c);
  fs.writeFileSync(path.join(ROOT, 'concepts.json'), JSON.stringify(metadata, null, 2) + '\n');
  fs.writeFileSync(path.join(ROOT, 'index.html'), html(metadata));
  fs.writeFileSync(path.join(ROOT, 'README.md'), readme(metadata));
  console.log(JSON.stringify({ concepts: metadata.map(c => c.name), output: ROOT, png: '1024×1024, 512, 64, 48, 32', svg: 'full-bleed + transparent foreground + monochrome' }));
}

function html(data) {
  return `<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>水与温度 · 图标与命名提案</title>
<style>
:root{--paper:#F5F2EC;--ink:#243C39;--muted:#6D7972;--line:#DCDDD2}*{box-sizing:border-box}body{margin:0;background:var(--paper);color:var(--ink);font-family:"Microsoft YaHei","PingFang SC",sans-serif}main{max-width:1600px;margin:auto;padding:52px 48px 40px}.eyebrow{font:12px Georgia,serif;letter-spacing:3px;color:var(--muted)}header{display:flex;align-items:end;justify-content:space-between;margin:18px 0 40px;gap:24px}h1{font-family:"STSong","SimSun",serif;font-weight:400;font-size:48px;letter-spacing:5px;margin:0 0 14px}header p{margin:0;line-height:1.8;color:var(--muted);font-size:14px}.round{font:12px Georgia,serif;color:var(--muted);white-space:nowrap;margin-bottom:6px}.grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:22px}.card{padding:22px;background:#FEFCF8;border:1px solid var(--line);border-radius:3px;transition:border-color .2s,transform .2s}.card:hover{border-color:#9BAAA1;transform:translateY(-3px)}.card-top{display:flex;justify-content:space-between;align-items:center;font-size:11px;color:var(--muted);letter-spacing:1px}.num{font:18px Georgia,serif}.stage{display:grid;place-items:center;padding:30px 6px}.hero-icon{width:100%;max-width:230px;height:auto;display:block}.name-row{display:flex;justify-content:space-between;align-items:baseline;margin:6px 0 6px}h2{font-size:29px;font-weight:500;letter-spacing:4px;margin:0}.roman{font:10px Georgia,serif;letter-spacing:1.5px;color:var(--muted)}.tag{font-size:12px;color:var(--muted);margin:12px 0}.desc{min-height:72px;font-size:12px;line-height:1.9;margin-bottom:20px}.sizes{display:flex;gap:15px;align-items:center;padding:18px 0;border-top:1px solid var(--line);border-bottom:1px solid var(--line)}.size{display:flex;flex-direction:column;align-items:center;gap:9px;color:var(--muted);font:10px Georgia,serif}.mono{width:48px;height:48px;background:#E9E6DE;border-radius:11px}.mono img{width:48px;height:48px}.links{display:flex;gap:18px;padding-top:18px}a{color:var(--ink);font-size:12px;text-decoration:none;border-bottom:1px solid #B7BEB3;padding-bottom:3px}a:hover{border-color:var(--ink)}.footer{border-top:1px solid var(--line);margin-top:36px;padding-top:22px;display:flex;justify-content:space-between;gap:28px;font-size:12px;color:var(--muted);line-height:1.9}.recommend{font-weight:500;color:var(--ink)}.more{margin-top:20px;font-size:12px;color:var(--muted);line-height:2}.note{font-size:11px;color:var(--muted);margin-top:20px}@media(max-width:1050px){.grid{grid-template-columns:repeat(2,minmax(0,1fr))}.desc{min-height:48px}}@media(max-width:550px){main{padding:30px 22px}header{display:block}h1{font-size:36px}.round{margin-top:20px}.grid{grid-template-columns:1fr}.hero-icon{max-width:240px}.footer{display:block}.footer p{margin-top:0}}
</style></head><body><main>
<div class="eyebrow">WATER / WARMTH / EVERYDAY</div>
<header><div><h1>水与温度之间</h1><p>为校园热水的日常，留下一个简洁而有艺术感的符号。</p></div><div class="round">ICON & NAMING STUDIES &nbsp; / &nbsp; 02</div></header>
<section class="grid" aria-label="四组应用图标方案">${data.map((c, i) => `<article class="card"><div class="card-top"><span class="num">0${i + 1}</span><span>${c.phonetic}</span></div><div class="stage"><img class="hero-icon" src="${c.id}-preview.png" width="1024" height="1024" alt="${c.name}应用图标：${c.tag}"></div><div class="name-row"><h2>${c.name}</h2><span class="roman">${c.roman}</span></div><p class="tag">${c.tag}</p><p class="desc">${c.idea}</p><div class="sizes"><div class="size"><img src="${c.id}-64.png" width="64" height="64" alt="64像素图标"><span>64 PX</span></div><div class="size"><img src="${c.id}-48.png" width="48" height="48" alt="48像素图标"><span>48 PX</span></div><div class="size"><img src="${c.id}-32.png" width="32" height="32" alt="32像素图标"><span>32 PX</span></div><div class="size"><div class="mono"><img src="${c.id}-monochrome.png" alt="单色图标"></div><span>MONO</span></div></div><div class="links"><a href="${c.id}.png" download>1024 PNG ↗</a><a href="${c.id}.svg" download>矢量 SVG ↗</a></div></article>`).join('')}</section>
<div class="footer"><div><span class="recommend">偏艺术感，推荐「洄温」；偏清爽易记，推荐「沐白」。</span><br>名称与图标可自由搭配。图标不嵌入文字，便于跨平台使用。</div><div>图形采用原创矢量路径绘制。<br>已提供独立背景、透明前景与单色版本。</div></div>
<div class="more">更多名称：一沐 · 沐序 · 涟间 · 暖汀 · 澄一 · 沐和</div><div class="note">概念提案 · 尚未替换项目中的应用名称或图标。名称未进行商标或应用商店重名检索。</div>
</main></body></html>`;
}

function readme(data) {
  return `# 水与温度之间 · 图标与命名提案（第二轮）

四组原创矢量图标，围绕校园热水客户端的水流、温度与轻量日常。每组提供可编辑 SVG、1024×1024 PNG、圆角预览、透明前景、单色版与 512 / 64 / 48 / 32 像素预览。图标与名称可自由搭配。

![图标与名称总览](icon-studies.png)

本地打开 [index.html](index.html) 可并排比较，也可打开 [小尺寸与遮罩检查图](mask-and-size-check.png)。

${data.map((c, i) => `## 0${i + 1} · ${c.name} / ${c.roman}

${c.idea}

名称：${c.naming}

取向：${c.positioning}

配色：背景 \`${c.bg}\`、主色 \`${c.ink}\`。

[PNG 原图](${c.id}.png) · [完整 SVG](${c.id}.svg) · [透明前景](${c.id}-foreground.svg) · [单色 SVG](${c.id}-monochrome.svg)
`).join('\n')}
## 更多名称

| 名称 | 意象与适用方向 |
| --- | --- |
| 一沐 | 一次轻松的沐浴；短、直接、易记。 |
| 沐序 | 把校园用水安排得有序；偏工具气质。 |
| 涟间 | 水纹之间、日常片刻；偏文艺。 |
| 暖汀 | 水边的一点温暖；偏亲和。 |
| 澄一 | 清澈、专注、简单；偏克制。 |
| 沐和 | 沐浴与平和；偏温柔、舒适。 |

## 交付与适配说明

- 完整 SVG 和不带后缀的 PNG 使用不透明满版方形背景；\`-preview.png\` 是圆角演示，不应再用于叠加系统遮罩。
- \`-foreground.svg\` 为透明前景，\`-monochrome.svg\` / PNG 为真实单色前景。此处仅做概念层面的圆形与圆角遮罩检查，未写入正式平台资源；正式接入时需按各平台安全区校准前景尺寸。
- 应用名称与图标均为提案，未更改 Android / HarmonyOS 资源、包名、版本或应用代码。
- 名称未做商标、域名、应用商店重名检索；读音以普通话为准，其中“洄”读 huí，“汀”读 tīng。
- 重建：安装 \`sharp\` 后运行 \`node build-concepts.cjs\`；安装 Pillow 后运行 \`python make-board.py\`。
`;
}

main().catch(error => { console.error(error); process.exitCode = 1; });
