package com.linkedin.metadata.graph.elastic;

import com.codahale.metrics.Timer;
import com.datahub.util.exception.ESQueryException;
import com.google.common.collect.ImmutableList;
import com.linkedin.common.UrnArray;
import com.linkedin.common.urn.Urn;
import com.linkedin.metadata.graph.LineageDirection;
import com.linkedin.metadata.graph.LineageRegistry;
import com.linkedin.metadata.graph.LineageRegistry.EdgeInfo;
import com.linkedin.metadata.graph.LineageRelationship;
import com.linkedin.metadata.query.filter.Condition;
import com.linkedin.metadata.query.filter.ConjunctiveCriterion;
import com.linkedin.metadata.query.filter.Criterion;
import com.linkedin.metadata.query.filter.Filter;
import com.linkedin.metadata.query.filter.RelationshipDirection;
import com.linkedin.metadata.query.filter.RelationshipFilter;
import com.linkedin.metadata.utils.elasticsearch.IndexConvention;
import com.linkedin.metadata.utils.metrics.MetricUtils;
import io.opentelemetry.extension.annotations.WithSpan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.cache.Cache;

import static com.linkedin.metadata.graph.elastic.ElasticSearchGraphService.INDEX_NAME;


/**
 * A search DAO for Elasticsearch backend.
 */
@Slf4j
@RequiredArgsConstructor
public class ESGraphQueryDAO {

  private final RestHighLevelClient client;
  private final LineageRegistry lineageRegistry;
  private final IndexConvention indexConvention;
  private final Cache cache;

  private static final int MAX_ELASTIC_RESULT = 10000;
  private static final String SOURCE = "source";
  private static final String DESTINATION = "destination";
  private static final String RELATIONSHIP_TYPE = "relationshipType";

  @Nonnull
  public static void addFilterToQueryBuilder(@Nonnull Filter filter, String node, BoolQueryBuilder rootQuery) {
    BoolQueryBuilder orQuery = new BoolQueryBuilder();
    for (ConjunctiveCriterion conjunction : filter.getOr()) {
      final BoolQueryBuilder andQuery = new BoolQueryBuilder();
      final List<Criterion> criterionArray = conjunction.getAnd();
      if (!criterionArray.stream().allMatch(criterion -> Condition.EQUAL.equals(criterion.getCondition()))) {
        throw new RuntimeException("Currently Elastic query filter only supports EQUAL condition " + criterionArray);
      }
      criterionArray.forEach(
          criterion -> andQuery.must(QueryBuilders.termQuery(node + "." + criterion.getField(), criterion.getValue())));
      orQuery.should(andQuery);
    }
    rootQuery.must(orQuery);
  }

  private SearchResponse executeSearchQuery(@Nonnull final QueryBuilder query, final int offset, final int count) {
    SearchRequest searchRequest = new SearchRequest();

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

    searchSourceBuilder.from(offset);
    searchSourceBuilder.size(count);

    searchSourceBuilder.query(query);

    searchRequest.source(searchSourceBuilder);

    searchRequest.indices(indexConvention.getIndexName(INDEX_NAME));

    try (Timer.Context ignored = MetricUtils.timer(this.getClass(), "esQuery").time()) {
      return client.search(searchRequest, RequestOptions.DEFAULT);
    } catch (Exception e) {
      log.error("Search query failed", e);
      throw new ESQueryException("Search query failed:", e);
    }
  }

  public SearchResponse getSearchResponse(@Nullable final String sourceType, @Nonnull final Filter sourceEntityFilter,
      @Nullable final String destinationType, @Nonnull final Filter destinationEntityFilter,
      @Nonnull final List<String> relationshipTypes, @Nonnull final RelationshipFilter relationshipFilter,
      final int offset, final int count) {
    BoolQueryBuilder finalQuery =
        buildQuery(sourceType, sourceEntityFilter, destinationType, destinationEntityFilter, relationshipTypes,
            relationshipFilter);

    return executeSearchQuery(finalQuery, offset, count);
  }

