#!/usr/bin/env python3
"""Generate the release metadata consumed by the Android app."""
import argparse, hashlib, json, pathlib, re, xml.etree.ElementTree as ET

def main():
    p=argparse.ArgumentParser();p.add_argument('--repo',default='hoylturtle/xunyuan-android');p.add_argument('--tag');p.add_argument('--notes',default='更新搜索体验与应用功能。');args=p.parse_args()
    root=pathlib.Path(__file__).resolve().parent.parent
    manifest=ET.parse(root/'android/app/src/main/AndroidManifest.xml').getroot();prefix='{http://schemas.android.com/apk/res/android}'
    code=int(manifest.attrib[prefix+'versionCode']);name=manifest.attrib[prefix+'versionName'];tag=args.tag or 'v'+name
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+',args.repo) or tag!='v'+name:raise SystemExit('Repository or release tag does not match app version')
    apk=root/'android/build'/('Xunyuan-'+name+'.apk')
    info={'versionCode':code,'versionName':name,'apkUrl':'https://github.com/'+args.repo+'/releases/download/'+tag+'/'+apk.name,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'apkSize':apk.stat().st_size,'notes':args.notes}
    (apk.parent/'update.json').write_text(json.dumps(info,ensure_ascii=False,indent=2)+'\n')
    print('Created update.json for',tag)

if __name__=='__main__':main()
