from dataclasses import dataclass, field
from typing import Dict, Iterable, List, Optional, Set

import datahub.emitter.mce_builder as builder
import datahub.metadata.schema_classes as models
from datahub.api.urn import DataFlowUrn
from datahub.emitter.mcp import MetadataChangeProposalWrapper
from datahub.emitter.rest_emitter import DatahubRestEmitter


@dataclass
class DataFlow:
    urn: DataFlowUrn = field(init=False)
    id: str
    orchestrator: str
    cluster: str
    name: Optional[str] = None
    description: Optional[str] = None
    properties: Dict[str, str] = field(default_factory=dict)
    url: Optional[str] = None
    tags: Set[str] = field(default_factory=set)
    owners: Set[str] = field(default_factory=set)

    def __post_init__(self):
        self.urn = DataFlowUrn(
            orchestrator=self.orchestrator, cluster=self.cluster, id=self.id
        )

    def generate_ownership_aspect(self):
        ownership = models.OwnershipClass(
            owners=[
                models.OwnerClass(
                    owner=builder.make_user_urn(owner),
                    type=models.OwnershipTypeClass.DEVELOPER,
                    source=models.OwnershipSourceClass(
                        type=models.OwnershipSourceTypeClass.SERVICE,
                        # url=dag.filepath,
                    ),
                )
                for owner in (self.owners or [])
            ],
            lastModified=models.AuditStampClass(
                time=0, actor=builder.make_user_urn(self.orchestrator)
            ),
        )
        return [ownership]

    def generate_tags_aspect(self) -> List[models.GlobalTagsClass]:
        tags = models.GlobalTagsClass(
            tags=[
                models.TagAssociationClass(tag=builder.make_tag_urn(tag))
                for tag in (self.tags or [])
            ]
        )
        return [tags]

    def generate_mce(self) -> models.MetadataChangeEventClass:
        flow_mce = models.MetadataChangeEventClass(
            proposedSnapshot=models.DataFlowSnapshotClass(
                urn=self.urn.urn,
                aspects=[
                    models.DataFlowInfoClass(
                        name=self.id,
                        description=self.description,
                        customProperties=self.properties,
                        externalUrl=self.url,
                    ),
                    *self.generate_ownership_aspect(),
                    *self.generate_tags_aspect(),
                ],
            )
        )

        return flow_mce

    def generate_mcp(self) -> Iterable[MetadataChangeProposalWrapper]:
        mcp = MetadataChangeProposalWrapper(
            entityType="dataflow",
            entityUrn=self.urn.urn,
            aspectName="dataFlowInfo",
            aspect=models.DataFlowInfoClass(
                name=self.name if self.name is not None else self.id,
                description=self.description,
                customProperties=self.properties,
                externalUrl=self.url,
            ),
            changeType=models.ChangeTypeClass.UPSERT,
        )
        yield mcp

        for owner in self.generate_ownership_aspect():
            mcp = MetadataChangeProposalWrapper(
                entityType="dataflow",
                entityUrn=self.urn.urn,
                aspectName="ownership",
                aspect=owner,
                changeType=models.ChangeTypeClass.UPSERT,
            )
            yield mcp

        for tag in self.generate_tags_aspect():
            mcp = MetadataChangeProposalWrapper(
                entityType="dataflow",
                entityUrn=self.urn.urn,
                aspectName="globalTags",
                aspect=tag,
                changeType=models.ChangeTypeClass.UPSERT,
            )
            yield mcp

    def emit(self, emitter: DatahubRestEmitter) -> None:
        for mcp in self.generate_mcp():
            emitter.emit(mcp)
