from typing import Callable, List, Optional, Union

from datahub.configuration.common import (
    ConfigurationError,
    KeyValuePattern,
    TransformerSemantics,
    TransformerSemanticsConfigModel,
)
from datahub.configuration.import_resolver import pydantic_resolve_key
from datahub.emitter.mce_builder import Aspect
from datahub.ingestion.api.common import PipelineContext
from datahub.ingestion.graph.client import DataHubGraph
from datahub.ingestion.transformer.dataset_transformer import DatasetDomainTransformer
from datahub.metadata.schema_classes import DomainsClass
from datahub.utilities.registries.domain_registry import DomainRegistry


class AddDatasetDomainSemanticsConfig(TransformerSemanticsConfigModel):
    get_domains_to_add: Union[
        Callable[[str], DomainsClass],
        Callable[[str], DomainsClass],
    ]

    _resolve_domain_fn = pydantic_resolve_key("get_domains_to_add")


class SimpleDatasetDomainSemanticsConfig(TransformerSemanticsConfigModel):
    domain_urns: List[str]


class PatternDatasetDomainSemanticsConfig(TransformerSemanticsConfigModel):
    domain_pattern: KeyValuePattern = KeyValuePattern.all()


class AddDatasetDomain(DatasetDomainTransformer):
    """Transformer that adds domains to datasets according to a callback function."""

    ctx: PipelineContext
    config: AddDatasetDomainSemanticsConfig

    def __init__(self, config: AddDatasetDomainSemanticsConfig, ctx: PipelineContext):
        super().__init__()
        self.ctx = ctx
        self.config = config

    @classmethod
    def create(cls, config_dict: dict, ctx: PipelineContext) -> "AddDatasetDomain":
        config = AddDatasetDomainSemanticsConfig.parse_obj(config_dict)
        return cls(config, ctx)

    @staticmethod
    def get_domain_class(
        graph: Optional[DataHubGraph], domains: List[str]
    ) -> DomainsClass:
        domain_registry: DomainRegistry = DomainRegistry(
            cached_domains=[k for k in domains], graph=graph
        )
        domain_class = DomainsClass(
            domains=[domain_registry.get_domain_urn(domain) for domain in domains]
        )
        return domain_class

    @staticmethod
    def get_domains_to_set(
        graph: DataHubGraph, urn: str, mce_domain: Optional[DomainsClass]
    ) -> Optional[DomainsClass]:
        if not mce_domain or not mce_domain.domains:
            # nothing to add, no need to consult server
            return None

        server_domain = graph.get_domain(entity_urn=urn)
        if server_domain:
            # compute patch
            # we only include domain who are not present in the server domain list
            domains_to_add: List[str] = []
            for domain in mce_domain.domains:
                if domain not in server_domain.domains:
                    domains_to_add.append(domain)

            mce_domain.domains.extend(server_domain.domains)
            mce_domain.domains.extend(domains_to_add)

        return mce_domain

    def transform_aspect(
        self, entity_urn: str, aspect_name: str, aspect: Optional[Aspect]
    ) -> Optional[Aspect]:

        domain_aspect = DomainsClass(domains=[])
        # Check if we have received existing aspect
        if aspect is not None:
            domain_aspect.domains.extend(aspect.domains)  # type: ignore[attr-defined]

        domain_to_add = self.config.get_domains_to_add(entity_urn)

        domain_aspect.domains.extend(domain_to_add.domains)

        if self.config.semantics == TransformerSemantics.PATCH:
            assert self.ctx.graph
            domain_aspect = AddDatasetDomain.get_domains_to_set(
                self.ctx.graph, entity_urn, domain_aspect
            )  # type: ignore[assignment]
        # ignore mypy errors as Aspect is not a concrete class
        return domain_aspect  # type: ignore[return-value]


class SimpleAddDatasetDomain(AddDatasetDomain):
    """Transformer that adds a specified set of domains to each dataset."""

    def __init__(
        self, config: SimpleDatasetDomainSemanticsConfig, ctx: PipelineContext
    ):
        if ctx.graph is None:
            raise ConfigurationError(
                "AddDatasetDomain requires a datahub_api to connect to. Consider using the datahub-rest sink or provide a datahub_api: configuration on your ingestion recipe"
            )

        domains = AddDatasetDomain.get_domain_class(ctx.graph, config.domain_urns)
        generic_config = AddDatasetDomainSemanticsConfig(
            get_domains_to_add=lambda _: domains,
            semantics=config.semantics,
        )
        super().__init__(generic_config, ctx)

    @classmethod
    def create(
        cls, config_dict: dict, ctx: PipelineContext
    ) -> "SimpleAddDatasetDomain":
        config = SimpleDatasetDomainSemanticsConfig.parse_obj(config_dict)
        return cls(config, ctx)


class PatternAddDatasetDomain(AddDatasetDomain):
    """Transformer that adds a specified set of domains to each dataset."""

    def __init__(
        self, config: PatternDatasetDomainSemanticsConfig, ctx: PipelineContext
    ):
        if ctx.graph is None:
            raise ConfigurationError(
                "AddDatasetDomain requires a datahub_api to connect to. Consider using the datahub-rest sink or provide a datahub_api: configuration on your ingestion recipe"
            )

        domain_pattern = config.domain_pattern

        def resolve_domain(domain_urn: str) -> DomainsClass:
            domains = domain_pattern.value(domain_urn)
            return self.get_domain_class(ctx.graph, domains)

        generic_config = AddDatasetDomainSemanticsConfig(
            get_domains_to_add=resolve_domain,
            semantics=config.semantics,
        )
        super().__init__(generic_config, ctx)

    @classmethod
    def create(
        cls, config_dict: dict, ctx: PipelineContext
    ) -> "PatternAddDatasetDomain":
        config = PatternDatasetDomainSemanticsConfig.parse_obj(config_dict)
        return cls(config, ctx)
