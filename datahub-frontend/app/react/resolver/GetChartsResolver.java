package react.resolver;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.AnalyticsChart;
import graphql.AnalyticsChartGroup;
import graphql.BarChart;
import graphql.DateInterval;
import graphql.DateRange;
import graphql.NamedBar;
import graphql.NamedLine;
import graphql.Row;
import graphql.TableChart;
import graphql.TimeSeriesChart;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.joda.time.DateTime;
import react.analytics.AnalyticsService;


/**
 * Retrieves the Charts to be rendered of the Analytics screen of the DataHub application.
 */
public final class GetChartsResolver implements DataFetcher<List<AnalyticsChartGroup>> {

  private final AnalyticsService _analyticsService;

  public GetChartsResolver(final AnalyticsService analyticsService) {
    _analyticsService = analyticsService;
  }

  @Override
  public final List<AnalyticsChartGroup> get(DataFetchingEnvironment environment) throws Exception {

    final DateTime now = DateTime.now();

    final AnalyticsChartGroup lastWeekGroup = new AnalyticsChartGroup();
    lastWeekGroup.setTitle("Product Analytics (Last Week)");
    final DateRange dateRangeLastWeek =
        new DateRange(String.valueOf(now.minusWeeks(1).getMillis()), String.valueOf(now.getMillis()));
    lastWeekGroup.setCharts(getProductAnalyticsCharts(dateRangeLastWeek));

    final AnalyticsChartGroup lastMonthGroup = new AnalyticsChartGroup();
    lastMonthGroup.setTitle("Product Analytics (Last Month)");
    final DateRange dateRangeLastMonth =
        new DateRange(String.valueOf(now.minusMonths(1).getMillis()), String.valueOf(now.getMillis()));
    lastMonthGroup.setCharts(getProductAnalyticsCharts(dateRangeLastMonth));
    
    return ImmutableList.of(lastWeekGroup, lastMonthGroup);
  }

  /**
   * TODO: Config Driven Charts Instead of Hardcoded.
   */
  private List<AnalyticsChart> getProductAnalyticsCharts(final DateRange dateRange) {
    final List<AnalyticsChart> charts = new ArrayList<>();
    // Chart 1:  Time Series Chart
    String title = "Searches";
    DateInterval granularity = DateInterval.DAY;
    String eventType = "SearchEvent";

    final List<NamedLine> searchesTimeseries =
        _analyticsService.getTimeseriesChart(AnalyticsService.DATAHUB_USAGE_EVENT_INDEX, dateRange, granularity,
            Optional.empty(), ImmutableMap.of("type", ImmutableList.of("SearchEvent")), Optional.empty());
    charts.add(TimeSeriesChart.builder()
        .setTitle(title)
        .setDateRange(dateRange)
        .setInterval(granularity)
        .setLines(searchesTimeseries)
        .build());

    // Chart 2: Table Chart
    final String title2 = "Top Search Queries";
    final List<String> columns = ImmutableList.of("Query", "Count");

    final List<Row> topSearchQueries =
        _analyticsService.getTopNTableChart(AnalyticsService.DATAHUB_USAGE_EVENT_INDEX, Optional.of(dateRange),
            "query.keyword", ImmutableMap.of("type", ImmutableList.of(eventType)), Optional.empty(), 10);
    charts.add(TableChart.builder().setTitle(title2).setColumns(columns).setRows(topSearchQueries).build());

    // Chart 3: Bar Graph Chart
    final String title3 = "Section Views across Entity Types";
    final List<NamedBar> sectionViewsPerEntityType =
        _analyticsService.getBarChart(AnalyticsService.DATAHUB_USAGE_EVENT_INDEX, Optional.of(dateRange),
            ImmutableList.of("entityType.keyword", "section.keyword"),
            ImmutableMap.of("type", ImmutableList.of("EntitySectionViewEvent")), Optional.empty());
    charts.add(BarChart.builder().setTitle(title3).setBars(sectionViewsPerEntityType).build());

    // Chart 4: Bar Graph Chart
    final String title4 = "Actions by Entity Type";
    final List<NamedBar> eventsByEventType =
        _analyticsService.getBarChart(AnalyticsService.DATAHUB_USAGE_EVENT_INDEX, Optional.of(dateRange),
            ImmutableList.of("entityType.keyword", "actionType.keyword"),
            ImmutableMap.of("type", ImmutableList.of("EntityActionEvent")), Optional.empty());
    charts.add(BarChart.builder().setTitle(title4).setBars(eventsByEventType).build());

    // Chart 5: Table Chart
    final String title5 = "Top Viewed Dataset";
    final List<String> columns5 = ImmutableList.of("Dataset", "#Views");

    final List<Row> topViewedDatasets =
        _analyticsService.getTopNTableChart(AnalyticsService.DATAHUB_USAGE_EVENT_INDEX, Optional.of(dateRange),
            "dataset_name.keyword", ImmutableMap.of("type", ImmutableList.of("EntityViewEvent")), Optional.empty(), 10);
    charts.add(TableChart.builder().setTitle(title5).setColumns(columns5).setRows(topViewedDatasets).build());
    
    // Chart 6: Table Chart
    final String title6 = "Top Users";
    final List<String> columns6 = ImmutableList.of("User", "Count");

    final List<Row> topUsers =
        _analyticsService.getTopNTableChart(AnalyticsService.DATAHUB_USAGE_EVENT_INDEX, Optional.of(dateRange),
            "corp_user_username.keyword", ImmutableMap.of("type", ImmutableList.of("EntityActionEvent")), Optional.empty(), 10);
    charts.add(TableChart.builder().setTitle(title6).setColumns(columns6).setRows(topUsers).build());

    // Chart 7: Table Chart
    final String title7 = "Top Entity Viewers";
    final List<String> columns7 = ImmutableList.of("User", "Count");

    final List<Row> topEntityViewers =
        _analyticsService.getTopNTableChart(AnalyticsService.DATAHUB_USAGE_EVENT_INDEX, Optional.of(dateRange),
            "corp_user_username.keyword", ImmutableMap.of("type", ImmutableList.of("EntityViewEvent")), Optional.empty(), 10);
    charts.add(TableChart.builder().setTitle(title7).setColumns(columns7).setRows(topEntityViewers).build());
    
    return charts;
  }
}
