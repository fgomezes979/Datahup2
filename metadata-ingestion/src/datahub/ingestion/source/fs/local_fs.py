import os
import pathlib
from typing import Any, Iterable

import smart_open

from datahub.ingestion.source.fs.fs_base import FileInfo, FileSystem


class LocalFileSystem(FileSystem):
    @classmethod
    def create(cls, **kwargs):
        return LocalFileSystem()

    def open(self, path: str, **kwargs: Any) -> Any:
        return smart_open.open(path, mode="rb", transport_params=kwargs)

    def list(self, path: str) -> Iterable[FileInfo]:
        p = pathlib.Path(path)
        if p.is_file():
            return [self.file_status(path)]
        elif p.is_dir():
            return iter([self.file_status(str(x)) for x in p.iterdir()])
        else:
            raise Exception(f"Failed to process {path}")

    def file_status(self, path: str) -> FileInfo:
        if os.path.isfile(path):
            return FileInfo(path, os.path.getsize(path), is_file=True)
        else:
            return FileInfo(path, 0, is_file=False)
