import {spawnSync} from 'node:child_process';
export async function accountFixture(page,admin=false){
 if(!/^recomai-e2e-/.test(process.env.E2E_COMPOSE_PROJECT||''))throw new Error('Account fixtures require the isolated E2E runner');
 const suffix=Date.now()+'_'+Math.floor(Math.random()*100000),username='auditc_'+suffix,email=username+'@example.test',password='Account-Audit-482!';
 const response=await page.request.post('/api/auth/register',{data:{username,email,password,displayName:admin?'Admin audit fixture':'Account audit fixture'}});if(response.status()!==201)throw new Error('Isolated account registration failed');
 const niches=await (await page.request.get('/api/discovery/niches')).json();await page.request.post('/api/users/me/onboarding',{data:{purpose:'LEARN',categoryId:niches[0].categoryId,interestIds:[niches[0].topics[0].interestId]}});
 if(admin){
  if(!/^recomai-e2e-/.test(process.env.E2E_COMPOSE_PROJECT||'')||!/^auditc_\d+_\d+$/.test(username))throw new Error('Admin fixture requires the isolated E2E project');
  const result=spawnSync('docker',['compose','-p',process.env.E2E_COMPOSE_PROJECT,'exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" "$MYSQL_DATABASE"'],{input:`UPDATE users SET role='ADMIN' WHERE username='${username}'; SELECT ROW_COUNT();\n`,encoding:'utf8'});
  if(result.status!==0||!result.stdout.trim().endsWith('1'))throw new Error('Could not prepare isolated admin fixture');
  await page.request.post('/api/auth/logout');if(!(await page.request.post('/api/auth/login',{data:{email,password}})).ok())throw new Error('Admin fixture sign-in failed');
 }
 return{username,email,password,niches};
}
