#!/usr/bin/env python3
"""Build a single-file portable Windows EXE with the JDK."""
import argparse,hashlib,os,pathlib,shutil,subprocess,sys,json,zipfile
root=pathlib.Path(__file__).resolve().parent.parent
out=root/'desktop/build';classes=out/'classes';app=out/'app'
VERSION='0.3.0'

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
    sources=[shared/(n+'.java') for n in ['Source','Result','SearchEngine','TapSequence','LinkHealth','ResultTools','SourceCatalog']]+list((root/'desktop/src').rglob('*.java'))+[root/'android/tests/LinkHealthTests.java',root/'android/tests/ResultToolsTests.java',root/'android/tests/SourceCatalogTests.java']
    run(tool('javac'),'-encoding','UTF-8','--release','17','-cp',os.pathsep.join(map(str,deps)),'-d',classes,*sources)
    shutil.copy2(root/'android/app/src/main/assets/sources.json',classes/'sources.json')
    (classes/'icons').mkdir()
    for icon in (root/'desktop/assets').glob('icon-*.png'):shutil.copy2(icon,classes/'icons'/icon.name)
    (classes/'fixtures').mkdir()
    for n in ['pansearch','nyaa','3MAG','BTMOVI','ECLSO','ECLZZ','bitsearch','bitsearch_page2','bitsearch_empty','pansou_python','pansou_empty_retry','aipan','aipan_empty']:shutil.copy2(root/('research/probes/'+n+'.txt'),classes/'fixtures'/(n+'.txt'))
    shutil.copy2(root/'research/probes/aipan_details.json',classes/'fixtures/aipan_details.json')
    manifest=out/'MANIFEST.MF';manifest.write_text('Manifest-Version: 1.0\nMain-Class: com.btsou.search.DesktopApp\nClass-Path: jsoup-1.18.3.jar json-20240303.jar\n\n')
    run(tool('jar'),'--create','--file',app/'Xunyuan.jar','--manifest',manifest,'-C',classes,'.')
    for p in deps:shutil.copy2(p,app/p.name)
    (app/'licenses').mkdir()
    for p in (root/'android/licenses').iterdir():shutil.copy2(p,app/'licenses'/p.name)
    shutil.copy2(root/'desktop/README.md',app/'README.md')
    for name in ['7zip-LICENSE.txt','NOTICE.md']:shutil.copy2(root/'desktop/vendor'/name,app/'licenses'/name)
    run(tool('java'),'-jar',app/'Xunyuan.jar','--self-test')
    if not args.package:return
    if os.name!='nt':raise SystemExit('Create Windows packages on Windows, not by cross-compiling on Linux.')
    image=out/'image';installers=out/'release'
    if image.exists():shutil.rmtree(image)
    installers.mkdir(exist_ok=True)
    run(tool('jpackage'),'--type','app-image','--name','Xunyuan','--app-version',VERSION,'--vendor','Xunyuan','--input',app,'--main-jar','Xunyuan.jar','--main-class','com.btsou.search.DesktopApp','--dest',image,'--add-modules','java.desktop,jdk.crypto.ec','--java-options','-Dfile.encoding=UTF-8','--jlink-options','--strip-debug --no-header-files --no-man-pages --compress=zip-0','--icon',root/'desktop/assets/Xunyuan.ico')
    # Console launcher exercises the exact packaged runtime/classes before publication.
    run(image/'Xunyuan/runtime/bin/java.exe','-jar',image/'Xunyuan/app/Xunyuan.jar','--self-test')
    # Solid LZMA2 compresses the uncompressed module image across file boundaries.
    decoder=root/'desktop/vendor/7zr.exe'
    decoder_hash='256feca8e274e5da655e2a284fabafd9f554365eb164862089dacd4e8276d282'
    if hashlib.sha256(decoder.read_bytes()).hexdigest()!=decoder_hash:raise SystemExit('Decoder checksum mismatch')
    seven=shutil.which('7z') or str(pathlib.Path(os.environ.get('ProgramFiles','C:/Program Files'))/'7-Zip/7z.exe')
    # The launcher in the app image is redundant: our portable shell starts javaw.
    payload=out/'payload.7z';payload.unlink(missing_ok=True)
    subprocess.run([seven,'a','-t7z','-mx=9','-m0=LZMA2','-md=64m','-ms=on','-mmt=2',str(payload),'Xunyuan','-xr!Xunyuan.exe'],check=True,cwd=image)
    launcher=out/'PortableLauncher.cs'
    launcher.write_text((root/'desktop/PortableLauncher.cs').read_text(encoding='utf-8').replace('PAYLOAD_SHA256',hashlib.sha256(payload.read_bytes()).hexdigest()).replace('DECODER_SHA256',decoder_hash),encoding='utf-8-sig')
    csc=pathlib.Path(os.environ['WINDIR'])/'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
    target=installers/('Xunyuan-Windows-'+VERSION+'.exe')
    run(csc,'/nologo','/target:winexe','/platform:x64','/optimize+', '/out:'+str(target),'/win32manifest:'+str(root/'desktop/portable.manifest'),'/win32icon:'+str(root/'desktop/assets/Xunyuan.ico'),'/reference:System.Windows.Forms.dll','/resource:'+str(payload)+',payload.7z','/resource:'+str(decoder)+',7zr.exe',launcher)
    old_size=26690560
    report={'version':VERSION,'previousVersion':'0.1.5','previousBytes':old_size,'exeBytes':target.stat().st_size,'reductionPercent':round((1-target.stat().st_size/old_size)*100,1),'decoderSha256':decoder_hash,'compression':'solid LZMA2','iconSizes':[16,24,32,48,64,128,256]}
    (installers/'build-report.json').write_text(json.dumps(report,indent=2)+'\n')
    if target.stat().st_size>=old_size:raise SystemExit('Compressed EXE is not smaller than previous version')
    print(json.dumps(report),flush=True)
    # Test the actual single EXE, including extraction and its bundled runtime.
    run(target,'--self-test')
    # Distribute a normal ZIP containing only the portable EXE.
    archive=installers/('Xunyuan-Windows-'+VERSION+'.zip')
    with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as bundle:
        bundle.write(target,arcname=target.name)
    with zipfile.ZipFile(archive) as bundle:
        if bundle.namelist()!=[target.name] or bundle.testzip() is not None:
            raise SystemExit('Portable ZIP verification failed')
        if hashlib.sha256(bundle.read(target.name)).digest()!=hashlib.sha256(target.read_bytes()).digest():
            raise SystemExit('Portable ZIP EXE checksum mismatch')
    lines=[]
    for p in sorted(installers.iterdir()):
        if p.suffix in ('.exe','.zip'):lines.append(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name)
    (installers/'SHA256SUMS.txt').write_text('\n'.join(lines)+'\n')
    print('Windows packages:',installers)

if __name__=='__main__':main()
