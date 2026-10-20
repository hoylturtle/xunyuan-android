#!/usr/bin/env python3
"""Run parser regression fixtures; --live also queries real public sources."""
import os, pathlib, subprocess, sys
root=pathlib.Path(__file__).resolve().parent
jdk=pathlib.Path(os.environ.get('JAVA_HOME',str(next((root.parent/'tools').glob('zulu*'),root.parent/'tools/jdk'))))
out=root/'tests/classes';out.mkdir(exist_ok=True)
cp=os.pathsep.join(str(root/p) for p in ['app/libs/jsoup-1.18.3.jar','tests/json-20240303.jar'])
sources=[str(root/'app/src/main/java/com/btsou/search'/n) for n in ['Source.java','Result.java','SearchEngine.java','CloudApps.java','UpdateInfo.java','TapSequence.java','LinkHealth.java','ResultTools.java','SourceCatalog.java']]+[str(p) for p in (root/'tests').glob('*.java')]
subprocess.run([str(jdk/'bin/javac'),'--release','8','-encoding','UTF-8','-cp',cp,'-d',str(out)]+sources,check=True)
for test in (['EngineTests','com.btsou.search.LinkHealthTests','com.btsou.search.ResultToolsTests','com.btsou.search.SourceCatalogTests','LiveTests'] if '--live' in sys.argv else ['EngineTests','com.btsou.search.LinkHealthTests','com.btsou.search.ResultToolsTests','com.btsou.search.SourceCatalogTests']):
    trust=[]
    if os.environ.get('BTSOU_TEST_TRUSTSTORE'):
        trust=['-Djavax.net.ssl.trustStore='+os.environ['BTSOU_TEST_TRUSTSTORE']]
    subprocess.run([str(jdk/'bin/java')]+trust+['-cp',str(out)+os.pathsep+cp,test,str(root.parent)],check=True)