  public static BoolQueryBuilder buildQuery(@Nullable final String sourceType, @Nonnull final Filter sourceEntityFilter,
      @Nullable final String destinationType, @Nonnull final Filter destinationEntityFilter,
      @Nonnull final List<String> relationshipTypes, @Nonnull final RelationshipFilter relationshipFilter) {
    BoolQueryBuilder finalQuery = QueryBuilders.boolQuery();

    final RelationshipDirection relationshipDirection = relationshipFilter.getDirection();

    // set source filter
    String sourceNode = relationshipDirection == RelationshipDirection.OUTGOING ? SOURCE : DESTINATION;
    if (sourceType != null && sourceType.length() > 0) {
      finalQuery.must(QueryBuilders.termQuery(sourceNode + ".entityType", sourceType));
    }
    addFilterToQueryBuilder(sourceEntityFilter, sourceNode, finalQuery);

    // set destination filter
    String destinationNode = relationshipDirection == RelationshipDirection.OUTGOING ? DESTINATION : SOURCE;
    if (destinationType != null && destinationType.length() > 0) {
      finalQuery.must(QueryBuilders.termQuery(destinationNode + ".entityType", destinationType));
    }
    addFilterToQueryBuilder(destinationEntityFilter, destinationNode, finalQuery);

    // set relationship filter
    if (relationshipTypes.size() > 0) {
      BoolQueryBuilder relationshipQuery = QueryBuilders.boolQuery();
      relationshipTypes.forEach(
          relationshipType -> relationshipQuery.should(QueryBuilders.termQuery(RELATIONSHIP_TYPE, relationshipType)));
      finalQuery.must(relationshipQuery);
    }
    return finalQuery;
  }

  @WithSpan
  public LineageResponse getLineage(@Nonnull Urn entityUrn, @Nonnull LineageDirection direction, int offset, int count,
      int maxHops) {
    LineageResponse response = cache.get(Triple.of(entityUrn, direction, maxHops), LineageResponse.class);
    if (response == null) {
      List<LineageRelationship> result = new ArrayList<>();

      // Do a Level-order BFS
      Map<Urn, List<Urn>> visitedEntitiesWithPath = new HashMap<>();
      visitedEntitiesWithPath.put(entityUrn, Collections.emptyList());
      List<Urn> currentLevel = ImmutableList.of(entityUrn);

      for (int i = 0; i < maxHops; i++) {
        if (currentLevel.isEmpty()) {
          break;
        }

        List<LineageRelationship> oneHopRelationships =
            getLineageRelationships(currentLevel, direction, visitedEntitiesWithPath);
        result.addAll(oneHopRelationships);
        currentLevel = oneHopRelationships.stream().map(LineageRelationship::getEntity).collect(Collectors.toList());
      }
      response = new LineageResponse(result.size(), result);
      cache.put(Triple.of(entityUrn, direction, maxHops), response);
    }

    List<LineageRelationship> subList;
    if (offset >= response.getTotal()) {
      subList = Collections.emptyList();
    } else {
      subList = response.getLineageRelationships().subList(offset, Math.min(offset + count, response.getTotal()));
    }

    return new LineageResponse(response.getTotal(), subList);
  }

  // Get 1-hop lineage relationships
  @WithSpan
  private List<LineageRelationship> getLineageRelationships(@Nonnull List<Urn> entityUrns,
      @Nonnull LineageDirection direction, Map<Urn, List<Urn>> visitedEntitiesWithPath) {
    Map<String, List<Urn>> urnsPerEntityType = entityUrns.stream().collect(Collectors.groupingBy(Urn::getEntityType));
    Map<String, List<EdgeInfo>> edgesPerEntityType = urnsPerEntityType.keySet()
        .stream()
        .collect(Collectors.toMap(Function.identity(),
            entityType -> lineageRegistry.getLineageRelationships(entityType, direction)));
    BoolQueryBuilder finalQuery = QueryBuilders.boolQuery();
    urnsPerEntityType.forEach((entityType, urns) -> finalQuery.should(
        getQueryForLineage(urns, edgesPerEntityType.getOrDefault(entityType, Collections.emptyList()))));
    SearchResponse response = executeSearchQuery(finalQuery, 0, MAX_ELASTIC_RESULT);
    Set<Urn> entityUrnSet = new HashSet<>(entityUrns);
    Set<Pair<String, EdgeInfo>> validEdges = edgesPerEntityType.entrySet()
        .stream()
        .flatMap(entry -> entry.getValue().stream().map(edgeInfo -> Pair.of(entry.getKey(), edgeInfo)))
        .collect(Collectors.toSet());
    return extractRelationships(entityUrnSet, response, validEdges, visitedEntitiesWithPath);
  }

