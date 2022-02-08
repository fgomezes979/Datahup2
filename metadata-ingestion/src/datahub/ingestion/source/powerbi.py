#########################################################
#
# Meta Data Ingestion From the Power BI Source
#
#########################################################

import logging
from dataclasses import dataclass
from dataclasses import field as dataclass_field
from enum import Enum
from typing import Any, Iterable, List, Optional, Tuple

import msal
import requests

import datahub.emitter.mce_builder as builder
from datahub.configuration import ConfigModel
from datahub.configuration.common import AllowDenyPattern, ConfigurationError
from datahub.emitter.mcp import MetadataChangeProposalWrapper
from datahub.ingestion.api.common import PipelineContext
from datahub.ingestion.api.source import Source, SourceReport
from datahub.ingestion.api.workunit import MetadataWorkUnit
from datahub.metadata.com.linkedin.pegasus2avro.common import ChangeAuditStamps
from datahub.metadata.schema_classes import (
    ChangeTypeClass,
    ChartInfoClass,
    ChartKeyClass,
    CorpUserInfoClass,
    CorpUserKeyClass,
    DashboardInfoClass,
    DashboardKeyClass,
    DatasetKeyClass,
    DatasetPropertiesClass,
    OwnerClass,
    OwnershipClass,
    OwnershipTypeClass,
    StatusClass,
)

# Logger instance
LOGGER = logging.getLogger(__name__)


class Constant:
    """
    keys used in powerbi plugin
    """

    PBIAccessToken = "PBIAccessToken"
    DASHBOARD_LIST = "DASHBOARD_LIST"
    TILE_LIST = "TILE_LIST"
    DATASET_GET = "DATASET_GET"
    REPORT_GET = "REPORT_GET"
    TILE_GET = "TILE_GET"
    ENTITY_USER_LIST = "ENTITY_USER_LIST"
    Authorization = "Authorization"
    WorkspaceId = "WorkspaceId"
    DashboardId = "DashboardId"
    DatasetId = "DatasetId"
    ReportId = "ReportId"
    CHART = "chart"
    CORP_USER = "corpuser"
    CORP_USER_INFO = "corpUserInfo"
    CORP_USER_KEY = "corpUserKey"
    CHART_INFO = "chartInfo"
    STATUS = "status"
    CHART_ID = "powerbi.linkedin.com/charts/{}"
    CHART_KEY = "chartKey"
    DASHBOARD_ID = "powerbi.linkedin.com/dashboards/{}"
    DASHBOARD = "dashboard"
    DASHBOARD_KEY = "dashboardKey"
    OWNERSHIP = "ownership"
    DASHBOARD_INFO = "dashboardInfo"
    DATASET = "dataset"
    DATASET_ID = "powerbi.linkedin.com/datasets/{}"
    DATASET_KEY = "datasetKey"
    DATASET_PROPERTIES = "datasetProperties"
    VALUE = "value"
    ENTITY = "ENTITY"
    ID = "ID"


class PowerBiAPIConfig(ConfigModel):
    client_id: str
    client_secret: str
    tenant_id: str
    workspace_id: str
    scope: str = "https://analysis.windows.net/powerbi/api/.default"
    base_url: str = "https://api.powerbi.com/v1.0/myorg/groups"
    admin_base_url = "https://api.powerbi.com/v1.0/myorg/admin"
    authority = "https://login.microsoftonline.com/"

    def get_authority_url(self):
        return "{}{}".format(self.authority, self.tenant_id)


