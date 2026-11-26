export function installCatalogIngestion(target, api, refreshCatalog) {
    const section=document.createElement('section');section.className='account-surface';
    section.innerHTML='<p class="eyebrow">TRACEABLE EDUCATIONAL DATA</p><h3>Verified-source imports</h3><p>Import a reviewed educational-resources.json file. Existing resources and manual edits are kept. Different editions with the same title require review. Each batch is transactional; completed batches remain if a later batch fails, and retries are safe.</p><label>Source metadata JSON<input id="sourceMetadataFile" type="file" accept="application/json,.json"></label><div class="form-actions"><button id="previewSourceImport" type="button" class="button button-secondary" disabled>Preview source import</button><button id="commitSourceImport" type="button" class="button button-primary" disabled>Import reviewed records</button></div><p id="sourceImportStatus" role="status" aria-live="polite">Choose a source file to preview.</p><div id="sourceImportPreview"></div><div class="form-actions"><button id="refreshSourceHistory" type="button" class="button button-secondary">Refresh source history</button><button id="checkSourceUrls" type="button" class="button button-secondary">Check up to 10 source URLs</button></div><div id="sourceRegistrySummary" aria-live="polite"></div>';
    target.prepend(section);
    const find=id=>section.querySelector('#'+id), status=message=>{find('sourceImportStatus').textContent=message;};
    let records=[],previews=[],version=0,busy=false;
    const element=(tag,text)=>{const e=document.createElement(tag);e.textContent=text;return e;};
    async function overview(){
        try{const data=await api('/admin/ingestion');if(!section.isConnected)return;const summary=find('sourceRegistrySummary');summary.replaceChildren(element('h4','Catalog provenance'));
            for(const row of data.statistics)summary.append(element('p',row.name+' · '+row.resources+' imported resources · '+(row.verified||0)+' HTTP verified · '+row.license));
            summary.append(element('p',data.batches.length+' recent import batches · '+data.reviews.length+' pending duplicate reviews shown (up to 100). External research learners are excluded from platform analytics.'));
            const history=document.createElement('details');history.append(element('summary','Import history and duplicate review'));
            for(const row of data.batches)history.append(element('p','Batch '+row.batch_id+' · '+row.imported_count+' imported · '+row.skipped_count+' kept · '+row.review_count+' review · '+row.created_at));
            for(const row of data.reviews)history.append(element('p',row.title+' · '+row.source_id+' / '+row.external_id+' · '+row.reason));summary.append(history);
            summary.append(element('p','URL maintenance: '+(data.maintenance.running?'running':data.maintenance.last.state)+' · scheduled checks '+(data.maintenance.scheduledEnabled?'enabled':'disabled')));
        }catch(error){status(error.message);}
    }
    function controls(){find('sourceMetadataFile').disabled=busy;find('previewSourceImport').disabled=busy||!records.length;find('commitSourceImport').disabled=busy||!previews.length||previews.some(p=>!p.canImport);}
    find('sourceMetadataFile').onchange=async()=>{
        const current=++version;records=[];previews=[];find('sourceImportPreview').replaceChildren();controls();
        try{const file=find('sourceMetadataFile').files[0];if(!file)return;if(file.size>10_000_000)throw Error('Source JSON must be at most 10 MB.');const parsed=JSON.parse(await file.text());if(current!==version)return;if(!Array.isArray(parsed)||!parsed.length||parsed.length>3000)throw Error('Supply an array of 1–3000 reviewed source records.');records=parsed;status(records.length+' records loaded. Preview before importing.');}catch(error){status(error.message);}controls();
    };
    find('previewSourceImport').onclick=async()=>{
        if(busy)return;busy=true;previews=[];controls();status('Validating source records…');
        try{for(let offset=0;offset<records.length;offset+=250)previews.push(await api('/admin/ingestion/preview',{method:'POST',body:JSON.stringify({resources:records.slice(offset,offset+250)})}));
            const totals=previews.reduce((s,p)=>({imported:s.imported+p.imported,existing:s.existing+p.existing,review:s.review+p.review}),{imported:0,existing:0,review:0});
            status(totals.imported+' new · '+totals.existing+' existing · '+totals.review+' review · '+previews.length+' batches. '+(previews.every(p=>p.canImport)?'Ready to import the reviewed records.':'Fix rejected records before importing.'));
            const output=find('sourceImportPreview');output.replaceChildren();for(const p of previews)for(const row of p.rows.filter(r=>r.decision==='REJECT'||r.decision==='REVIEW').slice(0,20))output.append(element('p',row.title+' · '+row.decision+' · '+row.reason));
        }catch(error){previews=[];status(error.message);}finally{busy=false;controls();}
    };
    find('commitSourceImport').onclick=async()=>{
        if(busy||!previews.length)return;busy=true;controls();let imported=0,kept=0,review=0;
        try{for(let i=0;i<previews.length;i++){status('Importing batch '+(i+1)+' of '+previews.length+'…');const result=await api('/admin/ingestion/commit',{method:'POST',body:JSON.stringify({resources:records.slice(i*250,(i+1)*250),expectedSha256:previews[i].sha256})});imported+=result.imported;kept+=result.skipped;review+=result.review;}
            status('Import finished: '+imported+' added · '+kept+' kept · '+review+' need review.');await overview();await refreshCatalog();
        }catch(error){status(imported+' added before failure. '+error.message+' Retry safely after resolving the error.');}finally{busy=false;controls();}
    };
    find('refreshSourceHistory').onclick=overview;
    find('checkSourceUrls').onclick=async()=>{const button=find('checkSourceUrls');button.disabled=true;try{const result=await api('/admin/ingestion/check-urls',{method:'POST'});status('URL checks: '+result.state+'. Refresh source history to see progress. Checks never automatically pause resources.');await overview();}catch(error){status(error.message);}finally{button.disabled=false;}};
    find('refreshSourceHistory').addEventListener('click',()=>status('Source history refreshed.'));
    // Load only when the administrator asks; no external requests run on page navigation.
}
