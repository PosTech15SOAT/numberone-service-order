"""Check that the runtime image carries the same active Saga JSON files as source."""
from pathlib import Path
from sys import argv
from zipfile import ZipFile

root = Path("doc/contracts/saga")
files = [root / "packages.json", *sorted((root / "v2").rglob("*.json"))]
if not (root / "v2/catalog.json").is_file():
    raise SystemExit("Catálogo ativo da Saga ausente no checkout.")

with ZipFile(argv[1]) as jar:
    for file in files:
        name = "BOOT-INF/classes/contracts/saga/" + file.relative_to(root).as_posix()
        if jar.read(name) != file.read_bytes():
            raise SystemExit(f"Contrato da Saga diferente no JAR: {name}")
print(f"{len(files)} arquivos da Saga conferidos no JAR da imagem.")
