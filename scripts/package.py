"""Package source from an explicit allowlist; financial files are never included."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib
import shutil

root=Path(__file__).resolve().parents[1]
release=root/'releases'
release.mkdir(exist_ok=True)
version='1.0.0'
apk=root/'app/build/outputs/apk/release/app-release.apk'
if not apk.exists():
    raise SystemExit('Build :app:assembleRelease first.')
target=release/f'VibeFinancier-{version}.apk'
shutil.copy2(apk,target)
archive=release/f'VibeFinancier-{version}-source.zip'
allowed_files=['README.md','.gitignore','settings.gradle','build.gradle','gradle.properties','gradlew','gradlew.bat','app/build.gradle']
allowed_dirs=['app/src','gradle/wrapper','docs','importer','scripts','sheets']
skipped={'node_modules','__pycache__','build','.gradle'}
files=[root/name for name in allowed_files]
for name in allowed_dirs:
    for p in (root/name).rglob('*'):
        if not p.is_file() or p.is_symlink() or skipped.intersection(p.relative_to(root).parts):
            continue
        if p.suffix in {'.png','.ndjson'} or p.name in {'synthetic-events.json'}:
            continue
        files.append(p)
with ZipFile(archive,'w',ZIP_DEFLATED) as output:
    for p in sorted(set(files)):
        if p.is_file():
            output.write(p,p.relative_to(root).as_posix())
lines=[]
for p in (target,archive):
    lines.append(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name)
(release/'SHA256SUMS.txt').write_text('\n'.join(lines)+'\n',encoding='utf-8')
print('\n'.join(lines))
