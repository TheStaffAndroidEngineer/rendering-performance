#!/usr/bin/env python3
"""Run the release scroll investigation and retain its raw evidence in a new directory."""
from pathlib import Path
import argparse,subprocess,os,json,hashlib,shutil,datetime
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);p.add_argument('--serial',default='emulator-5554');p.add_argument('--emulator-procedure',action='store_true');a=p.parse_args()
a.out.mkdir(parents=True,exist_ok=False)
adb=str(Path.home()/'Library/Android/sdk/platform-tools/adb');base=[adb,'-s',a.serial];commands=[]
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def run(args):
 start=datetime.datetime.now(datetime.timezone.utc).isoformat();r=subprocess.run(args,capture_output=True,text=True)
 commands.append({'argv':args,'start':start,'exit':r.returncode,'stdout':r.stdout,'stderr':r.stderr});(a.out/'commands.json').write_text(json.dumps(commands,indent=2)+'\n')
 if r.returncode:raise RuntimeError(r.stderr or r.stdout)
 return r.stdout
try:
 devices=run([adb,'devices','-l']);connected=[s.split()[0] for s in devices.splitlines() if len(s.split()) > 1 and s.split()[1] == 'device']
 assert connected==[a.serial], 'Use exactly the selected connected device to avoid mixing instrumentation runs'
 qemu=run(base+['shell','getprop','ro.kernel.qemu']).strip()=='1'
 if qemu:assert a.emulator_procedure,'Emulator use requires explicit --emulator-procedure; metrics are not representative hardware evidence'
 release=ROOT/'app/build/outputs/apk/release/app-release.apk';test=ROOT/'benchmark/build/outputs/apk/benchmark/benchmark-benchmark.apk'
 expected={'release':sha(release),'benchmark':sha(test)}
 run(base+['install','-r',str(release)])
 installed=run(base+['shell','pm','path','staff.android.rendering']).strip().removeprefix('package:')
 run(base+['pull',installed,str(a.out/'installed-before.apk')]);assert sha(a.out/'installed-before.apk')==expected['release']
 def observe(stage):
  result={}
  for name,cmd in [('properties',['getprop']),('battery',['dumpsys','battery']),('thermal',['dumpsys','thermalservice']),('display',['dumpsys','display']),('package',['dumpsys','package','staff.android.rendering'])]:
   text=run(base+['shell']+cmd);(a.out/f'{stage}-{name}.txt').write_text(text);result[name]=f'{stage}-{name}.txt'
  return result
 before=observe('before')
 manifest={'status':'STARTING','started_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'serial':a.serial,'emulator':qemu,'scope':'Procedure/trace demonstration only on emulator; no representative physical-device timing claim.','artifacts':expected,'setup':'Full method AOT per benchmark test; new process per iteration; six local-image load calls complete before gestures; 8 MiB cache may evict them and decoding may recur; 3 iterations of each route; default JUnit method order, not interleaved pairs.','before':before,'suppressed_errors':['EMULATOR'] if a.emulator_procedure else []}
 (a.out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
 command=[str(ROOT/'gradlew'),'-p',str(ROOT),':benchmark:connectedBenchmarkAndroidTest']
 if a.emulator_procedure:command+=['-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR']
 manifest['invocation']=command
 with (a.out/'invocation.log').open('w') as log:
  result=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,env=os.environ.copy())
 manifest['exit']=result.returncode;manifest['ended_at']=datetime.datetime.now(datetime.timezone.utc).isoformat()
 for name,source in [('android-results',ROOT/'benchmark/build/outputs/androidTest-results'),('additional-output',ROOT/'benchmark/build/outputs/connected_android_test_additional_output')]:
  if source.exists():shutil.copytree(source,a.out/name)
 manifest['after']=observe('after')
 assert sha(release)==expected['release'] and sha(test)==expected['benchmark'],'Artifacts changed during invocation'
 manifest['status']='INVOCATION_COMPLETED_REVIEW_REQUIRED' if result.returncode==0 else 'INVOCATION_FAILED'
 manifest['files']={str(f.relative_to(a.out)):sha(f) for f in a.out.rglob('*') if f.is_file() and f.name!='manifest.json'}
 (a.out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
 assert result.returncode==0,'Inspect retained invocation log and actual discovered tests'
 print('Raw benchmark invocation retained; independent frame/metric review required')
except Exception as error:
 (a.out/'failure.json').write_text(json.dumps({'status':'FAILED_OR_INCOMPLETE','error':str(error)},indent=2)+'\n');raise
