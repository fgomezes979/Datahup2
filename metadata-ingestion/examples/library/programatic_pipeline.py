from datahub.ingestion.run.pipeline import Pipeline

# The pipeline configuration is similar to the recipe YAML files provided to the CLI tool.
pipeline = Pipeline.create(
    {
        "source": {
            "type": "mysql",
            "config": {
                "username": "root",
                "password": "root",
                "database": "source",
                "host_port": "localhost:3307",
                "schema_pattern": {
                    "deny": ["information_schema", "sys", "mysql", "performance_schema"]
                },
                "table_pattern": {
                    "allow": ["source.compras"]
                },
            },
        },
        "sink": {
            "type": "datahub-kafka",
            "config": {
                "connection": {
                    "bootstrap": "localhost:9092"}
            },
        },
    }
)

# Run the pipeline and report the results.
pipeline.run()
pipeline.pretty_print_summary()


