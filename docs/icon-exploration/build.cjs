/* Four original studies: folded sheet, overlapping panes, light seam, optical bloom. */
const fs = require('node:fs');
const path = require('node:path');
const sharp = require('sharp');
const DIR = __dirname;

const studies = [
  {
    id:'01-quyang', name:'趣漾', roman:'QUYANG', title:'折页', pronunciation:'qù yàng',
    description:'把“智”的 Z 折成一条玻璃带，转折之间保留水的轻盈。',
    naming:'保留趣智校园的“趣”，“漾”带出水与校园的活力。',
    art:'以一笔连续的折面建立节奏，艺术性来自留白与折射的方向。',
    bg:['#F5FAFC','#DDEAF0'], glow:'#9CBFD5', text:'#263F4F',
    layers:[{
      d:'M164 158H346Q360 158 356 174L350 193Q347 201 339 208L220 307H345Q358 307 358 321V337Q358 350 344 350H165Q151 350 156 334L163 314Q166 306 175 299L290 201H166Q153 201 153 188V172Q153 158 164 158Z',
      colors:['#F2FBFF','#9BC9D9','#427A96'], opacity:.78,
      facet:'M290 201L350 193L220 307L163 314Z', facetColor:'#69A7BA',
    }],
  },
  {
    id:'02-muzhi', name:'沐知', roman:'MUZHI', title:'交叠', pronunciation:'mù zhī',
    description:'两片错位的玻璃像校园的窗，把冷暖颜色交给交叠处。',
    naming:'“沐”点出用途，“知”借“智”的同音，也联系校园与知性。',
    art:'不把水画出来，以透明叠色与不对称平衡表达清澈、温润。',
    bg:['#FAF8F4','#EEE9E4'], glow:'#E7B3A7', text:'#554D50',
    layers:[
      {d:'M176 149H268Q289 149 289 170V262Q289 283 268 283H176Q155 283 155 262V170Q155 149 176 149Z',transform:'rotate(-12 222 216)',colors:['#F7FCFF','#A9C6E5','#5E8FBA'],opacity:.64},
      {d:'M246 231H338Q359 231 359 252V344Q359 365 338 365H246Q225 365 225 344V252Q225 231 246 231Z',transform:'rotate(11 292 298)',colors:['#FFF8EC','#DAB6A3','#AD788D'],opacity:.66},
    ],
  },
  {
    id:'03-qujian', name:'趣间', roman:'QUJIAN', title:'光隙', pronunciation:'qù jiān',
    description:'两扇静立的玻璃之间留一束光，像忙碌校园里短暂的独处。',
    naming:'“趣”联系趣智校园，“间”既是空间，也是一段自己的时间。',
    art:'用两块不对称的体量与一道负空间完成构图，克制而有张力。',
    bg:['#142A35','#233A46'], glow:'#66ABB7', text:'#EDF4F5', dark:true,
    layers:[
      {d:'M181 156H237V311L157 354V180Q157 156 181 156Z',colors:['#EAF9FF','#6D9EAB','#385A6C'],opacity:.72},
      {d:'M278 198L355 156V330Q355 354 331 354H278Z',colors:['#F1FFFD','#99B4AE','#497C8A'],opacity:.73},
    ],
  },
  {
    id:'04-zhinuan', name:'知暖', roman:'ZHINUAN', title:'棱花', pronunciation:'zhī nuǎn',
    description:'一片轻轻旋转的晶体，像被温度舒展开的花。',
    naming:'用“知”与“智”同音的联系，融入“知你冷暖”的日常关照。',
    art:'让四个方向稍有偏转；光从棱面穿过，轮廓轻盈，中心安静。',
    bg:['#FCF9F4','#F0E9DD'], glow:'#E8C4AB', text:'#5A5147',
    layers:[{
      d:'M254 136C271 196 305 219 375 234C312 254 289 291 276 375C253 313 217 290 137 276C196 252 220 217 254 136Z',
      colors:['#FFF9E9','#CDC1B5','#8093B4'],opacity:.90,
      facet:'M254 136L257 256L375 234C305 219 271 196 254 136ZM276 375L257 256L137 276C217 290 253 313 276 375Z',facetColor:'#D7B8A5',
    }],
  },
];

function shape(layer, attrs='') {
  return `<path d="${layer.d}"${layer.transform ? ` transform="${layer.transform}"` : ''} ${attrs}/>`;
}

