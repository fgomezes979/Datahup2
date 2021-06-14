import io
import datetime
import botocore.session
from botocore.stub import Stubber
from botocore.response import StreamingBody

get_databases_response = {
    "DatabaseList": [
        {
            "Name": "flights-database",
            "CreateTime": datetime.datetime(2021, 6, 9, 14, 14, 19),
            "CreateTableDefaultPermissions": [
                {
                    "Principal": {
                        "DataLakePrincipalIdentifier": "IAM_ALLOWED_PRINCIPALS"
                    },
                    "Permissions": ["ALL"],
                }
            ],
            "CatalogId": "123412341234",
        },
        {
            "Name": "test-database",
            "CreateTime": datetime.datetime(2021, 6, 1, 14, 55, 2),
            "CreateTableDefaultPermissions": [
                {
                    "Principal": {
                        "DataLakePrincipalIdentifier": "IAM_ALLOWED_PRINCIPALS"
                    },
                    "Permissions": ["ALL"],
                }
            ],
            "CatalogId": "123412341234",
        },
    ]
}
get_tables_response_1 = {
    "TableList": [
        {
            "Name": "avro",
            "DatabaseName": "flights-database",
            "Owner": "owner",
            "CreateTime": datetime.datetime(2021, 6, 9, 14, 17, 35),
            "UpdateTime": datetime.datetime(2021, 6, 9, 14, 17, 35),
            "LastAccessTime": datetime.datetime(2021, 6, 9, 14, 17, 35),
            "Retention": 0,
            "StorageDescriptor": {
                "Columns": [
                    {"Name": "yr", "Type": "int"},
                    {"Name": "flightdate", "Type": "string"},
                    {"Name": "uniquecarrier", "Type": "string"},
                    {"Name": "airlineid", "Type": "int"},
                    {"Name": "carrier", "Type": "string"},
                    {"Name": "flightnum", "Type": "string"},
                    {"Name": "origin", "Type": "string"},
                    {"Name": "dest", "Type": "string"},
                    {"Name": "depdelay", "Type": "int"},
                    {"Name": "carrierdelay", "Type": "int"},
                    {"Name": "weatherdelay", "Type": "int"},
                ],
                "Location": "s3://crawler-public-us-west-2/flight/avro/",
                "InputFormat": "org.apache.hadoop.hive.ql.io.avro.AvroContainerInputFormat",
                "OutputFormat": "org.apache.hadoop.hive.ql.io.avro.AvroContainerOutputFormat",
                "Compressed": False,
                "NumberOfBuckets": -1,
                "SerdeInfo": {
                    "SerializationLibrary": "org.apache.hadoop.hive.serde2.avro.AvroSerDe",
                    "Parameters": {
                        "avro.schema.literal": '{"type":"record","name":"flights_avro_subset","namespace":"default","fields":[{"name":"yr","type":["null","int"],"default":null},{"name":"flightdate","type":["null","string"],"default":null},{"name":"uniquecarrier","type":["null","string"],"default":null},{"name":"airlineid","type":["null","int"],"default":null},{"name":"carrier","type":["null","string"],"default":null},{"name":"flightnum","type":["null","string"],"default":null},{"name":"origin","type":["null","string"],"default":null},{"name":"dest","type":["null","string"],"default":null},{"name":"depdelay","type":["null","int"],"default":null},{"name":"carrierdelay","type":["null","int"],"default":null},{"name":"weatherdelay","type":["null","int"],"default":null}]}',
                        "serialization.format": "1",
                    },
                },
                "BucketColumns": [],
                "SortColumns": [],
                "Parameters": {
                    "CrawlerSchemaDeserializerVersion": "1.0",
                    "CrawlerSchemaSerializerVersion": "1.0",
                    "UPDATED_BY_CRAWLER": "flights-crawler",
                    "averageRecordSize": "55",
                    "avro.schema.literal": '{"type":"record","name":"flights_avro_subset","namespace":"default","fields":[{"name":"yr","type":["null","int"],"default":null},{"name":"flightdate","type":["null","string"],"default":null},{"name":"uniquecarrier","type":["null","string"],"default":null},{"name":"airlineid","type":["null","int"],"default":null},{"name":"carrier","type":["null","string"],"default":null},{"name":"flightnum","type":["null","string"],"default":null},{"name":"origin","type":["null","string"],"default":null},{"name":"dest","type":["null","string"],"default":null},{"name":"depdelay","type":["null","int"],"default":null},{"name":"carrierdelay","type":["null","int"],"default":null},{"name":"weatherdelay","type":["null","int"],"default":null}]}',
                    "classification": "avro",
                    "compressionType": "none",
                    "objectCount": "30",
                    "recordCount": "169222196",
                    "sizeKey": "9503351413",
                    "typeOfData": "file",
                },
                "StoredAsSubDirectories": False,
            },
            "PartitionKeys": [{"Name": "year", "Type": "string"}],
            "TableType": "EXTERNAL_TABLE",
            "Parameters": {
                "CrawlerSchemaDeserializerVersion": "1.0",
                "CrawlerSchemaSerializerVersion": "1.0",
                "UPDATED_BY_CRAWLER": "flights-crawler",
                "averageRecordSize": "55",
                "avro.schema.literal": '{"type":"record","name":"flights_avro_subset","namespace":"default","fields":[{"name":"yr","type":["null","int"],"default":null},{"name":"flightdate","type":["null","string"],"default":null},{"name":"uniquecarrier","type":["null","string"],"default":null},{"name":"airlineid","type":["null","int"],"default":null},{"name":"carrier","type":["null","string"],"default":null},{"name":"flightnum","type":["null","string"],"default":null},{"name":"origin","type":["null","string"],"default":null},{"name":"dest","type":["null","string"],"default":null},{"name":"depdelay","type":["null","int"],"default":null},{"name":"carrierdelay","type":["null","int"],"default":null},{"name":"weatherdelay","type":["null","int"],"default":null}]}',
                "classification": "avro",
                "compressionType": "none",
                "objectCount": "30",
                "recordCount": "169222196",
                "sizeKey": "9503351413",
                "typeOfData": "file",
            },
            "CreatedBy": "arn:aws:sts::123412341234:assumed-role/AWSGlueServiceRole-flights-crawler/AWS-Crawler",
            "IsRegisteredWithLakeFormation": False,
            "CatalogId": "123412341234",
        }
    ]
}
get_tables_response_2 = {
    "TableList": [
        {
            "Name": "test_jsons_markers",
            "DatabaseName": "test-database",
            "Owner": "owner",
            "CreateTime": datetime.datetime(2021, 6, 2, 12, 6, 59),
            "UpdateTime": datetime.datetime(2021, 6, 2, 12, 6, 59),
            "LastAccessTime": datetime.datetime(2021, 6, 2, 12, 6, 59),
            "Retention": 0,
            "StorageDescriptor": {
                "Columns": [
                    {
                        "Name": "markers",
                        "Type": "array<struct<name:string,position:array<double>,location:array<double>>>",
                    }
                ],
                "Location": "s3://test-glue-jsons/markers/",
                "InputFormat": "org.apache.hadoop.mapred.TextInputFormat",
                "OutputFormat": "org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat",
                "Compressed": False,
                "NumberOfBuckets": -1,
                "SerdeInfo": {
                    "SerializationLibrary": "org.openx.data.jsonserde.JsonSerDe",
                    "Parameters": {"paths": "markers"},
                },
                "BucketColumns": [],
                "SortColumns": [],
                "Parameters": {
                    "CrawlerSchemaDeserializerVersion": "1.0",
                    "CrawlerSchemaSerializerVersion": "1.0",
                    "UPDATED_BY_CRAWLER": "test-jsons",
                    "averageRecordSize": "273",
                    "classification": "json",
                    "compressionType": "none",
                    "objectCount": "1",
                    "recordCount": "1",
                    "sizeKey": "273",
                    "typeOfData": "file",
                },
                "StoredAsSubDirectories": False,
            },
            "PartitionKeys": [],
            "TableType": "EXTERNAL_TABLE",
            "Parameters": {
                "CrawlerSchemaDeserializerVersion": "1.0",
                "CrawlerSchemaSerializerVersion": "1.0",
                "UPDATED_BY_CRAWLER": "test-jsons",
                "averageRecordSize": "273",
                "classification": "json",
                "compressionType": "none",
                "objectCount": "1",
                "recordCount": "1",
                "sizeKey": "273",
                "typeOfData": "file",
            },
            "CreatedBy": "arn:aws:sts::795586375822:assumed-role/AWSGlueServiceRole-test-crawler/AWS-Crawler",
            "IsRegisteredWithLakeFormation": False,
            "CatalogId": "795586375822",
        },
        {
            "Name": "test_parquet",
            "DatabaseName": "test-database",
            "Owner": "owner",
            "CreateTime": datetime.datetime(2021, 6, 1, 16, 14, 53),
            "UpdateTime": datetime.datetime(2021, 6, 1, 16, 14, 53),
            "LastAccessTime": datetime.datetime(2021, 6, 1, 16, 14, 53),
            "Retention": 0,
            "StorageDescriptor": {
                "Columns": [
                    {"Name": "yr", "Type": "int"},
                    {"Name": "quarter", "Type": "int"},
                    {"Name": "month", "Type": "int"},
                    {"Name": "dayofmonth", "Type": "int"},
                    {"Name": "dayofweek", "Type": "int"},
                    {"Name": "flightdate", "Type": "string"},
                    {"Name": "uniquecarrier", "Type": "string"},
                    {"Name": "airlineid", "Type": "int"},
                    {"Name": "carrier", "Type": "string"},
                    {"Name": "tailnum", "Type": "string"},
                    {"Name": "flightnum", "Type": "string"},
                    {"Name": "originairportid", "Type": "int"},
                    {"Name": "originairportseqid", "Type": "int"},
                    {"Name": "origincitymarketid", "Type": "int"},
                    {"Name": "origin", "Type": "string"},
                    {"Name": "origincityname", "Type": "string"},
                    {"Name": "originstate", "Type": "string"},
                    {"Name": "originstatefips", "Type": "string"},
                    {"Name": "originstatename", "Type": "string"},
                    {"Name": "originwac", "Type": "int"},
                    {"Name": "destairportid", "Type": "int"},
                    {"Name": "destairportseqid", "Type": "int"},
                    {"Name": "destcitymarketid", "Type": "int"},
                    {"Name": "dest", "Type": "string"},
                    {"Name": "destcityname", "Type": "string"},
                    {"Name": "deststate", "Type": "string"},
                    {"Name": "deststatefips", "Type": "string"},
                    {"Name": "deststatename", "Type": "string"},
                    {"Name": "destwac", "Type": "int"},
                    {"Name": "crsdeptime", "Type": "string"},
                    {"Name": "deptime", "Type": "string"},
                    {"Name": "depdelay", "Type": "int"},
                    {"Name": "depdelayminutes", "Type": "int"},
                    {"Name": "depdel15", "Type": "int"},
                    {"Name": "departuredelaygroups", "Type": "int"},
                    {"Name": "deptimeblk", "Type": "string"},
                    {"Name": "taxiout", "Type": "int"},
                    {"Name": "wheelsoff", "Type": "string"},
                    {"Name": "wheelson", "Type": "string"},
                    {"Name": "taxiin", "Type": "int"},
                    {"Name": "crsarrtime", "Type": "int"},
                    {"Name": "arrtime", "Type": "string"},
                    {"Name": "arrdelay", "Type": "int"},
                    {"Name": "arrdelayminutes", "Type": "int"},
                    {"Name": "arrdel15", "Type": "int"},
                    {"Name": "arrivaldelaygroups", "Type": "int"},
                    {"Name": "arrtimeblk", "Type": "string"},
                    {"Name": "cancelled", "Type": "int"},
                    {"Name": "cancellationcode", "Type": "string"},
                    {"Name": "diverted", "Type": "int"},
                    {"Name": "crselapsedtime", "Type": "int"},
                    {"Name": "actualelapsedtime", "Type": "int"},
                    {"Name": "airtime", "Type": "int"},
                    {"Name": "flights", "Type": "int"},
                    {"Name": "distance", "Type": "int"},
                    {"Name": "distancegroup", "Type": "int"},
                    {"Name": "carrierdelay", "Type": "int"},
                    {"Name": "weatherdelay", "Type": "int"},
                    {"Name": "nasdelay", "Type": "int"},
                    {"Name": "securitydelay", "Type": "int"},
                    {"Name": "lateaircraftdelay", "Type": "int"},
                    {"Name": "firstdeptime", "Type": "string"},
                    {"Name": "totaladdgtime", "Type": "int"},
                    {"Name": "longestaddgtime", "Type": "int"},
                    {"Name": "divairportlandings", "Type": "int"},
                    {"Name": "divreacheddest", "Type": "int"},
                    {"Name": "divactualelapsedtime", "Type": "int"},
                    {"Name": "divarrdelay", "Type": "int"},
                    {"Name": "divdistance", "Type": "int"},
                    {"Name": "div1airport", "Type": "string"},
                    {"Name": "div1airportid", "Type": "int"},
                    {"Name": "div1airportseqid", "Type": "int"},
                    {"Name": "div1wheelson", "Type": "string"},
                    {"Name": "div1totalgtime", "Type": "int"},
                    {"Name": "div1longestgtime", "Type": "int"},
                    {"Name": "div1wheelsoff", "Type": "string"},
                    {"Name": "div1tailnum", "Type": "string"},
                    {"Name": "div2airport", "Type": "string"},
                    {"Name": "div2airportid", "Type": "int"},
                    {"Name": "div2airportseqid", "Type": "int"},
                    {"Name": "div2wheelson", "Type": "string"},
                    {"Name": "div2totalgtime", "Type": "int"},
                    {"Name": "div2longestgtime", "Type": "int"},
                    {"Name": "div2wheelsoff", "Type": "string"},
                    {"Name": "div2tailnum", "Type": "string"},
                    {"Name": "div3airport", "Type": "string"},
                    {"Name": "div3airportid", "Type": "int"},
                    {"Name": "div3airportseqid", "Type": "int"},
                    {"Name": "div3wheelson", "Type": "string"},
                    {"Name": "div3totalgtime", "Type": "int"},
                    {"Name": "div3longestgtime", "Type": "int"},
                    {"Name": "div3wheelsoff", "Type": "string"},
                    {"Name": "div3tailnum", "Type": "string"},
                    {"Name": "div4airport", "Type": "string"},
                    {"Name": "div4airportid", "Type": "int"},
                    {"Name": "div4airportseqid", "Type": "int"},
                    {"Name": "div4wheelson", "Type": "string"},
                    {"Name": "div4totalgtime", "Type": "int"},
                    {"Name": "div4longestgtime", "Type": "int"},
                    {"Name": "div4wheelsoff", "Type": "string"},
                    {"Name": "div4tailnum", "Type": "string"},
                    {"Name": "div5airport", "Type": "string"},
                    {"Name": "div5airportid", "Type": "int"},
                    {"Name": "div5airportseqid", "Type": "int"},
                    {"Name": "div5wheelson", "Type": "string"},
                    {"Name": "div5totalgtime", "Type": "int"},
                    {"Name": "div5longestgtime", "Type": "int"},
                    {"Name": "div5wheelsoff", "Type": "string"},
                    {"Name": "div5tailnum", "Type": "string"},
                ],
                "Location": "s3://crawler-public-us-west-2/flight/parquet/",
                "InputFormat": "org.apache.hadoop.hive.ql.io.parquet.MapredParquetInputFormat",
                "OutputFormat": "org.apache.hadoop.hive.ql.io.parquet.MapredParquetOutputFormat",
                "Compressed": False,
                "NumberOfBuckets": -1,
                "SerdeInfo": {
                    "SerializationLibrary": "org.apache.hadoop.hive.ql.io.parquet.serde.ParquetHiveSerDe",
                    "Parameters": {"serialization.format": "1"},
                },
                "BucketColumns": [],
                "SortColumns": [],
                "Parameters": {
                    "CrawlerSchemaDeserializerVersion": "1.0",
                    "CrawlerSchemaSerializerVersion": "1.0",
                    "UPDATED_BY_CRAWLER": "test",
                    "averageRecordSize": "19",
                    "classification": "parquet",
                    "compressionType": "none",
                    "objectCount": "60",
                    "recordCount": "167497743",
                    "sizeKey": "4463574900",
                    "typeOfData": "file",
                },
                "StoredAsSubDirectories": False,
            },
            "PartitionKeys": [{"Name": "year", "Type": "string"}],
            "TableType": "EXTERNAL_TABLE",
            "Parameters": {
                "CrawlerSchemaDeserializerVersion": "1.0",
                "CrawlerSchemaSerializerVersion": "1.0",
                "UPDATED_BY_CRAWLER": "test",
                "averageRecordSize": "19",
                "classification": "parquet",
                "compressionType": "none",
                "objectCount": "60",
                "recordCount": "167497743",
                "sizeKey": "4463574900",
                "typeOfData": "file",
            },
            "CreatedBy": "arn:aws:sts::795586375822:assumed-role/AWSGlueServiceRole-test-crawler/AWS-Crawler",
            "IsRegisteredWithLakeFormation": False,
            "CatalogId": "795586375822",
        },
    ]
}
get_jobs_response = {
    "Jobs": [
        {
            "Name": "test-job-1",
            "Description": "The first test job",
            "Role": "arn:aws:iam::123412341234:role/service-role/AWSGlueServiceRole-glue-crawler",
            "CreatedOn": datetime.datetime(2021, 6, 10, 16, 51, 25, 690000),
            "LastModifiedOn": datetime.datetime(2021, 6, 10, 16, 55, 35, 307000),
            "ExecutionProperty": {"MaxConcurrentRuns": 1},
            "Command": {
                "Name": "glueetl",
                "ScriptLocation": "s3://aws-glue-assets-123412341234-us-west-2/scripts/job-1.py",
                "PythonVersion": "3",
            },
            "DefaultArguments": {
                "--TempDir": "s3://aws-glue-assets-123412341234-us-west-2/temporary/",
                "--class": "GlueApp",
                "--enable-continuous-cloudwatch-log": "true",
                "--enable-glue-datacatalog": "true",
                "--enable-metrics": "true",
                "--enable-spark-ui": "true",
                "--encryption-type": "sse-s3",
                "--job-bookmark-option": "job-bookmark-enable",
                "--job-language": "python",
                "--spark-event-logs-path": "s3://aws-glue-assets-123412341234-us-west-2/sparkHistoryLogs/",
            },
            "MaxRetries": 3,
            "AllocatedCapacity": 10,
            "Timeout": 2880,
            "MaxCapacity": 10.0,
            "WorkerType": "G.1X",
            "NumberOfWorkers": 10,
            "GlueVersion": "2.0",
        },
        {
            "Name": "test-job-2",
            "Description": "The second test job",
            "Role": "arn:aws:iam::123412341234:role/service-role/AWSGlueServiceRole-glue-crawler",
            "CreatedOn": datetime.datetime(2021, 6, 10, 16, 58, 32, 469000),
            "LastModifiedOn": datetime.datetime(2021, 6, 10, 16, 58, 32, 469000),
            "ExecutionProperty": {"MaxConcurrentRuns": 1},
            "Command": {
                "Name": "glueetl",
                "ScriptLocation": "s3://aws-glue-assets-123412341234-us-west-2/scripts/job-2.py",
                "PythonVersion": "3",
            },
            "DefaultArguments": {
                "--TempDir": "s3://aws-glue-assets-123412341234-us-west-2/temporary/",
                "--class": "GlueApp",
                "--enable-continuous-cloudwatch-log": "true",
                "--enable-glue-datacatalog": "true",
                "--enable-metrics": "true",
                "--enable-spark-ui": "true",
                "--encryption-type": "sse-s3",
                "--job-bookmark-option": "job-bookmark-enable",
                "--job-language": "python",
                "--spark-event-logs-path": "s3://aws-glue-assets-123412341234-us-west-2/sparkHistoryLogs/",
            },
            "MaxRetries": 3,
            "AllocatedCapacity": 10,
            "Timeout": 2880,
            "MaxCapacity": 10.0,
            "WorkerType": "G.1X",
            "NumberOfWorkers": 10,
            "GlueVersion": "2.0",
        },
    ]
}
# for job 1
get_dataflow_graph_response_1 = {
    "DagNodes": [
        {
            "Id": "Transform0",
            "NodeType": "Filter",
            "Args": [
                {"Name": "f", "Value": "lambda row : ()", "Param": False},
                {
                    "Name": "transformation_ctx",
                    "Value": '"Transform0"',
                    "Param": False,
                },
            ],
            "LineNumber": 32,
        },
        {
            "Id": "Transform1",
            "NodeType": "ApplyMapping",
            "Args": [
                {
                    "Name": "mappings",
                    "Value": '[("yr", "int", "yr", "int"), ("flightdate", "string", "flightdate", "string"), ("uniquecarrier", "string", "uniquecarrier", "string"), ("airlineid", "int", "airlineid", "int"), ("carrier", "string", "carrier", "string"), ("flightnum", "string", "flightnum", "string"), ("origin", "string", "origin", "string"), ("dest", "string", "dest", "string"), ("depdelay", "int", "depdelay", "int"), ("carrierdelay", "int", "carrierdelay", "int"), ("weatherdelay", "int", "weatherdelay", "int"), ("year", "string", "year", "string")]',
                    "Param": False,
                },
                {
                    "Name": "transformation_ctx",
                    "Value": '"Transform1"',
                    "Param": False,
                },
            ],
            "LineNumber": 37,
        },
        {
            "Id": "Transform2",
            "NodeType": "ApplyMapping",
            "Args": [
                {
                    "Name": "mappings",
                    "Value": '[("yr", "int", "yr", "int"), ("flightdate", "string", "flightdate", "string"), ("uniquecarrier", "string", "uniquecarrier", "string"), ("airlineid", "int", "airlineid", "int"), ("carrier", "string", "carrier", "string"), ("flightnum", "string", "flightnum", "string"), ("origin", "string", "origin", "string"), ("dest", "string", "dest", "string"), ("depdelay", "int", "depdelay", "int"), ("carrierdelay", "int", "carrierdelay", "int"), ("weatherdelay", "int", "weatherdelay", "int"), ("year", "string", "year", "string")]',
                    "Param": False,
                },
                {
                    "Name": "transformation_ctx",
                    "Value": '"Transform2"',
                    "Param": False,
                },
            ],
            "LineNumber": 22,
        },
        {
            "Id": "Transform3",
            "NodeType": "Join",
            "Args": [
                {
                    "Name": "keys2",
                    "Value": '["(right) flightdate"]',
                    "Param": False,
                },
                {
                    "Name": "transformation_ctx",
                    "Value": '"Transform3"',
                    "Param": False,
                },
                {"Name": "keys1", "Value": '["yr"]', "Param": False},
            ],
            "LineNumber": 47,
        },
        {
            "Id": "DataSource0",
            "NodeType": "DataSource",
            "Args": [
                {
                    "Name": "database",
                    "Value": '"flights-database"',
                    "Param": False,
                },
                {"Name": "table_name", "Value": '"avro"', "Param": False},
                {
                    "Name": "transformation_ctx",
                    "Value": '"DataSource0"',
                    "Param": False,
                },
            ],
            "LineNumber": 17,
        },
        {
            "Id": "DataSink0",
            "NodeType": "DataSink",
            "Args": [
                {
                    "Name": "database",
                    "Value": '"test-database"',
                    "Param": False,
                },
                {
                    "Name": "table_name",
                    "Value": '"test_jsons_markers"',
                    "Param": False,
                },
                {
                    "Name": "transformation_ctx",
                    "Value": '"DataSink0"',
                    "Param": False,
                },
            ],
            "LineNumber": 57,
        },
        {
            "Id": "Transform4",
            "NodeType": "ApplyMapping",
            "Args": [
                {
                    "Name": "mappings",
                    "Value": '[("yr", "int", "yr", "int"), ("flightdate", "string", "flightdate", "string"), ("uniquecarrier", "string", "uniquecarrier", "string"), ("airlineid", "int", "airlineid", "int"), ("carrier", "string", "carrier", "string"), ("flightnum", "string", "flightnum", "string"), ("origin", "string", "origin", "string"), ("dest", "string", "dest", "string"), ("depdelay", "int", "depdelay", "int"), ("carrierdelay", "int", "carrierdelay", "int"), ("weatherdelay", "int", "weatherdelay", "int"), ("year", "string", "year", "string")]',
                    "Param": False,
                },
                {
                    "Name": "transformation_ctx",
                    "Value": '"Transform4"',
                    "Param": False,
                },
            ],
            "LineNumber": 27,
        },
        {
            "Id": "Transform5",
            "NodeType": "ApplyMapping",
            "Args": [
                {
                    "Name": "mappings",
                    "Value": '[("yr", "int", "(right) yr", "int"), ("flightdate", "string", "(right) flightdate", "string"), ("uniquecarrier", "string", "(right) uniquecarrier", "string"), ("airlineid", "int", "(right) airlineid", "int"), ("carrier", "string", "(right) carrier", "string"), ("flightnum", "string", "(right) flightnum", "string"), ("origin", "string", "(right) origin", "string"), ("dest", "string", "(right) dest", "string"), ("depdelay", "int", "(right) depdelay", "int"), ("carrierdelay", "int", "(right) carrierdelay", "int"), ("weatherdelay", "int", "(right) weatherdelay", "int"), ("year", "string", "(right) year", "string")]',
                    "Param": False,
                },
                {
                    "Name": "transformation_ctx",
                    "Value": '"Transform5"',
                    "Param": False,
                },
            ],
            "LineNumber": 42,
        },
        {
            "Id": "DataSink1",
            "NodeType": "DataSink",
            "Args": [
                {"Name": "connection_type", "Value": '"s3"', "Param": False},
                {"Name": "format", "Value": '"json"', "Param": False},
                {
                    "Name": "connection_options",
                    "Value": '{"path": "s3://test-glue-jsons/", "partitionKeys": []}',
                    "Param": False,
                },
                {
                    "Name": "transformation_ctx",
                    "Value": '"DataSink1"',
                    "Param": False,
                },
            ],
            "LineNumber": 52,
        },
    ],
    "DagEdges": [
        {
            "Source": "Transform2",
            "Target": "Transform0",
            "TargetParameter": "frame",
        },
        {
            "Source": "Transform0",
            "Target": "Transform1",
            "TargetParameter": "frame",
        },
        {
            "Source": "DataSource0",
            "Target": "Transform2",
            "TargetParameter": "frame",
        },
        {
            "Source": "Transform4",
            "Target": "Transform3",
            "TargetParameter": "frame1",
        },
        {
            "Source": "Transform5",
            "Target": "Transform3",
            "TargetParameter": "frame2",
        },
        {
            "Source": "Transform3",
            "Target": "DataSink0",
            "TargetParameter": "frame",
        },
        {
            "Source": "Transform2",
            "Target": "Transform4",
            "TargetParameter": "frame",
        },
        {
            "Source": "Transform1",
            "Target": "Transform5",
            "TargetParameter": "frame",
        },
        {
            "Source": "Transform3",
            "Target": "DataSink1",
            "TargetParameter": "frame",
        },
    ],
}
# for job 2
get_dataflow_graph_response_2 = {
    "DagNodes": [
        {
            "Id": "Transform0",
            "NodeType": "SplitFields",
            "Args": [
                {
                    "Name": "paths",
                    "Value": '["yr", "quarter", "month", "dayofmonth", "dayofweek", "flightdate", "uniquecarrier", "airlineid", "carrier", "tailnum", "flightnum", "originairportid", "originairportseqid", "origincitymarketid", "origin", "origincityname", "originstate", "originstatefips", "originstatename", "originwac", "destairportid", "destairportseqid", "destcitymarketid", "dest", "destcityname", "deststate", "deststatefips", "deststatename", "destwac", "crsdeptime", "deptime", "depdelay", "depdelayminutes", "depdel15", "departuredelaygroups", "deptimeblk", "taxiout", "wheelsoff", "wheelson", "taxiin", "crsarrtime", "arrtime", "arrdelay", "arrdelayminutes", "arrdel15", "arrivaldelaygroups", "arrtimeblk", "cancelled", "cancellationcode", "diverted", "crselapsedtime", "actualelapsedtime", "airtime", "flights", "distance", "distancegroup", "carrierdelay", "weatherdelay", "nasdelay", "securitydelay", "lateaircraftdelay", "firstdeptime", "totaladdgtime", "longestaddgtime", "divairportlandings", "divreacheddest", "divactualelapsedtime", "divarrdelay", "divdistance", "div1airport", "div1airportid", "div1airportseqid", "div1wheelson", "div1totalgtime", "div1longestgtime", "div1wheelsoff", "div1tailnum", "div2airport", "div2airportid", "div2airportseqid", "div2wheelson", "div2totalgtime", "div2longestgtime", "div2wheelsoff", "div2tailnum", "div3airport", "div3airportid", "div3airportseqid", "div3wheelson", "div3totalgtime", "div3longestgtime", "div3wheelsoff", "div3tailnum", "div4airport", "div4airportid", "div4airportseqid", "div4wheelson", "div4totalgtime", "div4longestgtime", "div4wheelsoff", "div4tailnum", "div5airport", "div5airportid", "div5airportseqid", "div5wheelson", "div5totalgtime", "div5longestgtime", "div5wheelsoff", "div5tailnum", "year"]',
                    "Param": False,
                },
                {
                    "Name": "name2",
                    "Value": '"Transform0Output1"',
                    "Param": False,
                },
                {
                    "Name": "name1",
                    "Value": '"Transform0Output0"',
                    "Param": False,
                },
                {
                    "Name": "transformation_ctx",
                    "Value": '"Transform0"',
                    "Param": False,
                },
            ],
            "LineNumber": 42,
        },
        {
            "Id": "Transform1",
            "NodeType": "ApplyMapping",
            "Args": [
                {
                    "Name": "mappings",
                    "Value": '[("yr", "int", "yr", "int"), ("quarter", "int", "quarter", "int"), ("month", "int", "month", "int"), ("dayofmonth", "int", "dayofmonth", "int"), ("dayofweek", "int", "dayofweek", "int"), ("flightdate", "string", "flightdate", "string"), ("uniquecarrier", "string", "uniquecarrier", "string"), ("airlineid", "int", "airlineid", "int"), ("carrier", "string", "carrier", "string"), ("tailnum", "string", "tailnum", "string"), ("flightnum", "string", "flightnum", "string"), ("originairportid", "int", "originairportid", "int"), ("originairportseqid", "int", "originairportseqid", "int"), ("origincitymarketid", "int", "origincitymarketid", "int"), ("origin", "string", "origin", "string"), ("origincityname", "string", "origincityname", "string"), ("originstate", "string", "originstate", "string"), ("originstatefips", "string", "originstatefips", "string"), ("originstatename", "string", "originstatename", "string"), ("originwac", "int", "originwac", "int"), ("destairportid", "int", "destairportid", "int"), ("destairportseqid", "int", "destairportseqid", "int"), ("destcitymarketid", "int", "destcitymarketid", "int"), ("dest", "string", "dest", "string"), ("destcityname", "string", "destcityname", "string"), ("deststate", "string", "deststate", "string"), ("deststatefips", "string", "deststatefips", "string"), ("deststatename", "string", "deststatename", "string"), ("destwac", "int", "destwac", "int"), ("crsdeptime", "string", "crsdeptime", "string"), ("deptime", "string", "deptime", "string"), ("depdelay", "int", "depdelay", "int"), ("depdelayminutes", "int", "depdelayminutes", "int"), ("depdel15", "int", "depdel15", "int"), ("departuredelaygroups", "int", "departuredelaygroups", "int"), ("deptimeblk", "string", "deptimeblk", "string"), ("taxiout", "int", "taxiout", "int"), ("wheelsoff", "string", "wheelsoff", "string"), ("wheelson", "string", "wheelson", "string"), ("taxiin", "int", "taxiin", "int"), ("crsarrtime", "int", "crsarrtime", "int"), ("arrtime", "string", "arrtime", "string"), ("arrdelay", "int", "arrdelay", "int"), ("arrdelayminutes", "int", "arrdelayminutes", "int"), ("arrdel15", "int", "arrdel15", "int"), ("arrivaldelaygroups", "int", "arrivaldelaygroups", "int"), ("arrtimeblk", "string", "arrtimeblk", "string"), ("cancelled", "int", "cancelled", "int"), ("cancellationcode", "string", "cancellationcode", "string"), ("diverted", "int", "diverted", "int"), ("crselapsedtime", "int", "crselapsedtime", "int"), ("actualelapsedtime", "int", "actualelapsedtime", "int"), ("airtime", "int", "airtime", "int"), ("flights", "int", "flights", "int"), ("distance", "int", "distance", "int"), ("distancegroup", "int", "distancegroup", "int"), ("carrierdelay", "int", "carrierdelay", "int"), ("weatherdelay", "int", "weatherdelay", "int"), ("nasdelay", "int", "nasdelay", "int"), ("securitydelay", "int", "securitydelay", "int"), ("lateaircraftdelay", "int", "lateaircraftdelay", "int"), ("firstdeptime", "string", "firstdeptime", "string"), ("totaladdgtime", "int", "totaladdgtime", "int"), ("longestaddgtime", "int", "longestaddgtime", "int"), ("divairportlandings", "int", "divairportlandings", "int"), ("divreacheddest", "int", "divreacheddest", "int"), ("divactualelapsedtime", "int", "divactualelapsedtime", "int"), ("divarrdelay", "int", "divarrdelay", "int"), ("divdistance", "int", "divdistance", "int"), ("div1airport", "string", "div1airport", "string"), ("div1airportid", "int", "div1airportid", "int"), ("div1airportseqid", "int", "div1airportseqid", "int"), ("div1wheelson", "string", "div1wheelson", "string"), ("div1totalgtime", "int", "div1totalgtime", "int"), ("div1longestgtime", "int", "div1longestgtime", "int"), ("div1wheelsoff", "string", "div1wheelsoff", "string"), ("div1tailnum", "string", "div1tailnum", "string"), ("div2airport", "string", "div2airport", "string"), ("div2airportid", "int", "div2airportid", "int"), ("div2airportseqid", "int", "div2airportseqid", "int"), ("div2wheelson", "string", "div2wheelson", "string"), ("div2totalgtime", "int", "div2totalgtime", "int"), ("div2longestgtime", "int", "div2longestgtime", "int"), ("div2wheelsoff", "string", "div2wheelsoff", "string"), ("div2tailnum", "string", "div2tailnum", "string"), ("div3airport", "string", "div3airport", "string"), ("div3airportid", "int", "div3airportid", "int"), ("div3airportseqid", "int", "div3airportseqid", "int"), ("div3wheelson", "string", "div3wheelson", "string"), ("div3totalgtime", "int", "div3totalgtime", "int"), ("div3longestgtime", "int", "div3longestgtime", "int"), ("div3wheelsoff", "string", "div3wheelsoff", "string"), ("div3tailnum", "string", "div3tailnum", "string"), ("div4airport", "string", "div4airport", "string"), ("div4airportid", "int", "div4airportid", "int"), ("div4airportseqid", "int", "div4airportseqid", "int"), ("div4wheelson", "string", "div4wheelson", "string"), ("div4totalgtime", "int", "div4totalgtime", "int"), ("div4longestgtime", "int", "div4longestgtime", "int"), ("div4wheelsoff", "string", "div4wheelsoff", "string"), ("div4tailnum", "string", "div4tailnum", "string"), ("div5airport", "string", "div5airport", "string"), ("div5airportid", "int", "div5airportid", "int"), ("div5airportseqid", "int", "div5airportseqid", "int"), ("div5wheelson", "string", "div5wheelson", "string"), ("div5totalgtime", "int", "div5totalgtime", "int"), ("div5longestgtime", "int", "div5longestgtime", "int"), ("div5wheelsoff", "string", "div5wheelsoff", "string"), ("div5tailnum", "string", "div5tailnum", "string"), ("year", "string", "year", "string")]',
                    "Param": False,
                },
                {
                    "Name": "transformation_ctx",
                    "Value": '"Transform1"',
                    "Param": False,
                },
            ],
            "LineNumber": 22,
        },
        {
            "Id": "Transform2",
            "NodeType": "FillMissingValues",
            "Args": [
                {
                    "Name": "missing_values_column",
                    "Value": '"dayofmonth"',
                    "Param": False,
                },
                {
                    "Name": "transformation_ctx",
                    "Value": '"Transform2"',
                    "Param": False,
                },
            ],
            "LineNumber": 27,
        },
        {
            "Id": "Transform3",
            "NodeType": "SelectFields",
            "Args": [
                {"Name": "paths", "Value": "[]", "Param": False},
                {
                    "Name": "transformation_ctx",
                    "Value": '"Transform3"',
                    "Param": False,
                },
            ],
            "LineNumber": 32,
        },
        {
            "Id": "DataSource0",
            "NodeType": "DataSource",
            "Args": [
                {
                    "Name": "database",
                    "Value": '"test-database"',
                    "Param": False,
                },
                {
                    "Name": "table_name",
                    "Value": '"test_parquet"',
                    "Param": False,
                },
                {
                    "Name": "transformation_ctx",
                    "Value": '"DataSource0"',
                    "Param": False,
                },
            ],
            "LineNumber": 17,
        },
        {
            "Id": "DataSink0",
            "NodeType": "DataSink",
            "Args": [
                {"Name": "connection_type", "Value": '"s3"', "Param": False},
                {"Name": "format", "Value": '"json"', "Param": False},
                {
                    "Name": "connection_options",
                    "Value": '{"path": "s3://test-glue-jsons/", "partitionKeys": []}',
                    "Param": False,
                },
                {
                    "Name": "transformation_ctx",
                    "Value": '"DataSink0"',
                    "Param": False,
                },
            ],
            "LineNumber": 37,
        },
    ],
    "DagEdges": [
        {
            "Source": "Transform1",
            "Target": "Transform0",
            "TargetParameter": "frame",
        },
        {
            "Source": "DataSource0",
            "Target": "Transform1",
            "TargetParameter": "frame",
        },
        {
            "Source": "Transform1",
            "Target": "Transform2",
            "TargetParameter": "frame",
        },
        {
            "Source": "Transform2",
            "Target": "Transform3",
            "TargetParameter": "frame",
        },
        {
            "Source": "Transform3",
            "Target": "DataSink0",
            "TargetParameter": "frame",
        },
    ],
}

