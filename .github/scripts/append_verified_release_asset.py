"""Append one fingerprint-verified build while preserving every existing asset."""
import hashlib
import json
import os
from pathlib import Path
import urllib.parse
import urllib.request
import zipfile

config = json.loads(Path(os.environ['VERIFIED_ASSET_CONFIG']).read_text(encoding='utf-8'))
repo = os.environ['GITHUB_REPOSITORY']
assert repo == config['repository']
token = os.environ['GH_TOKEN']

def api(path, method='GET', data=None, binary=False):
    url = 'https://api.github.com/repos/' + repo + '/' + path
    headers = {'Authorization':'Bearer ' + token, 'Accept':'application/vnd.github+json',
               'X-GitHub-Api-Version':'2022-11-28', 'User-Agent':'Dory-verified-mapped-release'}
    body = json.dumps(data).encode('utf-8') if data is not None else None
    request = urllib.request.Request(url, data=body, method=method, headers=headers)
    with urllib.request.urlopen(request, timeout=120) as response:
        result = response.read()
    return result if binary else json.loads(result)

run = api('actions/runs/' + str(config['run']))
assert run['conclusion'] == 'success' and run['head_sha'] == config['source'], 'Pinned CI run must pass at the exact source commit'
source = Path('.ci/pinned') / config['jar']
assert source.is_file(), source
rows = []
entry_hashes = {}
classes = 0
with zipfile.ZipFile(source) as archive:
    for name in sorted(p for p in archive.namelist() if not p.endswith('/')):
        raw = archive.read(name)
        if name.upper() == 'META-INF/MANIFEST.MF':
            raw = '\n'.join(sorted(line for line in raw.decode('utf-8').replace('\r\n','\n').split('\n') if line)).encode('utf-8')
        elif name in config['normalizeLF']:
            raw = raw.replace(b'\r\n', b'\n')
        value = hashlib.sha256(raw).hexdigest()
        entry_hashes[name] = value
        rows.append(name + '\0' + value)
        classes += name.endswith('.class')
fingerprint = hashlib.sha256('\n'.join(rows).encode('utf-8')).hexdigest()
if fingerprint != config['fingerprint']:
    print('VERIFY_ENTRIES ' + json.dumps(entry_hashes, sort_keys=True), flush=True)
    raise AssertionError('Built classes/resources differ from the locally tested build: ' + fingerprint)
assert classes == config['classes']
release_path = 'releases/' + str(config['release'])
release = api(release_path)
preserved = {a['id']:(a['name'],a.get('digest')) for a in release['assets']}
raw = source.read_bytes()
digest = hashlib.sha256(raw).hexdigest()
existing = [a for a in release['assets'] if a['name'] == config['name']]
if existing:
    assert len(existing) == 1 and existing[0].get('digest') == 'sha256:' + digest, 'Canonical asset name already contains different bytes; never overwrite'
    asset = existing[0]
else:
    url = 'https://uploads.github.com/repos/' + repo + '/' + release_path + '/assets?name=' + urllib.parse.quote(config['name'])
    request = urllib.request.Request(url, data=raw, method='POST', headers={
        'Authorization':'Bearer ' + token, 'Content-Type':'application/java-archive', 'User-Agent':'Dory-verified-mapped-release'})
    with urllib.request.urlopen(request, timeout=180) as response:
        asset = json.load(response)
assert asset.get('digest') == 'sha256:' + digest, 'Uploaded asset digest mismatch'
marker = '<!-- dory-verified-' + config['minecraft'] + ' -->'
ending = '<!-- /dory-verified-' + config['minecraft'] + ' -->'
body = release.get('body') or ''
section = marker + '\n' + config['notes'].strip() + '\n' + ending
if marker in body:
    start = body.index(marker)
    stop = body.index(ending, start) + len(ending)
    body = body[:start] + section + body[stop:]
else:
    body = body.rstrip() + '\n\n' + section + '\n'
api(release_path, method='PATCH', data={'body':body})
after = api(release_path)
actual = {a['id']:(a['name'],a.get('digest')) for a in after['assets']}
assert all(actual.get(key) == value for key,value in preserved.items()), 'An existing asset changed'
print(json.dumps({'status':'PASS','name':config['name'],'sha256':digest,'classes':classes,
                  'fingerprint':fingerprint,'preservedAssets':len(preserved),'url':asset['browser_download_url']},indent=2))
