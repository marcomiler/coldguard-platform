#!/usr/bin/env python3
"""
ColdGuard Burndown Chart Generator
Genera un HTML standalone con burndown charts por sprint.
Uso:  python fetch_burndown.py <GITHUB_TOKEN>
      python fetch_burndown.py          # pide el token interactivamente
Deps: pip install requests
"""

import json
import os
import re
import sys
from datetime import date, datetime, timedelta

import requests

# ── Configuración ──────────────────────────────────────────────────────────────
REPO_OWNER  = "marcomiler"
REPO_NAME   = "coldguard-platform"
OUTPUT_FILE = "coldguard_burndown.html"
GITHUB_API  = "https://api.github.com"
GITHUB_GQL  = "https://api.github.com/graphql"

SP_FIELDS = {
    "estimate", "storypoints", "story_points",
    "points", "sp", "estimacion", "estimación", "puntos",
}
TARGET_DATE_FIELDS = {
    "targetdate", "target_date", "target",
    "fechaobjetivo", "fecha_objetivo",
}

GQL_PROJECT_DATA = """
query($owner: String!, $repo: String!, $cursor: String) {
  repository(owner: $owner, name: $repo) {
    projectsV2(first: 5) {
      nodes {
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

# ── Auth ───────────────────────────────────────────────────────────────────────
def get_token() -> str:
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

def rest_headers(token: str) -> dict:
    return {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github.v3+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }

def gql_headers(token: str) -> dict:
    return {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

# ── REST helpers ───────────────────────────────────────────────────────────────
def paginate(url: str, token: str, params: dict = None) -> list:
    params = {**(params or {}), "per_page": 100}
    results, page = [], 1
    while True:
        r = requests.get(url, headers=rest_headers(token), params={**params, "page": page})
        r.raise_for_status()
        batch = r.json()
        if not batch:
            break
        results.extend(batch)
        if len(batch) < 100:
            break
        page += 1
    return results

def fetch_milestones(token: str) -> list:
    print("📅 Obteniendo milestones…")
    milestones = paginate(
        f"{GITHUB_API}/repos/{REPO_OWNER}/{REPO_NAME}/milestones",
        token,
        {"state": "all", "sort": "due_on", "direction": "asc"},
    )
    print(f"   → {len(milestones)} milestones encontrados")
    return milestones

def fetch_issues(milestone_number: int, token: str) -> list:
    all_items = paginate(
        f"{GITHUB_API}/repos/{REPO_OWNER}/{REPO_NAME}/issues",
        token,
        {"milestone": milestone_number, "state": "all"},
    )
    return [i for i in all_items if "pull_request" not in i]

# ── GraphQL ────────────────────────────────────────────────────────────────────
def _normalize(field_name: str) -> str:
    return field_name.lower().replace(" ", "").replace("_", "")

def fetch_project_data(token: str) -> tuple[dict, dict]:
    """Devuelve (sp_map, target_map) indexados por número de issue."""
    print("📊 Consultando GitHub Projects v2 (Estimate y Target date)…")
    sp_map, target_map, cursor = {}, {}, None

    while True:
        r = requests.post(
            GITHUB_GQL,
            json={"query": GQL_PROJECT_DATA, "variables": {
                "owner": REPO_OWNER, "repo": REPO_NAME, "cursor": cursor,
            }},
            headers=gql_headers(token),
        )
        r.raise_for_status()
        payload = r.json()

        if errors := payload.get("errors"):
            for e in errors:
                print(f"   ⚠ GraphQL: {e.get('message', e)}")
            break

        projects = (
            payload.get("data", {})
            .get("repository", {})
            .get("projectsV2", {})
            .get("nodes", [])
        )
        has_next = False

        for project in projects:
            items_data = project.get("items", {})
            page_info  = items_data.get("pageInfo", {})
            if page_info.get("hasNextPage"):
                has_next = True
                cursor = page_info["endCursor"]

            for item in items_data.get("nodes", []):
                issue_num = (item.get("content") or {}).get("number")
                if not issue_num:
                    continue
                for fv in item.get("fieldValues", {}).get("nodes", []):
                    if not fv:
                        continue
                    fname = _normalize((fv.get("field") or {}).get("name", ""))
                    if fname in SP_FIELDS and fv.get("number") is not None:
                        sp_map[issue_num] = int(fv["number"])
                    elif fname in TARGET_DATE_FIELDS and fv.get("date"):
                        target_map[issue_num] = fv["date"]

        if not has_next:
            break

    print(f"   → {len(sp_map)} issues con SP · {len(target_map)} con Target date")
    return sp_map, target_map

# ── Burndown math ──────────────────────────────────────────────────────────────
def parse_date(value: str | None) -> date | None:
    return datetime.strptime(value[:10], "%Y-%m-%d").date() if value else None

def sp_from_labels(labels: list) -> int:
    for label in labels:
        name = label.get("name", "")
        if name.lower().startswith("sp:"):
            try:
                return int(name.split(":")[1].strip())
            except (ValueError, IndexError):
                pass
    return 0

def issue_sp(issue: dict, sp_map: dict) -> int:
    return sp_map.get(issue["number"]) or sp_from_labels(issue.get("labels", []))

def sprint_start_date(milestone: dict) -> date:
    desc  = milestone.get("description") or ""
    match = re.search(r"start\s*:\s*(\d{4}-\d{2}-\d{2})", desc, re.IGNORECASE)
    return parse_date(match.group(1)) if match else parse_date(milestone.get("created_at"))

def build_actual_line(issues: list, sp_map: dict, start: date, end: date) -> list:
    total  = sum(issue_sp(i, sp_map) for i in issues)
    points = []
    d = start
    while d <= min(end, date.today()):
        closed_sp = sum(
            issue_sp(i, sp_map) for i in issues
            if i["state"] == "closed"
            and (cd := parse_date(i.get("closed_at"))) is not None
            and cd <= d
        )
        points.append({"date": d.isoformat(), "remaining": total - closed_sp})
        d += timedelta(days=1)
    return points

def build_ideal_line(total: int, start: date, end: date) -> list:
    span   = max((end - start).days, 1)
    points = []
    d, k   = start, 0
    while d <= end:
        points.append({"date": d.isoformat(), "remaining": round(total * (1 - k / span), 2)})
        d += timedelta(days=1)
        k += 1
    return points

def build_sprint(milestone: dict, issues: list, sp_map: dict, target_map: dict) -> dict:
    start = sprint_start_date(milestone)
    end   = parse_date(milestone.get("due_on")) or start + timedelta(days=14)

    total       = sum(issue_sp(i, sp_map) for i in issues)
    completed   = sum(issue_sp(i, sp_map) for i in issues if i["state"] == "closed")
    sprint_days = max((end - start).days, 1)

    issue_rows = sorted(
        [{
            "number":       i["number"],
            "title":        i["title"],
            "state":        i["state"],
            "story_points": issue_sp(i, sp_map),
            "target_date":  target_map.get(i["number"]),
            "closed_at":    i["closed_at"][:10] if i.get("closed_at") else None,
        } for i in issues],
        key=lambda x: (x["state"] != "closed", x["number"]),
    )

    return {
        "sprint_name":      milestone["title"],
        "start_date":       start.isoformat(),
        "end_date":         end.isoformat(),
        "total_points":     total,
        "completed_points": completed,
        "remaining_points": total - completed,
        "velocity":         round(completed / sprint_days, 1),
        "actual":           build_actual_line(issues, sp_map, start, end),
        "ideal":            build_ideal_line(total, start, end),
        "issues":           issue_rows,
    }

# ── HTML ───────────────────────────────────────────────────────────────────────
HTML_TEMPLATE = r"""<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>ColdGuard · Burndown</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Syne:wght@700;800&family=JetBrains+Mono:wght@400;500&family=Barlow+Condensed:wght@500;600;700&family=Barlow:wght@400;500&display=swap">
<style>
:root{
  --bg:#07101A;--sf1:#0D1B2B;--sf2:#132840;--sf3:#1A3550;
  --bd:#1E3D54;--bd2:#285270;
  --cyan:#07C3E8;--steel:#2B5070;--mint:#0FB87A;--amber:#F39C0A;--danger:#EF4444;
  --t1:#CAE8F8;--t2:#4D8FAD;--t3:#2D607A;
  --fd:'Syne',sans-serif;--fm:'JetBrains Mono',monospace;
  --fl:'Barlow Condensed',sans-serif;--fb:'Barlow',sans-serif
}
*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
body{background:var(--bg);color:var(--t1);font-family:var(--fb);min-height:100vh;padding:2rem clamp(1rem,4vw,2.5rem)}
.wrap{max-width:1100px;margin:0 auto}

