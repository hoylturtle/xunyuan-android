#!/usr/bin/env python3
"""Reproducible SDK-only build; no Gradle/Maven download during packaging."""
import hashlib, os, pathlib, shutil, subprocess, sys, zipfile, xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent
TOOLS = pathlib.Path(os.environ.get('XUNYUAN_TOOLS', os.environ.get('BTSOU_TOOLS', str(ROOT.parent / 'tools'))))
SDK = pathlib.Path(os.environ.get('ANDROID_SDK_ROOT', os.environ.get('ANDROID_HOME', str(TOOLS / 'android-sdk'))))
JAVA = pathlib.Path(os.environ.get('JAVA_HOME', str(next(TOOLS.glob('zulu*-jdk*'), TOOLS / 'jdk'))))
if not (JAVA / 'bin/javac').exists():
    jdk = next(TOOLS.glob('zulu*'), None)
    if jdk: JAVA = jdk
BT = next((SDK / 'build-tools').glob('35.*'), SDK / 'build-tools/android-15')
ANDROID = SDK / 'platforms/android-35/android.jar'
BUILD = ROOT / 'build'
BUILD.mkdir(exist_ok=True)
ENV = dict(os.environ, JAVA_HOME=str(JAVA), PATH=str(JAVA / 'bin') + os.pathsep + os.environ.get('PATH', ''))

def run(*args):
    print('>', str(args[0]).split('/')[-1], flush=True)
    subprocess.run([str(a) for a in args], check=True, cwd=ROOT, env=ENV)

def build():
    if not ANDROID.exists() or not (JAVA / 'bin/javac').exists():
        sys.exit('Need Android SDK platform 35, build-tools 35 and JDK 17+. Set ANDROID_SDK_ROOT and JAVA_HOME.')
    for name in ('aapt2', 'd8', 'zipalign', 'apksigner'):
        (BT / name).chmod(0o755)
    for folder in ('generated', 'classes', 'dex'):
        target = BUILD / folder
        if target.exists(): shutil.rmtree(target)
        target.mkdir()
    app = ROOT / 'app/src/main'
    run(BT / 'aapt2', 'compile', '--dir', app / 'res', '-o', BUILD / 'resources.zip')
    run(BT / 'aapt2', 'link', '-o', BUILD / 'base.apk', '-I', ANDROID,
        '--manifest', app / 'AndroidManifest.xml', '--java', BUILD / 'generated',
        '--min-sdk-version', '26', '--target-sdk-version', '35',
        '-A', app / 'assets', BUILD / 'resources.zip')
    sources = sorted((app / 'java').rglob('*.java')) + sorted((BUILD / 'generated').rglob('*.java'))
    jsoup = ROOT / 'app/libs/jsoup-1.18.3.jar'
    if hashlib.sha256(jsoup.read_bytes()).hexdigest() != '5be1ccd3228ae5fd6eed1bd6d827bac2bc65b91c20e9957d16ea65f739f15302':
        sys.exit('jsoup dependency checksum mismatch')
    run(JAVA / 'bin/javac', '-encoding', 'UTF-8', '-source', '8', '-target', '8',
        '-classpath', str(ANDROID) + os.pathsep + str(jsoup), '-d', BUILD / 'classes', *sources)
    with zipfile.ZipFile(BUILD / 'classes.jar', 'w') as jar:
        for p in sorted((BUILD / 'classes').rglob('*.class')): jar.write(p, p.relative_to(BUILD / 'classes'))
    run(BT / 'd8', '--lib', ANDROID, '--min-api', '26', '--output', BUILD / 'dex', BUILD / 'classes.jar', jsoup)
    shutil.copy(BUILD / 'base.apk', BUILD / 'unaligned.apk')
    with zipfile.ZipFile(BUILD / 'unaligned.apk', 'a', zipfile.ZIP_DEFLATED) as apk:
        for p in (BUILD / 'dex').glob('*.dex'): apk.write(p, p.name)
    run(BT / 'zipalign', '-f', '-p', '4', BUILD / 'unaligned.apk', BUILD / 'aligned.apk')
    signing = ROOT / 'signing'
    signing.mkdir(exist_ok=True)
    key = signing / 'btsou-development.jks'
    if not key.exists():
        run(JAVA / 'bin/keytool', '-genkeypair', '-keystore', key, '-storepass', 'android',
            '-keypass', 'android', '-alias', 'btsou', '-keyalg', 'RSA', '-keysize', '3072',
            '-validity', '10000', '-dname', 'CN=BTSOU Personal Development')
    manifest = ET.parse(app / 'AndroidManifest.xml').getroot()
    version_name = manifest.attrib['{http://schemas.android.com/apk/res/android}versionName']
    output = BUILD / ('Xunyuan-' + version_name + '.apk')
    run(BT / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'pass:android',
        '--key-pass', 'pass:android', '--out', output, BUILD / 'aligned.apk')
    run(BT / 'apksigner', 'verify', '--verbose', '--print-certs', output)
    run(BT / 'zipalign', '-c', '4', output)
    print('APK:', output, '\nSHA256:', hashlib.sha256(output.read_bytes()).hexdigest())

if __name__ == '__main__': build()
