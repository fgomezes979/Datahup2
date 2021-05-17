package com.linkedin.metadata;

import com.linkedin.common.urn.Urn;
import com.linkedin.data.DataMap;
import com.linkedin.data.schema.NamedDataSchema;
import com.linkedin.data.schema.RecordDataSchema;
import com.linkedin.data.template.RecordTemplate;
import com.linkedin.metadata.dao.exception.ModelConversionException;
import com.linkedin.metadata.dao.utils.RecordUtils;
import com.linkedin.metadata.models.annotation.EntityAnnotation;


public class EntitySpecUtils {

  private EntitySpecUtils() {
  }

  public static String getEntityNameFromSchema(final RecordDataSchema entitySnapshotSchema) {
    final Object entityAnnotationObj = entitySnapshotSchema.getProperties().get("Entity");
    if (entityAnnotationObj != null) {
      return EntityAnnotation.fromSchemaProperty(entityAnnotationObj).getName();
    }
    throw new IllegalArgumentException(
        String.format("Failed to extract entity name from provided schema %s", entitySnapshotSchema.getName()));
  }

  // TODO: Figure out a better iteration strategy.
  public static String getAspectNameFromFullyQualifiedName(final String fullyQualifiedRecordTemplateName) {
    final RecordTemplate template = RecordUtils.toRecordTemplate(fullyQualifiedRecordTemplateName, new DataMap());
    final RecordDataSchema aspectSchema = template.schema();
    return getAspectNameFromSchema(aspectSchema);
  }

  public static String getAspectNameFromSchema(final RecordDataSchema aspectSchema) {
    final Object aspectAnnotationObj = aspectSchema.getProperties().get("Aspect");
    if (aspectAnnotationObj != null) {
      return EntityAnnotation.fromSchemaProperty(aspectAnnotationObj).getName();
    }
    throw new IllegalArgumentException(
        String.format("Failed to extract aspect name from provided schema %s", aspectSchema.getName()));
  }

  public static <T> Class<? extends T> getDataTemplateClassFromSchema(final NamedDataSchema schema, final Class<T> clazz) {
    try {
      return Class.forName(schema.getFullName()).asSubclass(clazz);
    } catch (ClassNotFoundException e) {
      throw new ModelConversionException("Unable to find class for RecordDataSchema named " + schema.getFullName(), e);
    }
  }

  public static String urnToEntityName(final Urn urn) {
    return urn.getEntityType();
  }
}