/* Header */
.hdr{display:flex;flex-wrap:wrap;justify-content:space-between;align-items:flex-end;gap:1rem;margin-bottom:1.75rem}
.hdr-l{display:flex;flex-direction:column;gap:3px}
.eyebrow{font-family:var(--fl);font-size:.65rem;font-weight:700;letter-spacing:.2em;text-transform:uppercase;color:var(--cyan)}
.page-h{font-family:var(--fd);font-size:clamp(1.5rem,3vw,2rem);font-weight:800;color:var(--t1);line-height:1.15;text-wrap:balance}
.dates-sub{font-family:var(--fm);font-size:.68rem;color:var(--t3);margin-top:2px}

/* Tabs */
.tabs{display:flex;gap:3px;background:var(--sf1);border:1px solid var(--bd);border-radius:8px;padding:3px;align-self:flex-start}
.tab{font-family:var(--fl);font-size:.75rem;font-weight:700;letter-spacing:.04em;padding:.4rem .875rem;border:1px solid transparent;border-radius:5px;background:transparent;color:var(--t3);cursor:pointer;transition:color .15s,background .15s,border-color .15s;white-space:nowrap}
.tab:hover{color:var(--t2);background:var(--sf2)}
.tab.on{background:var(--sf3);color:var(--cyan);border-color:var(--bd2)}

