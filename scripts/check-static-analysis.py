"""Reject new SpotBugs findings while keeping every reviewed warning visible."""
import csv
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parent.parent
report = root / 'backend/target/spotbugsXml.xml'
register = root / 'docs/technical-quality/spotbugs-resolution-register.csv'
with register.open(encoding='utf-8', newline='') as stream:
    accepted = {row['fingerprint'] for row in csv.DictReader(stream)
                if row['finalDisposition'] == 'retained and reviewed'}
findings = ET.parse(report).getroot().findall('BugInstance')
new = [bug for bug in findings if bug.get('instanceHash') not in accepted or bug.get('priority') == '1']
for bug in new:
    print(bug.get('type'), bug.findtext('LongMessage'), file=sys.stderr)
print(f'SpotBugs: {len(findings)} visible findings; {len(new)} unreviewed/high-priority findings.')
sys.exit(bool(new))