function definitions(c) {
  return `<defs>
    <linearGradient id="background" x1="0" y1="0" x2=".65" y2="1"><stop stop-color="${c.bg[0]}"/><stop offset="1" stop-color="${c.bg[1]}"/></linearGradient>
    <radialGradient id="atmosphere"><stop stop-color="${c.glow}" stop-opacity="${c.dark?'.20':'.18'}"/><stop offset="1" stop-color="${c.glow}" stop-opacity="0"/></radialGradient>
    <linearGradient id="rim" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#FFFFFF" stop-opacity=".95"/><stop offset=".28" stop-color="#D9F4FF" stop-opacity=".5"/><stop offset=".53" stop-color="#456D89" stop-opacity=".45"/><stop offset=".74" stop-color="#DBBFA4" stop-opacity=".72"/><stop offset="1" stop-color="#C0E9F3" stop-opacity=".9"/></linearGradient>
    <linearGradient id="reflection" x1="0" y1="0" x2=".8" y2="1"><stop stop-color="#FFFFFF" stop-opacity=".65"/><stop offset=".36" stop-color="#FFFFFF" stop-opacity=".04"/><stop offset=".47" stop-color="#FFFFFF" stop-opacity=".02"/><stop offset=".57" stop-color="#FFFFFF" stop-opacity=".33"/><stop offset=".64" stop-color="#FFFFFF" stop-opacity="0"/><stop offset="1" stop-color="#FFFFFF" stop-opacity=".03"/></linearGradient>
    <filter id="shadow" x="-35%" y="-35%" width="170%" height="180%"><feGaussianBlur stdDeviation="6"/></filter>
    <filter id="softEdge" x="-10%" y="-10%" width="120%" height="120%"><feGaussianBlur stdDeviation=".8"/></filter>
    <filter id="fineBevel" x="-10%" y="-10%" width="120%" height="120%" color-interpolation-filters="sRGB"><feGaussianBlur in="SourceAlpha" stdDeviation="1.3" result="height"/><feSpecularLighting in="height" surfaceScale="4" specularConstant=".34" specularExponent="32" lighting-color="#FFFFFF" result="light"><fePointLight x="40" y="-100" z="200"/></feSpecularLighting><feComposite in="light" in2="SourceAlpha" operator="in" result="edgeLight"/><feComposite in="SourceGraphic" in2="edgeLight" operator="arithmetic" k2="1" k3=".45"/></filter>
    ${c.layers.map((l,i)=>`<clipPath id="clip${i}">${shape(l)}</clipPath><linearGradient id="face${i}" x1="0" y1="0" x2=".8" y2="1"><stop stop-color="${l.colors[0]}" stop-opacity=".95"/><stop offset=".38" stop-color="${l.colors[1]}" stop-opacity=".47"/><stop offset=".62" stop-color="${l.colors[1]}" stop-opacity=".36"/><stop offset="1" stop-color="${l.colors[2]}" stop-opacity=".76"/></linearGradient>`).join('')}
  </defs>`;
}

function glassLayers(c) {
  return c.layers.map((l,i)=>`<g>
    <g transform="translate(0 9)" opacity="${c.dark?'.20':'.14'}" filter="url(#shadow)">${shape(l,'fill="#294755"')}</g>
    <g transform="translate(0 3.2)" opacity="${c.dark?'.50':'.25'}">${shape(l,`fill="${l.colors[2]}"`)}</g>
    <g filter="url(#fineBevel)">${shape(l,`fill="url(#face${i})" fill-opacity="${l.opacity}" stroke="url(#rim)" stroke-width="2.1" stroke-linejoin="round"`)}</g>
    <g clip-path="url(#clip${i})"><rect x="100" y="100" width="320" height="320" fill="url(#reflection)"/>${l.facet?`<path d="${l.facet}" fill="${l.facetColor}" opacity=".13" stroke="#FFFFFF" stroke-opacity=".20" stroke-width=".65"/>`:''}<path d="M99 176L372 62L429 111L121 310Z" fill="#FFFFFF" opacity="${c.dark?'.10':'.11'}" filter="url(#softEdge)"/></g>
    ${shape(l,'fill="none" stroke="#FFFFFF" stroke-opacity=".44" stroke-width=".6" stroke-linejoin="round"')}
  </g>`).join('');
}

