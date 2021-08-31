package com.linkedin.metadata.resources.usage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkedin.common.WindowDuration;
import com.linkedin.common.urn.Urn;
import com.linkedin.data.template.StringArray;
import com.linkedin.metadata.query.Condition;
import com.linkedin.metadata.query.Criterion;
import com.linkedin.metadata.query.CriterionArray;
import com.linkedin.metadata.query.Filter;
import com.linkedin.metadata.restli.RestliUtils;
import com.linkedin.metadata.timeseries.elastic.ElasticSearchTimeseriesAspectService;
import com.linkedin.metadata.usage.UsageService;
import com.linkedin.parseq.Task;
import com.linkedin.restli.server.annotations.Action;
import com.linkedin.restli.server.annotations.ActionParam;
import com.linkedin.restli.server.annotations.RestLiSimpleResource;
import com.linkedin.restli.server.resources.SimpleResourceTemplate;
import com.linkedin.timeseries.AggregationSpec;
import com.linkedin.timeseries.AggregationType;
import com.linkedin.timeseries.CalendarInterval;
import com.linkedin.timeseries.DateGroupingBucket;
import com.linkedin.timeseries.GenericTable;
import com.linkedin.timeseries.GroupingBucket;
import com.linkedin.timeseries.StringGroupingBucket;
import com.linkedin.usage.FieldUsageCounts;
import com.linkedin.usage.FieldUsageCountsArray;
import com.linkedin.usage.UsageAggregation;
import com.linkedin.usage.UsageAggregationArray;
import com.linkedin.usage.UsageAggregationMetrics;
import com.linkedin.usage.UsageQueryResult;
import com.linkedin.usage.UsageQueryResultAggregations;
import com.linkedin.usage.UsageTimeRange;
import com.linkedin.usage.UserUsageCounts;
import com.linkedin.usage.UserUsageCountsArray;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Rest.li entry point: /usageStats
 */
@RestLiSimpleResource(name = "usageStats", namespace = "com.linkedin.usage")
public class UsageStats extends SimpleResourceTemplate<UsageAggregation> {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String ACTION_BATCH_INGEST = "batchIngest";
  private static final String PARAM_BUCKETS = "buckets";

  private static final String ACTION_QUERY = "query";
  private static final String PARAM_RESOURCE = "resource";
  private static final String PARAM_DURATION = "duration";
  private static final String PARAM_START_TIME = "startTime";
  private static final String PARAM_END_TIME = "endTime";
  private static final String PARAM_MAX_BUCKETS = "maxBuckets";

  private static final String ACTION_QUERY_RANGE = "queryRange";
  private static final String PARAM_RANGE = "rangeFromEnd";
  private static final String USAGE_STATS_ENTITY_NAME = "dataset";
  private static final String USAGE_STATS_ASPECT_NAME = "datasetUsageStatistics";
  private static final String ES_FIELD_TIMESTAMP = "timestampMillis";
  private static final String ES_NULL_VALUE = "NULL";
  private final Logger _logger = LoggerFactory.getLogger(UsageStats.class.getName());
  @Inject
  @Named("usageService")
  private UsageService _usageService;
  @Inject
  @Named("elasticSearchTimeseriesAspectService")
  private ElasticSearchTimeseriesAspectService _elasticSearchTimeseriesAspectService;

  @Action(name = ACTION_BATCH_INGEST)
  @Nonnull
  public Task<Void> batchIngest(@ActionParam(PARAM_BUCKETS) @Nonnull UsageAggregation[] buckets) {
    _logger.info("Ingesting {} usage stats aggregations", buckets.length);
    return RestliUtils.toTask(() -> {
      for (UsageAggregation agg : buckets) {
        this.ingest(agg);
      }
      return null;
    });
  }

  private CalendarInterval windowToInterval(@Nonnull WindowDuration duration) {
    switch (duration) {
      case HOUR:
        return CalendarInterval.HOUR;
      case DAY:
        return CalendarInterval.DAY;
      case WEEK:
        return CalendarInterval.WEEK;
      case MONTH:
        return CalendarInterval.MONTH;
      case YEAR:
        return CalendarInterval.YEAR;
      default:
        throw new IllegalArgumentException("Unsupported duration value" + duration);
    }
  }