/* Status chip */
.chip{display:inline-flex;align-items:center;gap:5px;padding:3px 10px;border-radius:99px;font-family:var(--fl);font-size:.65rem;font-weight:700;letter-spacing:.08em;text-transform:uppercase;border:1px solid}
.chip.done{background:rgba(15,184,122,.1);color:var(--mint);border-color:rgba(15,184,122,.25)}
.chip.active{background:rgba(7,195,232,.1);color:var(--cyan);border-color:rgba(7,195,232,.25)}
.chip.behind{background:rgba(243,156,10,.1);color:var(--amber);border-color:rgba(243,156,10,.25)}

/* KPIs */
.kpis{display:grid;grid-template-columns:repeat(auto-fit,minmax(155px,1fr));gap:10px;margin-bottom:1.25rem}
.kpi{background:var(--sf1);border:1px solid var(--bd);border-radius:10px;padding:1rem 1.125rem;display:flex;flex-direction:column;gap:5px;transition:border-color .2s}
.kpi:hover{border-color:var(--bd2)}
.kpi-lbl{font-family:var(--fl);font-size:.62rem;font-weight:700;letter-spacing:.14em;text-transform:uppercase;color:var(--t3)}
.kpi-val{font-family:var(--fm);font-size:1.65rem;font-weight:500;color:var(--t1);font-variant-numeric:tabular-nums;line-height:1}
.kpi-sub{font-family:var(--fm);font-size:.63rem;color:var(--t3)}
.kpi.hi .kpi-val{color:var(--cyan)}.kpi.good .kpi-val{color:var(--mint)}.kpi.warn .kpi-val{color:var(--amber)}

