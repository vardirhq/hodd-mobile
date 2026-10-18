#!/usr/bin/env python3
"""Create/reuse a private key locally and upload only to GitHub Actions secrets."""
import base64
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys

for tool in ('gh', 'keytool'):
    if not shutil.which(tool):
        sys.exit(f'{tool} is required. Install GitHub CLI and JDK 17, then rerun.')
subprocess.run(['gh', 'auth', 'status'], check=True)
folder = Path(sys.argv[1]).expanduser().resolve() if len(sys.argv) > 1 else Path.home() / '.local/share/hodd-signing'
folder.mkdir(parents=True, exist_ok=True, mode=0o700)
key = folder / 'hodd-signing.jks'
credentials = folder / 'credentials.json'
if key.exists() != credentials.exists():
    sys.exit('Incomplete signing backup. Restore the matching keystore and credentials; do not generate a replacement.')
if credentials.exists():
    values = json.loads(credentials.read_text())
else:
    values = {'password': secrets.token_hex(32), 'alias': 'hodd'}
    descriptor = os.open(credentials, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, 'w') as output:
        json.dump(values, output)
    env = dict(os.environ, HODD_SETUP_PASSWORD=values['password'])
    subprocess.run(['keytool', '-genkeypair', '-noprompt', '-storetype', 'PKCS12', '-keystore', str(key),
                    '-storepass:env', 'HODD_SETUP_PASSWORD', '-keypass:env', 'HODD_SETUP_PASSWORD',
                    '-alias', values['alias'], '-keyalg', 'RSA', '-keysize', '4096', '-validity', '36500',
                    '-dname', 'CN=Hodd Mobile, O=Vardir'], env=env, check=True)
    key.chmod(0o600)
# A failure leaves the same local key available for a safe retry.
entries = {'HODD_KEYSTORE_BASE64': base64.b64encode(key.read_bytes()).decode(),
           'HODD_KEYSTORE_PASSWORD': values['password'], 'HODD_KEY_ALIAS': values['alias'],
           'HODD_KEY_PASSWORD': values['password']}
for name, value in entries.items():
    subprocess.run(['gh', 'secret', 'set', name, '--repo', 'vardirhq/hodd-mobile'], input=value, text=True, check=True)
print(f'Signing secrets saved. Keep a secure backup of {folder}; reuse this key for every update.')
