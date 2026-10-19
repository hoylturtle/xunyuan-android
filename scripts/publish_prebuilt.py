#!/usr/bin/env python3
"""Publish an already signed APK without exposing its private signing key."""
import hashlib, json, os, pathlib, subprocess, re

root=pathlib.Path(__file__).resolve().parent.parent
assets=root/'release-bootstrap'
info=json.loads((assets/'update.json').read_text())
name=info['versionName']
if not re.fullmatch(r'\d+\.\d+\.\d+',name):raise SystemExit('Invalid release version')
tag='v'+name
apk=assets/('Xunyuan-'+name+'.apk')
repo=os.environ['GITHUB_REPOSITORY']
if info['apkUrl']!='https://github.com/'+repo+'/releases/download/'+tag+'/'+apk.name:raise SystemExit('Unexpected asset URL')
if len(apk.read_bytes())!=info['apkSize'] or hashlib.sha256(apk.read_bytes()).hexdigest()!=info['sha256']:raise SystemExit('APK checksum mismatch')
args=['--repo',repo]
exists=subprocess.run(['gh','release','view',tag]+args,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode==0
if not exists:
    subprocess.run(['gh','release','create',tag,'--target',os.environ['GITHUB_SHA'],'--title','寻源 '+name,'--notes-file',str(assets/'RELEASE_NOTES.md')]+args,check=True)
subprocess.run(['gh','release','upload',tag,str(apk),str(assets/'update.json'),'--clobber']+args,check=True)
print('Published',tag)