class PowerBiAPI:
    # API endpoints of PowerBi to fetch dashboards, tiles, datasets
    API_ENDPOINTS = {
        "DASHBOARD_LIST": "{POWERBI_BASE_URL}/{WORKSPACE_ID}/dashboards",
        "TILE_LIST": "{POWERBI_BASE_URL}/{WORKSPACE_ID}/dashboards/{DASHBOARD_ID}/tiles",
        "DATASET_GET": "{POWERBI_BASE_URL}/{WORKSPACE_ID}/datasets/{DATASET_ID}",
        "REPORT_GET": "{POWERBI_BASE_URL}/{WORKSPACE_ID}/reports/{REPORT_ID}",
        "ENTITY_USER_LIST": "{POWERBI_ADMIN_BASE_URL}/{ENTITY}/{ENTITY_ID}/users",
    }

    # dataclasses for PowerBi Dashboard
    @dataclass
    class Dataset:
        id: str
        name: str
        webUrl: str

        def get_urn_part(self):
            return "datasets.{}".format(self.id)

    @dataclass
    class Report:
        id: str
        name: str
        webUrl: str
        embedUrl: str
        dataset: Any

        def get_urn_part(self):
            return "datasets.{}".format(self.id)

    @dataclass
    class Tile:
        class CreatedFrom(Enum):
            REPORT = "Report"
            DATASET = "Dataset"
            UNKNOWN = "UNKNOWN"

        id: str
        title: str
        embedUrl: str
        dataset: Optional[Any]
        report: Optional[Any]
        createdFrom: CreatedFrom

        def get_urn_part(self):
            return "tiles.{}".format(self.id)

    @dataclass
    class User:
        id: str
        displayName: str
        emailAddress: str
        dashboardUserAccessRight: str
        graphId: str
        principalType: str

        def get_urn_part(self):
            return "users.{}".format(self.id)

    @dataclass
    class Dashboard:
        id: str
        displayName: str
        embedUrl: str
        webUrl: str
        isReadOnly: Any
        workspace_id: str
        tiles: List[Any]
        users: List[Any]

        def get_urn_part(self):
            return "dashboards.{}".format(self.id)

    def __init__(self, config: PowerBiAPIConfig) -> None:
        self.__config: PowerBiAPIConfig = config
        self.__access_token: str = ""

        # Power-Bi Auth (Service Principal Auth)
        self.__msal_client = msal.ConfidentialClientApplication(
            self.__config.client_id,
            client_credential=self.__config.client_secret,
            authority=self.__config.authority + self.__config.tenant_id,
        )

        # Test connection by generating a access token
        LOGGER.info("Trying to connect to {}".format(self.__config.get_authority_url()))
        self.get_access_token()
        LOGGER.info("Able to connect to {}".format(self.__config.get_authority_url()))

    def get_access_token(self):
        if self.__access_token != "":
            LOGGER.info("Returning the cached access token")

            return self.__access_token

        LOGGER.info("Generating PowerBi access token")

        auth_response = self.__msal_client.acquire_token_for_client(
            scopes=[self.__config.scope]
        )

        if not auth_response.get("access_token"):
            LOGGER.warn(
                "Failed to generate the PowerBi access token. Please check input configuration"
            )
            raise ConfigurationError(
                "Powerbi authorization failed . Please check your input configuration."
            )

        LOGGER.info("Generated PowerBi access token")

        self.__access_token = "Bearer {}".format(auth_response.get("access_token"))

        LOGGER.debug("{}={}".format(Constant.PBIAccessToken, self.__access_token))

        return self.__access_token

    def __get_users(self, workspace_id: str, entity: str, id: str) -> List[User]:
        """
        Get user for the given PowerBi entity
        """
        user_list_endpoint: str = PowerBiAPI.API_ENDPOINTS[Constant.ENTITY_USER_LIST]
        # Replace place holders
        user_list_endpoint = user_list_endpoint.format(
            POWERBI_ADMIN_BASE_URL=self.__config.admin_base_url,
            ENTITY=entity,
            ENTITY_ID=id,
        )
        # Hit PowerBi
        LOGGER.info("Request to URL={}".format(user_list_endpoint))
        response = requests.get(
            url=user_list_endpoint,
            headers={Constant.Authorization: self.get_access_token()},
        )

        # Check if we got response from PowerBi
        if response.status_code != 200:
            LOGGER.warning("Failed to fetch user list from power-bi for, http_status={}, message={}".format(response.status_code, response.text))
            LOGGER.info("{}={}".format(Constant.WorkspaceId, workspace_id))
            LOGGER.info("{}={}".format(Constant.ENTITY, entity))
            LOGGER.info("{}={}".format(Constant.ID, id))
            raise ConnectionError("Failed to fetch the user list from the power-bi")

        users_dict: List[Any] = response.json()[Constant.VALUE]

        # Iterate through response and create a list of PowerBiAPI.Dashboard
        users: List[PowerBiAPI.User] = [
            PowerBiAPI.User(
                id=instance.get("identifier"),
                displayName=instance.get("displayName"),
                emailAddress=instance.get("emailAddress"),
                dashboardUserAccessRight=instance.get("datasetUserAccessRight"),
                graphId=instance.get("graphId"),
                principalType=instance.get("principalType"),
            )
            for instance in users_dict
        ]

        return users

    def get_dashboard_users(self, dashboard: Dashboard) -> List[User]:
        """
        Return list of dashboard users
        """
        return self.__get_users(
            workspace_id=dashboard.workspace_id, entity="dashboards", id=dashboard.id
        )

    def get_dashboards(self, workspace_id: str) -> List[Dashboard]:
        """
        Get the list of dashboard from PowerBi for the given workspace identifier

        TODO: Pagination. As per REST API doc (https://docs.microsoft.com/en-us/rest/api/power-bi/dashboards/get-dashboards), there is no information available on pagination
        """
        dashboard_list_endpoint: str = PowerBiAPI.API_ENDPOINTS[Constant.DASHBOARD_LIST]
        # Replace place holders
        dashboard_list_endpoint = dashboard_list_endpoint.format(
            POWERBI_BASE_URL=self.__config.base_url, WORKSPACE_ID=workspace_id
        )
        # Hit PowerBi
        LOGGER.info("Request to URL={}".format(dashboard_list_endpoint))
        response = requests.get(
            url=dashboard_list_endpoint,
            headers={Constant.Authorization: self.get_access_token()},
        )

        # Check if we got response from PowerBi
        if response.status_code != 200:
            LOGGER.warning("Failed to fetch dashboard list from power-bi for")
            LOGGER.warning("{}={}".format(Constant.WorkspaceId, workspace_id))
            raise ConnectionError(
                "Failed to fetch the dashboard list from the power-bi"
            )

        dashboards_dict: List[Any] = response.json()[Constant.VALUE]

        # Iterate through response and create a list of PowerBiAPI.Dashboard
        dashboards: List[PowerBiAPI.Dashboard] = [
            PowerBiAPI.Dashboard(
                id=instance.get("id"),
                isReadOnly=instance.get("isReadOnly"),
                displayName=instance.get("displayName"),
                embedUrl=instance.get("embedUrl"),
                webUrl=instance.get("webUrl"),
                workspace_id=workspace_id,
                tiles=[],
                users=[],
            )
            for instance in dashboards_dict
        ]

        return dashboards

    def get_dataset(self, workspace_id: str, dataset_id: str) -> Dataset:
        """
        Fetch the dataset from PowerBi for the given dataset identifier
        """
        if workspace_id is None or dataset_id is None:
            LOGGER.info("Input values are None")
            LOGGER.info("{}={}".format(Constant.WorkspaceId, workspace_id))
            LOGGER.info("{}={}".format(Constant.DatasetId, dataset_id))
            return None

        dataset_get_endpoint: str = PowerBiAPI.API_ENDPOINTS[Constant.DATASET_GET]
        # Replace place holders
        dataset_get_endpoint = dataset_get_endpoint.format(
            POWERBI_BASE_URL=self.__config.base_url,
            WORKSPACE_ID=workspace_id,
            DATASET_ID=dataset_id,
        )
        # Hit PowerBi
        LOGGER.info("Request to dataset URL={}".format(dataset_get_endpoint))
        response = requests.get(
            url=dataset_get_endpoint,
            headers={Constant.Authorization: self.get_access_token()},
        )

        # Check if we got response from PowerBi
        if response.status_code != 200:
            message: str = "Failed to fetch dataset from power-bi for"
            LOGGER.warning(message)
            LOGGER.warning("{}={}".format(Constant.WorkspaceId, workspace_id))
            LOGGER.warning("{}={}".format(Constant.DatasetId, dataset_id))
            raise ConnectionError(message)

        response_dict = response.json()

        return PowerBiAPI.Dataset(
            id=response_dict.get("id"),
            name=response_dict.get("name"),
            webUrl=response_dict.get("webUrl"),
        )

    def get_report(self, workspace_id: str, report_id: str) -> Report:
        """
        Fetch the dataset from PowerBi for the given dataset identifier
        """
        if workspace_id is None or report_id is None:
            LOGGER.info("Input values are None")
            LOGGER.info("{}={}".format(Constant.WorkspaceId, workspace_id))
            LOGGER.info("{}={}".format(Constant.ReportId, report_id))
            return None

        report_get_endpoint: str = PowerBiAPI.API_ENDPOINTS[Constant.REPORT_GET]
        # Replace place holders
        report_get_endpoint = report_get_endpoint.format(
            POWERBI_BASE_URL=self.__config.base_url,
            WORKSPACE_ID=workspace_id,
            REPORT_ID=report_id,
        )
        # Hit PowerBi
        LOGGER.info("Request to report URL={}".format(report_get_endpoint))
        response = requests.get(
            url=report_get_endpoint,
            headers={Constant.Authorization: self.get_access_token()},
        )

        # Check if we got response from PowerBi
        if response.status_code != 200:
            message: str = "Failed to fetch report from power-bi for"
            LOGGER.warning(message)
            LOGGER.warning("{}={}".format(Constant.WorkspaceId, workspace_id))
            LOGGER.warning("{}={}".format(Constant.ReportId, report_id))
            raise ConnectionError(message)

        response_dict = response.json()

        return PowerBiAPI.Report(
            id=response_dict.get("id"),
            name=response_dict.get("name"),
            webUrl=response_dict.get("webUrl"),
            embedUrl=response_dict.get("embedUrl"),
            dataset=self.get_dataset(
                workspace_id=workspace_id, dataset_id=response_dict.get("datasetId")
            ),
        )

    def get_tiles(self, dashboard: Dashboard) -> List[Tile]:

        """
        Get the list of tiles from PowerBi for the given workspace identifier

        TODO: Pagination. As per REST API doc (https://docs.microsoft.com/en-us/rest/api/power-bi/dashboards/get-tiles), there is no information available on pagination
        """
        tile_list_endpoint: str = PowerBiAPI.API_ENDPOINTS[Constant.TILE_LIST]
        # Replace place holders
        tile_list_endpoint = tile_list_endpoint.format(
            POWERBI_BASE_URL=self.__config.base_url,
            WORKSPACE_ID=dashboard.workspace_id,
            DASHBOARD_ID=dashboard.id,
        )
        # Hit PowerBi
        LOGGER.info("Request to URL={}".format(tile_list_endpoint))
        response = requests.get(
            url=tile_list_endpoint,
            headers={Constant.Authorization: self.get_access_token()},
        )

        # Check if we got response from PowerBi
        if response.status_code != 200:
            LOGGER.warning("Failed to fetch tiles list from power-bi for")
            LOGGER.warning("{}={}".format(Constant.WorkspaceId, dashboard.workspace_id))
            LOGGER.warning("{}={}".format(Constant.DashboardId, dashboard.id))
            raise ConnectionError("Failed to fetch the tile list from the power-bi")

        def new_dataset_or_report(tile_instance: Any) -> dict:
            """
            Find out which is the data source for tile. It is either REPORT or DATASET
            """
            report_fields = {
                "dataset": None,
                "report": None,
                "createdFrom": PowerBiAPI.Tile.CreatedFrom.UNKNOWN,
            }

            import json

            print(json.dumps(tile_instance))

            if (
                tile_instance.get("datasetId") is None
                and tile_instance.get("reportId") is None
            ):
                # It is mandatory for tile to have either reportId or datasetId
                # Need to raise valueError
                message = "Tile {}(id={}) created from is unknown".format(
                    tile_instance.get("title"), tile_instance.get("id")
                )
                LOGGER.warning(message)
                return report_fields

            if tile_instance.get("datasetId") is not None:
                report_fields["dataset"] = self.get_dataset(
                    workspace_id=dashboard.workspace_id,
                    dataset_id=tile_instance.get("datasetId"),
                )
                report_fields["createdFrom"] = PowerBiAPI.Tile.CreatedFrom.DATASET
                # Return function from here
                return report_fields

            report_fields["report"] = self.get_report(
                workspace_id=dashboard.workspace_id,
                report_id=tile_instance.get("reportId"),
            )
            report_fields["createdFrom"] = PowerBiAPI.Tile.CreatedFrom.REPORT

            return report_fields

        # Iterate through response and create a list of PowerBiAPI.Dashboard
        tile_dict: List[Any] = response.json()[Constant.VALUE]
        tiles: List[PowerBiAPI.Tile] = [
            PowerBiAPI.Tile(
                id=instance.get("id"),
                title=instance.get("title"),
                embedUrl=instance.get("embedUrl"),
                **new_dataset_or_report(instance),
            )
            for instance in tile_dict
            if instance is not None
        ]

        return tiles


