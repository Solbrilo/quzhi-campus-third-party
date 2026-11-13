/* Local delivery QA, using the bundled Playwright runtime. */
const path=require('node:path');
const fs=require('node:fs');
const {pathToFileURL}=require('node:url');
const assert=require('node:assert/strict');
const {chromium}=require('playwright');
async function main(){
 const root=__dirname;
 const browser=await chromium.launch({headless:true});
 try{
 const page=await browser.newPage({viewport:{width:1500,height:1100},deviceScaleFactor:1});
 const errors=[];page.on('pageerror',e=>errors.push(String(e)));
 await page.goto(pathToFileURL(path.join(root,'index.html')).href);
 await page.waitForLoadState('networkidle');
 assert.equal(await page.locator('article.card').count(),3);
 const bad=await page.locator('article img').evaluateAll(images=>images.filter(i=>!i.complete||!i.naturalWidth).map(i=>i.src));
 assert.deepEqual(bad,[]);
 const localLinks=await page.locator('a[href]').evaluateAll(links=>links.map(l=>l.getAttribute('href')).filter(h=>!/^https?:/.test(h)));
 for(const href of localLinks)assert.ok(fs.existsSync(path.join(root,href)),href);
 for(let i=0;i<3;i++){
  await page.locator('button[data-index]').nth(i).click();
  assert.equal(await page.locator('dialog').evaluate(d=>d.open),true);
  await page.locator('#preview-image').evaluate(img=>img.decode());
  assert.ok(await page.locator('#preview-image').evaluate(img=>img.naturalWidth===1024));
  await page.getByRole('button',{name:'关闭预览'}).click();
  assert.equal(await page.locator('dialog').evaluate(d=>d.open),false);
 }
 await page.screenshot({path:path.join(root,'gallery-desktop.png'),fullPage:true});
 await page.setViewportSize({width:390,height:844});
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 await page.screenshot({path:path.join(root,'gallery-mobile.png'),fullPage:true});
 assert.deepEqual(errors,[]);
 console.log(JSON.stringify({cards:3,images:12,localLinks:localLinks.length,dialogs:3,mobileWidth:390,pageErrors:errors.length}));
 }finally{await browser.close();}
}
main().catch(e=>{console.error(e);process.exitCode=1;});
