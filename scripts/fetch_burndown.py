#!/usr/bin/env python3
"""
ColdGuard Burndown Chart Generator
────────────────────────────────────
Lee Issues, Milestones y Story Points de GitHub Projects v2
y genera un archivo HTML standalone con el burndown chart.

Uso:
    python fetch_burndown.py ghp_TuTokenAqui
    python fetch_burndown.py          # pedirá el token si no está en env

Requiere:
    pip install requests
"""

import os
import sys
import json
import requests
from datetime import datetime, timedelta, date

# ── Configuración ──────────────────────────────────────────────────────────────
REPO_OWNER  = "marcomiler"
REPO_NAME   = "coldguard-platform"
OUTPUT_FILE = "coldguard_burndown.html"
BASE_URL    = "https://api.github.com"

def get_token():
    if len(sys.argv) > 1 and not sys.argv[1].startswith("-"):
        return sys.argv[1]
    token = os.environ.get("GITHUB_TOKEN", "")
    if token:
        return token
    try:
        return input("GitHub Personal Access Token: ").strip()
    except (EOFError, KeyboardInterrupt):
        print("\nAbortado.")
        sys.exit(1)

def rest_headers(token):
    return {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github.v3+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }

def gql_headers(token):
    return {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

# ── REST helper ────────────────────────────────────────────────────────────────
def paginate(url, token, params=None):
    params = dict(params or {})
    params.setdefault("per_page", 100)
    out, page = [], 1
    while True:
        params["page"] = page
        r = requests.get(url, headers=rest_headers(token), params=params)
        r.raise_for_status()
        data = r.json()
        if not data:
            break
        out.extend(data)
        if len(data) < int(params["per_page"]):
            break
        page += 1
    return out

# ── GraphQL helper ─────────────────────────────────────────────────────────────
GQL_STORY_POINTS = """
query($owner: String!, $repo: String!, $cursor: String) {
  repository(owner: $owner, name: $repo) {
    projectsV2(first: 5) {
      nodes {
        title
        items(first: 100, after: $cursor) {
          pageInfo { hasNextPage endCursor }
          nodes {
            fieldValues(first: 20) {
              nodes {
                ... on ProjectV2ItemFieldNumberValue {
                  number
                  field { ... on ProjectV2FieldCommon { name } }
                }
                ... on ProjectV2ItemFieldDateValue {
                  date
                  field { ... on ProjectV2FieldCommon { name } }
                }
              }
            }
            content { ... on Issue { number } }
          }
        }
      }
    }
  }
}
"""

def fetch_story_points(token):
    print("  📊 Consultando Story Points y Start Dates de GitHub Projects v2…")
    sp_map, start_map, cursor = {}, {}, None
    SP_FIELDS   = {"storypoints","story_points","points","sp","estimacion","estimación","puntos","estimate"}
    DATE_FIELDS = {"startdate","start_date","start","fechainicio","fecha_inicio"}

    while True:
        r = requests.post(
            "https://api.github.com/graphql",
            json={"query": GQL_STORY_POINTS, "variables": {"owner": REPO_OWNER, "repo": REPO_NAME, "cursor": cursor}},
            headers=gql_headers(token),
        )
        r.raise_for_status()
        result = r.json()

        if "errors" in result:
            for e in result["errors"]:
                print(f"    ⚠ GraphQL: {e.get('message', e)}")
            break

        projects = (result.get("data") or {}).get("repository", {}).get("projectsV2", {}).get("nodes", [])
        has_next = False

        for project in projects:
            items_data = project.get("items", {})
            pi = items_data.get("pageInfo", {})
            if pi.get("hasNextPage"):
                has_next = True
                cursor = pi["endCursor"]

            for item in items_data.get("nodes", []):
                content = item.get("content") or {}
                num = content.get("number")
                if not num:
                    continue
                for fv in item.get("fieldValues", {}).get("nodes", []):
                    if not fv:
                        continue
                    fname = (fv.get("field") or {}).get("name", "").lower().replace(" ", "").replace("_", "")
                    if fname in SP_FIELDS and "number" in fv:
                        sp_map[num] = int(fv.get("number") or 0)
                    elif fname in DATE_FIELDS and "date" in fv:
                        start_map[num] = fv.get("date")

        if not has_next:
            break

    print(f"    → {len(sp_map)} issues con SP · {len(start_map)} issues con Start date")
    return sp_map, start_map

# ── Datos ──────────────────────────────────────────────────────────────────────
def fetch_milestones(token):
    print("📅 Obteniendo milestones (sprints)…")
    ms = paginate(f"{BASE_URL}/repos/{REPO_OWNER}/{REPO_NAME}/milestones",
                  token, {"state": "all", "sort": "due_on", "direction": "asc"})
    print(f"  → {len(ms)} milestones encontrados")
    return ms

def fetch_issues(milestone_number, token):
    issues = paginate(
        f"{BASE_URL}/repos/{REPO_OWNER}/{REPO_NAME}/issues",
        token, {"milestone": milestone_number, "state": "all"},
    )
    return [i for i in issues if "pull_request" not in i]

# ── Burndown math ──────────────────────────────────────────────────────────────
def pdate(s):
    return datetime.strptime(s[:10], "%Y-%m-%d").date() if s else None

def build_sprint(milestone, issues, sp_map, start_map):
    issue_starts = [pdate(start_map.get(i["number"])) for i in issues if start_map.get(i["number"])]
    start = min(issue_starts) if issue_starts else pdate(milestone.get("created_at"))
    end   = pdate(milestone.get("due_on")) or start + timedelta(days=14)
    today = date.today()

    total = sum(sp_map.get(i["number"], 0) for i in issues)

    actual = []
    d = start
    while d <= min(end, today):
        rem = total
        for issue in issues:
            if issue["state"] == "closed":
                cd = pdate(issue.get("closed_at"))
                if cd and cd <= d:
                    rem -= sp_map.get(issue["number"], 0)
        actual.append({"date": d.isoformat(), "remaining": rem})
        d += timedelta(days=1)

    span = max((end - start).days, 1)
    ideal = []
    d, k = start, 0
    while d <= end:
        ideal.append({"date": d.isoformat(), "remaining": round(total * (1 - k / span), 2)})
        d += timedelta(days=1)
        k += 1

    completed = total - (actual[-1]["remaining"] if actual else total)
    velocity  = round(completed / max(len(actual), 1), 1)

    return {
        "sprint_name":      milestone["title"],
        "start_date":       start.isoformat(),
        "end_date":         end.isoformat(),
        "total_points":     total,
        "completed_points": completed,
        "remaining_points": total - completed,
        "velocity":         velocity,
        "actual":           actual,
        "ideal":            ideal,
        "issues": sorted(
            [{"number": i["number"], "title": i["title"], "state": i["state"],
              "story_points": sp_map.get(i["number"], 0),
              "closed_at": i.get("closed_at", "")[:10] if i.get("closed_at") else None}
             for i in issues],
            key=lambda x: (x["state"] != "closed", x["number"]),
        ),
    }

# ── HTML template ──────────────────────────────────────────────────────────────
HTML_TEMPLATE = r"""<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>ColdGuard · Burndown</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Syne:wght@700;800&family=JetBrains+Mono:wght@400;500&family=Barlow+Condensed:wght@500;600;700&family=Barlow:wght@400;500&display=swap">
<style>
:root{--bg:#07101A;--sf1:#0D1B2B;--sf2:#132840;--sf3:#1A3550;--bd:#1E3D54;--bd2:#285270;--cyan:#07C3E8;--steel:#2B5070;--mint:#0FB87A;--amber:#F39C0A;--danger:#EF4444;--t1:#CAE8F8;--t2:#4D8FAD;--t3:#2D607A;--fd:'Syne',sans-serif;--fm:'JetBrains Mono',monospace;--fl:'Barlow Condensed',sans-serif;--fb:'Barlow',sans-serif}
*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
body{background:var(--bg);color:var(--t1);font-family:var(--fb);min-height:100vh;padding:2rem clamp(1rem,4vw,2.5rem)}
.wrap{max-width:1100px;margin:0 auto}
.hdr{display:flex;flex-wrap:wrap;justify-content:space-between;align-items:flex-end;gap:1rem;margin-bottom:1.75rem}
.hdr-l{display:flex;flex-direction:column;gap:3px}
.eyebrow{font-family:var(--fl);font-size:.65rem;font-weight:700;letter-spacing:.2em;text-transform:uppercase;color:var(--cyan)}
.page-h{font-family:var(--fd);font-size:clamp(1.5rem,3vw,2rem);font-weight:800;color:var(--t1);line-height:1.15;text-wrap:balance}
.dates-sub{font-family:var(--fm);font-size:.68rem;color:var(--t3);margin-top:2px}
.tabs{display:flex;gap:3px;background:var(--sf1);border:1px solid var(--bd);border-radius:8px;padding:3px;align-self:flex-start}
.tab{font-family:var(--fl);font-size:.75rem;font-weight:700;letter-spacing:.04em;padding:.4rem .875rem;border:1px solid transparent;border-radius:5px;background:transparent;color:var(--t3);cursor:pointer;transition:color .15s,background .15s,border-color .15s;white-space:nowrap}
.tab:hover{color:var(--t2);background:var(--sf2)}
.tab.on{background:var(--sf3);color:var(--cyan);border-color:var(--bd2)}
.chip{display:inline-flex;align-items:center;gap:5px;padding:3px 10px;border-radius:99px;font-family:var(--fl);font-size:.65rem;font-weight:700;letter-spacing:.08em;text-transform:uppercase;border:1px solid}
.chip.done{background:rgba(15,184,122,.1);color:var(--mint);border-color:rgba(15,184,122,.25)}
.chip.active{background:rgba(7,195,232,.1);color:var(--cyan);border-color:rgba(7,195,232,.25)}
.chip.behind{background:rgba(243,156,10,.1);color:var(--amber);border-color:rgba(243,156,10,.25)}
.kpis{display:grid;grid-template-columns:repeat(auto-fit,minmax(155px,1fr));gap:10px;margin-bottom:1.25rem}
.kpi{background:var(--sf1);border:1px solid var(--bd);border-radius:10px;padding:1rem 1.125rem;display:flex;flex-direction:column;gap:5px;transition:border-color .2s}
.kpi:hover{border-color:var(--bd2)}
.kpi-lbl{font-family:var(--fl);font-size:.62rem;font-weight:700;letter-spacing:.14em;text-transform:uppercase;color:var(--t3)}
.kpi-val{font-family:var(--fm);font-size:1.65rem;font-weight:500;color:var(--t1);font-variant-numeric:tabular-nums;line-height:1}
.kpi-sub{font-family:var(--fm);font-size:.63rem;color:var(--t3)}
.kpi.hi .kpi-val{color:var(--cyan)}.kpi.good .kpi-val{color:var(--mint)}.kpi.warn .kpi-val{color:var(--amber)}
.chart-card{background:var(--sf1);border:1px solid var(--bd);border-radius:12px;padding:1.375rem 1.5rem 1.125rem;margin-bottom:1.125rem}
.chart-hdr{display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:.75rem;margin-bottom:1rem}
.chart-ttl{font-family:var(--fl);font-size:.68rem;font-weight:700;letter-spacing:.14em;text-transform:uppercase;color:var(--t2)}
.legend{display:flex;gap:1.25rem;align-items:center}
.leg-item{display:flex;align-items:center;gap:6px;font-family:var(--fl);font-size:.68rem;font-weight:600;letter-spacing:.04em;color:var(--t2)}
.leg-line{width:22px;height:2px;border-radius:1px}.leg-line.a{background:var(--cyan)}.leg-line.b{background:transparent;border-top:2px dashed var(--steel);height:0}
.prog-wrap{margin-bottom:1rem}
.prog-hdr{display:flex;justify-content:space-between;align-items:baseline;margin-bottom:6px}
.prog-lbl{font-family:var(--fl);font-size:.62rem;font-weight:700;letter-spacing:.12em;text-transform:uppercase;color:var(--t3)}
.prog-pct{font-family:var(--fm);font-size:.72rem;font-weight:500;color:var(--cyan)}
.prog-track{height:3px;background:var(--sf3);border-radius:2px;overflow:hidden}
.prog-fill{height:100%;background:linear-gradient(90deg,var(--steel),var(--cyan));border-radius:2px;transition:width .8s cubic-bezier(.4,0,.2,1)}
.cvs-wrap{position:relative;height:300px}
.issues-card{background:var(--sf1);border:1px solid var(--bd);border-radius:12px;overflow:hidden}
.issues-hdr{display:flex;align-items:center;justify-content:space-between;padding:.875rem 1.25rem;border-bottom:1px solid var(--bd);cursor:pointer;user-select:none;gap:.5rem;transition:background .15s}
.issues-hdr:hover{background:var(--sf2)}
.issues-ttl{font-family:var(--fl);font-size:.68rem;font-weight:700;letter-spacing:.12em;text-transform:uppercase;color:var(--t2)}
.tog{font-family:var(--fm);font-size:.6rem;color:var(--t3);letter-spacing:.06em}
.table-wrap{overflow-x:auto}
.itbl{width:100%;border-collapse:collapse;font-size:.8rem}
.itbl thead th{font-family:var(--fl);font-size:.6rem;font-weight:700;letter-spacing:.12em;text-transform:uppercase;color:var(--t3);background:var(--sf2);padding:.5rem .875rem;text-align:left;white-space:nowrap}
.itbl thead th:nth-child(3){text-align:center}.itbl thead th:nth-child(4){text-align:right}
.itbl tbody tr{border-bottom:1px solid var(--bd);transition:background .12s}
.itbl tbody tr:last-child{border-bottom:none}.itbl tbody tr:hover{background:var(--sf2)}
.itbl td{padding:.625rem .875rem;vertical-align:middle}
.nc{font-family:var(--fm);font-size:.68rem;color:var(--t3);white-space:nowrap;width:48px}
.tc{color:var(--t1);max-width:360px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.sc{white-space:nowrap;text-align:center}
.badge{display:inline-flex;align-items:center;gap:4px;padding:2px 8px;border-radius:99px;font-family:var(--fl);font-size:.6rem;font-weight:700;letter-spacing:.06em;text-transform:uppercase;border:1px solid;white-space:nowrap}
.badge.closed{background:rgba(15,184,122,.1);color:var(--mint);border-color:rgba(15,184,122,.22)}
.badge.open{background:rgba(243,156,10,.09);color:var(--amber);border-color:rgba(243,156,10,.2)}
.spc{font-family:var(--fm);font-size:.75rem;font-weight:500;color:var(--t2);font-variant-numeric:tabular-nums;text-align:right;white-space:nowrap;width:60px}
.dc{font-family:var(--fm);font-size:.65rem;color:var(--t3);font-variant-numeric:tabular-nums;width:80px}
.footer{margin-top:1.5rem;display:flex;justify-content:space-between;align-items:center;gap:1rem}
.ft{font-family:var(--fm);font-size:.6rem;color:var(--t3);letter-spacing:.04em}
@media(max-width:600px){.kpis{grid-template-columns:repeat(2,1fr)}.cvs-wrap{height:240px}.dc{display:none}}
</style>
</head>
<body>
<div class="wrap">
<header class="hdr">
  <div class="hdr-l">
    <span class="eyebrow">ColdGuard Platform</span>
    <h1 class="page-h" id="sname">Burndown Chart</h1>
    <span class="dates-sub" id="sdates">—</span>
  </div>
  <div style="display:flex;gap:.625rem;align-items:center;flex-wrap:wrap">
    <span class="chip" id="chip">—</span>
    <nav class="tabs" id="tabs" aria-label="Seleccionar sprint"></nav>
  </div>
</header>

<section class="kpis" id="kpis" aria-label="Métricas del sprint"></section>

<div class="chart-card">
  <div class="chart-hdr">
    <span class="chart-ttl">Story Points Restantes</span>
    <div class="legend" aria-hidden="true">
      <div class="leg-item"><div class="leg-line a"></div>Real</div>
      <div class="leg-item"><div class="leg-line b"></div>Ideal</div>
    </div>
  </div>
  <div class="prog-wrap">
    <div class="prog-hdr">
      <span class="prog-lbl">Avance del sprint</span>
      <span class="prog-pct" id="ppct">0%</span>
    </div>
    <div class="prog-track"><div class="prog-fill" id="pfill" style="width:0%"></div></div>
  </div>
  <div class="cvs-wrap">
    <canvas id="chart" role="img" aria-label="Burndown chart: story points restantes vs ideal por día"></canvas>
  </div>
</div>

<div class="issues-card">
  <div class="issues-hdr" onclick="toggleIssues()">
    <span class="issues-ttl" id="ilbl">Historias de Usuario</span>
    <span class="tog" id="tog">▼ colapsar</span>
  </div>
  <div id="ibody" class="table-wrap">
    <table class="itbl">
      <thead><tr>
        <th style="width:48px">#</th>
        <th>Historia de Usuario</th>
        <th style="text-align:center;width:100px">Estado</th>
        <th style="text-align:right;width:60px">SP</th>
        <th style="width:80px">Cierre</th>
      </tr></thead>
      <tbody id="irows"></tbody>
    </table>
  </div>
</div>

<footer class="footer">
  <span class="ft">marcomiler/coldguard-platform</span>
  <span class="ft" id="fgen"></span>
</footer>
</div>

<script src="https://cdnjs.cloudflare.com/ajax/libs/Chart.js/4.4.1/chart.umd.min.js"></script>
<script>
const SPRINTS = __SPRINTS_DATA__;
let active = 0, chart = null, open = true;

const fmtS = iso => new Date(iso+'T12:00:00').toLocaleDateString('es-MX',{month:'short',day:'numeric'});
const fmtL = iso => new Date(iso+'T12:00:00').toLocaleDateString('es-MX',{year:'numeric',month:'short',day:'numeric'});

function status(s){
  const now=new Date(), end=new Date(s.end_date);
  if(now>end) return s.remaining_points===0 ? {c:'done',t:'✓ Completado'} : {c:'behind',t:`△ ${s.remaining_points} SP pendientes`};
  const ip=Math.round(((now-new Date(s.start_date))/(end-new Date(s.start_date)))*100);
  const ap=Math.round((s.completed_points/s.total_points)*100);
  return ap>=ip-5 ? {c:'active',t:'● En progreso'} : {c:'behind',t:'▾ Atrasado'};
}

function render(idx){
  active=idx;
  const s=SPRINTS[idx];
  document.querySelectorAll('.tab').forEach((t,i)=>t.classList.toggle('on',i===idx));
  document.getElementById('sname').textContent=s.sprint_name;
  document.getElementById('sdates').textContent=`${fmtL(s.start_date)} – ${fmtL(s.end_date)}`;
  const st=status(s);
  const ch=document.getElementById('chip'); ch.className='chip '+st.c; ch.textContent=st.t;
  const pct=s.total_points>0?Math.round((s.completed_points/s.total_points)*100):0;
  document.getElementById('ppct').textContent=pct+'%';
  document.getElementById('pfill').style.width=pct+'%';
  const rc=s.remaining_points===0?'good':s.remaining_points<=s.total_points*.15?'hi':'warn';
  document.getElementById('kpis').innerHTML=`
    <div class="kpi hi"><span class="kpi-lbl">Total SP</span><span class="kpi-val">${s.total_points}</span><span class="kpi-sub">puntos planificados</span></div>
    <div class="kpi good"><span class="kpi-lbl">Completados</span><span class="kpi-val">${s.completed_points}</span><span class="kpi-sub">puntos cerrados</span></div>
    <div class="kpi ${rc}"><span class="kpi-lbl">Pendientes</span><span class="kpi-val">${s.remaining_points}</span><span class="kpi-sub">puntos restantes</span></div>
    <div class="kpi"><span class="kpi-lbl">Velocidad</span><span class="kpi-val">${s.velocity}</span><span class="kpi-sub">SP / día promedio</span></div>`;
  buildChart(s);
  buildIssues(s);
  document.getElementById('fgen').textContent='Generado '+new Date().toLocaleDateString('es-MX',{year:'numeric',month:'short',day:'numeric'});
}

function buildChart(s){
  const im=Object.fromEntries(s.ideal.map(d=>[d.date,d.remaining]));
  const am=Object.fromEntries(s.actual.map(d=>[d.date,d.remaining]));
  const all=[...new Set([...s.ideal.map(d=>d.date),...s.actual.map(d=>d.date)])].sort();
  if(chart) chart.destroy();
  chart=new Chart(document.getElementById('chart').getContext('2d'),{
    type:'line',
    data:{
      labels:all.map(fmtS),
      datasets:[
        {label:'Real',data:all.map(d=>am[d]??null),borderColor:'#07C3E8',borderWidth:2.5,
         pointBackgroundColor:'#07C3E8',pointBorderColor:'#0D1B2B',pointBorderWidth:2,
         pointRadius:3.5,pointHoverRadius:6,fill:true,
         backgroundColor:ctx=>{const g=ctx.chart.ctx.createLinearGradient(0,0,0,ctx.chart.height);g.addColorStop(0,'rgba(7,195,232,.16)');g.addColorStop(1,'rgba(7,195,232,0)');return g;},
         tension:.3,spanGaps:false,order:1},
        {label:'Ideal',data:all.map(d=>im[d]??null),borderColor:'#2B5070',borderWidth:1.5,
         borderDash:[6,4],pointRadius:0,pointHoverRadius:0,fill:false,tension:0,spanGaps:false,order:2}
      ]
    },
    options:{
      responsive:true,maintainAspectRatio:false,
      interaction:{mode:'index',intersect:false},
      animation:{duration:700,easing:'easeInOutCubic'},
      plugins:{
        legend:{display:false},
        tooltip:{
          backgroundColor:'#132840',borderColor:'#1E3D54',borderWidth:1,
          titleColor:'#4D8FAD',bodyColor:'#CAE8F8',
          titleFont:{family:"'Barlow Condensed'",size:11,weight:'700'},
          bodyFont:{family:"'JetBrains Mono'",size:12},
          padding:{x:14,y:12},cornerRadius:8,
          callbacks:{
            title:items=>items[0]?.label??'',
            label:item=>{if(item.raw===null)return null;return(item.datasetIndex===0?'  Real  ':'  Ideal ')+item.raw+' SP';},
            afterBody:items=>{
              const r=items.find(i=>i.datasetIndex===0)?.raw??null;
              const id=items.find(i=>i.datasetIndex===1)?.raw??null;
              if(r===null||id===null)return[];
              const d=Math.round(r-id);
              if(d===0)return['  ─ en línea ideal'];
              return['  '+(d>0?'▲ '+d+' SP sobre ideal':'▼ '+Math.abs(d)+' SP bajo ideal')];
            }
          }
        }
      },
      scales:{
        x:{grid:{color:'rgba(255,255,255,.04)',drawBorder:false},
           ticks:{color:'#2D607A',font:{family:"'JetBrains Mono'",size:10},maxRotation:45,autoSkip:true,maxTicksLimit:12},
           border:{display:false}},
        y:{min:0,suggestedMax:s.total_points*1.05,
           grid:{color:'rgba(255,255,255,.05)',drawBorder:false},
           ticks:{color:'#2D607A',font:{family:"'JetBrains Mono'",size:10},callback:v=>v+' SP'},
           border:{display:false}}
      }
    }
  });
}

function buildIssues(s){
  document.getElementById('ilbl').textContent=`Historias de Usuario — ${s.issues.length} items`;
  document.getElementById('irows').innerHTML=s.issues.map(i=>{
    const t=i.title.length>62?i.title.slice(0,60)+'…':i.title;
    const b=i.state==='closed'?`<span class="badge closed">✓ Cerrada</span>`:`<span class="badge open">· Abierta</span>`;
    return`<tr>
      <td class="nc">#${i.number}</td>
      <td class="tc" title="${i.title.replace(/"/g,'&quot;')}">${t}</td>
      <td class="sc">${b}</td>
      <td class="spc">${i.story_points>0?i.story_points+' SP':'—'}</td>
      <td class="dc">${i.closed_at?fmtS(i.closed_at):'—'}</td>
    </tr>`;
  }).join('');
}

function toggleIssues(){
  open=!open;
  document.getElementById('ibody').style.display=open?'':'none';
  document.getElementById('tog').textContent=open?'▼ colapsar':'▶ expandir';
}

function init(){
  const tabs=document.getElementById('tabs');
  SPRINTS.forEach((s,i)=>{
    const b=document.createElement('button');
    b.className='tab'+(i===SPRINTS.length-1?' on':'');
    const m=s.sprint_name.match(/Sprint\s*\d+/i);
    b.textContent=m?m[0]:`Sprint ${i+1}`;
    b.onclick=()=>render(i);
    tabs.appendChild(b);
  });
  render(SPRINTS.length-1);
}
init();
</script>
</body>
</html>
"""

# ── HTML generation ────────────────────────────────────────────────────────────
def generate_html(sprints):
    return HTML_TEMPLATE.replace("__SPRINTS_DATA__", json.dumps(sprints, ensure_ascii=False))

# ── Main ───────────────────────────────────────────────────────────────────────
def main():
    token = get_token()
    print(f"\n🔗 Conectando a {REPO_OWNER}/{REPO_NAME}…\n")

    milestones = fetch_milestones(token)
    if not milestones:
        print("❌ No se encontraron milestones. Verifica que el token tenga el scope 'repo'.")
        sys.exit(1)

    sp_map, start_map = fetch_story_points(token)
    if not sp_map:
        print("  ⚠  No se encontraron story points en GitHub Projects.")
        print("     Verifica que el token tenga el scope 'read:project'.")
        print("     Continuando con 0 puntos por issue…\n")

    sprints = []
    for ms in milestones:
        print(f"\n📌 Procesando: {ms['title']}")
        issues = fetch_issues(ms["number"], token)
        print(f"  → {len(issues)} issues encontradas")
        data = build_sprint(ms, issues, sp_map, start_map)
        sprints.append(data)
        print(f"  → {data['total_points']} SP total · {data['completed_points']} completados · {data['remaining_points']} pendientes")

    html = generate_html(sprints)
    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        f.write(html)

    print(f"\n✅ Burndown chart generado: {OUTPUT_FILE}")
    print(f"   Abre el archivo en tu navegador para ver el resultado.\n")

if __name__ == "__main__":
    main()
