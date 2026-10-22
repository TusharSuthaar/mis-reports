"""Loads the demo month (demo-data/, see generate.py) into a running, EMPTY app through its own API, the way a user would
on the screens, then processes August 2026. Standard library only.

    python tools/demo-data/seed.py                      # app on http://localhost:8090
    MIS_URL=http://localhost:8090 python tools/demo-data/seed.py

Signs in with MIS_BOOTSTRAP_ADMIN_USERNAME / _PASSWORD (environment, else the repo's .env). Refuses to run when the app
already has clients, so it can never mix demo data into real data. Prints counts and totals only.
"""
import http.cookiejar
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
DATA = os.path.join(ROOT, 'demo-data')
BASE = os.environ.get('MIS_URL', 'http://localhost:8090').rstrip('/')
MONTH = '2026-08'
FROM = '2026-04-01'   # Master MIS RMs and partners apply from this day

jar = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def env(name):
    if os.environ.get(name):
        return os.environ[name]
    try:
        with open(os.path.join(ROOT, '.env')) as f:
            for line in f:
                k, _, v = line.strip().partition('=')
                if k == name:
                    return v.strip()
    except FileNotFoundError:
        pass
    return None


def csrf():
    opener.open(BASE + '/api/auth/csrf').read()
    return next(c.value for c in jar if c.name == 'XSRF-TOKEN')


def send(method, path, data=None, content_type=None):
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header('Accept', 'application/json')
    if content_type:
        req.add_header('Content-Type', content_type)
    if method != 'GET':
        req.add_header('X-XSRF-TOKEN', csrf())
    try:
        with opener.open(req) as r:
            text = r.read().decode()
            return json.loads(text) if text else None
    except urllib.error.HTTPError as e:
        text = e.read().decode()
        try:
            detail = json.loads(text).get('detail', text)
        except ValueError:
            detail = text
        sys.exit('%s %s failed (%s): %s' % (method, path, e.code, detail))


def call(method, path, body=None):
    return send(method, path, None if body is None else json.dumps(body).encode(), 'application/json')


def upload(path, file_name, **fields):
    boundary = uuid.uuid4().hex
    parts = []
    for k, v in fields.items():
        parts.append(('--%s\r\nContent-Disposition: form-data; name="%s"\r\n\r\n%s\r\n' % (boundary, k, v)).encode())
    with open(os.path.join(DATA, file_name), 'rb') as f:
        content = f.read()
    parts.append(('--%s\r\nContent-Disposition: form-data; name="file"; filename="%s"\r\n'
                  'Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n' % (boundary, file_name)).encode()
                 + content + b'\r\n')
    parts.append(('--%s--\r\n' % boundary).encode())
    return send('POST', path, b''.join(parts), 'multipart/form-data; boundary=' + boundary)


def wait(job_id, what):
    while True:
        j = call('GET', '/api/jobs/%d' % job_id)
        if j['status'] in ('DONE', 'FAILED'):
            break
        time.sleep(1)
    if j['status'] == 'FAILED':
        sys.exit('%s failed: %s' % (what, j.get('error')))
    return j.get('result') or {}


def step(text):
    print('-', text, flush=True)


# ---- sign in, and never touch an app that has data -------------------------------------------------------------------------
user, password = env('MIS_BOOTSTRAP_ADMIN_USERNAME'), env('MIS_BOOTSTRAP_ADMIN_PASSWORD')
if not user or not password:
    sys.exit('Set MIS_BOOTSTRAP_ADMIN_USERNAME / MIS_BOOTSTRAP_ADMIN_PASSWORD (environment or .env)')
call('POST', '/api/auth/login', {'username': user, 'password': password})
if call('GET', '/api/clients?size=1')['total'] > 0:
    sys.exit('This app already has clients: the demo data is only loaded into an empty app. Nothing was changed.')

step('Setup import: regions, branches, RMs, RDs')
r = wait(upload('/api/imports/masters', 'masters.xlsx', dryRun='false')['jobId'], 'masters import')
print('  ', {k: v for k, v in r.items() if isinstance(v, (int, str))})

