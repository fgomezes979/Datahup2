package com.linkedin.datahub.upgrade.restoreaspect;

import com.google.common.collect.ImmutableList;
import com.linkedin.datahub.upgrade.Upgrade;
import com.linkedin.datahub.upgrade.UpgradeCleanupStep;
import com.linkedin.datahub.upgrade.UpgradeStep;
import com.linkedin.datahub.upgrade.common.steps.ClearGraphServiceStep;
import com.linkedin.datahub.upgrade.common.steps.ClearSearchServiceStep;
import com.linkedin.datahub.upgrade.common.steps.GMSDisableWriteModeStep;
import com.linkedin.datahub.upgrade.common.steps.GMSEnableWriteModeStep;
import com.linkedin.datahub.upgrade.common.steps.GMSQualificationStep;
import com.linkedin.entity.client.RestliEntityClient;
import com.linkedin.metadata.entity.EntityService;
import com.linkedin.metadata.graph.GraphService;
import com.linkedin.metadata.models.registry.EntityRegistry;
import com.linkedin.metadata.search.EntitySearchService;
import io.ebean.EbeanServer;
import java.util.ArrayList;
import java.util.List;


public class RestoreAspect implements Upgrade {

  private final List<UpgradeStep> _steps;

  public RestoreAspect(final EbeanServer server, final EntityService entityService, final EntityRegistry entityRegistry,
      final RestliEntityClient entityClient, final GraphService graphClient, final EntitySearchService searchClient) {
    _steps = buildSteps(server, entityService, entityRegistry, entityClient, graphClient, searchClient);
  }

  @Override
  public String id() {
    return "RestoreAspect";
  }

  @Override
  public List<UpgradeStep> steps() {
    return _steps;
  }

  private List<UpgradeStep> buildSteps(final EbeanServer server, final EntityService entityService,
      final EntityRegistry entityRegistry, final RestliEntityClient entityClient, final GraphService graphClient,
      final EntitySearchService searchClient) {
    final List<UpgradeStep> steps = new ArrayList<>();
    steps.add(new GMSQualificationStep());
    steps.add(new RestoreAspectStep(entityService, entityRegistry));
    return steps;
  }

  @Override
  public List<UpgradeCleanupStep> cleanupSteps() {
    return ImmutableList.of();
  }
}

