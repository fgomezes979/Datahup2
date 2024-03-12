package com.linkedin.metadata.graph;

import static com.linkedin.metadata.search.utils.QueryUtils.EMPTY_FILTER;
import static com.linkedin.metadata.search.utils.QueryUtils.newFilter;
import static com.linkedin.metadata.search.utils.QueryUtils.newRelationshipFilter;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.linkedin.common.urn.DataFlowUrn;
import com.linkedin.common.urn.DataJobUrn;
import com.linkedin.common.urn.Urn;
import com.linkedin.data.schema.annotation.PathSpecBasedSchemaAnnotationVisitor;
import com.linkedin.metadata.config.search.GraphQueryConfiguration;
import com.linkedin.metadata.graph.dgraph.DgraphGraphService;
import com.linkedin.metadata.graph.neo4j.Neo4jGraphService;
import com.linkedin.metadata.query.filter.Filter;
import com.linkedin.metadata.query.filter.RelationshipDirection;
import com.linkedin.metadata.query.filter.RelationshipFilter;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Base class for testing any GraphService implementation. Derive the test class from this base and
 * get your GraphService implementation tested with all these tests.
 *
 * <p>You can add implementation specific tests in derived classes, or add general tests here and
 * have all existing implementations tested in the same way.
 *
 * <p>The `getPopulatedGraphService` method calls `GraphService.addEdge` to provide a populated
 * Graph. Feel free to add a test to your test implementation that calls `getPopulatedGraphService`
 * and asserts the state of the graph in an implementation specific way.
 */
public abstract class GraphServiceTestBase extends AbstractTestNGSpringContextTests {

  private static class RelatedEntityComparator implements Comparator<RelatedEntity> {
    @Override
    public int compare(RelatedEntity left, RelatedEntity right) {
      int cmp = left.relationshipType.compareTo(right.relationshipType);
      if (cmp != 0) {
        return cmp;
      }
      return left.urn.compareTo(right.urn);
    }
  }

  protected static final RelatedEntityComparator RELATED_ENTITY_COMPARATOR =
      new RelatedEntityComparator();

  /** Some test URN types. */
  protected static String datasetType = "dataset";

  protected static String userType = "user";

  /** Some test datasets. */
  protected static String datasetOneUrnString =
      "urn:li:" + datasetType + ":(urn:li:dataPlatform:type,SampleDatasetOne,PROD)";

  protected static String datasetTwoUrnString =
      "urn:li:" + datasetType + ":(urn:li:dataPlatform:type,SampleDatasetTwo,PROD)";
  protected static String datasetThreeUrnString =
      "urn:li:" + datasetType + ":(urn:li:dataPlatform:type,SampleDatasetThree,PROD)";
  protected static String datasetFourUrnString =
      "urn:li:" + datasetType + ":(urn:li:dataPlatform:type,SampleDatasetFour,PROD)";
  protected static String datasetFiveUrnString =
      "urn:li:" + datasetType + ":(urn:li:dataPlatform:type,SampleDatasetFive,PROD)";

  protected static final String schemaFieldUrnOneString =
      "urn:li:schemaField:(urn:li:dataset:(urn:li:dataPlatform:type,SampleDatasetFive,PROD),fieldOne)";
  protected static final String schemaFieldUrnTwoString =
      "urn:li:schemaField:(urn:li:dataset:(urn:li:dataPlatform:type,SampleDatasetFour,PROD),fieldTwo)";

  protected static final String lifeCycleOwnerOneString =
      "urn:li:dataJob:(urn:li:dataFlow:(fivetran,calendar_elected,PROD),calendar_elected)";
  protected static final String lifeCycleOwnerTwoString =
      "urn:li:dataset:(urn:li:dataPlatform:hive,SampleHiveDataset,PROD)";

  protected static Urn datasetOneUrn = createFromString(datasetOneUrnString);
  protected static Urn datasetTwoUrn = createFromString(datasetTwoUrnString);
  protected static Urn datasetThreeUrn = createFromString(datasetThreeUrnString);
  protected static Urn datasetFourUrn = createFromString(datasetFourUrnString);
  protected static Urn datasetFiveUrn = createFromString(datasetFiveUrnString);
  protected static final Urn schemaFieldUrnOne = createFromString(schemaFieldUrnOneString);
  protected static final Urn schemaFieldUrnTwo = createFromString(schemaFieldUrnTwoString);
  protected static final Urn lifeCycleOwnerOne = createFromString(lifeCycleOwnerOneString);
  protected static final Urn lifeCycleOwnerTwo = createFromString(lifeCycleOwnerTwoString);

  protected static String unknownUrnString = "urn:li:unknown:(urn:li:unknown:Unknown)";

  /** Some dataset owners. */
  protected static String userOneUrnString =
      "urn:li:" + userType + ":(urn:li:user:system,Ingress,PROD)";

  protected static String userTwoUrnString =
      "urn:li:" + userType + ":(urn:li:user:individual,UserA,DEV)";

  protected static Urn userOneUrn = createFromString(userOneUrnString);
  protected static Urn userTwoUrn = createFromString(userTwoUrnString);

  protected static Urn unknownUrn = createFromString(unknownUrnString);

  /** Some data jobs */
  protected static Urn dataJobOneUrn =
      new DataJobUrn(new DataFlowUrn("orchestrator", "flow", "cluster"), "job1");

  protected static Urn dataJobTwoUrn =
      new DataJobUrn(new DataFlowUrn("orchestrator", "flow", "cluster"), "job2");

  /** Some test relationships. */
  protected static String downstreamOf = "DownstreamOf";

  protected static String hasOwner = "HasOwner";
  protected static String knowsUser = "KnowsUser";
  protected static String produces = "Produces";
  protected static String consumes = "Consumes";
  protected static Set<String> allRelationshipTypes =
      new HashSet<>(Arrays.asList(downstreamOf, hasOwner, knowsUser));

  /** Some expected related entities. */
  protected static RelatedEntity downstreamOfDatasetOneRelatedEntity =
      new RelatedEntity(downstreamOf, datasetOneUrnString);

  protected static RelatedEntity downstreamOfDatasetTwoRelatedEntity =
      new RelatedEntity(downstreamOf, datasetTwoUrnString);
  protected static RelatedEntity downstreamOfDatasetThreeRelatedEntity =
      new RelatedEntity(downstreamOf, datasetThreeUrnString);
  protected static RelatedEntity downstreamOfDatasetFourRelatedEntity =
      new RelatedEntity(downstreamOf, datasetFourUrnString);
  protected static final RelatedEntity downstreamOfSchemaFieldOneVia =
      new RelatedEntity(downstreamOf, schemaFieldUrnOneString, lifeCycleOwnerOneString);
  protected static final RelatedEntity downstreamOfSchemaFieldOne =
      new RelatedEntity(downstreamOf, schemaFieldUrnOneString);
  protected static final RelatedEntity downstreamOfSchemaFieldTwoVia =
      new RelatedEntity(downstreamOf, schemaFieldUrnTwoString, lifeCycleOwnerOneString);
  protected static final RelatedEntity downstreamOfSchemaFieldTwo =
      new RelatedEntity(downstreamOf, schemaFieldUrnTwoString);

  protected static RelatedEntity hasOwnerDatasetOneRelatedEntity =
      new RelatedEntity(hasOwner, datasetOneUrnString);
  protected static RelatedEntity hasOwnerDatasetTwoRelatedEntity =
      new RelatedEntity(hasOwner, datasetTwoUrnString);
  protected static RelatedEntity hasOwnerDatasetThreeRelatedEntity =
      new RelatedEntity(hasOwner, datasetThreeUrnString);
  protected static RelatedEntity hasOwnerDatasetFourRelatedEntity =
      new RelatedEntity(hasOwner, datasetFourUrnString);
  protected static RelatedEntity hasOwnerUserOneRelatedEntity =
      new RelatedEntity(hasOwner, userOneUrnString);
  protected static RelatedEntity hasOwnerUserTwoRelatedEntity =
      new RelatedEntity(hasOwner, userTwoUrnString);

  protected static RelatedEntity knowsUserOneRelatedEntity =
      new RelatedEntity(knowsUser, userOneUrnString);
  protected static RelatedEntity knowsUserTwoRelatedEntity =
      new RelatedEntity(knowsUser, userTwoUrnString);

  /** Some relationship filters. */
  protected static RelationshipFilter outgoingRelationships =
      newRelationshipFilter(EMPTY_FILTER, RelationshipDirection.OUTGOING);

  protected static RelationshipFilter incomingRelationships =
      newRelationshipFilter(EMPTY_FILTER, RelationshipDirection.INCOMING);
  protected static RelationshipFilter undirectedRelationships =
      newRelationshipFilter(EMPTY_FILTER, RelationshipDirection.UNDIRECTED);

  /** Any source and destination type value. */
  protected static @Nullable List<String> anyType = null;

  /** Timeout used to test concurrent ops in doTestConcurrentOp. */
  protected Duration getTestConcurrentOpTimeout() {
    return Duration.ofMinutes(1);
  }

  @BeforeMethod
  public void disableAssert() {
    PathSpecBasedSchemaAnnotationVisitor.class
        .getClassLoader()
        .setClassAssertionStatus(PathSpecBasedSchemaAnnotationVisitor.class.getName(), false);
  }

  @Test
  public void testStaticUrns() {
    assertNotNull(datasetOneUrn);
    assertNotNull(datasetTwoUrn);
    assertNotNull(datasetThreeUrn);
    assertNotNull(datasetFourUrn);

    assertNotNull(userOneUrn);
    assertNotNull(userTwoUrn);
  }

  /**
   * Provides the current GraphService instance to test. This is being called by the test method at
   * most once. The serviced graph should be empty.
   *
   * @return the GraphService instance to test
   * @throws Exception on failure
   */
  @Nonnull
  protected abstract GraphService getGraphService() throws Exception;

