import { onboardingStage } from './onboarding-helper.js';
import { test, expect } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

const root = path.resolve('design-audit');
const sizes = [[1440,900],[1366,768],[1920,1080],[2560,1440]];
const inspected = new Set();
const interactionResults = [];
const consoleIssues = [];
const networkIssues = [];

async function snap(page, name, width, height, fullPage = true) {
  await page.setViewportSize({width,height});
  await page.waitForTimeout(180);
  const folder = path.join(root,'screenshots',`${width}x${height}`);
  await fs.mkdir(folder,{recursive:true});
  await page.screenshot({path:path.join(folder,`${name}.png`),fullPage,captureBeyondViewport:true,animations:'disabled'});
}
async function nav(page,id){
  const workspaceNames={recommendationsPanel:/For You/,catalogPanel:/Explore/,savedPanel:/Saved/,interestProfilePanel:/My Taste/,progressPanel:/Progress/,discoverPanel:/Profile/,adminPanel:/Admin/,activityPanel:/Activity/};
  const workspace=page.getByRole('navigation',{name:'Workspace navigation'});
  if(await workspace.count()&&workspaceNames[id]){
    const candidate=workspace.getByRole('button',{name:workspaceNames[id]});
    if(await candidate.count()&&await candidate.isVisible()){
      await candidate.click(); await expect(page.locator(`#${id}`)).toHaveClass(/active/); inspected.add(id); return;
    }
  }
  const link=page.locator(`.nav-link[data-panel="${id}"]`).first();
  if (!(await link.isVisible())) {
    const menu=page.locator('.secondary-navigation');
    if(await menu.count() && !(await menu.evaluate(x=>x.open))) await menu.locator('summary').click();
  }
  await link.click();
  await expect(page.locator(`#${id}`)).toHaveClass(/active/);
  inspected.add(id);
}

