package com.linkedin.metadata.entity;

import com.google.common.collect.ImmutableSet;
import com.linkedin.common.UrnArray;
import com.linkedin.common.urn.Urn;
import com.linkedin.data.template.RecordTemplate;
import com.linkedin.entity.Aspect;
import com.linkedin.entity.EntityResponse;
import com.linkedin.events.metadata.ChangeType;
import com.linkedin.metadata.query.ListResult;
import com.linkedin.metadata.search.SearchEntity;
import com.linkedin.metadata.search.SearchResult;
import com.linkedin.metadata.utils.EntityKeyUtils;
import com.linkedin.metadata.utils.GenericRecordUtils;
import com.linkedin.mxe.GenericAspect;
import com.linkedin.mxe.MetadataChangeProposal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import com.linkedin.entity.client.EntityClient;
import com.datahub.authentication.Authentication;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AspectUtils {

  private AspectUtils() { }

  public static List<MetadataChangeProposal> getAdditionalChanges(
      @Nonnull MetadataChangeProposal metadataChangeProposal,
      @Nonnull EntityService entityService
  ) {
    // No additional changes for delete operation
    if (metadataChangeProposal.getChangeType() == ChangeType.DELETE) {
      return Collections.emptyList();
    }

    final Urn urn = EntityKeyUtils.getUrnFromProposal(metadataChangeProposal,
        entityService.getKeyAspectSpec(metadataChangeProposal.getEntityType()));

    return entityService.generateDefaultAspectsIfMissing(urn, ImmutableSet.of(metadataChangeProposal.getAspectName()))
        .stream()
        .map(entry -> getProposalFromAspect(entry.getKey(), entry.getValue(), metadataChangeProposal))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @Nullable
  public static Aspect getLatestAspect(
      Urn urn,
      String aspectName,
      EntityClient entityClient,
      Authentication authentication
  ) throws Exception {
    EntityResponse response = entityClient.getV2(
        urn.getEntityType(),
        urn,
        ImmutableSet.of(aspectName),
        authentication);
    if (response != null && response.getAspects().containsKey(aspectName)) {
      return response.getAspects().get(aspectName).getValue();
    }
    return null;
  }

  public static Map<Urn, Aspect> batchGetLatestAspect(
      String entity,
      Set<Urn> urns,
      String aspectName,
      EntityClient entityClient,
      Authentication authentication
  ) throws Exception {
    final Map<Urn, EntityResponse> gmsResponse = entityClient.batchGetV2(
        entity,
        urns,
        ImmutableSet.of(aspectName),
        authentication);
    final Map<Urn, Aspect> finalResult = new HashMap<>();
    for (Urn urn : urns) {
      EntityResponse response = gmsResponse.get(urn);
      if (response != null && response.getAspects().containsKey(aspectName)) {
        finalResult.put(urn, response.getAspects().get(aspectName).getValue());
      }
    }
    return finalResult;
  }

  private static MetadataChangeProposal getProposalFromAspect(String aspectName, RecordTemplate aspect,
      MetadataChangeProposal original) {
    try {
      MetadataChangeProposal proposal = original.copy();
      GenericAspect genericAspect = GenericRecordUtils.serializeAspect(aspect);
      proposal.setAspect(genericAspect);
      proposal.setAspectName(aspectName);
      return proposal;
    } catch (CloneNotSupportedException e) {
      log.error("Issue while generating additional proposals corresponding to the input proposal", e);
    }
    return null;
  }

  public static MetadataChangeProposal buildMetadataChangeProposal(
      @Nonnull Urn urn,
      @Nonnull String aspectName,
      @Nonnull RecordTemplate aspect) {
    final MetadataChangeProposal proposal = new MetadataChangeProposal();
    proposal.setEntityUrn(urn);
    proposal.setEntityType(urn.getEntityType());
    proposal.setAspectName(aspectName);
    proposal.setAspect(GenericRecordUtils.serializeAspect(aspect));
    proposal.setChangeType(ChangeType.UPSERT);
    return proposal;
  }

  public static ListResult toListResult(final SearchResult searchResult) {
    if (searchResult == null) {
      return null;
    }
    final ListResult listResult = new ListResult();
    listResult.setStart(searchResult.getFrom());
    listResult.setCount(searchResult.getPageSize());
    listResult.setTotal(searchResult.getNumEntities());
    listResult.setEntities(
        new UrnArray(searchResult.getEntities().stream().map(SearchEntity::getEntity).collect(Collectors.toList())));
    return listResult;
  }
}