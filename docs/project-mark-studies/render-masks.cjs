const fs=require('node:fs');
const path=require('node:path');
const sharp=require('sharp');
const concepts=require('./marks.cjs');
const svg=(body,w=1024,h=1024,view='0 0 512 512')=>`<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="${view}">${body}</svg>`;
const shape=p=>`<path d="${p.d}" fill-rule="${p.rule||'nonzero'}"${p.transform?` transform="${p.transform}"`:''}/>`;
async function main(){
  let draft='';
  for(const [i,c] of concepts.entries()){
    const geom=`<g fill="#173842">${c.paths.map(shape).join('')}</g>`;
    fs.writeFileSync(path.join(__dirname,c.id+'-geometry.svg'),svg(geom)+'\n');
    await sharp(Buffer.from(svg(geom))).png().toFile(path.join(__dirname,c.id+'-geometry.png'));
    for(const [j,p] of c.paths.entries())await sharp(Buffer.from(svg('<g fill="white">'+shape(p)+'</g>'))).png().toFile(path.join(__dirname,c.id+'-mask-'+j+'.png'));
    draft+=`<g transform="translate(${i*512} 0)"><rect width="512" height="512" fill="#F5F7F6"/>${geom}</g>`;
  }
  await sharp(Buffer.from(svg(draft,1536,512,'0 0 1536 512'))).png().toFile(path.join(__dirname,'silhouette-draft.png'));
  fs.writeFileSync(path.join(__dirname,'concepts.json'),JSON.stringify(concepts,null,2)+'\n');
}
main().catch(e=>{console.error(e);process.exitCode=1;});