  /**
   * Graph services that support multi-path search should override this method to provide a
   * multi-path search enabled GraphService instance.
   *
   * @param enableMultiPathSearch
   * @return
   * @throws Exception
   */
  @Nonnull
  protected GraphService getGraphService(boolean enableMultiPathSearch) throws Exception {
    return getGraphService();
  }

  /**
   * Allows the specific GraphService test implementation to wait for GraphService writes to be
   * synced / become available to reads.
   *
   * @throws Exception on failure
   */
  protected abstract void syncAfterWrite() throws Exception;

  /**
   * Calls getGraphService to retrieve the test GraphService and populates it with edges via
   * `GraphService.addEdge`.
   *
   * @return test GraphService
   * @throws Exception on failure
   */
  protected GraphService getPopulatedGraphService() throws Exception {
    GraphService service = getGraphService();

    List<Edge> edges =
        Arrays.asList(
            new Edge(datasetTwoUrn, datasetOneUrn, downstreamOf, null, null, null, null, null),
            new Edge(datasetThreeUrn, datasetTwoUrn, downstreamOf, null, null, null, null, null),
            new Edge(datasetFourUrn, datasetTwoUrn, downstreamOf, null, null, null, null, null),
            new Edge(datasetOneUrn, userOneUrn, hasOwner, null, null, null, null, null),
            new Edge(datasetTwoUrn, userOneUrn, hasOwner, null, null, null, null, null),
            new Edge(datasetThreeUrn, userTwoUrn, hasOwner, null, null, null, null, null),
            new Edge(datasetFourUrn, userTwoUrn, hasOwner, null, null, null, null, null),
            new Edge(userOneUrn, userTwoUrn, knowsUser, null, null, null, null, null),
            new Edge(userTwoUrn, userOneUrn, knowsUser, null, null, null, null, null),
            new Edge(
                schemaFieldUrnOne,
                schemaFieldUrnTwo,
                downstreamOf,
                0L,
                null,
                0L,
                null,
                null,
                lifeCycleOwnerOne,
                lifeCycleOwnerOne),
            new Edge(
                schemaFieldUrnOne,
                schemaFieldUrnTwo,
                downstreamOf,
                0L,
                null,
                0L,
                null,
                null,
                lifeCycleOwnerTwo,
                null));

    edges.forEach(service::addEdge);
    syncAfterWrite();

    return service;
  }

  protected GraphService getLineagePopulatedGraphService() throws Exception {
    return getLineagePopulatedGraphService(
        GraphQueryConfiguration.testDefaults.isEnableMultiPathSearch());
  }

  protected GraphService getLineagePopulatedGraphService(boolean multiPathSearch) throws Exception {
    GraphService service = getGraphService(multiPathSearch);

    List<Edge> edges =
        Arrays.asList(
            new Edge(datasetTwoUrn, datasetOneUrn, downstreamOf, null, null, null, null, null),
            new Edge(datasetThreeUrn, datasetTwoUrn, downstreamOf, null, null, null, null, null),
            new Edge(datasetFourUrn, datasetTwoUrn, downstreamOf, null, null, null, null, null),
            new Edge(datasetOneUrn, userOneUrn, hasOwner, null, null, null, null, null),
            new Edge(datasetTwoUrn, userOneUrn, hasOwner, null, null, null, null, null),
            new Edge(datasetThreeUrn, userTwoUrn, hasOwner, null, null, null, null, null),
            new Edge(datasetFourUrn, userTwoUrn, hasOwner, null, null, null, null, null),
            new Edge(userOneUrn, userTwoUrn, knowsUser, null, null, null, null, null),
            new Edge(userTwoUrn, userOneUrn, knowsUser, null, null, null, null, null),
            new Edge(dataJobOneUrn, datasetOneUrn, consumes, null, null, null, null, null),
            new Edge(dataJobOneUrn, datasetTwoUrn, consumes, null, null, null, null, null),
            new Edge(dataJobOneUrn, datasetThreeUrn, produces, null, null, null, null, null),
            new Edge(dataJobOneUrn, datasetFourUrn, produces, null, null, null, null, null),
            new Edge(dataJobTwoUrn, datasetOneUrn, consumes, null, null, null, null, null),
            new Edge(dataJobTwoUrn, datasetTwoUrn, consumes, null, null, null, null, null),
            new Edge(dataJobTwoUrn, dataJobOneUrn, downstreamOf, null, null, null, null, null));

    edges.forEach(service::addEdge);
    syncAfterWrite();

    return service;
  }

  protected static @Nullable Urn createFromString(@Nonnull String rawUrn) {
    try {
      return Urn.createFromString(rawUrn);
    } catch (URISyntaxException e) {
      return null;
    }
  }

  protected void assertEqualsAnyOrder(RelatedEntitiesResult actual, List<RelatedEntity> expected) {
    assertEqualsAnyOrder(
        actual, new RelatedEntitiesResult(0, expected.size(), expected.size(), expected));
  }

  protected void assertEqualsAnyOrder(
      RelatedEntitiesResult actual, RelatedEntitiesResult expected) {
    assertEquals(actual.start, expected.start);
    assertEquals(actual.count, expected.count);
    assertEquals(actual.total, expected.total);
    assertEqualsAnyOrder(actual.entities, expected.entities, RELATED_ENTITY_COMPARATOR);
  }

  protected <T> void assertEqualsAnyOrder(List<T> actual, List<T> expected) {
    assertEquals(
        actual.stream().sorted().collect(Collectors.toList()),
        expected.stream().sorted().collect(Collectors.toList()));
  }

  protected <T> void assertEqualsAnyOrder(
      List<T> actual, List<T> expected, Comparator<T> comparator) {
    assertEquals(
        actual.stream().sorted(comparator).collect(Collectors.toList()),
        expected.stream().sorted(comparator).collect(Collectors.toList()));
  }

  @DataProvider(name = "AddEdgeTests")
  public Object[][] getAddEdgeTests() {
    return new Object[][] {
      new Object[] {Arrays.asList(), Arrays.asList(), Arrays.asList()},
      new Object[] {
        Arrays.asList(
            new Edge(datasetOneUrn, datasetTwoUrn, downstreamOf, null, null, null, null, null)),
        Arrays.asList(downstreamOfDatasetTwoRelatedEntity),
        Arrays.asList(downstreamOfDatasetOneRelatedEntity)
      },
      new Object[] {
        Arrays.asList(
            new Edge(datasetOneUrn, datasetTwoUrn, downstreamOf, null, null, null, null, null),
            new Edge(datasetTwoUrn, datasetThreeUrn, downstreamOf, null, null, null, null, null)),
        Arrays.asList(downstreamOfDatasetTwoRelatedEntity, downstreamOfDatasetThreeRelatedEntity),
        Arrays.asList(downstreamOfDatasetOneRelatedEntity, downstreamOfDatasetTwoRelatedEntity)
      },
      new Object[] {
        Arrays.asList(
            new Edge(datasetOneUrn, datasetTwoUrn, downstreamOf, null, null, null, null, null),
            new Edge(datasetOneUrn, userOneUrn, hasOwner, null, null, null, null, null),
            new Edge(datasetTwoUrn, userTwoUrn, hasOwner, null, null, null, null, null),
            new Edge(userOneUrn, userTwoUrn, knowsUser, null, null, null, null, null)),
        Arrays.asList(
            downstreamOfDatasetTwoRelatedEntity,
            hasOwnerUserOneRelatedEntity,
            hasOwnerUserTwoRelatedEntity,
            knowsUserTwoRelatedEntity),
        Arrays.asList(
            downstreamOfDatasetOneRelatedEntity,
            hasOwnerDatasetOneRelatedEntity,
            hasOwnerDatasetTwoRelatedEntity,
            knowsUserOneRelatedEntity)
      },
      new Object[] {
        Arrays.asList(
            new Edge(userOneUrn, userOneUrn, knowsUser, null, null, null, null, null),
            new Edge(userOneUrn, userOneUrn, knowsUser, null, null, null, null, null),
            new Edge(userOneUrn, userOneUrn, knowsUser, null, null, null, null, null)),
        Arrays.asList(knowsUserOneRelatedEntity),
        Arrays.asList(knowsUserOneRelatedEntity)
      }
    };
  }

  @Test(dataProvider = "AddEdgeTests")
  public void testAddEdge(
      List<Edge> edges, List<RelatedEntity> expectedOutgoing, List<RelatedEntity> expectedIncoming)
      throws Exception {
    GraphService service = getGraphService();

    edges.forEach(service::addEdge);
    syncAfterWrite();

    RelatedEntitiesResult relatedOutgoing =
        service.findRelatedEntities(
            anyType,
            EMPTY_FILTER,
            anyType,
            EMPTY_FILTER,
            Arrays.asList(downstreamOf, hasOwner, knowsUser),
            outgoingRelationships,
            0,
            100);
    assertEqualsAnyOrder(relatedOutgoing, expectedOutgoing);

    RelatedEntitiesResult relatedIncoming =
        service.findRelatedEntities(
            anyType,
            EMPTY_FILTER,
            anyType,
            EMPTY_FILTER,
            Arrays.asList(downstreamOf, hasOwner, knowsUser),
            incomingRelationships,
            0,
            100);
    assertEqualsAnyOrder(relatedIncoming, expectedIncoming);
  }