get_object_body_1 = """
import sys
from awsglue.transforms import *
from awsglue.utils import getResolvedOptions
from pyspark.context import SparkContext
from awsglue.context import GlueContext
from awsglue.job import Job
import re

## @params: [JOB_NAME]
args = getResolvedOptions(sys.argv, ['JOB_NAME'])

sc = SparkContext()
glueContext = GlueContext(sc)
spark = glueContext.spark_session
job = Job(glueContext)
job.init(args['JOB_NAME'], args)
## @type: DataSource
## @args: [database = "flights-database", table_name = "avro", transformation_ctx = "DataSource0"]
## @return: DataSource0
## @inputs: []
DataSource0 = glueContext.create_dynamic_frame.from_catalog(database = "flights-database", table_name = "avro", transformation_ctx = "DataSource0")
## @type: ApplyMapping
## @args: [mappings = [("yr", "int", "yr", "int"), ("flightdate", "string", "flightdate", "string"), ("uniquecarrier", "string", "uniquecarrier", "string"), ("airlineid", "int", "airlineid", "int"), ("carrier", "string", "carrier", "string"), ("flightnum", "string", "flightnum", "string"), ("origin", "string", "origin", "string"), ("dest", "string", "dest", "string"), ("depdelay", "int", "depdelay", "int"), ("carrierdelay", "int", "carrierdelay", "int"), ("weatherdelay", "int", "weatherdelay", "int"), ("year", "string", "year", "string")], transformation_ctx = "Transform2"]
## @return: Transform2
## @inputs: [frame = DataSource0]
Transform2 = ApplyMapping.apply(frame = DataSource0, mappings = [("yr", "int", "yr", "int"), ("flightdate", "string", "flightdate", "string"), ("uniquecarrier", "string", "uniquecarrier", "string"), ("airlineid", "int", "airlineid", "int"), ("carrier", "string", "carrier", "string"), ("flightnum", "string", "flightnum", "string"), ("origin", "string", "origin", "string"), ("dest", "string", "dest", "string"), ("depdelay", "int", "depdelay", "int"), ("carrierdelay", "int", "carrierdelay", "int"), ("weatherdelay", "int", "weatherdelay", "int"), ("year", "string", "year", "string")], transformation_ctx = "Transform2")
## @type: ApplyMapping
## @args: [mappings = [("yr", "int", "yr", "int"), ("flightdate", "string", "flightdate", "string"), ("uniquecarrier", "string", "uniquecarrier", "string"), ("airlineid", "int", "airlineid", "int"), ("carrier", "string", "carrier", "string"), ("flightnum", "string", "flightnum", "string"), ("origin", "string", "origin", "string"), ("dest", "string", "dest", "string"), ("depdelay", "int", "depdelay", "int"), ("carrierdelay", "int", "carrierdelay", "int"), ("weatherdelay", "int", "weatherdelay", "int"), ("year", "string", "year", "string")], transformation_ctx = "Transform4"]
## @return: Transform4
## @inputs: [frame = Transform2]
Transform4 = ApplyMapping.apply(frame = Transform2, mappings = [("yr", "int", "yr", "int"), ("flightdate", "string", "flightdate", "string"), ("uniquecarrier", "string", "uniquecarrier", "string"), ("airlineid", "int", "airlineid", "int"), ("carrier", "string", "carrier", "string"), ("flightnum", "string", "flightnum", "string"), ("origin", "string", "origin", "string"), ("dest", "string", "dest", "string"), ("depdelay", "int", "depdelay", "int"), ("carrierdelay", "int", "carrierdelay", "int"), ("weatherdelay", "int", "weatherdelay", "int"), ("year", "string", "year", "string")], transformation_ctx = "Transform4")
## @type: Filter
## @args: [f = lambda row : (), transformation_ctx = "Transform0"]
## @return: Transform0
## @inputs: [frame = Transform2]
Transform0 = Filter.apply(frame = Transform2, f = lambda row : (), transformation_ctx = "Transform0")
## @type: ApplyMapping
## @args: [mappings = [("yr", "int", "yr", "int"), ("flightdate", "string", "flightdate", "string"), ("uniquecarrier", "string", "uniquecarrier", "string"), ("airlineid", "int", "airlineid", "int"), ("carrier", "string", "carrier", "string"), ("flightnum", "string", "flightnum", "string"), ("origin", "string", "origin", "string"), ("dest", "string", "dest", "string"), ("depdelay", "int", "depdelay", "int"), ("carrierdelay", "int", "carrierdelay", "int"), ("weatherdelay", "int", "weatherdelay", "int"), ("year", "string", "year", "string")], transformation_ctx = "Transform1"]
## @return: Transform1
## @inputs: [frame = Transform0]
Transform1 = ApplyMapping.apply(frame = Transform0, mappings = [("yr", "int", "yr", "int"), ("flightdate", "string", "flightdate", "string"), ("uniquecarrier", "string", "uniquecarrier", "string"), ("airlineid", "int", "airlineid", "int"), ("carrier", "string", "carrier", "string"), ("flightnum", "string", "flightnum", "string"), ("origin", "string", "origin", "string"), ("dest", "string", "dest", "string"), ("depdelay", "int", "depdelay", "int"), ("carrierdelay", "int", "carrierdelay", "int"), ("weatherdelay", "int", "weatherdelay", "int"), ("year", "string", "year", "string")], transformation_ctx = "Transform1")
## @type: ApplyMapping
## @args: [mappings = [("yr", "int", "(right) yr", "int"), ("flightdate", "string", "(right) flightdate", "string"), ("uniquecarrier", "string", "(right) uniquecarrier", "string"), ("airlineid", "int", "(right) airlineid", "int"), ("carrier", "string", "(right) carrier", "string"), ("flightnum", "string", "(right) flightnum", "string"), ("origin", "string", "(right) origin", "string"), ("dest", "string", "(right) dest", "string"), ("depdelay", "int", "(right) depdelay", "int"), ("carrierdelay", "int", "(right) carrierdelay", "int"), ("weatherdelay", "int", "(right) weatherdelay", "int"), ("year", "string", "(right) year", "string")], transformation_ctx = "Transform5"]
## @return: Transform5
## @inputs: [frame = Transform1]
Transform5 = ApplyMapping.apply(frame = Transform1, mappings = [("yr", "int", "(right) yr", "int"), ("flightdate", "string", "(right) flightdate", "string"), ("uniquecarrier", "string", "(right) uniquecarrier", "string"), ("airlineid", "int", "(right) airlineid", "int"), ("carrier", "string", "(right) carrier", "string"), ("flightnum", "string", "(right) flightnum", "string"), ("origin", "string", "(right) origin", "string"), ("dest", "string", "(right) dest", "string"), ("depdelay", "int", "(right) depdelay", "int"), ("carrierdelay", "int", "(right) carrierdelay", "int"), ("weatherdelay", "int", "(right) weatherdelay", "int"), ("year", "string", "(right) year", "string")], transformation_ctx = "Transform5")
## @type: Join
## @args: [keys2 = ["(right) flightdate"], keys1 = ["yr"], transformation_ctx = "Transform3"]
## @return: Transform3
## @inputs: [frame1 = Transform4, frame2 = Transform5]
Transform3 = Join.apply(frame1 = Transform4, frame2 = Transform5, keys2 = ["(right) flightdate"], keys1 = ["yr"], transformation_ctx = "Transform3")
## @type: DataSink
## @args: [connection_type = "s3", format = "json", connection_options = {"path": "s3://test-glue-jsons/", "partitionKeys": []}, transformation_ctx = "DataSink1"]
## @return: DataSink1
## @inputs: [frame = Transform3]
DataSink1 = glueContext.write_dynamic_frame.from_options(frame = Transform3, connection_type = "s3", format = "json", connection_options = {"path": "s3://test-glue-jsons/", "partitionKeys": []}, transformation_ctx = "DataSink1")
## @type: DataSink
## @args: [database = "test-database", table_name = "test_jsons_markers", transformation_ctx = "DataSink0"]
## @return: DataSink0
## @inputs: [frame = Transform3]
DataSink0 = glueContext.write_dynamic_frame.from_catalog(frame = Transform3, database = "test-database", table_name = "test_jsons_markers", transformation_ctx = "DataSink0")
job.commit()
"""

