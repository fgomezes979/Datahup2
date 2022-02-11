package com.linkedin.metadata.timeline.ebean;

import com.datahub.util.RecordUtils;
import com.github.fge.jsonpatch.JsonPatch;
import com.linkedin.common.GlossaryTermAssociation;
import com.linkedin.common.TagAssociation;
import com.linkedin.common.urn.DatasetUrn;
import com.linkedin.metadata.entity.ebean.EbeanAspectV2;
import com.linkedin.metadata.timeline.data.ChangeCategory;
import com.linkedin.metadata.timeline.data.ChangeEvent;
import com.linkedin.metadata.timeline.data.ChangeOperation;
import com.linkedin.metadata.timeline.data.ChangeTransaction;
import com.linkedin.metadata.timeline.data.SemanticChangeType;
import com.linkedin.schema.SchemaField;
import com.linkedin.schema.SchemaFieldArray;
import com.linkedin.schema.SchemaMetadata;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


public class SchemaDiffer implements Differ {
  private static final String SCHEMA_METADATA_ASPECT_NAME = "schemaMetadata";
  private static final String BACKWARDS_INCOMPATIBLE_DESC = "A backwards incompatible change due to ";
  private static final String FORWARDS_COMPATIBLE_DESC = "A forwards compatible change due to ";
  private static final String BACK_AND_FORWARD_COMPATIBLE_DESC = "A forwards & backwards compatible change due to ";

  private static String getFieldPathV1(SchemaField field) {
    assert (field != null);
    String[] v1PathTokens = Arrays.stream(field.getFieldPath().split("\\."))
        .filter(x -> !(x.startsWith("[") || x.endsWith("]")))
        .toArray(String[]::new);
    return String.join(".", v1PathTokens);
  }

  private static String getSchemaFieldUrn(DatasetUrn datasetUrn, SchemaField schemaField) {
    assert (datasetUrn != null && schemaField != null);
    return String.format("urn:li:schemaField:(%s,%s)", datasetUrn, getFieldPathV1(schemaField));
  }

  private static String getSchemaFieldUrn(DatasetUrn datasetUrn, String schemaFieldPath) {
    assert (datasetUrn != null && schemaFieldPath != null);
    return String.format("urn:li:schemaField:(%s,%s)", datasetUrn, schemaFieldPath);
  }

  @Override
  public ChangeTransaction getSemanticDiff(EbeanAspectV2 previousValue, EbeanAspectV2 currentValue,
      ChangeCategory element, JsonPatch rawDiff, boolean rawDiffRequested) {
    if (!previousValue.getAspect().equals(SCHEMA_METADATA_ASPECT_NAME) || !currentValue.getAspect()
        .equals(SCHEMA_METADATA_ASPECT_NAME)) {
      throw new IllegalArgumentException("Aspect is not " + SCHEMA_METADATA_ASPECT_NAME);
    }

    SchemaMetadata baseSchema = getSchemaMetadataFromAspect(previousValue);
    SchemaMetadata targetSchema = getSchemaMetadataFromAspect(currentValue);
    assert (targetSchema != null);
    List<ChangeEvent> changeEvents = new ArrayList<>();
    ChangeEvent incompatibleChangeEvent = getIncompatibleChangeEvent(baseSchema, targetSchema);
    if (incompatibleChangeEvent != null) {
      changeEvents.add(incompatibleChangeEvent);
    } else {
      try {
        changeEvents.addAll(
            computeSchemaDiff(baseSchema, targetSchema, DatasetUrn.createFromString(currentValue.getUrn())));
      } catch (URISyntaxException e) {
        throw new IllegalArgumentException("Malformed DatasetUrn " + currentValue.getUrn());
      }
    }

    // Assess the highest change at the transaction(schema) level.
    SemanticChangeType highestSematicChange = SemanticChangeType.NONE;
    if (changeEvents != null) {
      ChangeEvent highestChangeEvent =
          changeEvents.stream().max(Comparator.comparing(ChangeEvent::getSemVerChange)).orElse(null);
      if (highestChangeEvent != null) {
        highestSematicChange = highestChangeEvent.getSemVerChange();
      }
    }
    return ChangeTransaction.builder()
        .changeEvents(changeEvents)
        .timestamp(currentValue.getCreatedOn().getTime())
        .rawDiff(rawDiffRequested ? rawDiff : null)
        .semVerChange(highestSematicChange)
        .actor(currentValue.getCreatedBy())
        .build();
  }