  @Test
  public void testPopulatedGraphService() throws Exception {
    GraphService service = getPopulatedGraphService();

    RelatedEntitiesResult relatedOutgoingEntitiesBeforeRemove =
        service.findRelatedEntities(
            anyType,
            EMPTY_FILTER,
            anyType,
            EMPTY_FILTER,
            Arrays.asList(downstreamOf, hasOwner, knowsUser),
            outgoingRelationships,
            0,
            100);
    // All downstreamOf, hasOwner, or knowsUser relationships, outgoing
    assertEqualsAnyOrder(
        relatedOutgoingEntitiesBeforeRemove,
        Arrays.asList(
            downstreamOfDatasetOneRelatedEntity, downstreamOfDatasetTwoRelatedEntity,
            hasOwnerUserOneRelatedEntity, hasOwnerUserTwoRelatedEntity,
            knowsUserOneRelatedEntity, knowsUserTwoRelatedEntity,
            downstreamOfSchemaFieldTwoVia, downstreamOfSchemaFieldTwo));
    RelatedEntitiesResult relatedIncomingEntitiesBeforeRemove =
        service.findRelatedEntities(
            anyType,
            EMPTY_FILTER,
            anyType,
            EMPTY_FILTER,
            Arrays.asList(downstreamOf, hasOwner, knowsUser),
            incomingRelationships,
            0,
            100);
    // All downstreamOf, hasOwner, or knowsUser relationships, incoming
    assertEqualsAnyOrder(
        relatedIncomingEntitiesBeforeRemove,
        Arrays.asList(
            downstreamOfDatasetTwoRelatedEntity,
            downstreamOfDatasetThreeRelatedEntity,
            downstreamOfDatasetFourRelatedEntity,
            hasOwnerDatasetOneRelatedEntity,
            hasOwnerDatasetTwoRelatedEntity,
            hasOwnerDatasetThreeRelatedEntity,
            hasOwnerDatasetFourRelatedEntity,
            knowsUserOneRelatedEntity,
            knowsUserTwoRelatedEntity,
            downstreamOfSchemaFieldOneVia,
            downstreamOfSchemaFieldOne));
    EntityLineageResult viaNodeResult =
        service.getLineage(
            schemaFieldUrnOne,
            LineageDirection.UPSTREAM,
            new GraphFilters(List.of("schemaField")),
            0,
            1000,
            100,
            null,
            null);
    // Multi-path enabled
    assertEquals(viaNodeResult.getRelationships().size(), 2);
    // First one is via node
    assertTrue(
        viaNodeResult.getRelationships().get(0).getPaths().get(0).contains(lifeCycleOwnerOne));
    EntityLineageResult viaNodeResultNoMulti =
        getGraphService(false).getLineage(
            schemaFieldUrnOne,
            LineageDirection.UPSTREAM,
            new GraphFilters(List.of("schemaField")),
            0,
            1000,
            100,
            null,
            null);

    // Multi-path disabled, still has two because via flow creates both edges in response
    assertEquals(viaNodeResultNoMulti.getRelationships().size(), 2);
    // First one is via node
    assertTrue(
        viaNodeResult.getRelationships().get(0).getPaths().get(0).contains(lifeCycleOwnerOne));

    // reset graph service
    getGraphService();
  }

  @Test
  public void testPopulatedGraphServiceGetLineage() throws Exception {
    GraphService service = getLineagePopulatedGraphService();

    EntityLineageResult upstreamLineage =
        service.getLineage(datasetOneUrn, LineageDirection.UPSTREAM, 0, 1000, 1);
    assertEquals(upstreamLineage.getTotal().intValue(), 0);
    assertEquals(upstreamLineage.getRelationships().size(), 0);

    EntityLineageResult downstreamLineage =
        service.getLineage(datasetOneUrn, LineageDirection.DOWNSTREAM, 0, 1000, 1);
    assertEquals(downstreamLineage.getTotal().intValue(), 3);
    assertEquals(downstreamLineage.getRelationships().size(), 3);
    Map<Urn, LineageRelationship> relationships =
        downstreamLineage.getRelationships().stream()
            .collect(Collectors.toMap(LineageRelationship::getEntity, Function.identity()));
    assertTrue(relationships.containsKey(datasetTwoUrn));
    assertEquals(relationships.get(datasetTwoUrn).getType(), downstreamOf);
    assertTrue(relationships.containsKey(dataJobOneUrn));
    assertEquals(relationships.get(dataJobOneUrn).getType(), consumes);
    assertTrue(relationships.containsKey(dataJobTwoUrn));
    assertEquals(relationships.get(dataJobTwoUrn).getType(), consumes);

    upstreamLineage = service.getLineage(datasetThreeUrn, LineageDirection.UPSTREAM, 0, 1000, 1);
    assertEquals(upstreamLineage.getTotal().intValue(), 2);
    assertEquals(upstreamLineage.getRelationships().size(), 2);
    relationships =
        upstreamLineage.getRelationships().stream()
            .collect(Collectors.toMap(LineageRelationship::getEntity, Function.identity()));
    assertTrue(relationships.containsKey(datasetTwoUrn));
    assertEquals(relationships.get(datasetTwoUrn).getType(), downstreamOf);
    assertTrue(relationships.containsKey(dataJobOneUrn));
    assertEquals(relationships.get(dataJobOneUrn).getType(), produces);

    downstreamLineage =
        service.getLineage(datasetThreeUrn, LineageDirection.DOWNSTREAM, 0, 1000, 1);
    assertEquals(downstreamLineage.getTotal().intValue(), 0);
    assertEquals(downstreamLineage.getRelationships().size(), 0);

    upstreamLineage = service.getLineage(dataJobOneUrn, LineageDirection.UPSTREAM, 0, 1000, 1);
    assertEquals(upstreamLineage.getTotal().intValue(), 2);
    assertEquals(upstreamLineage.getRelationships().size(), 2);
    relationships =
        upstreamLineage.getRelationships().stream()
            .collect(Collectors.toMap(LineageRelationship::getEntity, Function.identity()));
    assertTrue(relationships.containsKey(datasetOneUrn));
    assertEquals(relationships.get(datasetOneUrn).getType(), consumes);
    assertTrue(relationships.containsKey(datasetTwoUrn));
    assertEquals(relationships.get(datasetTwoUrn).getType(), consumes);

    downstreamLineage = service.getLineage(dataJobOneUrn, LineageDirection.DOWNSTREAM, 0, 1000, 1);
    assertEquals(downstreamLineage.getTotal().intValue(), 3);
    assertEquals(downstreamLineage.getRelationships().size(), 3);
    relationships =
        downstreamLineage.getRelationships().stream()
            .collect(Collectors.toMap(LineageRelationship::getEntity, Function.identity()));
    assertTrue(relationships.containsKey(datasetThreeUrn));
    assertEquals(relationships.get(datasetThreeUrn).getType(), produces);
    assertTrue(relationships.containsKey(datasetFourUrn));
    assertEquals(relationships.get(datasetFourUrn).getType(), produces);
    assertTrue(relationships.containsKey(dataJobTwoUrn));
    assertEquals(relationships.get(dataJobTwoUrn).getType(), downstreamOf);
  }

  @DataProvider(name = "FindRelatedEntitiesSourceEntityFilterTests")
  public Object[][] getFindRelatedEntitiesSourceEntityFilterTests() {
    return new Object[][] {
      new Object[] {
        newFilter("urn", datasetTwoUrnString),
        Arrays.asList(downstreamOf),
        outgoingRelationships,
        Arrays.asList(downstreamOfDatasetOneRelatedEntity)
      },
      new Object[] {
        newFilter("urn", datasetTwoUrnString),
        Arrays.asList(downstreamOf),
        incomingRelationships,
        Arrays.asList(downstreamOfDatasetThreeRelatedEntity, downstreamOfDatasetFourRelatedEntity)
      },
      new Object[] {
        newFilter("urn", datasetTwoUrnString),
        Arrays.asList(downstreamOf),
        undirectedRelationships,
        Arrays.asList(
            downstreamOfDatasetOneRelatedEntity,
            downstreamOfDatasetThreeRelatedEntity,
            downstreamOfDatasetFourRelatedEntity)
      },
      new Object[] {
        newFilter("urn", datasetTwoUrnString),
        Arrays.asList(hasOwner),
        outgoingRelationships,
        Arrays.asList(hasOwnerUserOneRelatedEntity)
      },
      new Object[] {
        newFilter("urn", datasetTwoUrnString),
        Arrays.asList(hasOwner),
        incomingRelationships,
        Arrays.asList()
      },
      new Object[] {
        newFilter("urn", datasetTwoUrnString),
        Arrays.asList(hasOwner),
        undirectedRelationships,
        Arrays.asList(hasOwnerUserOneRelatedEntity)
      },
      new Object[] {
        newFilter("urn", userOneUrnString),
        Arrays.asList(hasOwner),
        outgoingRelationships,
        Arrays.asList()
      },
      new Object[] {
        newFilter("urn", userOneUrnString),
        Arrays.asList(hasOwner),
        incomingRelationships,
        Arrays.asList(hasOwnerDatasetOneRelatedEntity, hasOwnerDatasetTwoRelatedEntity)
      },
      new Object[] {
        newFilter("urn", userOneUrnString),
        Arrays.asList(hasOwner),
        undirectedRelationships,
        Arrays.asList(hasOwnerDatasetOneRelatedEntity, hasOwnerDatasetTwoRelatedEntity)
      }
    };
  }

  @Test(dataProvider = "FindRelatedEntitiesSourceEntityFilterTests")
  public void testFindRelatedEntitiesSourceEntityFilter(
      Filter sourceEntityFilter,
      List<String> relationshipTypes,
      RelationshipFilter relationships,
      List<RelatedEntity> expectedRelatedEntities)
      throws Exception {
    doTestFindRelatedEntities(
        sourceEntityFilter,
        EMPTY_FILTER,
        relationshipTypes,
        relationships,
        expectedRelatedEntities);
  }

