import { initializePersonalization, initializeTaste, buildChoices, syncWizard, wizardStep, loadTaste } from "./modules/personalization.js";
import { api } from "./modules/api-client.js";
import { installCatalogIngestion } from "./modules/catalog-ingestion.js";
import { initializeThemeToggle } from "./modules/theme-toggle.js";
import { createResourceArtwork } from "./modules/resource-artwork.js";
import { initializeRisingLines } from "./modules/rising-lines.js";
import { initializeLandingDemo } from "./modules/landing-demo.js";
import { initializeLearningConstellation } from "./modules/learning-constellation.js";
// Phase 3 state is scoped to the signed-in workspace; response guards also survive same-user re-login.
let workspaceEpoch=0,journeyRequestVersion=0,journeyPage=1,journeyPageCount=0,selectedJourneyId=null;
let journeyCreateRequestId=null;
function sameWorkspace(userId,epoch){return currentUser?.userId===userId&&workspaceEpoch===epoch;}
function mergeResourceMetadata(rows){const merged=new Map(items.map(item=>[Number(item.itemId),item]));rows.forEach(item=>merged.set(Number(item.itemId),item));items=[...merged.values()];}
async function ensureResourceMetadata(ids){
    const epoch=workspaceEpoch,userId=currentUser?.userId;
    const missing=[...new Set(ids.filter(Boolean).map(Number))].filter(id=>!items.some(item=>Number(item.itemId)===id));
    for(let index=0;index<missing.length;index+=100){const rows=await api('/items/batch?ids='+missing.slice(index,index+100).join(','));if(!sameWorkspace(userId,epoch))return;mergeResourceMetadata(rows||[]);}
}
function workspaceButton(label,action,primary=false){const button=createElement('button','button '+(primary?'button-primary':'button-secondary'),label);button.type='button';button.onclick=action;return button;}

function installPhaseThreeControls(){
    const shelves=document.querySelector('.library-shelves');
    shelves.appendChild(workspaceButton('Rename collection',()=>{
        const collection=userCollections.find(row=>String(row.collectionId)===document.getElementById('collectionSelect').value);if(!collection)return;
        const dialog=document.getElementById('collectionRenameDialog');dialog.dataset.collectionId=String(collection.collectionId);dialog.dataset.expectedName=collection.name;
        document.getElementById('collectionRenameName').value=collection.name;document.getElementById('collectionRenameStatus').textContent='';dialog.showModal();document.getElementById('collectionRenameName').focus();
    }));shelves.lastElementChild.id='renameCollection';shelves.lastElementChild.hidden=true;
    const pager=createElement('div','command-pagination');pager.innerHTML='<button id="libraryPrevious" class="button button-secondary" type="button">Previous library page</button><p id="libraryPageSummary" role="status"></p><button id="libraryNext" class="button button-secondary" type="button">Next library page</button>';document.querySelector('.library-main').appendChild(pager);
    document.getElementById('libraryPrevious').onclick=()=>{if(libraryPage>1){libraryPage--;loadSavedItems();}};document.getElementById('libraryNext').onclick=()=>{if(libraryPage<libraryTotalPages){libraryPage++;loadSavedItems();}};
    const more=workspaceButton('Load more activity',()=>loadActivity(true));more.id='activityMore';more.hidden=true;document.getElementById('activityList').after(more);
    const filter=document.getElementById('activityFilter');for(const type of ['VIEW','CLICK','READ_PROGRESS','SAVE','PURCHASE','LIKE','DISLIKE','HIDE','SHARE','SKIP','MORE_LIKE','SEARCH','COMPLETE'])filter.appendChild(new Option(activityLabel(type),type));
    const dialog=document.getElementById('collectionRenameDialog');
    dialog.querySelector('[data-cancel-rename]').onclick=()=>dialog.close();dialog.addEventListener('close',()=>document.getElementById('renameCollection').focus());
    dialog.addEventListener('cancel',event=>{if(dialog.querySelector('[type=submit]').disabled)event.preventDefault();});
    document.getElementById('collectionRenameForm').onsubmit=async event=>{
        event.preventDefault();const form=event.currentTarget,button=form.querySelector('[type=submit]'),status=document.getElementById('collectionRenameStatus'),name=document.getElementById('collectionRenameName').value.trim();
        if(button.disabled)return;if(!name||name.length>80){status.textContent='Enter a collection name between 1 and 80 characters.';return;}
        const epoch=workspaceEpoch,userId=currentUser.userId;form.querySelectorAll('input,button').forEach(control=>control.disabled=true);status.textContent='Saving collection name…';
        try{await api('/users/me/collections/'+dialog.dataset.collectionId,{method:'PATCH',body:JSON.stringify({name,expectedName:dialog.dataset.expectedName})});if(!sameWorkspace(userId,epoch))return;dialog.close();await loadSavedItems();showToast('Collection renamed.');}
        catch(error){if(sameWorkspace(userId,epoch)){status.textContent=error.message;handleError(error);}}
        finally{form.querySelectorAll('input,button').forEach(control=>control.disabled=false);}
    };
    document.getElementById('generateRecommendations').parentElement.appendChild(workspaceButton('Your journeys',()=>showPanel('journeysPanel')));
    const progress=createElement('section','account-surface journey-progress-summary');progress.id='journeyProgressSummary';progress.setAttribute('aria-label','Recent learning journey');document.querySelector('#progressPanel .progress-overview').after(progress);
    document.querySelector('#interestProfilePanel .page-title').after(createElement('p','privacy-note','Your preferences shape new recommendations and journeys. Existing journey steps are preserved when your interests change.'));
    const topicLabel=createElement('label','','Topic focus (optional)');const topicField=document.createElement('input');topicField.name='topic';topicField.maxLength=120;topicField.placeholder='e.g. Java or academic communication';topicLabel.append(topicField);document.getElementById('journeyForm').prepend(topicLabel);
    document.getElementById('journeyForm').addEventListener('input',()=>{journeyCreateRequestId=null;document.getElementById('journeyForm').dataset.dirty='true';});
    document.getElementById('journeyForm').onsubmit=createJourney;
    document.getElementById('journeysRefresh').onclick=()=>loadJourneys();
    document.getElementById('journeysPrevious').onclick=()=>{if(journeyPage>1){journeyPage--;loadJourneys();}};document.getElementById('journeysNext').onclick=()=>{if(journeyPage<journeyPageCount){journeyPage++;loadJourneys();}};
    const itemForm=document.getElementById('itemForm');const editStatus=createElement('p','');editStatus.id='itemEditStatus';editStatus.setAttribute('role','status');itemForm.appendChild(editStatus);
    itemForm.appendChild(workspaceButton('Reload current resource',async()=>{const id=itemForm.elements.itemId.value;if(!id)return;if(adminItemDirty&&!confirm('Discard this draft and load the current server resource?'))return;try{const rows=await api('/admin/items');const item=rows.find(row=>String(row.itemId)===id);if(item){adminItemDirty=false;editItem(item);}}catch(error){editStatus.textContent=error.message;handleError(error);}}));
    document.getElementById('adminRecommendationSettings').appendChild(workspaceButton('Reload saved configuration',()=>{if(adminSettingsDirty&&!confirm('Discard your draft and reload saved configuration?'))return;adminSettingsDirty=false;loadAdminSettings(true);}));
}

async function createJourney(event){
    event.preventDefault();const form=event.currentTarget,button=form.querySelector('[type=submit]'),status=document.getElementById('journeyStatus');if(button.disabled)return;
    const data=new FormData(form),epoch=workspaceEpoch,userId=currentUser.userId;
    journeyCreateRequestId ||= crypto.randomUUID();
    const body={title:String(data.get('title')).trim(),goal:data.get('goal'),categoryId:Number(data.get('categoryId')),steps:Number(data.get('steps')),topic:String(data.get('topic')||'').trim()||null,requestId:journeyCreateRequestId};
    if(!body.title||body.title.length>150||!body.categoryId){status.textContent='Give your journey a title and choose a learning area.';return;}
    form.querySelectorAll('input,select,button').forEach(control=>control.disabled=true);status.textContent='Building your suggested sequence from available resources…';
    try{const result=await api('/users/me/journeys',{method:'POST',body:JSON.stringify(body)});if(!sameWorkspace(userId,epoch))return;journeyCreateRequestId=null;journeyPage=1;form.reset();delete form.dataset.dirty;document.querySelector('.journey-create').open=false;await loadJourneys(result.journey.journeyId);status.textContent='Journey saved. Steps are suggestions, not verified prerequisites.';}
    catch(error){if(sameWorkspace(userId,epoch)){status.textContent=error.message;handleError(error);}}
    finally{form.querySelectorAll('input,select,button').forEach(control=>control.disabled=false);}
}
async function loadJourneys(preferredId){
    if(!currentUser)return;const version=++journeyRequestVersion,epoch=workspaceEpoch,userId=currentUser.userId,status=document.getElementById('journeyStatus');status.textContent='Loading your journeys…';
    try{
        const [result,areas]=await Promise.all([api('/users/me/journeys?page='+journeyPage+'&pageSize=10'),api('/discovery/niches')]);if(version!==journeyRequestVersion||!sameWorkspace(userId,epoch))return;
        const select=document.getElementById('journeyCategory'),chosen=select.value;select.replaceChildren(new Option('Choose an area',''));areas.forEach(area=>select.appendChild(new Option(area.name,String(area.categoryId))));if([...select.options].some(option=>option.value===chosen))select.value=chosen;
        journeyPageCount=result.totalPages;document.getElementById('journeysPrevious').disabled=journeyPage<=1;document.getElementById('journeysNext').disabled=journeyPage>=journeyPageCount;document.getElementById('journeysPageSummary').textContent='Page '+journeyPage+' · '+result.totalItems+' saved journeys';
        const list=document.getElementById('journeyList');list.replaceChildren();document.getElementById('journeysEmpty').hidden=result.items.length>0;
        for(const header of result.items){const button=workspaceButton(header.title,()=>openJourney(header.journeyId));button.dataset.journeyId=String(header.journeyId);button.classList.add('journey-list-entry');button.appendChild(createElement('small','',header.goal.replaceAll('_',' ').toLowerCase()+' · '+header.status.toLowerCase()));list.appendChild(button);}
        if(result.items.length&&!document.getElementById('journeyForm').dataset.dirty)document.querySelector('.journey-create').open=false;
        const id=typeof preferredId==='number'?preferredId:result.items.some(row=>row.journeyId===selectedJourneyId)?selectedJourneyId:result.items[0]?.journeyId;
        status.textContent='';if(id)await openJourney(id);else document.getElementById('journeyDetail').replaceChildren();
    }catch(error){if(version===journeyRequestVersion&&sameWorkspace(userId,epoch)){status.textContent='Could not load journeys. '+error.message;handleError(error);}}
}
async function openJourney(id){
    const version=++journeyRequestVersion,epoch=workspaceEpoch,userId=currentUser?.userId;selectedJourneyId=id;
    try{const journey=await api('/users/me/journeys/'+id);if(version!==journeyRequestVersion||!sameWorkspace(userId,epoch))return;renderJourney(journey);document.querySelectorAll('.journey-list-entry').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.journeyId===String(journey.journey.journeyId))));}
    catch(error){if(version===journeyRequestVersion&&sameWorkspace(userId,epoch)){document.getElementById('journeyStatus').textContent=error.message;handleError(error);}}
}
function renderJourney(data){
    const container=document.getElementById('journeyDetail');container.replaceChildren();const header=data.journey;
    const summary=createElement('header','journey-summary');summary.append(createElement('p','eyebrow','SUGGESTED LEARNING SEQUENCE'),createElement('h3','',header.title),createElement('p','',header.goal.replaceAll('_',' ').toLowerCase()+' · '+categoryName(header.categoryId)+' · '+header.status.toLowerCase()),createElement('strong','journey-completion',data.completedSteps+' of '+data.totalSteps+' steps user-marked complete · '+data.completionPercent+'%'),createElement('p','privacy-note','Completion is your own record, not verification by an external course provider. Starting or opening a resource does not mark it complete. Progress is shared with the same resource elsewhere in your workspace.'));
    const meter=document.createElement('progress');meter.max=Math.max(1,data.totalSteps);meter.value=data.completedSteps;meter.setAttribute('aria-label','User-recorded journey completion');summary.appendChild(meter);container.appendChild(summary);
    const roadmap=createElement('ol','journey-roadmap');
    for(const step of data.steps){
        const row=createElement('li','journey-step');row.dataset.stepId=String(step.stepId);if(step.stepId===data.currentStepId)row.dataset.current='true';
        row.append(createElement('p','eyebrow','STEP '+step.position+' · '+(step.stepId===data.currentStepId?'CURRENT · ':'')+step.status.replaceAll('_',' ')),createElement('h4','',step.title),createElement('p','',step.rationale));
        if(!step.available)row.appendChild(createElement('p','privacy-note','Unavailable resource. Your reference and any completed progress are preserved.'));
        const actions=createElement('div','journey-actions');
        if(step.available&&step.resource)actions.appendChild(workspaceButton('View resource',()=>openResourceDetails(step.resource)));
        if(header.status==='ACTIVE'&&step.available){
            if(step.status==='NOT_STARTED')actions.appendChild(workspaceButton('Start step',()=>mutateJourney(data,'/steps/'+step.stepId,'PATCH',{status:'IN_PROGRESS'})));
            actions.appendChild(workspaceButton(step.status==='COMPLETED'?'Reopen step':'Mark step complete',()=>mutateJourney(data,'/steps/'+step.stepId,'PATCH',{status:step.status==='COMPLETED'?'IN_PROGRESS':'COMPLETED'}),step.status!=='COMPLETED'));
        }
        row.appendChild(actions);roadmap.appendChild(row);
    }
    container.appendChild(roadmap);const manage=createElement('div','journey-actions');
    if(header.status==='ACTIVE'){
        if(data.steps.some(step=>!step.available&&step.status!=='COMPLETED'))manage.appendChild(workspaceButton('Replace unavailable steps',()=>mutateJourney(data,'/replace-unavailable','POST')));
        manage.appendChild(workspaceButton('Archive journey',()=>{if(confirm('Archive this journey? Its steps and progress will be kept.'))mutateJourney(data,'/archive','POST');}));
    }
    manage.appendChild(workspaceButton('Delete journey',()=>{if(confirm('Delete this journey? Your separate resource progress will remain.'))mutateJourney(data,'','DELETE');}));container.appendChild(manage);
    if(data.replacements.length)container.appendChild(createElement('p','privacy-note',data.replacements.length+' recent replacement records retained. Completed steps were not replaced.'));
    container.appendChild(createElement('p','privacy-note','This plan keeps its generation-time preferences when your interests change. Create a new journey for a different direction. Clearing activity also clears the progress events used by these steps.'));
}
async function mutateJourney(data,path,method,body){
    const epoch=workspaceEpoch,userId=currentUser.userId,version=++journeyRequestVersion;const container=document.getElementById('journeyDetail');container.querySelectorAll('button').forEach(button=>button.disabled=true);
    try{const updated=await api('/users/me/journeys/'+data.journey.journeyId+path,{method,...(body?{body:JSON.stringify(body)}:{})});if(!sameWorkspace(userId,epoch)||version!==journeyRequestVersion)return;if(method==='DELETE'){selectedJourneyId=null;await loadJourneys();}else renderJourney(updated);document.getElementById('journeyStatus').textContent=method==='DELETE'?'Journey deleted.':'Journey updated from your recorded action.';}
    catch(error){if(sameWorkspace(userId,epoch)&&version===journeyRequestVersion){document.getElementById('journeyStatus').textContent=error.message;handleError(error);}}
    finally{if(sameWorkspace(userId,epoch)&&version===journeyRequestVersion)container.querySelectorAll('button').forEach(button=>button.disabled=false);}
}
async function loadJourneyProgress(){
    const epoch=workspaceEpoch,userId=currentUser?.userId,version=progressVersion,container=document.getElementById('journeyProgressSummary');if(!userId)return;
    try{const page=await api('/users/me/journeys?pageSize=5');const recent=page.items.find(row=>row.status==='ACTIVE');const journey=recent?await api('/users/me/journeys/'+recent.journeyId):null;if(!sameWorkspace(userId,epoch)||version!==progressVersion)return;container.replaceChildren(createElement('p','eyebrow','RECENT LEARNING JOURNEY'),createElement('h3','',journey?journey.journey.title:'Keep a learning path to return to.'),createElement('p','',journey?journey.completedSteps+' of '+journey.totalSteps+' steps user-marked complete.':'Create a suggested sequence from your current profile and an area you choose.'));container.appendChild(workspaceButton(journey?'Continue journey':'Open journeys',()=>{selectedJourneyId=journey?.journey.journeyId||null;showPanel('journeysPanel');}));}
    catch(error){if(sameWorkspace(userId,epoch)&&version===progressVersion){container.replaceChildren(createElement('p','','Your journey summary could not be loaded.'),workspaceButton('Retry journey summary',loadJourneyProgress));handleError(error);}}
}
const authPanel = document.getElementById("authPanel");
const appShell = document.getElementById("appShell");
const landingPage = document.getElementById("landingPage");
const authMessage = document.getElementById("authMessage");
const dashboardMessage = document.getElementById("dashboardMessage");
const panels = Array.from(document.querySelectorAll(".content-panel"));
const navLinks = Array.from(document.querySelectorAll(".nav-link"));
const secondaryNavigation = document.querySelector(".secondary-navigation");
const secondaryNavigationClose = document.querySelector(".secondary-nav-close");
secondaryNavigationClose?.addEventListener("click", function () {
    secondaryNavigation.open = false;
    secondaryNavigation.querySelector("summary").focus();
});
document.addEventListener("click", function (event) {
    if (secondaryNavigation?.open && !secondaryNavigation.contains(event.target)) secondaryNavigation.open = false;
});
secondaryNavigation?.addEventListener("toggle", function () {
    secondaryNavigation.querySelector("summary")?.setAttribute("aria-expanded", String(secondaryNavigation.open));
});
document.addEventListener("keydown", function (event) {
    if (event.key === "Escape" && secondaryNavigation?.open) {
        secondaryNavigation.open = false;
        secondaryNavigation.querySelector("summary").focus();
    }
});

let currentUser = null;
let categories = [];
let items = [];
let catalogTopicNames = [];
let niches = [];
let feedbackByItemId = new Map();
let feedbackLoaded = false;
let userCollections = [];
let toastTimer = null;
let previousFocus = null;
let catalogRequestId = 0;
let recommendationImpressionObserver = null;
const sentRecommendationImpressions = new Set();
let authRequestInProgress = false;
let logoutInProgress = false;
let sessionCheckInProgress = false;
let pendingHomepageQuery = null;
let temporaryGoal = sessionStorage.getItem("recomai.temporaryGoal") || "technology";
let requestedLandingDestination = sessionStorage.getItem("recomai.landingDestination") || "";
let onboardingCompleted = false;
let onboardingStatusLoaded = false;
let passwordResetToken = null;
let catalogPage = 1;
const catalogPageSize = 12;

function showToast(message, isError) {
    dashboardMessage.textContent = message;
    dashboardMessage.classList.toggle("error", Boolean(isError));
    dashboardMessage.classList.add("visible");
    window.clearTimeout(toastTimer);
    toastTimer = window.setTimeout(function () {
        dashboardMessage.classList.remove("visible");
    }, 3200);
}

function showAuthMessage(message, isSuccess) {
    authMessage.textContent = message || "";
    authMessage.classList.toggle("success", Boolean(isSuccess));
    document.getElementById("duplicateRegistrationAction").hidden = true;
}

function setAuthRequestState(active) {
    authRequestInProgress = active;
    ["loginForm", "registerForm", "forgotPasswordForm", "resetPasswordForm"].forEach(function (formId) {
        const form = document.getElementById(formId);
        form.setAttribute("aria-busy", String(active));
        form.querySelectorAll("input, button, select, textarea").forEach(function (control) {
            control.disabled = active;
        });
    });
    ["showLogin", "showRegister"].forEach(function (tabId) {
        document.getElementById(tabId).disabled = active;
    });
}

function showSessionCheckFailure(error) {
    authPanel.hidden = true;
    appShell.hidden = true;
    landingPage.hidden = false;
    setLandingSessionControls(false);
    const status = document.getElementById("connectionStatus");
    const message = document.getElementById("connectionStatusMessage");
    message.textContent = error instanceof TypeError
        ? "We couldn't reach Recom AI to check your sign-in. Your account state is unknown. Check your connection and retry."
        : "Recom AI couldn't confirm your sign-in because the server returned an error. Your account state is unknown. Retry in a moment.";
    status.hidden = false;
}

async function restoreSession() {
    const status = document.getElementById("connectionStatus");
    if (sessionCheckInProgress) return;
    sessionCheckInProgress = true;
    const retry = document.getElementById("retrySessionCheck");
    retry.disabled = true;
    status.hidden = true;
    try {
        const profile = await api("/users/me");
        openDashboard(profile);
    } catch (error) {
        if (error && error.status === 401) {
            authPanel.hidden = true;
            appShell.hidden = true;
            landingPage.hidden = false;
            setLandingSessionControls(false);
            status.hidden = true;
            return;
        }
        showSessionCheckFailure(error);
    } finally {
        sessionCheckInProgress = false;
        retry.disabled = false;
    }
}

function handleError(error, target) {
    if (error && error.status === 401 && target !== "auth") {
        if (currentUser) {
            currentUser = null;
            resetLearningWorkspace();
            setLandingSessionControls(false);
            openAuth("login");
            showAuthMessage("Your session has expired. Sign in again to continue.", false);
        }
        return;
    }
    const message = error instanceof TypeError
        ? "Unable to reach the server. Check that it is running and try again."
        : error && error.message ? error.message : "Something went wrong. Please try again.";
    if (target === "auth") {
        showAuthMessage(message, false);
    } else {
        if (document.getElementById("adminPanel")?.classList.contains("active")) document.getElementById("adminStatus").textContent = message;
        showToast(message, true);
    }
}

