package com.linkedin.datahub.graphql.resolvers.mutate;

import com.linkedin.datahub.graphql.exception.AuthorizationException;
import com.linkedin.datahub.graphql.types.BatchMutableType;
import com.linkedin.metadata.utils.metrics.MetricUtils;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.micrometer.core.instrument.LongTaskTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.linkedin.datahub.graphql.resolvers.ResolverUtils.*;


/**
 * Generic GraphQL resolver responsible for performing updates against particular types.
 *
 * @param <I> the generated GraphQL POJO corresponding to the input type.
 * @param <T> the generated GraphQL POJO corresponding to the return type.
 */
public class MutableTypeBatchResolver<I, B, T> implements DataFetcher<CompletableFuture<List<T>>> {

  private static final Logger _logger = LoggerFactory.getLogger(MutableTypeBatchResolver.class.getName());

  private final BatchMutableType<I, B, T> _batchMutableType;

  public MutableTypeBatchResolver(final BatchMutableType<I, B, T> batchMutableType) {
    _batchMutableType = batchMutableType;
  }

  @Override
  public CompletableFuture<List<T>> get(DataFetchingEnvironment environment) throws Exception {
    final B[] input = bindArgument(environment.getArgument("input"), _batchMutableType.batchInputClass());

    return CompletableFuture.supplyAsync(() -> {
      LongTaskTimer.Sample timer = MetricUtils.timer(this.getClass(), "batchMutate").start();
      try {
        return _batchMutableType.batchUpdate(input, environment.getContext());
      } catch (AuthorizationException e) {
        throw e;
      } catch (Exception e) {
        _logger.error("Failed to perform batchUpdate", e);
        throw new IllegalArgumentException(e);
      } finally {
        timer.stop();
      }
    });
  }
}
