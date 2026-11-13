const fs=require('node:fs');
const path=require('node:path');
const sharp=require('sharp');
const data=require('./marks.cjs');
const root=__dirname;
const svg=body=>`<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 512 512">${body}</svg>`;
const shape=p=>`<path d="${p.d}" fill-rule="${p.rule||'nonzero'}"/>`;
async function main(){
 for(const c of data){
  const mono=svg(`<g fill="#273F46">${c.paths.map(shape).join('')}</g>`);
  fs.writeFileSync(path.join(root,c.id+'-geometry.svg'),mono);
  await sharp(Buffer.from(mono)).png().toFile(path.join(root,c.id+'-geometry.png'));
  for(const [j,p] of c.paths.entries())await sharp(Buffer.from(svg('<g fill="white">'+shape(p)+'</g>'))).png().toFile(path.join(root,c.id+'-mask-'+j+'.png'));
 }
 fs.writeFileSync(path.join(root,'concepts.json'),JSON.stringify(data,null,2)+'\n');
 const sheet=`<svg xmlns="http://www.w3.org/2000/svg" width="${512*data.length}" height="512" viewBox="0 0 ${512*data.length} 512">${data.map((c,i)=>`<g transform="translate(${512*i} 0)"><rect width="512" height="512" fill="#F6F6F3"/><g fill="#273F46">${c.paths.map(shape).join('')}</g></g>`).join('')}</svg>`;
 await sharp(Buffer.from(sheet)).png().toFile(path.join(root,'shape-study.png'));
}
main().catch(e=>{console.error(e);process.exitCode=1;});
