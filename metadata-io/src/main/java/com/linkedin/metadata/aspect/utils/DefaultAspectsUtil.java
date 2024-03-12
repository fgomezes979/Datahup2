package com.linkedin.metadata.aspect.utils;

import static com.linkedin.metadata.Constants.BROWSE_PATHS_ASPECT_NAME;
import static com.linkedin.metadata.Constants.BROWSE_PATHS_V2_ASPECT_NAME;
import static com.linkedin.metadata.Constants.DATA_PLATFORM_INSTANCE_ASPECT_NAME;
import static com.linkedin.metadata.search.utils.BrowsePathUtils.buildDataPlatformUrn;
import static com.linkedin.metadata.search.utils.BrowsePathUtils.getDefaultBrowsePath;
import static com.linkedin.metadata.search.utils.BrowsePathV2Utils.getDefaultBrowsePathV2;

import com.google.common.collect.ImmutableSet;
import com.linkedin.common.BrowsePaths;
import com.linkedin.common.BrowsePathsV2;
import com.linkedin.common.urn.Urn;
import com.linkedin.data.template.RecordTemplate;
import com.linkedin.data.template.StringArray;
import com.linkedin.dataplatform.DataPlatformInfo;
import com.linkedin.entity.EntityResponse;
import com.linkedin.events.metadata.ChangeType;
import com.linkedin.metadata.Constants;
import com.linkedin.metadata.aspect.batch.AspectsBatch;
import com.linkedin.metadata.aspect.batch.BatchItem;
import com.linkedin.metadata.aspect.batch.MCPItem;
import com.linkedin.metadata.entity.EntityService;
import com.linkedin.metadata.entity.EntityUtils;
import com.linkedin.metadata.entity.ebean.batch.AspectsBatchImpl;
import com.linkedin.metadata.entity.ebean.batch.ChangeItemImpl;
import com.linkedin.metadata.models.registry.EntityRegistry;
import com.linkedin.metadata.utils.DataPlatformInstanceUtils;
import com.linkedin.metadata.utils.GenericRecordUtils;
import com.linkedin.mxe.GenericAspect;
import com.linkedin.mxe.MetadataChangeProposal;
import com.linkedin.util.Pair;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

/** Consolidates logic for default aspects */
@Slf4j
public class DefaultAspectsUtil {
  private DefaultAspectsUtil() {}

  public static final Set<ChangeType> SUPPORTED_TYPES =
      Set.of(ChangeType.UPSERT, ChangeType.CREATE, ChangeType.PATCH);

  public static AspectsBatch withAdditionalChanges(
      @Nonnull final AspectsBatch inputBatch,
      @Nonnull EntityService<?> entityService,
      boolean enableBrowseV2) {
    List<BatchItem> itemsWithDefaults = new LinkedList<>(inputBatch.getItems());
    itemsWithDefaults.addAll(
        DefaultAspectsUtil.getAdditionalChanges(inputBatch, entityService, enableBrowseV2));
    return AspectsBatchImpl.builder()
        .aspectRetriever(inputBatch.getAspectRetriever())
        .items(itemsWithDefaults)
        .build();
  }

  public static List<MCPItem> getAdditionalChanges(
      @Nonnull AspectsBatch batch, @Nonnull EntityService<?> entityService, boolean browsePathV2) {

    Map<Urn, List<MCPItem>> itemsByUrn =
        batch.getMCPItems().stream()
            .filter(item -> SUPPORTED_TYPES.contains(item.getChangeType()))
            .collect(Collectors.groupingBy(BatchItem::getUrn));

    Set<MCPItem> batchIncludedKeys =
        batch.getMCPItems().stream()
            .filter(item -> item.getAspectName().equals(item.getEntitySpec().getKeyAspectName()))
            .collect(Collectors.toSet());

    Set<Urn> urnsWithExistingKeyAspects = entityService.exists(itemsByUrn.keySet());

    // create default aspects when key aspect is missing
    return itemsByUrn.entrySet().stream()
        .filter(aspectsEntry -> !urnsWithExistingKeyAspects.contains(aspectsEntry.getKey()))
        .flatMap(
            aspectsEntry -> {
              // Exclude aspects already in the batch
              Set<String> currentBatchAspectNames =
                  aspectsEntry.getValue().stream()
                      .map(BatchItem::getAspectName)
                      .collect(Collectors.toSet());

              // Generate key aspect and defaults
              List<Pair<String, RecordTemplate>> defaultAspects =
                  generateDefaultAspects(
                      entityService, aspectsEntry.getKey(), currentBatchAspectNames, browsePathV2);

              // First is the key aspect
              RecordTemplate entityKeyAspect = defaultAspects.get(0).getSecond();

              // pick the first item as a template (use entity information)
              MCPItem templateItem = aspectsEntry.getValue().get(0);

              // generate default aspects (including key aspect, always upserts)
              return defaultAspects.stream()
                  .map(
                      entry ->
                          ChangeItemImpl.ChangeItemImplBuilder.build(
                              getProposalFromAspectForDefault(
                                  entry.getKey(), entry.getValue(), entityKeyAspect, templateItem),
                              templateItem.getAuditStamp(),
                              entityService))
                  .filter(Objects::nonNull);
            })
        .filter(item -> !batchIncludedKeys.contains(item))
        .collect(Collectors.toList());
  }

