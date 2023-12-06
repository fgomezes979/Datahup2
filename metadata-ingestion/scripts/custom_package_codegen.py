import re
import subprocess
import sys
from pathlib import Path

import avro_codegen
import click

if sys.version_info < (3, 10):
    from importlib_metadata import version
else:
    from importlib.metadata import version

_avrogen_version = version("avro-gen3")

autogen_header = """# Autogenerated by datahub's custom_package_codegen.py
# DO NOT EDIT THIS FILE DIRECTLY
"""


def python_package_name_normalize(name):
    return re.sub(r"[-_.]+", "_", name).lower()


@click.command()
@click.argument(
    "entity_registry", type=click.Path(exists=True, dir_okay=False), required=True
)
@click.argument(
    "pdl_path", type=click.Path(exists=True, file_okay=False), required=True
)
@click.argument(
    "schemas_path", type=click.Path(exists=True, file_okay=False), required=True
)
@click.argument("outdir", type=click.Path(), required=True)
@click.argument("package_name", type=str, required=True)
@click.argument("package_version", type=str, required=True)
@click.pass_context
def generate(
    ctx: click.Context,
    entity_registry: str,
    pdl_path: str,
    schemas_path: str,
    outdir: str,
    package_name: str,
    package_version: str,
) -> None:
    package_path = Path(outdir) / package_name
    if package_path.is_absolute():
        raise click.UsageError("outdir must be a relative path")

    python_package_name = python_package_name_normalize(package_name)
    click.echo(
        f"Generating distribution {package_name} (package name {python_package_name}) at {package_path}"
    )

    src_path = package_path / "src" / python_package_name
    src_path.mkdir(parents=True)

    ctx.invoke(
        avro_codegen.generate,
        entity_registry=entity_registry,
        pdl_path=pdl_path,
        schemas_path=schemas_path,
        outdir=str(src_path / "models"),
        enable_custom_loader=False,
    )

    (src_path / "__init__.py").write_text(
        f"""{autogen_header}
__package_name__ = "{package_name}"
__version__ = "{package_version}"
"""
    )

    (package_path / "setup.py").write_text(
        f"""{autogen_header}
from setuptools import setup

_package_name = "{package_name}"
_package_version = "{package_version}"

setup(
    name=_package_name,
    version=_package_version,
    install_requires=[
        "avro-gen3=={_avrogen_version}",
        "acryl-datahub",
    ],
    entry_points={{
        "datahub.custom_packages": [
            "models={python_package_name}.models.schema_classes",
            "urns={python_package_name}.models._urns.urn_defs",
        ],
    }},
)
"""
    )

    # TODO add a README.md?
    click.echo("Building package...")
    subprocess.run(["python", "-m", "build", str(package_path)])

    click.echo()
    click.secho(f"Generated package at {package_path}", fg="green")
    click.echo(
        "This package should be installed alongside the main acryl-datahub package."
    )
    click.echo()
    click.echo(f"Install the custom package locally with `pip install {package_path}`")
    click.echo(
        f"To enable others to use it, share the file at {package_path}/dist/*.whl and have them install it with `pip install <wheel file>.whl`"
    )
    click.echo(
        f"Alternatively, publish it to PyPI with `twine upload {package_path}/dist/*`"
    )


if __name__ == "__main__":
    generate()
