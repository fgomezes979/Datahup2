package com.linkedin.metadata.aspect.plugins.hooks;

import com.linkedin.metadata.aspect.RetrieverContext;
import com.linkedin.metadata.aspect.batch.ChangeMCP;
import com.linkedin.metadata.aspect.batch.MCLItem;
import com.linkedin.metadata.aspect.batch.MCPItem;
import com.linkedin.metadata.aspect.plugins.PluginSpec;
import java.util.Collection;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

/** Given an MCP produce additional MCPs to write */
public abstract class MCPSideEffect extends PluginSpec
    implements BiFunction<Collection<ChangeMCP>, RetrieverContext, Stream<ChangeMCP>> {

  /**
   * Given the list of MCP upserts, output additional upserts
   *
   * @param changeMCPS list
   * @return additional upserts
   */
  public final Stream<ChangeMCP> apply(
      Collection<ChangeMCP> changeMCPS, @Nonnull RetrieverContext retrieverContext) {
    return applyMCPSideEffect(
        changeMCPS.stream()
            .filter(item -> shouldApply(item.getChangeType(), item.getUrn(), item.getAspectName()))
            .collect(Collectors.toList()),
        retrieverContext);
  }

  /**
   * Apply MCP Side Effects after commit.
   *
   * @param mclItems MCL items generated by MCP commit.
   * @param retrieverContext accessors for aspect and graph data
   * @return additional MCPs
   */
  public final Stream<MCPItem> postApply(
      Collection<MCLItem> mclItems, @Nonnull RetrieverContext retrieverContext) {
    return postMCPSideEffect(
        mclItems.stream()
            .filter(item -> shouldApply(item.getChangeType(), item.getUrn(), item.getAspectName()))
            .collect(Collectors.toList()),
        retrieverContext);
  }

  /**
   * Generate additional MCPs during the transaction of the given MCPs
   *
   * @param changeMCPS MCPs being committed
   * @param retrieverContext accessors for aspect and graph data
   * @return additional MCPs
   */
  protected abstract Stream<ChangeMCP> applyMCPSideEffect(
      Collection<ChangeMCP> changeMCPS, @Nonnull RetrieverContext retrieverContext);

  /**
   * Generate additional MCPs after the transaction of an MCP. This task will not block the
   * production of the MCL for downstream processing.
   *
   * @param mclItems MCL items generated from committing the MCP
   * @param retrieverContext accessors for aspect and graph data
   * @return additional MCPs
   */
  protected abstract Stream<MCPItem> postMCPSideEffect(
      Collection<MCLItem> mclItems, @Nonnull RetrieverContext retrieverContext);
}