  /**
   * Generate default aspects
   *
   * @param entityService entity service
   * @param urn entity urn
   * @return a list of aspect name/aspect pairs to be written
   */
  private static List<Pair<String, RecordTemplate>> generateDefaultAspects(
      @Nonnull EntityService<?> entityService,
      @Nonnull final Urn urn,
      @Nonnull Set<String> currentBatchAspectNames,
      boolean browsePathV2) {

    final List<Pair<String, RecordTemplate>> defaultAspects = new LinkedList<>();

    // Key Aspect
    final String keyAspectName = entityService.getKeyAspectName(urn);
    defaultAspects.add(
        Pair.of(keyAspectName, EntityUtils.buildKeyAspect(entityService.getEntityRegistry(), urn)));

    // Other Aspects
    defaultAspects.addAll(
        generateDefaultAspectsIfMissing(
            entityService,
            urn,
            defaultAspects.get(0).getSecond(),
            currentBatchAspectNames,
            browsePathV2));

    return defaultAspects;
  }

  /**
   * Generate default aspects if the aspect is NOT in the database.
   *
   * <p>Does not automatically create key aspects.
   *
   * @see #generateDefaultAspectsIfMissing if key aspects need autogeneration
   * @param entityService
   * @param urn entity urn
   * @param entityKeyAspect entity's key aspect
   * @return additional aspects to be written
   */
  private static List<Pair<String, RecordTemplate>> generateDefaultAspectsIfMissing(
      @Nonnull EntityService<?> entityService,
      @Nonnull final Urn urn,
      RecordTemplate entityKeyAspect,
      @Nonnull Set<String> currentAspectNames,
      boolean browsePathV2) {
    EntityRegistry entityRegistry = entityService.getEntityRegistry();

    Set<String> fetchAspects =
        Stream.of(
                BROWSE_PATHS_ASPECT_NAME,
                BROWSE_PATHS_V2_ASPECT_NAME,
                DATA_PLATFORM_INSTANCE_ASPECT_NAME)
            // If browsePathV2 then exclude v1
            .filter(aspectName -> !(BROWSE_PATHS_ASPECT_NAME.equals(aspectName) && browsePathV2))
            // Exclude currently ingesting aspects
            .filter(aspectName -> !currentAspectNames.contains(aspectName))
            // Exclude in case when we have limited test entity registry which doesn't include these
            .filter(
                aspectName ->
                    entityRegistry
                        .getEntitySpec(urn.getEntityType())
                        .getAspectSpecMap()
                        .containsKey(aspectName))
            .collect(Collectors.toSet());

    if (!fetchAspects.isEmpty()) {

      Set<String> latestAspects = entityService.getLatestAspectsForUrn(urn, fetchAspects).keySet();

      return fetchAspects.stream()
          .filter(aspectName -> !latestAspects.contains(aspectName))
          .map(
              aspectName -> {
                switch (aspectName) {
                  case BROWSE_PATHS_ASPECT_NAME:
                    return Pair.of(
                        BROWSE_PATHS_ASPECT_NAME,
                        (RecordTemplate) buildDefaultBrowsePath(urn, entityService));
                  case BROWSE_PATHS_V2_ASPECT_NAME:
                    return Pair.of(
                        BROWSE_PATHS_V2_ASPECT_NAME,
                        (RecordTemplate) buildDefaultBrowsePathV2(urn, false, entityService));
                  case DATA_PLATFORM_INSTANCE_ASPECT_NAME:
                    return DataPlatformInstanceUtils.buildDataPlatformInstance(
                            urn.getEntityType(), entityKeyAspect)
                        .map(
                            aspect ->
                                Pair.of(
                                    DATA_PLATFORM_INSTANCE_ASPECT_NAME, (RecordTemplate) aspect))
                        .orElse(null);
                  default:
                    return null;
                }
              })
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
    }

    return Collections.emptyList();
  }

