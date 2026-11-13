/* Editable geometry + vector glass study. PNG finish is rendered by render-glass.py. */
const fs = require('node:fs');
const path = require('node:path');
const sharp = require('sharp');
const ROOT = __dirname;
const concepts = [
  {
    id:'01-qumu', name:'趣沐', roman:'QUMU', phonetic:'qù mù',
    tag:'环形水纹 · 烟青玻璃',
    idea:'沿用现有图标的环与水纹，右下轻轻收出 Q 的尾笔，呼应“趣”的首字母。',
    naming:'“趣”承接趣智校园，“沐”点出沐浴与热水；读起来也像“去沐”，轻巧、好记。',
    position:'首推：兼顾原品牌联系、用水含义和当前图标的视觉延续。',
    top:'#1D3C4B', bottom:'#95B9C6', bg:'#F2F5F6', material:'smoke',
    mark:'<g transform="translate(256 256) scale(.96) translate(-256 -256)"><path fill-rule="evenodd" d="M256 133C324 133 379 188 379 256C379 324 324 379 256 379C188 379 133 324 133 256C133 188 188 133 256 133ZM178 245C178 201 211 171 255 171C301 171 341 207 341 248C341 264 328 274 311 272C284 271 260 259 238 249C211 236 194 235 178 245ZM176 287C179 323 214 347 253 347C295 347 327 326 334 297C310 300 285 297 260 286C230 272 202 270 181 281C177 282 175 285 176 287Z"/><path d="M333 321L363 351C371 359 372 366 365 372C359 378 351 375 346 369L315 338Z"/></g>',
  },
  {
    id:'02-zhimu', name:'智沐', roman:'ZHIMU', phonetic:'zhì mù',
    tag:'留白水滴 · 冰青玻璃',
    idea:'水滴由一条留白水线分成上下两面，用通透的冰青表达清爽与轻盈。',
    naming:'从“趣智”保留“智”，与“沐”组成短名称；更强调智能、顺手的校园用水体验。',
    position:'备选：直接、清楚，适合想保留“智”字关联的方向。',
    top:'#32647B', bottom:'#B5E0E3', bg:'#EFF6F6', material:'aqua',
    mark:'<path d="M256 126C224 169 160 238 160 291C190 271 210 270 235 280C277 298 308 301 352 273C346 221 282 160 256 126Z"/><path d="M163 319C192 298 213 298 239 309C281 328 315 324 356 297C352 349 312 386 257 386C212 386 176 361 163 319Z"/>',
  },
  {
    id:'03-qucheng', name:'趣澄', roman:'QUCHENG', phonetic:'qù chéng',
    tag:'层叠波纹 · 雾蓝玻璃',
    idea:'把应用里的波纹化成三条柔润的玻璃线，保持最简洁的水的记忆。',
    naming:'“趣”保留品牌线索，“澄”表达水的清澈，也呼应界面清楚、操作简单。',
    position:'备选：现代、清爽，适合强调简洁界面的方向。',
    top:'#314F74', bottom:'#9FBED7', bg:'#F0F3F8', material:'blue',
    mark:'<g fill="none" stroke="currentColor" stroke-width="32" stroke-linecap="round"><path d="M151 190C186 161 219 161 254 190C289 219 324 219 359 190"/><path d="M151 255C186 226 219 226 254 255C289 284 324 284 359 255"/><path d="M151 320C186 291 219 291 254 320C289 349 324 349 359 320"/></g>',
  },
  {
    id:'04-quwen', name:'趣温', roman:'QUWEN', phonetic:'qù wēn',
    tag:'暖阳倒影 · 香槟玻璃',
    idea:'日轮和水面只留下两个柔和色块，用低饱和的香槟色留住热水的温度。',
    naming:'“趣”联系趣智校园，“温”是热水与舒适；语气温和，适合日常陪伴。',
    position:'备选：更温暖、有生活感，适合偏暖色的品牌气质。',
    top:'#786052', bottom:'#E4CCAC', bg:'#F7F3ED', material:'warm',
    mark:'<path d="M145 246C145 184 195 137 256 137C317 137 367 184 367 246C302 270 216 220 145 246Z"/><path d="M145 281C213 255 298 310 367 282L367 317C298 345 213 290 145 317Z"/>',
  },
];

function geometry(c, fill='#FFFFFF') {
  const mark=c.mark.replace('stroke="currentColor"',`stroke="${fill}"`);
  return `<svg xmlns="http://www.w3.org/2000/svg" width="1536" height="1536" viewBox="0 0 512 512"><title>${c.name} · 原创矢量几何</title><g fill="${fill}">${mark}</g></svg>`;
}

function glassSvg(c) {
  const wave=c.mark.replace('stroke="currentColor"','stroke="url(#glass)"');
  return `<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 512 512"><title>${c.name} · 玻璃质感矢量概念</title><defs>
  <linearGradient id="bg" x2=".4" y2="1"><stop stop-color="#FFFFFF"/><stop offset="1" stop-color="${c.bg}"/></linearGradient>
  <linearGradient id="glass" x1=".1" y1="0" x2=".85" y2="1"><stop stop-color="${c.top}"/><stop offset=".25" stop-color="${c.top}"/><stop offset=".52" stop-color="${c.bottom}"/><stop offset=".7" stop-color="${c.top}"/><stop offset="1" stop-color="${c.bottom}"/></linearGradient>
  <filter id="glassDepth" x="-30%" y="-30%" width="160%" height="160%" color-interpolation-filters="sRGB"><feGaussianBlur in="SourceAlpha" stdDeviation="3" result="bevel"/><feSpecularLighting in="bevel" surfaceScale="10" specularConstant=".8" specularExponent="28" lighting-color="#FFFFFF" result="shine"><fePointLight x="-90" y="-130" z="250"/></feSpecularLighting><feComposite in="shine" in2="SourceAlpha" operator="in" result="shineClip"/><feComposite in="SourceGraphic" in2="shineClip" operator="arithmetic" k2="1" k3=".8" result="surface"/><feGaussianBlur in="SourceAlpha" stdDeviation="7" result="blur"/><feOffset in="blur" dx="0" dy="10" result="offset"/><feFlood flood-color="#61777B" flood-opacity=".22"/><feComposite in2="offset" operator="in" result="shadow"/><feMerge><feMergeNode in="shadow"/><feMergeNode in="surface"/></feMerge></filter>
  </defs><rect width="512" height="512" fill="url(#bg)"/><g fill="url(#glass)" filter="url(#glassDepth)">${wave}</g></svg>`;
}

async function main(){
  for(const c of concepts){
    fs.writeFileSync(path.join(ROOT,c.id+'-geometry.svg'),geometry(c,'#253D43'));
    fs.writeFileSync(path.join(ROOT,c.id+'-glass.svg'),glassSvg(c));
    await sharp(Buffer.from(geometry(c))).png().toFile(path.join(ROOT,c.id+'-mask.png'));
    await sharp(Buffer.from(geometry(c,'#253D43'))).resize(1024).png().toFile(path.join(ROOT,c.id+'-monochrome.png'));
    await sharp(Buffer.from(glassSvg(c))).png().toFile(path.join(ROOT,c.id+'-vector-preview.png'));
  }
  fs.writeFileSync(path.join(ROOT,'concepts.json'),JSON.stringify(concepts.map(({mark,...c})=>c),null,2)+'\n');
  console.log(JSON.stringify({created:concepts.map(c=>c.name),folder:ROOT}));
}
main().catch(e=>{console.error(e);process.exitCode=1});
