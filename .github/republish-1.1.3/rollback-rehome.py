"""Restore this turn's two asset moves; preserve author files and concurrent notes."""
from pathlib import Path
import hashlib,json,os,urllib.parse,urllib.request
ROOT=Path.cwd();REPO='water2004/ChainVeinFabric'
CONFIG=json.loads((ROOT/'.github/republish-1.1.3/rollback-config.json').read_text())
assert os.environ['GITHUB_REPOSITORY']==REPO and os.environ['GITHUB_REF'].startswith('refs/heads/dory/')
TOKEN=os.environ['GH_TOKEN'];OUT=ROOT/'.ci/rollback-rehome';OUT.mkdir(exist_ok=True,parents=True)
def api(path,method='GET',data=None):
    req=urllib.request.Request('https://api.github.com/repos/'+REPO+'/'+path,data=json.dumps(data).encode() if data is not None else None,method=method,headers={'Authorization':'Bearer '+TOKEN,'Accept':'application/vnd.github+json','Content-Type':'application/json','User-Agent':'Dory-user-requested-rollback'})
    with urllib.request.urlopen(req,timeout=120) as r:raw=r.read()
    return json.loads(raw) if raw else None
START='<!-- dory-minimum-1.21-family -->';END='<!-- /dory-minimum-1.21-family -->'
def block(body):
    assert body.count(START)==1 and body.count(END)==1
    a=body.index(START);b=body.index(END,a)+len(END)
    return a,b,body[a:b]
assert api('git/ref/heads/master')['object']['sha']==CONFIG['masterSha']
before={rid:api('releases/'+str(rid)) for rid in [382907789,411959495]}
assert before[382907789]['tag_name']=='v4.1.0' and before[411959495]['tag_name']=='v4.1.2'
prepared=[]
for row in CONFIG['moves']:
    raw=(ROOT/'minimum-release-files'/row['minecraft']/row['oldName']).read_bytes()
    assert hashlib.sha256(raw).hexdigest()==row['oldSha256']
    existing=[a for a in before[411959495]['assets'] if a['name']==row['oldName']]
    assert not existing or (len(existing)==1 and existing[0].get('digest')=='sha256:'+row['oldSha256'])
    temporary=[a for a in before[382907789]['assets'] if a['name']==row['newName']]
    assert not temporary or (len(temporary)==1 and temporary[0]['id']==row['newId'] and temporary[0].get('digest')=='sha256:'+row['newSha256'])
    assert existing or temporary,'Neither the replacement nor the original exists'
    prepared.append((row,raw))
restored=[]
for row,raw in prepared:
    found=[a for a in api('releases/411959495')['assets'] if a['name']==row['oldName']]
    if found:asset=found[0]
    else:
        url='https://uploads.github.com/repos/'+REPO+'/releases/411959495/assets?name='+urllib.parse.quote(row['oldName'])
        req=urllib.request.Request(url,data=raw,method='POST',headers={'Authorization':'Bearer '+TOKEN,'Content-Type':'application/java-archive','User-Agent':'Dory-user-requested-rollback'})
        with urllib.request.urlopen(req,timeout=120) as r:asset=json.load(r)
    assert asset['digest']=='sha256:'+row['oldSha256']
    with urllib.request.urlopen(urllib.request.Request(asset['browser_download_url'],headers={'User-Agent':'Dory-rollback-public-verification'}),timeout=120) as r:public=r.read()
    assert hashlib.sha256(public).hexdigest()==row['oldSha256']
    restored.append({'name':row['oldName'],'sha256':row['oldSha256'],'id':asset['id']})
for rid,saved in [(382907789,CONFIG['beforeTargetNotes']),(411959495,CONFIG['beforeSourceNotes'])]:
    body=api('releases/'+str(rid))['body'];_,_,saved_block=block(saved)
    if rid==382907789 and body==CONFIG['currentTargetNotes']:restored_body=saved
    elif rid==411959495 and body==CONFIG['originalSourceNotes']:restored_body=saved
    elif START in body:
        a,b,_=block(body);restored_body=body[:a]+saved_block+body[b:]
    else:restored_body=body+'\n\n'+saved_block+'\n'
    api('releases/'+str(rid),'PATCH',{'body':restored_body})
    assert api('releases/'+str(rid))['body']==restored_body
for row in CONFIG['moves']:
    temporary=[a for a in api('releases/382907789')['assets'] if a['id']==row['newId']]
    if temporary:
        assert temporary[0]['name']==row['newName'] and temporary[0]['digest']=='sha256:'+row['newSha256']
        api('releases/assets/'+str(row['newId']),'DELETE')
after={rid:api('releases/'+str(rid)) for rid in before}
removed={r['newId'] for r in CONFIG['moves']}
for rid in before:
    actual={a['id']:(a['name'],a.get('digest')) for a in after[rid]['assets']}
    assert all(actual.get(a['id'])==(a['name'],a.get('digest')) for a in before[rid]['assets'] if a['id'] not in removed)
    assert all(before[rid][k]==after[rid][k] for k in ['tag_name','name','draft','prerelease','target_commitish'])
for row in CONFIG['moves']:
    assert not any(a['name']==row['newName'] for a in after[382907789]['assets'])
    found=[a for a in after[411959495]['assets'] if a['name']==row['oldName']]
    assert len(found)==1 and found[0]['digest']=='sha256:'+row['oldSha256']
assert api('git/ref/heads/master')['object']['sha']==CONFIG['masterSha']
result={'status':'PASS','scope':'Only this turn rehome reverted','restored':restored,'newAssetsRemoved':sorted(removed),'authorAssetsPreserved':True,'notesExactBeforeTurn':all(after[rid]['body']==CONFIG['beforeTargetNotes' if rid==382907789 else 'beforeSourceNotes'] for rid in after)}
(OUT/'verification.json').write_text(json.dumps(result,indent=2))
print('ROLLBACK_REHOME_PASS',json.dumps(result),flush=True)