  // Extract relationships from search response
  @SneakyThrows
  @WithSpan
  private List<LineageRelationship> extractRelationships(@Nonnull Set<Urn> entityUrns,
      @Nonnull SearchResponse searchResponse, Set<Pair<String, EdgeInfo>> validEdges,
      Map<Urn, List<Urn>> visitedEntitiesWithPath) {
    List<LineageRelationship> result = new LinkedList<>();
    for (SearchHit hit : searchResponse.getHits().getHits()) {
      Map<String, Object> document = hit.getSourceAsMap();
      Urn sourceUrn = Urn.createFromString(((Map<String, Object>) document.get(SOURCE)).get("urn").toString());
      Urn destinationUrn =
          Urn.createFromString(((Map<String, Object>) document.get(DESTINATION)).get("urn").toString());
      String type = document.get(RELATIONSHIP_TYPE).toString();

      // Potential outgoing edge
      if (entityUrns.contains(sourceUrn)) {
        List<Urn> pathSoFar = visitedEntitiesWithPath.get(sourceUrn);
        // Skip if already visited
        // Skip if edge is not a valid outgoing edge
        if (!visitedEntitiesWithPath.containsKey(destinationUrn) && validEdges.contains(
            Pair.of(sourceUrn.getEntityType(), new EdgeInfo(type, RelationshipDirection.OUTGOING)))) {
          visitedEntitiesWithPath.put(destinationUrn,
              ImmutableList.<Urn>builder().addAll(pathSoFar).add(destinationUrn).build());
          result.add(
              new LineageRelationship().setType(type).setEntity(destinationUrn).setPath(new UrnArray(pathSoFar)));
        }
      }

      // Potential incoming edge
      if (entityUrns.contains(destinationUrn)) {
        List<Urn> pathSoFar = visitedEntitiesWithPath.get(destinationUrn);
        // Skip if already visited
        // Skip if edge is not a valid outgoing edge
        if (!visitedEntitiesWithPath.containsKey(sourceUrn) && validEdges.contains(
            Pair.of(destinationUrn.getEntityType(), new EdgeInfo(type, RelationshipDirection.INCOMING)))) {
          visitedEntitiesWithPath.put(sourceUrn, ImmutableList.<Urn>builder().addAll(pathSoFar).add(sourceUrn).build());
          result.add(new LineageRelationship().setType(type).setEntity(sourceUrn).setPath(new UrnArray(pathSoFar)));
        }
      }
    }
    return result;
  }

  public QueryBuilder getQueryForLineage(List<Urn> urns, List<EdgeInfo> lineageEdges) {
    BoolQueryBuilder query = QueryBuilders.boolQuery();
    if (lineageEdges.isEmpty()) {
      return query;
    }
    Map<RelationshipDirection, List<EdgeInfo>> edgesByDirection =
        lineageEdges.stream().collect(Collectors.groupingBy(EdgeInfo::getDirection));
    List<EdgeInfo> outgoingEdges =
        edgesByDirection.getOrDefault(RelationshipDirection.OUTGOING, Collections.emptyList());
    if (!outgoingEdges.isEmpty()) {
      BoolQueryBuilder outgoingEdgeQuery = QueryBuilders.boolQuery();
      outgoingEdgeQuery.must(buildUrnFilters(urns, SOURCE));
      outgoingEdgeQuery.must(buildEdgeFilters(outgoingEdges));
      query.should(outgoingEdgeQuery);
    }

    List<EdgeInfo> incomingEdges =
        edgesByDirection.getOrDefault(RelationshipDirection.INCOMING, Collections.emptyList());
    if (!incomingEdges.isEmpty()) {
      BoolQueryBuilder incomingEdgeQuery = QueryBuilders.boolQuery();
      incomingEdgeQuery.must(buildUrnFilters(urns, DESTINATION));
      incomingEdgeQuery.must(buildEdgeFilters(incomingEdges));
      query.should(incomingEdgeQuery);
    }
    return query;
  }

  public QueryBuilder buildUrnFilters(List<Urn> urns, String prefix) {
    return QueryBuilders.termsQuery(prefix + ".urn", urns.stream().map(Object::toString).collect(Collectors.toList()));
  }

  public QueryBuilder buildEdgeFilters(List<EdgeInfo> edgeInfos) {
    return QueryBuilders.termsQuery("relationshipType",
        edgeInfos.stream().map(EdgeInfo::getType).distinct().collect(Collectors.toList()));
  }

  @Value
  public static class LineageResponse {
    int total;
    List<LineageRelationship> lineageRelationships;
  }
}