  private ChangeEvent getDescriptionChange(SchemaField baseField, SchemaField targetField, String datasetFieldUrn) {
    if (baseField.getDescription() == null && targetField.getDescription() == null) {
      return null;
    }
    if (baseField.getDescription() == null && targetField.getDescription() != null) {
      // Description got added.
      return ChangeEvent.builder()
          .changeType(ChangeOperation.ADD)
          .semVerChange(SemanticChangeType.MINOR)
          .category(ChangeCategory.DOCUMENTATION)
          .target(datasetFieldUrn)
          .description("Description added.")
          .build();
    }
    if (baseField.getDescription() != null && targetField.getDescription() == null) {
      // Description removed.
      return ChangeEvent.builder()
          .changeType(ChangeOperation.REMOVE)
          .semVerChange(SemanticChangeType.MINOR)
          .category(ChangeCategory.DOCUMENTATION)
          .target(datasetFieldUrn)
          .description("Description removed.")
          .build();
    }
    if (!baseField.getDescription().equals(targetField.getDescription())) {
      // Description Change
      return ChangeEvent.builder()
          .changeType(ChangeOperation.MODIFY)
          .semVerChange(SemanticChangeType.MINOR)
          .category(ChangeCategory.DOCUMENTATION)
          .target(datasetFieldUrn)
          .description(
              "Description updated from '" + baseField.getDescription() + "' to '" + targetField.getDescription() + "'")
          .build();
    }
    return null;
  }

  private List<ChangeEvent> getGlobalTagChangeEvents(SchemaField baseField, SchemaField targetField,
      String datasetFieldUrn) {
    if (baseField.getGlobalTags() == null && targetField.getGlobalTags() == null) {
      return null;
    }
    if (baseField.getGlobalTags() == null && targetField.getGlobalTags() != null) {
      // Global tags added.
      return Collections.singletonList(ChangeEvent.builder()
          .changeType(ChangeOperation.ADD)
          .semVerChange(SemanticChangeType.MINOR)
          .category(ChangeCategory.TAG)
          .target(datasetFieldUrn)
          .description("Global Tags created.")
          .build());
    }

    if (baseField.getGlobalTags() != null && targetField.getGlobalTags() == null) {
      // Global tags removed.
      return Collections.singletonList(ChangeEvent.builder()
          .changeType(ChangeOperation.REMOVE)
          .semVerChange(SemanticChangeType.MINOR)
          .category(ChangeCategory.TAG)
          .target(datasetFieldUrn)
          .description("Global Tags removed.")
          .build());
    }

    if (!baseField.getGlobalTags().equals(targetField.getGlobalTags())) {
      List<ChangeEvent> glossaryTermChangeEvents = new ArrayList<>();
      Set<TagAssociation> baseFieldTags = new HashSet<>(baseField.getGlobalTags().getTags());
      Set<TagAssociation> targetFieldTags = new HashSet<>(targetField.getGlobalTags().getTags());
      Set<TagAssociation> removedTags = baseFieldTags.stream()
          .filter(key -> !targetFieldTags.contains(key))
          .collect(Collectors.toSet());
      Set<TagAssociation> addedTags = targetFieldTags.stream()
          .filter(key -> !baseFieldTags.contains(key))
          .collect(Collectors.toSet());
      for (TagAssociation removedTag : removedTags) {
        // Global tags changed.
        glossaryTermChangeEvents.add(ChangeEvent.builder()
            .changeType(ChangeOperation.REMOVE)
            .semVerChange(SemanticChangeType.MINOR)
            .category(ChangeCategory.TAG)
            .target(datasetFieldUrn)
            .description("The tag '" + removedTag.getTag() + "' has been removed.")
            .build());
      }
      for (TagAssociation addedTag : addedTags) {
        glossaryTermChangeEvents.add(ChangeEvent.builder()
            .changeType(ChangeOperation.ADD)
            .semVerChange(SemanticChangeType.MINOR)
            .category(ChangeCategory.TAG)
            .target(datasetFieldUrn)
            .description("The tag '" + addedTag.getTag() + "' has been added.")
            .build());
      }
      return glossaryTermChangeEvents;
    }
    return null;
  }

