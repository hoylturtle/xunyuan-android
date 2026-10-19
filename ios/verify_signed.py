#!/usr/bin/env python3
"""Verify the signed prebuilt and compare its actions with the generated source."""
import pathlib,urllib.request,hashlib,subprocess,plistlib,shutil
root=pathlib.Path(__file__).resolve().parent
tool=root/'dist/shortcut-sign'
url='https://github.com/0xilis/shortcut-sign/releases/download/v1.0.0-beta.5/shortcut-sign-linux-x86_64'
urllib.request.urlretrieve(url,tool)
if hashlib.sha256(tool.read_bytes()).hexdigest()!='5d7eccd78b28940d0108f827d442fb5275d00191fc29f2907e9260500b743940':raise SystemExit('Verifier checksum mismatch')
tool.chmod(0o755)
for stem,name in [('Xunyuan','寻源'),('Xunyuan-Plus','寻源 Plus')]:
    signed=root/'prebuilt'/(name+'.shortcut')
    if signed.read_bytes()[:4]!=b'AEA1':raise SystemExit('Not a signed Shortcut')
    subprocess.run([str(tool),'verify','-i',str(signed)],check=True)
    extracted=root/'dist'/(stem+'.extracted.plist')
    subprocess.run([str(tool),'extract','-i',str(signed),'-o',str(extracted)],check=True)
    source=plistlib.loads((root/'dist'/(stem+'.unsigned.shortcut')).read_bytes())
    actual=plistlib.loads(extracted.read_bytes())
    if actual['WFWorkflowActions']!=source['WFWorkflowActions']:raise SystemExit('Signed actions differ from generated actions')
    shutil.copy2(signed,root/'dist'/(name+'.shortcut'))
    print(name,'signature verified; all',len(actual['WFWorkflowActions']),'actions match source')
