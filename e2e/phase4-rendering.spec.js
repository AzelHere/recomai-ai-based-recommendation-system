import {test,expect} from '@playwright/test';
import fs from 'node:fs/promises';
test('Phase 4 landing rendering measurements',async({page})=>{
 test.skip(!['baseline','final'].includes(process.env.PHASE4_RENDER),'Explicit rendering audit only');
 await page.addInitScript(()=>{
  const raf=window.requestAnimationFrame;window.__auditFrames=0;
  window.requestAnimationFrame=callback=>raf.call(window,time=>{window.__auditFrames++;callback(time);});
 });
 await page.setViewportSize({width:1440,height:900});await page.emulateMedia({reducedMotion:'no-preference'});
 await page.goto('/');await expect(page.locator('canvas[data-learning-constellation]')).toBeVisible();await page.evaluate(()=>document.fonts.ready);
 async function sample(){const start=await page.evaluate(()=>({frames:window.__auditFrames,time:performance.now()}));await page.waitForTimeout(2000);return page.evaluate(s=>({durationMs:performance.now()-s.time,callbacks:window.__auditFrames-s.frames}),start);}
 const active=await sample();await page.emulateMedia({reducedMotion:'reduce'});await page.waitForTimeout(250);const reduced=await sample();
 const loading=await page.evaluate(()=>({navigation:performance.getEntriesByType('navigation').map(n=>({domContentLoadedMs:n.domContentLoadedEventEnd,loadMs:n.loadEventEnd,transferBytes:n.transferSize})),resources:performance.getEntriesByType('resource').map(r=>({path:new URL(r.name).pathname,type:r.initiatorType,durationMs:r.duration,transferBytes:r.transferSize,decodedBytes:r.decodedBodySize}))}));
 expect(reduced.callbacks).toBeLessThanOrEqual(2);
 await page.emulateMedia({reducedMotion:'no-preference'});
 await page.locator('#landingPage').evaluate(node=>{node.hidden=true;});await page.waitForTimeout(250);const hidden=await sample();expect(hidden.callbacks).toBeLessThanOrEqual(2);
 await page.locator('#landingPage').evaluate(node=>{node.hidden=false;});await page.waitForTimeout(250);
 const canvas=await page.locator('canvas[data-learning-constellation]').evaluate(c=>({width:c.width,height:c.height,cssWidth:c.getBoundingClientRect().width,cssHeight:c.getBoundingClientRect().height,devicePixelRatio:devicePixelRatio}));
 const cdp=await page.context().newCDPSession(page);await cdp.send('Performance.enable');const metrics=await cdp.send('Performance.getMetrics');
 if(process.env.PHASE4_RENDER==='final'){
  const supported=await page.locator('canvas[data-learning-constellation]').evaluate(canvas=>{const extension=canvas.getContext('webgl2')?.getExtension('WEBGL_lose_context');if(!extension)return false;extension.loseContext();return true;});
  if(supported)await expect(page.locator('.constellation-static canvas[data-learning-constellation]')).toBeHidden();
 }
 await fs.writeFile(`design-audit/recomai-2-phase-4/evidence/rendering-${process.env.PHASE4_RENDER}.json`,JSON.stringify({method:'1440x900, default DPR, no CPU throttling; global RAF callback windows 2 seconds, normal then reduced motion, then hidden landing DOM. Browser automation timing is not display FPS or production capacity.',active,reduced,hidden,canvas,loading,metrics:Object.fromEntries(metrics.metrics.filter(m=>['JSHeapUsedSize','JSHeapTotalSize','Nodes','Documents','TaskDuration','ScriptDuration'].includes(m.name)).map(m=>[m.name,m.value]))},null,2));
});