  private List<ChangeEvent> getGlossaryTermsChangeEvents(SchemaField baseField, SchemaField targetField,
      String datasetFieldUrn) {
    if (baseField.getGlossaryTerms() == null && targetField.getGlossaryTerms() == null) {
      return null;
    }
    if (baseField.getGlossaryTerms() == null && targetField.getGlossaryTerms() != null) {
      // Glossary terms created.
      return Collections.singletonList(ChangeEvent.builder()
          .changeType(ChangeOperation.ADD)
          .semVerChange(SemanticChangeType.MINOR)
          .category(ChangeCategory.GLOSSARY_TERM)
          .target(datasetFieldUrn)
          .description("Glossary terms created.")
          .build());
    }

    if (baseField.getGlossaryTerms() != null && targetField.getGlossaryTerms() == null) {
      // Glossary terms removed.
      return Collections.singletonList(ChangeEvent.builder()
          .changeType(ChangeOperation.REMOVE)
          .semVerChange(SemanticChangeType.MINOR)
          .category(ChangeCategory.GLOSSARY_TERM)
          .target(datasetFieldUrn)
          .description("Glossary terms removed.")
          .build());
    }

    if (!baseField.getGlossaryTerms().equals(targetField.getGlossaryTerms())) {
      List<ChangeEvent> tagChangeEvents = new ArrayList<>();
      Set<GlossaryTermAssociation> baseFieldTerms = new HashSet<>(baseField.getGlossaryTerms().getTerms());
      Set<GlossaryTermAssociation> targetFieldTerms = new HashSet<>(targetField.getGlossaryTerms().getTerms());
      Set<GlossaryTermAssociation> removedTerms = baseFieldTerms.stream()
          .filter(key -> !targetFieldTerms.contains(key))
          .collect(Collectors.toSet());
      Set<GlossaryTermAssociation> addedTerms = targetFieldTerms.stream()
          .filter(key -> !baseFieldTerms.contains(key))
          .collect(Collectors.toSet());
      for (GlossaryTermAssociation removedTerm : removedTerms) {
        // Global tags changed.
        tagChangeEvents.add(ChangeEvent.builder()
            .changeType(ChangeOperation.REMOVE)
            .semVerChange(SemanticChangeType.MINOR)
            .category(ChangeCategory.GLOSSARY_TERM)
            .target(datasetFieldUrn)
            .description("The glossary term '" + removedTerm.getUrn() + "' has been removed.")
            .build());
      }
      for (GlossaryTermAssociation addedTerm : addedTerms) {
        tagChangeEvents.add(ChangeEvent.builder()
            .changeType(ChangeOperation.ADD)
            .semVerChange(SemanticChangeType.MINOR)
            .category(ChangeCategory.GLOSSARY_TERM)
            .target(datasetFieldUrn)
            .description("The glossary term'" + addedTerm.getUrn() + "' has been added.")
            .build());
      }
      return tagChangeEvents;
    }
    return null;
  }

