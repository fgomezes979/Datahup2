import copy
import functools
import logging
import threading
from typing import TYPE_CHECKING, Callable, Dict, List, Optional, TypeVar, cast

import datahub.emitter.mce_builder as builder
from airflow.listeners import hookimpl
from datahub.api.entities.datajob import DataJob
from datahub.api.entities.dataprocess.dataprocess_instance import InstanceRunResult
from datahub.emitter.rest_emitter import DatahubRestEmitter
from datahub.ingestion.graph.client import DataHubGraph
from datahub.metadata.schema_classes import (
    FineGrainedLineageClass,
    FineGrainedLineageDownstreamTypeClass,
    FineGrainedLineageUpstreamTypeClass,
)
from datahub.utilities.sqlglot_lineage import SqlParsingResult
from datahub.utilities.urns.dataset_urn import DatasetUrn
from openlineage.airflow.listener import TaskHolder
from openlineage.airflow.utils import redact_with_exclusions
from openlineage.client.serde import Serde

from datahub_airflow_plugin._airflow_shims import (
    Operator,
    get_task_inlets,
    get_task_outlets,
)
from datahub_airflow_plugin._config import DatahubLineageConfig, get_lineage_config
from datahub_airflow_plugin._datahub_ol_adapter import translate_ol_to_datahub_urn
from datahub_airflow_plugin._extractors import SQL_PARSING_RESULT_KEY, ExtractorManager
from datahub_airflow_plugin.client.airflow_generator import AirflowGenerator
from datahub_airflow_plugin.entities import _Entity

if TYPE_CHECKING:
    from airflow.models import DAG, DagRun, TaskInstance
    from sqlalchemy.orm import Session


logger = logging.getLogger(__name__)
_F = TypeVar("_F", bound=Callable[..., None])

_airflow_listener_initialized = False
_airflow_listener: Optional["DataHubListener"] = None
_RUN_IN_THREAD = True

if TYPE_CHECKING:
    # On Airflow versions that don't have the listener API, we placate mypy
    # by making hookimpl an identity function.

    def hookimpl(f: _F) -> _F:  # type: ignore[misc] # noqa: F811
        return f


def get_airflow_plugin_listener() -> Optional["DataHubListener"]:
    # Using globals instead of functools.lru_cache to make testing easier.
    global _airflow_listener_initialized
    global _airflow_listener

    if not _airflow_listener_initialized:
        _airflow_listener_initialized = True

        plugin_config = get_lineage_config()

        if plugin_config.enabled:
            _airflow_listener = DataHubListener(config=plugin_config)

            if plugin_config.disable_openlineage_plugin:
                # Deactivate the OpenLineagePlugin listener to avoid conflicts.
                from openlineage.airflow.plugin import OpenLineagePlugin

                OpenLineagePlugin.listeners = []

    return _airflow_listener


def run_in_thread(f: _F) -> _F:
    @functools.wraps(f)
    def wrapper(*args, **kwargs):
        try:
            if _RUN_IN_THREAD:
                # A poor-man's timeout mechanism.
                # This ensures that we don't hang the task if the extractors
                # are slow or the DataHub API is slow to respond.

                thread = threading.Thread(
                    target=f, args=args, kwargs=kwargs, daemon=True
                )
                thread.start()

                thread.join(timeout=15)
            else:
                f(*args, **kwargs)
        except Exception as e:
            logger.exception(e)

    return cast(_F, wrapper)


