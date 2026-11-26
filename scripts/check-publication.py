"""Check the proposed Git tree for publication hazards and broken Markdown asset links."""
from pathlib import Path
import json,re,subprocess,sys
root=Path(__file__).resolve().parents[1]
def git(*args):return subprocess.check_output(['git',*args],cwd=root)
paths=git('ls-files','--cached').decode().splitlines();known=set(paths);issues=[];checked=0
for name in paths:
    if re.search(r'(^|/)(node_modules|target|raw|private|restricted|\.git)(/|$)|(^|/)\.env$|\.(zip|dump|sqlite|vhdx)$',name):issues.append([name,'private/generated path'])
    body=git('show',':'+name)
    if re.search(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|\bgh[pousr]_[A-Za-z0-9]{30,}|\bgithub_pat_[A-Za-z0-9_]{40,}',body):issues.append([name,'credential signature'])
    if b'C:\\Users\\' in body or (b'C:' + b'/Users/') in body:issues.append([name,'machine-specific user path'])
    if name.endswith('.md'):
        text=re.sub(r'```[\s\S]*?```','',body.decode('utf8'))
        for md,html in re.findall(r'\]\(([^)]+)\)|src="([^"]+)"',text):
            target=md or html
            if re.match(r'^[a-z]+:|^#',target):continue
            target=target.split('#')[0].replace('%20',' ')
            resolved=(root/Path(name).parent/target).resolve()
            try:relative=resolved.relative_to(root).as_posix()
            except ValueError:issues.append([name,'link outside repository']);continue
            if relative not in known and not any(p.startswith(relative.rstrip('/')+'/') for p in paths):issues.append([name,'missing tracked link: '+target])
            checked+=1
summary={'trackedFiles':len(paths),'relativeLinksChecked':checked,'issues':issues}
print(json.dumps(summary,indent=2));sys.exit(bool(issues))