  @DataProvider(name = "FindRelatedEntitiesDestinationEntityFilterTests")
  public Object[][] getFindRelatedEntitiesDestinationEntityFilterTests() {
    return new Object[][] {
      new Object[] {
        newFilter("urn", datasetTwoUrnString),
        Arrays.asList(downstreamOf),
        outgoingRelationships,
        Arrays.asList(downstreamOfDatasetTwoRelatedEntity)
      },
      new Object[] {
        newFilter("urn", datasetTwoUrnString),
        Arrays.asList(downstreamOf),
        incomingRelationships,
        Arrays.asList(downstreamOfDatasetTwoRelatedEntity)
      },
      new Object[] {
        newFilter("urn", datasetTwoUrnString),
        Arrays.asList(downstreamOf),
        undirectedRelationships,
        Arrays.asList(downstreamOfDatasetTwoRelatedEntity)
      },
      new Object[] {
        newFilter("urn", userOneUrnString),
        Arrays.asList(downstreamOf),
        outgoingRelationships,
        Arrays.asList()
      },
      new Object[] {
        newFilter("urn", userOneUrnString),
        Arrays.asList(downstreamOf),
        incomingRelationships,
        Arrays.asList()
      },
      new Object[] {
        newFilter("urn", userOneUrnString),
        Arrays.asList(downstreamOf),
        undirectedRelationships,
        Arrays.asList()
      },
      new Object[] {
        newFilter("urn", userOneUrnString),
        Arrays.asList(hasOwner),
        outgoingRelationships,
        Arrays.asList(hasOwnerUserOneRelatedEntity)
      },
      new Object[] {
        newFilter("urn", userOneUrnString),
        Arrays.asList(hasOwner),
        incomingRelationships,
        Arrays.asList()
      },
      new Object[] {
        newFilter("urn", userOneUrnString),
        Arrays.asList(hasOwner),
        undirectedRelationships,
        Arrays.asList(hasOwnerUserOneRelatedEntity)
      }
    };
  }

  @Test(dataProvider = "FindRelatedEntitiesDestinationEntityFilterTests")
  public void testFindRelatedEntitiesDestinationEntityFilter(
      Filter destinationEntityFilter,
      List<String> relationshipTypes,
      RelationshipFilter relationships,
      List<RelatedEntity> expectedRelatedEntities)
      throws Exception {
    doTestFindRelatedEntities(
        EMPTY_FILTER,
        destinationEntityFilter,
        relationshipTypes,
        relationships,
        expectedRelatedEntities);
  }

  private void doTestFindRelatedEntities(
      final Filter sourceEntityFilter,
      final Filter destinationEntityFilter,
      List<String> relationshipTypes,
      final RelationshipFilter relationshipFilter,
      List<RelatedEntity> expectedRelatedEntities)
      throws Exception {
    GraphService service = getPopulatedGraphService();

    RelatedEntitiesResult relatedEntities =
        service.findRelatedEntities(
            anyType,
            sourceEntityFilter,
            anyType,
            destinationEntityFilter,
            relationshipTypes,
            relationshipFilter,
            0,
            10);

    assertEqualsAnyOrder(relatedEntities, expectedRelatedEntities);
  }

  @DataProvider(name = "FindRelatedEntitiesSourceTypeTests")
  public Object[][] getFindRelatedEntitiesSourceTypeTests() {
    return new Object[][] {
      // All DownstreamOf relationships, outgoing
      new Object[] {
        null,
        Arrays.asList(downstreamOf),
        outgoingRelationships,
        Arrays.asList(
            downstreamOfDatasetOneRelatedEntity,
            downstreamOfDatasetTwoRelatedEntity,
            downstreamOfSchemaFieldTwoVia,
            downstreamOfSchemaFieldTwo)
      },
      // All DownstreamOf relationships, incoming
      new Object[] {
        null,
        Arrays.asList(downstreamOf),
        incomingRelationships,
        Arrays.asList(
            downstreamOfDatasetTwoRelatedEntity,
            downstreamOfDatasetThreeRelatedEntity,
            downstreamOfDatasetFourRelatedEntity,
            downstreamOfSchemaFieldOneVia,
            downstreamOfSchemaFieldOne)
      },
      // All DownstreamOf relationships, both directions
      new Object[] {
        null,
        Arrays.asList(downstreamOf),
        undirectedRelationships,
        Arrays.asList(
            downstreamOfDatasetOneRelatedEntity, downstreamOfDatasetTwoRelatedEntity,
            downstreamOfDatasetThreeRelatedEntity, downstreamOfDatasetFourRelatedEntity,
            downstreamOfSchemaFieldTwoVia, downstreamOfSchemaFieldTwo,
            downstreamOfSchemaFieldOneVia, downstreamOfSchemaFieldOne)
      },

      // "" used to be any type before v0.9.0, which is now encoded by null
      new Object[] {
        "", Arrays.asList(downstreamOf), outgoingRelationships, Collections.emptyList()
      },
      new Object[] {
        "", Arrays.asList(downstreamOf), incomingRelationships, Collections.emptyList()
      },
      new Object[] {
        "", Arrays.asList(downstreamOf), undirectedRelationships, Collections.emptyList()
      },
      new Object[] {
        datasetType,
        Arrays.asList(downstreamOf),
        outgoingRelationships,
        Arrays.asList(downstreamOfDatasetOneRelatedEntity, downstreamOfDatasetTwoRelatedEntity)
      },
      new Object[] {
        datasetType,
        Arrays.asList(downstreamOf),
        incomingRelationships,
        Arrays.asList(
            downstreamOfDatasetTwoRelatedEntity,
            downstreamOfDatasetThreeRelatedEntity,
            downstreamOfDatasetFourRelatedEntity)
      },
      new Object[] {
        datasetType,
        Arrays.asList(downstreamOf),
        undirectedRelationships,
        Arrays.asList(
            downstreamOfDatasetOneRelatedEntity, downstreamOfDatasetTwoRelatedEntity,
            downstreamOfDatasetThreeRelatedEntity, downstreamOfDatasetFourRelatedEntity)
      },
      new Object[] {userType, Arrays.asList(downstreamOf), outgoingRelationships, Arrays.asList()},
      new Object[] {userType, Arrays.asList(downstreamOf), incomingRelationships, Arrays.asList()},
      new Object[] {
        userType, Arrays.asList(downstreamOf), undirectedRelationships, Arrays.asList()
      },
      new Object[] {userType, Arrays.asList(hasOwner), outgoingRelationships, Arrays.asList()},
      new Object[] {
        userType,
        Arrays.asList(hasOwner),
        incomingRelationships,
        Arrays.asList(
            hasOwnerDatasetOneRelatedEntity, hasOwnerDatasetTwoRelatedEntity,
            hasOwnerDatasetThreeRelatedEntity, hasOwnerDatasetFourRelatedEntity)
      },
      new Object[] {
        userType,
        Arrays.asList(hasOwner),
        undirectedRelationships,
        Arrays.asList(
            hasOwnerDatasetOneRelatedEntity, hasOwnerDatasetTwoRelatedEntity,
            hasOwnerDatasetThreeRelatedEntity, hasOwnerDatasetFourRelatedEntity)
      }
    };
  }

  @Test(dataProvider = "FindRelatedEntitiesSourceTypeTests")
  public void testFindRelatedEntitiesSourceType(
      String entityTypeFilter,
      List<String> relationshipTypes,
      RelationshipFilter relationships,
      List<RelatedEntity> expectedRelatedEntities)
      throws Exception {
    doTestFindRelatedEntities(
        entityTypeFilter != null ? ImmutableList.of(entityTypeFilter) : null,
        anyType,
        relationshipTypes,
        relationships,
        expectedRelatedEntities);
  }

  @DataProvider(name = "FindRelatedEntitiesDestinationTypeTests")
  public Object[][] getFindRelatedEntitiesDestinationTypeTests() {
    return new Object[][] {
      new Object[] {
        null,
        Arrays.asList(downstreamOf),
        outgoingRelationships,
        // All DownstreamOf relationships, outgoing
        Arrays.asList(
            downstreamOfDatasetOneRelatedEntity,
            downstreamOfDatasetTwoRelatedEntity,
            downstreamOfSchemaFieldTwoVia,
            downstreamOfSchemaFieldTwo)
      },
      new Object[] {
        null,
        Arrays.asList(downstreamOf),
        incomingRelationships,
        // All DownstreamOf relationships, incoming
        Arrays.asList(
            downstreamOfDatasetTwoRelatedEntity,
            downstreamOfDatasetThreeRelatedEntity,
            downstreamOfDatasetFourRelatedEntity,
            downstreamOfSchemaFieldOneVia,
            downstreamOfSchemaFieldOne)
      },
      new Object[] {
        null,
        Arrays.asList(downstreamOf),
        undirectedRelationships,
        Arrays.asList(
            downstreamOfDatasetOneRelatedEntity, downstreamOfDatasetTwoRelatedEntity,
            downstreamOfDatasetThreeRelatedEntity, downstreamOfDatasetFourRelatedEntity,
            downstreamOfSchemaFieldOneVia, downstreamOfSchemaFieldOne,
            downstreamOfSchemaFieldTwoVia, downstreamOfSchemaFieldTwo)
      },
      new Object[] {
        "", Arrays.asList(downstreamOf), outgoingRelationships, Collections.emptyList()
      },
      new Object[] {
        "", Arrays.asList(downstreamOf), incomingRelationships, Collections.emptyList()
      },
      new Object[] {
        "", Arrays.asList(downstreamOf), undirectedRelationships, Collections.emptyList()
      },
      new Object[] {
        datasetType,
        Arrays.asList(downstreamOf),
        outgoingRelationships,
        Arrays.asList(downstreamOfDatasetOneRelatedEntity, downstreamOfDatasetTwoRelatedEntity)
      },
      new Object[] {
        datasetType,
        Arrays.asList(downstreamOf),
        incomingRelationships,
        Arrays.asList(
            downstreamOfDatasetTwoRelatedEntity,
            downstreamOfDatasetThreeRelatedEntity,
            downstreamOfDatasetFourRelatedEntity)
      },
      new Object[] {
        datasetType,
        Arrays.asList(downstreamOf),
        undirectedRelationships,
        Arrays.asList(
            downstreamOfDatasetOneRelatedEntity, downstreamOfDatasetTwoRelatedEntity,
            downstreamOfDatasetThreeRelatedEntity, downstreamOfDatasetFourRelatedEntity)
      },
      new Object[] {datasetType, Arrays.asList(hasOwner), outgoingRelationships, Arrays.asList()},
      new Object[] {
        datasetType,
        Arrays.asList(hasOwner),
        incomingRelationships,
        Arrays.asList(
            hasOwnerDatasetOneRelatedEntity, hasOwnerDatasetTwoRelatedEntity,
            hasOwnerDatasetThreeRelatedEntity, hasOwnerDatasetFourRelatedEntity)
      },
      new Object[] {
        datasetType,
        Arrays.asList(hasOwner),
        undirectedRelationships,
        Arrays.asList(
            hasOwnerDatasetOneRelatedEntity, hasOwnerDatasetTwoRelatedEntity,
            hasOwnerDatasetThreeRelatedEntity, hasOwnerDatasetFourRelatedEntity)
      },
      new Object[] {
        userType,
        Arrays.asList(hasOwner),
        outgoingRelationships,
        Arrays.asList(hasOwnerUserOneRelatedEntity, hasOwnerUserTwoRelatedEntity)
      },
      new Object[] {userType, Arrays.asList(hasOwner), incomingRelationships, Arrays.asList()},
      new Object[] {
        userType,
        Arrays.asList(hasOwner),
        undirectedRelationships,
        Arrays.asList(hasOwnerUserOneRelatedEntity, hasOwnerUserTwoRelatedEntity)
      }
    };
  }

