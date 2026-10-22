from pathlib import Path
import os,subprocess,zipfile
root=Path(__file__).resolve().parent
workspace=root.parent.parent
tc=Path((workspace/'toolchain.path').read_text().strip())
jdk=next(p for p in (tc/'jdk').iterdir() if (p/'bin/javac.exe').is_file())
sdk=tc/'android-sdk';bt=sdk/'build-tools/35.0.0';android=sdk/'platforms/android-35/android.jar'
env=os.environ.copy();env['JAVA_HOME']=str(jdk)
out=root/'out';out.mkdir(exist_ok=True);classes=out/'classes';classes.mkdir(exist_ok=True);dex=out/'dex';dex.mkdir(exist_ok=True)
def run(*args):subprocess.run([str(x) for x in args],env=env,check=True)
run(jdk/'bin/javac.exe','-encoding','UTF-8','--release','8','-cp',android,'-d',classes,*sorted((root/'src').glob('*.java')))
run(bt/'d8.bat','--lib',android,'--min-api','26','--output',dex,*classes.rglob('*.class'))
run(bt/'aapt2.exe','link','-I',android,'--manifest',root/'AndroidManifest.xml','-A',root/'assets','-o',out/'unsigned.apk')
with zipfile.ZipFile(out/'unsigned.apk','a') as apk:apk.write(dex/'classes.dex','classes.dex')
run(bt/'zipalign.exe','-f','4',out/'unsigned.apk',out/'aligned.apk')
run(bt/'apksigner.bat','sign','--ks',Path.home()/'.android/debug.keystore','--ks-pass','pass:android','--out',out/'reader-probe.apk',out/'aligned.apk')
print(out/'reader-probe.apk')
