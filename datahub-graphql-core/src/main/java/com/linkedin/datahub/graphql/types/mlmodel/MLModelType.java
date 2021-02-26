package com.linkedin.datahub.graphql.types.mlmodel;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.ImmutableSet;
import com.linkedin.common.urn.MLModelUrn;
import com.linkedin.datahub.graphql.QueryContext;
import com.linkedin.datahub.graphql.generated.AutoCompleteResults;
import com.linkedin.datahub.graphql.generated.BrowsePath;
import com.linkedin.datahub.graphql.generated.BrowseResults;
import com.linkedin.datahub.graphql.generated.EntityType;
import com.linkedin.datahub.graphql.generated.FacetFilterInput;
import com.linkedin.datahub.graphql.generated.MLModel;
import com.linkedin.datahub.graphql.generated.SearchResults;
import com.linkedin.datahub.graphql.resolvers.ResolverUtils;
import com.linkedin.datahub.graphql.types.BrowsableEntityType;
import com.linkedin.datahub.graphql.types.SearchableEntityType;
import com.linkedin.datahub.graphql.types.mappers.AutoCompleteResultsMapper;
import com.linkedin.datahub.graphql.types.mappers.MlModelMapper;
import com.linkedin.datahub.graphql.types.mappers.SearchResultsMapper;
import com.linkedin.metadata.query.AutoCompleteResult;
import com.linkedin.ml.client.MLModels;
import com.linkedin.restli.common.CollectionResponse;

import lombok.NonNull;

public class MLModelType implements SearchableEntityType<MLModel>, BrowsableEntityType<MLModel> {

    private static final Set<String> FACET_FIELDS = ImmutableSet.of("origin", "platform");
    private static final String DEFAULT_AUTO_COMPLETE_FIELD = "name";
    private final MLModels _mlModelsClient;

    public MLModelType(MLModels _mlModelsClient) {
        this._mlModelsClient = _mlModelsClient;
    }

    @Override
    public EntityType type() {
        return EntityType.MLMODEL;
    }

    @Override
    public Class<MLModel> objectClass() {
        return MLModel.class;
    }

    @Override
    public List<MLModel> batchLoad(final List<String> urns, final QueryContext context) throws Exception {
        final List<MLModelUrn> mlModelUrns = urns.stream()
            .map(MlModelUtils::getMLModelUrn)
            .collect(Collectors.toList());

        try {
            final Map<MLModelUrn, com.linkedin.ml.MLModel> mlModelMap = _mlModelsClient.batchGet(mlModelUrns
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

            final List<com.linkedin.ml.MLModel> gmsResults = mlModelUrns.stream()
                .map(modelUrn -> mlModelMap.getOrDefault(modelUrn, null)).collect(Collectors.toList());

            return gmsResults.stream()
                .map(gmsMlModel -> gmsMlModel == null ? null : MlModelMapper.map(gmsMlModel))
                .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to batch load Datasets", e);
        }
    }

    @Override
    public SearchResults search(@Nonnull String query,
        @Nullable List<FacetFilterInput> filters,
        int start,
        int count,
        @Nonnull final QueryContext context) throws Exception {
        final Map<String, String> facetFilters = ResolverUtils.buildFacetFilters(filters, FACET_FIELDS);
        final CollectionResponse<com.linkedin.ml.MLModel> searchResult = _mlModelsClient.search(query, facetFilters, start, count);
        return SearchResultsMapper.map(searchResult, MlModelMapper::map);
    }

    @Override
    public AutoCompleteResults autoComplete(@Nonnull String query,
        @Nullable String field,
        @Nullable List<FacetFilterInput> filters,
        int limit,
        @Nonnull final QueryContext context) throws Exception {
        final Map<String, String> facetFilters = ResolverUtils.buildFacetFilters(filters, FACET_FIELDS);
        field = field != null ? field : DEFAULT_AUTO_COMPLETE_FIELD;
        final AutoCompleteResult result = _mlModelsClient.autoComplete(query, field, facetFilters, limit);
        return AutoCompleteResultsMapper.map(result);
    }

    @Override
    public BrowseResults browse(@Nonnull List<String> path,
        @Nullable List<FacetFilterInput> filters,
        int start,
        int count,
        @Nonnull final QueryContext context) throws Exception {
        //TODO Implement Browse in MLModels Client
        return null;
    }

    @Override
    public List<BrowsePath> browsePaths(@NonNull String urn, @NonNull QueryContext context) throws Exception {
        // TODO Implement Browse Paths in MLModels Client
        return null;
    }
}
