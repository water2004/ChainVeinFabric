"""Rehome only the two owned MC1.21 ChainVein assets after verifying new copies."""
from pathlib import Path
import copy,hashlib,io,json,os,shutil,urllib.parse,urllib.request,zipfile
ROOT=Path.cwd();REPO='water2004/ChainVeinFabric'
CONFIG=json.loads((ROOT/'.github/republish-1.1.3/rehome-config.json').read_text())
assert os.environ['GITHUB_REPOSITORY']==REPO and os.environ['GITHUB_REF'].startswith('refs/heads/dory/')
TOKEN=os.environ['GH_TOKEN'];SOURCE=411959495;TARGET=382907789
OUT=ROOT/'.ci/rehome-chain';OUT.mkdir(parents=True,exist_ok=True)
def api(path,method='GET',data=None):
    request=urllib.request.Request('https://api.github.com/repos/'+REPO+'/'+path,data=json.dumps(data).encode() if data is not None else None,method=method,headers={'Authorization':'Bearer '+TOKEN,'Accept':'application/vnd.github+json','Content-Type':'application/json','User-Agent':'Dory-rehome-owned-minimum-assets'})
    with urllib.request.urlopen(request,timeout=120) as r:raw=r.read()
    return json.loads(raw) if raw else None
def relabel(raw,mc):
    before='4.1.2+'+mc;after='4.1.0+'+mc;out=io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(raw)) as old,zipfile.ZipFile(out,'w') as new:
        metadata=json.loads(old.read('fabric.mod.json'));assert metadata['version']==before
        for info in old.infolist():
            data=old.read(info.filename)
            if info.filename in ('fabric.mod.json','META-INF/MANIFEST.MF'):data=data.replace(before.encode(),after.encode())
            new.writestr(info,data)
    result=out.getvalue()
    with zipfile.ZipFile(io.BytesIO(raw)) as old,zipfile.ZipFile(io.BytesIO(result)) as new:
        assert old.namelist()==new.namelist()
        for name in old.namelist():
            expected=old.read(name)
            if name in ('fabric.mod.json','META-INF/MANIFEST.MF'):expected=expected.replace(before.encode(),after.encode())
            assert new.read(name)==expected,('Unexpected non-label change',name)
        expected_meta=copy.deepcopy(metadata);expected_meta['version']=after
        assert json.loads(new.read('fabric.mod.json'))==expected_meta
        classes=sum(n.endswith('.class') for n in old.namelist())
    return result,expected_meta,classes
def replace_owned(body,section):
    start='<!-- dory-minimum-1.21-family -->';end='<!-- /dory-minimum-1.21-family -->'
    assert body.count(start)==1 and body.count(end)==1
    a=body.index(start);b=body.index(end,a)+len(end)
    return body[:a]+section+body[b:]
def remove_owned(body):
    start='<!-- dory-minimum-1.21-family -->';end='<!-- /dory-minimum-1.21-family -->'
    if start not in body:return body
    assert body.count(start)==1 and body.count(end)==1
    a=body.index(start);b=body.index(end,a)+len(end)
    if body[:a]==CONFIG['originalSourceNotes']+'\n\n' and body[b:]=='\n':return CONFIG['originalSourceNotes']
    return body[:a]+body[b:]
assert api('git/ref/heads/master')['object']['sha']==CONFIG['masterSha']
manifest=json.loads((ROOT/'minimum-release-files/publication-manifest.json').read_text())
assert len(manifest['files'])==36 and manifest['status']=='PASS'
prepared=[]
for move in CONFIG['moves']:
    path=ROOT/'minimum-release-files'/move['minecraft']/move['oldName'];raw=path.read_bytes()
    assert hashlib.sha256(raw).hexdigest()==move['oldSha256']
    payload,meta,classes=relabel(raw,move['minecraft']);digest=hashlib.sha256(payload).hexdigest()
    assert digest==move['newSha256'],'Canonical packaging differs from the locally verified candidate'
    prepared.append((move,payload,meta,classes))
before={rid:api('releases/'+str(rid)) for rid in [SOURCE,TARGET]}
assert before[SOURCE]['tag_name']=='v4.1.2' and before[TARGET]['tag_name']=='v4.1.0'
assert not before[SOURCE]['draft'] and not before[TARGET]['draft']
# Complete all collision and ownership checks before the first mutation.
for move,payload,meta,classes in prepared:
    old=[a for a in before[SOURCE]['assets'] if a['id']==move['oldId']]
    named=[a for a in before[SOURCE]['assets'] if a['name']==move['oldName']]
    assert not named or (len(named)==1 and named[0]['id']==move['oldId']),'Unexpected source asset ownership'
    assert not old or (len(old)==1 and old[0]['name']==move['oldName'] and old[0].get('digest')=='sha256:'+move['oldSha256'])
    new=[a for a in before[TARGET]['assets'] if a['name']==move['newName']]
    assert not new or (len(new)==1 and new[0].get('digest')=='sha256:'+move['newSha256']),'Destination canonical asset already contains different bytes'
    assert old or new,'Neither pinned source nor verified destination exists'
