#!/bin/sh
# Creates an isolated simulator with synthetic photo/project fixtures; never modifies existing simulators.
set -eu
cd "$(dirname "$0")/.."
if [ -z "${JAVA_HOME:-}" ] && [ -d '/Applications/Android Studio.app/Contents/jbr/Contents/Home' ]; then
    export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
fi
scratch=$(mktemp -d)
simulator_id=''
cleanup() {
    if [ -n "$simulator_id" ] && [ "${LASERSKETCH_KEEP_TEST_SIMULATOR:-0}" != 1 ]; then
        xcrun simctl shutdown "$simulator_id" >/dev/null 2>&1 || true
        xcrun simctl delete "$simulator_id" >/dev/null 2>&1 || true
    fi
    if [ "${LASERSKETCH_KEEP_TEST_SIMULATOR:-0}" = 1 ]; then echo "Retained test simulator: $simulator_id"; fi
    rm -rf "$scratch"
}
trap cleanup EXIT INT TERM
xcrun simctl list --json > "$scratch/simulators.json"
python3 - "$scratch" <<'PY'
import json,sys,pathlib,zlib,struct,base64,re
root=pathlib.Path(sys.argv[1]); inventory=json.loads((root/'simulators.json').read_text())
runtimes=[r for r in inventory['runtimes'] if r.get('isAvailable') and r['name'].startswith('iOS') and '-iPhone' not in r['identifier']]
if not runtimes:raise SystemExit('An installed iOS simulator runtime is required.')
runtime=max(runtimes,key=lambda r:tuple(map(int,r['version'].split('.'))))
types=[t for t in inventory['devicetypes'] if t['name'].startswith('iPhone')]
device=next((t for t in types if t['name']=='iPhone 18 Pro'),types[-1])
(root/'runtime').write_text(runtime['identifier']);(root/'type').write_text(device['identifier'])
fixture=pathlib.Path('shared-ui/src/iosTest/kotlin/com/example/boschlaser/ui/IosArchiveTest.kt').read_text()
encoded=re.search(r'Base64.decode\("([^"]+)"\)',fixture).group(1)
(root/'CrossPlatformFixture.lsk').write_bytes(base64.b64decode(encoded))
def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
w,h=800,600
pixels=b''.join(b'\0'+bytes(c for x in range(w) for c in (80+x*100//w,110+y*100//h,180)) for y in range(h))
(root/'LaserSketchTest.png').write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(pixels))+chunk(b'IEND',b''))
PY
simulator_id=$(xcrun simctl create 'LaserSketch isolated UI tests' "$(cat "$scratch/type")" "$(cat "$scratch/runtime")")
xcrun simctl boot "$simulator_id"
xcrun simctl bootstatus "$simulator_id" -b
xcrun simctl addmedia "$simulator_id" "$scratch/LaserSketchTest.png"
derived_data="${LASERSKETCH_DERIVED_DATA:-/tmp/laser-sketch-ios-build}"
xcodebuild -project iosApp/LaserSketch.xcodeproj -scheme LaserSketch -configuration Debug \
    -destination "platform=iOS Simulator,id=$simulator_id" -parallel-testing-enabled NO \
    -derivedDataPath "$derived_data" CODE_SIGNING_ALLOWED=NO LASERSKETCH_TEST_PHOTO_READY=1 build-for-testing
xcrun simctl install "$simulator_id" "$derived_data/Build/Products/Debug-iphonesimulator/LaserSketch.app"
# Grant add-only access and seed Files exclusively in the disposable simulator.
xcrun simctl privacy "$simulator_id" grant photos-add com.example.boschlaser.ios
xcrun simctl get_app_container "$simulator_id" com.apple.DocumentsApp groups > "$scratch/file-groups"
python3 - "$scratch" <<'PYSEED'
import pathlib,sys,shutil
root=pathlib.Path(sys.argv[1])
groups=dict(line.split('\t',1) for line in (root/'file-groups').read_text().splitlines())
# An ordinary local Files directory exercises import from outside the app sandbox.
folder=pathlib.Path(groups['group.com.apple.FileProvider.LocalStorage'])/'File Provider Storage'/'LaserSketchTest'
folder.mkdir(parents=True,exist_ok=True)
shutil.copy(root/'CrossPlatformFixture.lsk',folder/'CrossPlatformFixture.lsk')
PYSEED
xcodebuild -project iosApp/LaserSketch.xcodeproj -scheme LaserSketch -configuration Debug \
    -destination "platform=iOS Simulator,id=$simulator_id" -parallel-testing-enabled NO \
    -derivedDataPath "$derived_data" CODE_SIGNING_ALLOWED=NO LASERSKETCH_TEST_PHOTO_READY=1 test-without-building "$@"
