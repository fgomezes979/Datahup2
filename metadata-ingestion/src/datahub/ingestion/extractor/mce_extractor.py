from typing import Iterable, Union

from datahub.ingestion.api import RecordEnvelope
from datahub.ingestion.api.common import PipelineContext
from datahub.ingestion.api.source import Extractor, WorkUnit
from datahub.ingestion.api.workunit import MetadataWorkUnit, UsageStatsWorkUnit
from datahub.metadata.com.linkedin.pegasus2avro.mxe import MetadataChangeEvent, SystemMetadata
from datahub.metadata.com.linkedin.pegasus2avro.common import Status
from datahub.metadata.schema_classes import UsageAggregationClass
import time as t

def current_milli_time():
    return round(t.time() * 1000)


class WorkUnitRecordExtractor(Extractor):
    """An extractor that simply returns the data inside workunits back as records."""

    ctx: PipelineContext

    def configure(self, config_dict: dict, ctx: PipelineContext) -> None:
        self.ctx = ctx

    def get_records(
        self, workunit: WorkUnit
    ) -> Iterable[RecordEnvelope[Union[MetadataChangeEvent, UsageAggregationClass]]]:
        if isinstance(workunit, MetadataWorkUnit):

            system_metadata = SystemMetadata(
                lastObserved=current_milli_time(),
                runId=self.ctx.run_id
            )

            workunit.mce.systemMetadata = system_metadata
            if len(workunit.mce.proposedSnapshot.aspects) == 0:
                raise AttributeError("every mce must have at least one aspect")
            if not workunit.mce.validate():
                raise ValueError(f"source produced an invalid MCE: {workunit.mce}")

            yield RecordEnvelope(
                workunit.mce,
                {
                    "workunit_id": workunit.id,
                },
            )
        elif isinstance(workunit, UsageStatsWorkUnit):
            if not workunit.usageStats.validate():
                raise ValueError(
                    f"source produced an invalid usage stat: {workunit.usageStats}"
                )
            yield RecordEnvelope(
                workunit.usageStats,
                {
                    "workunit_id": workunit.id,
                },
            )
        else:
            raise ValueError(f"unknown WorkUnit type {type(workunit)}")

    def close(self):
        pass