  @Test(dataProvider = "FindRelatedEntitiesDestinationTypeTests")
  public void testFindRelatedEntitiesDestinationType(
      String entityTypeFilter,
      List<String> relationshipTypes,
      RelationshipFilter relationships,
      List<RelatedEntity> expectedRelatedEntities)
      throws Exception {
    doTestFindRelatedEntities(
        anyType,
        entityTypeFilter != null ? ImmutableList.of(entityTypeFilter) : null,
        relationshipTypes,
        relationships,
        expectedRelatedEntities);
  }

  private void doTestFindRelatedEntities(
      final List<String> sourceType,
      final List<String> destinationType,
      final List<String> relationshipTypes,
      final RelationshipFilter relationshipFilter,
      List<RelatedEntity> expectedRelatedEntities)
      throws Exception {
    GraphService service = getPopulatedGraphService();

    RelatedEntitiesResult relatedEntities =
        service.findRelatedEntities(
            sourceType,
            EMPTY_FILTER,
            destinationType,
            EMPTY_FILTER,
            relationshipTypes,
            relationshipFilter,
            0,
            10);

    assertEqualsAnyOrder(relatedEntities, expectedRelatedEntities);
  }

  private void doTestFindRelatedEntitiesEntityType(
      @Nullable List<String> sourceType,
      @Nullable List<String> destinationType,
      @Nonnull String relationshipType,
      @Nonnull RelationshipFilter relationshipFilter,
      @Nonnull GraphService service,
      @Nonnull RelatedEntity... expectedEntities) {
    RelatedEntitiesResult actualEntities =
        service.findRelatedEntities(
            sourceType,
            EMPTY_FILTER,
            destinationType,
            EMPTY_FILTER,
            Arrays.asList(relationshipType),
            relationshipFilter,
            0,
            100);
    assertEqualsAnyOrder(actualEntities, Arrays.asList(expectedEntities));
  }

  @Test
  public void testFindRelatedEntitiesNullSourceType() throws Exception {
    GraphService service = getGraphService();

    Urn nullUrn = createFromString("urn:li:null:(urn:li:null:Null)");
    assertNotNull(nullUrn);
    RelatedEntity nullRelatedEntity = new RelatedEntity(downstreamOf, nullUrn.toString());

    doTestFindRelatedEntitiesEntityType(
        anyType, ImmutableList.of("null"), downstreamOf, outgoingRelationships, service);
    doTestFindRelatedEntitiesEntityType(
        anyType, null, downstreamOf, outgoingRelationships, service);

    service.addEdge(
        new Edge(datasetTwoUrn, datasetOneUrn, downstreamOf, null, null, null, null, null));
    syncAfterWrite();
    doTestFindRelatedEntitiesEntityType(
        anyType, ImmutableList.of("null"), downstreamOf, outgoingRelationships, service);
    doTestFindRelatedEntitiesEntityType(
        anyType,
        null,
        downstreamOf,
        outgoingRelationships,
        service,
        downstreamOfDatasetOneRelatedEntity);

    service.addEdge(new Edge(datasetOneUrn, nullUrn, downstreamOf, null, null, null, null, null));
    syncAfterWrite();
    doTestFindRelatedEntitiesEntityType(
        anyType,
        ImmutableList.of("null"),
        downstreamOf,
        outgoingRelationships,
        service,
        nullRelatedEntity);
    doTestFindRelatedEntitiesEntityType(
        anyType,
        null,
        downstreamOf,
        outgoingRelationships,
        service,
        nullRelatedEntity,
        downstreamOfDatasetOneRelatedEntity);
  }

  @Test
  public void testFindRelatedEntitiesNullDestinationType() throws Exception {
    GraphService service = getGraphService();

    Urn nullUrn = createFromString("urn:li:null:(urn:li:null:Null)");
    assertNotNull(nullUrn);
    RelatedEntity nullRelatedEntity = new RelatedEntity(downstreamOf, nullUrn.toString());

    doTestFindRelatedEntitiesEntityType(
        anyType, ImmutableList.of("null"), downstreamOf, outgoingRelationships, service);
    doTestFindRelatedEntitiesEntityType(
        anyType, null, downstreamOf, outgoingRelationships, service);

    service.addEdge(
        new Edge(datasetTwoUrn, datasetOneUrn, downstreamOf, null, null, null, null, null));
    syncAfterWrite();
    doTestFindRelatedEntitiesEntityType(
        anyType, ImmutableList.of("null"), downstreamOf, outgoingRelationships, service);
    doTestFindRelatedEntitiesEntityType(
        anyType,
        null,
        downstreamOf,
        outgoingRelationships,
        service,
        downstreamOfDatasetOneRelatedEntity);

    service.addEdge(new Edge(datasetOneUrn, nullUrn, downstreamOf, null, null, null, null, null));
    syncAfterWrite();
    doTestFindRelatedEntitiesEntityType(
        anyType,
        ImmutableList.of("null"),
        downstreamOf,
        outgoingRelationships,
        service,
        nullRelatedEntity);
    doTestFindRelatedEntitiesEntityType(
        anyType,
        null,
        downstreamOf,
        outgoingRelationships,
        service,
        nullRelatedEntity,
        downstreamOfDatasetOneRelatedEntity);
  }

  @Test
  public void testFindRelatedEntitiesRelationshipTypes() throws Exception {
    GraphService service = getPopulatedGraphService();

    RelatedEntitiesResult allOutgoingRelatedEntities =
        service.findRelatedEntities(
            anyType,
            EMPTY_FILTER,
            anyType,
            EMPTY_FILTER,
            Arrays.asList(downstreamOf, hasOwner, knowsUser),
            outgoingRelationships,
            0,
            100);
    // All DownstreamOf relationships, outgoing (destination)
    assertEqualsAnyOrder(
        allOutgoingRelatedEntities,
        Arrays.asList(
            downstreamOfDatasetOneRelatedEntity, downstreamOfDatasetTwoRelatedEntity,
            hasOwnerUserOneRelatedEntity, hasOwnerUserTwoRelatedEntity,
            knowsUserOneRelatedEntity, knowsUserTwoRelatedEntity,
            downstreamOfSchemaFieldTwoVia, downstreamOfSchemaFieldTwo));

    RelatedEntitiesResult allIncomingRelatedEntities =
        service.findRelatedEntities(
            anyType,
            EMPTY_FILTER,
            anyType,
            EMPTY_FILTER,
            Arrays.asList(downstreamOf, hasOwner, knowsUser),
            incomingRelationships,
            0,
            100);
    // All DownstreamOf relationships, incoming (source)
    assertEqualsAnyOrder(
        allIncomingRelatedEntities,
        Arrays.asList(
            downstreamOfDatasetTwoRelatedEntity,
            downstreamOfDatasetThreeRelatedEntity,
            downstreamOfDatasetFourRelatedEntity,
            hasOwnerDatasetOneRelatedEntity,
            hasOwnerDatasetTwoRelatedEntity,
            hasOwnerDatasetThreeRelatedEntity,
            hasOwnerDatasetFourRelatedEntity,
            knowsUserOneRelatedEntity,
            knowsUserTwoRelatedEntity,
            downstreamOfSchemaFieldOneVia,
            downstreamOfSchemaFieldOne));

    RelatedEntitiesResult allUnknownRelationshipTypeRelatedEntities =
        service.findRelatedEntities(
            anyType,
            EMPTY_FILTER,
            anyType,
            EMPTY_FILTER,
            Arrays.asList("unknownRelationshipType", "unseenRelationshipType"),
            outgoingRelationships,
            0,
            100);
    assertEqualsAnyOrder(allUnknownRelationshipTypeRelatedEntities, Collections.emptyList());

    RelatedEntitiesResult someUnknownRelationshipTypeRelatedEntities =
        service.findRelatedEntities(
            anyType,
            EMPTY_FILTER,
            anyType,
            EMPTY_FILTER,
            Arrays.asList("unknownRelationshipType", downstreamOf),
            outgoingRelationships,
            0,
            100);
    // All DownstreamOf relationships, outgoing (destination)
    assertEqualsAnyOrder(
        someUnknownRelationshipTypeRelatedEntities,
        Arrays.asList(
            downstreamOfDatasetOneRelatedEntity,
            downstreamOfDatasetTwoRelatedEntity,
            downstreamOfSchemaFieldTwoVia,
            downstreamOfSchemaFieldTwo));
  }

