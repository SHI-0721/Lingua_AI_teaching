"""Create a source archive without credentials, caches, SDKs, or signing keys."""
import argparse
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

def package(root, output):
    excluded_dirs={'.tools','.gradle','build','.git','.idea','__pycache__'}
    excluded_names={'local.properties','supabase.local.properties','.env'}
    files=[p for p in root.rglob('*') if p.is_file()
        and not any(part in excluded_dirs for part in p.relative_to(root).parts)
        and p.name not in excluded_names and not p.name.startswith('.env.')
        and p.suffix.lower() not in {'.jks','.keystore','.apk'}]
    with ZipFile(output,'w',ZIP_DEFLATED) as archive:
        for file in files: archive.write(file,'LanguageLearning/'+file.relative_to(root).as_posix())
    with ZipFile(output) as archive:
        assert not any(Path(name).name in excluded_names for name in archive.namelist())
        assert archive.testzip() is None
    print(f'Packaged {len(files)} project files, excluding private configuration and keys.')

if __name__=='__main__':
    parser=argparse.ArgumentParser(); parser.add_argument('output')
    args=parser.parse_args(); package(Path(__file__).resolve().parent,Path(args.output))
