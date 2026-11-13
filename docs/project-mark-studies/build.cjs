/* Rebuild editable geometry, raster materials, gallery and presentation sheets. */
const fs=require('node:fs');
const path=require('node:path');
const {spawnSync}=require('node:child_process');
const ROOT=__dirname;
const PYTHON=process.env.ICON_STUDY_PYTHON||'C:/Users/86158/AppData/Local/Programs/Python/Python312/python.exe';
const concepts=require('./marks.cjs');
function run(bin,args){const r=spawnSync(bin,args,{cwd:ROOT,encoding:'utf8',env:{...process.env,PYTHONUTF8:'1'}});if(r.stdout)process.stdout.write(r.stdout);if(r.status!==0)throw new Error(r.stderr||r.error||'Build failed');}
const sources=[
 ['Craft','https://www.craft.do/es/brand','少量饱满分片，靠边缘与交接建立层次。'],
 ['Arc','https://dockshare.io/apps/arc','字母经过专门重画，交叠成为记忆点。'],
 ['Linear','https://linear.app/brand','统一外轮廓里保留清楚的负形节奏。'],
 ['Things','https://culturedcode.com/things/blog/','一个主视觉承载含义，其余层次支持它。'],
 ['Raycast','https://www.raycast.com/blog/a-fresh-look-and-feel','材质选择与产品的使用方式有关联。'],
 ['Anytype','https://anytype.io/','形状、留白与少量色彩建立独立识别。'],
 ['Structured','https://structured.app/blog/structured-3','功能线索凝练为完整符号，缩小后仍成立。'],
 ['Gentler Streak','https://www.floow.design/aso-screens/gentler','不均匀的圆润曲线也能形成强烈的个性。'],
];
const esc=s=>s.replace(/[&<>"]/g,ch=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[ch]));
function gallery(){return `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>趣序 / 趣合 / 知沐 · 图标提案</title><style>
:root{color-scheme:light;--ink:#242934;--muted:#777b87;--line:#dfdfe4;--paper:#f5f4f2}*{box-sizing:border-box}body{margin:0;color:var(--ink);background:var(--paper);font-family:"Microsoft YaHei","PingFang SC",sans-serif}main{max-width:1540px;margin:auto;padding:55px 48px 50px}.eyebrow{font:11px Arial,sans-serif;letter-spacing:2.2px;color:var(--muted)}header{display:flex;justify-content:space-between;gap:40px;align-items:end;margin:22px 0 43px}h1{font:400 48px "STSong","SimSun",serif;letter-spacing:3px;margin:0 0 18px}header p{font-size:13px;color:var(--muted);line-height:1.9;margin:0}.edition{font:11px Arial,sans-serif;letter-spacing:1.3px;white-space:nowrap;color:var(--muted);padding-bottom:7px}.grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:22px}.card{background:#fff;border:1px solid var(--line);border-radius:16px;overflow:hidden}.picture{position:relative;display:grid;place-items:center;width:100%;padding:40px 31px 26px;border:0;background:var(--stage);cursor:zoom-in}.picture img{display:block;width:100%;max-width:350px;height:auto;filter:drop-shadow(0 13px 14px #20243d0c)}.number{position:absolute;left:22px;top:18px;font:12px Arial;color:var(--muted)}.content{padding:25px 25px 26px}.heading{display:flex;justify-content:space-between;align-items:baseline}h2{font:400 32px "STSong","SimSun",serif;letter-spacing:3px;margin:0}.direction{font-size:11px;color:var(--muted)}.tag{font-size:13px;line-height:1.8;margin:17px 0;color:#535967}.points{margin:0;display:grid;gap:14px}.point{border-top:1px solid var(--line);padding-top:15px}.point b{font-size:12px;font-weight:500}.point p{font-size:12px;line-height:1.9;color:var(--muted);margin:6px 0 0}.sizes{display:flex;align-items:center;gap:15px;margin:22px 0}.downloads{display:flex;gap:17px;flex-wrap:wrap}a{color:#465b83;text-underline-offset:5px;text-decoration-thickness:1px;font-size:12px}a:hover{color:#1b3156}button:focus-visible,a:focus-visible{outline:2px solid #4b6a9a;outline-offset:5px}.research{margin-top:46px;border-top:1px solid var(--line);padding-top:30px}.research h3{font:400 26px "STSong","SimSun",serif;margin:0 0 16px}.research>p{max-width:850px;font-size:13px;line-height:2;color:var(--muted)}.references{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:15px;margin:20px 0 25px}.reference{padding:15px 0;border-top:1px solid var(--line)}.reference p{font-size:11px;line-height:1.8;margin:10px 0 0;color:var(--muted)}footer{margin-top:35px;border-top:1px solid var(--line);padding-top:21px;color:var(--muted);font-size:11px;line-height:2}dialog{padding:0;border:1px solid #c4c8d1;border-radius:18px;background:#f7f7f8;max-width:min(90vw,740px);max-height:95vh;overflow:auto}dialog::backdrop{background:#10172caa;backdrop-filter:blur(9px)}dialog img{display:block;width:100%;height:auto}.modal-head{display:flex;justify-content:space-between;align-items:center;padding:15px 20px;font-size:13px}.close{border:0;border-radius:50%;width:33px;height:33px;color:#46516a;background:#e5e8ee;font-size:22px;cursor:pointer}.modal-text{padding:0 22px 15px;font-size:13px;line-height:2;color:#636c7b}@media(max-width:1000px){.grid{grid-template-columns:1fr}.card{display:grid;grid-template-columns:1fr 1fr}.picture{padding:35px}.references{grid-template-columns:repeat(2,1fr)}}@media(max-width:620px){main{padding:30px 20px}header{display:block}h1{font-size:clamp(25px,7.4vw,32px);letter-spacing:1px;text-wrap:balance}.edition{margin-top:23px}.card{display:block}.picture{padding:39px 29px 27px}.content{padding:24px}.references{gap:20px}}
</style></head><body><main><div class="eyebrow">APP IDENTITY / THREE NEW DIRECTIONS</div><header><div><h1>为日常，留下一个记号</h1><p>从成熟应用的识别方式出发，探索连字、曲面与字印。<br>三种独立轮廓，三组配色与表面处理。</p></div><div class="edition">QZXY · ICON STUDIES</div></header><section class="grid">${concepts.map((c,i)=>`<article class="card"><button type="button" class="picture" data-index="${i}" aria-label="放大${c.name}图标" style="--stage:${['#EDF0F5','#F3EFF9','#FBF2E8'][i]}"><span class="number">0${i+1}</span><img src="${c.id}-preview.png" width="1024" height="1024" alt="${c.name}：${c.tagline}"></button><div class="content"><div class="heading"><h2>${c.name}</h2><span class="direction">${c.title} / ${c.roman}</span></div><p class="tag">${c.tagline}</p><div class="points">${c.points.map(([t,p])=>`<div class="point"><b>${t}</b><p>${p}</p></div>`).join('')}</div><div class="sizes">${[64,48,32].map(s=>`<img src="${c.id}-${s}.png" width="${s}" height="${s}" alt="${s} 像素预览">`).join('')}</div><div class="downloads"><a href="${c.id}.png" download>1024 PNG</a><a href="${c.id}-geometry.svg" download>矢量轮廓</a><a href="${c.id}-foreground.png" download>透明前景</a></div></div></article>`).join('')}</section><section class="research"><h3>本轮的参考与判断</h3><p>参考应用的轮廓组织、留白和材质分工；三版均重新绘制路径。连字版研究品牌缩写的可读性；曲面版研究两个部件怎样形成完整符号；字印版研究名称怎样成为直接的识别线索。</p><div class="references">${sources.map(([name,url,note])=>`<div class="reference"><a href="${esc(url)}" target="_blank" rel="noopener noreferrer">${name} ↗</a><p>${note}</p></div>`).join('')}</div><a href="references.md">完整参考记录</a>　<a href="size-check.png">小尺寸与圆形遮罩</a>　<a href="silhouette-sheet.png">单色轮廓对比</a></section><footer>图标与名称均为设计提案。PNG 为静态材质表现，SVG 为可编辑的平面几何源。命名尚未做商标与应用商店重名检索。<br>本页仅展示本轮三版。<a href="README.md">文件说明</a></footer></main><dialog id="preview"><div class="modal-head"><span id="preview-title"></span><button type="button" class="close" aria-label="关闭预览">×</button></div><img id="preview-image" alt=""><p id="preview-text" class="modal-text"></p></dialog><script>const data=${JSON.stringify(concepts)};const dialog=document.getElementById('preview');document.querySelector('.grid').addEventListener('click',event=>{const b=event.target.closest('button[data-index]');if(!b)return;const c=data[Number(b.dataset.index)];document.getElementById('preview-title').textContent=c.name+' / '+c.title;const img=document.getElementById('preview-image');img.src=c.id+'.png';img.alt=c.name+'图标完整预览';document.getElementById('preview-text').textContent=c.idea;dialog.showModal()});document.querySelector('.close').addEventListener('click',()=>dialog.close());dialog.addEventListener('click',e=>{if(e.target===dialog){const r=dialog.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)dialog.close();}});</script></body></html>`;}
function readme(){return `# 趣序 / 趣合 / 知沐

本轮研究了 Craft、Arc、Linear、Things、Raycast、Anytype、Structured 和 Gentler Streak 的图形与材质处理，再绘制三组独立路径。参考记录见 [references.md](references.md)。

![三版图标](overview.png)

[打开画廊](index.html) · [小尺寸与遮罩](size-check.png) · [单色轮廓](silhouette-sheet.png)

${concepts.map((c,i)=>`## 0${i+1} · ${c.name} / ${c.title}

${c.idea}

名称建议：${c.meaning}

[1024 PNG](${c.id}.png) · [透明前景](${c.id}-foreground.png) · [矢量轮廓](${c.id}-geometry.svg)
`).join('\n')}
## 文件与再生成

- PNG：1024×1024 满版底图、圆角预览、透明前景，以及 512 / 64 / 48 / 32 像素小样。
- SVG：独立、可编辑的平面路径。PNG 中的表面明暗通过向量遮罩和法线计算生成，为静态材质表现。
- 完整满版底图：文件名不带后缀的 PNG；圆角预览只用于展示。
- marks.cjs：几何、配色、文案。
- render-masks.cjs：生成可编辑矢量与逐层遮罩。
- render-materials.py：根据遮罩的距离场计算圆角、表面法线和局部反光。
- compose.py：比较板、单色板、小尺寸与遮罩板。
- 配置 sharp、Pillow、numpy、scipy 后运行 node build.cjs。可通过 ICON_STUDY_PYTHON 指定 Python 路径。
- 本轮交付是设计研究；名称仍需重名与商标检索，平台接入时需进一步校准安全区。
`;}
async function main(){
 run(process.execPath,[path.join(ROOT,'render-masks.cjs')]);
 run(PYTHON,[path.join(ROOT,'render-materials.py')]);
 // Replace former material vectors with the current honest, editable flat source.
 for(const c of concepts){
  const geometry=fs.readFileSync(path.join(ROOT,c.id+'-geometry.svg'),'utf8');
  fs.writeFileSync(path.join(ROOT,c.id+'.svg'),geometry);
  fs.writeFileSync(path.join(ROOT,c.id+'-foreground.svg'),geometry);
  const full=geometry.replace('<svg ','<svg ').replace(/(<svg[^>]*>)/,'$1<rect width="512" height="512" fill="'+c.bg[0]+'"/>');
  fs.writeFileSync(path.join(ROOT,c.id+'-preview.svg'),full);
 }
 fs.writeFileSync(path.join(ROOT,'index.html'),gallery());
 fs.writeFileSync(path.join(ROOT,'README.md'),readme());
 run(PYTHON,[path.join(ROOT,'compose.py')]);
 console.log('Built '+concepts.map(c=>c.name).join(' / '));
}
main().catch(e=>{console.error(e);process.exitCode=1});
