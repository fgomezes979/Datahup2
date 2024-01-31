package com.linkedin.datahub.upgrade.system.via;

import static com.linkedin.metadata.Constants.*;

import com.linkedin.common.urn.Urn;
import com.linkedin.datahub.upgrade.UpgradeContext;
import com.linkedin.datahub.upgrade.UpgradeStep;
import com.linkedin.datahub.upgrade.UpgradeStepResult;
import com.linkedin.datahub.upgrade.impl.DefaultUpgradeStepResult;
import com.linkedin.metadata.boot.BootstrapStep;
import com.linkedin.metadata.entity.EntityService;
import com.linkedin.metadata.entity.restoreindices.RestoreIndicesArgs;
import com.linkedin.metadata.entity.restoreindices.RestoreIndicesResult;
import java.net.URISyntaxException;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReindexDataJobViaNodesCLLStep implements UpgradeStep {

  private static final String UPGRADE_ID = "via-node-cll-reindex-datajob";
  private static final Urn UPGRADE_ID_URN = BootstrapStep.getUpgradeUrn(UPGRADE_ID);

  private static final Integer BATCH_SIZE = 5000;

  private final EntityService _entityService;

  public ReindexDataJobViaNodesCLLStep(EntityService entityService) {
    _entityService = entityService;
  }

  @Override
  public Function<UpgradeContext, UpgradeStepResult> executable() {
    return (context) -> {
      RestoreIndicesArgs args =
          new RestoreIndicesArgs()
              .setAspectName(DATA_JOB_INPUT_OUTPUT_ASPECT_NAME)
              .setUrnLike("urn:li:" + DATA_JOB_ENTITY_NAME + ":%");
      RestoreIndicesResult result =
          _entityService.restoreIndices(args, x -> context.report().addLine((String) x));
      context.report().addLine("Rows migrated: " + result.rowsMigrated);
      context.report().addLine("Rows ignored: " + result.ignored);
      try {
        BootstrapStep.setUpgradeResult(UPGRADE_ID_URN, _entityService);
        context.report().addLine("State updated: " + UPGRADE_ID_URN);
      } catch (URISyntaxException e) {
        throw new RuntimeException(e);
      }
      return new DefaultUpgradeStepResult(id(), UpgradeStepResult.Result.SUCCEEDED);
    };
  }

  @Override
  public String id() {
    return UPGRADE_ID;
  }

  /**
   * Returns whether the upgrade should proceed if the step fails after exceeding the maximum
   * retries.
   */
  @Override
  public boolean isOptional() {
    return false;
  }

  @Override
  /**
   * Returns whether the upgrade should be skipped. Uses previous run history or the environment
   * variable SKIP_REINDEX_DATA_JOB_INPUT_OUTPUT to determine whether to skip.
   */
  public boolean skip(UpgradeContext context) {
    boolean previouslyRun = _entityService.exists(UPGRADE_ID_URN, true);
    boolean envFlagRecommendsSkip =
        Boolean.parseBoolean(System.getenv("SKIP_REINDEX_DATA_JOB_INPUT_OUTPUT"));
    if (previouslyRun) {
      log.info("{} was already run. Skipping.", id());
    }
    if (envFlagRecommendsSkip) {
      log.info("Environment variable SKIP_REINDEX_DATA_JOB_INPUT_OUTPUT is set to true. Skipping.");
    }
    return (previouslyRun || envFlagRecommendsSkip);
  }
}
