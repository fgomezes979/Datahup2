package com.linkedin.metadata.entity;

import com.google.common.collect.ImmutableList;
import com.linkedin.common.AuditStamp;
import com.linkedin.common.urn.Urn;
import com.linkedin.data.schema.RecordDataSchema;
import com.linkedin.data.template.RecordTemplate;
import com.linkedin.data.template.UnionTemplate;
import com.linkedin.experimental.Entity;
import com.linkedin.metadata.ModelUtils;
import com.linkedin.metadata.dao.exception.ModelConversionException;
import com.linkedin.metadata.dao.producer.EntityKafkaMetadataEventProducer;
import com.linkedin.metadata.dao.utils.RecordUtils;
import com.linkedin.metadata.models.AspectSpec;
import com.linkedin.metadata.models.EntityKeyUtils;
import com.linkedin.metadata.models.EntitySpec;
import com.linkedin.metadata.models.registry.EntityRegistry;
import com.linkedin.metadata.snapshot.Snapshot;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.linkedin.metadata.ModelUtils.*;


/**
 * An abstract base class specifying create, update, and read operations against metadata entities and aspects
 * by primary key (urn).
 *
 * This interface is meant to abstract away the storage concerns of these pieces of metadata, permitting
 * any underlying storage system to be used in materializing GMS domain objects, which are implemented using Pegasus
 * {@link RecordTemplate}s.
 *
 * The key requirement of any implementation is being able to bind what is persisted
 * in storage to an aspect {@link RecordTemplate}, using help from the {@link EntityRegistry}.
 *
 * Note that currently, implementations of this interface are responsible for producing Metadata Audit Events on
 * ingestion using {@link #produceMetadataAuditEvent(Urn, RecordTemplate, RecordTemplate)}.
 */
public abstract class EntityService {

  public static final long LATEST_ASPECT_VERSION = 0;

  private final EntityKafkaMetadataEventProducer _producer;
  private final EntityRegistry _entityRegistry;
  private final Map<String, Set<String>> _entityToValidAspects;
  private Boolean _emitAspectSpecificAuditEvent = false;

  protected EntityService(final EntityKafkaMetadataEventProducer producer, final EntityRegistry entityRegistry) {
    _producer = producer;
    _entityRegistry = entityRegistry;
    _entityToValidAspects = buildEntityToValidAspects(entityRegistry);
  }

  public abstract RecordTemplate getAspect(
      @Nonnull final Urn urn,
      @Nonnull final String aspectName,
      @Nonnull long version);

  public abstract Map<Urn, List<RecordTemplate>> getLatestAspects(
      @Nonnull final Set<Urn> urns,
      @Nonnull final Set<String> aspectNames);

  public abstract ListResult<RecordTemplate> listLatestAspects(
      @Nonnull final String aspectName,
      @Nonnull final int start,
      @Nonnull int count);

  public abstract RecordTemplate ingestAspect(
      @Nonnull final Urn urn,
      @Nonnull final String aspectName,
      @Nonnull final RecordTemplate newValue,
      @Nonnull final AuditStamp auditStamp);

  public abstract RecordTemplate updateAspect(
      @Nonnull final Urn urn,
      @Nonnull final String aspectName,
      @Nonnull final RecordTemplate newValue,
      @Nonnull final AuditStamp auditStamp,
      @Nonnull final long version,
      @Nonnull final boolean emitMae);

  /**
   * Default implementations. Subclasses should feel free to override if necessary.
   */
  public void ingestEntities(@Nonnull final List<Entity> entities, @Nonnull final AuditStamp auditStamp) {
    for (final Entity entity : entities) {
      ingestEntity(entity, auditStamp);
    }
  }

  public  void ingestEntity(@Nonnull final Entity entity, @Nonnull final AuditStamp auditStamp) {
    ingestSnapshotUnion(entity.getValue(), auditStamp);
  }

  public Entity getEntity(@Nonnull final Urn urn, @Nonnull final Set<String> aspectNames) {
    return getEntities(Collections.singleton(urn), aspectNames).entrySet().stream()
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }

