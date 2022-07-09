import datetime
import itertools
import logging
import uuid
from typing import Any, Dict, Iterable, List, Optional

import click
from pydantic import root_validator, validator

from datahub.cli.cli_utils import get_url_and_token
from datahub.configuration import config_loader
from datahub.configuration.common import (
    ConfigModel,
    DynamicTypedConfig,
    PipelineExecutionError,
)
from datahub.ingestion.api.committable import CommitPolicy
from datahub.ingestion.api.common import EndOfStream, PipelineContext, RecordEnvelope
from datahub.ingestion.api.sink import Sink, WriteCallback
from datahub.ingestion.api.source import Extractor, Source
from datahub.ingestion.api.transform import Transformer
from datahub.ingestion.extractor.extractor_registry import extractor_registry
from datahub.ingestion.graph.client import DatahubClientConfig
from datahub.ingestion.reporting.reporting_provider_registry import (
    reporting_provider_registry,
)
from datahub.ingestion.sink.sink_registry import sink_registry
from datahub.ingestion.source.source_registry import source_registry
from datahub.ingestion.transformer.transform_registry import transform_registry
from datahub.telemetry import stats, telemetry

logger = logging.getLogger(__name__)


class SourceConfig(DynamicTypedConfig):
    extractor: str = "generic"


class PipelineConfig(ConfigModel):
    # Once support for discriminated unions gets merged into Pydantic, we can
    # simplify this configuration and validation.
    # See https://github.com/samuelcolvin/pydantic/pull/2336.

    source: SourceConfig
    sink: DynamicTypedConfig
    transformers: Optional[List[DynamicTypedConfig]]
    reporting: Optional[List[DynamicTypedConfig]] = None
    run_id: str = "__DEFAULT_RUN_ID"
    datahub_api: Optional[DatahubClientConfig] = None
    pipeline_name: Optional[str] = None

    @validator("run_id", pre=True, always=True)
    def run_id_should_be_semantic(
        cls, v: Optional[str], values: Dict[str, Any], **kwargs: Any
    ) -> str:
        if v == "__DEFAULT_RUN_ID":
            if "source" in values and hasattr(values["source"], "type"):
                source_type = values["source"].type
                current_time = datetime.datetime.now().strftime("%Y_%m_%d-%H_%M_%S")
                return f"{source_type}-{current_time}"

            return str(uuid.uuid1())  # default run_id if we cannot infer a source type
        else:
            assert v is not None
            return v

    @root_validator(pre=True)
    def default_sink_is_datahub_rest(cls, values: Dict[str, Any]) -> Any:
        if "sink" not in values:
            gms_host, gms_token = get_url_and_token()
            default_sink_config = {
                "type": "datahub-rest",
                "config": {
                    "server": gms_host,
                    "token": gms_token,
                },
            }
            # resolve env variables if present
            default_sink_config = config_loader.resolve_env_variables(
                default_sink_config
            )
            values["sink"] = default_sink_config

        return values

    @validator("datahub_api", always=True)
    def datahub_api_should_use_rest_sink_as_default(
        cls, v: Optional[DatahubClientConfig], values: Dict[str, Any], **kwargs: Any
    ) -> Optional[DatahubClientConfig]:
        if v is None and "sink" in values and hasattr(values["sink"], "type"):
            sink_type = values["sink"].type
            if sink_type == "datahub-rest":
                sink_config = values["sink"].config
                v = DatahubClientConfig.parse_obj(sink_config)
        return v


class LoggingCallback(WriteCallback):
    def on_success(
        self, record_envelope: RecordEnvelope, success_metadata: dict
    ) -> None:
        logger.info(f"sink wrote workunit {record_envelope.metadata['workunit_id']}")

    def on_failure(
        self,
        record_envelope: RecordEnvelope,
        failure_exception: Exception,
        failure_metadata: dict,
    ) -> None:
        logger.error(
            f"failed to write record with workunit {record_envelope.metadata['workunit_id']}"
            f" with {failure_exception} and info {failure_metadata}"
        )