get_object_body_2 = """
import sys
from awsglue.transforms import *
from awsglue.utils import getResolvedOptions
from pyspark.context import SparkContext
from awsglue.context import GlueContext
from awsglue.job import Job
from awsglueml.transforms import FillMissingValues

## @params: [JOB_NAME]
args = getResolvedOptions(sys.argv, ['JOB_NAME'])

sc = SparkContext()
glueContext = GlueContext(sc)
spark = glueContext.spark_session
job = Job(glueContext)
job.init(args['JOB_NAME'], args)
## @type: DataSource
## @args: [database = "test-database", table_name = "test_parquet", transformation_ctx = "DataSource0"]
## @return: DataSource0
## @inputs: []
DataSource0 = glueContext.create_dynamic_frame.from_catalog(database = "test-database", table_name = "test_parquet", transformation_ctx = "DataSource0")
## @type: ApplyMapping
## @args: [mappings = [("yr", "int", "yr", "int"), ("quarter", "int", "quarter", "int"), ("month", "int", "month", "int"), ("dayofmonth", "int", "dayofmonth", "int"), ("dayofweek", "int", "dayofweek", "int"), ("flightdate", "string", "flightdate", "string"), ("uniquecarrier", "string", "uniquecarrier", "string"), ("airlineid", "int", "airlineid", "int"), ("carrier", "string", "carrier", "string"), ("tailnum", "string", "tailnum", "string"), ("flightnum", "string", "flightnum", "string"), ("originairportid", "int", "originairportid", "int"), ("originairportseqid", "int", "originairportseqid", "int"), ("origincitymarketid", "int", "origincitymarketid", "int"), ("origin", "string", "origin", "string"), ("origincityname", "string", "origincityname", "string"), ("originstate", "string", "originstate", "string"), ("originstatefips", "string", "originstatefips", "string"), ("originstatename", "string", "originstatename", "string"), ("originwac", "int", "originwac", "int"), ("destairportid", "int", "destairportid", "int"), ("destairportseqid", "int", "destairportseqid", "int"), ("destcitymarketid", "int", "destcitymarketid", "int"), ("dest", "string", "dest", "string"), ("destcityname", "string", "destcityname", "string"), ("deststate", "string", "deststate", "string"), ("deststatefips", "string", "deststatefips", "string"), ("deststatename", "string", "deststatename", "string"), ("destwac", "int", "destwac", "int"), ("crsdeptime", "string", "crsdeptime", "string"), ("deptime", "string", "deptime", "string"), ("depdelay", "int", "depdelay", "int"), ("depdelayminutes", "int", "depdelayminutes", "int"), ("depdel15", "int", "depdel15", "int"), ("departuredelaygroups", "int", "departuredelaygroups", "int"), ("deptimeblk", "string", "deptimeblk", "string"), ("taxiout", "int", "taxiout", "int"), ("wheelsoff", "string", "wheelsoff", "string"), ("wheelson", "string", "wheelson", "string"), ("taxiin", "int", "taxiin", "int"), ("crsarrtime", "int", "crsarrtime", "int"), ("arrtime", "string", "arrtime", "string"), ("arrdelay", "int", "arrdelay", "int"), ("arrdelayminutes", "int", "arrdelayminutes", "int"), ("arrdel15", "int", "arrdel15", "int"), ("arrivaldelaygroups", "int", "arrivaldelaygroups", "int"), ("arrtimeblk", "string", "arrtimeblk", "string"), ("cancelled", "int", "cancelled", "int"), ("cancellationcode", "string", "cancellationcode", "string"), ("diverted", "int", "diverted", "int"), ("crselapsedtime", "int", "crselapsedtime", "int"), ("actualelapsedtime", "int", "actualelapsedtime", "int"), ("airtime", "int", "airtime", "int"), ("flights", "int", "flights", "int"), ("distance", "int", "distance", "int"), ("distancegroup", "int", "distancegroup", "int"), ("carrierdelay", "int", "carrierdelay", "int"), ("weatherdelay", "int", "weatherdelay", "int"), ("nasdelay", "int", "nasdelay", "int"), ("securitydelay", "int", "securitydelay", "int"), ("lateaircraftdelay", "int", "lateaircraftdelay", "int"), ("firstdeptime", "string", "firstdeptime", "string"), ("totaladdgtime", "int", "totaladdgtime", "int"), ("longestaddgtime", "int", "longestaddgtime", "int"), ("divairportlandings", "int", "divairportlandings", "int"), ("divreacheddest", "int", "divreacheddest", "int"), ("divactualelapsedtime", "int", "divactualelapsedtime", "int"), ("divarrdelay", "int", "divarrdelay", "int"), ("divdistance", "int", "divdistance", "int"), ("div1airport", "string", "div1airport", "string"), ("div1airportid", "int", "div1airportid", "int"), ("div1airportseqid", "int", "div1airportseqid", "int"), ("div1wheelson", "string", "div1wheelson", "string"), ("div1totalgtime", "int", "div1totalgtime", "int"), ("div1longestgtime", "int", "div1longestgtime", "int"), ("div1wheelsoff", "string", "div1wheelsoff", "string"), ("div1tailnum", "string", "div1tailnum", "string"), ("div2airport", "string", "div2airport", "string"), ("div2airportid", "int", "div2airportid", "int"), ("div2airportseqid", "int", "div2airportseqid", "int"), ("div2wheelson", "string", "div2wheelson", "string"), ("div2totalgtime", "int", "div2totalgtime", "int"), ("div2longestgtime", "int", "div2longestgtime", "int"), ("div2wheelsoff", "string", "div2wheelsoff", "string"), ("div2tailnum", "string", "div2tailnum", "string"), ("div3airport", "string", "div3airport", "string"), ("div3airportid", "int", "div3airportid", "int"), ("div3airportseqid", "int", "div3airportseqid", "int"), ("div3wheelson", "string", "div3wheelson", "string"), ("div3totalgtime", "int", "div3totalgtime", "int"), ("div3longestgtime", "int", "div3longestgtime", "int"), ("div3wheelsoff", "string", "div3wheelsoff", "string"), ("div3tailnum", "string", "div3tailnum", "string"), ("div4airport", "string", "div4airport", "string"), ("div4airportid", "int", "div4airportid", "int"), ("div4airportseqid", "int", "div4airportseqid", "int"), ("div4wheelson", "string", "div4wheelson", "string"), ("div4totalgtime", "int", "div4totalgtime", "int"), ("div4longestgtime", "int", "div4longestgtime", "int"), ("div4wheelsoff", "string", "div4wheelsoff", "string"), ("div4tailnum", "string", "div4tailnum", "string"), ("div5airport", "string", "div5airport", "string"), ("div5airportid", "int", "div5airportid", "int"), ("div5airportseqid", "int", "div5airportseqid", "int"), ("div5wheelson", "string", "div5wheelson", "string"), ("div5totalgtime", "int", "div5totalgtime", "int"), ("div5longestgtime", "int", "div5longestgtime", "int"), ("div5wheelsoff", "string", "div5wheelsoff", "string"), ("div5tailnum", "string", "div5tailnum", "string"), ("year", "string", "year", "string")], transformation_ctx = "Transform1"]
## @return: Transform1
## @inputs: [frame = DataSource0]
Transform1 = ApplyMapping.apply(frame = DataSource0, mappings = [("yr", "int", "yr", "int"), ("quarter", "int", "quarter", "int"), ("month", "int", "month", "int"), ("dayofmonth", "int", "dayofmonth", "int"), ("dayofweek", "int", "dayofweek", "int"), ("flightdate", "string", "flightdate", "string"), ("uniquecarrier", "string", "uniquecarrier", "string"), ("airlineid", "int", "airlineid", "int"), ("carrier", "string", "carrier", "string"), ("tailnum", "string", "tailnum", "string"), ("flightnum", "string", "flightnum", "string"), ("originairportid", "int", "originairportid", "int"), ("originairportseqid", "int", "originairportseqid", "int"), ("origincitymarketid", "int", "origincitymarketid", "int"), ("origin", "string", "origin", "string"), ("origincityname", "string", "origincityname", "string"), ("originstate", "string", "originstate", "string"), ("originstatefips", "string", "originstatefips", "string"), ("originstatename", "string", "originstatename", "string"), ("originwac", "int", "originwac", "int"), ("destairportid", "int", "destairportid", "int"), ("destairportseqid", "int", "destairportseqid", "int"), ("destcitymarketid", "int", "destcitymarketid", "int"), ("dest", "string", "dest", "string"), ("destcityname", "string", "destcityname", "string"), ("deststate", "string", "deststate", "string"), ("deststatefips", "string", "deststatefips", "string"), ("deststatename", "string", "deststatename", "string"), ("destwac", "int", "destwac", "int"), ("crsdeptime", "string", "crsdeptime", "string"), ("deptime", "string", "deptime", "string"), ("depdelay", "int", "depdelay", "int"), ("depdelayminutes", "int", "depdelayminutes", "int"), ("depdel15", "int", "depdel15", "int"), ("departuredelaygroups", "int", "departuredelaygroups", "int"), ("deptimeblk", "string", "deptimeblk", "string"), ("taxiout", "int", "taxiout", "int"), ("wheelsoff", "string", "wheelsoff", "string"), ("wheelson", "string", "wheelson", "string"), ("taxiin", "int", "taxiin", "int"), ("crsarrtime", "int", "crsarrtime", "int"), ("arrtime", "string", "arrtime", "string"), ("arrdelay", "int", "arrdelay", "int"), ("arrdelayminutes", "int", "arrdelayminutes", "int"), ("arrdel15", "int", "arrdel15", "int"), ("arrivaldelaygroups", "int", "arrivaldelaygroups", "int"), ("arrtimeblk", "string", "arrtimeblk", "string"), ("cancelled", "int", "cancelled", "int"), ("cancellationcode", "string", "cancellationcode", "string"), ("diverted", "int", "diverted", "int"), ("crselapsedtime", "int", "crselapsedtime", "int"), ("actualelapsedtime", "int", "actualelapsedtime", "int"), ("airtime", "int", "airtime", "int"), ("flights", "int", "flights", "int"), ("distance", "int", "distance", "int"), ("distancegroup", "int", "distancegroup", "int"), ("carrierdelay", "int", "carrierdelay", "int"), ("weatherdelay", "int", "weatherdelay", "int"), ("nasdelay", "int", "nasdelay", "int"), ("securitydelay", "int", "securitydelay", "int"), ("lateaircraftdelay", "int", "lateaircraftdelay", "int"), ("firstdeptime", "string", "firstdeptime", "string"), ("totaladdgtime", "int", "totaladdgtime", "int"), ("longestaddgtime", "int", "longestaddgtime", "int"), ("divairportlandings", "int", "divairportlandings", "int"), ("divreacheddest", "int", "divreacheddest", "int"), ("divactualelapsedtime", "int", "divactualelapsedtime", "int"), ("divarrdelay", "int", "divarrdelay", "int"), ("divdistance", "int", "divdistance", "int"), ("div1airport", "string", "div1airport", "string"), ("div1airportid", "int", "div1airportid", "int"), ("div1airportseqid", "int", "div1airportseqid", "int"), ("div1wheelson", "string", "div1wheelson", "string"), ("div1totalgtime", "int", "div1totalgtime", "int"), ("div1longestgtime", "int", "div1longestgtime", "int"), ("div1wheelsoff", "string", "div1wheelsoff", "string"), ("div1tailnum", "string", "div1tailnum", "string"), ("div2airport", "string", "div2airport", "string"), ("div2airportid", "int", "div2airportid", "int"), ("div2airportseqid", "int", "div2airportseqid", "int"), ("div2wheelson", "string", "div2wheelson", "string"), ("div2totalgtime", "int", "div2totalgtime", "int"), ("div2longestgtime", "int", "div2longestgtime", "int"), ("div2wheelsoff", "string", "div2wheelsoff", "string"), ("div2tailnum", "string", "div2tailnum", "string"), ("div3airport", "string", "div3airport", "string"), ("div3airportid", "int", "div3airportid", "int"), ("div3airportseqid", "int", "div3airportseqid", "int"), ("div3wheelson", "string", "div3wheelson", "string"), ("div3totalgtime", "int", "div3totalgtime", "int"), ("div3longestgtime", "int", "div3longestgtime", "int"), ("div3wheelsoff", "string", "div3wheelsoff", "string"), ("div3tailnum", "string", "div3tailnum", "string"), ("div4airport", "string", "div4airport", "string"), ("div4airportid", "int", "div4airportid", "int"), ("div4airportseqid", "int", "div4airportseqid", "int"), ("div4wheelson", "string", "div4wheelson", "string"), ("div4totalgtime", "int", "div4totalgtime", "int"), ("div4longestgtime", "int", "div4longestgtime", "int"), ("div4wheelsoff", "string", "div4wheelsoff", "string"), ("div4tailnum", "string", "div4tailnum", "string"), ("div5airport", "string", "div5airport", "string"), ("div5airportid", "int", "div5airportid", "int"), ("div5airportseqid", "int", "div5airportseqid", "int"), ("div5wheelson", "string", "div5wheelson", "string"), ("div5totalgtime", "int", "div5totalgtime", "int"), ("div5longestgtime", "int", "div5longestgtime", "int"), ("div5wheelsoff", "string", "div5wheelsoff", "string"), ("div5tailnum", "string", "div5tailnum", "string"), ("year", "string", "year", "string")], transformation_ctx = "Transform1")
## @type: FillMissingValues
## @args: [missing_values_column = "dayofmonth", transformation_ctx = "Transform2"]
## @return: Transform2
## @inputs: [frame = Transform1]
Transform2 = FillMissingValues.apply(frame = Transform1, missing_values_column = "dayofmonth", transformation_ctx = "Transform2")
## @type: SelectFields
## @args: [paths = [], transformation_ctx = "Transform3"]
## @return: Transform3
## @inputs: [frame = Transform2]
Transform3 = SelectFields.apply(frame = Transform2, paths = [], transformation_ctx = "Transform3")
## @type: DataSink
## @args: [connection_type = "s3", format = "json", connection_options = {"path": "s3://test-glue-jsons/", "partitionKeys": []}, transformation_ctx = "DataSink0"]
## @return: DataSink0
## @inputs: [frame = Transform3]
DataSink0 = glueContext.write_dynamic_frame.from_options(frame = Transform3, connection_type = "s3", format = "json", connection_options = {"path": "s3://test-glue-jsons/", "partitionKeys": []}, transformation_ctx = "DataSink0")
## @type: SplitFields
## @args: [paths = ["yr", "quarter", "month", "dayofmonth", "dayofweek", "flightdate", "uniquecarrier", "airlineid", "carrier", "tailnum", "flightnum", "originairportid", "originairportseqid", "origincitymarketid", "origin", "origincityname", "originstate", "originstatefips", "originstatename", "originwac", "destairportid", "destairportseqid", "destcitymarketid", "dest", "destcityname", "deststate", "deststatefips", "deststatename", "destwac", "crsdeptime", "deptime", "depdelay", "depdelayminutes", "depdel15", "departuredelaygroups", "deptimeblk", "taxiout", "wheelsoff", "wheelson", "taxiin", "crsarrtime", "arrtime", "arrdelay", "arrdelayminutes", "arrdel15", "arrivaldelaygroups", "arrtimeblk", "cancelled", "cancellationcode", "diverted", "crselapsedtime", "actualelapsedtime", "airtime", "flights", "distance", "distancegroup", "carrierdelay", "weatherdelay", "nasdelay", "securitydelay", "lateaircraftdelay", "firstdeptime", "totaladdgtime", "longestaddgtime", "divairportlandings", "divreacheddest", "divactualelapsedtime", "divarrdelay", "divdistance", "div1airport", "div1airportid", "div1airportseqid", "div1wheelson", "div1totalgtime", "div1longestgtime", "div1wheelsoff", "div1tailnum", "div2airport", "div2airportid", "div2airportseqid", "div2wheelson", "div2totalgtime", "div2longestgtime", "div2wheelsoff", "div2tailnum", "div3airport", "div3airportid", "div3airportseqid", "div3wheelson", "div3totalgtime", "div3longestgtime", "div3wheelsoff", "div3tailnum", "div4airport", "div4airportid", "div4airportseqid", "div4wheelson", "div4totalgtime", "div4longestgtime", "div4wheelsoff", "div4tailnum", "div5airport", "div5airportid", "div5airportseqid", "div5wheelson", "div5totalgtime", "div5longestgtime", "div5wheelsoff", "div5tailnum", "year"], name2 = "Transform0Output1", name1 = "Transform0Output0", transformation_ctx = "Transform0"]
## @return: Transform0
## @inputs: [frame = Transform1]
Transform0 = SplitFields.apply(frame = Transform1, paths = ["yr", "quarter", "month", "dayofmonth", "dayofweek", "flightdate", "uniquecarrier", "airlineid", "carrier", "tailnum", "flightnum", "originairportid", "originairportseqid", "origincitymarketid", "origin", "origincityname", "originstate", "originstatefips", "originstatename", "originwac", "destairportid", "destairportseqid", "destcitymarketid", "dest", "destcityname", "deststate", "deststatefips", "deststatename", "destwac", "crsdeptime", "deptime", "depdelay", "depdelayminutes", "depdel15", "departuredelaygroups", "deptimeblk", "taxiout", "wheelsoff", "wheelson", "taxiin", "crsarrtime", "arrtime", "arrdelay", "arrdelayminutes", "arrdel15", "arrivaldelaygroups", "arrtimeblk", "cancelled", "cancellationcode", "diverted", "crselapsedtime", "actualelapsedtime", "airtime", "flights", "distance", "distancegroup", "carrierdelay", "weatherdelay", "nasdelay", "securitydelay", "lateaircraftdelay", "firstdeptime", "totaladdgtime", "longestaddgtime", "divairportlandings", "divreacheddest", "divactualelapsedtime", "divarrdelay", "divdistance", "div1airport", "div1airportid", "div1airportseqid", "div1wheelson", "div1totalgtime", "div1longestgtime", "div1wheelsoff", "div1tailnum", "div2airport", "div2airportid", "div2airportseqid", "div2wheelson", "div2totalgtime", "div2longestgtime", "div2wheelsoff", "div2tailnum", "div3airport", "div3airportid", "div3airportseqid", "div3wheelson", "div3totalgtime", "div3longestgtime", "div3wheelsoff", "div3tailnum", "div4airport", "div4airportid", "div4airportseqid", "div4wheelson", "div4totalgtime", "div4longestgtime", "div4wheelsoff", "div4tailnum", "div5airport", "div5airportid", "div5airportseqid", "div5wheelson", "div5totalgtime", "div5longestgtime", "div5wheelsoff", "div5tailnum", "year"], name2 = "Transform0Output1", name1 = "Transform0Output0", transformation_ctx = "Transform0")
job.commit()
"""


def mock_get_object_response(raw_body: str):
    """
    Mock s3 client get_object() response object.

    See https://gist.github.com/grantcooksey/132ddc85274a50b94b821302649f9d7b

    Parameters
    ----------
        raw_body:
            Content of the 'Body' field to return
    """

    encoded_message = raw_body.encode("utf-8")
    raw_stream = StreamingBody(io.BytesIO(encoded_message), len(encoded_message))

    return {"Body": raw_stream}


get_object_response_1 = mock_get_object_response(get_object_body_1)
get_object_response_2 = mock_get_object_response(get_object_body_2)

s3 = botocore.session.get_session().create_client("s3")
s3_stubber = Stubber(s3)
