package com.linkedin.datahub.graphql.types.glossary.mappers;

import static com.linkedin.metadata.Constants.*;

import com.linkedin.common.Deprecation;
import com.linkedin.common.InstitutionalMemory;
import com.linkedin.common.Ownership;
import com.linkedin.common.urn.Urn;
import com.linkedin.data.DataMap;
import com.linkedin.data.template.RecordTemplate;
import com.linkedin.datahub.graphql.generated.EntityType;
import com.linkedin.datahub.graphql.generated.GlossaryTerm;
import com.linkedin.datahub.graphql.types.common.mappers.DeprecationMapper;
import com.linkedin.datahub.graphql.types.common.mappers.InstitutionalMemoryMapper;
import com.linkedin.datahub.graphql.types.common.mappers.OwnershipMapper;
import com.linkedin.datahub.graphql.types.common.mappers.util.MappingHelper;
import com.linkedin.datahub.graphql.types.domain.DomainAssociationMapper;
import com.linkedin.datahub.graphql.types.glossary.GlossaryTermUtils;
import com.linkedin.datahub.graphql.types.mappers.ModelMapper;
import com.linkedin.datahub.graphql.types.structuredproperty.StructuredPropertiesMapper;
import com.linkedin.domain.Domains;
import com.linkedin.entity.EntityResponse;
import com.linkedin.entity.EnvelopedAspectMap;
import com.linkedin.glossary.GlossaryTermInfo;
import com.linkedin.metadata.key.GlossaryTermKey;
import com.linkedin.structured.StructuredProperties;
import javax.annotation.Nonnull;

/**
 * Maps Pegasus {@link RecordTemplate} objects to objects conforming to the GQL schema.
 *
 * <p>To be replaced by auto-generated mappers implementations
 */
public class GlossaryTermMapper implements ModelMapper<EntityResponse, GlossaryTerm> {

  public static final GlossaryTermMapper INSTANCE = new GlossaryTermMapper();

  public static GlossaryTerm map(@Nonnull final EntityResponse entityResponse) {
    return INSTANCE.apply(entityResponse);
  }

  @Override
  public GlossaryTerm apply(@Nonnull final EntityResponse entityResponse) {
    GlossaryTerm result = new GlossaryTerm();
    Urn entityUrn = entityResponse.getUrn();

    result.setUrn(entityResponse.getUrn().toString());
    result.setType(EntityType.GLOSSARY_TERM);
    final String legacyName =
        GlossaryTermUtils.getGlossaryTermName(entityResponse.getUrn().getId());

    EnvelopedAspectMap aspectMap = entityResponse.getAspects();
    MappingHelper<GlossaryTerm> mappingHelper = new MappingHelper<>(aspectMap, result);
    mappingHelper.mapToResult(GLOSSARY_TERM_KEY_ASPECT_NAME, this::mapGlossaryTermKey);
    mappingHelper.mapToResult(
        GLOSSARY_TERM_INFO_ASPECT_NAME,
        (glossaryTerm, dataMap) ->
            glossaryTerm.setGlossaryTermInfo(
                GlossaryTermInfoMapper.map(new GlossaryTermInfo(dataMap), entityUrn)));
    mappingHelper.mapToResult(
        GLOSSARY_TERM_INFO_ASPECT_NAME,
        (glossaryTerm, dataMap) ->
            glossaryTerm.setProperties(
                GlossaryTermPropertiesMapper.map(new GlossaryTermInfo(dataMap), entityUrn)));
    mappingHelper.mapToResult(
        OWNERSHIP_ASPECT_NAME,
        (glossaryTerm, dataMap) ->
            glossaryTerm.setOwnership(OwnershipMapper.map(new Ownership(dataMap), entityUrn)));
    mappingHelper.mapToResult(DOMAINS_ASPECT_NAME, this::mapDomains);
    mappingHelper.mapToResult(
        DEPRECATION_ASPECT_NAME,
        (glossaryTerm, dataMap) ->
            glossaryTerm.setDeprecation(DeprecationMapper.map(new Deprecation(dataMap))));
    mappingHelper.mapToResult(
        INSTITUTIONAL_MEMORY_ASPECT_NAME,
        (dataset, dataMap) ->
            dataset.setInstitutionalMemory(
                InstitutionalMemoryMapper.map(new InstitutionalMemory(dataMap), entityUrn)));
    mappingHelper.mapToResult(
        STRUCTURED_PROPERTIES_ASPECT_NAME,
        ((entity, dataMap) ->
            entity.setStructuredProperties(
                StructuredPropertiesMapper.map(new StructuredProperties(dataMap)))));

    // If there's no name property, resort to the legacy name computation.
    if (result.getGlossaryTermInfo() != null && result.getGlossaryTermInfo().getName() == null) {
      result.getGlossaryTermInfo().setName(legacyName);
    }
    if (result.getProperties() != null && result.getProperties().getName() == null) {
      result.getProperties().setName(legacyName);
    }
    return mappingHelper.getResult();
  }

  private void mapGlossaryTermKey(@Nonnull GlossaryTerm glossaryTerm, @Nonnull DataMap dataMap) {
    GlossaryTermKey glossaryTermKey = new GlossaryTermKey(dataMap);
    glossaryTerm.setName(GlossaryTermUtils.getGlossaryTermName(glossaryTermKey.getName()));
    glossaryTerm.setHierarchicalName(glossaryTermKey.getName());
  }

  private void mapDomains(@Nonnull GlossaryTerm glossaryTerm, @Nonnull DataMap dataMap) {
    final Domains domains = new Domains(dataMap);
    glossaryTerm.setDomain(DomainAssociationMapper.map(domains, glossaryTerm.getUrn()));
  }
}
