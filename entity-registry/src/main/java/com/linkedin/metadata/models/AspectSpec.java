package com.linkedin.metadata.models;

import com.linkedin.data.schema.RecordDataSchema;
import com.linkedin.metadata.models.annotation.AspectAnnotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;


public class AspectSpec {

  private final AspectAnnotation _aspectAnnotation;
  private final Map<String, SearchableFieldSpec> _searchableFieldSpecs;
  private final Map<String, RelationshipFieldSpec> _relationshipFieldSpecs;
  private final Map<String, TimeseriesFieldSpec> _timeseriesFieldSpecs;
  private final Map<String, TimeseriesFieldCollectionSpec> _timeseriesFieldCollectionSpecs;

  // Classpath & Pegasus-specific: Temporary.
  private final RecordDataSchema _schema;

  public AspectSpec(@Nonnull final AspectAnnotation aspectAnnotation,
      @Nonnull final List<SearchableFieldSpec> searchableFieldSpecs,
      @Nonnull final List<RelationshipFieldSpec> relationshipFieldSpecs,
      @Nonnull final List<TimeseriesFieldSpec> timeseriesFieldSpecs,
      @Nonnull final List<TimeseriesFieldCollectionSpec> timeseriesFieldCollectionSpecs,
      final RecordDataSchema schema) {
    _aspectAnnotation = aspectAnnotation;
    _searchableFieldSpecs = searchableFieldSpecs.stream()
        .collect(Collectors.toMap(spec -> spec.getPath().toString(), spec -> spec, (val1, val2) -> val1));
    _relationshipFieldSpecs = relationshipFieldSpecs.stream()
        .collect(Collectors.toMap(spec -> spec.getPath().toString(), spec -> spec, (val1, val2) -> val1));
    _timeseriesFieldSpecs = timeseriesFieldSpecs.stream()
        .collect(Collectors.toMap(spec -> spec.getTimeseriesFieldAnnotation().getStatName(), spec -> spec,
            (val1, val2) -> val1));
    _timeseriesFieldCollectionSpecs = timeseriesFieldCollectionSpecs.stream()
        .collect(Collectors.toMap(spec -> spec.getTimeseriesFieldCollectionAnnotation().getCollectionName(), spec -> spec,
            (val1, val2) -> val1));
    _schema = schema;
  }

  public String getName() {
    return _aspectAnnotation.getName();
  }

  public boolean isTimeseries() {
    return _aspectAnnotation.isTimeseries();
  }

  public Map<String, SearchableFieldSpec> getSearchableFieldSpecMap() {
    return _searchableFieldSpecs;
  }

  public Map<String, RelationshipFieldSpec> getRelationshipFieldSpecMap() {
    return _relationshipFieldSpecs;
  }

  public Map<String, TimeseriesFieldSpec> getTimeseriesFieldSpecMap() {
    return _timeseriesFieldSpecs;
  }

  public Map<String, TimeseriesFieldCollectionSpec> getTimeseriesFieldCollectionSpecMap() {
    return _timeseriesFieldCollectionSpecs;
  }

  public List<SearchableFieldSpec> getSearchableFieldSpecs() {
    return new ArrayList<>(_searchableFieldSpecs.values());
  }

  public List<RelationshipFieldSpec> getRelationshipFieldSpecs() {
    return new ArrayList<>(_relationshipFieldSpecs.values());
  }

  public List<TimeseriesFieldSpec> getTimeseriesFieldSpecs() {
    return new ArrayList<>(_timeseriesFieldSpecs.values());
  }

  public List<TimeseriesFieldCollectionSpec> getTimeseriesFieldCollectionSpecs() {
    return new ArrayList<>(_timeseriesFieldCollectionSpecs.values());
  }

  public RecordDataSchema getPegasusSchema() {
    return _schema;
  }
}