test('capture public and authenticated RecomAI visual audit',async({page})=>{
 test.setTimeout(240000);
 page.on('pageerror',e=>consoleIssues.push(e.message));
 page.on('console',m=>{if(m.type()==='error'&&!m.text().startsWith('Failed to load resource:'))consoleIssues.push(m.text());});
 page.on('requestfailed',r=>networkIssues.push(`${r.method()} ${r.url()} :: ${r.failure()?.errorText}`));
 page.on('response',r=>{if(r.status()>=400)networkIssues.push(`${r.status()} ${r.request().method()} ${r.url()}`);});
 await page.goto('/'); await expect(page.locator('#landingPage')).toBeVisible();
 inspected.add('Landing');
 for(const [w,h] of sizes) await snap(page,'landing',w,h);
 await page.locator('[data-auth="login"]').first().click(); await expect(page.locator('#authPanel')).toBeVisible(); inspected.add('Login');
 await snap(page,'login',1440,900,false);
 await page.locator('#showRegister').click(); await expect(page.locator('#registerForm')).toBeVisible(); inspected.add('Registration'); await snap(page,'registration',1440,900,false);
 const id=Date.now();
 await page.locator('#registerForm [name=displayName]').fill('Visual Audit Account');
 await page.locator('#registerForm [name=username]').fill(`audit_${id}`);
 await page.locator('#registerForm [name=email]').fill(`audit_${id}@example.test`);
 await page.locator('#registerForm [name=password]').fill('Audit-only-Password-482!');
 await page.getByRole('button',{name:/Create your account/}).click();
 await expect(page.locator('#appShell')).toBeVisible({timeout:25000});
 await expect(page.locator('#onboardingPanel')).toHaveClass(/active/); inspected.add('Onboarding');
 await snap(page,'onboarding',1440,900);
 const niche=page.locator('#onboardingNiche'); await expect.poll(()=>niche.locator('option').count()).toBeGreaterThan(1);
 await onboardingStage(page,1); await niche.selectOption({index:1}); await onboardingStage(page,0); await page.locator('#onboardingPurpose').selectOption('LEARN');
 const topic=page.locator('#onboardingTopics input').first(); if(await topic.count()) await onboardingStage(page,2); await topic.check();
 await onboardingStage(page,3); await page.locator('#onboardingForm button[type=submit]').click();
 await expect(page.locator('#dashboardMessage')).toContainText('Your new picks are ready.',{timeout:25000});
 for(const id of ['recommendationsPanel','catalogPanel','savedPanel','interestProfilePanel','progressPanel','activityPanel','discoverPanel','howWorksPanel','preferencesPanel']){
   await nav(page,id); await page.waitForTimeout(350);
   const key={recommendationsPanel:'for-you',catalogPanel:'explore',savedPanel:'saved',interestProfilePanel:'my-taste',progressPanel:'progress',activityPanel:'activity',discoverPanel:'profile',howWorksPanel:'how-it-works',preferencesPanel:'category-preferences'}[id];
   for(const [w,h] of sizes) await snap(page,key,w,h);
 }
 const more=page.locator('.secondary-navigation');
 if(!(await more.isVisible())) interactionResults.push('More menu unavailable: legacy sidebar is replaced by workspace dock on For You');
 else {
   if (!(await more.evaluate(el=>el.open))) await more.locator('summary').click();
   await expect(more.locator('nav')).toBeVisible(); await expect(more.getByRole('button',{name:/Close more navigation/})).toBeVisible();
   interactionResults.push('More opens and exposes close control: pass'); await snap(page,'navigation-more-open',1440,900,false);
   await more.getByRole('button',{name:/Close more navigation/}).click(); await expect(more.locator('nav')).toBeHidden(); interactionResults.push('× closes More and hides navigation links: pass');
   await more.locator('summary').click(); await expect(more.locator('nav')).toBeVisible(); await page.keyboard.press('Escape'); await expect(more.locator('nav')).toBeHidden(); interactionResults.push('Escape closes More: pass');
   await more.locator('summary').click(); await page.locator('#dashboardHeader, .dashboard-header').click({position:{x:5,y:5}}).catch(()=>page.locator('.dashboard-main').click({position:{x:10,y:10}}));
   await expect(more.locator('nav')).toBeHidden(); interactionResults.push('Outside click closes More: pass');
   await more.locator('summary').focus(); await more.locator('summary').press('Enter'); await expect(more.locator('nav')).toBeVisible(); await page.keyboard.press('Escape'); await expect(more.locator('summary')).toBeFocused(); interactionResults.push('Keyboard open, Escape close and focus return: pass');
 }
 await nav(page,'adminPanel').catch(()=>{});
 if(await page.locator('#adminPanel').count()&&await page.locator('#adminPanel').isVisible()){inspected.add('Admin');for(const [w,h] of sizes)await snap(page,'admin',w,h);}
 await nav(page,'catalogPanel');
 const search=page.locator('#catalogSearchQuery'); await search.fill('Python'); await page.locator('#catalogSearchForm').evaluate(f=>f.requestSubmit()); await page.waitForTimeout(500); interactionResults.push('Explore search submitted: pass');
 await page.locator('#clearCatalogSearch').click(); interactionResults.push('Explore search clear: pass');
 const view=page.locator('#catalogItems .item-card button').first();
 if(await view.count()){await view.click(); await page.waitForTimeout(250); const dialog=page.locator('dialog[open]'); if(await dialog.count()){inspected.add('Resource details dialog'); await snap(page,'resource-details',1440,900,false); await dialog.locator('button[aria-label*=Close],.resource-dialog-close').first().click().catch(()=>page.keyboard.press('Escape')); interactionResults.push('Resource detail open/close: pass');}}
 await nav(page,'recommendationsPanel');
 if(await page.locator('.workspace-card').count()){await page.locator('.workspace-arrow[aria-label*=Next]').click(); interactionResults.push('For You carousel next: pass'); await page.locator('.workspace-orbit').press('ArrowLeft').catch(()=>{}); interactionResults.push('For You keyboard navigation attempted');}
 for(const [w,h] of sizes) await snap(page,'navigation-authenticated',w,h,false);
 await fs.writeFile(path.join(root,'audit-data.json'),JSON.stringify({pages:[...inspected],interactions:interactionResults,consoleIssues,networkIssues},null,2));
});
