# Configuration file for the Sphinx documentation builder.
#
# For the full list of built-in configuration values, see the documentation:
# https://www.sphinx-doc.org/en/master/usage/configuration.html


# -- Project information -----------------------------------------------------
# https://www.sphinx-doc.org/en/master/usage/configuration.html#project-information

project = "DataHub Python SDK"
copyright = "2023, Acryl Data"
author = "Acryl Data"

# -- General configuration ---------------------------------------------------
# https://www.sphinx-doc.org/en/master/usage/configuration.html#general-configuration

extensions = [
    "sphinx.ext.autodoc",
    "sphinx.ext.intersphinx",
    # TODO: set up 'sphinx.ext.viewcode'
    # Via https://stackoverflow.com/a/51312475/5004662.
    "sphinx.ext.napoleon",
    "sphinx_autodoc_typehints",
]

intersphinx_mapping = {
    "python": ("https://docs.python.org/3", None),
    "requests": ("https://docs.python-requests.org/en/latest/", None),
    "urllib3": ("https://urllib3.readthedocs.io/en/latest/", None),
}

napoleon_use_param = True


# Move type hint info to function description instead of signature
# Via: https://chromium.googlesource.com/external/github.com/reclosedev/requests-cache/+/refs/heads/master/docs/conf.py#104.
autodoc_typehints = "description"
always_document_param_types = True

templates_path = ["_templates"]
exclude_patterns = ["_build", "Thumbs.db", ".DS_Store", "venv"]
source_suffix = [".rst"]

autodoc_default_options = {
    "members": True,
    "member-order": "bysource",
    "undoc-members": True,
    "show-inheritance": True,
}

# -- Options for HTML output -------------------------------------------------
# https://www.sphinx-doc.org/en/master/usage/configuration.html#options-for-html-output

# html_theme = "alabaster"
html_theme = "pydata_sphinx_theme"

html_static_path = ["_static"]

# TODO enable html_show_sourcelink
