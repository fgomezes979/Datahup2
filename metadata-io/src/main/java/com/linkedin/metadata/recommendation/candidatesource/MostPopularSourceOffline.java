package com.linkedin.metadata.recommendation.candidatesource;

import com.codahale.metrics.Timer;
import com.datahub.util.exception.ESQueryException;
import com.linkedin.common.urn.Urn;
import com.linkedin.entity.EnvelopedAspect;
import com.linkedin.metadata.datahubusage.DataHubUsageEventConstants;
import com.linkedin.metadata.datahubusage.DataHubUsageEventType;
import com.linkedin.metadata.entity.EntityService;
import com.linkedin.metadata.recommendation.*;
import com.linkedin.metadata.search.utils.ESUtils;
import com.linkedin.metadata.utils.elasticsearch.IndexConvention;
import com.linkedin.metadata.utils.metrics.MetricUtils;
import io.opentelemetry.extension.annotations.WithSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedTerms;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class MostPopularSourceOffline implements RecommendationSource {
    private final EntityService _searchClient;
    private final IndexConvention _indexConvention;

    private static final String DATAHUB_USAGE_INDEX = "datahub_usage_event";
    private static final String ENTITY_AGG_NAME = "entity";
    private static final int MAX_CONTENT = 5;

    @Override
    public String getTitle() {
        return "Most Popular";
    }

    @Override
    public String getModuleId() {
        return "HighUsageEntities";
    }

    @Override
    public RecommendationRenderType getRenderType() {
        return RecommendationRenderType.ENTITY_NAME_LIST;
    }

    @Override
    public boolean isEligible(@Nonnull Urn userUrn, @Nonnull RecommendationRequestContext requestContext) {
        boolean analyticsEnabled = false;
        try {
            analyticsEnabled = _searchClient.indices()
                    .exists(new GetIndexRequest(_indexConvention.getIndexName(DATAHUB_USAGE_INDEX)), RequestOptions.DEFAULT);
        } catch (IOException e) {
            log.error("Failed to determine whether DataHub usage index exists");
        }
        return requestContext.getScenario() == ScenarioType.HOME && analyticsEnabled;
    }

    @Override
    @WithSpan
    public List<RecommendationContent> getRecommendations(@Nonnull Urn userUrn,
                                                          @Nonnull RecommendationRequestContext requestContext) {
        SearchRequest searchRequest = buildSearchRequest(userUrn);
        try (Timer.Context ignored = MetricUtils.timer(this.getClass(), "getMostPopular").time()) {
            EnvelopedAspect envelopedAspect = _searchClient.getEnvelopedAspect("recommendationModule", userUrn, "recommendationModule"0);
            //envelopedAspect.
            final SearchResponse searchResponse = _searchClient.search(searchRequest, RequestOptions.DEFAULT);
            // extract results
            ParsedTerms parsedTerms = searchResponse.getAggregations().get(ENTITY_AGG_NAME);
            return parsedTerms.getBuckets()
                    .stream()
                    .map(bucket -> buildContent(bucket.getKeyAsString()))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Search query to get most popular entities failed", e);
            throw new ESQueryException("Search query failed:", e);
        }
    }

    private SearchRequest buildSearchRequest(@Nonnull Urn userUrn) {
        SearchRequest request = new SearchRequest();
        SearchSourceBuilder source = new SearchSourceBuilder();
        BoolQueryBuilder query = QueryBuilders.boolQuery();
        // Filter for all entity view events
        query.must(
                QueryBuilders.termQuery(DataHubUsageEventConstants.TYPE, DataHubUsageEventType.ENTITY_VIEW_EVENT.getType()));
        source.query(query);

        // Find the entities with the most views
        AggregationBuilder aggregation = AggregationBuilders.terms(ENTITY_AGG_NAME)
                .field(DataHubUsageEventConstants.ENTITY_URN + ESUtils.KEYWORD_SUFFIX)
                .size(MAX_CONTENT);
        source.aggregation(aggregation);
        source.size(0);

        request.source(source);
        request.indices(_indexConvention.getIndexName(DATAHUB_USAGE_INDEX));
        return request;
    }

    private Optional<RecommendationContent> buildContent(@Nonnull String entityUrn) {
        Urn entity;
        try {
            entity = Urn.createFromString(entityUrn);
        } catch (URISyntaxException e) {
            log.error("Error decoding entity URN: {}", entityUrn, e);
            return Optional.empty();
        }
        return Optional.of(new RecommendationContent().setEntity(entity)
                .setValue(entityUrn)
                .setParams(new RecommendationParams().setEntityProfileParams(new EntityProfileParams().setUrn(entity))));
    }
}