  @Test
  public void testFindRelatedEntitiesNoRelationshipTypes() throws Exception {
    GraphService service = getPopulatedGraphService();

    RelatedEntitiesResult relatedEntities =
        service.findRelatedEntities(
            anyType,
            EMPTY_FILTER,
            anyType,
            EMPTY_FILTER,
            Collections.emptyList(),
            outgoingRelationships,
            0,
            10);

    assertEquals(relatedEntities.entities, Collections.emptyList());

    // does the test actually test something? is the Collections.emptyList() the only reason why we
    // did not get any related urns?
    RelatedEntitiesResult relatedEntitiesAll =
        service.findRelatedEntities(
            anyType,
            EMPTY_FILTER,
            anyType,
            EMPTY_FILTER,
            Arrays.asList(downstreamOf, hasOwner, knowsUser),
            outgoingRelationships,
            0,
            10);

    assertNotEquals(relatedEntitiesAll.entities, Collections.emptyList());
  }

  @Test
  public void testFindRelatedEntitiesAllFilters() throws Exception {
    GraphService service = getPopulatedGraphService();

    RelatedEntitiesResult relatedEntities =
        service.findRelatedEntities(
            ImmutableList.of(datasetType),
            newFilter("urn", datasetOneUrnString),
            ImmutableList.of(userType),
            newFilter("urn", userOneUrnString),
            Arrays.asList(hasOwner),
            outgoingRelationships,
            0,
            10);

    assertEquals(relatedEntities.entities, Arrays.asList(hasOwnerUserOneRelatedEntity));

    relatedEntities =
        service.findRelatedEntities(
            ImmutableList.of(datasetType),
            newFilter("urn", datasetOneUrnString),
            ImmutableList.of(userType),
            newFilter("urn", userTwoUrnString),
            Arrays.asList(hasOwner),
            incomingRelationships,
            0,
            10);

    assertEquals(relatedEntities.entities, Collections.emptyList());
  }

  @Test
  public void testFindRelatedEntitiesMultipleEntityTypes() throws Exception {
    GraphService service = getPopulatedGraphService();

    RelatedEntitiesResult relatedEntities =
        service.findRelatedEntities(
            ImmutableList.of(datasetType, userType),
            newFilter("urn", datasetOneUrnString),
            ImmutableList.of(datasetType, userType),
            newFilter("urn", userOneUrnString),
            Arrays.asList(hasOwner),
            outgoingRelationships,
            0,
            10);

    assertEquals(relatedEntities.entities, Arrays.asList(hasOwnerUserOneRelatedEntity));

    relatedEntities =
        service.findRelatedEntities(
            ImmutableList.of(datasetType, userType),
            newFilter("urn", datasetOneUrnString),
            ImmutableList.of(datasetType, userType),
            newFilter("urn", userTwoUrnString),
            Arrays.asList(hasOwner),
            incomingRelationships,
            0,
            10);

    assertEquals(relatedEntities.entities, Collections.emptyList());
  }

  @Test
  public void testFindRelatedEntitiesOffsetAndCount() throws Exception {
    GraphService service = getPopulatedGraphService();

    // populated graph asserted in testPopulatedGraphService
    RelatedEntitiesResult allRelatedEntities =
        service.findRelatedEntities(
            ImmutableList.of(datasetType),
            EMPTY_FILTER,
            anyType,
            EMPTY_FILTER,
            Arrays.asList(downstreamOf, hasOwner, knowsUser),
            outgoingRelationships,
            0,
            100);

    List<RelatedEntity> individualRelatedEntities = new ArrayList<>();
    IntStream.range(0, allRelatedEntities.entities.size())
        .forEach(
            idx ->
                individualRelatedEntities.addAll(
                    service.findRelatedEntities(
                            ImmutableList.of(datasetType),
                            EMPTY_FILTER,
                            anyType,
                            EMPTY_FILTER,
                            Arrays.asList(downstreamOf, hasOwner, knowsUser),
                            outgoingRelationships,
                            idx,
                            1)
                        .entities));
    Assert.assertEquals(individualRelatedEntities, allRelatedEntities.entities);
  }

  @DataProvider(name = "RemoveEdgesFromNodeTests")
  public Object[][] getRemoveEdgesFromNodeTests() {
    return new Object[][] {
      new Object[] {
        datasetTwoUrn,
        Arrays.asList(downstreamOf),
        outgoingRelationships,
        Arrays.asList(downstreamOfDatasetOneRelatedEntity),
        Arrays.asList(downstreamOfDatasetThreeRelatedEntity, downstreamOfDatasetFourRelatedEntity),
        Arrays.asList(),
        Arrays.asList(downstreamOfDatasetThreeRelatedEntity, downstreamOfDatasetFourRelatedEntity)
      },
      new Object[] {
        datasetTwoUrn,
        Arrays.asList(downstreamOf),
        incomingRelationships,
        Arrays.asList(downstreamOfDatasetOneRelatedEntity),
        Arrays.asList(downstreamOfDatasetThreeRelatedEntity, downstreamOfDatasetFourRelatedEntity),
        Arrays.asList(downstreamOfDatasetOneRelatedEntity),
        Arrays.asList(),
      },
      new Object[] {
        datasetTwoUrn,
        Arrays.asList(downstreamOf),
        undirectedRelationships,
        Arrays.asList(downstreamOfDatasetOneRelatedEntity),
        Arrays.asList(downstreamOfDatasetThreeRelatedEntity, downstreamOfDatasetFourRelatedEntity),
        Arrays.asList(),
        Arrays.asList()
      },
      new Object[] {
        userOneUrn,
        Arrays.asList(hasOwner, knowsUser),
        outgoingRelationships,
        Arrays.asList(knowsUserTwoRelatedEntity),
        Arrays.asList(
            hasOwnerDatasetOneRelatedEntity,
            hasOwnerDatasetTwoRelatedEntity,
            knowsUserTwoRelatedEntity),
        Arrays.asList(),
        Arrays.asList(
            hasOwnerDatasetOneRelatedEntity,
            hasOwnerDatasetTwoRelatedEntity,
            knowsUserTwoRelatedEntity)
      },
      new Object[] {
        userOneUrn,
        Arrays.asList(hasOwner, knowsUser),
        incomingRelationships,
        Arrays.asList(knowsUserTwoRelatedEntity),
        Arrays.asList(
            hasOwnerDatasetOneRelatedEntity,
            hasOwnerDatasetTwoRelatedEntity,
            knowsUserTwoRelatedEntity),
        Arrays.asList(knowsUserTwoRelatedEntity),
        Arrays.asList()
      },
      new Object[] {
        userOneUrn,
        Arrays.asList(hasOwner, knowsUser),
        undirectedRelationships,
        Arrays.asList(knowsUserTwoRelatedEntity),
        Arrays.asList(
            hasOwnerDatasetOneRelatedEntity,
            hasOwnerDatasetTwoRelatedEntity,
            knowsUserTwoRelatedEntity),
        Arrays.asList(),
        Arrays.asList()
      }
    };
  }

  @Test(dataProvider = "RemoveEdgesFromNodeTests")
  public void testRemoveEdgesFromNode(
      @Nonnull Urn nodeToRemoveFrom,
      @Nonnull List<String> relationTypes,
      @Nonnull RelationshipFilter relationshipFilter,
      List<RelatedEntity> expectedOutgoingRelatedUrnsBeforeRemove,
      List<RelatedEntity> expectedIncomingRelatedUrnsBeforeRemove,
      List<RelatedEntity> expectedOutgoingRelatedUrnsAfterRemove,
      List<RelatedEntity> expectedIncomingRelatedUrnsAfterRemove)
      throws Exception {
    GraphService service = getPopulatedGraphService();

    List<String> allOtherRelationTypes =
        allRelationshipTypes.stream()
            .filter(relation -> !relationTypes.contains(relation))
            .collect(Collectors.toList());
    assertTrue(allOtherRelationTypes.size() > 0);

    RelatedEntitiesResult actualOutgoingRelatedUrnsBeforeRemove =
        service.findRelatedEntities(
            anyType,
            newFilter("urn", nodeToRemoveFrom.toString()),
            anyType,
            EMPTY_FILTER,
            relationTypes,
            outgoingRelationships,
            0,
            100);
    RelatedEntitiesResult actualIncomingRelatedUrnsBeforeRemove =
        service.findRelatedEntities(
            anyType,
            newFilter("urn", nodeToRemoveFrom.toString()),
            anyType,
            EMPTY_FILTER,
            relationTypes,
            incomingRelationships,
            0,
            100);
    assertEqualsAnyOrder(
        actualOutgoingRelatedUrnsBeforeRemove, expectedOutgoingRelatedUrnsBeforeRemove);
    assertEqualsAnyOrder(
        actualIncomingRelatedUrnsBeforeRemove, expectedIncomingRelatedUrnsBeforeRemove);

    // we expect these do not change
    RelatedEntitiesResult relatedEntitiesOfOtherOutgoingRelationTypesBeforeRemove =
        service.findRelatedEntities(
            anyType,
            newFilter("urn", nodeToRemoveFrom.toString()),
            anyType,
            EMPTY_FILTER,
            allOtherRelationTypes,
            outgoingRelationships,
            0,
            100);
    RelatedEntitiesResult relatedEntitiesOfOtherIncomingRelationTypesBeforeRemove =
        service.findRelatedEntities(
            anyType,
            newFilter("urn", nodeToRemoveFrom.toString()),
            anyType,
            EMPTY_FILTER,
            allOtherRelationTypes,
            incomingRelationships,
            0,
            100);

    service.removeEdgesFromNode(nodeToRemoveFrom, relationTypes, relationshipFilter);
    syncAfterWrite();

    RelatedEntitiesResult actualOutgoingRelatedUrnsAfterRemove =
        service.findRelatedEntities(
            anyType,
            newFilter("urn", nodeToRemoveFrom.toString()),
            anyType,
            EMPTY_FILTER,
            relationTypes,
            outgoingRelationships,
            0,
            100);
    RelatedEntitiesResult actualIncomingRelatedUrnsAfterRemove =
        service.findRelatedEntities(
            anyType,
            newFilter("urn", nodeToRemoveFrom.toString()),
            anyType,
            EMPTY_FILTER,
            relationTypes,
            incomingRelationships,
            0,
            100);
    assertEqualsAnyOrder(
        actualOutgoingRelatedUrnsAfterRemove, expectedOutgoingRelatedUrnsAfterRemove);
    assertEqualsAnyOrder(
        actualIncomingRelatedUrnsAfterRemove, expectedIncomingRelatedUrnsAfterRemove);

    // assert these did not change
    RelatedEntitiesResult relatedEntitiesOfOtherOutgoingRelationTypesAfterRemove =
        service.findRelatedEntities(
            anyType,
            newFilter("urn", nodeToRemoveFrom.toString()),
            anyType,
            EMPTY_FILTER,
            allOtherRelationTypes,
            outgoingRelationships,
            0,
            100);
    RelatedEntitiesResult relatedEntitiesOfOtherIncomingRelationTypesAfterRemove =
        service.findRelatedEntities(
            anyType,
            newFilter("urn", nodeToRemoveFrom.toString()),
            anyType,
            EMPTY_FILTER,
            allOtherRelationTypes,
            incomingRelationships,
            0,
            100);
    assertEqualsAnyOrder(
        relatedEntitiesOfOtherOutgoingRelationTypesAfterRemove,
        relatedEntitiesOfOtherOutgoingRelationTypesBeforeRemove);
    assertEqualsAnyOrder(
        relatedEntitiesOfOtherIncomingRelationTypesAfterRemove,
        relatedEntitiesOfOtherIncomingRelationTypesBeforeRemove);
  }

