package com.linkedin.metadata.graph;

import com.linkedin.metadata.models.annotation.RelationshipAnnotation;
import com.linkedin.metadata.models.registry.EntityRegistry;
import com.linkedin.metadata.query.filter.RelationshipDirection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Value;
import org.apache.commons.lang3.tuple.Triple;


public class LineageRegistry {

  private final Map<String, LineageSpec> _lineageSpecMap;

  public LineageRegistry(EntityRegistry entityRegistry) {
    _lineageSpecMap = buildLineageSpecs(entityRegistry);
  }

  private Map<String, LineageSpec> buildLineageSpecs(EntityRegistry entityRegistry) {
    // 1. Flatten relationship annotations into a list of lineage edges (source, dest, type, isUpstream)
    Collection<LineageEdge> lineageEdges = entityRegistry.getEntitySpecs()
        .entrySet()
        .stream()
        .flatMap(entry -> entry.getValue()
            .getRelationshipFieldSpecs()
            .stream()
            .flatMap(
                spec -> getLineageEdgesFromRelationshipAnnotation(entry.getKey(), spec.getRelationshipAnnotation())))
        // If there are multiple edges with the same source, dest, edge type, get one of them
        .collect(Collectors.toMap(edge -> Triple.of(edge.getSourceEntity(), edge.getDestEntity(), edge.getType()),
            Function.identity(), (x1, x2) -> x1))
        .values();

    // 2. Figure out the upstream and downstream edges of each entity type
    Map<String, Set<EdgeInfo>> upstreamPerEntity = new HashMap<>();
    Map<String, Set<EdgeInfo>> downstreamPerEntity = new HashMap<>();
    // A downstreamOf B  ---> A -> upstream (downstreamOf, OUTGOING), B -> downstream (downstreamOf, INCOMING)
    // A produces B ---> A -> downstream (produces, OUTGOING), B -> upstream (produces, INCOMING)
    for (LineageEdge edge : lineageEdges) {
      if (edge.isUpstream()) {
        upstreamPerEntity.computeIfAbsent(edge.sourceEntity, (k) -> new HashSet<>())
            .add(new EdgeInfo(edge.type, RelationshipDirection.OUTGOING));
        downstreamPerEntity.computeIfAbsent(edge.destEntity, (k) -> new HashSet<>())
            .add(new EdgeInfo(edge.type, RelationshipDirection.INCOMING));
      } else {
        downstreamPerEntity.computeIfAbsent(edge.sourceEntity, (k) -> new HashSet<>())
            .add(new EdgeInfo(edge.type, RelationshipDirection.OUTGOING));
        upstreamPerEntity.computeIfAbsent(edge.destEntity, (k) -> new HashSet<>())
            .add(new EdgeInfo(edge.type, RelationshipDirection.INCOMING));
      }
    }

    return entityRegistry.getEntitySpecs()
        .keySet()
        .stream()
        .collect(Collectors.toMap(Function.identity(), entityName -> new LineageSpec(
            new ArrayList<>(upstreamPerEntity.getOrDefault(entityName, Collections.emptySet())),
            new ArrayList<>(downstreamPerEntity.getOrDefault(entityName, Collections.emptySet())))));
  }

  private Stream<LineageEdge> getLineageEdgesFromRelationshipAnnotation(String sourceEntity,
      RelationshipAnnotation annotation) {
    if (!annotation.isLineage()) {
      return Stream.empty();
    }
    return annotation.getValidDestinationTypes()
        .stream()
        .map(destEntity -> new LineageEdge(sourceEntity, destEntity, annotation.getName(), annotation.isUpstream()));
  }

  public LineageSpec getLineageSpec(String entityName) {
    return _lineageSpecMap.get(entityName);
  }

  public List<EdgeInfo> getLineageRelationships(String entityName, LineageDirection direction) {
    LineageSpec spec = getLineageSpec(entityName);
    if (spec == null) {
      return Collections.emptyList();
    }
    
    if (direction == LineageDirection.UPSTREAM) {
      return spec.getUpstreamEdges();
    }
    return spec.getDownstreamEdges();
  }

  @Value
  private static class LineageEdge {
    String sourceEntity;
    String destEntity;
    String type;
    boolean isUpstream;
  }

  @Value
  public static class LineageSpec {
    List<EdgeInfo> upstreamEdges;
    List<EdgeInfo> downstreamEdges;
  }

  @Value
  public static class EdgeInfo {
    String type;
    RelationshipDirection direction;
  }
}