  private List<ChangeEvent> getFieldPropertyChangeEvents(SchemaField baseField, SchemaField targetField,
      DatasetUrn datasetUrn) {
    List<ChangeEvent> propChangeEvents = new ArrayList<>();
    String datasetFieldUrn = getSchemaFieldUrn(datasetUrn, targetField);
    // Description Change.
    ChangeEvent descriptionChangeEvent = getDescriptionChange(baseField, targetField, datasetFieldUrn);
    if (descriptionChangeEvent != null) {
      propChangeEvents.add(descriptionChangeEvent);
    }
    // Global Tags
    List<ChangeEvent> globalTagChangeEvents = getGlobalTagChangeEvents(baseField, targetField, datasetFieldUrn);
    if (globalTagChangeEvents != null) {
      propChangeEvents.addAll(globalTagChangeEvents);
    }
    // Glossary terms.
    List<ChangeEvent> glossaryTermsChangeEvents = getGlossaryTermsChangeEvents(baseField, targetField, datasetFieldUrn);
    if (glossaryTermsChangeEvents != null) {
      propChangeEvents.addAll(glossaryTermsChangeEvents);
    }
    return propChangeEvents;
  }

  private List<ChangeEvent> computeSchemaDiff(SchemaMetadata baseSchema, SchemaMetadata targetSchema,
      DatasetUrn datasetUrn) {
    boolean isOrdinalBasedSchema = isSchemaOrdinalBased(targetSchema);
    if (!isOrdinalBasedSchema) {
      // Sort the fields by their field path.
      if (baseSchema != null) {
        sortFieldsByPath(baseSchema);
      }
      sortFieldsByPath(targetSchema);
    }
    /**
     * Performs ordinal based diff, primarily based on fixed field ordinals and their types.
     */
    SchemaFieldArray baseFields = (baseSchema != null ? baseSchema.getFields() : new SchemaFieldArray());
    SchemaFieldArray targetFields = targetSchema.getFields();
    int baseFieldIdx = 0;
    int targetFieldIdx = 0;
    List<ChangeEvent> changeEvents = new ArrayList<>();
    while (baseFieldIdx < baseFields.size() && targetFieldIdx < targetFields.size()) {
      SchemaField curBaseField = baseFields.get(baseFieldIdx);
      SchemaField curTargetField = targetFields.get(targetFieldIdx);
      if (isOrdinalBasedSchema) {
        if (!curBaseField.getNativeDataType().equals(curTargetField.getNativeDataType())) {
          // Non-backward compatible change + Major version bump
          changeEvents.add(ChangeEvent.builder()
              .category(ChangeCategory.TECHNICAL_SCHEMA)
              .elementId(getSchemaFieldUrn(datasetUrn, curBaseField))
              .target(datasetUrn.toString())
              .changeType(ChangeOperation.MODIFY)
              .semVerChange(SemanticChangeType.MAJOR)
              .description(String.format("%s native datatype of the field '%s' changed from '%s' to '%s'.",
                  BACKWARDS_INCOMPATIBLE_DESC, getFieldPathV1(curTargetField), curBaseField.getNativeDataType(),
                  curTargetField.getNativeDataType()))
              .build());
          ++baseFieldIdx;
          ++targetFieldIdx;
          continue;
        }
        if (baseFieldIdx == targetFieldIdx && !curBaseField.getFieldPath().equals(curTargetField.getFieldPath())) {
          // The field got renamed. Forward compatible + Minor version bump.
          changeEvents.add(ChangeEvent.builder()
              .category(ChangeCategory.TECHNICAL_SCHEMA)
              .elementId(getSchemaFieldUrn(datasetUrn, curBaseField))
              .target(datasetUrn.toString())
              .changeType(ChangeOperation.MODIFY)
              .semVerChange(SemanticChangeType.MINOR)
              .description(
                  FORWARDS_COMPATIBLE_DESC + "field name changed from '" + getFieldPathV1(curBaseField) + "' to '"
                      + getFieldPathV1(curTargetField) + "'")
              .build());
        }
        // Generate change events from property changes
        List<ChangeEvent> propChangeEvents = getFieldPropertyChangeEvents(curBaseField, curTargetField, datasetUrn);
        changeEvents.addAll(propChangeEvents);
        ++baseFieldIdx;
        ++targetFieldIdx;
      } else {
        // Non-ordinal based schemas are pre-sorted by ascending order of fieldPaths.
        int comparison = curBaseField.getFieldPath().compareTo(curTargetField.getFieldPath());
        if (comparison == 0) {
          // This is the same field. Check for change events from property changes.
          List<ChangeEvent> propChangeEvents = getFieldPropertyChangeEvents(curBaseField, curTargetField, datasetUrn);
          changeEvents.addAll(propChangeEvents);
          ++baseFieldIdx;
          ++targetFieldIdx;
        } else if (comparison < 0) {
          // BaseFiled got removed. Non-backward compatible change + Major version bump
          changeEvents.add(ChangeEvent.builder()
              .category(ChangeCategory.TECHNICAL_SCHEMA)
              .elementId(getSchemaFieldUrn(datasetUrn, curBaseField))
              .target(datasetUrn.toString())
              .changeType(ChangeOperation.REMOVE)
              .semVerChange(SemanticChangeType.MAJOR)
              .description(BACKWARDS_INCOMPATIBLE_DESC + "removal of the field'" + getFieldPathV1(curBaseField) + "'.")
              .build());
          ++baseFieldIdx;
        } else {
          // The targetField got added. Forward & backwards compatible change + minor version bump.
          changeEvents.add(ChangeEvent.builder()
              .category(ChangeCategory.TECHNICAL_SCHEMA)
              .elementId(getSchemaFieldUrn(datasetUrn, curTargetField))
              .target(datasetUrn.toString())
              .changeType(ChangeOperation.ADD)
              .semVerChange(SemanticChangeType.MINOR)
              .description(
                  BACK_AND_FORWARD_COMPATIBLE_DESC + "the newly added field '" + getFieldPathV1(curTargetField) + "'.")
              .build());
          ++targetFieldIdx;
        }
      }
    }
    while (baseFieldIdx < baseFields.size()) {
      // Handle removed fields. Non-backward compatible change + major version bump
      SchemaField baseField = baseFields.get(baseFieldIdx);
      changeEvents.add(ChangeEvent.builder()
          .elementId(getSchemaFieldUrn(datasetUrn, baseField))
          .target(datasetUrn.toString())
          .category(ChangeCategory.TECHNICAL_SCHEMA)
          .changeType(ChangeOperation.REMOVE)
          .semVerChange(SemanticChangeType.MAJOR)
          .description(BACKWARDS_INCOMPATIBLE_DESC + "removal of field: '" + getFieldPathV1(baseField) + "'.")
          .build());
      ++baseFieldIdx;
    }
    while (targetFieldIdx < targetFields.size()) {
      // Newly added fields. Forwards & backwards compatible change + minor version bump.
      SchemaField targetField = targetFields.get(targetFieldIdx);
      changeEvents.add(ChangeEvent.builder()
          .elementId(getSchemaFieldUrn(datasetUrn, targetField))
          .target(datasetUrn.toString())
          .category(ChangeCategory.TECHNICAL_SCHEMA)
          .changeType(ChangeOperation.ADD)
          .semVerChange(SemanticChangeType.MINOR)
          .description(
              BACK_AND_FORWARD_COMPATIBLE_DESC + "the newly added field '" + getFieldPathV1(targetField) + "'.")
          .build());
      ++targetFieldIdx;
    }

    // Handle primary key constraint change events.
    List<ChangeEvent> primaryKeyChangeEvents = getPrimaryKeyChangeEvents(baseSchema, targetSchema, datasetUrn);
    changeEvents.addAll(primaryKeyChangeEvents);

    // Handle foreign key constraint change events.
    List<ChangeEvent> foreignKeyChangeEvents = getForeignKeyChangeEvents(baseSchema, targetSchema);
    changeEvents.addAll(foreignKeyChangeEvents);

    return changeEvents;
  }