  @Test
  public void testRemoveEdgesFromNodeNoRelationshipTypes() throws Exception {
    GraphService service = getPopulatedGraphService();
    Urn nodeToRemoveFrom = datasetOneUrn;

    // populated graph asserted in testPopulatedGraphService
    RelatedEntitiesResult relatedOutgoingEntitiesBeforeRemove =
        service.findRelatedEntities(
            anyType,
            newFilter("urn", nodeToRemoveFrom.toString()),
            anyType,
            EMPTY_FILTER,
            Arrays.asList(downstreamOf, hasOwner, knowsUser),
            outgoingRelationships,
            0,
            100);

    // can be replaced with a single removeEdgesFromNode and undirectedRelationships once supported
    // by all implementations
    service.removeEdgesFromNode(nodeToRemoveFrom, Collections.emptyList(), outgoingRelationships);
    service.removeEdgesFromNode(nodeToRemoveFrom, Collections.emptyList(), incomingRelationships);
    syncAfterWrite();

    RelatedEntitiesResult relatedOutgoingEntitiesAfterRemove =
        service.findRelatedEntities(
            anyType,
            newFilter("urn", nodeToRemoveFrom.toString()),
            anyType,
            EMPTY_FILTER,
            Arrays.asList(downstreamOf, hasOwner, knowsUser),
            outgoingRelationships,
            0,
            100);
    assertEqualsAnyOrder(relatedOutgoingEntitiesAfterRemove, relatedOutgoingEntitiesBeforeRemove);

    // does the test actually test something? is the Collections.emptyList() the only reason why we
    // did not see changes?
    service.removeEdgesFromNode(
        nodeToRemoveFrom, Arrays.asList(downstreamOf, hasOwner, knowsUser), outgoingRelationships);
    service.removeEdgesFromNode(
        nodeToRemoveFrom, Arrays.asList(downstreamOf, hasOwner, knowsUser), incomingRelationships);
    syncAfterWrite();

    RelatedEntitiesResult relatedOutgoingEntitiesAfterRemoveAll =
        service.findRelatedEntities(
            anyType,
            newFilter("urn", nodeToRemoveFrom.toString()),
            anyType,
            EMPTY_FILTER,
            Arrays.asList(downstreamOf, hasOwner, knowsUser),
            outgoingRelationships,
            0,
            100);
    assertEqualsAnyOrder(relatedOutgoingEntitiesAfterRemoveAll, Collections.emptyList());
  }

  @Test
  public void testRemoveEdgesFromUnknownNode() throws Exception {
    GraphService service = getPopulatedGraphService();
    Urn nodeToRemoveFrom = unknownUrn;

    // populated graph asserted in testPopulatedGraphService
    RelatedEntitiesResult relatedOutgoingEntitiesBeforeRemove =
        service.findRelatedEntities(
            anyType,
            EMPTY_FILTER,
            anyType,
            EMPTY_FILTER,
            Arrays.asList(downstreamOf, hasOwner, knowsUser),
            outgoingRelationships,
            0,
            100);

    // can be replaced with a single removeEdgesFromNode and undirectedRelationships once supported
    // by all implementations
    service.removeEdgesFromNode(
        nodeToRemoveFrom, Arrays.asList(downstreamOf, hasOwner, knowsUser), outgoingRelationships);
    service.removeEdgesFromNode(
        nodeToRemoveFrom, Arrays.asList(downstreamOf, hasOwner, knowsUser), incomingRelationships);
    syncAfterWrite();

    RelatedEntitiesResult relatedOutgoingEntitiesAfterRemove =
        service.findRelatedEntities(
            anyType,
            EMPTY_FILTER,
            anyType,
            EMPTY_FILTER,
            Arrays.asList(downstreamOf, hasOwner, knowsUser),
            outgoingRelationships,
            0,
            100);
    assertEqualsAnyOrder(relatedOutgoingEntitiesAfterRemove, relatedOutgoingEntitiesBeforeRemove);
  }

  @Test
  public void testRemoveNode() throws Exception {
    GraphService service = getPopulatedGraphService();

    service.removeNode(datasetTwoUrn);
    syncAfterWrite();

    // assert the modified graph
    // All downstreamOf, hasOwner, knowsUser relationships minus datasetTwo's, outgoing
    assertEqualsAnyOrder(
        service.findRelatedEntities(
            anyType,
            EMPTY_FILTER,
            anyType,
            EMPTY_FILTER,
            Arrays.asList(downstreamOf, hasOwner, knowsUser),
            outgoingRelationships,
            0,
            100),
        Arrays.asList(
            hasOwnerUserOneRelatedEntity, hasOwnerUserTwoRelatedEntity,
            knowsUserOneRelatedEntity, knowsUserTwoRelatedEntity,
            downstreamOfSchemaFieldTwoVia, downstreamOfSchemaFieldTwo));
  }

  @Test
  public void testRemoveUnknownNode() throws Exception {
    GraphService service = getPopulatedGraphService();

    // populated graph asserted in testPopulatedGraphService
    RelatedEntitiesResult entitiesBeforeRemove =
        service.findRelatedEntities(
            anyType,
            EMPTY_FILTER,
            anyType,
            EMPTY_FILTER,
            Arrays.asList(downstreamOf, hasOwner, knowsUser),
            outgoingRelationships,
            0,
            100);

    service.removeNode(unknownUrn);
    syncAfterWrite();

    RelatedEntitiesResult entitiesAfterRemove =
        service.findRelatedEntities(
            anyType,
            EMPTY_FILTER,
            anyType,
            EMPTY_FILTER,
            Arrays.asList(downstreamOf, hasOwner, knowsUser),
            outgoingRelationships,
            0,
            100);
    assertEqualsAnyOrder(entitiesBeforeRemove, entitiesAfterRemove);
  }

  @Test
  public void testClear() throws Exception {
    GraphService service = getPopulatedGraphService();

    // populated graph asserted in testPopulatedGraphService

    service.clear();
    syncAfterWrite();

    // assert the modified graph: check all nodes related to upstreamOf and nextVersionOf edges
    // again
    assertEqualsAnyOrder(
        service.findRelatedEntities(
            ImmutableList.of(datasetType),
            EMPTY_FILTER,
            anyType,
            EMPTY_FILTER,
            Arrays.asList(downstreamOf),
            outgoingRelationships,
            0,
            100),
        Collections.emptyList());
    assertEqualsAnyOrder(
        service.findRelatedEntities(
            ImmutableList.of(userType),
            EMPTY_FILTER,
            anyType,
            EMPTY_FILTER,
            Arrays.asList(hasOwner),
            outgoingRelationships,
            0,
            100),
        Collections.emptyList());
    assertEqualsAnyOrder(
        service.findRelatedEntities(
            anyType,
            EMPTY_FILTER,
            ImmutableList.of(userType),
            EMPTY_FILTER,
            Arrays.asList(knowsUser),
            outgoingRelationships,
            0,
            100),
        Collections.emptyList());
  }

  private List<Edge> getFullyConnectedGraph(int nodes, List<String> relationshipTypes) {
    List<Edge> edges = new ArrayList<>();

    for (int sourceNode = 1; sourceNode <= nodes; sourceNode++) {
      for (int destinationNode = 1; destinationNode <= nodes; destinationNode++) {
        for (String relationship : relationshipTypes) {
          int sourceType = sourceNode % 3;
          Urn source =
              createFromString("urn:li:type" + sourceType + ":(urn:li:node" + sourceNode + ")");
          int destinationType = destinationNode % 3;
          Urn destination =
              createFromString(
                  "urn:li:type" + destinationType + ":(urn:li:node" + destinationNode + ")");

          edges.add(new Edge(source, destination, relationship, null, null, null, null, null));
        }
      }
    }

    return edges;
  }