/* Chart card */
.chart-card{background:var(--sf1);border:1px solid var(--bd);border-radius:12px;padding:1.375rem 1.5rem 1.125rem;margin-bottom:1.125rem}
.chart-hdr{display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:.75rem;margin-bottom:1rem}
.chart-ttl{font-family:var(--fl);font-size:.68rem;font-weight:700;letter-spacing:.14em;text-transform:uppercase;color:var(--t2)}
.legend{display:flex;gap:1.25rem;align-items:center}
.leg-item{display:flex;align-items:center;gap:6px;font-family:var(--fl);font-size:.68rem;font-weight:600;letter-spacing:.04em;color:var(--t2)}
.leg-line{width:22px;height:2px;border-radius:1px}.leg-line.a{background:var(--cyan)}.leg-line.b{background:transparent;border-top:2px dashed var(--steel);height:0}

/* Progress bar */
.prog-wrap{margin-bottom:1rem}
.prog-hdr{display:flex;justify-content:space-between;align-items:baseline;margin-bottom:6px}
.prog-lbl{font-family:var(--fl);font-size:.62rem;font-weight:700;letter-spacing:.12em;text-transform:uppercase;color:var(--t3)}
.prog-pct{font-family:var(--fm);font-size:.72rem;font-weight:500;color:var(--cyan)}
.prog-track{height:3px;background:var(--sf3);border-radius:2px;overflow:hidden}
.prog-fill{height:100%;background:linear-gradient(90deg,var(--steel),var(--cyan));border-radius:2px;transition:width .8s cubic-bezier(.4,0,.2,1)}
.cvs-wrap{position:relative;height:300px}

/* Issues table */
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

/* Footer */
.footer{margin-top:1.5rem;display:flex;justify-content:space-between;align-items:center;gap:1rem}
.ft{font-family:var(--fm);font-size:.6rem;color:var(--t3);letter-spacing:.04em}

@media(max-width:600px){
  .kpis{grid-template-columns:repeat(2,1fr)}
  .cvs-wrap{height:240px}
  .dc{display:none}
}
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
    <canvas id="chart" role="img" aria-label="Burndown chart"></canvas>
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
        <th style="width:80px">Objetivo</th>
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
let active = 0, chart = null, tableOpen = true;

const fmtShort = iso => new Date(iso + 'T12:00:00').toLocaleDateString('es-MX', { month: 'short', day: 'numeric' });
const fmtLong  = iso => new Date(iso + 'T12:00:00').toLocaleDateString('es-MX', { year: 'numeric', month: 'short', day: 'numeric' });

function sprintStatus(s) {
  const now = new Date(), end = new Date(s.end_date + 'T23:59:59');
  if (now > end) {
    return s.remaining_points === 0
      ? { c: 'done',   t: '✓ Completado' }
      : { c: 'behind', t: `△ ${s.remaining_points} SP pendientes` };
  }
  const elapsed  = Math.round(((now - new Date(s.start_date)) / (end - new Date(s.start_date))) * 100);
  const progress = s.total_points > 0 ? Math.round((s.completed_points / s.total_points) * 100) : 0;
  return progress >= elapsed - 5
    ? { c: 'active', t: '● En progreso' }
    : { c: 'behind', t: '▾ Atrasado' };
}

function renderKPIs(s) {
  const remClass = s.remaining_points === 0 ? 'good' : s.remaining_points <= s.total_points * 0.15 ? 'hi' : 'warn';
  document.getElementById('kpis').innerHTML = `
    <div class="kpi hi">  <span class="kpi-lbl">Total SP</span>     <span class="kpi-val">${s.total_points}</span>     <span class="kpi-sub">puntos planificados</span></div>
    <div class="kpi good"><span class="kpi-lbl">Completados</span>  <span class="kpi-val">${s.completed_points}</span> <span class="kpi-sub">puntos cerrados</span></div>
    <div class="kpi ${remClass}"><span class="kpi-lbl">Pendientes</span><span class="kpi-val">${s.remaining_points}</span><span class="kpi-sub">puntos restantes</span></div>
    <div class="kpi">     <span class="kpi-lbl">Velocidad</span>    <span class="kpi-val">${s.velocity}</span>      <span class="kpi-sub">SP / día promedio</span></div>`;
}

