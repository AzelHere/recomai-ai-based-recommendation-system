import {test,expect} from '@playwright/test';

test('More menu behavior at 1366 and 1440 desktop widths',async({page})=>{
 test.setTimeout(90000);
 await page.goto('/'); await page.locator('[data-auth="register"]').first().click();
 const id=Date.now();
 await page.locator('#registerForm [name=displayName]').fill('Menu verification');
 await page.locator('#registerForm [name=username]').fill(`menu_${id}`);
 await page.locator('#registerForm [name=email]').fill(`menu_${id}@example.test`);
 await page.locator('#registerForm [name=password]').fill('Menu-test-password-482!');
 await page.getByRole('button',{name:/Create your account/}).click();
 await expect(page.locator('#appShell')).toBeVisible({timeout:25000});
 const menu=page.locator('.secondary-navigation'); const trigger=menu.locator('summary'); const links=menu.locator('nav');
 const observations=[];
 for(const [width,height] of [[1366,768],[1440,900]]){
  await page.setViewportSize({width,height});
  await page.screenshot({path:`design-audit/before-more-${width}x${height}.png`,fullPage:true});
  await trigger.click();
  const state=await menu.evaluate(e=>({tag:e.tagName,open:e.open,display:getComputedStyle(e.querySelector('nav')).display,visibility:getComputedStyle(e.querySelector('nav')).visibility,rect:e.querySelector('nav').getBoundingClientRect().toJSON(),expanded:e.querySelector('summary').getAttribute('aria-expanded')}));
  observations.push({width,opened:await links.isVisible(),state});
  await expect(links).toBeVisible(); await expect(menu.getByRole('button',{name:'Category preferences'})).toBeVisible();
  await menu.getByRole('button',{name:/Close more navigation/}).click(); await expect(links).toBeHidden(); await expect(trigger).toBeFocused();
  await trigger.press('Enter'); await expect(links).toBeVisible(); await page.keyboard.press('Escape'); await expect(links).toBeHidden(); await expect(trigger).toBeFocused();
  await trigger.press('Enter'); await expect(links).toBeVisible(); await page.locator('.dashboard-header').click({position:{x:10,y:10}}); await expect(links).toBeHidden();
  await trigger.press('Enter'); await expect(links).toBeVisible(); await trigger.press('Enter'); await expect(links).toBeHidden();
  await trigger.press('Enter'); await expect(links).toBeVisible(); await trigger.press('Enter'); await expect(links).toBeHidden();
  await page.screenshot({path:`design-audit/after-more-${width}x${height}.png`,fullPage:true});
 }
 console.log('MORE_MENU_OBSERVATIONS='+JSON.stringify(observations));
});