  @Test
  public void testConcurrentAddEdge() throws Exception {
    final GraphService service = getGraphService();

    // too many edges may cause too many threads throwing
    // java.util.concurrent.RejectedExecutionException: Thread limit exceeded replacing blocked
    // worker
    int nodes = 5;
    int relationshipTypes = 3;
    List<String> allRelationships =
        IntStream.range(1, relationshipTypes + 1)
            .mapToObj(id -> "relationship" + id)
            .collect(Collectors.toList());
    List<Edge> edges = getFullyConnectedGraph(nodes, allRelationships);

    List<Runnable> operations =
        edges.stream()
            .map(
                edge ->
                    new Runnable() {
                      @Override
                      public void run() {
                        service.addEdge(edge);
                      }
                    })
            .collect(Collectors.toList());

    doTestConcurrentOp(operations);
    syncAfterWrite();

    RelatedEntitiesResult relatedEntities =
        service.findRelatedEntities(
            null,
            EMPTY_FILTER,
            null,
            EMPTY_FILTER,
            allRelationships,
            outgoingRelationships,
            0,
            nodes * relationshipTypes * 2);

    Set<RelatedEntity> expectedRelatedEntities =
        edges.stream()
            .map(
                edge ->
                    new RelatedEntity(edge.getRelationshipType(), edge.getDestination().toString()))
            .collect(Collectors.toSet());
    assertEquals(new HashSet<>(relatedEntities.entities), expectedRelatedEntities);
  }

  @Test
  public void testConcurrentRemoveEdgesFromNode() throws Exception {
    final GraphService service = getGraphService();

    int nodes = 5;
    int relationshipTypes = 3;
    List<String> allRelationships =
        IntStream.range(1, relationshipTypes + 1)
            .mapToObj(id -> "relationship" + id)
            .collect(Collectors.toList());
    List<Edge> edges = getFullyConnectedGraph(nodes, allRelationships);

    // add fully connected graph
    edges.forEach(service::addEdge);
    syncAfterWrite();

    // assert the graph is there
    RelatedEntitiesResult relatedEntities =
        service.findRelatedEntities(
            null,
            EMPTY_FILTER,
            null,
            EMPTY_FILTER,
            allRelationships,
            outgoingRelationships,
            0,
            nodes * relationshipTypes * 2);
    assertEquals(relatedEntities.entities.size(), nodes * relationshipTypes);

    // delete all edges concurrently
    List<Runnable> operations =
        edges.stream()
            .map(
                edge ->
                    new Runnable() {
                      @Override
                      public void run() {
                        service.removeEdgesFromNode(
                            edge.getSource(),
                            Arrays.asList(edge.getRelationshipType()),
                            outgoingRelationships);
                      }
                    })
            .collect(Collectors.toList());
    doTestConcurrentOp(operations);
    syncAfterWrite();

    // assert the graph is gone
    RelatedEntitiesResult relatedEntitiesAfterDeletion =
        service.findRelatedEntities(
            null,
            EMPTY_FILTER,
            null,
            EMPTY_FILTER,
            allRelationships,
            outgoingRelationships,
            0,
            nodes * relationshipTypes * 2);
    assertEquals(relatedEntitiesAfterDeletion.entities.size(), 0);
  }

  @Test
  public void testConcurrentRemoveNodes() throws Exception {
    final GraphService service = getGraphService();

    // too many edges may cause too many threads throwing
    // java.util.concurrent.RejectedExecutionException: Thread limit exceeded replacing blocked
    // worker
    int nodes = 5;
    int relationshipTypes = 3;
    List<String> allRelationships =
        IntStream.range(1, relationshipTypes + 1)
            .mapToObj(id -> "relationship" + id)
            .collect(Collectors.toList());
    List<Edge> edges = getFullyConnectedGraph(nodes, allRelationships);

    // add fully connected graph
    edges.forEach(service::addEdge);
    syncAfterWrite();

    // assert the graph is there
    RelatedEntitiesResult relatedEntities =
        service.findRelatedEntities(
            null,
            EMPTY_FILTER,
            null,
            EMPTY_FILTER,
            allRelationships,
            outgoingRelationships,
            0,
            nodes * relationshipTypes * 2);
    assertEquals(relatedEntities.entities.size(), nodes * relationshipTypes);

    // remove all nodes concurrently
    // nodes will be removed multiple times
    List<Runnable> operations =
        edges.stream()
            .map(
                edge ->
                    new Runnable() {
                      @Override
                      public void run() {
                        service.removeNode(edge.getSource());
                      }
                    })
            .collect(Collectors.toList());
    doTestConcurrentOp(operations);
    syncAfterWrite();

    // assert the graph is gone
    RelatedEntitiesResult relatedEntitiesAfterDeletion =
        service.findRelatedEntities(
            null,
            EMPTY_FILTER,
            null,
            EMPTY_FILTER,
            allRelationships,
            outgoingRelationships,
            0,
            nodes * relationshipTypes * 2);
    assertEquals(relatedEntitiesAfterDeletion.entities.size(), 0);
  }

  private void doTestConcurrentOp(List<Runnable> operations) throws Exception {
    final Queue<Throwable> throwables = new ConcurrentLinkedQueue<>();
    final CountDownLatch started = new CountDownLatch(operations.size());
    final CountDownLatch finished = new CountDownLatch(operations.size());
    operations.forEach(
        operation ->
            new Thread(
                    new Runnable() {
                      @Override
                      public void run() {
                        try {
                          started.countDown();

                          try {
                            if (!started.await(10, TimeUnit.SECONDS)) {
                              fail("Timed out waiting for all threads to start");
                            }
                          } catch (InterruptedException e) {
                            fail("Got interrupted waiting for all threads to start");
                          }

                          operation.run();
                        } catch (Throwable t) {
                          t.printStackTrace();
                          throwables.add(t);
                        }
                        finished.countDown();
                      }
                    })
                .start());

    assertTrue(finished.await(getTestConcurrentOpTimeout().toMillis(), TimeUnit.MILLISECONDS));
    throwables.forEach(
        throwable ->
            System.err.printf(
                System.currentTimeMillis() + ": exception occurred: %s%n", throwable));
    assertEquals(throwables.size(), 0);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testPopulatedGraphServiceGetLineageMultihop(boolean attemptMultiPathAlgo)
      throws Exception {

    GraphService service = getLineagePopulatedGraphService(attemptMultiPathAlgo);
    // Implementations other than Neo4J and DGraph explore more of the graph to discover nodes at
    // multiple hops
    boolean expandedGraphAlgoEnabled =
        (!((service instanceof Neo4jGraphService) || (service instanceof DgraphGraphService)));

    EntityLineageResult upstreamLineage =
        service.getLineage(datasetOneUrn, LineageDirection.UPSTREAM, 0, 1000, 2);
    assertEquals(upstreamLineage.getTotal().intValue(), 0);
    assertEquals(upstreamLineage.getRelationships().size(), 0);

    EntityLineageResult downstreamLineage =
        service.getLineage(datasetOneUrn, LineageDirection.DOWNSTREAM, 0, 1000, 2);

    assertEquals(downstreamLineage.getTotal().intValue(), 5);
    assertEquals(downstreamLineage.getRelationships().size(), 5);
    Map<Urn, LineageRelationship> relationships =
        downstreamLineage.getRelationships().stream()
            .collect(Collectors.toMap(LineageRelationship::getEntity, Function.identity()));
    Set<Urn> entities = relationships.keySet().stream().collect(Collectors.toUnmodifiableSet());
    assertEquals(entities.size(), 5);
    assertTrue(relationships.containsKey(datasetTwoUrn));
    assertEquals(relationships.get(dataJobTwoUrn).getDegree(), 1);
    assertTrue(relationships.containsKey(datasetThreeUrn));
    assertEquals(relationships.get(datasetThreeUrn).getDegree(), 2);
    assertTrue(relationships.containsKey(datasetFourUrn));
    assertEquals(relationships.get(datasetFourUrn).getDegree(), 2);
    assertTrue(relationships.containsKey(dataJobOneUrn));
    assertEquals(relationships.get(dataJobOneUrn).getDegree(), 1);
    // dataJobOne is present both at degree 1 and degree 2
    if (expandedGraphAlgoEnabled && attemptMultiPathAlgo) {
      relationships.get(dataJobOneUrn).getDegrees().contains(Integer.valueOf(1));
      relationships.get(dataJobOneUrn).getDegrees().contains(Integer.valueOf(2));
    }
    assertTrue(relationships.containsKey(dataJobTwoUrn));
    assertEquals(relationships.get(dataJobTwoUrn).getDegree(), 1);

    upstreamLineage = service.getLineage(datasetThreeUrn, LineageDirection.UPSTREAM, 0, 1000, 2);
    assertEquals(upstreamLineage.getTotal().intValue(), 3);
    assertEquals(upstreamLineage.getRelationships().size(), 3);
    relationships =
        upstreamLineage.getRelationships().stream()
            .collect(Collectors.toMap(LineageRelationship::getEntity, Function.identity()));
    assertTrue(relationships.containsKey(datasetOneUrn));
    assertEquals(relationships.get(datasetOneUrn).getDegree(), 2);
    assertTrue(relationships.containsKey(datasetTwoUrn));
    assertEquals(relationships.get(datasetTwoUrn).getDegree(), 1);
    assertTrue(relationships.containsKey(dataJobOneUrn));
    assertEquals(relationships.get(dataJobOneUrn).getDegree(), 1);

    downstreamLineage =
        service.getLineage(datasetThreeUrn, LineageDirection.DOWNSTREAM, 0, 1000, 2);
    assertEquals(downstreamLineage.getTotal().intValue(), 0);
    assertEquals(downstreamLineage.getRelationships().size(), 0);
  }
}
