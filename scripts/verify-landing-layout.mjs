import {chromium} from '@playwright/test';
import fs from 'node:fs/promises';
import assert from 'node:assert/strict';
const out='target/landing-fix';await fs.mkdir(out,{recursive:true});
const browser=await chromium.launch({channel:process.env.LANDING_BROWSER||'chrome'});
const results=[];
try{
 for(const [width,height] of [[1366,768],[1536,800],[1920,1080],[2560,1440],[390,844]]){
  const context=await browser.newContext({viewport:{width,height},deviceScaleFactor:width===1536?1.25:1});
  const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
  if(process.env.LANDING_LOCAL_CSS==='true')await page.route('**/css/premium-graphite.css*',route=>route.fulfill({path:'frontend/css/premium-graphite.css',contentType:'text/css'}));
  await page.goto('http://localhost:8080');await page.waitForFunction(()=>document.documentElement.dataset.theme==='dark');await page.evaluate(()=>document.fonts.ready);
  for(const selector of ['#about','#how-it-works','#recommendations-preview','.features-section']){
   await page.locator(selector).scrollIntoViewIfNeeded();
   await page.waitForFunction(s=>document.querySelector(s).classList.contains('in-view'),selector);
   await page.locator(selector).evaluate(e=>e.getAnimations().forEach(a=>a.finish()));
   const metrics=await page.locator(selector).evaluate(e=>{const r=e.getBoundingClientRect(),c=getComputedStyle(e);return{x:r.x,right:r.right,width:r.width,background:c.backgroundColor,transform:c.transform,overflow:document.documentElement.scrollWidth>innerWidth}});
   assert.equal(metrics.background,'rgba(0, 0, 0, 0)',selector+' paints a separate page background');assert.equal(metrics.transform,'none');assert.equal(metrics.overflow,false);assert(metrics.x>=0&&metrics.right<=width+.5);
   results.push({viewportWidth:width,selector,...metrics});
   await page.screenshot({path:`${out}/${width}-${selector.replace(/[#.]/g,'')}.png`});
  }
  if(width>1000){const boxes=await page.locator('#landingPage .editorial-section').evaluateAll(es=>es.map(e=>e.getBoundingClientRect().x));assert(boxes.every(x=>Math.abs(x-boxes[0])<.5),'Landing gutters must align');}
  assert(await page.locator('#landingPage .feature-card').evaluateAll(es=>es.every(e=>getComputedStyle(e).backdropFilter==='none')),'Opaque feature cards retain backdrop blur');
  // Check the previously covered heading while scrolling through the first feature card.
  const card=page.locator('.feature-card').first();await card.scrollIntoViewIfNeeded();
  assert(await card.locator('h3').evaluate(e=>{const r=e.getBoundingClientRect();return document.elementsFromPoint(r.x+r.width/2,r.y+r.height/2).includes(e)}),'Feature title is covered');
  await page.screenshot({path:`${out}/${width}-feature-card.png`});
  await context.close();assert.deepEqual(errors,[]);
 }
 const light=await browser.newPage({viewport:{width:1536,height:800}});
 if(process.env.LANDING_LOCAL_CSS==='true')await light.route('**/css/premium-graphite.css*',route=>route.fulfill({path:'frontend/css/premium-graphite.css',contentType:'text/css'}));
 await light.addInitScript(()=>localStorage.setItem('recomai-theme','light'));
 await light.goto('http://localhost:8080');await light.waitForFunction(()=>document.documentElement.dataset.theme==='light');
 assert(await light.locator('#about h2').evaluate(e=>getComputedStyle(e).color===getComputedStyle(document.body).color),'Landing headings must follow the selected theme');
 await light.close();
 await fs.writeFile(`${out}/verification.json`,JSON.stringify({passed:true,lightThemeHeadingChecked:true,results},null,2));
 console.log('Landing backgrounds, aligned gutters, card visibility and overflow passed at five desktop/mobile sizes.');
}finally{await browser.close();}
