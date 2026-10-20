#!/usr/bin/env python3
"""Sign with an existing Windows certificate; never create a self-signed publisher."""
import argparse, os, pathlib, re, shutil, subprocess

def find_signtool():
    found=shutil.which('signtool')
    if found:return found
    kits=pathlib.Path(os.environ.get('ProgramFiles(x86)',r'C:\Program Files (x86)'))/'Windows Kits/10/bin'
    candidates=sorted(kits.glob('*/x64/signtool.exe'),reverse=True)
    if not candidates:raise SystemExit('Windows SDK SignTool is required for code signing.')
    return str(candidates[0])

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('file',type=pathlib.Path);p.add_argument('--thumbprint',required=True,help='SHA1 certificate thumbprint in CurrentUser/My (not a private key)');args=p.parse_args()
    thumbprint=args.thumbprint.replace(' ','')
    if not re.fullmatch(r'[0-9a-fA-F]{40}',thumbprint):p.error('Certificate thumbprint must contain 40 hexadecimal characters.')
    if os.name!='nt':p.error('Signing requires Windows with an installed code-signing certificate.')
    if not args.file.is_file():p.error('Executable not found.')
    tool=find_signtool()
    subprocess.run([tool,'sign','/sha1',thumbprint,'/fd','SHA256','/tr','http://timestamp.digicert.com','/td','SHA256',str(args.file)],check=True)
    # /pa requires a trusted Authenticode chain; failure prevents ZIP publication.
    subprocess.run([tool,'verify','/pa','/all','/v',str(args.file)],check=True)

if __name__=='__main__':main()
