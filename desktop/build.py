#!/usr/bin/env python3
"""Build a single-file portable Windows EXE with the JDK."""
import argparse,hashlib,os,pathlib,shutil,subprocess,sys
root=pathlib.Path(__file__).resolve().parent.parent
out=root/'desktop/build';classes=out/'classes';app=out/'app'
VERSION='0.1.5'

def run(*args):
    subprocess.run([str(x) for x in args],check=True,cwd=root)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--package',action='store_true');args=parser.parse_args()
    jdk=os.environ.get('JAVA_HOME','')
    def tool(name):
        return pathlib.Path(jdk)/'bin'/(name+('.exe' if os.name=='nt' else '')) if jdk else name
    deps=[root/'android/app/libs/jsoup-1.18.3.jar',root/'android/tests/json-20240303.jar']
    expected=['5be1ccd3228ae5fd6eed1bd6d827bac2bc65b91c20e9957d16ea65f739f15302','3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed']
    for path,digest in zip(deps,expected):
        if hashlib.sha256(path.read_bytes()).hexdigest()!=digest:raise SystemExit('Dependency checksum mismatch: '+str(path))
    for p in [classes,app]:
        if p.exists():shutil.rmtree(p)
        p.mkdir(parents=True)
    shared=root/'android/app/src/main/java/com/btsou/search'
    sources=[shared/(n+'.java') for n in ['Source','Result','SearchEngine','TapSequence','LinkHealth']]+list((root/'desktop/src').rglob('*.java'))+[root/'android/tests/LinkHealthTests.java']
    run(tool('javac'),'-encoding','UTF-8','--release','17','-cp',os.pathsep.join(map(str,deps)),'-d',classes,*sources)
    shutil.copy2(root/'android/app/src/main/assets/sources.json',classes/'sources.json')
    (classes/'fixtures').mkdir()
    for n in ['pansearch','nyaa','3MAG','BTMOVI','ECLSO','ECLZZ']:shutil.copy2(root/('research/probes/'+n+'.txt'),classes/'fixtures'/(n+'.txt'))
    manifest=out/'MANIFEST.MF';manifest.write_text('Manifest-Version: 1.0\nMain-Class: com.btsou.search.DesktopApp\nClass-Path: jsoup-1.18.3.jar json-20240303.jar\n\n')
    run(tool('jar'),'--create','--file',app/'Xunyuan.jar','--manifest',manifest,'-C',classes,'.')
    for p in deps:shutil.copy2(p,app/p.name)
    (app/'licenses').mkdir()
    for p in (root/'android/licenses').iterdir():shutil.copy2(p,app/'licenses'/p.name)
    shutil.copy2(root/'desktop/README.md',app/'README.md')
    run(tool('java'),'-jar',app/'Xunyuan.jar','--self-test')
    if not args.package:return
    if os.name!='nt':raise SystemExit('Create Windows packages on Windows, not by cross-compiling on Linux.')
    image=out/'image';installers=out/'release'
    if image.exists():shutil.rmtree(image)
    installers.mkdir(exist_ok=True)
    run(tool('jpackage'),'--type','app-image','--name','Xunyuan','--app-version',VERSION,'--vendor','Xunyuan','--input',app,'--main-jar','Xunyuan.jar','--main-class','com.btsou.search.DesktopApp','--dest',image,'--add-modules','java.desktop,java.logging,java.naming,java.xml,jdk.crypto.ec','--java-options','-Dfile.encoding=UTF-8','--jlink-options','--strip-debug --no-header-files --no-man-pages')
    # Console launcher exercises the exact packaged runtime/classes before publication.
    run(image/'Xunyuan/runtime/bin/java.exe','-jar',image/'Xunyuan/app/Xunyuan.jar','--self-test')
    # Bundle the app image into a single, non-installing Windows executable.
    payload=pathlib.Path(shutil.make_archive(str(out/'payload'),'zip',image))
    launcher=out/'PortableLauncher.cs'
    launcher.write_text((root/'desktop/PortableLauncher.cs').read_text(encoding='utf-8').replace('PAYLOAD_SHA256',hashlib.sha256(payload.read_bytes()).hexdigest()),encoding='utf-8-sig')
    csc=pathlib.Path(os.environ['WINDIR'])/'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
    target=installers/('Xunyuan-Windows-'+VERSION+'.exe')
    run(csc,'/nologo','/target:winexe','/platform:x64','/optimize+', '/out:'+str(target),'/win32manifest:'+str(root/'desktop/portable.manifest'),'/reference:System.Windows.Forms.dll','/reference:System.IO.Compression.dll','/reference:System.IO.Compression.FileSystem.dll','/resource:'+str(payload)+',payload.zip',launcher)
    # Test the actual single EXE, including extraction and its bundled runtime.
    run(target,'--self-test')
    lines=[]
    for p in sorted(installers.iterdir()):
        if p.suffix == '.exe':lines.append(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name)
    (installers/'SHA256SUMS.txt').write_text('\n'.join(lines)+'\n')
    print('Windows packages:',installers)

if __name__=='__main__':main()
