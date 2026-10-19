#!/usr/bin/env python3
"""Validate named build notes and package a traceable install artifact."""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parent.parent
VERSION_PATTERN = re.compile(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?')


def validate(root=ROOT):
    version = (root / 'VERSION').read_text().strip()
    match = VERSION_PATTERN.fullmatch(version)
    if not match or (match[4] and any(p.isdigit() and len(p) > 1 and p[0] == '0' for p in match[4].split('.'))):
        raise ValueError('VERSION must contain a valid semantic version, for example 0.1.0-alpha.1.')
    changes = (root / 'CHANGELOG.md').read_text()
    headings = re.findall(r'^## \[([^]]+)\](?: - (\d{4}-\d{2}-\d{2}))?\s*$', changes, re.M)
    if not headings or headings[0][0] != 'Unreleased':
        raise ValueError('CHANGELOG.md must start with an Unreleased section.')
    versions = [name for name, _ in headings]
    if len(versions) != len(set(versions)):
        raise ValueError('Changelog version headings must be unique.')
    dated = dict(headings).get(version)
    if not dated:
        raise ValueError('The VERSION value needs a dated CHANGELOG.md entry.')
    datetime.date.fromisoformat(dated)
    start = changes.index(f'## [{version}]')
    notes = changes[start:].split('\n## ', 1)[0].strip() + '\n'
    if not re.search(r'^- \S', notes, re.M):
        raise ValueError('The current changelog entry needs at least one change.')
    pending = changes[changes.index('## [Unreleased]'):].split('\n## ', 1)[0].strip()
    if re.search(r'^- \S', pending, re.M):
        notes = pending + '\n\n' + notes
    return version, notes


def package(apk, build, commit, output, root=ROOT):
    version, notes = validate(root)
    if not 1 <= build <= 2_100_000_000:
        raise ValueError('Build number is outside the Android versionCode range.')
    if not re.fullmatch(r'[0-9a-f]{40}', commit):
        raise ValueError('A complete Git commit SHA is required.')
    output.mkdir(parents=True, exist_ok=True)
    target = output / f'hodd-mobile-{version}-build{build}.apk'
    shutil.copyfile(apk, target)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    (output / 'SHA256SUMS.txt').write_text(f'{digest}  {target.name}\n')
    (output / 'build-info.json').write_text(json.dumps({
        'versionName': version, 'versionCode': build, 'commit': commit,
        'apk': target.name, 'sha256': digest,
    }, indent=2) + '\n')
    (output / 'release-notes.md').write_text(notes)
    shutil.copyfile(root / 'CHANGELOG.md', output / 'CHANGELOG.md')
    return target


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path)
    parser.add_argument('--build', type=int)
    parser.add_argument('--commit')
    parser.add_argument('--output', type=Path, default=ROOT / 'dist')
    args = parser.parse_args()
    if args.apk:
        if args.build is None or args.commit is None:
            parser.error('--apk requires --build and --commit')
        print(package(args.apk, args.build, args.commit, args.output))
    else:
        print(f'Version and changelog valid: {validate()[0]}')