function renderChart(s) {
  const idealMap  = Object.fromEntries(s.ideal.map(d => [d.date, d.remaining]));
  const actualMap = Object.fromEntries(s.actual.map(d => [d.date, d.remaining]));
  const labels    = [...new Set([...s.ideal.map(d => d.date), ...s.actual.map(d => d.date)])].sort();

  if (chart) chart.destroy();
  chart = new Chart(document.getElementById('chart').getContext('2d'), {
    type: 'line',
    data: {
      labels: labels.map(fmtShort),
      datasets: [
        {
          label: 'Real',
          data: labels.map(d => actualMap[d] ?? null),
          borderColor: '#07C3E8', borderWidth: 2.5,
          pointBackgroundColor: '#07C3E8', pointBorderColor: '#0D1B2B', pointBorderWidth: 2,
          pointRadius: 3.5, pointHoverRadius: 6,
          fill: true,
          backgroundColor: ctx => {
            const g = ctx.chart.ctx.createLinearGradient(0, 0, 0, ctx.chart.height);
            g.addColorStop(0, 'rgba(7,195,232,.16)');
            g.addColorStop(1, 'rgba(7,195,232,0)');
            return g;
          },
          tension: .3, spanGaps: false, order: 1,
        },
        {
          label: 'Ideal',
          data: labels.map(d => idealMap[d] ?? null),
          borderColor: '#2B5070', borderWidth: 1.5,
          borderDash: [6, 4], pointRadius: 0, pointHoverRadius: 0,
          fill: false, tension: 0, spanGaps: false, order: 2,
        },
      ],
    },
    options: {
      responsive: true, maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      animation: { duration: 700, easing: 'easeInOutCubic' },
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: '#132840', borderColor: '#1E3D54', borderWidth: 1,
          titleColor: '#4D8FAD', bodyColor: '#CAE8F8',
          titleFont: { family: "'Barlow Condensed'", size: 11, weight: '700' },
          bodyFont:  { family: "'JetBrains Mono'",  size: 12 },
          padding: { x: 14, y: 12 }, cornerRadius: 8,
          callbacks: {
            title: items => items[0]?.label ?? '',
            label: item => {
              if (item.raw === null) return null;
              return (item.datasetIndex === 0 ? '  Real  ' : '  Ideal ') + item.raw + ' SP';
            },
            afterBody: items => {
              const real  = items.find(i => i.datasetIndex === 0)?.raw ?? null;
              const ideal = items.find(i => i.datasetIndex === 1)?.raw ?? null;
              if (real === null || ideal === null) return [];
              const diff = Math.round(real - ideal);
              if (diff === 0) return ['  ─ en línea ideal'];
              return ['  ' + (diff > 0 ? `▲ ${diff} SP sobre ideal` : `▼ ${Math.abs(diff)} SP bajo ideal`)];
            },
          },
        },
      },
      scales: {
        x: {
          grid: { color: 'rgba(255,255,255,.04)', drawBorder: false },
          ticks: { color: '#2D607A', font: { family: "'JetBrains Mono'", size: 10 }, maxRotation: 45, autoSkip: true, maxTicksLimit: 12 },
          border: { display: false },
        },
        y: {
          min: 0, suggestedMax: s.total_points * 1.05,
          grid: { color: 'rgba(255,255,255,.05)', drawBorder: false },
          ticks: { color: '#2D607A', font: { family: "'JetBrains Mono'", size: 10 }, callback: v => v + ' SP' },
          border: { display: false },
        },
      },
    },
  });
}