published=[]
for move,payload,meta,classes in prepared:
    present=[a for a in api('releases/'+str(TARGET))['assets'] if a['name']==move['newName']]
    if present:asset=present[0]
    else:
        url='https://uploads.github.com/repos/'+REPO+'/releases/'+str(TARGET)+'/assets?name='+urllib.parse.quote(move['newName'])
        request=urllib.request.Request(url,data=payload,method='POST',headers={'Authorization':'Bearer '+TOKEN,'Content-Type':'application/java-archive','User-Agent':'Dory-verified-label-rehome'})
        with urllib.request.urlopen(request,timeout=120) as r:asset=json.load(r)
    assert asset.get('digest')=='sha256:'+move['newSha256']
    with urllib.request.urlopen(urllib.request.Request(asset['browser_download_url'],headers={'User-Agent':'Dory-public-rehome-verification'}),timeout=120) as r:public=r.read()
    assert hashlib.sha256(public).hexdigest()==move['newSha256']
    with zipfile.ZipFile(io.BytesIO(public)) as z:assert json.loads(z.read('fabric.mod.json'))==meta
    published.append({**move,'newAssetId':asset['id'],'url':asset['browser_download_url'],'classFilesUnchanged':classes})
    p=OUT/'files'/move['minecraft']/move['newName'];p.parent.mkdir(exist_ok=True,parents=True);p.write_bytes(public)
target=api('releases/'+str(TARGET));body=target['body']
start='<!-- dory-minimum-1.21-family -->';end='<!-- /dory-minimum-1.21-family -->'
section=body[body.index(start):body.index(end)+len(end)]
for move in CONFIG['moves']:section=section.replace(move['oldName'],move['newName'])
section=section.replace('/releases/download/v4.1.2/','/releases/download/v4.1.0/')
newbody=replace_owned(body,section);api('releases/'+str(TARGET),'PATCH',{'body':newbody})
assert api('releases/'+str(TARGET))['body']==newbody
source=api('releases/'+str(SOURCE));source_body=remove_owned(source['body'])
api('releases/'+str(SOURCE),'PATCH',{'body':source_body})
assert api('releases/'+str(SOURCE))['body']==source_body
# Delete only the two pinned owned assets, after both replacement files are public.
for move in CONFIG['moves']:
    old=[a for a in api('releases/'+str(SOURCE))['assets'] if a['id']==move['oldId']]
    if old:
        assert old[0]['name']==move['oldName'] and old[0].get('digest')=='sha256:'+move['oldSha256']
        api('releases/assets/'+str(move['oldId']),'DELETE')
after={rid:api('releases/'+str(rid)) for rid in [SOURCE,TARGET]}
removed={m['oldId'] for m in CONFIG['moves']}
for rid in before:
    actual={a['id']:(a['name'],a.get('digest')) for a in after[rid]['assets']}
    assert all(actual.get(a['id'])==(a['name'],a.get('digest')) for a in before[rid]['assets'] if a['id'] not in removed)
    assert all(before[rid][k]==after[rid][k] for k in ['tag_name','name','draft','prerelease','target_commitish'])
    for row in (r for r in manifest['files'] if r['repository']==REPO and r['minecraft'] not in ('1.21.10','1.21.11')):
        found=[a for a in after[TARGET]['assets'] if a['name']==row['name']]
        assert len(found)==1 and found[0]['digest']=='sha256:'+row['sha256']
assert '<!-- dory-minimum-1.21-family -->' not in after[SOURCE]['body']
table=[s for s in after[TARGET]['body'].splitlines() if s.startswith('| 1.21')]
assert len(table)==12 and all('/releases/download/v4.1.0/' in s for s in table)
assert all(not any(a['id']==old for a in after[SOURCE]['assets']) for old in removed)
assert not any(a['name'] in {m['oldName'] for m in CONFIG['moves']} for a in after[SOURCE]['assets'])
assert api('git/ref/heads/master')['object']['sha']==CONFIG['masterSha']
(OUT/'verification.json').write_text(json.dumps({'status':'PASS','moves':published,'targetRelease':'v4.1.0','targetVersionCount':12,'originalAssetsPreserved':True,'masterUnchanged':True,'sourceNotesRestored':source_body==CONFIG['originalSourceNotes']},indent=2))
print('REHOME_CHAIN_PASS',json.dumps(published),flush=True)