function svg(c,kind='full') {
  if(kind==='geometry')return `<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 512 512"><title>${c.name} · ${c.title} · 原创几何稿</title><g fill="#294B59" transform="translate(256 256) scale(1.22) translate(-256 -256)">${c.layers.map(l=>shape(l)).join('')}</g></svg>`;
  const rounded=kind==='preview';
  const background=kind!=='foreground';
  return `<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 512 512"><title>${c.name} · ${c.title} · 玻璃图标</title>${definitions(c)}${rounded?'<defs><clipPath id="iconClip"><rect width="512" height="512" rx="112"/></clipPath></defs><g clip-path="url(#iconClip)">':''}${background?'<rect width="512" height="512" fill="url(#background)"/><ellipse cx="256" cy="282" rx="240" ry="214" fill="url(#atmosphere)"/>':''}<g transform="translate(256 256) scale(1.22) translate(-256 -256)">${glassLayers(c)}</g>${rounded?'</g>':''}</svg>`;
}

async function build() {
  for(const c of studies){
    for(const [kind,suffix] of [['full',''],['preview','-preview'],['foreground','-foreground'],['geometry','-geometry']]){
      const source=svg(c,kind);
      fs.writeFileSync(path.join(DIR,c.id+suffix+'.svg'),source+'\n');
      await sharp(Buffer.from(source)).png().toFile(path.join(DIR,c.id+suffix+'.png'));
    }
    for(const s of [512,64,48,32])await sharp(Buffer.from(svg(c,'preview'))).resize(s,s).png().toFile(path.join(DIR,`${c.id}-${s}.png`));
  }
  fs.writeFileSync(path.join(DIR,'concepts.json'),JSON.stringify(studies.map(({layers,...c})=>c),null,2)+'\n');
  fs.writeFileSync(path.join(DIR,'index.html'),gallery());
  fs.writeFileSync(path.join(DIR,'README.md'),readme());
  console.log(JSON.stringify({folder:DIR,concepts:studies.map(c=>({name:c.name,title:c.title}))}));
}

function gallery(){
return `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>另起一笔 · 应用图标新提案</title><style>
*{box-sizing:border-box}body{margin:0;background:#141c25;color:#e9edf0;font-family:"Microsoft YaHei","PingFang SC",sans-serif}main{max-width:1500px;margin:auto;padding:52px 48px}header{display:flex;justify-content:space-between;gap:28px;align-items:end;border-bottom:1px solid #36404b;padding-bottom:30px;margin-bottom:35px}.eyebrow{font:12px Georgia,serif;letter-spacing:2px;color:#a8b4c0}h1{font-family:"STSong","SimSun",serif;font-size:52px;font-weight:400;margin:18px 0}header p{font-size:13px;line-height:2;color:#a8b4c0;margin:0}.round{font:12px Georgia,serif;color:#a8b4c0;white-space:nowrap}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:28px}.card{background:#202a35;border:1px solid #3b4552;border-radius:6px;overflow:hidden}.picture{border:0;padding:30px;width:100%;display:grid;place-items:center;cursor:zoom-in;background:var(--stage);position:relative}.picture img{display:block;width:100%;max-width:370px;height:auto;border-radius:22%;transition:transform .3s}.picture:hover img{transform:scale(1.035)}.concept{position:absolute;left:26px;top:24px;font:12px Georgia,serif;letter-spacing:2px;color:var(--tone)}.content{padding:24px 28px 27px}.line{display:flex;justify-content:space-between;align-items:baseline}h2{margin:0;font-family:"STSong","SimSun",serif;font-size:31px;font-weight:400;letter-spacing:3px}.roman{font:11px Georgia,serif;letter-spacing:1px;color:#a8b4c0}.description{font-size:13px;color:#b9c4ce;line-height:1.9;min-height:48px;margin:16px 0 20px}.bottom{display:flex;align-items:center;justify-content:space-between;gap:20px}.sizes{display:flex;align-items:center;gap:14px}.sizes img{display:block}.links{display:flex;gap:15px}a{color:#dfe8ed;text-decoration:none;font-size:12px;border-bottom:1px solid #8293a0;padding-bottom:3px}a:hover{border-color:white}.footer{font-size:12px;color:#9eacb9;line-height:2;border-top:1px solid #36404b;margin-top:35px;padding-top:25px}button:focus-visible,a:focus-visible{outline:2px solid #b1d3e2;outline-offset:4px}dialog{border:1px solid #647789;border-radius:15px;padding:0;background:#1d2833;color:#eaf0f3;max-width:min(92vw,760px);max-height:94vh;overflow:auto}dialog::backdrop{background:#090f17bc;backdrop-filter:blur(12px)}.modal-head{padding:16px 20px;display:flex;align-items:center;justify-content:space-between}.close{border:0;background:#354656;color:white;font-size:22px;border-radius:50%;height:32px;width:32px;cursor:pointer}dialog img{display:block;width:100%;height:auto}.modal-note{font-size:12px;line-height:1.9;color:#bdc9d2;padding:18px 23px;margin:0}@media(max-width:850px){main{padding:32px 22px}header{display:block}.round{margin-top:20px}.picture img{max-width:330px}.content{padding:22px}.bottom{display:block}.links{margin-top:20px}}@media(max-width:590px){.grid{grid-template-columns:1fr}h1{font-size:40px}.picture{padding:45px 24px 24px}.picture img{max-width:300px}.description{min-height:0}}
</style></head><body><main><header><div><div class="eyebrow">OPTICAL OBJECTS / A FRESH START</div><h1>另起一笔</h1><p>折页、交叠、光隙、棱花。<br>从四种新的构图出发，让玻璃的透明成为图形的一部分。</p></div><div class="round">FOUR ORIGINAL ICON STUDIES / 2026</div></header><section class="grid">${studies.map((c,i)=>`<article class="card"><button class="picture" type="button" data-index="${i}" aria-label="放大${c.name}图标" style="--stage:${c.bg[0]};--tone:${c.text}"><span class="concept">0${i+1} / ${c.title}</span><img src="${c.id}-preview.png" width="1024" height="1024" alt="${c.name}：${c.title}"></button><div class="content"><div class="line"><h2>${c.name}</h2><span class="roman">${c.roman}</span></div><p class="description">${c.description}</p><div class="bottom"><div class="sizes">${[64,48,32].map(s=>`<img src="${c.id}-${s}.png" width="${s}" height="${s}" alt="${s}像素预览">`).join('')}</div><div class="links"><a href="${c.id}.png" download>PNG 原图</a><a href="${c.id}.svg" download>SVG 原稿</a></div></div></div></article>`).join('')}</section><div class="footer">命名方向：趣漾 · 沐知 · 趣间 · 知暖。名称与图标可以交叉搭配。<br>每组提供 1024 PNG、完整材质 SVG、透明玻璃前景与单色几何稿。概念提案，尚未替换应用资源。</div></main><dialog id="preview"><div class="modal-head"><span id="modal-title"></span><button class="close" type="button" aria-label="关闭预览">×</button></div><img id="modal-image" alt=""><p class="modal-note" id="modal-note"></p></dialog><script>const data=${JSON.stringify(studies.map(({layers,...c})=>c))};const modal=document.getElementById('preview');document.querySelector('.grid').addEventListener('click',e=>{const b=e.target.closest('button[data-index]');if(!b)return;const c=data[Number(b.dataset.index)];document.getElementById('modal-title').textContent=c.name+' / '+c.title;const img=document.getElementById('modal-image');img.src=c.id+'.png';img.alt=c.name+'图标原图';document.getElementById('modal-note').textContent=c.naming+' '+c.art;modal.showModal()});document.querySelector('.close').addEventListener('click',()=>modal.close());</script></body></html>`;
}

