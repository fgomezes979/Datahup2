from functools import reduce
from typing import TYPE_CHECKING, List, Optional, Union

import boto3
from boto3.session import Session
from pydantic.fields import Field

from datahub.configuration import ConfigModel
from datahub.configuration.common import AllowDenyPattern
from datahub.configuration.source_common import EnvBasedSourceConfigBase

if TYPE_CHECKING:

    from mypy_boto3_glue import GlueClient
    from mypy_boto3_s3 import S3Client, S3ServiceResource
    from mypy_boto3_sagemaker import SageMakerClient


def assume_role(
    role_arn: str, aws_region: str, credentials: Optional[dict] = None
) -> dict:
    credentials = credentials or {}
    sts_client = boto3.client(
        "sts",
        region_name=aws_region,
        aws_access_key_id=credentials.get("AccessKeyId"),
        aws_secret_access_key=credentials.get("SecretAccessKey"),
        aws_session_token=credentials.get("SessionToken"),
    )

    assumed_role_object = sts_client.assume_role(
        RoleArn=role_arn, RoleSessionName="DatahubIngestionSource"
    )
    return assumed_role_object["Credentials"]


class AwsSourceConfig(ConfigModel, EnvBasedSourceConfigBase):
    """
    Common AWS credentials config.

    Currently used by:
        - Glue source
        - SageMaker source
    """

    database_pattern: AllowDenyPattern = Field(
        default=AllowDenyPattern.allow_all(),
        description="regex patterns for databases to filter in ingestion.",
    )
    table_pattern: AllowDenyPattern = Field(
        default=AllowDenyPattern.allow_all(),
        description="regex patterns for tables to filter in ingestion.",
    )

    aws_access_key_id: Optional[str] = Field(
        default=None,
        description="Autodetected. See https://boto3.amazonaws.com/v1/documentation/api/latest/guide/credentials.html",
    )
    aws_secret_access_key: Optional[str] = Field(
        default=None,
        description="Autodetected. See https://boto3.amazonaws.com/v1/documentation/api/latest/guide/credentials.html",
    )
    aws_session_token: Optional[str] = Field(
        default=None,
        description="Autodetected. See https://boto3.amazonaws.com/v1/documentation/api/latest/guide/credentials.html",
    )
    aws_role: Optional[Union[str, List[str]]] = Field(
        default=None,
        description="Autodetected. See https://boto3.amazonaws.com/v1/documentation/api/latest/guide/credentials.html",
    )
    aws_profile: Optional[str] = Field(
        default=None,
        description="Named AWS profile to use, if not set the default will be used",
    )
    aws_region: str = Field(description="AWS region code.")

    def get_session(self) -> Session:
        if (
            self.aws_access_key_id
            and self.aws_secret_access_key
            and self.aws_session_token
        ):
            return Session(
                aws_access_key_id=self.aws_access_key_id,
                aws_secret_access_key=self.aws_secret_access_key,
                aws_session_token=self.aws_session_token,
                region_name=self.aws_region,
            )
        elif self.aws_access_key_id and self.aws_secret_access_key:
            return Session(
                aws_access_key_id=self.aws_access_key_id,
                aws_secret_access_key=self.aws_secret_access_key,
                region_name=self.aws_region,
            )
        elif self.aws_role:
            if isinstance(self.aws_role, str):
                credentials = assume_role(self.aws_role, self.aws_region)
            else:
                credentials = reduce(
                    lambda new_credentials, role_arn: assume_role(
                        role_arn, self.aws_region, new_credentials
                    ),
                    self.aws_role,
                    {},
                )
            return Session(
                aws_access_key_id=credentials["AccessKeyId"],
                aws_secret_access_key=credentials["SecretAccessKey"],
                aws_session_token=credentials["SessionToken"],
                region_name=self.aws_region,
            )
        else:
            return Session(region_name=self.aws_region, profile_name=self.aws_profile)

    def get_s3_client(self) -> "S3Client":
        return self.get_session().client("s3")

    def get_s3_resource(self) -> "S3ServiceResource":
        return self.get_session().resource("s3")

    def get_glue_client(self) -> "GlueClient":
        return self.get_session().client("glue")

    def get_sagemaker_client(self) -> "SageMakerClient":
        return self.get_session().client("sagemaker")