  private UsageAggregationArray getBuckets(@Nonnull Filter filter, @Nonnull String resource,
      @Nonnull WindowDuration duration) {
    // NOTE: We will not populate the per-bucket userCounts and fieldCounts in this implementation because
    // (a) it is very expensive to compute the un-explode equivalent queries for temporal stat collections, and
    // (b) the equivalent data for the whole query will anyways be populated in the `aggregations` part of the results
    // (see getAggregations).

    // 1. Construct the aggregation specs for latest value of uniqueUserCount, totalSqlQueries & topSqlQueries.
    AggregationSpec uniqueUserCountAgg =
        new AggregationSpec().setAggregationType(AggregationType.LATEST).setMemberName("uniqueUserCount");
    AggregationSpec totalSqlQueriesAgg =
        new AggregationSpec().setAggregationType(AggregationType.LATEST).setMemberName("totalSqlQueries");
    AggregationSpec topSqlQueriesAgg =
        new AggregationSpec().setAggregationType(AggregationType.LATEST).setMemberName("topSqlQueries");
    AggregationSpec[] aggregationSpecs =
        new AggregationSpec[]{uniqueUserCountAgg, totalSqlQueriesAgg, topSqlQueriesAgg};

    // 2. Construct the Grouping buckets with just the ts bucket.
    GroupingBucket timestampBucket = new GroupingBucket();
    timestampBucket.setDateGroupingBucket(
        new DateGroupingBucket().setKey(ES_FIELD_TIMESTAMP).setGranularity(windowToInterval(duration)));
    GroupingBucket[] groupingBuckets = new GroupingBucket[]{timestampBucket};

    // 3. Query
    GenericTable result =
        _elasticSearchTimeseriesAspectService.getAggregatedStats(USAGE_STATS_ENTITY_NAME, USAGE_STATS_ASPECT_NAME,
            aggregationSpecs, filter, groupingBuckets);

    // 4. Populate buckets from the result.
    UsageAggregationArray buckets = new UsageAggregationArray();
    for (StringArray row : result.getRows()) {
      UsageAggregation usageAggregation = new UsageAggregation();
      usageAggregation.setBucket(Long.valueOf(row.get(0)));
      usageAggregation.setDuration(duration);
      try {
        usageAggregation.setResource(new Urn(resource));
      } catch (URISyntaxException e) {
        throw new IllegalArgumentException("Invalid resource" + e);
      }
      UsageAggregationMetrics usageAggregationMetrics = new UsageAggregationMetrics();
      if (!row.get(1).equals(ES_NULL_VALUE)) {
        usageAggregationMetrics.setUniqueUserCount(Integer.valueOf(row.get(1)));
      }
      if (!row.get(2).equals(ES_NULL_VALUE)) {
        usageAggregationMetrics.setTotalSqlQueries(Integer.valueOf(row.get(2)));
      }
      if (!row.get(3).equals(ES_NULL_VALUE)) {
        try {
          usageAggregationMetrics.setTopSqlQueries(OBJECT_MAPPER.readValue(row.get(3), StringArray.class));
        } catch (JsonProcessingException e) {
          throw new IllegalArgumentException("Failed to convert topSqlQueries from ES to object" + e);
        }
      }
      usageAggregation.setMetrics(usageAggregationMetrics);
      buckets.add(usageAggregation);
    }

    return buckets;
  }

