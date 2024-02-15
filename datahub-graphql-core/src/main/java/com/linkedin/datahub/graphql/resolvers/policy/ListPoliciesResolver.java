package com.linkedin.datahub.graphql.resolvers.policy;

import static com.linkedin.datahub.graphql.resolvers.ResolverUtils.bindArgument;

import com.datahub.authorization.PolicyFetcher;
import com.linkedin.datahub.graphql.QueryContext;
import com.linkedin.datahub.graphql.exception.AuthorizationException;
import com.linkedin.datahub.graphql.generated.ListPoliciesInput;
import com.linkedin.datahub.graphql.generated.ListPoliciesResult;
import com.linkedin.datahub.graphql.generated.Policy;
import com.linkedin.datahub.graphql.resolvers.policy.mappers.PolicyInfoPolicyMapper;
import com.linkedin.entity.client.EntityClient;
import com.linkedin.metadata.query.filter.ConjunctiveCriterion;
import com.linkedin.metadata.query.filter.ConjunctiveCriterionArray;
import com.linkedin.metadata.query.filter.Criterion;
import com.linkedin.metadata.query.filter.CriterionArray;
import com.linkedin.metadata.query.filter.Filter;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// import com.linkedin.metadata.query.filter.DisjunctiveCriterionArray;

@Slf4j
public class ListPoliciesResolver implements DataFetcher<CompletableFuture<ListPoliciesResult>> {

  private static final Logger logger = LoggerFactory.getLogger(ListPoliciesResolver.class);
  private static final Integer DEFAULT_START = 0;
  private static final Integer DEFAULT_COUNT = 20;
  private static final String DEFAULT_QUERY = "";

  private final PolicyFetcher _policyFetcher;

  public ListPoliciesResolver(final EntityClient entityClient) {
    _policyFetcher = new PolicyFetcher(entityClient);
  }

  @Override
  public CompletableFuture<ListPoliciesResult> get(final DataFetchingEnvironment environment)
      throws Exception {

    final QueryContext context = environment.getContext();

    if (PolicyAuthUtils.canManagePolicies(context)) {
      final ListPoliciesInput input =
          bindArgument(environment.getArgument("input"), ListPoliciesInput.class);
      final Integer start = input.getStart() == null ? DEFAULT_START : input.getStart();
      final Integer count = input.getCount() == null ? DEFAULT_COUNT : input.getCount();
      final String query = input.getQuery() == null ? DEFAULT_QUERY : input.getQuery();
      CriterionArray conjunctiveCriterion = new CriterionArray();
      if (input.getFilter() != null) {
        // Filter filter = new Filter();

        input
            .getFilter()
            .forEach(
                filterInput -> {
                  System.out.print("Field parameter " + filterInput.getField());
                  logger.info("Field parameter " + filterInput.getField());
                  if ("state".equals(filterInput.getField())) {
                    // Check if the value is "ACTIVE" or "INACTIVE" and add corresponding criteria
                    System.out.print("Value parameter " + filterInput.getValues().get(0));
                    logger.info("Akarsh Value parameter " + filterInput.getValues().get(0));
                    if ("ACTIVE".equals(filterInput.getValues().get(0))) {
                      conjunctiveCriterion.add(
                          new Criterion().setField("state").setValue("ACTIVE"));
                    } else if ("INACTIVE".equals(filterInput.getValues().get(0))) {
                      conjunctiveCriterion.add(
                          new Criterion().setField("state").setValue("INACTIVE"));
                    }
                  }
                  // Add more conditions if needed
                });
      }
      //        ConjunctiveCriterionArray conjunctiveCriterionArray = new
      // ConjunctiveCriterionArray();
      //        conjunctiveCriterion.forEach(conjunctiveCriterionArray::add);
      //        filter.setOr(conjunctiveCriterionArray);
      //        filter.setOr(conjunctiveCriterion);

      return _policyFetcher
          .fetchPolicies(
              start,
              query,
              count,
              context.getAuthentication(),
              new Filter()
                  .setOr(
                      new ConjunctiveCriterionArray(
                          new ConjunctiveCriterion().setAnd(conjunctiveCriterion))))
          .thenApply(
              policyFetchResult -> {
                final ListPoliciesResult result = new ListPoliciesResult();
                result.setStart(start);
                result.setCount(count);
                result.setTotal(policyFetchResult.getTotal());
                result.setPolicies(mapEntities(policyFetchResult.getPolicies()));
                return result;
              });
    }
    throw new AuthorizationException(
        "Unauthorized to perform this action. Please contact your DataHub administrator.");
  }

  private List<Policy> mapEntities(final List<PolicyFetcher.Policy> policies) {
    return policies.stream()
        .map(
            policy -> {
              Policy mappedPolicy = PolicyInfoPolicyMapper.map(policy.getPolicyInfo());
              mappedPolicy.setUrn(policy.getUrn().toString());
              return mappedPolicy;
            })
        .collect(Collectors.toList());
  }
}