  private List<ChangeEvent> getForeignKeyChangeEvents(SchemaMetadata baseSchema, SchemaMetadata targetSchema) {
    List<ChangeEvent> foreignKeyChangeEvents = new ArrayList<>();
    // TODO: Implement the diffing logic.
    return foreignKeyChangeEvents;
  }

  private List<ChangeEvent> getPrimaryKeyChangeEvents(SchemaMetadata baseSchema, SchemaMetadata targetSchema,
      DatasetUrn datasetUrn) {
    List<ChangeEvent> primaryKeyChangeEvents = new ArrayList<>();
    Set<String> basePrimaryKeys =
        (baseSchema != null && baseSchema.getPrimaryKeys() != null) ? new HashSet<>(baseSchema.getPrimaryKeys())
            : new HashSet<>();
    Set<String> targetPrimaryKeys =
        (targetSchema.getPrimaryKeys() != null) ? new HashSet<>(targetSchema.getPrimaryKeys()) : new HashSet<>();
    Set<String> removedBaseKeys = basePrimaryKeys.stream()
        .filter(key -> !targetPrimaryKeys.contains(key))
        .collect(Collectors.toSet());
    for (String removedBaseKeyField : removedBaseKeys) {
      primaryKeyChangeEvents.add(ChangeEvent.builder()
          .category(ChangeCategory.TECHNICAL_SCHEMA)
          .elementId(getSchemaFieldUrn(datasetUrn, removedBaseKeyField))
          .target(datasetUrn.toString())
          .changeType(ChangeOperation.MODIFY)
          .semVerChange(SemanticChangeType.MAJOR)
          .description(
              BACKWARDS_INCOMPATIBLE_DESC + "removal of the primary key field '" + removedBaseKeyField + "'")
          .build());
    }

    Set<String> addedTargetKeys = targetPrimaryKeys.stream()
        .filter(key -> !basePrimaryKeys.contains(key))
        .collect(Collectors.toSet());
    for (String addedTargetKeyField : addedTargetKeys) {
      primaryKeyChangeEvents.add(ChangeEvent.builder()
          .category(ChangeCategory.TECHNICAL_SCHEMA)
          .elementId(getSchemaFieldUrn(datasetUrn, addedTargetKeyField))
          .target(datasetUrn.toString())
          .changeType(ChangeOperation.MODIFY)
          .semVerChange(SemanticChangeType.MAJOR)
          .description(
              BACKWARDS_INCOMPATIBLE_DESC + "addition of the primary key field '" + addedTargetKeyField.toString()
                  + "'")
          .build());
    }
    return primaryKeyChangeEvents;
  }

