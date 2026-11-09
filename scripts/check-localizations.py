#!/usr/bin/env python3
"""Check complete locale coverage, array sizes, placeholders and iOS resource registration."""
import collections
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
PLACEHOLDER = re.compile(r'%(?:\d+\$)?[sdwoc]')


def android(path):
    result = {}
    for node in ET.parse(path).getroot():
        key = node.attrib['name']
        assert key not in result, f'Duplicate Android key: {path}: {key}'
        result[key] = [item.text or '' for item in node] if node.tag == 'string-array' else [node.text or '']
    return result


def ios(path):
    result = {}
    for line in path.read_text().splitlines():
        if not line.strip() or line.lstrip().startswith('//'):
            continue
        match = re.fullmatch(r'("(?:[^"\\]|\\.)*")\s*=\s*("(?:[^"\\]|\\.)*");', line)
        assert match, f'Invalid strings syntax: {path}: {line}'
        key, value = (json.loads(part) for part in match.groups())
        assert key not in result, f'Duplicate iOS key: {path}: {key}'
        result[key] = [value]
    return result


def check(base, translated, path):
    assert base.keys() == translated.keys(), f'Locale keys differ: {path}: {base.keys() ^ translated.keys()}'
    for key, values in base.items():
        actual = translated[key]
        assert len(values) == len(actual), f'Array size mismatch: {path}: {key}'
        for before, after in zip(values, actual):
            assert after.strip(), f'Empty translation: {path}: {key}'
            assert collections.Counter(PLACEHOLDER.findall(before)) == collections.Counter(PLACEHOLDER.findall(after)), f'Placeholder mismatch: {path}: {key}'


def main():
    res = ROOT / 'app/src/main/res'
    base = android(res / 'values/strings.xml')
    for locale in ('en', 'b+zh+Hans', 'b+zh+Hant', 'ja', 'ko'):
        path = res / f'values-{locale}/strings.xml'
        check(base, android(path), path)
    ios_root = ROOT / 'iosApp/LaserSketch'
    project = (ROOT / 'iosApp/LaserSketch.xcodeproj/project.pbxproj').read_text()
    for name in ('Localizable.strings', 'InfoPlist.strings'):
        base_ios = ios(ios_root / 'en.lproj' / name)
        for locale in ('en', 'zh-Hans', 'zh-Hant', 'ja', 'ko'):
            path = ios_root / f'{locale}.lproj' / name
            check(base_ios, ios(path), path)
            assert f'{locale}.lproj/{name}' in project, f'Missing Xcode resource: {path}'
    config = ET.parse(res / 'xml/locales_config.xml').getroot()
    assert {node.attrib['{http://schemas.android.com/apk/res/android}name'] for node in config} == {'en', 'zh-Hans', 'zh-Hant', 'ja', 'ko'}
    print(f'Five-language resource coverage and placeholders OK ({len(base)} Android keys).')


if __name__ == '__main__':
    main()
