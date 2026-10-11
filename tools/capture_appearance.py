#!/usr/bin/env python3
"""Bounded native screen capture; fresh accessibility targets, restored display settings."""
from pathlib import Path
import argparse, hashlib, json, re, sys, xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'projects/commerce-app/tools'))
from capture_checkpoint_appearance import CaptureSession, panes

def main():
 p=argparse.ArgumentParser(); p.add_argument('--output',type=Path,required=True); p.add_argument('--serial',default='emulator-5554'); p.add_argument('--configs',default='light,dark,large,wide'); p.add_argument('--candidate', help='Optional retained author checkpoint; omit to use a fresh release build'); p.add_argument('--apk', type=Path, help='Explicit installable APK'); p.add_argument('--views-only',action='store_true'); a=p.parse_args(); configs=a.configs.split(','); assert all(c in ['light','dark','large','wide'] for c in configs)
 a.package='staff.android.rendering'; a.settle_timeout=45; a.max_scrolls=8
 apk=(a.apk or (ROOT/'projects/rendering-performance/evidence'/a.candidate/'artifacts/app-release.apk' if a.candidate else ROOT/'projects/rendering-performance/app/build/outputs/apk/release/app-release.apk')).resolve()
 if not apk.is_file(): p.error('APK does not exist; run ./gradlew :app:assembleRelease or supply --apk: '+str(apk))
 a.output.mkdir(parents=True,exist_ok=False)
 s=CaptureSession(a)
 sha=lambda path:hashlib.sha256(path.read_bytes()).hexdigest()
 report={'status':'STARTING','package':a.package,'apkSha256':sha(apk),'rows':[], 'restorationErrors':[], 'scope':'API36 emulator pixels. Bounded top and scrolled collection viewports, Lab, empty state and Views comparison in light/dark/font-scale-2/wide. Not every product, all interaction states, or a physical performance/accessibility certification.'}
 report['viewsOnly']=a.views_only; report['candidate']=a.candidate
 if a.views_only: report['scope']='API36 emulator pixels, Views comparison top and two-swipe viewports only, requested configurations. Other screen evidence is separate; no physical timing/accessibility certification.'
 original=None; failure=None
 def save():
  (a.output/'result.json').write_text(json.dumps(report,indent=2)+'\n')
 def launch():
  s.adb('shell','am','force-stop',a.package)
  s.adb('shell','am','start','-W','-n',a.package+'/.MainActivity','-f','0x10008000')
  return s.settled(exact=['collection_ready'])
 def scroll(raw,count=1):
  for _ in range(count):
   nodes=panes(ET.fromstring(raw),a.package)
   if not nodes: raise RuntimeError('No fresh scroll pane')
   raw=s.swipe(nodes[-1],'down')
  return raw
 try:
  if 'StaffAndroid_API_36' not in s.adb('emu','avd','name').decode().splitlines(): raise RuntimeError('Wrong AVD')
  original=s.settings();report['originalSettings']=original
  s.adb('install','-r',str(apk))
  path=s.adb('shell','pm','path',a.package).decode().strip().removeprefix('package:')
  s.adb('pull',path,str(a.output/'installed.apk'))
  assert sha(a.output/'installed.apk')==report['apkSha256']
  for config in configs:
   s.adb('shell','am','force-stop',a.package)
   s.adb('shell','wm','size','1920x1200' if config=='wide' else 'reset')
   s.adb('shell','wm','density','240' if config=='wide' else 'reset')
   s.adb('shell','settings','put','system','font_scale','2.0' if config=='large' else '1.0')
   s.adb('shell','cmd','uimode','night','yes' if config=='dark' else 'no')
   row={'config':config,'settings':s.settings(),'frames':[]}; report['rows'].append(row)
   assert float(row['settings']['font'])==(2.0 if config=='large' else 1.0)
   assert ('Night mode: '+('yes' if config=='dark' else 'no')) in row['settings']['night']
   if config=='wide': assert 'Override size: 1920x1200' in row['settings']['size'] and 'Override density: 240' in row['settings']['density']
   else: assert 'Override' not in row['settings']['size'] and 'Override' not in row['settings']['density']
   raw=launch()
   if not a.views_only:
    row['frames'].append(s.capture(config+'-shop-top',raw))
    raw=scroll(raw,2);row['frames'].append(s.capture(config+'-shop-scrolled',raw))
    raw=s.tap('Lab',raw);row['frames'].append(s.capture(config+'-lab-top',raw))
    if config=='large' and panes(ET.fromstring(raw),a.package):
     raw=scroll(raw,2);row['frames'].append(s.capture(config+'-lab-bottom',raw))
    raw=s.tap('Empty collection',raw);row['frames'].append(s.capture(config+'-empty',raw))
   raw=launch();raw=s.tap('Lab',raw);raw=s.tap('Views comparison',raw)
   row['frames'].append(s.capture(config+'-views-top',raw))
   raw=scroll(raw,2);row['frames'].append(s.capture(config+'-views-scrolled',raw))
   row['status']='CAPTURED_NOT_REVIEWED';save();print(config+' captured',flush=True)
  report['status']='CAPTURED_NOT_REVIEWED'
 except Exception as e:
  failure=e;report['status']='FAILED';report['error']=str(e);report['failureWitness']=s.failure_witness()
 finally:
  commands=[('shell','am','force-stop',a.package)]
  if original:
   commands.append(('shell','settings','delete','system','font_scale') if original['font'] in ('null','') else ('shell','settings','put','system','font_scale',original['font']))
   commands.append(('shell','cmd','uimode','night',original['night'].split(':')[-1].strip()))
   for kind in ['size','density']:
    override=re.search(r'Override '+kind+r':\s*(\S+)',original[kind],re.I)
    commands.append(('shell','wm',kind,override.group(1) if override else 'reset'))
  for cmd in commands:
   try:s.adb(*cmd)
   except Exception as e:report['restorationErrors'].append(str(e))
  try:report['restoredSettings']=s.settings()
  except Exception as e:report['restorationErrors'].append(str(e))
  save();(a.output/'commands.json').write_text(json.dumps(s.commands,indent=2)+'\n');(a.output/'tap-guards.json').write_text(json.dumps(s.tap_guards,indent=2)+'\n')
 if failure:raise failure
 if report['restorationErrors']:raise RuntimeError('Settings restoration errors')
if __name__=='__main__':main()
