import {expect} from '@playwright/test';
import fs from 'node:fs/promises';import path from 'node:path';
export async function phase3Capture(page,name){
 await page.emulateMedia({reducedMotion:'reduce'});
 for(const [width,height] of [[1366,768],[1440,900],[1920,1080]]){
  await page.setViewportSize({width,height});await page.evaluate(()=>scrollTo({top:0,behavior:'instant'}));
  await expect(page.locator('.content-panel.active')).toHaveCSS('opacity','1');expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
  const audit=process.env.E2E_AUDIT_PHASE==='3-5'?'recomai-2-phase-3-5':'recomai-2-phase-3';
  const folder=path.resolve('design-audit',audit,'screenshots',`${width}x${height}`);await fs.mkdir(folder,{recursive:true});await page.screenshot({path:path.join(folder,name+'.png'),animations:'disabled'});
  if(width===1366&&['journeys','saved','admin-configuration'].includes(name)){const review=path.resolve('design-audit',audit,'review-states');await fs.mkdir(review,{recursive:true});await page.screenshot({path:path.join(review,name+'-full.png'),animations:'disabled',fullPage:true});}
 }
}