  private List<UserUsageCounts> getUserUsageCounts(Filter filter) {
    // Sum aggregation on userCounts.count
    AggregationSpec sumUserCountsCountAggSpec =
        new AggregationSpec().setAggregationType(AggregationType.SUM).setMemberName("userCounts.count");
    AggregationSpec[] aggregationSpecs = new AggregationSpec[]{sumUserCountsCountAggSpec};

    // String grouping bucket on userCounts.user
    GroupingBucket userGroupingBucket = new GroupingBucket();
    userGroupingBucket.setStringGroupingBucket(new StringGroupingBucket().setKey("userCounts.user"));
    GroupingBucket[] groupingBuckets = new GroupingBucket[]{userGroupingBucket};

    // Query backend
    GenericTable result =
        _elasticSearchTimeseriesAspectService.getAggregatedStats(USAGE_STATS_ENTITY_NAME, USAGE_STATS_ASPECT_NAME,
            aggregationSpecs, filter, groupingBuckets);
    // Process response
    List<UserUsageCounts> userUsageCounts = new ArrayList<>();
    for (StringArray row : result.getRows()) {
      UserUsageCounts userUsageCount = new UserUsageCounts();
      try {
        userUsageCount.setUser(new Urn(row.get(0)));
      } catch (URISyntaxException e) {
        _logger.error("Failed to convert {} to urn. Exception: {}", row.get(0), e);
      }
      if (!row.get(1).equals(ES_NULL_VALUE)) {
        userUsageCount.setCount(Integer.valueOf(row.get(1)));
      }
      userUsageCounts.add(userUsageCount);
    }
    return userUsageCounts;
  }

  private List<FieldUsageCounts> getFieldUsageCounts(Filter filter) {
    // Sum aggregation on fieldCounts.count
    AggregationSpec sumFieldCountAggSpec =
        new AggregationSpec().setAggregationType(AggregationType.SUM).setMemberName("fieldCounts.count");
    AggregationSpec[] aggregationSpecs = new AggregationSpec[]{sumFieldCountAggSpec};

    // String grouping bucket on fieldCounts.fieldName
    GroupingBucket userGroupingBucket = new GroupingBucket();
    userGroupingBucket.setStringGroupingBucket(new StringGroupingBucket().setKey("fieldCounts.fieldName"));
    GroupingBucket[] groupingBuckets = new GroupingBucket[]{userGroupingBucket};

    // Query backend
    GenericTable result =
        _elasticSearchTimeseriesAspectService.getAggregatedStats(USAGE_STATS_ENTITY_NAME, USAGE_STATS_ASPECT_NAME,
            aggregationSpecs, filter, groupingBuckets);

    // Process response
    List<FieldUsageCounts> fieldUsageCounts = new ArrayList<>();
    for (StringArray row : result.getRows()) {
      FieldUsageCounts fieldUsageCount = new FieldUsageCounts();
      fieldUsageCount.setFieldName(row.get(0));
      if (!row.get(1).equals(ES_NULL_VALUE)) {
        fieldUsageCount.setCount(Integer.valueOf(row.get(1)));
      }
      fieldUsageCounts.add(fieldUsageCount);
    }
    return fieldUsageCounts;
  }

  private UsageQueryResultAggregations getAggregations(Filter filter) {
    // TODO: make the aggregation computation logic reusable
    UsageQueryResultAggregations aggregations = new UsageQueryResultAggregations();
    List<UserUsageCounts> userUsageCounts = getUserUsageCounts(filter);
    aggregations.setUsers(new UserUsageCountsArray(userUsageCounts));
    aggregations.setUniqueUserCount(userUsageCounts.size());

    List<FieldUsageCounts> fieldUsageCounts = getFieldUsageCounts(filter);
    aggregations.setFields(new FieldUsageCountsArray(fieldUsageCounts));

    return aggregations;
  }

