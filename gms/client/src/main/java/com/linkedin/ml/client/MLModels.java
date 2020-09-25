package com.linkedin.ml.client;

import com.linkedin.common.urn.MLModelUrn;
import com.linkedin.common.client.MLModelsClient;
import com.linkedin.metadata.query.AutoCompleteResult;
import com.linkedin.ml.MLModel;
import com.linkedin.ml.MLModelKey;
import com.linkedin.ml.MlModelsDoAutocompleteRequestBuilder;
import com.linkedin.ml.MlModelsFindBySearchRequestBuilder;
import com.linkedin.ml.MlModelsRequestBuilders;
import com.linkedin.r2.RemoteInvocationException;
import com.linkedin.restli.client.BatchGetEntityRequest;
import com.linkedin.restli.client.Client;
import com.linkedin.restli.client.GetRequest;
import com.linkedin.restli.common.CollectionResponse;
import com.linkedin.restli.common.ComplexResourceKey;
import com.linkedin.restli.common.EmptyRecord;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import static com.linkedin.metadata.dao.utils.QueryUtils.*;

public class MLModels extends MLModelsClient {
    private static final MlModelsRequestBuilders ML_MODELS_REQUEST_BUILDERS = new MlModelsRequestBuilders();

    public MLModels(@Nonnull Client restliClient) {
        super(restliClient);
    }

    /**
     * Gets {@link MLModel} model for the given urn
     *
     * @param urn ML Model urn
     * @return {@link MLModel} ML model
     * @throws RemoteInvocationException
     */
    @Nonnull
    public MLModel get(@Nonnull MLModelUrn urn)
        throws RemoteInvocationException {
        GetRequest<MLModel> getRequest = ML_MODELS_REQUEST_BUILDERS.get()
            .id(new ComplexResourceKey<>(toMLModelKey(urn), new EmptyRecord()))
            .build();

        return _client.sendRequest(getRequest).getResponse().getEntity();
    }

    /**
     * Searches for ML models matching to a given query and filters
     *
     * @param input search query
     * @param requestFilters search filters
     * @param start start offset for search results
     * @param count max number of search results requested
     * @return Snapshot key
     * @throws RemoteInvocationException
     */
    @Nonnull
    public CollectionResponse<MLModel> search(@Nonnull String input, @Nonnull Map<String, String> requestFilters,
        int start, int count) throws RemoteInvocationException {

        MlModelsFindBySearchRequestBuilder requestBuilder = ML_MODELS_REQUEST_BUILDERS
            .findBySearch()
            .inputParam(input)
            .filterParam(newFilter(requestFilters)).paginate(start, count);
        return _client.sendRequest(requestBuilder.build()).getResponse().getEntity();
    }

    /**
     * Gets browse snapshot of a given path
     *
     * @param query search query
     * @param field field of the ML Model
     * @param requestFilters autocomplete filters
     * @param limit max number of autocomplete results
     * @throws RemoteInvocationException
     */
    @Nonnull
    public AutoCompleteResult autoComplete(@Nonnull String query, @Nonnull String field,
        @Nonnull Map<String, String> requestFilters,
        @Nonnull int limit) throws RemoteInvocationException {
        MlModelsDoAutocompleteRequestBuilder requestBuilder = ML_MODELS_REQUEST_BUILDERS
            .actionAutocomplete()
            .queryParam(query)
            .fieldParam(field)
            .filterParam(newFilter(requestFilters))
            .limitParam(limit);
        return _client.sendRequest(requestBuilder.build()).getResponse().getEntity();
    }

    /**
     * Batch gets list of {@link MLModel}
     *
     * @param urns list of model urn
     * @return map of {@link MLModel}
     * @throws RemoteInvocationException
     */
    @Nonnull
    public Map<MLModelUrn, MLModel> batchGet(@Nonnull Set<MLModelUrn> urns)
        throws RemoteInvocationException {
        BatchGetEntityRequest<ComplexResourceKey<MLModelKey, EmptyRecord>, MLModel> batchGetRequest
            = ML_MODELS_REQUEST_BUILDERS.batchGet()
            .ids(urns.stream().map(this::getKeyFromUrn).collect(Collectors.toSet()))
            .build();

        return _client.sendRequest(batchGetRequest).getResponseEntity().getResults()
            .entrySet().stream().collect(Collectors.toMap(
                entry -> getUrnFromKey(entry.getKey()),
                entry -> entry.getValue().getEntity())
            );
    }

    @Nonnull
    private ComplexResourceKey<MLModelKey, EmptyRecord> getKeyFromUrn(@Nonnull MLModelUrn urn) {
        return new ComplexResourceKey<>(toMLModelKey(urn), new EmptyRecord());
    }

    @Nonnull
    private MLModelUrn getUrnFromKey(@Nonnull ComplexResourceKey<MLModelKey, EmptyRecord> key) {
        return toModelUrn(key.getKey());
    }
}