function showPanel(panelId, fromRoute) {
    if (panelId === "adminPanel" && currentUser?.role !== "ADMIN") panelId = "discoverPanel";
    if (!panels.some(function (panel) { return panel.id === panelId; })) return;
    if (!fromRoute) window.scrollTo({ top: 0, behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth" });
    if (!fromRoute && currentUser && window.location.hash !== "#app/" + panelId) {
        window.history.pushState({ panelId: panelId }, "", "#app/" + panelId);
    }
    panels.forEach(function (panel) {
        panel.classList.toggle("active", panel.id === panelId);
    });
    navLinks.forEach(function (link) {
        const active = link.dataset.panel === panelId;
        link.classList.toggle("active", active);
        if (active) link.setAttribute("aria-current", "page");
        else link.removeAttribute("aria-current");
    });
    if (secondaryNavigation.contains(document.querySelector('.nav-link[data-panel="' + panelId + '"]'))) {
        secondaryNavigation.open = true;
    } else {
        secondaryNavigation.open = false;
    }
    if (panelId === "activityPanel") {
        loadActivity();
    }
    if (panelId === "progressPanel") {
        loadProgress();
    }
    if (panelId === "journeysPanel") loadJourneys();
    if (panelId === "howWorksPanel") loadRecommendationInfo();
    if (panelId === "savedPanel") {
        loadSavedItems();
    }
    if (panelId === "recommendationsPanel") {
        loadRecommendations();
    }
    if (panelId === "onboardingPanel" && onboardingCompleted) loadOnboardingStatus();
    if (panelId === "interestProfilePanel") {
        loadExploration();
        loadInterestProfile();
    }
    if (panelId === "discoverPanel" || panelId === "settingsPanel") loadAccountProfile();
    if (panelId === "adminPanel") {
        loadAdminData();
    }
}

function setLandingSessionControls(authenticated) {
    document.querySelectorAll("#landingPage [data-auth]").forEach(function (control) {
        control.hidden = authenticated;
    });
    document.querySelector("[data-enter-workspace]").hidden = !authenticated;
}

function returnToHome() {
    if (!currentUser) return;
    window.history.pushState({ screen: "home" }, "", "#home");
    appShell.hidden = true;
    landingPage.hidden = false;
    authPanel.hidden = true;
    document.body.classList.remove("dialog-open");
    document.getElementById("heroTitle").focus({ preventScroll: true });
    window.scrollTo({ top: 0, behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth" });
}

function enterWorkspace() {
    if (!currentUser) return openAuth("login");
    landingPage.hidden = true;
    appShell.hidden = false;
    showPanel(onboardingStatusLoaded && !onboardingCompleted ? "onboardingPanel" : "recommendationsPanel");
    if (!onboardingStatusLoaded) loadOnboardingStatus();
}

function syncWorkspaceRoute() {
    if (!currentUser) return;
    if (window.location.hash.startsWith("#app/")) {
        const panelId = window.location.hash.slice("#app/".length);
        if (!panels.some(function (panel) { return panel.id === panelId; })) return;
        landingPage.hidden = true;
        appShell.hidden = false;
        showPanel(panelId, true);
    } else if (!window.location.hash || window.location.hash === "#home") {
        appShell.hidden = true;
        landingPage.hidden = false;
        setLandingSessionControls(true);
    }
}

document.getElementById("returnHome").addEventListener("click", returnToHome);
document.querySelector("[data-enter-workspace]").addEventListener("click", enterWorkspace);

const explorationForm = document.getElementById("explorationForm");
const explorationSlider = document.getElementById("explorationValue");
const explorationButton = explorationForm.querySelector("button");
const explorationStatus = document.getElementById("explorationStatus");
const explorationPath = "/users/me/recommendations/exploration";
let explorationRequest = 0;
let savedExploration = null;

function showExplorationValue() {
    const value = Number(explorationSlider.value);
    const mode = value <= 25 ? "Focused" : value >= 75 ? "Adventurous" : "Balanced";
    document.getElementById("explorationMode").textContent = mode;
    explorationSlider.setAttribute("aria-valuetext", mode + ", " + value + " of 100");
    document.getElementById("explorationHelp").textContent = {
        Focused: "Give more space to resources that match your interests.",
        Balanced: "Mix familiar topics with related and exploratory picks.",
        Adventurous: "Make more room for discoveries outside your usual topics."
    }[mode];
    explorationButton.disabled = savedExploration === value;
}

async function loadExploration() {
    const request = ++explorationRequest;
    savedExploration = null;
    explorationButton.textContent = "Save preference";
    explorationSlider.disabled = explorationButton.disabled = true;
    explorationStatus.textContent = "Loading preference…";
    try {
        const result = await api(explorationPath);
        if (request !== explorationRequest) return;
        savedExploration = result.value;
        explorationSlider.value = result.value;
        explorationSlider.disabled = false;
        showExplorationValue();
        explorationStatus.textContent = "";
    } catch {
        if (request !== explorationRequest) return;
        explorationStatus.textContent = "Couldn’t load your preference.";
        explorationButton.textContent = "Retry";
        explorationButton.disabled = false;
    }
}

explorationSlider.addEventListener("input", () => {
    explorationStatus.textContent = "";
    showExplorationValue();
});
explorationForm.addEventListener("submit", async event => {
    event.preventDefault();
    if (savedExploration === null) return loadExploration();
    const request = ++explorationRequest;
    const value = Number(explorationSlider.value);
    explorationSlider.disabled = explorationButton.disabled = true;
    explorationStatus.textContent = "Saving and refreshing your picks…";
    try {
        await api(explorationPath, { method: "PUT", body: JSON.stringify({ value }) });
        if (request !== explorationRequest) return;
        savedExploration = value;
        explorationStatus.textContent = "Preference saved. Your For You picks are ready.";
    } catch {
        if (request === explorationRequest) explorationStatus.textContent = "Couldn’t confirm the save. Try again.";
    } finally {
        if (request === explorationRequest) {
            explorationSlider.disabled = false;
            showExplorationValue();
        }
    }
});

function openDashboard(profile) {
    if (!profile || !profile.username) {
        showAuthMessage("Your account could not be loaded. Please sign in again.");
        return;
    }
    currentUser = profile;
    onboardingStatusLoaded = false;
    setLandingSessionControls(true);
    const remainOnLanding = window.location.hash === "#home";
    landingPage.hidden = !remainOnLanding;
    document.body.classList.remove("dialog-open");
    authPanel.hidden = true;
    appShell.hidden = remainOnLanding;
    document.querySelector(".admin-nav").hidden = profile.role !== "ADMIN";
    document.getElementById("greeting").textContent = "Welcome back, " + (profile.displayName || profile.username);
    document.getElementById("sidebarName").textContent = profile.displayName || profile.username;
    document.getElementById("sidebarRole").textContent = profile.role === "ADMIN" ? "Administrator" : "Member";
    document.getElementById("profileUsername").textContent = "@" + profile.username;
    document.getElementById("profileEmail").textContent = profile.email;
    document.getElementById("displayNameInput").value = profile.displayName || "";
    const initial = (profile.displayName || profile.username || "R").trim().charAt(0).toUpperCase();
    document.getElementById("sidebarAvatar").textContent = initial;
    document.getElementById("headerAvatar").textContent = initial;
    document.getElementById("todayLabel").textContent = new Intl.DateTimeFormat(undefined, { weekday: "long", month: "long", day: "numeric" }).format(new Date()).toUpperCase();
    document.getElementById("greeting").setAttribute("tabindex", "-1");
    if (!remainOnLanding) document.getElementById("greeting").focus();
    const requestedPanel = requestedLandingDestination || (window.location.hash.startsWith("#app/")
        ? window.location.hash.slice("#app/".length) : "discoverPanel");
    showPanel(requestedPanel === "adminPanel" && profile.role !== "ADMIN" ? "discoverPanel" : requestedPanel, true);
    loadWorkspace();
    loadOnboardingStatus();
}

let catalogMetadataReady = false;
async function loadWorkspace() {
    try {
        const result = await Promise.all([api("/categories"), api("/items/search?pageSize=50"),api('/interests')]);
        categories = Array.isArray(result[0]) ? result[0] : [];
        mergeResourceMetadata(result[1]?.items || []);catalogTopicNames=(result[2]||[]).map(topic=>topic.name);
        catalogMetadataReady = true;
        populateCatalogTopics();
        try {
            const feedback = await api("/users/me/feedback");
            feedbackByItemId = new Map((Array.isArray(feedback) ? feedback : [])
                .map(function (entry) { return [Number(entry.itemId), entry]; }));
            feedbackLoaded = true;
        } catch (feedbackError) {
            feedbackLoaded = false;
            handleError(feedbackError);
        }
        renderCatalog();
        renderCategoryFilters();
        renderPreferences();
        renderFeaturedItems();
        renderAdminCategoryOptions();
        if (document.getElementById("recommendationsPanel").classList.contains("active")) {
            loadRecommendations();
        }
        if (document.getElementById("savedPanel")?.classList.contains("active")) loadSavedItems();
        if (document.getElementById("activityPanel")?.classList.contains("active")) loadActivity();
        if (document.getElementById("progressPanel")?.classList.contains("active")) loadProgress();
    } catch (error) {
        handleError(error);
    }
}

function applyTemporaryGoal() {
    const purposeByGoal = { technology: "LEARN", business: "LEARN", career: "CAREER", health: "IMPROVE_MYSELF", creative: "EXPLORE" };
    const categoryName = { technology: "technology", business: "business & finance", career: "learning & career", health: "health & fitness", creative: "creative & lifestyle" }[temporaryGoal];
    document.getElementById("onboardingPurpose").value = purposeByGoal[temporaryGoal] || "LEARN";
    const category = niches.find(function (niche) { return String(niche.name || "").toLowerCase() === categoryName; });
    if (category) document.getElementById("onboardingNiche").value = String(category.categoryId);
}

let savedOnboarding = null;
async function loadOnboardingStatus() {
    document.querySelectorAll("#onboardingForm select, #onboardingForm button").forEach(control => { control.disabled = true; });
    let personalizationReady = false;
    try {
        const results = await Promise.all([api("/discovery/niches"), api("/users/me/onboarding")]);
        niches = Array.isArray(results[0]) ? results[0] : [];
        savedOnboarding = results[1];
        await renderOnboardingOptions();
        wizardStep(0, false);
        personalizationReady = true;
        onboardingCompleted = Boolean(results[1] && results[1].completed);
        onboardingStatusLoaded = true;
        if (onboardingCompleted && requestedLandingDestination) {
            requestedLandingDestination = "";
            sessionStorage.removeItem("recomai.landingDestination");
        }
        if (!onboardingCompleted) {
            if (!appShell.hidden) showPanel("onboardingPanel");
        } else if (pendingHomepageQuery) {
            processPendingHomepageSearch();
        }
    } catch (error) {
        document.getElementById("onboardingStatus").textContent = "Could not load personalization. Refresh the page to retry. " + error.message;
        handleError(error);
    } finally {
        document.querySelectorAll("#onboardingForm select, #onboardingForm button").forEach(control => { control.disabled = !personalizationReady; });
        document.getElementById("onboardingBack").disabled = true;
    }
}

async function renderOnboardingOptions() {
    const select = document.getElementById("onboardingNiche");
    if (!select) return;
    const selected = select.value;
    select.replaceChildren(new Option("Choose an area", ""));
    niches.forEach(function (niche) {
        select.appendChild(new Option(niche.name, String(niche.categoryId)));
    });
    if (selected && niches.some(function (niche) { return String(niche.categoryId) === selected; })) {
        select.value = selected;
    }
    if (savedOnboarding?.completed) {
        document.getElementById("onboardingPurpose").value = savedOnboarding.purpose;
        select.value = String(savedOnboarding.categoryId);
    } else applyTemporaryGoal();
    renderOnboardingTopics();
    buildChoices();
    if (savedOnboarding?.completed) {
        await api("/users/me/recommendation-profile").then(profile => {
            const ids = new Set((profile.interests || []).map(row => String(row.interestId)));
            document.querySelectorAll("#onboardingTopics input").forEach(input => { input.checked = ids.has(input.value); });
            syncWizard();
        });
    }
}

function renderOnboardingTopics() {
    const selectedNiche = niches.find(function (niche) {
        return String(niche.categoryId) === document.getElementById("onboardingNiche").value;
    });
    const container = document.getElementById("onboardingTopics");
    container.replaceChildren();
    if (!selectedNiche || !Array.isArray(selectedNiche.topics)) return;
    selectedNiche.topics.forEach(function (topic) {
        const label = createElement("label", "topic-choice");
        const checkbox = document.createElement("input");
        checkbox.type = "checkbox";
        checkbox.value = String(topic.interestId);
        checkbox.name = "onboardingTopic";
        checkbox.addEventListener("change", function () {
            const selected = Array.from(container.querySelectorAll("input:checked"));
            container.querySelectorAll("input:not(:checked)").forEach(function (entry) {
                entry.disabled = selected.length >= 3;
            });
        });
        label.append(checkbox, document.createTextNode(topic.name));
        container.appendChild(label);
    });
}

async function loadInterestProfile() {
    if (currentUser && document.getElementById("interestProfilePanel").classList.contains("active")) loadTaste();
    if (!currentUser) return;
    try {
        const profile = await api("/users/me/interests/insights");
        renderInterestGroup("currentInterests", profile.current, true);
        renderInterestGroup("growingInterests", profile.growing, true);
        renderInterestGroup("decliningInterests", profile.declining, true);
        renderInterestGroup("exploreInterests", profile.exploreNext);
        renderEmergingInterest(profile.growing);
    } catch (error) {
        handleError(error);
    }
}

function renderEmergingInterest(insights) {
    const callout = document.getElementById("emergingInterestCallout");
    const message = document.getElementById("emergingInterestMessage");
    const confidence = document.getElementById("emergingInterestConfidence");
    const candidates = Array.isArray(insights) ? insights.slice(0, 3) : [];
    callout.hidden = candidates.length === 0;
    if (!candidates.length) return;

    const topics = candidates.map(function (insight) { return insight.topic; });
    message.textContent = "Your recent activity suggests growing interest in " + topics.join(", ") + ". We’re still learning.";
    const labels = Array.from(new Set(candidates.map(function (insight) { return insight.confidence; })));
    confidence.textContent = "Signal confidence: " + labels.join(" · ").toLowerCase() + ".";
}

function renderInterestGroup(elementId, insights, showStrength) {
    const container = document.getElementById(elementId);
    if (!container) return;
    container.replaceChildren();
    const list = Array.isArray(insights) ? insights : [];
    if (!list.length) {
        container.appendChild(createElement("p", "insight-empty", "Nothing to show yet. Explore a few resources and this space will adapt."));
        return;
    }
    list.forEach(function (insight) {
        const row = createElement("div", "insight-row");
        const copy = createElement("div", "insight-copy");
        const trend = insight.trend === "UP" ? " ↑" : insight.trend === "DOWN" ? " ↓" : "";
        copy.appendChild(createElement("strong", "", insight.topic + trend));
        copy.appendChild(createElement("small", "", insight.niche + " · " + (insight.confidence || "confidence unavailable")));
        if (insight.evidence) {
            copy.appendChild(createElement("small", "insight-evidence", insight.evidence));
        }
        row.appendChild(copy);
        if (showStrength) {
            const meter = document.createElement("meter");
            meter.min = "0";
            meter.max = "100";
            meter.value = String(insight.strengthPercent || 0);
            meter.setAttribute("aria-label", insight.topic + " interest estimate");
            row.appendChild(meter);
            row.appendChild(createElement("span", "insight-strength", (insight.strengthPercent || 0) + "%"));
        }
        if (elementId === "exploreInterests") {
            const button = createElement("button", "small-action", "Explore");
            button.type = "button";
            button.addEventListener("click", function () {
                showPanel("catalogPanel");
                const niche = niches.find(function (entry) { return entry.name === insight.niche; });
                if (niche) {
                    document.getElementById("categoryFilter").value = String(niche.categoryId);
                    renderCatalog();
                }
            });
            row.appendChild(button);
        }
        container.appendChild(row);
    });
}

function categoryName(categoryId) {
    const category = categories.find(function (entry) { return Number(entry.categoryId) === Number(categoryId); });
    return category ? category.name : "General";
}

function createElement(tag, className, text) {
    const element = document.createElement(tag);
    if (className) {
        element.className = className;
    }
    if (text !== undefined && text !== null) {
        element.textContent = text;
    }
    return element;
}

function itemCard(item) {
    const card = createElement("article", "item-card");
    const art = createElement("div", "item-art");
    const artwork = createResourceArtwork(item, categoryName(item.categoryId));
    const svg = artwork.element;
    art.classList.add("resource-art", "art-" + artwork.key);
    art.appendChild(createElement("span", "item-type", item.type));
    art.appendChild(svg);
    const content = createElement("div", "item-content");
    const meta = createElement("div", "item-meta");
    meta.appendChild(createElement("span", "", categoryName(item.categoryId)));
    if (item.editorPick) meta.appendChild(createElement("span", "editor-pick-badge", "Editor's pick"));
    meta.appendChild(createElement("span", "", item.price === null ? "" : "Price " + Number(item.price).toFixed(2)));
    content.appendChild(meta);
    content.appendChild(createElement("h3", "", item.title));
    content.appendChild(createElement("p", "", item.description || "A new find from the Recom AI collection."));
    const resourceMeta = [item.sourceName, item.difficulty && item.difficulty.replaceAll("_", " "),
        item.estimatedMinutes ? item.estimatedMinutes + " min" : null].filter(Boolean).join(" · ");
    if (resourceMeta) content.appendChild(createElement("small", "resource-meta", resourceMeta));
    if (Array.isArray(item.tags) && item.tags.length) {
        const tags = createElement("div", "item-tags");
        item.tags.forEach(function (tag) { tags.appendChild(createElement("span", "topic-tag", tag)); });
        content.appendChild(tags);
    }
    const actions = createElement("div", "item-actions");
    const primaryActions = createElement("div", "item-primary-actions");
    const secondaryActions = createElement("div", "item-secondary-actions");
    const viewAction = interactionButton(item.itemId, "VIEW", "View");
    viewAction.classList.add("action-view");
    viewAction.addEventListener("click", function () { openResourceDetails(item); });
    primaryActions.appendChild(viewAction);
    if (item.externalUrl) {
        const open = createElement("a", "small-action", item.type === "ARTICLE" ? "Read Article ↗" : "Open resource ↗");
        open.classList.add("action-open");
        open.href = item.externalUrl;
        open.target = "_blank";
        open.rel = "noopener noreferrer";
        open.addEventListener("click", function () { recordInteraction(item.itemId, "CLICK"); });
        primaryActions.appendChild(open);
    }
    const likeAction = interactionButton(item.itemId, "LIKE", "Like");
    likeAction.classList.add("action-like");
    primaryActions.appendChild(likeAction);
    const saveAction = interactionButton(item.itemId, "SAVE", "Save");
    saveAction.classList.add("action-save");
    primaryActions.appendChild(saveAction);
    secondaryActions.appendChild(interactionButton(item.itemId, "READ_PROGRESS", "In progress"));
    secondaryActions.appendChild(interactionButton(item.itemId, "COMPLETE", "Mark complete"));
    secondaryActions.appendChild(interactionButton(item.itemId, "MORE_LIKE", "More like this"));
    secondaryActions.appendChild(interactionButton(item.itemId, "DISLIKE", "Not for me", true));
    secondaryActions.appendChild(interactionButton(item.itemId, "HIDE", "Hide", true));
    actions.append(primaryActions, secondaryActions);
    content.appendChild(actions);
    content.appendChild(createRatingControl(item.itemId));
    card.appendChild(art);
    card.appendChild(content);
    if (!window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
        card.addEventListener("pointermove", function (event) {
            if (event.pointerType === "touch") return;
            const bounds = card.getBoundingClientRect();
            const horizontal = (event.clientX - bounds.left) / bounds.width - 0.5;
            const vertical = (event.clientY - bounds.top) / bounds.height - 0.5;
            card.style.setProperty("--tilt-x", (vertical * -3.5).toFixed(2) + "deg");
            card.style.setProperty("--tilt-y", (horizontal * 4).toFixed(2) + "deg");
            card.classList.add("has-pointer");
        });
        card.addEventListener("pointerleave", function () {
            card.classList.remove("has-pointer");
        });
    }
    return card;
}

function openResourceDetails(item, recommendationReason, administrative = false) {
    if (!item) return;
    const dialog = document.getElementById("resourceDetailsDialog");
    const body = document.getElementById("resourceDetailsBody");
    body.replaceChildren();
    const category = categoryName(item.categoryId);
    const artwork = createResourceArtwork(item, category);
    const visual = createElement("div", "resource-detail-art art-" + artwork.key);
    visual.setAttribute("aria-hidden", "true"); visual.appendChild(artwork.element);
    body.appendChild(visual);
    body.appendChild(createElement("p", "eyebrow", category + (item.type ? " / " + item.type.replaceAll("_", " ") : "")));
    const title = createElement("h2", "", item.title);
    title.id = "resourceDetailsTitle";
    body.appendChild(title);
    if (item.description) body.appendChild(createElement("p", "resource-detail-description", item.description));
    const meta = [item.sourceName, item.difficulty && item.difficulty.replaceAll("_", " "), item.estimatedMinutes ? item.estimatedMinutes + " min" : null].filter(Boolean).join(" · ");
    if (meta) body.appendChild(createElement("p", "resource-detail-meta", meta));
    if (recommendationReason) {
        const explanation = createElement("section", "resource-detail-explanation");
        explanation.append(createElement("h3", "", "Why this was recommended"), createElement("p", "", recommendationReason));
        body.appendChild(explanation);
    }
    if (Array.isArray(item.tags) && item.tags.length) {
        const tags = createElement("div", "resource-detail-tags");
        item.tags.forEach(tag => tags.appendChild(createElement("span", "topic-tag", tag)));
        body.appendChild(tags);
    }
    const actions = createElement("div", "resource-detail-actions");
    if (item.externalUrl) {
        const open = createElement("a", "button button-primary", item.type === "ARTICLE" ? "Read Article ↗" : "Open resource ↗");
        open.href = item.externalUrl; open.target = "_blank"; open.rel = "noopener noreferrer";
        if (!administrative) open.addEventListener("click", () => recordInteraction(item.itemId, "CLICK"));
        actions.appendChild(open);
    }
    if (!administrative) {
    const save = createElement("button", "button button-secondary", "Save resource ♡"); save.type = "button";
    save.addEventListener("click", () => recordInteraction(item.itemId, "SAVE", save));
    actions.append(save);
    } else body.appendChild(createElement("p", "resource-detail-meta", "Catalog status · " + (item.active ? "Active" : "Paused")));
    body.appendChild(actions);
    if (!dialog.open) dialog.showModal();
    dialog.querySelector("[data-close-resource]").focus();
    const titleNode=title;
    api('/items/'+item.itemId+'/provenance').then(source=>{
        if(!dialog.open||!titleNode.isConnected)return;
        if(!source.source_id){body.append(createElement('p','resource-detail-meta','Catalog-curated metadata. Provider verification is not recorded.'));return;}
        const attribution=createElement('section','resource-detail-explanation');
        attribution.append(createElement('h3','','Source attribution'),createElement('p','',source.attribution));
        const license=createElement('a','text-link',source.license);license.href=source.license_url;license.target='_blank';license.rel='noopener noreferrer';attribution.append(license);
        attribution.append(createElement('p','','Difficulty and study time are not provided. Suggested learning order; prerequisites are not verified.'));
        if(administrative)attribution.append(createElement('p','',source.metadata_origin+' · Source record '+source.external_id+' · Batch '+source.batch_id+' · URL '+source.url_status));
        body.append(attribution);
    }).catch(()=>{if(titleNode.isConnected&&dialog.open)body.append(createElement('p','','Source attribution could not be loaded.'));});
}

function createRatingControl(itemId) {
    const wrapper = createElement("div", "rating-control");
    const label = createElement("label", "", "Your rating");
    const select = document.createElement("select");
    select.setAttribute("aria-label", "Your rating for this resource");
    select.appendChild(new Option("Choose a rating", ""));
    [1, 2, 3, 4, 5].forEach(function (rating) {
        select.appendChild(new Option(rating + " out of 5", String(rating)));
    });
    const existing = feedbackByItemId.get(Number(itemId));
    if (existing) select.value = String(existing.rating);
    const button = createElement("button", "small-action", existing ? "Update rating" : "Save rating");
    button.type = "button";
    const status = createElement("span", "rating-status");
    status.setAttribute("role", "status");
    status.setAttribute("aria-live", "polite");
    if (!feedbackLoaded) {
        select.disabled = true;
        button.disabled = true;
        status.textContent = "Your saved rating could not be loaded.";
    }
    label.appendChild(select);
    wrapper.appendChild(label);
    wrapper.appendChild(button);
    wrapper.appendChild(status);
    button.addEventListener("click", function () {
        saveRating(itemId, select, button, status);
    });
    return wrapper;
}

async function saveRating(itemId, select, button, status) {
    const rating = Number(select.value);
    if (!rating || rating < 1 || rating > 5) {
        status.textContent = "Choose a rating from 1 to 5 first.";
        return;
    }
    const existing = feedbackByItemId.get(Number(itemId));
    button.disabled = true;
    select.disabled = true;
    status.textContent = "Saving your rating…";
    try {
        const payload = { itemId: Number(itemId), rating: rating, comment: null };
        const saved = await api(existing ? "/users/me/feedback/" + itemId : "/users/me/feedback", {
            method: existing ? "PUT" : "POST",
            body: JSON.stringify(payload)
        });
        feedbackByItemId.set(Number(itemId), saved);
        button.textContent = "Update rating";
        status.textContent = "Rating saved. Refresh your picks to use it.";
        showToast("Your rating has been saved.");
    } catch (error) {
        status.textContent = error && error.message ? error.message : "Your rating could not be saved.";
        handleError(error);
    } finally {
        button.disabled = false;
        select.disabled = false;
    }
}

function interactionButton(itemId, type, label, negative) {
    const button = createElement("button", negative ? "small-action negative" : "small-action", label);
    button.type = "button";
    button.addEventListener("click", function () {
        recordInteraction(itemId, type, button);
    });
    return button;
}

function newInteractionRequestId() {
    if (window.crypto && typeof window.crypto.randomUUID === "function") return window.crypto.randomUUID();
    return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function (character) {
        const random = Math.floor(Math.random() * 16);
        return (character === "x" ? random : (random & 3) | 8).toString(16);
    });
}

async function recordInteraction(itemId, type, button) {
    if (button && button.disabled) return false;
    if (button) button.disabled = true;
    try {
        await api("/users/me/interactions", {
            method: "POST",
            body: JSON.stringify({ itemId: itemId, type: type, requestId: newInteractionRequestId() })
        });
        const message = type === "DISLIKE" || type === "HIDE" ? "Got it. We’ll show you less of this."
            : type === "SAVE" ? "Saved to your reading list."
                : type === "COMPLETE" ? "Marked complete. Nice progress."
                    : type === "READ_PROGRESS" ? "Added to your in-progress list."
                : type === "MORE_LIKE" ? "Got it. Refresh your picks to see more like this."
                    : "Activity saved. Your picks will adapt over time.";
        showToast(message);
        loadInterestProfile();
        if (type === "SAVE" && document.getElementById("savedPanel").classList.contains("active")) {
            loadSavedItems();
        }
        if (document.getElementById("progressPanel")?.classList.contains("active")) loadProgress();
        if (document.getElementById("recommendationsPanel").classList.contains("active")
                && ["LIKE", "SAVE", "DISLIKE", "HIDE", "MORE_LIKE", "COMPLETE"].includes(type)) {
            generateRecommendations();
        }
        return true;
    } catch (error) {
        handleError(error);
        return false;
    } finally {
        if (button) button.disabled = false;
    }
}

async function renderCatalog(trackSearch) {
    const container = document.getElementById("catalogItems");
    const filter = document.getElementById("categoryFilter").value;
    const query = document.getElementById("catalogSearchQuery").value.trim();
    const topic = document.getElementById("topicFilter")?.value || "";
    const difficulty = document.getElementById("difficultyFilter")?.value || "";
    const maximumTime = Number(document.getElementById("timeFilter")?.value || 0);
    const requestId = ++catalogRequestId;
    const loading = document.getElementById("catalogLoading");
    const catalogError = document.getElementById("catalogError");
    const hadResults = container.childElementCount > 0;
    catalogError.hidden = true;
    // A pending query must not leave obsolete resource actions usable while its cards are replaced.
    container.hidden = true;
    container.setAttribute('aria-busy','true');
    loading.hidden = false;
    const sort=document.getElementById('sortFilter').value;
    const params = new URLSearchParams({ page: String(catalogPage), pageSize: String(catalogPageSize), sort: sort==='title'?'TITLE_ASC':sort==='time'?'TIME_ASC':'RELEVANCE' });
    if(difficulty)params.set('difficulty',difficulty);if(maximumTime)params.set('maximumTime',String(maximumTime));
    if (query) params.set("query", query);
    if (topic) params.set("topic", topic);
    const provider = document.getElementById("providerFilter")?.value.trim();
    if (provider) params.set("provider", provider);
    if (filter) params.set("categoryId", filter);
    const type = document.getElementById("typeFilter")?.value || "";
    if (type) params.set("type", type);
    try {
        const page = await api("/items/search?" + params.toString());
        if (requestId !== catalogRequestId) return;
        loading.hidden = true;
        catalogError.hidden = true;
        const visiblePageItems = Array.isArray(page.items) ? page.items : [];
        mergeResourceMetadata(visiblePageItems);
        const totalPages=Number(page.totalPages||0);
        container.replaceChildren();
        visiblePageItems.forEach(function (item) { container.appendChild(itemCard(item)); });
        container.hidden = false;
        container.setAttribute('aria-busy','false');
        document.getElementById("catalogEmpty").hidden = visiblePageItems.length > 0;
        document.querySelector("#catalogEmpty h3").textContent = type ? "No " + type.toLowerCase().replaceAll("_", " ") + " resources match" : "No resources match";
        document.querySelector("#catalogEmpty p").textContent = "Try clearing filters or changing your search. Resources with unknown difficulty or reading time are excluded by level and time limits.";
        const resultCount = Number(page.totalItems || 0);
        document.getElementById("catalogResultsSummary").textContent =
            resultCount + (resultCount === 1 ? " resource found" : " resources found")
            + "; " + visiblePageItems.length + " shown on this page with the selected filters";
        const pagination = document.getElementById("catalogPagination");
        const previousPage = document.getElementById("catalogPreviousPage");
        const nextPage = document.getElementById("catalogNextPage");
        pagination.hidden = totalPages <= 1;
        previousPage.disabled = catalogPage <= 1;
        nextPage.disabled = catalogPage >= totalPages;
        document.getElementById("catalogPageSummary").textContent = totalPages > 0
            ? "Page " + catalogPage + " of " + totalPages : "No pages";
        let tracked = false;
        if (trackSearch && currentUser && query.length >= 2) {
            await api("/users/me/interactions/search", {
                method: "POST",
                body: JSON.stringify({ query: query, requestId: newInteractionRequestId() })
            });
            tracked = true;
            await Promise.all([loadInterestProfile(), loadActivity()]);
        }
        return { ok: true, tracked: tracked };
    } catch (error) {
        if (requestId !== catalogRequestId) return;
        loading.hidden = true;
        catalogError.hidden = false;
        if (!hadResults) container.replaceChildren();
        container.hidden = false;
        container.setAttribute('aria-busy','false');
        handleError(error);
        return { ok: false, tracked: false };
    }
}

async function processPendingHomepageSearch() {
    if (!pendingHomepageQuery || !currentUser || !onboardingCompleted) return;
    const query = pendingHomepageQuery;
    document.getElementById("catalogSearchQuery").value = query;
    catalogPage = 1;
    showPanel("catalogPanel");
    const result = await renderCatalog(true);
    if (!result || !result.ok) return;
    if (query.length >= 2 && !result.tracked) return;
    pendingHomepageQuery = null;
    await generateRecommendations();
    if (query.length < 2) {
        showToast("Search results loaded. Add another character to save this as a recommendation signal.", true);
    }
}

function renderFeaturedItems() {
    const container = document.getElementById("featuredItems");
    container.replaceChildren();
    items.slice(0, 3).forEach(function (item) {
        container.appendChild(itemCard(item));
    });
}

function renderCategoryFilters() {
    const select = document.getElementById("categoryFilter");
    const selected = select.value;
    select.replaceChildren();
    select.appendChild(new Option("All categories", ""));
    categories.forEach(function (category) {
        select.appendChild(new Option(category.name, String(category.categoryId)));
    });
    select.value = selected;
}

function populateCatalogTopics() {
    const select = document.getElementById("topicFilter");
    if (!select) return;
    const selected = select.value;
    select.replaceChildren(new Option("All topics", ""));
    Array.from(new Set(catalogTopicNames))
        .sort((a, b) => a.localeCompare(b))
        .forEach(topic => select.appendChild(new Option(topic, topic)));
    if (Array.from(select.options).some(option => option.value === selected)) select.value = selected;
}

async function renderPreferences() {
    const container = document.getElementById("preferenceList");
    container.replaceChildren();
    const empty = document.getElementById("preferencesEmpty");
    if (!categories.length) {
        empty.hidden = false;
        return;
    }
    empty.hidden = true;
    let savedPreferences = [];
    try {
        const result = await api("/users/me/preferences");
        savedPreferences = Array.isArray(result) ? result : [];
    } catch (error) {
        handleError(error);
        return;
    }
    const weights = new Map((savedPreferences || []).map(function (preference) {
        return [preference.categoryId, Number(preference.weight)];
    }));
    categories.forEach(function (category) {
        const row = createElement("div", "preference-row");
        const name = createElement("div", "preference-name");
        name.appendChild(document.createTextNode(category.name));
        name.appendChild(createElement("span", "preference-description", category.description || "Choose how often you would like to see this category."));
        const range = document.createElement("input");
        range.className = "preference-range";
        range.type = "range";
        range.setAttribute("aria-label", "Preference weight for " + category.name);
        range.min = "0";
        range.max = "1";
        range.step = "0.05";
        range.value = String(weights.has(category.categoryId) ? weights.get(category.categoryId) : 0.5);
        const value = createElement("span", "preference-value", Number(range.value).toFixed(2));
        range.addEventListener("input", function () { value.textContent = Number(range.value).toFixed(2); });
        const save = createElement("button", "small-action", "Save");
        save.type = "button";
        save.addEventListener("click", async function () {
            try {
                await api("/users/me/preferences/" + category.categoryId, {
                    method: "PUT",
                    body: JSON.stringify({ weight: Number(range.value) })
                });
                showToast("Interest updated for " + category.name + ".");
            } catch (error) {
                handleError(error);
            }
        });
        row.appendChild(name);
        row.appendChild(range);
        row.appendChild(value);
        row.appendChild(save);
        container.appendChild(row);
    });
}

let recommendationRequestVersion = 0;

async function loadRecommendations() {
    const requestVersion = ++recommendationRequestVersion;
    const container = document.getElementById("recommendationList");
    try {
        const [recommendations, profile] = await Promise.all([
            api("/users/me/recommendations?limit=10"),
            api("/users/me/recommendation-profile").catch(function () { return null; })
        ]);
        if (requestVersion !== recommendationRequestVersion) return;
        if (recommendations && recommendations.length) {await ensureResourceMetadata(recommendations.map(row=>row.itemId));if(requestVersion!==recommendationRequestVersion)return;renderRecommendations(recommendations, profile);}
        else {
            const discovery = await api("/users/me/recommendations/discovery?limit=10");
            if (requestVersion === recommendationRequestVersion) renderCatalogDiscovery(discovery);
        }
    } catch (error) {
        handleError(error);
        container.replaceChildren();
        document.getElementById("spatialCarousel").hidden = true;
        const empty = document.getElementById("recommendationsEmpty");
        empty.querySelector("h3").textContent = "Couldn't load your picks";
        empty.querySelector("p").textContent = "Check your connection and try loading recommendations again.";
        empty.hidden = false;
    }
}

function renderRecommendations(recommendations, profile) {
    const container = document.getElementById("recommendationList");
    const empty = document.getElementById("recommendationsEmpty");
    const evidenceNote = document.getElementById("recommendationEvidenceNote");
    container.replaceChildren();
    const results = Array.isArray(recommendations) ? recommendations : [];
    empty.hidden = results.length > 0;
    const noBehaviorYet = profile && Number(profile.interactionCount) === 0 && Number(profile.feedbackCount) === 0;
    const exploratoryCount = results.filter(function (entry) { return entry.reasonType === "COLD_START"; }).length;
    evidenceNote.hidden = !noBehaviorYet && exploratoryCount === 0;
    evidenceNote.textContent = noBehaviorYet
        ? "You have no interaction or rating history yet. These starting picks use your onboarding choices and editor-selected resources; your activity and feedback can help personalize future picks."
        : exploratoryCount > 0
            ? "" + exploratoryCount + (exploratoryCount === 1 ? " pick is" : " picks are")
                + " exploratory because there isn't enough evidence to personalize those items yet. Your feedback helps shape later picks."
            : "";
    if (results.length === 0) {
        empty.querySelector("h3").textContent = "Your picks start here";
        empty.querySelector("p").textContent = "Choose “Refresh my picks” to generate a personalized list from your saved signals.";
    }
    const bucketLists = new Map();
    if (recommendationImpressionObserver) recommendationImpressionObserver.disconnect();
    recommendationImpressionObserver = "IntersectionObserver" in window
        ? new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (!entry.isIntersecting || entry.intersectionRatio < 0.5) return;
                const recommendationId = entry.target.dataset.recommendationId;
                if (!recommendationId) return;
                recommendationImpressionObserver.unobserve(entry.target);
                if (sentRecommendationImpressions.has(recommendationId)) return;
                sentRecommendationImpressions.add(recommendationId);
                api("/users/me/recommendations/" + encodeURIComponent(recommendationId) + "/impression", {
                    method: "POST"
                }).catch(function () {
                    sentRecommendationImpressions.delete(recommendationId);
                    if (entry.target.isConnected) recommendationImpressionObserver.observe(entry.target);
                });
            });
        }, { threshold: 0.5 }) : null;
    ["FAMILIAR", "RELATED", "DISCOVERY"].forEach(function (bucket) {
        if (!results.some(function (entry) { return (entry.bucketType || "FAMILIAR") === bucket; })) return;
        const section = createElement("section", "feed-bucket");
        const label = bucket === "FAMILIAR" ? "Based on your interests"
            : bucket === "RELATED" ? "Explore something related" : "Something new for you";
        section.appendChild(createElement("h3", "feed-bucket-title", label));
        const list = createElement("div", "recommendation-list");
        section.appendChild(list);
        container.appendChild(section);
        bucketLists.set(bucket, list);
    });
    results.forEach(function (recommendation, index) {
        const bucket = recommendation.bucketType || "FAMILIAR";
        const card = createElement("article", "recommendation-card");
        if (recommendation.recommendationId) card.dataset.recommendationId = recommendation.recommendationId;
        card.appendChild(createElement("div", "recommendation-rank", String(index + 1).padStart(2, "0")));
        const copy = createElement("div", "");
        copy.appendChild(createElement("h3", "", recommendation.itemTitle));
        const item = items.find(function (entry) { return Number(entry.itemId) === Number(recommendation.itemId); });
        copy.appendChild(createElement("p", "recommendation-category", categoryName(item ? item.categoryId : 0)));
        if (item && item.description) {
            copy.appendChild(createElement("p", "recommendation-description", item.description));
        }
        if (item) {
            const details = [item.type, item.sourceName,
                item.estimatedMinutes ? item.estimatedMinutes + " min" : null].filter(Boolean).join(" · ");
            if (details) copy.appendChild(createElement("p", "recommendation-meta", details));
            if (Array.isArray(item.tags) && item.tags.length) {
                copy.appendChild(createElement("p", "recommendation-meta", "Topics: " + item.tags.join(", ")));
            }
        }
        const bucketLabel = recommendation.reasonType === "EDITOR_PICK" ? "Editor's pick"
            : recommendation.reasonType === "POPULARITY" ? "Popular in the catalog"
            : recommendation.bucketType === "RELATED" ? "Related discovery"
                : recommendation.bucketType === "DISCOVERY" ? "Explore"
                    : "Based on your interests";
        copy.appendChild(createElement("span", "recommendation-bucket", bucketLabel));
        copy.appendChild(createElement("p", "recommendation-reason", "Why this was recommended: " + (recommendation.reason || "This item is a match for your current profile.")));
        const breakdown = recommendation.scoreBreakdown && typeof recommendation.scoreBreakdown === "object"
            ? Object.entries(recommendation.scoreBreakdown).filter(function (entry) { return Number(entry[1]) !== 0; }) : [];
        if (breakdown.length) {
            const disclosure = createElement("details", "recommendation-explanation");
            disclosure.appendChild(createElement("summary", "", "See the signals used"));
            const list = createElement("ul", "recommendation-signals");
            breakdown.forEach(function (entry) {
                const labels = {
                    category_preference: "Selected area match",
                    selected_topic_match: "Topics you chose",
                    related_topic_match: "Related topics",
                    metadata_similarity: "Similar descriptions and topics",
                    positive_interaction: "Your positive activity",
                    rating_affinity: "Your ratings",
                    catalog_popularity: "Recent activity from other users",
                    collaborative_evidence: "Similar users' activity",
                    onboarding_goal: "Your discovery goal",
                    negative_feedback_penalty: "Negative feedback",
                    editor_pick: "Editor's selection"
                };
                const label = labels[entry[0]] || entry[0].replaceAll("_", " ");
                const value = Number(entry[1]);
                list.appendChild(createElement("li", "", label + ": " + (value < 0 ? "−" : "+")
                    + Math.abs(value).toFixed(2) + " ranking points"));
            });
            disclosure.appendChild(list);
            disclosure.appendChild(createElement("p", "recommendation-score-note",
                "Ranking points show which signals raised or lowered this score. The feed also balances familiar, related, and exploratory picks. Points are not a probability of liking this item."));
            copy.appendChild(disclosure);
        }
        copy.appendChild(createRatingControl(recommendation.itemId));
        if (item && item.externalUrl) {
            const open = createElement("a", "small-action recommendation-open", item.type === "ARTICLE" ? "Read Article ↗" : "Open resource ↗");
            open.href = item.externalUrl;
            open.target = "_blank";
            open.rel = "noopener noreferrer";
            open.addEventListener("click", function () {
                recordInteraction(item.itemId, "CLICK");
                recordRecommendationClick(recommendation.recommendationId);
            });
            copy.appendChild(open);
        }
        card.appendChild(copy);
        const actions = createElement("div", "recommendation-actions");
        const like = createElement("button", "small-action", "Like");
        like.type = "button";
        like.setAttribute("aria-label", "Like " + (recommendation.itemTitle || "recommendation"));
        like.addEventListener("click", function () { recordInteraction(recommendation.itemId, "LIKE", like); });
        actions.appendChild(like);
        const save = createElement("button", "small-action recommendation-save", "Save");
        save.type = "button";
        save.setAttribute("aria-label", "Save " + (recommendation.itemTitle || "recommendation"));
        save.addEventListener("click", function () { recordInteraction(recommendation.itemId, "SAVE", save); });
        actions.appendChild(save);
        const more = createElement("button", "small-action recommendation-more", "More like this");
        more.type = "button";
        more.setAttribute("aria-label", "Show me more like " + (recommendation.itemTitle || "this"));
        more.addEventListener("click", function () { recordInteraction(recommendation.itemId, "MORE_LIKE", more); });
        actions.appendChild(more);
        const dismiss = createElement("button", "small-action negative recommendation-dismiss", "Not interested");
        dismiss.type = "button";
        dismiss.setAttribute("aria-label", "Not interested in " + (recommendation.itemTitle || "this item"));
        dismiss.addEventListener("click", async function () {
            if (await recordInteraction(recommendation.itemId, "DISLIKE", dismiss)) card.remove();
        });
        actions.appendChild(dismiss);
        const hide = createElement("button", "small-action negative recommendation-hide", "Hide");
        hide.type = "button";
        hide.setAttribute("aria-label", "Hide " + (recommendation.itemTitle || "this item"));
        hide.addEventListener("click", async function () {
            if (await recordInteraction(recommendation.itemId, "HIDE", hide)) card.remove();
        });
        actions.appendChild(hide);
        card.appendChild(actions);
        (bucketLists.get(bucket) || container).appendChild(card);
        if (recommendationImpressionObserver && recommendation.recommendationId) {
            recommendationImpressionObserver.observe(card);
        }
    });
    renderGoalFirstDashboard(results);
}

