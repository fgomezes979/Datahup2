-- create datahub database
CREATE DATABASE IF NOT EXISTS DATAHUB_DB_NAME;

-- create metadata aspect table
CREATE TABLE IF NOT EXISTS DATAHUB_DB_NAME.metadata_aspect_v2 (
  urn                           varchar(500) not null,
  aspect                        varchar(200) not null,
  version                       bigint not null,
  metadata                      text not null,
  systemmetadata                text,
  createdon                     timestamp not null,
  createdby                     varchar(255) not null,
  createdfor                    varchar(255),
  CONSTRAINT pk_metadata_aspect_v2 PRIMARY KEY (urn, aspect, version)
);

INSERT INTO DATAHUB_DB_NAME.metadata_aspect_v2 (urn, aspect, version, metadata, createdon, createdby) VALUES(
  'urn:li:corpuser:datahub',
  'corpUserInfo',
  0,
  '{"displayName":"Data Hub","active":true,"fullName":"Data Hub","email":"datahub@linkedin.com"}',
  now(),
  'urn:li:principal:datahub'
), (
  'urn:li:corpuser:datahub',
  'corpUserEditableInfo',
  0,
  '{"skills":[],"teams":[],"pictureLink":"https://raw.githubusercontent.com/linkedin/datahub/master/datahub-web/packages/data-portal/public/assets/images/default_avatar.png"}',
  now(),
  'urn:li:principal:datahub'
);