function renderIssues(s) {
  document.getElementById('ilbl').textContent = `Historias de Usuario — ${s.issues.length} items`;
  document.getElementById('irows').innerHTML = s.issues.map(i => {
    const title  = i.title.length > 62 ? i.title.slice(0, 60) + '…' : i.title;
    const badge  = i.state === 'closed'
      ? `<span class="badge closed">✓ Cerrada</span>`
      : `<span class="badge open">· Abierta</span>`;
    const target = i.target_date ? fmtShort(i.target_date) : '—';
    return `<tr>
      <td class="nc">#${i.number}</td>
      <td class="tc" title="${i.title.replace(/"/g, '&quot;')}">${title}</td>
      <td class="sc">${badge}</td>
      <td class="spc">${i.story_points > 0 ? i.story_points + ' SP' : '—'}</td>
      <td class="dc">${target}</td>
    </tr>`;
  }).join('');
}

function render(idx) {
  active = idx;
  const s = SPRINTS[idx];

  document.querySelectorAll('.tab').forEach((t, i) => t.classList.toggle('on', i === idx));
  document.getElementById('sname').textContent  = s.sprint_name;
  document.getElementById('sdates').textContent = `${fmtLong(s.start_date)} – ${fmtLong(s.end_date)}`;

  const st = sprintStatus(s);
  const chip = document.getElementById('chip');
  chip.className   = 'chip ' + st.c;
  chip.textContent = st.t;

  const pct = s.total_points > 0 ? Math.round((s.completed_points / s.total_points) * 100) : 0;
  document.getElementById('ppct').textContent      = pct + '%';
  document.getElementById('pfill').style.width     = pct + '%';
  document.getElementById('fgen').textContent      = 'Generado ' + new Date().toLocaleDateString('es-MX', { year: 'numeric', month: 'short', day: 'numeric' });

  renderKPIs(s);
  renderChart(s);
  renderIssues(s);
}

function toggleIssues() {
  tableOpen = !tableOpen;
  document.getElementById('ibody').style.display = tableOpen ? '' : 'none';
  document.getElementById('tog').textContent      = tableOpen ? '▼ colapsar' : '▶ expandir';
}

function init() {
  const tabs = document.getElementById('tabs');
  SPRINTS.forEach((s, i) => {
    const btn = document.createElement('button');
    btn.className   = 'tab' + (i === SPRINTS.length - 1 ? ' on' : '');
    const match     = s.sprint_name.match(/Sprint\s*\d+/i);
    btn.textContent = match ? match[0] : `Sprint ${i + 1}`;
    btn.onclick     = () => render(i);
    tabs.appendChild(btn);
  });
  render(SPRINTS.length - 1);
}

init();
</script>
</body>
</html>
"""

# ── Output ─────────────────────────────────────────────────────────────────────
def generate_html(sprints: list) -> str:
    return HTML_TEMPLATE.replace("__SPRINTS_DATA__", json.dumps(sprints, ensure_ascii=False))

# ── Main ───────────────────────────────────────────────────────────────────────
def main() -> None:
    token = get_token()
    print(f"\n🔗 Conectando a {REPO_OWNER}/{REPO_NAME}…\n")

    milestones = fetch_milestones(token)
    if not milestones:
        print("❌ Sin milestones. Verifica que el token tenga scope 'repo'.")
        sys.exit(1)

    sp_map, target_map = fetch_project_data(token)
    if not sp_map:
        print("   ⚠ Sin SP desde Projects. Se usarán labels sp:X como fallback.\n")

    sprints = []
    for ms in milestones:
        print(f"\n📌 {ms['title']}")
        issues = fetch_issues(ms["number"], token)
        print(f"   → {len(issues)} issues")
        sprint = build_sprint(ms, issues, sp_map, target_map)
        sprints.append(sprint)
        print(f"   → Total {sprint['total_points']} SP · Completados {sprint['completed_points']} · Pendientes {sprint['remaining_points']}")

    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        f.write(generate_html(sprints))

    print(f"\n✅ {OUTPUT_FILE} generado correctamente.\n")

if __name__ == "__main__":
    main()