step('Finesse client master')
r = upload('/api/finesse/client-master', 'ClientMasterReport.xlsx')
print('  ', {k: v for k, v in r.items() if isinstance(v, (int, str))})

step('Master MIS (partner rate cards; each client\'s RM and partners from %s)' % FROM)
preview = upload('/api/share-load/preview', 'Master_MIS.xlsx', **{'from': FROM})
if preview.get('error') or preview.get('unknownRms'):
    sys.exit('Master MIS preview: %s %s' % (preview.get('error'), preview.get('unknownRms')))
print('   rows %(rows)s, new clients %(newClients)s, shares to set %(toSet)s' % preview)
wait(call('POST', '/api/share-load', {'token': preview['token'], 'fileName': preview['fileName'], 'from': FROM})['jobId'], 'Master MIS load')

step('Product entries')
partners = {p['name'].upper(): p['id'] for p in call('GET', '/api/partners')}
products = json.load(open(os.path.join(DATA, 'products.json')))
platforms = {}
for p in products['platforms']:
    platforms[p['name']] = call('POST', '/api/platforms', {'name': p['name']})['id']
    call('POST', '/api/platforms/%d/terms' % platforms[p['name']], p['terms'])
clients = {}


def client(pan):
    if pan not in clients:
        items = call('GET', '/api/clients?size=5&q=' + urllib.parse.quote(pan))['items']
        hit = next((c for c in items if c.get('pan') and c['pan'][:5] == pan[:5] and c['pan'][-1] == pan[-1]), None)
        if not hit:
            sys.exit('Client with demo PAN %s not found after the Master MIS load' % pan)
        clients[pan] = hit
    return clients[pan]


PATHS = {'fd': '/api/fd', 'bonds': '/api/bonds', 'aif': '/api/aif', 'loans': '/api/loans', 'gift': '/api/gift', 'pms': '/api/pms',
         'pms-payout': '/api/pms/payouts', 'nps': '/api/nps', 'p2p': '/api/p2p'}
counts = {}
for e in products['entries']:
    body = {k: v for k, v in e['body'].items() if v is not None}
    if 'partner' in body:
        body['partnerId'] = partners[body.pop('partner').upper()]
    if e['kind'] in ('bonds', 'aif'):
        body['platformId'] = platforms[body.pop('platform')]
    if e['client']:
        c = client(e['client']['pan'])
        body['clientId'] = c['id']
        body['rmId'] = c['rmId']
    call('POST', PATHS[e['kind']], body)
    counts[e['kind']] = counts.get(e['kind'], 0) + 1
print('  ', counts)

step('Finesse MF brokerage for %s' % MONTH)
r = wait(upload('/api/uploads', 'MFBrokerage_Aug2026.xlsx', month=MONTH)['jobId'], 'MF upload')
if not r.get('uploadId'):
    sys.exit('MF file not staged: %s' % r.get('errors'))
saved = call('POST', '/api/uploads/%d/commit' % r['uploadId'])
print('   saved %s lines, net %s' % (saved['lines']['lines'], saved['lines']['net']))

step('Equity brokerage report')
r = upload('/api/equity/upload', 'Equity_Brokerage_Aug2026.xlsx')
print('   %(trades)s trades, %(clients)s clients (%(clientsNotFound)s not found), franchise share %(shareTotal)s' % r)

step('Pre-check and process %s' % MONTH)
period = next(m for m in call('GET', '/api/months') if m['month'] == MONTH)
ready = call('GET', '/api/months/%d/readiness' % period['periodId'])
if not ready['ready']:
    sys.exit('Not ready to process: no RM %s, missing %% %s, equity PANs not in Clients %s'
             % (ready['notInMaster'], ready['missingPercent'], ready['equityNotInClients']))
r = wait(call('POST', '/api/months/%d/process' % period['periodId'])['jobId'], 'processing')
print('   %(lines)s lines, net %(net)s, partners %(partners)s, Epsilon %(epsilon)s' % r)
print('Done. Sign in as %s at the app (http://localhost:5180 with docker compose) and open Dashboard for August 2026.' % user)