class PowerBiDashboardSourceConfig(PowerBiAPIConfig):
    platform_name: str = "powerbi"
    dashboard_pattern: AllowDenyPattern = AllowDenyPattern.allow_all()
    chart_pattern: AllowDenyPattern = AllowDenyPattern.allow_all()
    env: str = builder.DEFAULT_ENV


class Mapper:
    """
    Transfrom PowerBi concepts Dashboard, Dataset and Tile to DataHub concepts Dashboard, Dataset and Chart
    """

    def __init__(self, config: PowerBiDashboardSourceConfig):
        self.__config = config

    def new_mcp(
        self,
        entity_type,
        entity_urn,
        aspect_name,
        aspect,
        change_type=ChangeTypeClass.UPSERT,
    ):
        """
        Create MCP
        """
        return MetadataChangeProposalWrapper(
            entityType=entity_type,
            changeType=change_type,
            entityUrn=entity_urn,
            aspectName=aspect_name,
            aspect=aspect,
        )

    def __to_work_unit(self, mcp: MetadataChangeProposalWrapper) -> MetadataWorkUnit:
        return MetadataWorkUnit(
            id="{PLATFORM}-{ENTITY_URN}-{ASPECT_NAME}".format(
                PLATFORM=self.__config.platform_name,
                ENTITY_URN=mcp.entityUrn,
                ASPECT_NAME=mcp.aspectName,
            ),
            mcp=mcp,
        )

    def __to_datahub_dataset(
        self, dataset: Optional[PowerBiAPI.Dataset]
    ) -> List[MetadataChangeProposalWrapper]:
        """
        Map PowerBi dataset to datahub dataset
        """
        if dataset is None:
            return []

        LOGGER.info(
            "Converting dataset={}(id={}) to datahub dataset".format(
                dataset.name, dataset.id
            )
        )
        # Create an URN for dataset
        ds_urn = builder.make_dataset_urn(
            self.__config.platform_name, dataset.get_urn_part()
        )

        # Create datasetProperties mcp
        ds_properties = DatasetPropertiesClass(description=dataset.name)

        info_mcp = self.new_mcp(
            entity_type=Constant.DATASET,
            entity_urn=ds_urn,
            aspect_name=Constant.DATASET_PROPERTIES,
            aspect=ds_properties,
        )

        # Remove status mcp
        status_mcp = self.new_mcp(
            entity_type=Constant.DATASET,
            entity_urn=ds_urn,
            aspect_name=Constant.STATUS,
            aspect=StatusClass(removed=False),
        )

        # Dataset key
        ds_key_instance = DatasetKeyClass(
            platform=ds_urn,
            name=Constant.DATASET_ID.format(dataset.id),
            origin=builder.DEFAULT_ENV,
        )

        dskey_mcp = self.new_mcp(
            entity_type=Constant.DATASET,
            entity_urn=ds_urn,
            aspect_name=Constant.DATASET_KEY,
            aspect=ds_key_instance,
        )

        return [info_mcp, status_mcp, dskey_mcp]

    def __to_datahub_chart(
        self, tile: PowerBiAPI.Tile, ds_mcp: Optional[MetadataChangeProposalWrapper]
    ) -> List[MetadataChangeProposalWrapper]:
        """
        Map PowerBi tile to datahub chart
        """
        LOGGER.info("Converting tile {}(id={}) to chart".format(tile.title, tile.id))
        # Create an URN for chart
        chart_urn = builder.make_chart_urn(
            self.__config.platform_name, tile.get_urn_part()
        )

        ds_input: List[str] = []

        if ds_mcp is not None and ds_mcp.entityUrn is not None:
            ds_input.append(ds_mcp.entityUrn)

        LOGGER.info(
            "Dataset URN {} for chart {}(id={})".format(ds_input, tile.title, tile.id)
        )

        # Create chartInfo mcp
        def tile_data_source(tile: PowerBiAPI.Tile) -> dict:
            return {
                "datasetId": str(tile.dataset.id) if tile.dataset else "",
                "reportId": str(tile.report.id) if tile.report else "",
                "createdFrom": tile.createdFrom.value,
            }

        chart_info_instance = ChartInfoClass(
            title=tile.title or "",
            description=tile.title or "",
            lastModified=ChangeAuditStamps(),
            inputs=ds_input,
            chartUrl=tile.embedUrl,
            customProperties={**tile_data_source(tile)},
        )

        info_mcp = self.new_mcp(
            entity_type=Constant.CHART,
            entity_urn=chart_urn,
            aspect_name=Constant.CHART_INFO,
            aspect=chart_info_instance,
        )

        # removed status mcp
        status_mcp = self.new_mcp(
            entity_type=Constant.CHART,
            entity_urn=chart_urn,
            aspect_name=Constant.STATUS,
            aspect=StatusClass(removed=False),
        )

        # ChartKey status
        chart_key_instance = ChartKeyClass(
            dashboardTool=self.__config.platform_name,
            chartId=Constant.CHART_ID.format(tile.id),
        )

        chartkey_mcp = self.new_mcp(
            entity_type=Constant.CHART,
            entity_urn=chart_urn,
            aspect_name=Constant.CHART_KEY,
            aspect=chart_key_instance,
        )

        return [info_mcp, status_mcp, chartkey_mcp]

    def __to_datahub_dashboard(
        self,
        dashboard: PowerBiAPI.Dashboard,
        chart_mcps: List[MetadataChangeProposalWrapper],
        user_mcps: List[MetadataChangeProposalWrapper],
    ) -> List[MetadataChangeProposalWrapper]:
        """
        Map PowerBi dashboard to Datahub dashboard
        """
        dashboard_urn = builder.make_dashboard_urn(
            self.__config.platform_name, dashboard.get_urn_part()
        )

        # written in this style to fix linter error
        def to_urn_set(mcps: List[MetadataChangeProposalWrapper]) -> List[str]:
            return list(
                set(
                    [
                        mcp.entityUrn
                        for mcp in mcps
                        if mcp is not None and mcp.entityUrn is not None
                    ]
                )
            )

        chart_urn_list: List[str] = to_urn_set(chart_mcps)
        user_urn_list: List[str] = to_urn_set(user_mcps)

        def chart_custom_properties(dashboard: PowerBiAPI.Dashboard) -> dict:
            return {"chartCount": str(len(dashboard.tiles))}

        # DashboardInfo mcp
        dashboard_info_cls = DashboardInfoClass(
            description=dashboard.displayName or "",
            title=dashboard.displayName or "",
            charts=chart_urn_list,
            lastModified=ChangeAuditStamps(),
            dashboardUrl=dashboard.webUrl,
            customProperties={**chart_custom_properties(dashboard)},
        )

        info_mcp = self.new_mcp(
            entity_type=Constant.DASHBOARD,
            entity_urn=dashboard_urn,
            aspect_name=Constant.DASHBOARD_INFO,
            aspect=dashboard_info_cls,
        )

        # removed status mcp
        removed_status_mcp = self.new_mcp(
            entity_type=Constant.DASHBOARD,
            entity_urn=dashboard_urn,
            aspect_name=Constant.STATUS,
            aspect=StatusClass(removed=False),
        )

        # dashboardKey mcp
        dashboard_key_cls = DashboardKeyClass(
            dashboardTool=self.__config.platform_name,
            dashboardId=Constant.DASHBOARD_ID.format(dashboard.id),
        )

        # Dashboard key
        dashboard_key_mcp = self.new_mcp(
            entity_type=Constant.DASHBOARD,
            entity_urn=dashboard_urn,
            aspect_name=Constant.DASHBOARD_KEY,
            aspect=dashboard_key_cls,
        )

        # Dashboard Ownership
        owners = [
            OwnerClass(owner=user_urn, type=OwnershipTypeClass.CONSUMER)
            for user_urn in user_urn_list
            if user_urn is not None
        ]
        ownership = OwnershipClass(owners=owners)
        # Dashboard owner MCP
        owner_mcp = self.new_mcp(
            entity_type=Constant.DASHBOARD,
            entity_urn=dashboard_urn,
            aspect_name=Constant.OWNERSHIP,
            aspect=ownership,
        )

        return [info_mcp, removed_status_mcp, dashboard_key_mcp, owner_mcp]

    def to_datahub_user(
        self, user: PowerBiAPI.User
    ) -> List[MetadataChangeProposalWrapper]:
        """
        Map PowerBi user to datahub user
        """
        LOGGER.info(
            "Converting user {}(id={}) to datahub's user".format(
                user.displayName, user.id
            )
        )
        # Create an URN for user
        user_urn = builder.make_user_urn(user.get_urn_part())

        user_info_instance = CorpUserInfoClass(
            displayName=user.displayName,
            email=user.emailAddress,
            title=user.displayName,
            active=True,
        )

        info_mcp = self.new_mcp(
            entity_type=Constant.CORP_USER,
            entity_urn=user_urn,
            aspect_name=Constant.CORP_USER_INFO,
            aspect=user_info_instance,
        )

        # removed status mcp
        status_mcp = self.new_mcp(
            entity_type=Constant.CORP_USER,
            entity_urn=user_urn,
            aspect_name=Constant.STATUS,
            aspect=StatusClass(removed=False),
        )

        user_key = CorpUserKeyClass(username=user.id)

        user_key_mcp = self.new_mcp(
            entity_type=Constant.CORP_USER,
            entity_urn=user_urn,
            aspect_name=Constant.CORP_USER_KEY,
            aspect=user_key,
        )

        return [info_mcp, status_mcp, user_key_mcp]

    def to_datahub_users(
        self, users: List[PowerBiAPI.User]
    ) -> List[MetadataChangeProposalWrapper]:
        user_mcps = []

        for user in users:
            user_mcps.extend(self.to_datahub_user(user))

        return user_mcps

    def to_datahub_chart(
        self, tiles: List[PowerBiAPI.Tile]
    ) -> Tuple[
        List[MetadataChangeProposalWrapper], List[MetadataChangeProposalWrapper]
    ]:
        ds_mcps = []
        chart_mcps = []

        # Return empty list if input list is empty
        if len(tiles) == 0:
            return [], []

        LOGGER.info("Converting tiles(count={}) to charts".format(len(tiles)))

        for tile in tiles:
            if tile is None:
                continue
            # First convert the dataset to MCP, because dataset mcp is used in input attribute of chart mcp
            dataset_mcps = []
            dataset_mcps = self.__to_datahub_dataset(tile.dataset)

            ds_mcp = None
            if len(dataset_mcps) > 0:
                # We are passing dataset_mcps[0] as we need only a dataset MCP to set entityURN in chart
                ds_mcp = dataset_mcps[0]

            # Now convert tile to chart MCP
            chart_mcp = self.__to_datahub_chart(tile, ds_mcp)

            ds_mcps.extend(dataset_mcps)
            chart_mcps.extend(chart_mcp)

        # Return dataset and chart MCPs

        return ds_mcps, chart_mcps

    def to_datahub_work_units(
        self, dashboard: PowerBiAPI.Dashboard
    ) -> List[MetadataWorkUnit]:
        mcps = []

        LOGGER.info(
            "Converting dashboard={} to datahub dashboard".format(dashboard.displayName)
        )

        # Convert user to CorpUser
        user_mcps = self.to_datahub_users(dashboard.users)
        # Convert tiles to charts
        ds_mcps, chart_mcps = self.to_datahub_chart(dashboard.tiles)
        # Lets convert dashboard to datahub dashboard
        dashboard_mcps = self.__to_datahub_dashboard(dashboard, chart_mcps, user_mcps)

        # Now add MCPs in sequence
        mcps.extend(ds_mcps)
        mcps.extend(user_mcps)
        mcps.extend(chart_mcps)
        mcps.extend(dashboard_mcps)

        # Convert MCP to work_units
        work_units = map(self.__to_work_unit, mcps)

        return [wu for wu in work_units if wu is not None]