class DataHubListener:
    __name__ = "DataHubListener"

    def __init__(self, config: DatahubLineageConfig):
        self.config = config
        self._set_log_level()

        self._emitter = config.make_emitter_hook().make_emitter()
        self._graph: Optional[DataHubGraph] = None
        logger.info(f"DataHub plugin using {repr(self._emitter)}")

        # See discussion here https://github.com/OpenLineage/OpenLineage/pull/508 for
        # why we need to keep track of tasks ourselves.
        self._task_holder = TaskHolder()

        # In our case, we also want to cache the initial datajob object
        # so that we can add to it when the task completes.
        self._datajob_holder: Dict[str, DataJob] = {}

        self.extractor_manager = ExtractorManager()

    @property
    def emitter(self):
        return self._emitter

    @property
    def graph(self) -> Optional[DataHubGraph]:
        if self._graph:
            return self._graph

        if isinstance(self._emitter, DatahubRestEmitter) and not isinstance(
            self._emitter, DataHubGraph
        ):
            # This is lazy initialized to avoid throwing errors on plugin load.
            self._graph = self._emitter.to_graph()
            self._emitter = self._graph

        return self._graph

    def _set_log_level(self) -> None:
        """Set the log level for the plugin and its dependencies.

        This may need to be called multiple times, since Airflow sometimes
        messes with the logging configuration after the plugin is loaded.
        In particular, the loggers may get changed when the worker starts
        executing a task.
        """

        if self.config.log_level:
            logging.getLogger(__name__.split(".")[0]).setLevel(self.config.log_level)
        if self.config.debug_emitter:
            logging.getLogger("datahub.emitter").setLevel(logging.DEBUG)

    def _make_emit_callback(self) -> Callable[[Optional[Exception], str], None]:
        def emit_callback(err: Optional[Exception], msg: str) -> None:
            if err:
                logger.error(f"Error sending metadata to datahub: {msg}", exc_info=err)

        return emit_callback

    def _extract_lineage(
        self,
        datajob: DataJob,
        dagrun: "DagRun",
        task: "Operator",
        task_instance: "TaskInstance",
        complete: bool = False,
    ) -> None:
        """
        Combine lineage (including column lineage) from task inlets/outlets and
        extractor-generated task_metadata and write it to the datajob. This
        routine is also responsible for converting the lineage to DataHub URNs.
        """

        input_urns: List[str] = []
        output_urns: List[str] = []
        fine_grained_lineages: List[FineGrainedLineageClass] = []

        task_metadata = None
        if self.config.enable_extractors:
            task_metadata = self.extractor_manager.extract_metadata(
                dagrun,
                task,
                complete=complete,
                task_instance=task_instance,
                task_uuid=str(datajob.urn),
                graph=self.graph,
            )
            logger.debug(f"Got task metadata: {task_metadata}")

            # Translate task_metadata.inputs/outputs to DataHub URNs.
            input_urns.extend(
                translate_ol_to_datahub_urn(dataset) for dataset in task_metadata.inputs
            )
            output_urns.extend(
                translate_ol_to_datahub_urn(dataset)
                for dataset in task_metadata.outputs
            )

        # Add DataHub-native SQL parser results.
        sql_parsing_result: Optional[SqlParsingResult] = None
        if task_metadata:
            sql_parsing_result = task_metadata.run_facets.pop(
                SQL_PARSING_RESULT_KEY, None
            )
        if sql_parsing_result:
            if sql_parsing_result.debug_info.error:
                datajob.properties["datahub_sql_parser_error"] = str(
                    sql_parsing_result.debug_info.error
                )
            else:
                input_urns.extend(sql_parsing_result.in_tables)
                output_urns.extend(sql_parsing_result.out_tables)

                if sql_parsing_result.column_lineage:
                    fine_grained_lineages.extend(
                        FineGrainedLineageClass(
                            upstreamType=FineGrainedLineageUpstreamTypeClass.FIELD_SET,
                            downstreamType=FineGrainedLineageDownstreamTypeClass.FIELD,
                            upstreams=[
                                builder.make_schema_field_urn(
                                    upstream.table, upstream.column
                                )
                                for upstream in column_lineage.upstreams
                            ],
                            downstreams=[
                                builder.make_schema_field_urn(
                                    downstream.table, downstream.column
                                )
                                for downstream in [column_lineage.downstream]
                                if downstream.table
                            ],
                        )
                        for column_lineage in sql_parsing_result.column_lineage
                    )

        # Add DataHub-native inlets/outlets.
        # These are filtered out by the extractor, so we need to add them manually.
        input_urns.extend(
            iolet.urn for iolet in get_task_inlets(task) if isinstance(iolet, _Entity)
        )
        output_urns.extend(
            iolet.urn for iolet in get_task_outlets(task) if isinstance(iolet, _Entity)
        )

        # Write the lineage to the datajob object.
        datajob.inlets.extend(DatasetUrn.create_from_string(urn) for urn in input_urns)
        datajob.outlets.extend(
            DatasetUrn.create_from_string(urn) for urn in output_urns
        )
        datajob.fine_grained_lineages.extend(fine_grained_lineages)

        # Merge in extra stuff that was present in the DataJob we constructed
        # at the start of the task.
        if complete:
            original_datajob = self._datajob_holder.get(str(datajob.urn), None)
        else:
            self._datajob_holder[str(datajob.urn)] = datajob
            original_datajob = None

        if original_datajob:
            logger.debug("Merging start datajob into finish datajob")
            datajob.inlets.extend(original_datajob.inlets)
            datajob.outlets.extend(original_datajob.outlets)
            datajob.fine_grained_lineages.extend(original_datajob.fine_grained_lineages)

            for k, v in original_datajob.properties.items():
                datajob.properties.setdefault(k, v)

        # TODO: Deduplicate inlets/outlets.

        # Write all other OL facets as DataHub properties.
        if task_metadata:
            for k, v in task_metadata.job_facets.items():
                datajob.properties[f"openlineage_job_facet_{k}"] = Serde.to_json(
                    redact_with_exclusions(v)
                )

            for k, v in task_metadata.run_facets.items():
                datajob.properties[f"openlineage_run_facet_{k}"] = Serde.to_json(
                    redact_with_exclusions(v)
                )

    @hookimpl
    @run_in_thread
    def on_task_instance_running(
        self,
        previous_state: None,
        task_instance: "TaskInstance",
        session: "Session",  # This will always be QUEUED
    ) -> None:
        self._set_log_level()

        # This if statement mirrors the logic in https://github.com/OpenLineage/OpenLineage/pull/508.
        if not hasattr(task_instance, "task"):
            logger.warning(
                f"No task set for task_id: {task_instance.task_id} - "
                f"dag_id: {task_instance.dag_id} - run_id {task_instance.run_id}"
            )
            return

        logger.debug(
            f"DataHub listener got notification about task instance start for {task_instance.task_id}"
        )

        # Render templates in a copy of the task instance.
        # This is necessary to get the correct operator args in the extractors.
        task_instance = copy.deepcopy(task_instance)
        task_instance.render_templates()

        dagrun: "DagRun" = task_instance.dag_run
        task = task_instance.task
        dag: "DAG" = task.dag  # type: ignore[assignment]

        self._task_holder.set_task(task_instance)

        # Handle async operators in Airflow 2.3 by skipping deferred state.
        # Inspired by https://github.com/OpenLineage/OpenLineage/pull/1601
        if task_instance.next_method is not None:
            return

        datajob = AirflowGenerator.generate_datajob(
            cluster=self.config.cluster,
            task=task,
            dag=dag,
            capture_tags=self.config.capture_tags_info,
            capture_owner=self.config.capture_ownership_info,
        )

        # TODO: Make use of get_task_location to extract github urls.

        # Add lineage info.
        self._extract_lineage(datajob, dagrun, task, task_instance)

        # TODO: Add handling for Airflow mapped tasks using task_instance.map_index

        datajob.emit(self.emitter, callback=self._make_emit_callback())
        logger.debug(f"Emitted DataHub Datajob start: {datajob}")

        if self.config.capture_executions:
            dpi = AirflowGenerator.run_datajob(
                emitter=self.emitter,
                cluster=self.config.cluster,
                ti=task_instance,
                dag=dag,
                dag_run=dagrun,
                datajob=datajob,
                emit_templates=False,
            )
            logger.debug(f"Emitted DataHub DataProcess Instance start: {dpi}")

        self.emitter.flush()

    def on_task_instance_finish(
        self, task_instance: "TaskInstance", status: InstanceRunResult
    ) -> None:
        dagrun: "DagRun" = task_instance.dag_run
        task = self._task_holder.get_task(task_instance) or task_instance.task
        dag: "DAG" = task.dag  # type: ignore[assignment]

        datajob = AirflowGenerator.generate_datajob(
            cluster=self.config.cluster,
            task=task,
            dag=dag,
            capture_tags=self.config.capture_tags_info,
            capture_owner=self.config.capture_ownership_info,
        )

        # Add lineage info.
        self._extract_lineage(datajob, dagrun, task, task_instance, complete=True)

        datajob.emit(self.emitter, callback=self._make_emit_callback())
        logger.debug(f"Emitted DataHub Datajob finish w/ status {status}: {datajob}")

        if self.config.capture_executions:
            dpi = AirflowGenerator.complete_datajob(
                emitter=self.emitter,
                cluster=self.config.cluster,
                ti=task_instance,
                dag=dag,
                dag_run=dagrun,
                datajob=datajob,
                result=status,
            )
            logger.debug(
                f"Emitted DataHub DataProcess Instance with status {status}: {dpi}"
            )

        self.emitter.flush()

    @hookimpl
    @run_in_thread
    def on_task_instance_success(
        self, previous_state: None, task_instance: "TaskInstance", session: "Session"
    ) -> None:
        self._set_log_level()

        logger.debug(
            f"DataHub listener got notification about task instance success for {task_instance.task_id}"
        )

        self.on_task_instance_finish(task_instance, status=InstanceRunResult.SUCCESS)

    @hookimpl
    @run_in_thread
    def on_task_instance_failed(
        self, previous_state: None, task_instance: "TaskInstance", session: "Session"
    ) -> None:
        self._set_log_level()

        logger.debug(
            f"DataHub listener got notification about task instance failure for {task_instance.task_id}"
        )

        # TODO: Handle UP_FOR_RETRY state.
        self.on_task_instance_finish(task_instance, status=InstanceRunResult.FAILURE)

    @hookimpl
    @run_in_thread
    def on_dag_run_running(self, dag_run: "DagRun", msg: str) -> None:
        self._set_log_level()

        logger.debug(
            f"DataHub listener got notification about dag run start for {dag_run.dag_id}"
        )

        dag = dag_run.dag
        if not dag:
            return

        dataflow = AirflowGenerator.generate_dataflow(
            cluster=self.config.cluster,
            dag=dag,
            capture_tags=self.config.capture_tags_info,
            capture_owner=self.config.capture_ownership_info,
        )
        dataflow.emit(self.emitter, callback=self._make_emit_callback())

        self.emitter.flush()

    # TODO: Add hooks for on_dag_run_success, on_dag_run_failed -> call AirflowGenerator.complete_dataflow