  public Map<Urn, Entity> getEntities(@Nonnull final Set<Urn> urns, @Nonnull final Set<String> aspectNames) {
    return batchGetSnapshotUnion(urns, aspectNames).entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> toEntity(entry.getValue())));
  }

  public RecordTemplate getLatestAspect(@Nonnull final Urn urn, @Nonnull final String aspectName) {
    return getAspect(urn, aspectName, LATEST_ASPECT_VERSION);
  }

  public void setEmitAspectSpecificAuditEvent(Boolean emitAspectSpecificAuditEvent) {
    _emitAspectSpecificAuditEvent = emitAspectSpecificAuditEvent;
  }

  public Boolean getEmitAspectSpecificAuditEvent() {
    return _emitAspectSpecificAuditEvent;
  }

  protected EntityRegistry getEntityRegistry() {
    return _entityRegistry;
  }

  protected Set<String> getEntityAspectNames(final Urn entityUrn) {
    return _entityToValidAspects.get(urnToEntityName(entityUrn));
  }

  protected Set<String> getEntityAspectNames(final String entityName) {
    return _entityToValidAspects.get(entityName);
  }

  protected void produceMetadataAuditEvent(
      @Nonnull final Urn urn,
      @Nullable final RecordTemplate oldValue,
      @Nonnull final RecordTemplate newValue) {
    // First, try to create a new and an old snapshot.
    final Snapshot newSnapshot = buildSnapshot(urn, newValue);
    Snapshot oldSnapshot = null;
    if (oldValue != null) {
      oldSnapshot = buildSnapshot(urn, oldValue);
    }

    _producer.produceMetadataAuditEvent(urn, oldSnapshot, newSnapshot);

    // 4.1 Produce aspect specific MAE after a successful update
    if (_emitAspectSpecificAuditEvent) {
      _producer.produceAspectSpecificMetadataAuditEvent(urn, oldValue, newValue);
    }
  }

  protected Map<Urn, Snapshot> batchGetSnapshotUnion(@Nonnull final Set<Urn> urns, @Nonnull final Set<String> aspectNames) {
    return batchGetSnapshotRecord(urns, aspectNames).entrySet()
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> toSnapshotUnion(entry.getValue())));
  }

  @Nonnull
  protected Map<Urn, RecordTemplate> batchGetSnapshotRecord(@Nonnull final Set<Urn> urns, @Nonnull final Set<String> aspectNames) {
    return batchGetLatestAspectUnions(urns, aspectNames).entrySet()
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> toSnapshotRecord(entry.getKey(), entry.getValue())));
  }

  @Nonnull
  protected Map<Urn, List<UnionTemplate>> batchGetLatestAspectUnions(@Nonnull final Set<Urn> urns, @Nonnull final Set<String> aspectNames) {
    return getLatestAspects(urns, aspectNames).entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
          return entry.getValue().stream().map(aspectRecord -> toAspectUnion(entry.getKey(), aspectRecord))
              .collect(Collectors.toList());
        }));
  }

  private void ingestSnapshotUnion(@Nonnull final Snapshot snapshotUnion, @Nonnull final AuditStamp auditStamp) {
    final RecordTemplate snapshotRecord = RecordUtils.getSelectedRecordTemplateFromUnion(snapshotUnion);
    final Urn urn = com.linkedin.metadata.dao.utils.ModelUtils.getUrnFromSnapshot(snapshotRecord);
    final List<RecordTemplate> aspectRecordsToIngest = com.linkedin.metadata.dao.utils.ModelUtils.getAspectsFromSnapshot(snapshotRecord);

    aspectRecordsToIngest.stream().map(aspect -> {
      final String aspectName = ModelUtils.getAspectNameFromSchema(aspect.schema());
      return ingestAspect(urn, aspectName, aspect, auditStamp);
    }).collect(Collectors.toList());
  }

  private Snapshot buildSnapshot(@Nonnull final Urn urn, @Nonnull final RecordTemplate aspectValue) {
    final RecordTemplate keyAspectValue = buildKeyAspect(urn);
    return toSnapshotUnion(
        toSnapshotRecord(
            urn,
            ImmutableList.of(toAspectUnion(urn, keyAspectValue), toAspectUnion(urn, aspectValue))
        )
    );
  }

  protected RecordTemplate buildKeyAspect(@Nonnull final Urn urn) {
    final EntitySpec spec = _entityRegistry.getEntitySpec(urnToEntityName(urn));
    final AspectSpec keySpec = spec.getAspectSpecs().stream().filter(AspectSpec::isKey).findFirst().get();
    final RecordDataSchema keySchema = keySpec.getPegasusSchema();
    return EntityKeyUtils.convertUrnToEntityKey(urn, keySchema);
  }

  protected Urn toUrn(final String urnStr) {
    try {
      return Urn.createFromString(urnStr);
    } catch (URISyntaxException e) {
      throw new ModelConversionException(String.format("Failed to convert urn string %s into Urn object ", urnStr), e);
    }
  }

  protected Entity toEntity(@Nonnull final Snapshot snapshot) {
    return new Entity().setValue(snapshot);
  }

  protected Snapshot toSnapshotUnion(@Nonnull final RecordTemplate snapshotRecord) {
    final Snapshot snapshot = new Snapshot();
    RecordUtils.setSelectedRecordTemplateInUnion(
        snapshot,
        snapshotRecord
    );
    return snapshot;
  }

  protected RecordTemplate toSnapshotRecord(
      @Nonnull final Urn urn,
      @Nonnull final List<UnionTemplate> aspectUnionTemplates) {
    final String entityName = urnToEntityName(urn);
    final EntitySpec entitySpec = _entityRegistry.getEntitySpec(entityName);
    return com.linkedin.metadata.dao.utils.ModelUtils.newSnapshot(
        getDataTemplateClassFromSchema(entitySpec.getSnapshotSchema(), RecordTemplate.class),
        urn,
        aspectUnionTemplates);
  }

  protected UnionTemplate toAspectUnion(
      @Nonnull final Urn urn,
      @Nonnull final RecordTemplate aspectRecord) {
    final EntitySpec entitySpec = _entityRegistry.getEntitySpec(urnToEntityName(urn));
    return com.linkedin.metadata.dao.utils.ModelUtils.newAspectUnion(
        getDataTemplateClassFromSchema(entitySpec.getAspectTyperefSchema(), UnionTemplate.class),
        aspectRecord
    );
  }

  private Map<String, Set<String>> buildEntityToValidAspects(final EntityRegistry entityRegistry) {
    return entityRegistry.getEntitySpecs()
        .stream()
        .collect(Collectors.toMap(EntitySpec::getName,
            entry -> entry.getAspectSpecs().stream()
                .map(AspectSpec::getName)
                .collect(Collectors.toSet())
        ));
  }

  public abstract void setWritable();
}
