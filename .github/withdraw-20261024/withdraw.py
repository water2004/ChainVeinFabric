"""Withdraw only the pinned agent-owned releases/assets requested by the user."""
import json
import os
from pathlib import Path
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parent
plan = json.loads((ROOT / 'plan.json').read_text(encoding='utf-8'))
before = json.loads((ROOT / 'before.json').read_text(encoding='utf-8'))
repo = plan['repository']
assert repo == os.environ['GITHUB_REPOSITORY']
assert os.environ['GITHUB_REF_NAME'] == plan['branch']
assert os.environ['GITHUB_ACTOR'] == 'Dory815'
base = 'https://api.github.com/repos/' + repo
headers = {'Authorization': 'Bearer ' + os.environ['GH_TOKEN'],
           'Accept': 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28'}
result = {'repository': repo, 'removed': [], 'drafts': [], 'status': 'RUNNING'}


def api(path, method='GET', data=None):
    request = urllib.request.Request(base + path, headers=headers, method=method,
        data=json.dumps(data).encode('utf-8') if data is not None else None)
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            raw = response.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        if error.code == 404 and method == 'GET':
            return None
        raise


def asset_tuple(asset):
    return (asset['id'], asset['name'], asset['digest'], asset['size'])


def release_assets(release):
    return {a['id']: a for a in release['assets']}


def verify_release_identity(live, row):
    assert live is not None and live['id'] == row['releaseId']
    assert live['tag_name'] == row['tag']


try:
    # Preflight the entire repository before the first mutation.
    for row in plan['actions']:
        live = api('/releases/' + str(row['releaseId']))
        verify_release_identity(live, row)
        assets = release_assets(live)
        if row['action'] == 'draft':
            assert live['target_commitish'] == row['target']
            assert {asset_tuple(a) for a in live['assets']} == {asset_tuple(a) for a in row['assets']}
            assert all(a['uploader']['login'] == 'github-actions[bot]' for a in live['assets'])
        else:
            assert live['draft'] is False
            assert live['body'] in (row['beforeBody'], row['afterBody'])
            for item in row['preserve']:
                assert item['id'] in assets and asset_tuple(assets[item['id']]) == asset_tuple(item)
            for item in row['assets']:
                assert item['uploader'] == 'github-actions[bot]'
                assert '+1.21' in item['name'] and item['created_at'] >= '2026-10-20'
                if item['id'] in assets:
                    assert asset_tuple(assets[item['id']]) == asset_tuple(item)
                    assert assets[item['id']]['uploader']['login'] == item['uploader']

    for row in plan['actions']:
        path = '/releases/' + str(row['releaseId'])
        live = api(path)
        verify_release_identity(live, row)
        if row['action'] == 'draft':
            if not live['draft']:
                api(path, 'PATCH', {'draft': True})
            result['drafts'].append({'id': row['releaseId'], 'tag': row['tag']})
        else:
            assets = release_assets(live)
            for item in row['assets']:
                if item['id'] in assets:
                    # Re-read the exact asset immediately before deleting it.
                    current = api('/releases/assets/' + str(item['id']))
                    assert current is not None and asset_tuple(current) == asset_tuple(item)
                    api('/releases/assets/' + str(item['id']), 'DELETE')
                result['removed'].append({'id': item['id'], 'name': item['name'], 'digest': item['digest']})
            live = api(path)
            assert live['body'] in (row['beforeBody'], row['afterBody'])
            if live['body'] != row['afterBody']:
                api(path, 'PATCH', {'body': row['afterBody']})

    # Verify every original release, including all unrelated original files.
    actions = {row['releaseId']: row for row in plan['actions']}
    kept = 0
    for original in before:
        live = api('/releases/' + str(original['id']))
        assert live is not None
        row = actions.get(original['id'])
        removed = {a['id'] for a in row['assets']} if row and row['action'] == 'remove_assets' else set()
        for key in ['tag_name', 'target_commitish', 'name', 'prerelease']:
            assert live[key] == original[key], (original['tag_name'], key)
        assert live['draft'] == (True if row and row['action'] == 'draft' else original['draft'])
        assert live['body'] == (row['afterBody'] if row and row['action'] == 'remove_assets' else original['body'])
        assets = release_assets(live)
        for item in original['assets']:
            if item['id'] in removed:
                assert item['id'] not in assets
                assert api('/releases/assets/' + str(item['id'])) is None
            else:
                assert item['id'] in assets and asset_tuple(assets[item['id']]) == asset_tuple(item)
                kept += 1
    result.update(status='PASS', preservedAssets=kept, checkedReleases=len(before))
finally:
    (ROOT / 'result.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(json.dumps(result, indent=2))
