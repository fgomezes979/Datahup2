package com.linkedin.datahub.graphql.resolvers;

import com.google.common.collect.ImmutableMap;
import com.linkedin.datahub.graphql.generated.EntityType;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;


public class EntityTypeMapper {

  private static final Map<EntityType, String> ENTITY_TYPE_TO_NAME =
      ImmutableMap.<EntityType, String>builder().put(EntityType.DATASET, "dataset")
          .put(EntityType.CORP_USER, "corpuser")
          .put(EntityType.CORP_GROUP, "corpGroup")
          .put(EntityType.DATA_PLATFORM, "dataPlatform")
          .put(EntityType.DASHBOARD, "dashboard")
          .put(EntityType.CHART, "chart")
          .put(EntityType.TAG, "tag")
          .put(EntityType.MLMODEL, "mlModel")
          .put(EntityType.MLMODEL_GROUP, "mlModelGroup")
          .put(EntityType.MLFEATURE_TABLE, "mlFeatureTable")
          .put(EntityType.MLFEATURE, "mlFeature")
          .put(EntityType.MLPRIMARY_KEY, "mlPrimaryKey")
          .put(EntityType.DATA_FLOW, "dataFlow")
          .put(EntityType.DATA_JOB, "dataJob")
          .put(EntityType.GLOSSARY_TERM, "glossaryTerm")
          .build();

  private static final Map<String, EntityType> ENTITY_NAME_TO_TYPE =
      ENTITY_TYPE_TO_NAME.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

  private EntityTypeMapper() {
  }

  public static EntityType getType(String name) {
    if (!ENTITY_NAME_TO_TYPE.containsKey(name)) {
      throw new IllegalArgumentException("Unknown entity name: " + name);
    }
    return ENTITY_NAME_TO_TYPE.get(name);
  }

  @Nonnull
  public static String getName(EntityType type) {
    if (!ENTITY_TYPE_TO_NAME.containsKey(type)) {
      throw new IllegalArgumentException("Unknown entity type: " + type);
    }
    return ENTITY_TYPE_TO_NAME.get(type);
  }
}
