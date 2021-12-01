package com.linkedin.datahub.graphql.resolvers.constraint;

import com.linkedin.common.urn.Urn;
import com.linkedin.constraint.ConstraintInfo;
import com.linkedin.constraint.ConstraintParams;
import com.linkedin.constraint.GlossaryTermInNodeConstraint;
import com.linkedin.datahub.graphql.QueryContext;
import com.linkedin.datahub.graphql.exception.AuthorizationException;
import com.linkedin.datahub.graphql.generated.ConstraintType;
import com.linkedin.datahub.graphql.generated.CreateTermConstraintInput;
import com.linkedin.entity.client.AspectClient;
import com.linkedin.entity.client.EntityClient;
import com.linkedin.events.metadata.ChangeType;
import com.linkedin.metadata.Constants;
import com.linkedin.metadata.key.ConstraintKey;
import com.linkedin.metadata.utils.GenericAspectUtils;
import com.linkedin.mxe.MetadataChangeProposal;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.linkedin.datahub.graphql.resolvers.ResolverUtils.*;


public class CreateTermConstraintResolver implements DataFetcher<CompletableFuture<String>> {

  private final EntityClient _aspectClient;

  public CreateTermConstraintResolver(final EntityClient aspectClient) {
    _aspectClient = aspectClient;
  }

  @Override
  public CompletableFuture<String> get(final DataFetchingEnvironment environment) throws Exception {

    final QueryContext context = environment.getContext();

    if (ConstraintUtils.isAuthorizedToCreateConstraints(context)) {
      final CreateTermConstraintInput input = bindArgument(environment.getArgument("input"), CreateTermConstraintInput.class);

      return CompletableFuture.supplyAsync(() -> {
        try {
          // Create the Constraint key.
          final ConstraintKey key = new ConstraintKey();
          key.setId(UUID.randomUUID().toString());

          // Create the constraint info.
          final ConstraintInfo info = new ConstraintInfo();
          info.setDisplayName(input.getName());
          info.setDescription(input.getDescription());
          info.setType(ConstraintType.HAS_GLOSSARY_TERM_IN_NODE.toString());

          ConstraintParams params = new ConstraintParams();
          GlossaryTermInNodeConstraint glossaryNodeConstraint = new GlossaryTermInNodeConstraint();
          glossaryNodeConstraint.setGlossaryNode(Urn.createFromString(input.getNodeUrn()));
          params.setHasGlossaryTermInNodeParams(glossaryNodeConstraint);

          info.setParams(params);

          // Finally, create the MetadataChangeProposal.
          final MetadataChangeProposal proposal = new MetadataChangeProposal();
          proposal.setEntityKeyAspect(GenericAspectUtils.serializeAspect(key));
          proposal.setEntityType(Constants.CONSTRAINT_ENTITY_NAME);
          proposal.setAspectName(Constants.CONSTRAINT_INFO_ASPECT_NAME);
          proposal.setAspect(GenericAspectUtils.serializeAspect(info));
          proposal.setChangeType(ChangeType.UPSERT);
          return _aspectClient.ingestProposal(proposal, context.getActor()).getEntity();
        } catch (Exception e) {
          throw new RuntimeException("Failed to create constraint", e);
        }
      });
    }
    throw new AuthorizationException("Unauthorized to perform this action. Please contact your DataHub administrator.");
  }
}