  /**
   * Builds the default browse path aspects for a subset of well-supported entities.
   *
   * <p>This method currently supports datasets, charts, dashboards, data flows, data jobs, and
   * glossary terms.
   */
  @Nonnull
  public static BrowsePaths buildDefaultBrowsePath(
      final @Nonnull Urn urn, EntityService<?> entityService) {
    Character dataPlatformDelimiter = getDataPlatformDelimiter(urn, entityService);
    String defaultBrowsePath =
        getDefaultBrowsePath(urn, entityService.getEntityRegistry(), dataPlatformDelimiter);
    StringArray browsePaths = new StringArray();
    browsePaths.add(defaultBrowsePath);
    BrowsePaths browsePathAspect = new BrowsePaths();
    browsePathAspect.setPaths(browsePaths);
    return browsePathAspect;
  }

  /**
   * Builds the default browse path V2 aspects for all entities.
   *
   * <p>This method currently supports datasets, charts, dashboards, and data jobs best. Everything
   * else will have a basic "Default" folder added to their browsePathV2.
   */
  @Nonnull
  public static BrowsePathsV2 buildDefaultBrowsePathV2(
      final @Nonnull Urn urn, boolean useContainerPaths, EntityService<?> entityService) {
    Character dataPlatformDelimiter = getDataPlatformDelimiter(urn, entityService);
    return getDefaultBrowsePathV2(
        urn,
        entityService.getEntityRegistry(),
        dataPlatformDelimiter,
        entityService,
        useContainerPaths);
  }

  /** Returns a delimiter on which the name of an asset may be split. */
  private static Character getDataPlatformDelimiter(Urn urn, EntityService<?> entityService) {
    // Attempt to construct the appropriate Data Platform URN
    Urn dataPlatformUrn = buildDataPlatformUrn(urn, entityService.getEntityRegistry());
    if (dataPlatformUrn != null) {
      // Attempt to resolve the delimiter from Data Platform Info
      DataPlatformInfo dataPlatformInfo = getDataPlatformInfo(dataPlatformUrn, entityService);
      if (dataPlatformInfo != null && dataPlatformInfo.hasDatasetNameDelimiter()) {
        return dataPlatformInfo.getDatasetNameDelimiter().charAt(0);
      }
    }
    // Else, fallback to a default delimiter (period) if one cannot be resolved.
    return '.';
  }

  @Nullable
  private static DataPlatformInfo getDataPlatformInfo(Urn urn, EntityService<?> entityService) {
    try {
      final EntityResponse entityResponse =
          entityService.getEntityV2(
              Constants.DATA_PLATFORM_ENTITY_NAME,
              urn,
              ImmutableSet.of(Constants.DATA_PLATFORM_INFO_ASPECT_NAME));
      if (entityResponse != null
          && entityResponse.hasAspects()
          && entityResponse.getAspects().containsKey(Constants.DATA_PLATFORM_INFO_ASPECT_NAME)) {
        return new DataPlatformInfo(
            entityResponse
                .getAspects()
                .get(Constants.DATA_PLATFORM_INFO_ASPECT_NAME)
                .getValue()
                .data());
      }
    } catch (Exception e) {
      log.warn(String.format("Failed to find Data Platform Info for urn %s", urn));
    }
    return null;
  }

  private static MetadataChangeProposal getProposalFromAspectForDefault(
      String aspectName,
      RecordTemplate aspect,
      RecordTemplate entityKeyAspect,
      MCPItem templateItem) {
    MetadataChangeProposal proposal = new MetadataChangeProposal();
    GenericAspect genericAspect = GenericRecordUtils.serializeAspect(aspect);

    // Set net new fields
    proposal.setAspect(genericAspect);
    proposal.setAspectName(aspectName);
    // already checked existence, default aspects should be changeType CREATE
    proposal.setChangeType(ChangeType.CREATE);

    // Set fields determined from original
    if (templateItem.getSystemMetadata() != null) {
      proposal.setSystemMetadata(templateItem.getSystemMetadata());
    }
    if (templateItem.getUrn() != null) {
      proposal.setEntityUrn(templateItem.getUrn());
    }
    if (entityKeyAspect != null) {
      proposal.setEntityKeyAspect(GenericRecordUtils.serializeAspect(entityKeyAspect));
    }
    proposal.setEntityType(templateItem.getUrn().getEntityType());

    return proposal;
  }
}
