package com.linkedin.datahub.graphql.resolvers.incident;

import static com.linkedin.datahub.graphql.resolvers.AuthUtils.ALL_PRIVILEGES_GROUP;

import com.datahub.authorization.ConjunctivePrivilegeGroup;
import com.datahub.authorization.DisjunctivePrivilegeGroup;
import com.google.common.collect.ImmutableList;
import com.linkedin.common.urn.Urn;
import com.linkedin.datahub.graphql.QueryContext;
import com.linkedin.datahub.graphql.authorization.AuthorizationUtils;
import com.linkedin.metadata.authorization.PoliciesConfig;

public class IncidentUtils {
  public static boolean isAuthorizedToEditIncidentForResource(
      final Urn resourceUrn, final QueryContext context) {
    final DisjunctivePrivilegeGroup orPrivilegeGroups =
        new DisjunctivePrivilegeGroup(
            ImmutableList.of(
                ALL_PRIVILEGES_GROUP,
                new ConjunctivePrivilegeGroup(
                    ImmutableList.of(PoliciesConfig.EDIT_ENTITY_INCIDENTS_PRIVILEGE.getType()))));

    return AuthorizationUtils.isAuthorized(
        context.getAuthorizer(),
        context.getActorUrn(),
        resourceUrn.getEntityType(),
        resourceUrn.toString(),
        orPrivilegeGroups);
  }

  private IncidentUtils() {}
}
