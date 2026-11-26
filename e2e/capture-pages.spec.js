import { onboardingStage } from './onboarding-helper.js';
import { test, expect } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

const out=path.resolve('design-audit');
const sizes=[[1366,768],[1440,900],[1920,1080],[2560,1440]];
const pages=[]; const failures=[]; const errors={console:[],network:[]};
const names={discoverPanel:'profile',recommendationsPanel:'for-you',catalogPanel:'explore',savedPanel:'saved',interestProfilePanel:'my-taste',progressPanel:'progress',activityPanel:'activity',howWorksPanel:'how-it-works',adminPanel:'admin',preferencesPanel:'category-preferences'};
async function shot(page,key,w,h,fullPage=true){
 await page.setViewportSize({width:w,height:h}); await page.screenshot({path:path.join(out,'screenshots',`${w}x${h}`,`${key}.png`),fullPage,animations:'disabled',timeout:20000});
}
test('independent screenshot collection',async({page})=>{
 test.setTimeout(240000);
 page.on('pageerror',e=>errors.console.push(e.message));
 page.on('console',m=>{if(m.type()==='error')errors.console.push(m.text())});
 page.on('requestfailed',r=>errors.network.push(`${r.method()} ${r.url()} ${r.failure()?.errorText||''}`));
 page.on('response',r=>{if(r.status()>=400)errors.network.push(`${r.status()} ${r.request().method()} ${r.url()}`)});
 async function capture(key,full=true){
   try{for(const [w,h] of sizes)await shot(page,key,w,h,full);pages.push(key);return true}
   catch(e){failures.push({page:key,error:String(e)});return false}
 }
 await page.goto('/'); await expect(page.locator('#landingPage')).toBeVisible(); await capture('landing');
 try{await page.locator('[data-auth="login"]').first().click();await expect(page.locator('#authPanel')).toBeVisible();await capture('login',false)}catch(e){failures.push({page:'login',error:String(e)})}
 try{await page.locator('#showRegister').click();await expect(page.locator('#registerForm')).toBeVisible();await capture('registration',false)}catch(e){failures.push({page:'registration',error:String(e)})}
 const unique=Date.now();
 try{
  await page.locator('#registerForm [name=displayName]').fill('Audit test user');
  await page.locator('#registerForm [name=username]').fill(`audit_${unique}`);
  await page.locator('#registerForm [name=email]').fill(`audit_${unique}@example.test`);
  await page.locator('#registerForm [name=password]').fill('Audit-only-Password-482!');
  await page.getByRole('button',{name:/Create your account/}).click();
  await expect(page.locator('#appShell')).toBeVisible({timeout:25000});
  await expect(page.locator('#onboardingPanel')).toHaveClass(/active/); await capture('onboarding');
  const niche=page.locator('#onboardingNiche'); await expect.poll(()=>niche.locator('option').count()).toBeGreaterThan(1); await onboardingStage(page,1); await niche.selectOption({index:1});
  await onboardingStage(page,0); await page.locator('#onboardingPurpose').selectOption('LEARN'); await onboardingStage(page,3); await page.locator('#onboardingForm button[type=submit]').click(); await expect(page.locator('#catalogItems')).toBeVisible({timeout:20000});
 }catch(e){failures.push({page:'authenticated setup/onboarding',error:String(e)})}
 if(await page.locator('#appShell').isVisible()){
   for(const [id,key] of Object.entries(names)){
    try{
     await page.goto(`${process.env.E2E_BASE_URL}/#app/${id}`); await expect(page.locator(`#${id}`)).toHaveClass(/active/,{timeout:10000});
     await page.waitForTimeout(250); await capture(key);
    }catch(e){failures.push({page:key,error:String(e)})}
   }
   // Record More-menu behavior as a defect without gating any captures.
   try{
    const menu=page.locator('.secondary-navigation');
    if(await menu.isVisible()){
     await menu.locator('summary').click(); const visible=await menu.locator('nav').isVisible();
     if(!visible) failures.push({page:'More menu interaction',error:'Confirmed: clicking More did not make its nav visible; selector .secondary-navigation nav remained hidden.'});
    } else failures.push({page:'More menu interaction',error:'More menu is not visible in this workspace state.'});
   }catch(e){failures.push({page:'More menu interaction',error:`Confirmed interaction test failed: ${String(e)}`})}
   // Feature affordances are captured before attempting to open them.
   try{
    await page.goto(`${process.env.E2E_BASE_URL}/#app/catalogPanel`); await expect(page.locator('#catalogPanel')).toHaveClass(/active/);
    await shot(page,'resource-dialog-before',1440,900,false);
    const view=page.locator('#catalogItems .item-card button').first(); if(await view.count()) await view.click({timeout:3000});
    const dialog=page.locator('dialog[open]'); if(await dialog.count()){for(const [w,h] of sizes)await shot(page,'resource-details',w,h,false);pages.push('resource-details');}
    else failures.push({page:'resource details',error:'No open resource dialog observed after clicking first catalog card action.'});
   }catch(e){failures.push({page:'resource details',error:String(e)})}
 }
 await fs.writeFile(path.join(out,'capture-results.json'),JSON.stringify({pages,failures,errors,sizes},null,2));
});
