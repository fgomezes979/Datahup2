import dataclasses
import json
from typing import Union

from datahub.emitter.serialization_helper import pre_json_transform
from datahub.metadata.schema_classes import (
    ChangeTypeClass,
    DictWrapper,
    GenericAspectClass,
    KafkaAuditHeaderClass,
    MetadataChangeProposalClass,
    SystemMetadataClass,
)


def _make_generic_aspect(codegen_obj: DictWrapper) -> GenericAspectClass:
    serialized = json.dumps(pre_json_transform(codegen_obj.to_obj()))
    return GenericAspectClass(
        value=serialized.encode(),
        contentType="application/json",
    )


@dataclasses.dataclass
class MetadataChangeProposalWrapper:
    entityType: str
    entityKey: Union[str, DictWrapper]
    changeType: Union[str, ChangeTypeClass]
    auditHeader: Union[None, KafkaAuditHeaderClass] = None
    aspectName: Union[None, str] = None
    aspect: Union[None, DictWrapper] = None
    systemMetadata: Union[None, SystemMetadataClass] = None

    def make_mcp(self) -> MetadataChangeProposalClass:
        serializedEntityKey: Union[str, GenericAspectClass]
        if isinstance(self.entityKey, DictWrapper):
            serializedEntityKey = _make_generic_aspect(self.entityKey)
        else:
            serializedEntityKey = self.entityKey

        serializedAspect = None
        if self.aspect is not None:
            serializedAspect = _make_generic_aspect(self.aspect)

        return MetadataChangeProposalClass(
            entityType=self.entityType,
            entityKey=serializedEntityKey,
            changeType=self.changeType,
            auditHeader=self.auditHeader,
            aspectName=self.aspectName,
            aspect=serializedAspect,
            systemMetadata=self.systemMetadata,
        )