function renderGoalFirstDashboard(recommendations) {
    const dashboard = document.getElementById("goalFirstDashboard");
    const nextStep = document.getElementById("nextStepCard");
    const path = document.getElementById("suggestedPath");
    const beyond = document.getElementById("beyondGoalItems");
    document.getElementById("spatialCarousel").hidden = true;
    const results = Array.isArray(recommendations) ? recommendations : [];
    if (!results.length) { dashboard.hidden = true; return; }
    const first = results[0];
    const firstItem = items.find(item => Number(item.itemId) === Number(first.itemId));
    if (!firstItem) { dashboard.hidden = true; return; }
    dashboard.hidden = false;
    nextStep.replaceChildren(); path.replaceChildren(); beyond.replaceChildren();

    const feature = createElement("article", "goal-next-card");
    feature.dataset.recommendationId = first.recommendationId || "";
    const artwork = createResourceArtwork(firstItem, categoryName(firstItem.categoryId));
    const visual = createElement("div", "goal-next-art art-" + artwork.key);
    visual.setAttribute("aria-hidden", "true"); visual.appendChild(artwork.element);
    const copy = createElement("div", "goal-next-copy");
    copy.appendChild(createElement("p", "goal-resource-category", categoryName(firstItem.categoryId) + " / " + (firstItem.type || "RESOURCE").replaceAll("_", " ")));
    copy.appendChild(createElement("h3", "", first.itemTitle || firstItem.title));
    copy.appendChild(createElement("p", "goal-resource-summary", firstItem.description || "A resource selected from your current recommendation results."));
    const meta = [firstItem.difficulty && firstItem.difficulty.replaceAll("_", " "), firstItem.estimatedMinutes ? firstItem.estimatedMinutes + " min" : null].filter(Boolean).join(" · ");
    if (meta) copy.appendChild(createElement("p", "goal-resource-meta", meta));
    if (first.reason) copy.appendChild(createElement("p", "goal-resource-reason", "Why this was recommended · " + first.reason));
    const actions = createElement("div", "goal-next-actions");
    const details = createElement("button", "button button-primary", "View details →");
    details.type = "button"; details.addEventListener("click", () => openResourceDetails(firstItem, first.reason));
    actions.appendChild(details);
    if (firstItem.externalUrl) {
        const open = createElement("a", "text-link", firstItem.type === "ARTICLE" ? "Read Article ↗" : "Open resource ↗");
        open.href = firstItem.externalUrl; open.target = "_blank"; open.rel = "noopener noreferrer";
        open.addEventListener("click", () => { recordInteraction(firstItem.itemId, "CLICK"); recordRecommendationClick(first.recommendationId); });
        actions.appendChild(open);
    }
    const save = createElement("button", "button button-quiet", "♡ Save"); save.type = "button";
    save.addEventListener("click", () => recordInteraction(firstItem.itemId, "SAVE", save));
    const like = createElement("button", "button button-quiet", "Like"); like.type = "button";
    like.addEventListener("click", () => recordInteraction(firstItem.itemId, "LIKE", like));
    const dismiss = createElement("button", "button button-quiet", "Not for me"); dismiss.type = "button";
    dismiss.addEventListener("click", () => recordInteraction(firstItem.itemId, "DISLIKE", dismiss));
    actions.append(save, like, dismiss); copy.appendChild(actions); feature.append(visual, copy); nextStep.appendChild(feature);
    if (recommendationImpressionObserver && first.recommendationId) recommendationImpressionObserver.observe(feature);

    results.slice(0, 4).forEach((recommendation, index) => {
        const item = items.find(entry => Number(entry.itemId) === Number(recommendation.itemId));
        if (!item) return;
        const step = createElement("article", "suggested-path-step");
        step.appendChild(createElement("span", "suggested-step-number", String(index + 1).padStart(2, "0")));
        const text = createElement("div", "suggested-step-copy");
        text.append(createElement("small", "", categoryName(item.categoryId)), createElement("strong", "", recommendation.itemTitle || item.title));
        text.appendChild(createElement("span", "", item.estimatedMinutes ? item.estimatedMinutes + " min" : item.difficulty?.replaceAll("_", " ") || "Resource"));
        const open = createElement(item.externalUrl ? "a" : "button", "suggested-step-open", "↗");
        open.setAttribute("aria-label", "Open " + item.title);
        if (item.externalUrl) { open.href = item.externalUrl; open.target = "_blank"; open.rel = "noopener noreferrer"; open.addEventListener("click", () => { recordInteraction(item.itemId, "CLICK"); recordRecommendationClick(recommendation.recommendationId); }); }
        else { open.type = "button"; open.addEventListener("click", () => openResourceDetails(item, recommendation.reason)); }
        step.append(text, open); path.appendChild(step);
    });
    loadBeyondGoal(firstItem.categoryId);
}

