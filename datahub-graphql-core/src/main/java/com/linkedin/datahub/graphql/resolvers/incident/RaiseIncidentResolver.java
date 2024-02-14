package com.linkedin.datahub.graphql.resolvers.incident;

import static com.linkedin.datahub.graphql.resolvers.AuthUtils.*;
import static com.linkedin.datahub.graphql.resolvers.ResolverUtils.*;
import static com.linkedin.datahub.graphql.resolvers.mutate.MutationUtils.*;
import static com.linkedin.metadata.Constants.*;

import com.google.common.collect.ImmutableList;
import com.linkedin.common.AuditStamp;
import com.linkedin.common.UrnArray;
import com.linkedin.common.urn.Urn;
import com.linkedin.data.template.SetMode;
import com.linkedin.datahub.graphql.QueryContext;
import com.linkedin.datahub.graphql.exception.AuthorizationException;
import com.linkedin.datahub.graphql.generated.RaiseIncidentInput;
import com.linkedin.entity.client.EntityClient;
import com.linkedin.incident.IncidentInfo;
import com.linkedin.incident.IncidentSource;
import com.linkedin.incident.IncidentSourceType;
import com.linkedin.incident.IncidentState;
import com.linkedin.incident.IncidentStatus;
import com.linkedin.incident.IncidentType;
import com.linkedin.metadata.key.IncidentKey;
import com.linkedin.mxe.MetadataChangeProposal;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import java.net.URISyntaxException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Resolver used for creating (raising) a new asset incident. */
@Slf4j
@RequiredArgsConstructor
public class RaiseIncidentResolver implements DataFetcher<CompletableFuture<String>> {

  private final EntityClient _entityClient;

  @Override
  public CompletableFuture<String> get(DataFetchingEnvironment environment) throws Exception {

    final QueryContext context = environment.getContext();
    final RaiseIncidentInput input =
        bindArgument(environment.getArgument("input"), RaiseIncidentInput.class);
    final Urn resourceUrn = Urn.createFromString(input.getResourceUrn());

    return CompletableFuture.supplyAsync(
        () -> {
          if (!IncidentUtils.isAuthorizedToEditIncidentForResource(resourceUrn, context)) {
            throw new AuthorizationException(
                "Unauthorized to perform this action. Please contact your DataHub administrator.");
          }

          try {
            // Create the Domain Key
            final IncidentKey key = new IncidentKey();

            // Generate a random UUID for the incident
            final String id = UUID.randomUUID().toString();
            key.setId(id);

            // Create the MCP
            final MetadataChangeProposal proposal =
                buildMetadataChangeProposalWithKey(
                    key,
                    INCIDENT_ENTITY_NAME,
                    INCIDENT_INFO_ASPECT_NAME,
                    mapIncidentInfo(input, context));
            return _entityClient.ingestProposal(proposal, context.getAuthentication(), false);
          } catch (Exception e) {
            log.error("Failed to create incident. {}", e.getMessage());
            throw new RuntimeException("Failed to incident", e);
          }
        });
  }

  private IncidentInfo mapIncidentInfo(final RaiseIncidentInput input, final QueryContext context)
      throws URISyntaxException {
    final IncidentInfo result = new IncidentInfo();
    result.setType(
        IncidentType.valueOf(
            input
                .getType()
                .name())); // Assumption Alert: This assumes that GMS incident type === GraphQL
    // incident type.
    result.setCustomType(input.getCustomType(), SetMode.IGNORE_NULL);
    result.setTitle(input.getTitle(), SetMode.IGNORE_NULL);
    result.setDescription(input.getDescription(), SetMode.IGNORE_NULL);
    result.setEntities(
        new UrnArray(ImmutableList.of(Urn.createFromString(input.getResourceUrn()))));
    result.setCreated(
        new AuditStamp()
            .setActor(Urn.createFromString(context.getActorUrn()))
            .setTime(System.currentTimeMillis()));
    // Create the incident in the 'active' state by default.
    result.setStatus(
        new IncidentStatus()
            .setState(IncidentState.ACTIVE)
            .setLastUpdated(
                new AuditStamp()
                    .setActor(Urn.createFromString(context.getActorUrn()))
                    .setTime(System.currentTimeMillis())));
    result.setSource(new IncidentSource().setType(IncidentSourceType.MANUAL), SetMode.IGNORE_NULL);
    result.setPriority(input.getPriority(), SetMode.IGNORE_NULL);
    return result;
  }
}