  @Action(name = ACTION_QUERY)
  @Nonnull
  public Task<UsageQueryResult> query(@ActionParam(PARAM_RESOURCE) @Nonnull String resource,
      @ActionParam(PARAM_DURATION) @Nonnull WindowDuration duration,
      @ActionParam(PARAM_START_TIME) @com.linkedin.restli.server.annotations.Optional Long startTime,
      @ActionParam(PARAM_END_TIME) @com.linkedin.restli.server.annotations.Optional Long endTime,
      @ActionParam(PARAM_MAX_BUCKETS) @com.linkedin.restli.server.annotations.Optional Integer maxBuckets) {
    _logger.info("Attempting to query usage stats");
    return RestliUtils.toTask(() -> {
      // 1. Populate the filter. This is common for all queries.
      Filter filter = new Filter();
      ArrayList<Criterion> criteria = new ArrayList<>();
      Criterion hasUrnCriterion = new Criterion().setField("urn").setCondition(Condition.EQUAL).setValue(resource);
      criteria.add(hasUrnCriterion);
      if (startTime != null) {
        Criterion startTimeCriterion = new Criterion().setField(ES_FIELD_TIMESTAMP)
            .setCondition(Condition.GREATER_THAN_OR_EQUAL_TO)
            .setValue(startTime.toString());
        criteria.add(startTimeCriterion);
      }
      if (endTime != null) {
        Criterion endTimeCriterion = new Criterion().setField(ES_FIELD_TIMESTAMP)
            .setCondition(Condition.LESS_THAN_OR_EQUAL_TO)
            .setValue(endTime.toString());
        criteria.add(endTimeCriterion);
      }
      filter.setCriteria(new CriterionArray(criteria));

      // 2. Get buckets.
      UsageAggregationArray buckets = getBuckets(filter, resource, duration);

      // 3. Get aggregations.
      UsageQueryResultAggregations aggregations = getAggregations(filter);

      // 4. Compute totalSqlQuery count from the buckets itself.
      // We want to avoid issuing an additional query with a sum aggregation.
      Integer totalQueryCount = null;
      for (UsageAggregation bucket : buckets) {
        if (bucket.getMetrics().getTotalSqlQueries() != null) {
          if (totalQueryCount == null) {
            totalQueryCount = 0;
          }
          totalQueryCount += bucket.getMetrics().getTotalSqlQueries();
        }
      }

      if (totalQueryCount != null) {
        aggregations.setTotalSqlQueries(totalQueryCount);
      }

      // 5. Populate and return the result.
      return new UsageQueryResult().setBuckets(buckets).setAggregations(aggregations);
    });
  }

  @Action(name = ACTION_QUERY_RANGE)
  @Nonnull
  public Task<UsageQueryResult> queryRange(@ActionParam(PARAM_RESOURCE) @Nonnull String resource,
      @ActionParam(PARAM_DURATION) @Nonnull WindowDuration duration, @ActionParam(PARAM_RANGE) UsageTimeRange range) {
    final long now = Instant.now().toEpochMilli();
    return this.query(resource, duration, convertRangeToStartTime(range, now), now, null);
  }

  private void ingest(@Nonnull UsageAggregation bucket) {
    // TODO attempt to resolve users into emails
    _usageService.upsertDocument(bucket);
  }

  @Nonnull
  Long convertRangeToStartTime(@Nonnull UsageTimeRange range, long currentEpochMillis) {
    // TRICKY: since start_time must be before the bucket's start, we actually
    // need to subtract extra from the current time to ensure that we get precisely
    // what we're looking for. Note that start_time and end_time are both inclusive,
    // so we must also do an off-by-one adjustment.
    final long oneHourMillis = 60 * 60 * 1000;
    final long oneDayMillis = 24 * oneHourMillis;

    if (range == UsageTimeRange.HOUR) {
      return currentEpochMillis - (2 * oneHourMillis + 1);
    } else if (range == UsageTimeRange.DAY) {
      return currentEpochMillis - (2 * oneDayMillis + 1);
    } else if (range == UsageTimeRange.WEEK) {
      return currentEpochMillis - (8 * oneDayMillis + 1);
    } else if (range == UsageTimeRange.MONTH) {
      // Assuming month is last 30 days.
      return currentEpochMillis - (31 * oneDayMillis + 1);
    } else if (range == UsageTimeRange.QUARTER) {
      // Assuming a quarter is 91 days.
      return currentEpochMillis - (92 * oneDayMillis + 1);
    } else if (range == UsageTimeRange.YEAR) {
      return currentEpochMillis - (366 * oneDayMillis + 1);
    } else if (range == UsageTimeRange.ALL) {
      return 0L;
    } else {
      throw new IllegalArgumentException("invalid UsageTimeRange enum state: " + range.name());
    }
  }
}