async function loadBeyondGoal(primaryCategoryId) {
    const container = document.getElementById("beyondGoalItems");
    try {
        const discovery = await api("/users/me/recommendations/discovery?limit=10");
        const alternatives = (Array.isArray(discovery) ? discovery : []).filter(item => Number(item.categoryId) !== Number(primaryCategoryId)).slice(0, 3);
        if (!alternatives.length) {
            container.appendChild(createElement("p", "beyond-goal-empty", "Explore the full catalog to find something beyond your current recommendations."));
            return;
        }
        alternatives.forEach(item => {
            const card = createElement("article", "beyond-goal-card");
            const artwork = createResourceArtwork(item, categoryName(item.categoryId));
            const visual = createElement("div", "beyond-goal-art art-" + artwork.key);
            visual.setAttribute("aria-hidden", "true"); visual.appendChild(artwork.element);
            const copy = createElement("div", "beyond-goal-copy");
            copy.append(createElement("small", "", categoryName(item.categoryId)), createElement("h4", "", item.title));
            const actions = createElement("div", "beyond-goal-actions");
            const view = createElement("button", "text-link", "Details →"); view.type = "button"; view.addEventListener("click", () => openResourceDetails(item));
            const save = createElement("button", "text-link", "Save ♡"); save.type = "button"; save.addEventListener("click", () => recordInteraction(item.itemId, "SAVE", save));
            actions.append(view, save); copy.appendChild(actions); card.append(visual, copy); container.appendChild(card);
        });
    } catch {
        container.appendChild(createElement("p", "beyond-goal-empty", "Discovery picks are temporarily unavailable. Open Explore to browse the full catalog."));
    }
}

function recordRecommendationClick(recommendationId) {
    if (!recommendationId) return;
    const url = "/api/users/me/recommendations/" + encodeURIComponent(recommendationId) + "/click";
    if (navigator.sendBeacon && navigator.sendBeacon(url, new Blob([], { type: "text/plain" }))) return;
    fetch(url, { method: "POST", credentials: "same-origin", keepalive: true })
        .catch(function () { /* Existing item click tracking remains available if attribution fails. */ });
}

function renderCatalogDiscovery(items) {
    document.getElementById("goalFirstDashboard").hidden = true;
    const container = document.getElementById("recommendationList");
    const empty = document.getElementById("recommendationsEmpty");
    const note = document.getElementById("recommendationEvidenceNote");
    container.replaceChildren();
    document.getElementById("spatialCarousel").hidden = true;
    note.hidden = false;
    note.textContent = "Your personalized feed is empty. These are general catalog discovery items, selected by editor curation and catalog quality; they are not trending or personalized.";
    if (!Array.isArray(items) || !items.length) {
        empty.querySelector("h3").textContent = "No eligible catalog items right now";
        empty.querySelector("p").textContent = "Hidden and recently opened items remain excluded. Check back after the catalog changes.";
        empty.hidden = false;
        return;
    }
    empty.hidden = true;
    const section = createElement("section", "feed-bucket");
    section.appendChild(createElement("h3", "feed-bucket-title", "Catalog discovery · not personalized"));
    const list = createElement("div", "recommendation-list");
    items.forEach(function (item) { list.appendChild(itemCard(item)); });
    section.appendChild(list);
    container.appendChild(section);
}

async function generateRecommendations() {
    const requestVersion = ++recommendationRequestVersion;
    const button = document.getElementById("generateRecommendations");
    button.disabled = true;
    button.textContent = "Finding your picks...";
    try {
        const [recommendations, profile] = await Promise.all([
            api("/users/me/recommendations?limit=10", { method: "POST" }),
            api("/users/me/recommendation-profile").catch(function () { return null; })
        ]);
        if (requestVersion !== recommendationRequestVersion) return;
        if (recommendations && recommendations.length) {
            await ensureResourceMetadata(recommendations.map(row=>row.itemId));if(requestVersion!==recommendationRequestVersion)return;
            renderRecommendations(recommendations, profile);
            showToast("Your new picks are ready.");
        } else {
            const discovery = await api("/users/me/recommendations/discovery?limit=10");
            if (requestVersion !== recommendationRequestVersion) return;
            renderCatalogDiscovery(discovery);
            showToast("No personalized matches yet. Here are general catalog discovery items.");
        }
    } catch (error) {
        handleError(error);
    } finally {
        button.disabled = false;
        button.textContent = "Find my picks ✳";
    }
}