class Pipeline:
    config: PipelineConfig
    ctx: PipelineContext
    source: Source
    sink: Sink
    transformers: List[Transformer]

    def __init__(
        self,
        config: PipelineConfig,
        dry_run: bool = False,
        preview_mode: bool = False,
        preview_workunits: int = 10,
    ):
        self.config = config
        self.dry_run = dry_run
        self.preview_mode = preview_mode
        self.preview_workunits = preview_workunits
        self.ctx = PipelineContext(
            run_id=self.config.run_id,
            datahub_api=self.config.datahub_api,
            pipeline_name=self.config.pipeline_name,
            dry_run=dry_run,
            preview_mode=preview_mode,
        )
        self.pipeline_init_failures = None

        sink_type = self.config.sink.type
        try:
            sink_class = sink_registry.get(sink_type)
        except Exception as e:
            self.pipeline_init_failures = f"Failed to create sink due to \n\t{e}"
            logger.error(e)
            return

        try:
            sink_config = self.config.sink.dict().get("config") or {}
            self.sink: Sink = sink_class.create(sink_config, self.ctx)
            logger.debug(f"Sink type:{self.config.sink.type},{sink_class} configured")
            logger.info(f"Sink configured successfully. {self.sink}")
        except Exception as e:
            self.pipeline_init_failures = f"Failed to configure sink due to \n\t{e}"
            logger.error(e)
            return

        try:
            source_type = self.config.source.type
            source_class = source_registry.get(source_type)
        except Exception as e:
            self.pipeline_init_failures = f"Failed to create source due to \n\t{e}"
            logger.error(e)
            return

        try:
            self.source: Source = source_class.create(
                self.config.source.dict().get("config", {}), self.ctx
            )
            logger.debug(f"Source type:{source_type},{source_class} configured")
        except Exception as e:
            self.pipeline_init_failures = (
                f"Failed to configure source ({source_type}) due to \n\t{e}"
            )
            logger.error(e)
            return

        try:
            self.extractor_class = extractor_registry.get(self.config.source.extractor)
        except Exception as e:
            self.pipeline_init_failures = f"Failed to configure extractor ({self.config.source.extractor}) due to \n\t{e}"
            logger.error(e)
            return

        try:
            self._configure_transforms()
        except ValueError as e:
            self.pipeline_init_failures = (
                f"Failed to configure transformers due to \n\t{e}"
            )
            logger.error(e)
            return

        self._configure_reporting()

    def _configure_transforms(self) -> None:
        self.transformers = []
        if self.config.transformers is not None:
            for transformer in self.config.transformers:
                transformer_type = transformer.type
                transformer_class = transform_registry.get(transformer_type)
                transformer_config = transformer.dict().get("config", {})
                self.transformers.append(
                    transformer_class.create(transformer_config, self.ctx)
                )
                logger.debug(
                    f"Transformer type:{transformer_type},{transformer_class} configured"
                )

    def _configure_reporting(self) -> None:
        if self.config.reporting is None:
            return

        for reporter in self.config.reporting:
            reporter_type = reporter.type
            reporter_class = reporting_provider_registry.get(reporter_type)
            reporter_config_dict = reporter.dict().get("config", {})
            self.ctx.register_reporter(
                reporter_class.create(
                    config_dict=reporter_config_dict,
                    ctx=self.ctx,
                    name=reporter_class.__name__,
                )
            )
            logger.debug(
                f"Transformer type:{reporter_type},{reporter_class} configured"
            )

    @classmethod
    def create(
        cls,
        config_dict: dict,
        dry_run: bool = False,
        preview_mode: bool = False,
        preview_workunits: int = 10,
    ) -> "Pipeline":
        config = PipelineConfig.parse_obj(config_dict)
        return cls(
            config,
            dry_run=dry_run,
            preview_mode=preview_mode,
            preview_workunits=preview_workunits,
        )

    def run(self) -> None:

        callback = LoggingCallback()
        if self.pipeline_init_failures:
            # no point continuing, return early
            return

        extractor: Extractor = self.extractor_class()
        for wu in itertools.islice(
            self.source.get_workunits(),
            self.preview_workunits if self.preview_mode else None,
        ):
            # TODO: change extractor interface
            extractor.configure({}, self.ctx)

            if not self.dry_run:
                self.sink.handle_work_unit_start(wu)
            try:
                record_envelopes = extractor.get_records(wu)
                for record_envelope in self.transform(record_envelopes):
                    if not self.dry_run:
                        self.sink.write_record_async(record_envelope, callback)
            except Exception as e:
                logger.error(f"Failed to extract some records due to: {e}")
            extractor.close()
            if not self.dry_run:
                self.sink.handle_work_unit_end(wu)
        self.source.close()
        # no more data is coming, we need to let the transformers produce any additional records if they are holding on to state
        for record_envelope in self.transform(
            [
                RecordEnvelope(
                    record=EndOfStream(), metadata={"workunit_id": "end-of-stream"}
                )
            ]
        ):
            if not self.dry_run and not isinstance(record_envelope.record, EndOfStream):
                # TODO: propagate EndOfStream and other control events to sinks, to allow them to flush etc.
                self.sink.write_record_async(record_envelope, callback)

        self.sink.close()
        self.process_commits()

    def transform(self, records: Iterable[RecordEnvelope]) -> Iterable[RecordEnvelope]:
        """
        Transforms the given sequence of records by passing the records through the transformers
        :param records: the records to transform
        :return: the transformed records
        """
        for transformer in self.transformers:
            records = transformer.transform(records)

        return records

    def process_commits(self) -> None:
        """
        Evaluates the commit_policy for each committable in the context and triggers the commit operation
        on the committable if its required commit policies are satisfied.
        """
        has_errors: bool = (
            True
            if self.source.get_report().failures or self.sink.get_report().failures
            else False
        )
        has_warnings: bool = bool(
            self.source.get_report().warnings or self.sink.get_report().warnings
        )

        for name, committable in self.ctx.get_committables():
            commit_policy: CommitPolicy = committable.commit_policy

            logger.info(
                f"Processing commit request for {name}. Commit policy = {commit_policy},"
                f" has_errors={has_errors}, has_warnings={has_warnings}"
            )

            if (
                commit_policy == CommitPolicy.ON_NO_ERRORS_AND_NO_WARNINGS
                and (has_errors or has_warnings)
            ) or (commit_policy == CommitPolicy.ON_NO_ERRORS and has_errors):
                logger.warning(
                    f"Skipping commit request for {name} since policy requirements are not met."
                )
                continue

            try:
                committable.commit()
            except Exception as e:
                logger.error(f"Failed to commit changes for {name}.", e)
                raise e
            else:
                logger.info(f"Successfully committed changes for {name}.")

    def raise_from_status(self, raise_warnings: bool = False) -> None:
        if self.source.get_report().failures:
            raise PipelineExecutionError(
                "Source reported errors", self.source.get_report()
            )
        if self.sink.get_report().failures:
            raise PipelineExecutionError("Sink reported errors", self.sink.get_report())
        if raise_warnings and (
            self.source.get_report().warnings or self.sink.get_report().warnings
        ):
            raise PipelineExecutionError(
                "Source reported warnings", self.source.get_report()
            )

    def log_ingestion_stats(self) -> None:
        if not self.pipeline_init_failures:
            telemetry.telemetry_instance.ping(
                "ingest_stats",
                {
                    "source_type": self.config.source.type,
                    "sink_type": self.config.sink.type,
                    "records_written": stats.discretize(
                        self.sink.get_report().records_written
                    ),
                },
                self.ctx.graph,
            )

    def _count_all_vals(self, d: Dict[str, List]) -> int:
        result = 0
        for val in d.values():
            result += len(val)
        return result

    def pretty_print_summary(self, warnings_as_failure: bool = False) -> int:
        click.echo()
        if self.pipeline_init_failures:
            click.secho(f"{self.pipeline_init_failures}", fg="red")
            return 1
        click.secho(f"Source ({self.config.source.type}) report:", bold=True)
        click.echo(self.source.get_report().as_string())
        click.secho(f"Sink ({self.config.sink.type}) report:", bold=True)
        click.echo(self.sink.get_report().as_string())
        click.echo()
        workunits_produced = self.source.get_report().workunits_produced
        if self.source.get_report().failures or self.sink.get_report().failures:
            num_failures_source = self._count_all_vals(
                self.source.get_report().failures
            )
            click.secho(
                f"Pipeline finished with {num_failures_source} failures in source producing {workunits_produced} workunits",
                fg="bright_red",
                bold=True,
            )
            return 1
        elif self.source.get_report().warnings or self.sink.get_report().warnings:
            num_warn_source = self._count_all_vals(self.source.get_report().warnings)
            click.secho(
                f"Pipeline finished with {num_warn_source} warnings in source producing {workunits_produced} workunits",
                fg="yellow",
                bold=True,
            )
            return 1 if warnings_as_failure else 0
        else:
            click.secho(
                f"Pipeline finished successfully producing {workunits_produced} workunits",
                fg="green",
                bold=True,
            )
            return 0