function readme(){
return `# 另起一笔 · 应用图标全新提案

全新四组设计，以折页、交叠、光隙、棱花为构图起点。保留艺术感、简洁和玻璃质感的要求，并重新探索与趣智校园有关的命名。

![四组全新设计](overview.png)

[打开方案画廊](index.html) · [小尺寸与遮罩检查](size-check.png)

${studies.map((c,i)=>`## 0${i+1} · ${c.name} / ${c.title}

${c.description}

命名：${c.naming}

艺术方向：${c.art}

[PNG 成稿](${c.id}.png) · [完整材质 SVG](${c.id}.svg) · [透明前景](${c.id}-foreground.png) · [单色几何 SVG](${c.id}-geometry.svg)
`).join('\n')}
## 交付说明

- 每组的 PNG 为 1024×1024；无后缀文件使用满版方形背景，适合继续导出平台资源。
- 完整材质 SVG 使用可编辑路径、渐变与滤镜；PNG 直接由同一 SVG 渲染。
- \`-preview\` 是圆角展示版；\`-foreground\` 是含玻璃效果与轻投影的透明前景；\`-geometry\` 是单色可编辑轮廓。
- 另附 512、64、48、32 像素预览；正式接入前需按平台要求校准安全区并查看设备效果。
- 名称和图标可交叉搭配；名称尚未进行商标与应用商店重名检索。
- 本次只交付设计提案，不改动 Android / HarmonyOS 代码与正式图标。
- 重建：为 Node.js 配置 \`sharp\`，运行 \`node build.cjs\`；安装 Pillow 后运行 \`python compose.py\`。
`;
}

build().catch(e=>{console.error(e);process.exitCode=1});