let activityVersion = 0;
let activityRows = [];
function recordedDate(value) {
    if (!value) return new Date(NaN);
    // JDBC is configured for UTC; offsetless database timestamps represent UTC.
    return new Date(/[zZ]$|[+-]\d\d:\d\d$/.test(value) ? value : value + 'Z');
}
function renderActivity() {
    const container = document.getElementById('activityList'); container.replaceChildren();
    const type = document.getElementById('activityFilter').value;
    const rows = activityRows.filter(event => !type || event.type === type);
    document.getElementById('activityEmpty').hidden = activityRows.length > 0;
    if (!rows.length && activityRows.length) container.appendChild(createElement('p', 'workspace-empty', 'No recorded events match this filter.'));
    let currentGroup = '';
    rows.forEach(event => {
        const date = recordedDate(event.occurredAt);
        const valid = !Number.isNaN(date.getTime());
        const group = valid ? activityGroupLabel(date.toISOString()) : 'Undated activity';
        if (group !== currentGroup) { container.appendChild(createElement('h3', 'activity-day', group)); currentGroup = group; }
        const row = createElement('article', 'activity-row'); row.dataset.eventType = event.type;
        const symbol = createElement('span', 'activity-symbol', interactionSymbol(event.type)); symbol.setAttribute('aria-hidden', 'true');
        const item = event.resource || items.find(candidate => Number(candidate.itemId) === Number(event.itemId));
        const copy = createElement('div', 'trail-copy');
        copy.appendChild(createElement('p', 'trail-event-type', activityLabel(event.type)));
        const detail = event.type === 'SEARCH' ? event.searchQuery : item?.title || 'Resource no longer available';
        copy.appendChild(createElement('strong', '', detail));
        copy.appendChild(createElement('small', '', item ? categoryName(item.categoryId) : event.type === 'SEARCH' ? 'Recorded search' : 'Recorded resource interaction'));
        const time = createElement('time', 'activity-date', valid ? new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' }).format(date) : 'Time unavailable');
        if (valid) { time.dateTime = date.toISOString(); time.title = date.toLocaleString(); }
        row.append(symbol, copy, time);
        if (item?.active) {
            const details = createElement('button', 'button button-quiet', 'View resource'); details.type = 'button';
            details.onclick = () => openResourceDetails(item); row.appendChild(details);
        }
        container.appendChild(row);
    });
}
let activityCursor = null;
async function loadActivity(append = false) {
    if (!currentUser) return;
    append = append === true;
    const version = ++activityVersion, status = document.getElementById('activityStatus');
    const more=document.getElementById('activityMore'); more.disabled=true;
    status.textContent = 'Loading your learning trail…'; document.getElementById('activityList').setAttribute('aria-busy', 'true');
    try {
        const params=new URLSearchParams({pageSize:'25'}),type=document.getElementById('activityFilter').value;
        if(type)params.set('type',type);if(append&&activityCursor)params.set('cursor',activityCursor);
        const response = await api('/users/me/activity?'+params); if (version !== activityVersion) return;
        const combined=append?[...activityRows,...response.items]:response.items;
        activityRows=[...new Map(combined.map(event=>[event.interactionId,event])).values()];
        activityCursor=response.nextCursor;more.hidden=!response.hasMore;
        mergeResourceMetadata(activityRows.map(event=>event.resource).filter(Boolean));
        renderActivity(); status.textContent = activityRows.length + ' of '+response.totalItems+' recorded events loaded · newest first.'+(response.hasMore?' More history is available.':' This snapshot is fully loaded.');
    } catch (error) { if (version === activityVersion) { status.textContent = 'Could not load your trail. Refresh activity to retry. ' + error.message; handleError(error); } }
    finally { if (version === activityVersion) {document.getElementById('activityList').setAttribute('aria-busy', 'false');more.disabled=false;} }
}

let libraryVersion = 0;
let libraryRows = [];
let libraryScopeCount = 0;
let libraryPage = 1;
let libraryTotalPages = 0;
let librarySearchTimer;

function installCollectionControls() {
    const panel = document.getElementById('savedPanel');
    const workspace = createElement('div', 'library-workspace');
    const aside = createElement('aside', 'library-shelves');
    aside.setAttribute('aria-label', 'Your collections');
    aside.innerHTML = '<p class="eyebrow">YOUR SHELVES</p><div id="collectionNavigation"></div><form id="collectionForm"><label for="collectionName">Create a collection</label><input id="collectionName" aria-label="New collection name" maxlength="80" required placeholder="Give your shelf a name"><button class="button button-secondary" type="submit">Create collection</button></form><label for="collectionSelect">Selected collection</label><select id="collectionSelect" aria-label="Filter saved resources by collection"><option value="">All saved resources</option></select><button id="deleteCollection" class="button button-quiet" type="button" hidden>Delete collection</button><p class="privacy-note">A resource can belong to more than one collection. Deleting a collection keeps its saved history.</p>';
    const main = createElement('div', 'library-main');
    main.innerHTML = '<div class="library-toolbar"><label for="librarySearch">Find something worth revisiting<input id="librarySearch" aria-label="Search your library" type="search" placeholder="Search title, category, source or topic"></label><div role="group" aria-label="Library display"><button id="libraryGrid" class="button button-quiet" type="button" aria-pressed="true">Grid view</button><button id="libraryList" class="button button-quiet" type="button" aria-pressed="false">List view</button></div></div><p id="libraryScope" class="privacy-note"></p><p id="libraryStatus" role="status" aria-live="polite"></p><div id="libraryNoResults" class="workspace-empty" hidden><h3>No matching finds.</h3><p>Try another title, topic or source, or choose a different shelf.</p></div>';
    for (const id of ['savedItems', 'savedItemsUnavailable', 'savedItemsEmpty']) main.appendChild(document.getElementById(id));
    workspace.append(aside, main); panel.appendChild(workspace);
    const picks = createElement('button', 'button button-secondary', 'View recommendations'); picks.type = 'button'; picks.onclick = () => showPanel('recommendationsPanel'); document.getElementById('savedItemsEmpty').appendChild(picks);
    const select = document.getElementById('collectionSelect');
    select.addEventListener('change', () => {libraryPage=1;loadSavedItems();});
    document.getElementById('librarySearch').addEventListener('input', () => {libraryPage=1;libraryVersion++;clearTimeout(librarySearchTimer);librarySearchTimer=setTimeout(loadSavedItems,200);});
    for (const view of ['Grid', 'List']) document.getElementById('library' + view).onclick = () => {
        localStorage.setItem('recomai.libraryView.' + currentUser.userId, view.toLowerCase()); renderLibrary();
    };
    document.getElementById('collectionForm').onsubmit = async event => {
        event.preventDefault(); const button = event.currentTarget.querySelector('button');
        if (button.disabled) return; button.disabled = true;
        try {
            await api('/users/me/collections', { method: 'POST', body: JSON.stringify({ name: document.getElementById('collectionName').value }) });
            document.getElementById('collectionName').value = ''; await loadSavedItems(); showToast('Collection created.');
        } catch (error) { document.getElementById('libraryStatus').textContent = 'Could not create collection. ' + error.message; handleError(error); }
        finally { button.disabled = false; }
    };
    document.getElementById('deleteCollection').onclick = async event => {
        const id = Number(select.value), button = event.currentTarget;
        if (!id || button.disabled || !confirm('Delete this collection? Your saved resources will remain saved.')) return;
        button.disabled = true;
        try { await api('/users/me/collections/' + id, { method: 'DELETE' });select.value='';libraryPage=1;await loadSavedItems(); showToast('Collection deleted.'); }
        catch (error) { document.getElementById('libraryStatus').textContent = 'Could not delete collection. ' + error.message; handleError(error); }
        finally { button.disabled = false; }
    };
}

function renderCollectionControls() {
    const select = document.getElementById('collectionSelect'), selected = select.value;
    select.replaceChildren(new Option('All saved resources', ''));
    userCollections.forEach(collection => select.appendChild(new Option(collection.name, String(collection.collectionId))));
    select.value = userCollections.some(collection => String(collection.collectionId) === selected) ? selected : '';
    document.getElementById('deleteCollection').hidden = !select.value;
    const navigation = document.getElementById('collectionNavigation'); navigation.replaceChildren();
    for (const collection of [{ collectionId: '', name: 'All saved & collected', itemIds: null }, ...userCollections]) {
        const button = createElement('button', 'shelf-button', collection.name);
        button.type = 'button'; button.setAttribute('aria-pressed', String(String(collection.collectionId) === select.value));
        if (collection.itemIds) button.appendChild(createElement('span', '', String(collection.itemIds.length)));
        button.onclick = () => { select.value = String(collection.collectionId); libraryPage=1;loadSavedItems(); };
        navigation.appendChild(button);
    }
}

function renderLibrary() {
    const container = document.getElementById('savedItems'); container.replaceChildren();
    const query = document.getElementById('librarySearch').value.trim().toLocaleLowerCase();
    const rows = libraryRows;
    const list = localStorage.getItem('recomai.libraryView.' + currentUser.userId) === 'list';
    container.classList.toggle('library-list', list);
    document.getElementById('libraryGrid').setAttribute('aria-pressed', String(!list));
    document.getElementById('libraryList').setAttribute('aria-pressed', String(list));
    document.getElementById('libraryNoResults').hidden = rows.length > 0 || !query;
    document.getElementById('savedItemsEmpty').hidden = libraryScopeCount > 0 || Boolean(query);
    const selected = userCollections.find(collection => String(collection.collectionId) === document.getElementById('collectionSelect').value);
    document.getElementById('libraryScope').textContent = rows.length + ' of '+libraryScopeCount+' resources shown · Complete saved and collected library. Unsave also removes this resource from all your collections.';
    document.getElementById('libraryPageSummary').textContent=libraryTotalPages?'Page '+libraryPage+' of '+libraryTotalPages:'No pages';
    document.getElementById('libraryPrevious').disabled=libraryPage<=1;document.getElementById('libraryNext').disabled=libraryPage>=libraryTotalPages;
    document.getElementById('renameCollection').hidden=!selected;
    rows.forEach(item => {
        const card = item.active ? itemCard(item) : createElement('article','item-card unavailable-resource');
        if(!item.active){const content=createElement('div','item-content');content.append(createElement('h3','',item.title),createElement('p','','This resource is currently unavailable. Your saved reference is retained.'));card.appendChild(content);}
        const save = card.querySelector('.action-save'); if (save) { save.textContent = 'Saved / collected'; save.disabled = true; }
        const unsave=createElement('button','small-action','Unsave');unsave.type='button';unsave.setAttribute('aria-label','Unsave '+item.title);
        unsave.onclick=async()=>{if(unsave.disabled)return;unsave.disabled=true;const epoch=workspaceEpoch,userId=currentUser.userId;try{await api('/users/me/saved/'+item.itemId,{method:'DELETE'});if(!sameWorkspace(userId,epoch))return;await loadSavedItems();showToast('Resource unsaved and removed from your collections.');}catch(error){if(sameWorkspace(userId,epoch)){document.getElementById('libraryStatus').textContent='Could not unsave. '+error.message;handleError(error);}}finally{unsave.disabled=false;}};
        (card.querySelector('.item-primary-actions')||card.querySelector('.item-content')).appendChild(unsave);
        const memberships = userCollections.filter(collection => collection.itemIds.map(Number).includes(Number(item.itemId)));
        if (memberships.length) card.querySelector('.item-content').appendChild(createElement('p', 'library-memberships', 'On your shelves · ' + memberships.map(collection => collection.name).join(', ')));
        if (userCollections.length && item.active) {
            const controls = createElement('div', 'collection-item-action'), target = document.createElement('select');
            target.setAttribute('aria-label', 'Collection for ' + item.title);
            userCollections.forEach(collection => target.appendChild(new Option(collection.name, String(collection.collectionId))));
            if (selected) target.value = String(selected.collectionId);
            const action = createElement('button', 'small-action'); action.type = 'button';
            const update = () => { const collection = userCollections.find(row => String(row.collectionId) === target.value); action.textContent = collection?.itemIds.map(Number).includes(Number(item.itemId)) ? 'Remove from collection' : 'Add to collection'; };
            target.onchange = update; update();
            action.onclick = async () => {
                if (action.disabled) return; action.disabled = true;
                const collection = userCollections.find(row => String(row.collectionId) === target.value);
                if (!collection) { action.disabled = false; return; }
                const member = collection.itemIds.map(Number).includes(Number(item.itemId));
                try { await api('/users/me/collections/' + collection.collectionId + '/items/' + item.itemId, { method: member ? 'DELETE' : 'PUT' }); await loadSavedItems(); showToast(member ? 'Removed from collection.' : 'Added to collection.'); }
                catch (error) { document.getElementById('libraryStatus').textContent = 'Collection change did not save. ' + error.message; handleError(error); }
                finally { action.disabled = false; }
            };
            controls.append(target, action); card.querySelector('.item-content').appendChild(controls);
        }
        container.appendChild(card);
    });
}

async function loadSavedItems() {
    if (!currentUser) return;
    const version = ++libraryVersion, container = document.getElementById('savedItems');
    const status = document.getElementById('libraryStatus');
    status.textContent = 'Loading your library…'; container.setAttribute('aria-busy', 'true'); container.inert = true;
    try {
        const params=new URLSearchParams({page:String(libraryPage),pageSize:'20'}),query=document.getElementById('librarySearch').value.trim(),collectionId=document.getElementById('collectionSelect').value;
        if(query)params.set('query',query);if(collectionId)params.set('collectionId',collectionId);
        const [page, collections] = await Promise.all([api('/users/me/saved?'+params), api('/users/me/collections')]);
        if (version !== libraryVersion) return;
        userCollections = Array.isArray(collections) ? collections : []; renderCollectionControls();
        libraryScopeCount = page.totalItems;libraryTotalPages=page.totalPages;
        if(libraryPage>Math.max(1,libraryTotalPages)){libraryPage=Math.max(1,libraryTotalPages);await loadSavedItems();return;}
        libraryRows = page.items;mergeResourceMetadata(libraryRows);
        const unavailable = document.getElementById('savedItemsUnavailable');
        const unavailableCount=libraryRows.filter(item=>!item.active).length;
        unavailable.hidden = !unavailableCount;
        unavailable.textContent = unavailableCount + ' resources on this page are currently unavailable.';
        renderLibrary(); status.textContent = '';
    } catch (error) {
        if (version !== libraryVersion) return;
        if(error.status===404&&document.getElementById('collectionSelect').value){document.getElementById('collectionSelect').value='';libraryPage=1;await loadSavedItems();return;}
        status.textContent = 'Could not load your library. Refresh saved to retry. ' + error.message; handleError(error);
    } finally { if (version === libraryVersion) { container.setAttribute('aria-busy', 'false'); container.inert = false; } }
}

let progressVersion = 0;
async function loadProgress() {
    if (!currentUser) return;
    const version = ++progressVersion;
    loadJourneyProgress();
    loadProgressContext(version);
    const panel = document.getElementById("progressPanel");
    const status = panel.querySelector("[data-progress-status]");
    const chart = panel.querySelector("[data-week-chart]");
    status.textContent = "Loading your recorded progress…";
    panel.querySelectorAll("[data-progress-in-progress], [data-progress-completed], [data-progress-week]").forEach(metric => { metric.textContent = "—"; });
    const inProgress = panel.querySelector("[data-in-progress]");
    const completed = panel.querySelector("[data-completed]");
    try {
        const [history, currentState] = await Promise.all([
            api("/users/me/interactions?limit=100"),
            api("/users/me/interactions/progress")
        ]);
        if (version !== progressVersion) return;
        const events = Array.isArray(history) ? history : [];
        const currentEvents = Array.isArray(currentState) ? currentState : [];
        await ensureResourceMetadata(currentEvents.map(row=>row.itemId));if(version!==progressVersion)return;
        const continueEvents = currentEvents.filter(function (entry) { return entry.type === "READ_PROGRESS"; });
        const completeEvents = currentEvents.filter(function (entry) { return entry.type === "COMPLETE"; });
        inProgress.replaceChildren(); completed.replaceChildren();
        continueEvents.forEach(function (entry) {
            const item = items.find(function (candidate) { return Number(candidate.itemId) === Number(entry.itemId); });
            if (item && !completeEvents.some(function (done) { return Number(done.itemId) === Number(entry.itemId); })) inProgress.appendChild(itemCard(item));
        });
        completeEvents.forEach(function (entry) {
            const item = items.find(function (candidate) { return Number(candidate.itemId) === Number(entry.itemId); });
            if (item) completed.appendChild(itemCard(item));
        });
        panel.querySelector('[data-progress-in-progress]').textContent = String(continueEvents.length);
        panel.querySelector('[data-progress-completed]').textContent = String(completeEvents.length);
        document.getElementById('progressWelcome').hidden = events.length > 0 || currentEvents.length > 0;
        panel.querySelector('[data-progress-empty="in-progress"]').hidden = inProgress.children.length > 0;
        panel.querySelector('[data-progress-empty="completed"]').hidden = completed.children.length > 0;
        status.textContent = events.length >= 100
            ? "Current progress uses your latest progress or completion event for each item. The weekly chart shows only your latest 100 activity events, not a complete week. Recom AI does not infer completion from a click."
            : "Current progress uses your latest progress or completion event for each item. The weekly chart uses recorded activity; Recom AI does not infer completion from a click.";
        const counts = Array.from({ length: 7 }, function (_, index) {
            const date = new Date(); date.setHours(0, 0, 0, 0); date.setDate(date.getDate() - (6 - index));
            const key = date.toDateString();
            return { date: date, count: events.filter(function (entry) {
                const occurred = recordedDate(entry.occurredAt); return !Number.isNaN(occurred.getTime()) && occurred.toDateString() === key;
            }).length };
        });
        const dated = events.map(event => recordedDate(event.occurredAt).getTime()).filter(value => !Number.isNaN(value));
        const oldest = dated.length ? Math.min(...dated) : Infinity;
        counts.forEach(entry => { const end = new Date(entry.date); end.setDate(end.getDate() + 1); entry.unknown = events.length >= 100 && end.getTime() <= oldest; });
        const weekTotal = counts.reduce(function (sum, entry) { return sum + entry.count; }, 0);
        panel.querySelector('[data-progress-week]').textContent = String(weekTotal);
        const max = Math.max(1, ...counts.map(function (entry) { return entry.count; }));
        chart.replaceChildren();
        if (weekTotal === 0) {
            chart.setAttribute("aria-label", "No recorded activity in the past seven days");
            const empty = createElement("div", "progress-chart-empty");
            empty.append(
                createElement("strong", "", "Your learning week starts here"),
                createElement("p", "", "When you save, open, or continue a resource, your recorded activity will appear here.")
            );
            chart.appendChild(empty);
            return;
        }
        chart.setAttribute("aria-label", "Recorded activity by day");
        counts.forEach(function (entry) {
            const bar = createElement("div", "weekly-bar");
            const count = createElement("strong", "weekly-bar-count", entry.unknown ? "—" : String(entry.count));
            const fill = createElement("span", "weekly-bar-fill");
            fill.style.height = (entry.count ? Math.max(5, entry.count / max * 100) : 0) + "%";
            const day = new Intl.DateTimeFormat(undefined, { weekday: "short" }).format(entry.date);
            fill.title = entry.count + " recorded activities";
            bar.setAttribute("aria-label", entry.unknown ? day + ": history coverage unavailable" : day + ": " + entry.count + " recorded activities");
            bar.append(count, fill, createElement("small", "", day));
            chart.appendChild(bar);
        });
    } catch (error) { if (version === progressVersion) { status.textContent = "Progress could not be loaded. Revisit this page to retry. " + error.message; handleError(error); } }
}

function interactionSymbol(type) {
    const symbols = { VIEW: "◉", CLICK: "↗", READ_PROGRESS: "◷", COMPLETE: "✓", SAVE: "♡", PURCHASE: "✓", LIKE: "♥", MORE_LIKE: "✦", SHARE: "↗", SKIP: "→", DISLIKE: "−", HIDE: "×", SEARCH: "⌕" };
    return symbols[type] || "•";
}

function activityLabel(type) {
    const labels = { VIEW: "Viewed", CLICK: "Opened", READ_PROGRESS: "In progress", COMPLETE: "Completed", SAVE: "Saved", PURCHASE: "Purchased", LIKE: "Liked", MORE_LIKE: "More like this", SHARE: "Shared", SKIP: "Skipped", DISLIKE: "Disliked", HIDE: "Hidden", SEARCH: "Searched" };
    return labels[type] || "Interacted with";
}

function activityGroupLabel(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "Earlier activity";
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const eventDay = new Date(date); eventDay.setHours(0, 0, 0, 0);
    const difference = Math.round((today - eventDay) / 86400000);
    if (difference === 0) return "Today";
    if (difference === 1) return "Yesterday";
    return new Intl.DateTimeFormat(undefined, { weekday: "long", month: "long", day: "numeric" }).format(date);
}

function formatActivityTime(value) {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "" : new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" }).format(date);
}

function formatDate(value) {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "" : new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric" }).format(date);
}

function installPhaseTwoControls() {
    const howPanel = createElement("section", "content-panel");
    howPanel.id = "howWorksPanel";
    howPanel.innerHTML = '<div class="section-heading page-title"><div><p class="eyebrow">A CLEARER PATH TO YOUR NEXT FIND</p><h2>How recommendations work</h2><p class="section-subtitle">Start with what interests you. Your choices and activity help bring useful resources into view.</p></div></div><ol class="recommendation-steps"><li><span>01</span><div><h3>Tell us what you like</h3><p>Choose areas and topics that feel relevant today. You can change them any time.</p></div></li><li><span>02</span><div><h3>Explore real resources</h3><p>Search the catalog, open learning links, save useful finds, or tell us what is not a fit.</p></div></li><li><span>03</span><div><h3>See why a pick appears</h3><p>Your For You feed ranks available resources using your preferences and recorded signals, with an explanation beside each pick.</p></div></li><li><span>04</span><div><h3>Shape what comes next</h3><p>Saving, rating, and other interactions provide feedback for future recommendations.</p></div></li></ol><details class="technical-details"><summary>See the current ranking details</summary><p class="technical-intro">These values are live configuration from the recommendation service. They describe ranking points and feed allocation; they are not probabilities or guarantees.</p><div class="insight-grid"><section class="admin-card liquid-glass"><h3>Feed balance</h3><div data-feed-mix></div></section><section class="admin-card liquid-glass"><h3>Interaction weights</h3><div data-signal-weights></div></section></div><p class="privacy-note" data-score-meaning></p></details>';
    const dashboardMain = document.querySelector(".dashboard-main");
    dashboardMain.insertBefore(howPanel, dashboardMain.querySelector(".dashboard-footer")); panels.push(howPanel);
    const section = createElement("section", "content-panel");
    section.id = "progressPanel";
    section.innerHTML = '<div class="section-heading page-title"><div><p class="eyebrow">YOUR LEARNING JOURNEY</p><h2>Your progress, in perspective.</h2><p class="section-subtitle">See how your learning activity is taking shape.</p></div></div><div class="progress-overview" aria-label="Your recorded learning progress"><article><span>IN PROGRESS</span><strong data-progress-in-progress>0</strong><small>resources</small></article><article><span>MARKED COMPLETED</span><strong data-progress-completed>0</strong><small>resources</small></article><article><span>RECENT ACTIONS · 7 DAYS</span><strong data-progress-week>0</strong><small>recorded actions</small></article></div><p class="privacy-note" data-progress-status role="status" aria-live="polite"></p><section class="admin-card liquid-glass progress-activity"><div class="section-heading"><div><p class="eyebrow">RECENT SIGNALS</p><h3>Activity this week</h3></div><p>Counts from your latest recorded activity only.</p></div><div class="weekly-activity-chart" data-week-chart aria-label="Recorded activity by day"></div></section><div class="progress-section"><div class="section-heading"><div><p class="eyebrow">PICK UP AGAIN</p><h3>Continue where you left off</h3></div></div><div class="item-grid" data-in-progress></div><div class="progress-empty" data-progress-empty="in-progress" hidden><span>↗</span><div><strong>No resources in progress yet</strong><p>Choose “In progress” on a resource when you want to keep your place.</p></div><button class="button button-secondary" data-go="catalogPanel" type="button">Explore resources</button></div></div><div class="progress-section"><div class="section-heading"><div><p class="eyebrow">MILESTONES</p><h3>Completed</h3></div></div><div class="item-grid" data-completed></div><div class="progress-empty" data-progress-empty="completed" hidden><span>✓</span><div><strong>Your completed resources will appear here</strong><p>We only count an item when you mark it complete.</p></div></div></div>';
    document.querySelector(".dashboard-main").insertBefore(section, document.getElementById("activityPanel"));
    section.insertAdjacentHTML('beforeend', '<div class="workspace-context"><section id="progressFocus" class="admin-card"><h3>Your current direction</h3><p>Loading saved preferences…</p></section><section id="progressNext" class="admin-card"><h3>A useful next step</h3><p>Loading your latest recommendation…</p></section></div>');
    section.querySelector('.progress-section').before(section.querySelector('.workspace-context'));
    section.querySelector('.progress-overview').insertAdjacentHTML('beforebegin', '<div id="progressWelcome" class="workspace-empty" hidden><h3>Every learning journey starts somewhere.</h3><p>Saving and opening resources records activity. Completion is counted only when you mark it yourself.</p><button class="button button-primary" data-go="catalogPanel" type="button">Explore resources</button><button class="button button-secondary" data-go="recommendationsPanel" type="button">View recommendations</button></div>');
    panels.push(section);
    installCollectionControls();
    installAdminSettingsControls();
    const privacy = document.querySelector(".privacy-controls");
    const exportButton = createElement("button", "button button-secondary", "Download my data (JSON)");
    exportButton.type = "button"; exportButton.id = "exportMyData";
    const resetButton = createElement("button", "button button-quiet", "Reset personalization");
    resetButton.type = "button"; resetButton.id = "resetPersonalization";
    privacy.append(exportButton, resetButton);
    exportButton.addEventListener("click", () => downloadAccountData(exportButton));
    resetButton.addEventListener("click", async function () {
        if (!window.confirm("Reset your personalization? This removes preferences, onboarding answers, ratings, activity, inferred interests, and recommendation history. Your account and saved collections remain.")) return;
        resetButton.disabled = true;
        try {
            await api("/users/me/personalization", { method: "DELETE" });
            feedbackByItemId.clear(); feedbackLoaded = true; onboardingCompleted = false;
            await loadWorkspace(); await loadOnboardingStatus();
            showToast("Your personalization data was reset.");
        } catch (error) { handleError(error); }
        finally { resetButton.disabled = false; }
    });
    document.getElementById("catalogSearchForm").insertAdjacentHTML("afterend", '<details class="catalog-filter-panel" id="catalogFilterPanel" open><summary><span>Filters</span><span class="catalog-filter-count" id="catalogFilterCount">No filters selected</span></summary><div class="catalog-filter-row"><label>Topic<select id="topicFilter"><option value="">All topics</option></select></label><label>Type<select id="typeFilter"><option value="">Any type</option></select></label><label>Difficulty<select id="difficultyFilter"><option value="">Any level</option><option>BEGINNER</option><option>INTERMEDIATE</option><option>ADVANCED</option><option>ALL_LEVELS</option><option>UNKNOWN</option></select></label><label>Maximum time<select id="timeFilter"><option value="">Any length</option><option value="15">15 minutes</option><option value="30">30 minutes</option><option value="60">60 minutes</option></select></label><label>Sort<select id="sortFilter"><option value="relevance">Current order</option><option value="title">Title A–Z</option><option value="time">Shortest first</option></select></label></div><div class="catalog-filter-actions"><button type="button" class="button button-quiet" id="clearCatalogFilters">Clear filters</button><button type="button" class="button button-secondary" id="applyCatalogFilters">Apply filters</button></div></details>');
    const filterPanel = document.getElementById("catalogFilterPanel");
    const filterRow = filterPanel.querySelector(".catalog-filter-row");
    const providerLabel = createElement("label", "", "Provider (exact name)");
    const providerInput = createElement("input", ""); providerInput.id = "providerFilter"; providerInput.maxLength = 160; providerInput.placeholder = "Any provider"; providerLabel.appendChild(providerInput); filterRow.appendChild(providerLabel);
    const categoryLabel = document.querySelector("#catalogPanel .filter-label");
    if (categoryLabel) filterRow.prepend(categoryLabel);
    const mobileFilters = matchMedia("(max-width: 700px)");
    if (mobileFilters.matches) filterPanel.open = false;
    mobileFilters.addEventListener("change", event => { filterPanel.open = !event.matches; });
    document.getElementById("applyCatalogFilters").addEventListener("click", () => { filterPanel.open = false; });
    document.getElementById("clearCatalogFilters").addEventListener("click", () => {
        ["categoryFilter", "topicFilter", "typeFilter", "difficultyFilter", "timeFilter", "providerFilter"].forEach(id => { document.getElementById(id).value = ""; });
        document.getElementById("sortFilter").value = "relevance";
        catalogPage = 1;
        updateCatalogFilterCount();
        renderCatalog();
    });
    updateCatalogFilterCount();
    const typeFilter = document.getElementById("typeFilter");
    ["ARTICLE", "VIDEO", "COURSE", "DOCUMENTATION", "GUIDE", "RESOURCE", "TOOL", "PROJECT", "CONTENT", "PRODUCT", "OTHER"].forEach(function (type) {
        typeFilter.appendChild(new Option(type.replaceAll("_", " "), type));
    });
    ["topicFilter", "typeFilter", "difficultyFilter", "timeFilter", "sortFilter", "providerFilter"].forEach(function (id) {
        document.getElementById(id).addEventListener("change", function () { catalogPage = 1; updateCatalogFilterCount(); renderCatalog(); });
    });
}

function updateCatalogFilterCount() {
    const ids = ["categoryFilter", "topicFilter", "typeFilter", "difficultyFilter", "timeFilter", "providerFilter"];
    const count = ids.filter(id => document.getElementById(id)?.value).length;
    const output = document.getElementById("catalogFilterCount");
    if (output) output.textContent = count ? `${count} active` : "No filters selected";
}

function installAdminSettingsControls() {
    const panel = document.getElementById("adminPanel");
    const engagement = createElement("section", "admin-card liquid-glass engagement-card");
    engagement.id = "adminRecommendationEngagement";
    engagement.innerHTML = '<div class="admin-card-title"><span class="admin-icon" aria-hidden="true">◎</span><div><p class="eyebrow">FEED ENGAGEMENT</p><h3>Recommendation reach</h3></div></div><p class="engagement-note" data-engagement-status role="status" aria-live="polite">Loading recommendation engagement…</p><div class="engagement-metrics"><div><strong data-engagement-impressions>—</strong><span>Seen</span></div><div><strong data-engagement-clicks>—</strong><span>Clicked</span></div><div><strong data-engagement-ctr>—</strong><span>Click-through</span></div></div><p class="engagement-note">A recommendation counts as seen after at least half of its card is visible. Click-through is descriptive and does not measure recommendation quality or lift.</p>';
    panel.querySelector(".section-heading").insertAdjacentElement("afterend", engagement);
    const card = createElement("section", "admin-card liquid-glass settings-card");
    card.id = "adminRecommendationSettings";
    card.innerHTML = '<div class="admin-card-title"><span class="admin-icon" aria-hidden="true">⚖</span><div><p class="eyebrow">RANKER SETTINGS</p><h3>Feed balance</h3></div></div><p>Choose the share for familiar, related, and exploratory results. Shares must total 100%. Preview uses your own account as the sample and does not save a recommendation batch.</p><div class="setting-sliders"><label>Familiar <output data-share-output="familiar">70%</output><input type="range" min="0" max="100" value="70" data-share="familiar"></label><label>Related <output data-share-output="related">20%</output><input type="range" min="0" max="100" value="20" data-share="related"></label><label>Discovery <output data-share-output="discovery">10%</output><input type="range" min="0" max="100" value="10" data-share="discovery"></label></div><p data-share-total role="status" aria-live="polite">Total: 100%</p><div class="form-actions"><button type="button" class="button button-secondary" data-settings-preview>Preview top 10</button><button type="button" class="button button-primary" data-settings-save>Save settings</button><button type="button" class="button button-quiet" data-settings-reset>Reset defaults</button></div><div class="admin-list" data-settings-preview-results aria-live="polite"></div><div><h4>Recent settings changes</h4><div class="admin-list" data-settings-audit></div></div>';
    panel.querySelector(".section-heading").insertAdjacentElement("afterend", card);
    const sliders = Array.from(card.querySelectorAll("input[data-share]"));
    sliders.forEach(function (slider) {
        slider.addEventListener("input", function () {
            const value = Number(slider.value);
            card.querySelector('[data-share-output="' + slider.dataset.share + '"]').textContent = value + "%";
            const total = sliders.reduce(function (sum, input) { return sum + Number(input.value); }, 0);
            const message = card.querySelector("[data-share-total]");
            message.textContent = "Total: " + total + "%" + (total === 100 ? "" : " — adjust shares to 100% before saving");
            message.classList.toggle("message-error", total !== 100);
            syncAdminMixValidity();
        });
    });
    card.querySelector("[data-settings-preview]").addEventListener("click", previewAdminSettings);
    card.querySelector("[data-settings-save]").addEventListener("click", saveAdminSettings);
    card.querySelector("[data-settings-reset]").addEventListener("click", async function () {
        setAdminMixSliders({ familiarShare: 0.70, relatedShare: 0.20, discoveryShare: 0.10 });
        await saveAdminSettings();
    });
}

function setAdminMixSliders(settings) {
    adminSettingsHydrating = true;
    [["familiar", settings.familiarShare], ["related", settings.relatedShare], ["discovery", settings.discoveryShare]]
        .forEach(function ([name, value]) {
            const slider = document.querySelector('#adminRecommendationSettings [data-share="' + name + '"]');
            slider.value = Math.round(Number(value) * 100);
            slider.dispatchEvent(new Event("input", { bubbles: true }));
        });
    adminSettingsHydrating = false;
}

function currentAdminMix() {
    const share = function (key) { return Number(document.querySelector('#adminRecommendationSettings [data-share="' + key + '"]').value) / 100; };
    return { familiarShare: share("familiar"), relatedShare: share("related"), discoveryShare: share("discovery") };
}

function syncAdminMixValidity(){
    const card=document.getElementById('adminRecommendationSettings'),mix=currentAdminMix();
    const disabled=adminSettingsBusy||card.dataset.settingsReady!=='true'||Math.abs(mix.familiarShare+mix.relatedShare+mix.discoveryShare-1)>0.0001;
    card.querySelectorAll('[data-settings-save],[data-settings-preview]').forEach(button=>button.disabled=disabled);
}

async function loadAdminSettings(force = false) {
    if (currentUser?.role !== 'ADMIN' || (adminSettingsBusy && !force)) return;
    const accountId = currentUser.userId, version = ++adminSettingsReadVersion;
    const card = document.getElementById('adminRecommendationSettings'), status = document.getElementById('adminConfigStatus');
    card.dataset.settingsReady = 'false'; status.textContent = 'Loading saved configuration…';
    card.querySelectorAll('input,button').forEach(control=>{control.disabled=true;});
    try {
        const [settings,audit] = await Promise.all([api('/admin/settings'),api('/admin/settings/audit?limit=10')]);
        if (version !== adminSettingsReadVersion || currentUser?.userId !== accountId || currentUser.role !== 'ADMIN') return;
        if (!adminSettingsDirty) {setAdminMixSliders(settings);card.dataset.revision=String(settings.revision);}
        const output = document.querySelector('[data-settings-audit]'); output.replaceChildren();
        (audit || []).forEach(entry=>output.appendChild(createElement('p','admin-list-row',entry.settingKey+': '+Number(entry.oldValue).toFixed(2)+' → '+Number(entry.newValue).toFixed(2)+' by '+(entry.actorUsername || 'deleted admin')+' · '+formatDate(entry.changedAt))));
        card.dataset.settingsReady = 'true'; status.textContent = adminSettingsDirty ? 'Unsaved changes retained. Save to update the server.' : 'Saved configuration loaded.';
        if(force&&!adminSettingsDirty){document.getElementById('adminStatus').textContent='Configuration loaded from server.';showToast('Saved configuration reloaded.');}
        card.querySelectorAll('input,button').forEach(control=>{control.disabled=false;});
        syncAdminMixValidity();
    } catch (error) {
        if (version !== adminSettingsReadVersion) return;
        status.textContent = 'Configuration could not be loaded. Refresh the snapshot before editing. '+error.message; handleError(error);
    }
}

async function previewAdminSettings() {
    if (adminSettingsBusy || currentUser?.role !== "ADMIN" || document.getElementById("adminRecommendationSettings").dataset.settingsReady !== "true") return;
    const accountId = currentUser.userId, draft = JSON.stringify(currentAdminMix());
    const button = document.querySelector("[data-settings-preview]");
    const output = document.querySelector("[data-settings-preview-results]");
    button.disabled = true; output.replaceChildren();
    try {
        const mix = currentAdminMix();
        const results = await api("/admin/settings/preview", { method: "POST", body: JSON.stringify({
            sampleUserId: currentUser.userId, familiarShare: mix.familiarShare,
            relatedShare: mix.relatedShare, discoveryShare: mix.discoveryShare
        }) });
        if (currentUser?.userId !== accountId || currentUser.role !== "ADMIN" || draft !== JSON.stringify(currentAdminMix())) return;
        (results || []).forEach(function (entry, index) {
            output.appendChild(createElement("p", "admin-list-row", (index + 1) + ". " + entry.itemTitle + " · " + entry.bucketType));
        });
        showToast("Preview generated without saving a recommendation batch.");
    } catch (error) { handleError(error); }
    finally { button.disabled = false; }
}

async function saveAdminSettings() {
    if (adminSettingsBusy || currentUser?.role !== "ADMIN" || document.getElementById("adminRecommendationSettings").dataset.settingsReady !== "true") return;
    const accountId = currentUser.userId;
    const button = document.querySelector("[data-settings-save]");
    const mix = currentAdminMix();
    if (Math.abs(mix.familiarShare + mix.relatedShare + mix.discoveryShare - 1) > 0.0001) {
        showToast("Feed shares must add up to 100% before saving.", true); return;
    }
    adminSettingsBusy = true;
    document.querySelectorAll("#adminRecommendationSettings button, #adminRecommendationSettings input").forEach(control => { control.disabled = true; });
    try {
        await api("/admin/settings", { method: "PUT",headers:{'X-Expected-Revision':document.getElementById('adminRecommendationSettings').dataset.revision}, body: JSON.stringify(mix) });
        if (currentUser?.userId !== accountId || currentUser.role !== "ADMIN") return;
        adminSettingsDirty = false;
        await loadAdminSettings(true); showToast("Recommendation feed settings saved.");
    } catch (error) { document.getElementById('adminConfigStatus').textContent=error.message;handleError(error); }
    finally { adminSettingsBusy = false; document.querySelectorAll("#adminRecommendationSettings button, #adminRecommendationSettings input").forEach(control => { control.disabled = document.getElementById("adminRecommendationSettings").dataset.settingsReady !== "true"; });syncAdminMixValidity(); }
}

async function loadRecommendationInfo() {
    try {
        const info = await api("/recommendation-info");
        const mix = document.querySelector("[data-feed-mix]");
        const weights = document.querySelector("[data-signal-weights]");
        mix.replaceChildren(); weights.replaceChildren();
        Object.entries(info.feedMix || {}).forEach(function ([name, value]) {
            mix.appendChild(createElement("p", "", name + ": " + (Number(value) * 100).toFixed(0) + "%"));
        });
        Object.entries(info.interactionWeights || {}).forEach(function ([name, value]) {
            weights.appendChild(createElement("p", "", name.toLowerCase().replaceAll("_", " ") + ": " + Number(value).toFixed(2)));
        });
        document.querySelector("[data-score-meaning]").textContent = info.scoreMeaning
            + " Popularity and collaborative signals require at least " + info.popularityMinimumUsers
            + " other users before they contribute.";
    } catch (error) { handleError(error); }
}

async function loadAdminData() {
    if (currentUser?.role !== "ADMIN") return;
    const version = ++adminLoadVersion, accountId = currentUser.userId;
    const status = document.getElementById('adminStatus'); status.textContent = 'Loading the current catalog snapshot…';
    try {
        const [categoryRows, itemRows, summary] = await Promise.all([api('/admin/categories'),api('/admin/items/composition'),api('/admin/analytics/summary')]);
        if (version !== adminLoadVersion || currentUser?.userId !== accountId || currentUser.role !== 'ADMIN') return;
        adminCacheCategories = categoryRows || []; adminComposition = itemRows || []; adminCacheItems = []; adminSnapshotData = summary;
        renderAdminCategories(adminCacheCategories); renderAdminItems(adminCacheItems); renderAdminCategoryOptions(adminCacheCategories); renderAdminSnapshot();
        status.textContent = 'Current snapshot loaded. Catalog changes affect all users.';
        loadAdminSettings(); loadAdminEngagement();
    } catch (error) { if (version === adminLoadVersion) { status.textContent = 'Could not load the admin workspace. Refresh snapshot to retry. ' + error.message; handleError(error); } }
}

async function loadAdminEngagement() {
    const accountId = currentUser?.userId;
    const card = document.getElementById("adminRecommendationEngagement");
    if (!card) return;
    const status = card.querySelector("[data-engagement-status]");
    try {
        const metrics = await api("/admin/analytics/recommendation-engagement");
        if (currentUser?.role !== "ADMIN" || currentUser.userId !== accountId) return;
        card.querySelector("[data-engagement-impressions]").textContent = Number(metrics.impressions).toLocaleString();
        card.querySelector("[data-engagement-clicks]").textContent = Number(metrics.clicks).toLocaleString();
        card.querySelector("[data-engagement-ctr]").textContent = metrics.available
            ? Number(metrics.clickThroughRatePercent).toFixed(1) + "%" : "—";
        status.textContent = metrics.available ? "Counts from cards actually seen in the personalized feed."
            : "No recommendation impression data yet. Impressions start after cards are at least half visible.";
    } catch (error) {
        status.textContent = "Recommendation engagement could not be loaded.";
        handleError(error);
    }
}

function renderAdminCategoryOptions(categoryList) {
    const select = document.getElementById("adminCategorySelect");
    if (!select) {
        return;
    }
    const list = categoryList || categories;
    const selected = select.value;
    select.replaceChildren();
    select.appendChild(new Option("Choose an active category", ""));
    list.filter(function (category) { return category.active; }).forEach(function (category) {
        select.appendChild(new Option(category.name, String(category.categoryId)));
    });
    select.value = selected;
}

function renderAdminCategories(categoryList) {
    const container = document.getElementById("adminCategories");
    container.replaceChildren();
    categoryList.forEach(function (category) {
        const row = createElement("div", "admin-list-row");
        const copy = createElement("div", "admin-list-copy");
        copy.appendChild(createElement("strong", "", category.name));
        copy.appendChild(createElement("small", "", category.description || "No description"));
        row.appendChild(copy);
        row.appendChild(createElement("span", category.active ? "status-badge" : "status-badge inactive", category.active ? "ACTIVE" : "PAUSED"));
        const edit = createElement("button", "small-action", "Edit");
        edit.type = "button";
        edit.addEventListener("click", function () { editCategory(category); });
        row.appendChild(edit);
        const toggle = createElement("button", "small-action", category.active ? "Pause" : "Enable");
        toggle.type = "button";
        toggle.addEventListener("click", async function () { toggle.disabled = true; try { await toggleCategory(category); } finally { toggle.disabled = false; } });
        row.appendChild(toggle);
        container.appendChild(row);
    });
}

function renderAdminItems(itemList) {
    adminCacheItems = itemList;
    const select = document.getElementById('adminCatalogCategory'), selected = select.value;
    select.replaceChildren(new Option('All categories',''));
    adminCacheCategories.forEach(category=>select.appendChild(new Option(category.name,String(category.categoryId))));
    select.value = adminCacheCategories.some(category=>String(category.categoryId)===selected) ? selected : '';
    renderAdminTable();
}

function editCategory(category) {
    if (adminCategoryDirty && !confirm("Discard the unsaved category draft?")) return;
    adminCategoryDirty = false; document.getElementById("adminEditor").open = true;
    const form = document.getElementById("categoryForm");
    form.elements.categoryId.value = category.categoryId;
    form.elements.name.value = category.name;
    form.elements.description.value = category.description || "";
    document.getElementById("categoryFormTitle").textContent = "Edit category";
    form.querySelector(".cancel-edit").hidden = false;
    form.scrollIntoView({ behavior: "smooth", block: "center" });
}

function editItem(item) {
    if (adminItemDirty && !confirm("Discard the unsaved resource draft?")) return;
    adminEditingItem = item;
    adminItemDirty = false; document.getElementById("adminEditor").open = true;
    const form = document.getElementById("itemForm");
    form.elements.itemId.value = item.itemId;
    form.dataset.revision=String(item.revision);document.getElementById('itemEditStatus').textContent='';
    form.elements.categoryId.value = item.categoryId;
    form.elements.title.value = item.title;
    form.elements.description.value = item.description || "";
    form.elements.type.value = item.type;
    form.elements.difficulty.value = item.difficulty || "ALL_LEVELS";
    form.elements.estimatedMinutes.value = item.estimatedMinutes || "";
    form.elements.sourceName.value = item.sourceName || "";
    form.elements.externalUrl.value = item.externalUrl || "";
    form.elements.editorPick.checked = Boolean(item.editorPick);
    document.getElementById("itemFormTitle").textContent = "Edit catalog item";
    form.querySelector(".cancel-edit").hidden = false;
    form.scrollIntoView({ behavior: "smooth", block: "center" });
}

function resetForm(formId) {
    if (formId === "itemForm") adminItemDirty = false;
    if (formId === "categoryForm") adminCategoryDirty = false;
    const form = document.getElementById(formId);
    form.reset();
    if (form.elements.categoryId && formId === "categoryForm") {
        form.elements.categoryId.value = "";
        document.getElementById("categoryFormTitle").textContent = "Add a category";
    }
    if (formId === "itemForm") {
        adminEditingItem = null;
        form.elements.itemId.value = "";
        document.getElementById("itemFormTitle").textContent = "Add an item";
    }
    form.querySelector(".cancel-edit").hidden = true;
}

async function toggleCategory(category) {
    if (adminCategoryMutations.has(category.categoryId)) return;
    if (category.active && !confirm("Pause this category? It will be unavailable for new public discovery.")) return;
    adminCategoryMutations.add(category.categoryId);
    try {
        await api("/admin/categories/" + category.categoryId + "/active", {
            method: "PATCH",
            body: JSON.stringify({ active: !category.active })
        });
        await refreshCatalogData();
        await loadAdminData();
        showToast(category.active ? "Category paused." : "Category enabled.");
    } catch (error) {
        handleError(error);
    } finally { adminCategoryMutations.delete(category.categoryId); }
}

async function toggleItem(item) {
    if (adminItemMutations.has(item.itemId)) return;
    adminItemMutations.add(item.itemId);
    try {
        await api("/admin/items/" + item.itemId + "/active", {
            method: "PATCH",
            body: JSON.stringify({ active: !item.active })
        });
        await refreshCatalogData();
        await loadAdminData();
        showToast(item.active ? "Item paused." : "Item enabled.");
    } catch (error) {
        handleError(error);
    } finally { adminItemMutations.delete(item.itemId); }
}

async function refreshCatalogData() {
    const result = await Promise.all([api("/categories"), api("/items/search?pageSize=50")]);
    categories = Array.isArray(result[0]) ? result[0] : [];
    mergeResourceMetadata(result[1]?.items || []);
    renderCatalog();
    renderCategoryFilters();
    renderFeaturedItems();
    renderAdminCategoryOptions();
}

function setAuthMode(mode) {
    const forms = { login: "loginForm", register: "registerForm", forgot: "forgotPasswordForm", reset: "resetPasswordForm" };
    if (!forms[mode]) mode = "login";
    Object.values(forms).forEach(function (id) { document.getElementById(id).hidden = id !== forms[mode]; });
    const register = mode === "register";
    const recovery = mode === "forgot" || mode === "reset";
    document.querySelector(".auth-switch").hidden = recovery;
    document.getElementById("showLogin").classList.toggle("active", !register);
    document.getElementById("showLogin").setAttribute("aria-selected", String(!register));
    document.getElementById("showLogin").tabIndex = register ? -1 : 0;
    document.getElementById("showRegister").classList.toggle("active", register);
    document.getElementById("showRegister").setAttribute("aria-selected", String(register));
    document.getElementById("showRegister").tabIndex = register ? 0 : -1;
    const copy = {
        login: ["Good to see you again", "Sign in to pick up where your curiosity left off."],
        register: ["Start your Recom AI account", "Create your profile and start discovering what fits."],
        forgot: ["Reset your password", "Enter your email and we’ll send reset instructions if an account matches."],
        reset: ["Choose a new password", "Use a new password you have not used here before."]
    }[mode];
    document.getElementById("authTitle").textContent = copy[0];
    document.getElementById("authSubtitle").textContent = copy[1];
    showAuthMessage("");
}

function openAuth(mode) {
    previousFocus = document.activeElement;
    appShell.hidden = true;
    landingPage.hidden = false;
    authPanel.hidden = false;
    document.body.classList.add("dialog-open");
    setAuthMode(mode || "login");
    const formId = { login: "loginForm", register: "registerForm", forgot: "forgotPasswordForm", reset: "resetPasswordForm" }[mode || "login"];
    const input = document.querySelector("#" + formId + " input");
    if (input) {
        window.setTimeout(function () { input.focus(); }, 0);
    }
}

function closeAuth() {
    if (authRequestInProgress) return;
    authPanel.hidden = true;
    document.body.classList.remove("dialog-open");
    if (previousFocus && typeof previousFocus.focus === "function") {
        previousFocus.focus();
    }
}

document.querySelectorAll("[data-auth]").forEach(function (button) {
    button.addEventListener("click", function () { openAuth(button.dataset.auth); });
});
document.querySelectorAll("[data-close-auth]").forEach(function (button) {
    button.addEventListener("click", closeAuth);
});
document.addEventListener("keydown", function (event) {
    if (event.key === "Escape" && !authPanel.hidden) {
        closeAuth();
        return;
    }
    if (event.key === "Tab" && !authPanel.hidden) {
        const focusable = Array.from(authPanel.querySelectorAll("button:not([disabled]), input:not([disabled]), a[href], select:not([disabled]), textarea:not([disabled])"))
            .filter(function (element) { return !element.closest("[hidden]"); });
        if (!focusable.length) {
            return;
        }
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    }
});
document.getElementById("discoveryForm")?.addEventListener("submit", function (event) {
    event.preventDefault();
    const input = document.getElementById("discoveryQuery");
    const query = input.value.trim();
    const message = document.getElementById("discoveryMessage");
    message.textContent = "";
    if (!query) {
        message.textContent = "Enter a topic or keyword to search the resource catalog.";
        input.focus();
        return;
    }
    if (query.length > 120) {
        message.textContent = "Search text must be 120 characters or fewer.";
        input.focus();
        return;
    }
    pendingHomepageQuery = query;
    if (currentUser && onboardingCompleted) {
        processPendingHomepageSearch();
        return;
    }
    if (currentUser) {
        showPanel("onboardingPanel");
        showToast("Your search is kept until you finish the quick-start profile.");
        return;
    }
    openAuth("register");
    showAuthMessage("We’ll keep “" + query + "” while you create your account and finish the quick start.");
});

document.getElementById("showLogin").addEventListener("click", function () {
    setAuthMode("login");
});

document.getElementById("showRegister").addEventListener("click", function () {
    setAuthMode("register");
});

document.getElementById("duplicateRegistrationAction").addEventListener("click", function () {
    setAuthMode("login");
    const loginEmail = document.querySelector("#loginForm [name=email]");
    loginEmail.value = document.querySelector("#registerForm [name=email]").value.trim();
    loginEmail.focus();
});
document.getElementById("forgotPasswordLink").addEventListener("click", function () {
    const email = document.querySelector("#loginForm [name=email]").value.trim();
    setAuthMode("forgot");
    document.querySelector("#forgotPasswordForm [name=email]").value = email;
    document.querySelector("#forgotPasswordForm [name=email]").focus();
});
document.getElementById("backToSignInFromForgot").addEventListener("click", function () { setAuthMode("login"); });
document.getElementById("backToSignInFromReset").addEventListener("click", function () { passwordResetToken = null; setAuthMode("login"); });
document.getElementById("requestAnotherReset").addEventListener("click", function () {
    passwordResetToken = null;
    setAuthMode("forgot");
});
document.querySelector(".auth-switch").addEventListener("keydown", function (event) {
    if (authRequestInProgress) return;
    const tabs = [document.getElementById("showLogin"), document.getElementById("showRegister")];
    const current = tabs.indexOf(document.activeElement);
    if (current < 0) return;
    let next = current;
    if (event.key === "ArrowRight" || event.key === "ArrowDown") next = (current + 1) % tabs.length;
    else if (event.key === "ArrowLeft" || event.key === "ArrowUp") next = (current + tabs.length - 1) % tabs.length;
    else if (event.key === "Home") next = 0;
    else if (event.key === "End") next = tabs.length - 1;
    else return;
    event.preventDefault();
    setAuthMode(next === 0 ? "login" : "register");
    tabs[next].focus();
});

document.getElementById("loginForm").addEventListener("submit", async function (event) {
    event.preventDefault();
    if (authRequestInProgress) return;
    showAuthMessage("");
    const submitButton = event.currentTarget.querySelector("[type=submit]");
    const data = new FormData(event.currentTarget);
    const credentials = { email: data.get("email"), password: data.get("password") };
    setAuthRequestState(true);
    try {
        const profile = await api("/auth/login", {
            method: "POST",
            body: JSON.stringify(credentials)
        });
        openDashboard(profile);
    } catch (error) {
        handleError(error, "auth");
    } finally {
        setAuthRequestState(false);
    }
});

document.getElementById("registerForm").addEventListener("submit", async function (event) {
    event.preventDefault();
    if (authRequestInProgress) return;
    showAuthMessage("");
    const form = event.currentTarget;
    const data = new FormData(form);
    const registration = {
        displayName: data.get("displayName"),
        username: data.get("username"),
        email: data.get("email"),
        password: data.get("password")
    };
    setAuthRequestState(true);
    let accountCreated = false;
    try {
        const profile = await api("/auth/register", { method: "POST", body: JSON.stringify(registration) });
        accountCreated = true;
        form.reset();
        if (!profile || !profile.username) {
            throw new Error("The server created your account but did not return its profile.");
        }
        openDashboard(profile);
    } catch (error) {
        if (accountCreated) {
            setAuthMode("login");
            const loginEmail = document.querySelector("#loginForm [name=email]");
            loginEmail.value = registration.email;
            showAuthMessage("Your account was created, but we couldn't finish signing you in. Please sign in to continue.", false);
            loginEmail.focus();
        } else if (error instanceof TypeError) {
            showAuthMessage("We couldn't confirm your registration. Check your connection and try again.", false);
        } else if (error && error.status === 409
                && error.message === "That username or email address is already registered.") {
            showAuthMessage("Those signup details are already in use. Sign in with your existing account, or try different details.", false);
            document.getElementById("duplicateRegistrationAction").hidden = false;
        } else {
            handleError(error, "auth");
        }
    } finally {
        setAuthRequestState(false);
    }
});

document.getElementById("forgotPasswordForm").addEventListener("submit", async function (event) {
    event.preventDefault();
    if (authRequestInProgress) return;
    showAuthMessage("");
    const email = new FormData(event.currentTarget).get("email");
    setAuthRequestState(true);
    try {
        const result = await api("/auth/password-reset/request", { method: "POST", body: JSON.stringify({ email }) });
        showAuthMessage(result.message, true);
    } catch (error) {
        handleError(error, "auth");
    } finally {
        setAuthRequestState(false);
    }
});

document.getElementById("resetPasswordForm").addEventListener("submit", async function (event) {
    event.preventDefault();
    if (authRequestInProgress) return;
    showAuthMessage("");
    const form = event.currentTarget;
    const data = new FormData(form);
    const password = data.get("password");
    const confirmPassword = data.get("confirmPassword");
    if (password !== confirmPassword) {
        showAuthMessage("The passwords do not match.");
        return;
    }
    if (!passwordResetToken) {
        showAuthMessage("This reset link is invalid or expired. Request a new password reset link.");
        return;
    }
    setAuthRequestState(true);
    try {
        const result = await api("/auth/password-reset/complete", {
            method: "POST", body: JSON.stringify({ token: passwordResetToken, password, confirmPassword })
        });
        passwordResetToken = null;
        form.reset();
        setAuthMode("login");
        showAuthMessage(result.message, true);
    } catch (error) {
        passwordResetToken = null;
        handleError(error, "auth");
    } finally {
        setAuthRequestState(false);
    }
});

document.getElementById("logoutButton").addEventListener("click", async function () {
    const button = document.getElementById("logoutButton");
    if (logoutInProgress) return;
    logoutInProgress = true;
    button.disabled = true;
    try {
        await api("/auth/logout", { method: "POST" });
    } catch (error) {
        document.getElementById("settingsStatus").textContent = "Sign out could not be confirmed. Your session status is unknown; please retry.";
        showToast(error instanceof TypeError
            ? "We couldn't reach the server to confirm sign out. Your session status is unknown; retry when your connection returns."
            : "The server couldn't confirm sign out. Your session status is unknown; please retry.", true);
        return;
    } finally {
        logoutInProgress = false;
        button.disabled = false;
        document.getElementById("settingsLogout").disabled = false;
    }
    currentUser = null;
    resetLearningWorkspace();
    setLandingSessionControls(false);
    appShell.hidden = true;
    landingPage.hidden = false;
    authPanel.hidden = false;
    document.body.classList.add("dialog-open");
    setAuthMode("login");
    document.getElementById("loginForm").reset();
    showAuthMessage("You are signed out.", true);
    document.querySelector("#loginForm input").focus();
});
document.querySelectorAll("[data-logout]").forEach(function (button) {
    button.addEventListener("click", function () {
        document.getElementById("logoutButton").click();
    });
});

installPhaseTwoControls();
installAccountCommand();
installPhaseThreeControls();
const mobileMenu = document.querySelector(".mobile-menu-toggle");
const landingHeader = document.querySelector("#landingPage .site-header");
mobileMenu?.addEventListener("click", function () {
    const expanded = mobileMenu.getAttribute("aria-expanded") !== "true";
    mobileMenu.setAttribute("aria-expanded", String(expanded));
    landingHeader?.classList.toggle("mobile-nav-open", expanded);
});
document.addEventListener("keydown", function (event) {
    if (event.key === "Escape" && mobileMenu?.getAttribute("aria-expanded") === "true") {
        mobileMenu.setAttribute("aria-expanded", "false");
        landingHeader?.classList.remove("mobile-nav-open");
        mobileMenu.focus();
    }
});
document.addEventListener("click", function (event) {
    if (landingHeader?.classList.contains("mobile-nav-open") && !landingHeader.contains(event.target)) {
        mobileMenu?.setAttribute("aria-expanded", "false");
        landingHeader.classList.remove("mobile-nav-open");
    }
});
document.querySelectorAll("#landingNav a").forEach(link => link.addEventListener("click", function () {
    mobileMenu?.setAttribute("aria-expanded", "false");
    landingHeader?.classList.remove("mobile-nav-open");
}));
document.querySelectorAll(".nav-link").forEach(function (link) {
    link.addEventListener("click", function () { showPanel(link.dataset.panel); });
});
document.querySelectorAll("[data-go]").forEach(function (button) {
    button.addEventListener("click", function () { showPanel(button.dataset.go); });
});
document.querySelectorAll("[data-goal]").forEach(function (button) {
    button.setAttribute("aria-pressed", String(button.dataset.goal === temporaryGoal));
    button.addEventListener("click", function () {
        temporaryGoal = button.dataset.goal;
        sessionStorage.setItem("recomai.temporaryGoal", temporaryGoal);
        document.querySelectorAll("[data-goal]").forEach(function (option) {
            option.setAttribute("aria-pressed", String(option === button));
        });
        document.getElementById("goalSelectionNote").textContent = "Temporary selection · it is saved only if you choose it in onboarding";
    });
});
document.querySelector("[data-start-path]").addEventListener("click", function () {
    requestedLandingDestination = "recommendationsPanel";
    sessionStorage.setItem("recomai.landingDestination", requestedLandingDestination);
    if (currentUser) {
        if (!onboardingStatusLoaded) { loadOnboardingStatus(); return; }
        if (onboardingStatusLoaded && !onboardingCompleted) {
            applyTemporaryGoal();
            showPanel("onboardingPanel");
        } else {
            document.getElementById("goalSelectionNote").textContent = "Your saved profile still shapes For You. This choice is temporary.";
            showPanel("recommendationsPanel");
        }
        return;
    }
    window.history.replaceState(null, "", "#app/recommendationsPanel");
    openAuth("register");
});
document.querySelector("[data-go-explore]").addEventListener("click", function () {
    requestedLandingDestination = "catalogPanel";
    sessionStorage.setItem("recomai.landingDestination", requestedLandingDestination);
    if (currentUser) { showPanel("catalogPanel"); return; }
    window.history.replaceState(null, "", "#app/catalogPanel");
    openAuth("register");
});
document.getElementById("categoryFilter").addEventListener("change", function () {
    catalogPage = 1;
    updateCatalogFilterCount();
    renderCatalog(false);
});
document.getElementById("catalogSearchForm").addEventListener("submit", function (event) {
    event.preventDefault();
    catalogPage = 1;
    renderCatalog(true);
});
window.addEventListener("popstate", syncWorkspaceRoute);
window.addEventListener("hashchange", syncWorkspaceRoute);
document.getElementById("clearCatalogSearch").addEventListener("click", function () {
    document.getElementById("catalogSearchQuery").value = "";
    catalogPage = 1;
    renderCatalog(false);
});
document.getElementById("catalogPreviousPage").addEventListener("click", function () {
    if (catalogPage > 1) {
        catalogPage -= 1;
        renderCatalog(false);
    }
});
document.getElementById("catalogNextPage").addEventListener("click", function () {
    catalogPage += 1;
    renderCatalog(false);
});
document.getElementById("retryCatalog").addEventListener("click", function () { renderCatalog(false); });
document.querySelector("[data-close-resource]").addEventListener("click", function () { document.getElementById("resourceDetailsDialog").close(); });
document.getElementById("resourceDetailsDialog").addEventListener("click", function (event) {
    if (event.target === event.currentTarget) event.currentTarget.close();
});
document.getElementById("onboardingNiche").addEventListener("change", renderOnboardingTopics);
document.getElementById("refreshInterestProfile").addEventListener("click", loadInterestProfile);
document.getElementById("generateRecommendations").addEventListener("click", generateRecommendations);
document.getElementById("refreshActivity").addEventListener("click", loadActivity);
document.getElementById("clearHistoryButton").addEventListener("click", async function (event) {
    const button = event.currentTarget;
    if (!window.confirm("Clear your activity, searches, saved events, resource and journey progress, inferred topic signals, and saved recommendation batches? Collections, journey plans, ratings and explicit preferences will remain.")) return;
    button.disabled = true;
    try {
        await api("/users/me/interactions", { method: "DELETE" });
        activityRows = [];
        activityCursor=null;document.getElementById('activityMore').hidden=true;
        document.getElementById("activityList").replaceChildren();
        document.getElementById("activityEmpty").hidden = false;
        document.getElementById("recommendationList").replaceChildren();
        document.getElementById("recommendationsEmpty").hidden = false;
        await loadInterestProfile();
        await loadActivity();showToast("Activity and search history cleared. Recorded resource and journey progress was also cleared. Collections, journey plans, ratings and explicit preferences were kept.");
    } catch (error) {
        handleError(error);
    } finally {
        button.disabled = false;
    }
});

document.getElementById("deleteAccountButton").addEventListener("click", async function (event) {
    if (window.prompt("This permanently deletes your account and associated data. Type DELETE to confirm.") !== "DELETE") return;
    const button = event.currentTarget;
    if (button.disabled) return;
    button.disabled = true;
    document.getElementById("settingsDelete").disabled = true;
    document.getElementById("settingsStatus").textContent = "Deleting your account…";
    try {
        await api("/users/me", { method: "DELETE" });
        currentUser = null;
        resetLearningWorkspace();
        setLandingSessionControls(false);
        onboardingCompleted = false;
        feedbackByItemId.clear();
        feedbackLoaded = false;
        appShell.hidden = true;
        landingPage.hidden = false;
        authPanel.hidden = true;
        showToast("Your account and associated data were deleted.");
    } catch (error) {
        document.getElementById("settingsStatus").textContent = "Account deletion failed. Your account remains available. " + error.message;
        handleError(error);
    } finally {
        button.disabled = false;
        document.getElementById("settingsDelete").disabled = false;
    }
});
document.getElementById("refreshSaved").addEventListener("click", loadSavedItems);

document.getElementById("profileForm").addEventListener("submit", async function (event) {
    event.preventDefault();
    const input = document.getElementById("displayNameInput"), status = document.getElementById("profileStatus"), button = event.currentTarget.querySelector('[type=submit]');
    if (button.disabled || !currentUser) return;
    const name = input.value.trim(), accountId = currentUser.userId;
    if (!name || name.length > 100) { status.textContent = "Enter a display name between 1 and 100 characters."; input.focus(); return; }
    accountProfileVersion++;
    button.disabled = true; input.disabled = true; status.textContent = "Saving your display name…";
    try {
        const profile = await api("/users/me/profile", { method: "PATCH", body: JSON.stringify({ displayName: name }) });
        if (currentUser?.userId !== accountId) return;
        accountProfileVersion++;
        currentUser = profile; profileDraftDirty = false; renderAccountIdentity(profile);
        document.getElementById("greeting").textContent = "Welcome back, " + profile.displayName;
        document.getElementById("sidebarName").textContent = profile.displayName;
        document.getElementById("sidebarAvatar").textContent = profile.displayName.charAt(0).toUpperCase();
        document.getElementById("headerAvatar").textContent = profile.displayName.charAt(0).toUpperCase();
        input.value = profile.displayName; status.textContent = "Your display name is saved."; showToast("Your profile has been updated.");
    } catch (error) { status.textContent = "Could not save your display name. " + error.message; handleError(error); }
    finally { button.disabled = false; input.disabled = false; }
});

document.getElementById("onboardingForm").addEventListener("submit", async function (event) {
    event.preventDefault();
    if (document.querySelector('[data-onboarding-step="3"]').hidden) { document.getElementById("onboardingContinue").click(); return; }
    if (!document.getElementById("onboardingPurpose").value || !document.getElementById("onboardingNiche").value) { document.getElementById("onboardingStatus").textContent = "Choose a goal and an area before saving."; return; }
    const selectedTopics = Array.from(document.querySelectorAll("#onboardingTopics input:checked"))
        .map(function (input) { return Number(input.value); });
    if (selectedTopics.length > 3) { wizardStep(2); document.getElementById("onboardingStatus").textContent = "Choose up to three starter topics before saving."; return; }
    const button = event.currentTarget.querySelector("[type=submit]");
    if (button.disabled) return;
    button.disabled = true;
    document.getElementById("onboardingStatus").textContent = "Saving your profile…";
    try {
        await api("/users/me/onboarding", {
            method: "POST",
            body: JSON.stringify({
                purpose: document.getElementById("onboardingPurpose").value,
                categoryId: Number(document.getElementById("onboardingNiche").value),
                interestIds: selectedTopics
            })
        });
        onboardingCompleted = true;
        sessionStorage.removeItem("recomai.temporaryGoal");
        showToast("Your Explore feed is ready. We’ll learn from your activity over time.");
        await loadWorkspace();
        if (pendingHomepageQuery) {
            await processPendingHomepageSearch();
        } else {
            const destination = requestedLandingDestination || "recommendationsPanel";
            requestedLandingDestination = "";
            sessionStorage.removeItem("recomai.landingDestination");
            showPanel(destination);
            if (destination === "recommendationsPanel") await generateRecommendations();
        }
    } catch (error) {
        document.getElementById("onboardingStatus").textContent = "Could not save your profile. Your choices are preserved. " + error.message;
        handleError(error);
    } finally {
        button.disabled = false;
    }
});

document.querySelectorAll(".cancel-edit").forEach(function (button) {
    button.addEventListener("click", function () { resetForm(button.dataset.form); });
});

document.getElementById("catalogCsvFile").addEventListener("change", async function (event) {
    const file = event.currentTarget.files && event.currentTarget.files[0];
    if (!file) return;
    const fileVersion = ++csvPreviewVersion;
    if (!/\.csv$/i.test(file.name)) { showCatalogImportMessage("Choose a .csv file.", true); event.currentTarget.value = ""; return; }
    if (file.size > 1_000_000) {
        showCatalogImportMessage("CSV must be no larger than 1 MB.", true);
        event.currentTarget.value = "";
        return;
    }
    const contents = await file.text();
    if (fileVersion !== csvPreviewVersion) return;
    document.getElementById("catalogCsvText").value = contents;
    document.getElementById("catalogCsvText").dispatchEvent(new Event("input"));
    showCatalogImportMessage("File loaded. Preview it to validate every row.");
});

document.getElementById("previewCatalogCsv").addEventListener("click", previewCatalogCsv);
document.getElementById("commitCatalogCsv").addEventListener("click", commitCatalogCsv);
document.getElementById("bulkCatalogActive").addEventListener("click", bulkCatalogActive);

let csvPreviewVersion = 0;
async function previewCatalogCsv() {
    const version = ++csvPreviewVersion, csv = document.getElementById("catalogCsvText").value;
    const button = document.getElementById("previewCatalogCsv");
    button.disabled = true;
    document.getElementById("commitCatalogCsv").disabled = true;
    const results = document.getElementById("catalogCsvPreview");
    results.replaceChildren();
    showCatalogImportMessage("Validating your CSV with the server…");
    try {
        const preview = await api("/admin/items/import/preview", {
            method: "POST", body: JSON.stringify({ csv })
        });
        if (version !== csvPreviewVersion || csv !== document.getElementById("catalogCsvText").value) return;
        preview.rows.forEach(function (row) {
            const line = createElement("p", row.valid ? "csv-row-valid" : "csv-row-invalid");
            line.textContent = "Row " + row.line + " · " + (row.title || "Untitled") + ": "
                + (row.valid ? "ready" : row.errors.join(" "));
            results.appendChild(line);
        });
        showCatalogImportMessage(preview.validRows + " valid, " + preview.invalidRows + " invalid rows.", !preview.canImport);
        document.getElementById("commitCatalogCsv").disabled = !preview.canImport;
    } catch (error) {
        if (version !== csvPreviewVersion) return;
        if (error.status === 401) handleError(error);
        showCatalogImportMessage(error.message || "Could not validate this CSV.", true);
    } finally { button.disabled = false; }
}

async function commitCatalogCsv() {
    if (currentUser?.role !== "ADMIN") return;
    const accountId = currentUser.userId;
    const button = document.getElementById("commitCatalogCsv");
    if (button.disabled) return;
    button.disabled = true;
    document.getElementById("previewCatalogCsv").disabled = true;
    document.getElementById("catalogCsvText").readOnly = true;
    document.getElementById("catalogCsvFile").disabled = true;
    showCatalogImportMessage("Importing the validated CSV…");
    try {
        const result = await api("/admin/items/import/commit", {
            method: "POST", body: JSON.stringify({ csv: document.getElementById("catalogCsvText").value })
        });
        if (currentUser?.userId !== accountId || currentUser.role !== "ADMIN") return;
        showCatalogImportMessage(result.imported + " resources imported; " + result.skippedExisting + " existing URLs skipped.");
        document.getElementById("catalogCsvPreview").replaceChildren();
        await refreshCatalogData();
        await loadAdminData();
    } catch (error) {
        if (currentUser?.userId !== accountId) return;
        if (error.status === 401) handleError(error);
        showCatalogImportMessage(error.message || "Import failed. Review the CSV and try again.", true);
    } finally {
        document.getElementById("previewCatalogCsv").disabled = false;
        document.getElementById("catalogCsvText").readOnly = false;
        document.getElementById("catalogCsvFile").disabled = false;
    }
}

function showCatalogImportMessage(message, isError) {
    const output = document.getElementById("catalogCsvMessage");
    output.textContent = message;
    output.classList.toggle("message-error", Boolean(isError));
}

async function bulkCatalogActive() {
    const ids = Array.from(document.querySelectorAll(".admin-item-select:checked"))
        .map(function (input) { return Number(input.value); });
    const active = document.getElementById("bulkCatalogState").value === "active";
    if (!ids.length) { showToast("Select at least one catalog item."); return; }
    const button = document.getElementById("bulkCatalogActive");
    button.disabled = true;
    try {
        const result = await api("/admin/items/active", {
            method: "PATCH", body: JSON.stringify({ itemIds: ids, active: active })
        });
        await refreshCatalogData();
        await loadAdminData();
        showToast(result.updated + " catalog items updated.");
    } catch (error) { handleError(error); }
    finally { button.disabled = false; }
}

document.getElementById("categoryForm").addEventListener("submit", async function (event) {
    event.preventDefault();
    const form = event.currentTarget;
    const submit = form.querySelector("[type=submit]");
    if (submit.disabled) return;
    const data = new FormData(form);
    const categoryId = data.get("categoryId");
    const request = { name: data.get("name"), description: data.get("description") || null };
    form.querySelectorAll("input,select,textarea,button").forEach(control => { control.disabled = true; });
    try {
        await api(categoryId ? "/admin/categories/" + categoryId : "/admin/categories", {
            method: categoryId ? "PUT" : "POST",
            body: JSON.stringify(request)
        });
        resetForm("categoryForm");
        await refreshCatalogData();
        await loadAdminData();
        showToast(categoryId ? "Category updated." : "Category created.");
    } catch (error) {
        handleError(error);
    } finally { form.querySelectorAll("input,select,textarea,button").forEach(control => { control.disabled = false; }); }
});

document.getElementById("itemForm").addEventListener("submit", async function (event) {
    event.preventDefault();
    const form = event.currentTarget;
    const submit = form.querySelector("[type=submit]");
    if (submit.disabled) return;
    const data = new FormData(form);
    const itemId = data.get("itemId");
    const price = data.get("price");
    const previous = String(adminEditingItem?.itemId) === String(itemId) ? adminEditingItem : adminCacheItems.find(item => String(item.itemId) === String(itemId));
    const request = {
        categoryId: Number(data.get("categoryId")),
        title: data.get("title"),
        description: data.get("description") || null,
        type: data.get("type"),
        externalUrl: data.get("externalUrl") || null,
        price: price === null ? previous?.price ?? null : price === "" ? null : Number(price),
        sourceName: data.get("sourceName") || null,
        difficulty: data.get("difficulty"),
        estimatedMinutes: data.get("estimatedMinutes") === "" ? null : Number(data.get("estimatedMinutes")),
        qualityScore: previous?.qualityScore ?? 0.5,
        editorPick: Boolean(data.get("editorPick"))
    };
    form.querySelectorAll("input,select,textarea,button").forEach(control => { control.disabled = true; });
    try {
        await api(itemId ? "/admin/items/" + itemId : "/admin/items", {
            method: itemId ? "PUT" : "POST",
            headers:itemId?{'X-Expected-Revision':form.dataset.revision}:{},
            body: JSON.stringify(request)
        });
        resetForm("itemForm");
        await refreshCatalogData();
        await loadAdminData();
        showToast(itemId ? "Catalog item updated." : "Catalog item created.");document.getElementById('itemEditStatus').textContent='';
    } catch (error) {
        document.getElementById('itemEditStatus').textContent=error.message;
        handleError(error);
    } finally { form.querySelectorAll("input,select,textarea,button").forEach(control => { control.disabled = false; }); }
});

function activateResetLink(fragment) {
    if (!fragment.startsWith("#reset-password?")) return false;
    const fragmentQuery = fragment.slice(fragment.indexOf("?") + 1);
    passwordResetToken = new URLSearchParams(fragmentQuery).get("token");
    window.history.replaceState(null, "", window.location.pathname + window.location.search + "#home");
    openAuth("reset");
    if (!passwordResetToken) showAuthMessage("This reset link is invalid or expired. Request a new password reset link.");
    return true;
}

document.getElementById("retrySessionCheck").addEventListener("click", restoreSession);
window.addEventListener("hashchange", function () {
    activateResetLink(window.location.hash);
});
if (!activateResetLink(window.location.hash)) {
    restoreSession();
}
initializeThemeToggle();
initializeRisingLines();
initializeLandingDemo();
initializeLearningConstellation();

const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
if ("IntersectionObserver" in window && !reducedMotion.matches) {
    const revealObserver = new IntersectionObserver(function (entries, observer) {
        entries.forEach(function (entry) {
            if (entry.isIntersecting) {
                entry.target.classList.add("in-view");
                observer.unobserve(entry.target);
            }
        });
    }, { threshold: 0.12, rootMargin: "0px 0px -35px 0px" });
    document.querySelectorAll(".editorial-section, .final-cta").forEach(function (section) {
        section.classList.add("reveal");
        revealObserver.observe(section);
    });
}

initializePersonalization();
initializeTaste();

document.getElementById("activityFilter").addEventListener("change", () => loadActivity());

async function loadProgressContext(version) {
    const focus = document.getElementById('progressFocus'), next = document.getElementById('progressNext');
    try {
        const [status, profile, recommendations] = await Promise.all([api('/users/me/onboarding'), api('/users/me/recommendation-profile'), api('/users/me/recommendations?limit=10')]);
        if (version !== progressVersion) return;
        focus.replaceChildren(createElement('p', 'eyebrow', 'YOUR CURRENT DIRECTION'));
        focus.appendChild(createElement('h3', '', status.completed ? String(status.purpose).replaceAll('_', ' ').toLowerCase() + ' · ' + categoryName(status.categoryId) : 'Choose your starting point'));
        focus.appendChild(createElement('p', '', (profile.interests || []).map(topic => topic.name).join(' · ') || 'No saved focus topics yet. My Taste is a place to begin.'));
        const edit = createElement('button', 'button button-secondary', 'Refine My Taste'); edit.type = 'button'; edit.onclick = () => showPanel('interestProfilePanel'); focus.appendChild(edit);
        const recommendation = recommendations?.[0];if(recommendation)await ensureResourceMetadata([recommendation.itemId]);if(version!==progressVersion)return;const item = recommendation && items.find(row => Number(row.itemId) === Number(recommendation.itemId));
        next.replaceChildren(createElement('p', 'eyebrow', 'A USEFUL NEXT STEP'));
        next.appendChild(createElement('h3', '', item ? item.title : 'Your next find is waiting.'));
        next.appendChild(createElement('p', '', item ? recommendation.reason || item.description : 'Visit For You to generate picks from your saved signals.'));
        const action = createElement('button', 'button button-secondary', item ? 'View suggested resource' : 'Visit For You'); action.type = 'button'; action.onclick = () => item ? openResourceDetails(item, recommendation.reason) : showPanel('recommendationsPanel'); next.appendChild(action);
    } catch (error) {
        if (version !== progressVersion) return;
        focus.replaceChildren(createElement('p', '', 'Saved direction could not be loaded. Revisit this page to retry.'));
        next.replaceChildren(createElement('p', '', 'Recommendation unavailable right now. Your recorded progress is shown separately.'));
        handleError(error);
    }
}

function resetLearningWorkspace() {
    workspaceEpoch++;journeyRequestVersion++;activityCursor=null;libraryPage=1;libraryTotalPages=0;journeyPage=1;selectedJourneyId=null;journeyCreateRequestId=null;clearTimeout(librarySearchTimer);
    document.getElementById('journeyForm')?.reset();delete document.getElementById('journeyForm').dataset.dirty;document.querySelector('.journey-create').open=true;document.getElementById('activityMore').hidden=true;items=[];catalogMetadataReady=false;
    document.getElementById('collectionRenameDialog')?.close();document.getElementById('journeyDetail')?.replaceChildren();document.getElementById('journeyList')?.replaceChildren();
    accountProfileVersion++; adminLoadVersion++; adminTableVersion++; clearTimeout(adminSearchTimer); adminSettingsReadVersion++; csvPreviewVersion++; profileDraftDirty = false; adminSettingsDirty = false; adminCacheItems = []; adminEditingItem = null; adminCacheCategories = [];
    for (const id of ["adminItems","adminCategories","adminSnapshot","adminDistribution","accountFacts","profileLearning","settingsAccount"]) document.getElementById(id)?.replaceChildren();
    document.getElementById("accountDisplayName").textContent = "Loading account…";
    document.getElementById("profileEmail").textContent = ''; document.getElementById("profileUsername").textContent = ''; document.getElementById("displayNameInput").value = '';
    document.querySelectorAll('[data-settings-audit], [data-settings-preview-results]').forEach(node=>node.replaceChildren());
    document.getElementById('catalogCsvText').value = ''; document.getElementById('catalogCsvPreview').replaceChildren();
    resetForm('itemForm'); resetForm('categoryForm');
    libraryVersion++; activityVersion++; progressVersion++;
    libraryRows = []; activityRows = []; userCollections = []; libraryScopeCount = 0;
    for (const id of ['savedItems', 'activityList', 'collectionNavigation', 'progressFocus', 'progressNext']) document.getElementById(id)?.replaceChildren();
    document.getElementById('librarySearch').value = '';
    document.getElementById('collectionSelect').replaceChildren(new Option('All saved resources', ''));
    document.getElementById('activityFilter').value='';
    document.querySelectorAll('[data-in-progress], [data-completed], [data-week-chart]').forEach(node => node.replaceChildren());
}

let accountProfileVersion = 0;
let profileDraftDirty = false;
let accountExportBusy = false;
let adminLoadVersion = 0;
let adminCacheItems = [];
let adminEditingItem = null;
let adminCacheCategories = [];
let adminSnapshotData = null;
let adminPage = 1;
let adminTableVersion = 0;
let adminSearchTimer;
let adminComposition = [];
let adminItemDirty = false;
let adminCategoryDirty = false;
let adminSettingsDirty = false;
let adminSettingsHydrating = false;
let adminSettingsBusy = false;
let adminSettingsReadVersion = 0;
const adminItemMutations = new Set();
const adminCategoryMutations = new Set();

function installAccountCommand() {
    document.getElementById('displayNameInput').addEventListener('input', () => { profileDraftDirty = true; });
    document.querySelectorAll('[data-settings-section]').forEach(button => button.onclick = () => {
        document.querySelectorAll('[data-settings-section]').forEach(entry => entry.setAttribute('aria-pressed', String(entry === button)));
        document.querySelectorAll('[data-settings-content]').forEach(section => { section.hidden = section.dataset.settingsContent !== button.dataset.settingsSection; });
        document.getElementById('settingsStatus').textContent = '';
    });
    document.getElementById('settingsExport').onclick = () => downloadAccountData(document.getElementById('settingsExport'));
    document.getElementById('settingsDelete').onclick = () => document.getElementById('deleteAccountButton').click();
    document.getElementById('settingsLogout').onclick = () => { if (logoutInProgress) return; document.getElementById('settingsLogout').disabled = true; document.getElementById('settingsStatus').textContent = 'Signing out…'; document.getElementById('logoutButton').click(); };
    document.getElementById('settingsRecovery').onclick = () => {
        const email = currentUser?.email || ''; openAuth('forgot'); document.querySelector('#forgotPasswordForm [name=email]').value = email;
    };
    const panel = document.getElementById('adminPanel');
    const tabs = createElement('nav', 'command-tabs'); tabs.setAttribute('aria-label', 'Admin sections');
    for (const [key, label] of [['overview','Overview'],['resources','Resources'],['analytics','Analytics'],['import','Import'],['configuration','Configuration']]) {
        const button = createElement('button', '', label); button.type = 'button'; button.dataset.adminSection = key;
        button.onclick = () => activateAdminSection(key); tabs.appendChild(button);
        const section = createElement('section', 'command-section'); section.dataset.adminContent = key; panel.appendChild(section);
    }
    panel.querySelector('.page-title').after(tabs);
    tabs.insertAdjacentHTML('afterend', '<p id="adminStatus" role="status" aria-live="polite"></p>');
    const target = key => panel.querySelector('[data-admin-content="' + key + '"]');
    target('overview').innerHTML = '<section class="command-intro"><div><p class="eyebrow">CURRENT OPERATIONAL SNAPSHOT</p><h3>A useful catalog starts with care.</h3><p>Review the current platform totals, maintain resources and validate imports before publishing them to the shared catalog.</p></div><button id="adminRefresh" type="button" class="button button-secondary">Refresh snapshot</button></section><div id="adminSnapshot" class="command-snapshot" aria-live="polite">Loading current totals…</div><p class="account-footnote">Aggregate snapshot, not a trend or a measure of recommendation quality. Administrative actions affect the shared catalog.</p>';
    document.getElementById('adminRefresh').onclick = loadAdminData;
    target('resources').innerHTML = '<div class="command-heading"><div><h3>The resource catalog</h3><p>Search the catalog, including paused resources. Results load 20 records per page.</p></div><button id="adminCreateResource" class="button button-primary" type="button">Create resource</button></div><div class="admin-filters"><label>Search<input id="adminCatalogSearch" aria-label="Search admin catalog" type="search" placeholder="Title, source or topic"></label><label>Category<select id="adminCatalogCategory"><option value="">All categories</option></select></label><label>Type<select id="adminCatalogType"><option value="">Any type</option><option value="ARTICLE">ARTICLE</option><option value="VIDEO">VIDEO</option><option value="COURSE">COURSE</option><option value="DOCUMENTATION">DOCUMENTATION</option><option value="GUIDE">GUIDE</option><option value="RESOURCE">RESOURCE</option><option value="TOOL">TOOL</option><option value="PROJECT">PROJECT</option><option value="CONTENT">CONTENT</option><option value="PRODUCT">PRODUCT</option><option value="OTHER">OTHER</option></select></label><label>Status<select id="adminCatalogStatus"><option value="">All statuses</option><option value="active">Active</option><option value="inactive">Paused</option></select></label><label>Order<select id="adminCatalogSort"><option value="title">Title A–Z</option><option value="updated">Recently updated</option></select></label></div>';
    const lists = panel.querySelector('.admin-list-grid');
    const catalog = document.getElementById('adminItems'); catalog.className = 'command-table-wrap';
    target('resources').append(panel.querySelector('.bulk-catalog-controls'), catalog);
    const pager = createElement('div', 'command-pagination');
    pager.innerHTML = '<button id="adminPrevious" class="button button-secondary" type="button">Previous page</button><p id="adminPageSummary" role="status"></p><button id="adminNext" class="button button-secondary" type="button">Next page</button>';
    target('resources').appendChild(pager);
    const editor = document.createElement('details'); editor.id = 'adminEditor';
    editor.innerHTML = '<summary>Create or edit catalog records</summary>'; editor.appendChild(panel.querySelector('.admin-grid'));
    const categoryList = createElement('section', 'command-categories'); categoryList.append(createElement('h3', '', 'Categories'), document.getElementById('adminCategories')); editor.appendChild(categoryList);
    target('resources').appendChild(editor); lists.remove();
    document.getElementById('adminCreateResource').onclick = () => {
        if (adminItemDirty && !confirm('Discard the current unsaved resource draft?')) return;
        resetForm('itemForm'); editor.open = true; document.querySelector('#itemForm [name=title]').focus(); document.getElementById('itemForm').scrollIntoView({block:'center'});
    };
    target('import').appendChild(panel.querySelector('.catalog-import-card'));
    installCatalogIngestion(target('import'),api,loadAdminData);
    target('configuration').appendChild(document.getElementById('adminRecommendationSettings'));
    target('analytics').appendChild(document.getElementById('adminRecommendationEngagement'));
    const distribution = createElement('section', 'account-surface'); distribution.innerHTML = '<p class="eyebrow">CATALOG COMPOSITION</p><h3>Resources by category</h3><p>Counts include active and paused resources. No historical trend is inferred.</p><div id="adminDistribution">Loading catalog composition…</div>'; target('analytics').appendChild(distribution);
    for (const id of ['adminCatalogSearch','adminCatalogCategory','adminCatalogStatus','adminCatalogSort','adminCatalogType']) document.getElementById(id).addEventListener(id === 'adminCatalogSearch' ? 'input' : 'change', () => { adminPage = 1; clearTimeout(adminSearchTimer); adminTableVersion++; document.getElementById('adminItems').replaceChildren(); document.getElementById('adminItems').setAttribute('aria-busy','true'); document.getElementById('adminPageSummary').textContent='Loading catalog page…'; adminSearchTimer = setTimeout(renderAdminTable, id === 'adminCatalogSearch' ? 180 : 0); });
    document.getElementById('adminPrevious').onclick = () => { adminPage--; renderAdminTable(); };
    document.getElementById('adminNext').onclick = () => { adminPage++; renderAdminTable(); };
    document.getElementById('itemForm').addEventListener('input', () => { adminItemDirty = true; });
    document.getElementById('categoryForm').addEventListener('input', () => { adminCategoryDirty = true; });
    document.querySelectorAll('#adminRecommendationSettings input').forEach(input => input.addEventListener('input', () => { if (!adminSettingsHydrating) adminSettingsDirty = true; }));
    document.getElementById('adminRecommendationSettings').insertAdjacentHTML('afterbegin','<p id="adminConfigStatus" role="status" aria-live="polite">Loading saved configuration…</p>');
    document.getElementById('adminRecommendationSettings').dataset.settingsReady = 'false';
    document.querySelectorAll('#adminRecommendationSettings input, #adminRecommendationSettings button').forEach(control=>{control.disabled=true;});
    activateAdminSection('overview');
}

function activateAdminSection(key) {
    document.querySelectorAll('[data-admin-section]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.adminSection === key)));
    document.querySelectorAll('[data-admin-content]').forEach(section => { section.hidden = section.dataset.adminContent !== key; });
}

function renderAccountIdentity(profile) {
    const name = profile.displayName || profile.username;
    document.getElementById('accountDisplayName').textContent = name;
    document.getElementById('accountInitials').textContent = name.trim().split(/\s+/).slice(0,2).map(word => word.charAt(0)).join('').toUpperCase();
    document.getElementById('profileUsername').textContent = '@' + profile.username;
    document.getElementById('profileEmail').textContent = profile.email || 'Email unavailable';
    const facts = document.getElementById('accountFacts'); facts.replaceChildren();
    const rows = [['Account role', profile.role === 'ADMIN' ? 'Administrator' : 'Member'], ['Joined', profile.createdAt ? new Intl.DateTimeFormat(undefined,{year:'numeric',month:'long',day:'numeric'}).format(recordedDate(profile.createdAt)) : 'Date unavailable']];
    for (const [label,value] of rows) facts.append(createElement('dt','',label),createElement('dd','',value));
    document.getElementById('settingsAccount').replaceChildren(createElement('h4','',name),createElement('p','',profile.email),createElement('p','','@'+profile.username+' · read-only account identifier'));
}

async function loadAccountProfile() {
    if (!currentUser) return;
    const version = ++accountProfileVersion, id = currentUser.userId;
    try {
        const [profile, direction, signals, history] = await Promise.all([api('/users/me'),api('/users/me/onboarding'),api('/users/me/recommendation-profile'),api('/users/me/interactions?limit=100')]);
        if (version !== accountProfileVersion || currentUser?.userId !== id) return;
        renderAccountIdentity(profile);
        if (!profileDraftDirty) document.getElementById('displayNameInput').value = profile.displayName || '';
        const learning = document.getElementById('profileLearning'); learning.replaceChildren();
        learning.appendChild(createElement('h3','',direction.completed ? String(direction.purpose).toLowerCase().replaceAll('_',' ') : 'Choose your starting point'));
        learning.appendChild(createElement('p','',(signals.interests || []).map(topic=>topic.name).join(' · ') || 'No saved focus topics yet.'));
        const saved = new Set((history || []).filter(event=>event.type==='SAVE').map(event=>event.itemId)).size;
        learning.appendChild(createElement('p','account-footnote',Number(signals.interactionCount)+' recorded interactions · '+saved+' resources saved within your latest 100 events. Saved does not mean completed.'));
    } catch (error) {
        if (version !== accountProfileVersion) return;
        document.getElementById('profileStatus').textContent = 'Could not refresh account information. ' + error.message;
        document.getElementById('settingsStatus').textContent = 'Could not refresh account information. ' + error.message; handleError(error);
    }
}

async function downloadAccountData(button) {
    if (accountExportBusy) return; accountExportBusy = true; button.disabled = true;
    const accountId = currentUser?.userId;
    const status = document.getElementById('settingsStatus'); status.textContent = 'Preparing your account data…';
    try {
        const data = await api('/users/me/export');
        if (!currentUser || currentUser.userId !== accountId) return;
        const link = document.createElement('a');
        const url = URL.createObjectURL(new Blob([JSON.stringify(data,null,2)],{type:'application/json'})); link.href = url; link.download = 'recomai-my-data.json'; document.body.appendChild(link); link.click(); link.remove(); setTimeout(()=>URL.revokeObjectURL(url),1000);
        status.textContent = 'Your JSON export is ready.'; showToast('Your data export is ready.');
    } catch (error) { status.textContent = 'Could not export your data. ' + error.message; handleError(error); }
    finally { accountExportBusy = false; button.disabled = false; }
}

async function renderAdminTable() {
    if (currentUser?.role !== 'ADMIN') return;
    const version=++adminTableVersion, accountId=currentUser.userId;
    const output=document.getElementById('adminItems'), summary=document.getElementById('adminPageSummary');
    const params=new URLSearchParams({query:document.getElementById('adminCatalogSearch').value.trim(),status:document.getElementById('adminCatalogStatus').value,sort:document.getElementById('adminCatalogSort').value,page:String(adminPage),pageSize:'20'});
    const category=document.getElementById('adminCatalogCategory').value;if(category)params.set('categoryId',category);
    const type=document.getElementById('adminCatalogType').value;if(type)params.set('type',type);
    output.replaceChildren();output.setAttribute('aria-busy','true');summary.textContent='Loading catalog page…';
    document.getElementById('adminPrevious').disabled=document.getElementById('adminNext').disabled=true;
    let result;
    try {
        result=await api('/admin/items/search?'+params);
        if(version!==adminTableVersion || currentUser?.userId!==accountId || currentUser?.role!=='ADMIN')return;
    } catch(error) {
        if(version===adminTableVersion){summary.textContent='Could not load this page. Refresh snapshot to retry. '+error.message;output.removeAttribute('aria-busy');handleError(error);}return;
    }
    adminCacheItems=result.items;const rows=result.items;adminPage=result.page;
    const pages=Math.max(1,result.totalPages);
    const table = document.createElement('table'); table.innerHTML = '<caption>Catalog records · bulk actions apply to checked rows on this page</caption><thead><tr><th scope="col">Select</th><th scope="col">Resource</th><th scope="col">Category</th><th scope="col">Provider / type</th><th scope="col">Status</th><th scope="col">Actions</th></tr></thead><tbody></tbody>';
    for (const item of rows) {
        const row = document.createElement('tr'), selectCell=document.createElement('td'), checkbox=document.createElement('input'); checkbox.type='checkbox';checkbox.className='admin-item-select';checkbox.value=item.itemId;checkbox.setAttribute('aria-label','Select '+item.title);selectCell.appendChild(checkbox);row.appendChild(selectCell);
        const title=document.createElement('td');title.append(createElement('strong','',item.title),createElement('small','','Updated '+(item.updatedAt?formatDate(item.updatedAt):'date unavailable')));row.appendChild(title);
        row.appendChild(createElement('td','',adminCacheCategories.find(category=>category.categoryId===item.categoryId)?.name||'Unavailable category'));
        const provider=createElement('td','',item.sourceName||'Not provided');provider.appendChild(createElement('small','',item.type));row.appendChild(provider);row.appendChild(createElement('td','',item.active?'ACTIVE':'PAUSED'));
        const actions=document.createElement('td');
        for (const [label,callback] of [['Details',()=>openResourceDetails(item, undefined, true)],['Edit',()=>editItem(item)],[item.active?'Pause':'Enable',async()=>{if(item.active&&!confirm('Pause this resource? It will be hidden from the public catalog.'))return;await toggleItem(item);}]]) { const button=createElement('button','small-action',label);button.type='button';button.onclick=async()=>{if(button.disabled)return;button.disabled=true;try{await callback();}finally{button.disabled=false;}};actions.appendChild(button); }
        row.appendChild(actions);table.querySelector('tbody').appendChild(row);
    }
    if(!rows.length){const row=document.createElement('tr'),cell=createElement('td','','No resources match these filters.');cell.colSpan=6;row.appendChild(cell);table.querySelector('tbody').appendChild(row);}
    document.getElementById('adminItems').replaceChildren(table);
    output.removeAttribute('aria-busy');
    summary.textContent = result.totalItems ? ((adminPage-1)*20+1)+'–'+Math.min(adminPage*20,result.totalItems)+' of '+result.totalItems+' matches · '+rows.length+' records loaded' : '0 matches';
    document.getElementById('adminPrevious').disabled = adminPage<=1;document.getElementById('adminNext').disabled = adminPage>=pages;
}

function renderAdminSnapshot() {
    const output=document.getElementById('adminSnapshot');output.replaceChildren();
    for(const [label,value] of [['Catalog resources',adminSnapshotData.totalItems],['Active resources',adminComposition.reduce((sum,row)=>sum+Number(row.active),0)],['Registered accounts',adminSnapshotData.totalUsers],['Recorded interactions',adminSnapshotData.totalInteractions]]){const card=createElement('article','');card.append(createElement('span','',label),createElement('strong','',Number(value).toLocaleString()));output.appendChild(card);}
    const distribution=document.getElementById('adminDistribution');distribution.replaceChildren();
    distribution.appendChild(createElement('p','account-footnote',Number(adminSnapshotData.recommendationsGenerated)+' stored recommendation records · '+Number(adminSnapshotData.totalCategories)+' categories. Snapshot totals, not time-series growth.'));
    for(const category of adminCacheCategories){const count=Number(adminComposition.find(row=>Number(row.categoryId)===category.categoryId)?.total||0);const row=createElement('div','distribution-row');row.append(createElement('span','',category.name),createElement('strong','',count+' resources'));const meter=document.createElement('meter');meter.min=0;meter.max=Math.max(1,Number(adminSnapshotData.totalItems));meter.value=count;meter.setAttribute('aria-label',category.name+': '+count+' resources');row.appendChild(meter);distribution.appendChild(row);}
}

document.getElementById("catalogCsvText").addEventListener("input", () => { csvPreviewVersion++; document.getElementById("commitCatalogCsv").disabled = true; document.getElementById("catalogCsvPreview").replaceChildren(); showCatalogImportMessage("CSV changed. Preview again to validate the current draft."); });