@dataclass
class PowerBiDashboardSourceReport(SourceReport):
    dashboards_scanned: int = 0
    charts_scanned: int = 0
    filtered_dashboards: List[str] = dataclass_field(default_factory=list)
    filtered_charts: List[str] = dataclass_field(default_factory=list)

    def report_dashboards_scanned(self, count: int = 1) -> None:
        self.dashboards_scanned += count

    def report_charts_scanned(self, count: int = 1) -> None:
        self.charts_scanned += count

    def report_dashboards_dropped(self, model: str) -> None:
        self.filtered_dashboards.append(model)

    def report_charts_dropped(self, view: str) -> None:
        self.filtered_charts.append(view)


class PowerBiDashboardSource(Source):
    """
    Datahub PowerBi plugin main class. This class extends Source to become PowerBi data ingestion source for Datahub
    """

    source_config: PowerBiDashboardSourceConfig
    reporter: PowerBiDashboardSourceReport
    accessed_dashboards: int = 0

    def __init__(self, config: PowerBiDashboardSourceConfig, ctx: PipelineContext):
        super().__init__(ctx)
        self.source_config = config
        self.reporter = PowerBiDashboardSourceReport()
        self.auth_token = PowerBiAPI(self.source_config).get_access_token()
        self.powerbi_client = PowerBiAPI(self.source_config)
        self.mapper = Mapper(config)

    @classmethod
    def create(cls, config_dict, ctx):
        config = PowerBiDashboardSourceConfig.parse_obj(config_dict)
        return cls(config, ctx)

    def get_workunits(self) -> Iterable[MetadataWorkUnit]:
        """
        Datahub Ingestion framework invoke this method
        """
        LOGGER.info("PowerBi plugin execution is started")

        # Fetch all PowerBi dashboard for given workspace identifier
        dashboards = self.powerbi_client.get_dashboards(self.source_config.workspace_id)

        for dashboard in dashboards:

            try:
                # Fetch PowerBi tiles for dashboards
                dashboard.tiles = self.powerbi_client.get_tiles(dashboard)
                # Fetch PowerBi users for dashboards
                dashboard.users = self.powerbi_client.get_dashboard_users(dashboard)
                # Increase dashboard and tiles count in report
                self.reporter.report_dashboards_scanned()
                self.reporter.report_charts_scanned(count=len(dashboard.tiles))
            except Exception as e:
                message = "Error ({}) occurred while loading dashboard {}(id={}) tiles.".format(
                    e, dashboard.displayName, dashboard.id
                )
                LOGGER.exception(message, e)
                self.reporter.report_warning(dashboard.id, message)

            # Convert PowerBi Dashboard and child entities to Datahub work unit to ingest into Datahub
            workunits = self.mapper.to_datahub_work_units(dashboard)
            for workunit in workunits:
                # Add workunit to report
                self.reporter.report_workunit(workunit)
                # Return workunit to Datahub Ingestion framework
                yield workunit

        print("Mohd-4")

    def get_report(self) -> SourceReport:
        return self.reporter
