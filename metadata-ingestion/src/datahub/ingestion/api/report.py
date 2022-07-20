import json
import pprint
import sys
from dataclasses import dataclass
from typing import Any, Dict

# The sort_dicts option was added in Python 3.8.
if sys.version_info >= (3, 8):
    PPRINT_OPTIONS = {"sort_dicts": False}
else:
    PPRINT_OPTIONS: Dict = {}


@dataclass
class Report:
    @staticmethod
    def to_dict(some_val: Any) -> Any:
        if hasattr(some_val, "as_obj"):
            return some_val.as_obj()
        if hasattr(some_val, "dict"):
            return some_val.dict()
        elif isinstance(some_val, list):
            return [Report.to_dict(v) for v in some_val if v is not None]
        elif isinstance(some_val, dict):
            return {k: Report.to_dict(v) for k, v in some_val.items() if v is not None}
        else:
            return str(some_val)

    def as_obj(self) -> dict:
        return {
            key: Report.to_dict(value)
            for (key, value) in self.__dict__.items()
            if value  # ignore nulls
        }

    def as_string(self) -> str:
        return pprint.pformat(self.as_obj(), width=150, **PPRINT_OPTIONS)

    def as_json(self) -> str:
        return json.dumps(self.as_obj())
