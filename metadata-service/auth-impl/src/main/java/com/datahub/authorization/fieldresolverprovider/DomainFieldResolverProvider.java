package com.datahub.authorization.fieldresolverprovider;

import com.datahub.authentication.Authentication;
import com.datahub.authorization.FieldResolver;
import com.datahub.authorization.ResourceFieldType;
import com.datahub.authorization.ResourceSpec;
import com.linkedin.common.urn.Urn;
import com.linkedin.common.urn.UrnUtils;
import com.linkedin.domain.Domains;
import com.linkedin.entity.EntityResponse;
import com.linkedin.entity.EnvelopedAspect;
import com.linkedin.entity.client.EntityClient;
import java.util.Collections;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.linkedin.metadata.Constants.*;


/**
 * Provides field resolver for domain given resourceSpec
 */
@Slf4j
@RequiredArgsConstructor
public class DomainFieldResolverProvider implements ResourceFieldResolverProvider {

  private final EntityClient _entityClient;
  private final Authentication _systemAuthentication;

  @Override
  public ResourceFieldType getFieldType() {
    return ResourceFieldType.DOMAIN;
  }

  @Override
  public FieldResolver getFieldResolver(ResourceSpec resourceSpec) {
    return FieldResolver.getResolverFromFunction(resourceSpec, this::getDomains);
  }

  private FieldResolver.FieldValue getDomains(ResourceSpec resourceSpec) {
    Urn entityUrn = UrnUtils.getUrn(resourceSpec.getResource());
    // In the case that the entity is a domain, the associated domain is the domain itself
    if (entityUrn.getEntityType().equals(DOMAIN_ENTITY_NAME)) {
      return FieldResolver.FieldValue.builder()
          .values(Collections.singleton(entityUrn.toString()))
          .build();
    }

    EnvelopedAspect domainsAspect;
    try {
      EntityResponse response = _entityClient.getV2(entityUrn.getEntityType(), entityUrn,
          Collections.singleton(DOMAINS_ASPECT_NAME), _systemAuthentication);
      if (response == null || !response.getAspects().containsKey(DOMAINS_ASPECT_NAME)) {
        return FieldResolver.emptyFieldValue();
      }
      domainsAspect = response.getAspects().get(DOMAINS_ASPECT_NAME);
    } catch (Exception e) {
      log.error("Error while retrieving domains aspect for urn {}", entityUrn, e);
      return FieldResolver.emptyFieldValue();
    }
    Domains domains = new Domains(domainsAspect.getValue().data());
    return FieldResolver.FieldValue.builder()
        .values(domains.getDomains().stream().map(Object::toString).collect(Collectors.toSet()))
        .build();
  }
}
