import itertools
import logging
from typing import Any, Dict, Iterable, List, Optional

import click

from datahub.configuration.common import PipelineExecutionError
from datahub.ingestion.api.committable import CommitPolicy
from datahub.ingestion.api.common import EndOfStream, PipelineContext, RecordEnvelope
from datahub.ingestion.api.pipeline_run_listener import PipelineRunListener
from datahub.ingestion.api.sink import Sink, WriteCallback
from datahub.ingestion.api.source import Extractor, Source
from datahub.ingestion.api.transform import Transformer
from datahub.ingestion.extractor.extractor_registry import extractor_registry
from datahub.ingestion.reporting.reporting_provider_registry import (
    reporting_provider_registry,
)
from datahub.ingestion.run.pipeline_config import PipelineConfig, ReporterConfig
from datahub.ingestion.sink.sink_registry import sink_registry
from datahub.ingestion.source.source_registry import source_registry
from datahub.ingestion.transformer.transform_registry import transform_registry
from datahub.telemetry import stats, telemetry

logger = logging.getLogger(__name__)


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


class PipelineInitError(Exception):
    pass


class Pipeline:
    config: PipelineConfig
    ctx: PipelineContext
    source: Source
    sink: Sink
    transformers: List[Transformer]

    def _record_initialization_failure(self, e: Exception, msg: str) -> None:
        raise PipelineInitError(msg) from e

    def __init__(
        self,
        config: PipelineConfig,
        dry_run: bool = False,
        preview_mode: bool = False,
        preview_workunits: int = 10,
        report_to: Optional[str] = None,
    ):
        self.config = config
        self.dry_run = dry_run
        self.preview_mode = preview_mode
        self.preview_workunits = preview_workunits
        self.report_to = report_to
        self.reporters: List[PipelineRunListener] = []

        try:
            self.ctx = PipelineContext(
                run_id=self.config.run_id,
                datahub_api=self.config.datahub_api,
                pipeline_name=self.config.pipeline_name,
                dry_run=dry_run,
                preview_mode=preview_mode,
                pipeline_config=self.config,
            )
        except Exception as e:
            self._record_initialization_failure(e, "Failed to set up framework context")

        sink_type = self.config.sink.type
        try:
            sink_class = sink_registry.get(sink_type)
        except Exception as e:
            self._record_initialization_failure(
                e, f"Failed to find a registered sink for type {sink_type}"
            )
            return

        try:
            sink_config = self.config.sink.dict().get("config") or {}
            self.sink: Sink = sink_class.create(sink_config, self.ctx)
            logger.debug(f"Sink type:{self.config.sink.type},{sink_class} configured")
            logger.info(f"Sink configured successfully. {self.sink.configured()}")
        except Exception as e:
            self._record_initialization_failure(
                e, f"Failed to configure sink ({sink_type})"
            )

        # once a sink is configured, we can configure reporting immediately to get observability
        try:
            self._configure_reporting(report_to)
        except Exception as e:
            self._record_initialization_failure(e, "Failed to configure reporters")
            return

        try:
            source_type = self.config.source.type
            source_class = source_registry.get(source_type)
        except Exception as e:
            self._record_initialization_failure(e, "Failed to create source")
            return

        try:
            self.source: Source = source_class.create(
                self.config.source.dict().get("config", {}), self.ctx
            )
            logger.debug(f"Source type:{source_type},{source_class} configured")
        except Exception as e:
            self._record_initialization_failure(
                e, f"Failed to configure source ({source_type})"
            )
            return

        try:
            self.extractor_class = extractor_registry.get(self.config.source.extractor)
        except Exception as e:
            self._record_initialization_failure(
                e, f"Failed to configure extractor ({self.config.source.extractor})"
            )
            return

        try:
            self._configure_transforms()
        except ValueError as e:
            self._record_initialization_failure(e, "Failed to configure transformers")
            return

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

    def _configure_reporting(self, report_to: Optional[str]) -> None:
        if report_to == "datahub":
            # we add the default datahub reporter unless a datahub reporter is already configured
            if not self.config.reporting or "datahub" not in [
                x.type for x in self.config.reporting
            ]:
                self.config.reporting.append(
                    ReporterConfig.parse_obj({"type": "datahub"})
                )
        elif report_to:
            # we assume this is a file name, and add the file reporter
            self.config.reporting.append(
                ReporterConfig.parse_obj(
                    {"type": "file", "config": {"filename": report_to}}
                )
            )

        for reporter in self.config.reporting:
            reporter_type = reporter.type
            reporter_class = reporting_provider_registry.get(reporter_type)
            reporter_config_dict = reporter.dict().get("config", {})
            try:
                self.reporters.append(
                    reporter_class.create(
                        config_dict=reporter_config_dict,
                        ctx=self.ctx,
                    )
                )
                logger.debug(
                    f"Reporter type:{reporter_type},{reporter_class} configured."
                )
            except Exception as e:
                if reporter.required:
                    raise
                else:
                    logger.warning(
                        f"Failed to configure reporter: {reporter_type}", exc_info=e
                    )

    def _notify_reporters_on_ingestion_start(self) -> None:
        for reporter in self.reporters:
            try:
                reporter.on_start(ctx=self.ctx)
            except Exception as e:
                logger.warning("Reporting failed on start", exc_info=e)

    def _notify_reporters_on_ingestion_completion(self) -> None:
        for reporter in self.reporters:
            try:
                reporter.on_completion(
                    status="FAILED"
                    if self.source.get_report().failures
                    or self.sink.get_report().failures
                    else "SUCCESS",
                    report=self._get_structured_report(),
                    ctx=self.ctx,
                )
            except Exception as e:
                logger.warning("Reporting failed on completion", exc_info=e)

    @classmethod
    def create(
        cls,
        config_dict: dict,
        dry_run: bool = False,
        preview_mode: bool = False,
        preview_workunits: int = 10,
        report_to: Optional[str] = None,
        raw_config: Optional[dict] = None,
    ) -> "Pipeline":
        config = PipelineConfig.from_dict(config_dict, raw_config)
        return cls(
            config,
            dry_run=dry_run,
            preview_mode=preview_mode,
            preview_workunits=preview_workunits,
            report_to=report_to,
        )

    def run(self) -> None:

        self._notify_reporters_on_ingestion_start()
        try:
            callback = LoggingCallback()
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
                if not self.dry_run and not isinstance(
                    record_envelope.record, EndOfStream
                ):
                    # TODO: propagate EndOfStream and other control events to sinks, to allow them to flush etc.
                    self.sink.write_record_async(record_envelope, callback)

            self.sink.close()
            self.process_commits()
        finally:
            self._notify_reporters_on_ingestion_completion()

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

    def _get_structured_report(self) -> Dict[str, Any]:
        return {
            "source": {
                "type": self.config.source.type,
                "report": self.source.get_report().as_obj(),
            },
            "sink": {
                "type": self.config.sink.type,
                "report": self.sink.get_report().as_obj(),
            },
        }