  private void sortFieldsByPath(SchemaMetadata schemaMetadata) {
    assert (schemaMetadata != null);
    List<SchemaField> schemaFields = new ArrayList<>(schemaMetadata.getFields());
    schemaFields.sort(Comparator.comparing(SchemaField::getFieldPath));
    schemaMetadata.setFields(new SchemaFieldArray(schemaFields));
  }

  private boolean isSchemaOrdinalBased(SchemaMetadata schemaMetadata) {
    if (schemaMetadata == null) {
      return false;
    }
    SchemaMetadata.PlatformSchema platformSchema = schemaMetadata.getPlatformSchema();
    return platformSchema.isOracleDDL() || platformSchema.isMySqlDDL() || platformSchema.isPrestoDDL();
  }

  private ChangeEvent getIncompatibleChangeEvent(SchemaMetadata baseSchema, SchemaMetadata targetSchema) {
    if (baseSchema != null && targetSchema != null) {
      if (!baseSchema.getPlatform().equals(targetSchema.getPlatform())) {
        return ChangeEvent.builder()
            .semVerChange(SemanticChangeType.EXCEPTIONAL)
            .description("Incompatible schema types," + baseSchema.getPlatform() + ", " + targetSchema.getPlatform())
            .build();
      }
      if (!baseSchema.getSchemaName().equals(targetSchema.getSchemaName())) {
        return ChangeEvent.builder()
            .semVerChange(SemanticChangeType.EXCEPTIONAL)
            .description(
                "Schema names are not same," + baseSchema.getSchemaName() + ", " + targetSchema.getSchemaName())
            .build();
      }
    }
    return null;
  }

  private SchemaMetadata getSchemaMetadataFromAspect(EbeanAspectV2 ebeanAspectV2) {
    if (ebeanAspectV2 != null && ebeanAspectV2.getMetadata() != null) {
      return RecordUtils.toRecordTemplate(SchemaMetadata.class, ebeanAspectV2.getMetadata());
    }
    return null;
  }
}
