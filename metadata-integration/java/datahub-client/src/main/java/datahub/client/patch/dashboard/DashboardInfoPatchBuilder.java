package datahub.client.patch.dashboard;

import static com.linkedin.metadata.Constants.*;
import static datahub.client.patch.common.PatchUtil.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linkedin.common.Edge;
import com.linkedin.common.urn.ChartUrn;
import com.linkedin.common.urn.DatasetUrn;
import com.linkedin.common.urn.Urn;
import datahub.client.patch.AbstractMultiFieldPatchBuilder;
import datahub.client.patch.PatchOperationType;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.tuple.ImmutableTriple;

public class DashboardInfoPatchBuilder
    extends AbstractMultiFieldPatchBuilder<DashboardInfoPatchBuilder> {
  private static final String CHART_EDGES_PATH_START = "/chartEdges/";
  private static final String DATASET_EDGES_PATH_START = "/datasetEdges/";

  // Simplified with just Urn
  public DashboardInfoPatchBuilder addChartEdge(@Nonnull ChartUrn urn) {
    ObjectNode value = createEdgeValue(urn);

    pathValues.add(
        ImmutableTriple.of(PatchOperationType.ADD.getValue(), CHART_EDGES_PATH_START + urn, value));
    return this;
  }

  public DashboardInfoPatchBuilder removeChartEdge(@Nonnull ChartUrn urn) {
    pathValues.add(
        ImmutableTriple.of(
            PatchOperationType.REMOVE.getValue(), CHART_EDGES_PATH_START + urn, null));
    return this;
  }

  public DashboardInfoPatchBuilder addDatasetEdge(@Nonnull DatasetUrn urn) {
    ObjectNode value = createEdgeValue(urn);

    pathValues.add(
        ImmutableTriple.of(
            PatchOperationType.ADD.getValue(), DATASET_EDGES_PATH_START + urn, value));
    return this;
  }

  public DashboardInfoPatchBuilder removeDatasetEdge(@Nonnull DatasetUrn urn) {
    pathValues.add(
        ImmutableTriple.of(
            PatchOperationType.REMOVE.getValue(), DATASET_EDGES_PATH_START + urn, null));
    return this;
  }

  // Full Edge modification
  public DashboardInfoPatchBuilder addEdge(@Nonnull Edge edge) {
    ObjectNode value = createEdgeValue(edge);
    String path = getEdgePath(edge);

    pathValues.add(ImmutableTriple.of(PatchOperationType.ADD.getValue(), path, value));
    return this;
  }

  public DashboardInfoPatchBuilder removeEdge(@Nonnull Edge edge) {
    String path = getEdgePath(edge);

    pathValues.add(ImmutableTriple.of(PatchOperationType.REMOVE.getValue(), path, null));
    return this;
  }

  /**
   * Determines Edge path based on supplied Urn, if not a valid entity type throws
   * IllegalArgumentException
   *
   * @param edge
   * @return
   * @throws IllegalArgumentException if destinationUrn is an invalid entity type
   */
  private String getEdgePath(@Nonnull Edge edge) {
    Urn destinationUrn = edge.getDestinationUrn();

    if (DATASET_ENTITY_NAME.equals(destinationUrn.getEntityType())) {
      return DATASET_EDGES_PATH_START + destinationUrn;
    }

    if (CHART_ENTITY_NAME.equals(destinationUrn.getEntityType())) {
      return CHART_EDGES_PATH_START + destinationUrn;
    }

    // TODO: Output Data Jobs not supported by aspect, add here if this changes

    throw new IllegalArgumentException(
        String.format("Unsupported entity type: %s", destinationUrn.getEntityType()));
  }

  @Override
  protected String getAspectName() {
    return DASHBOARD_INFO_ASPECT_NAME;
  }

  @Override
  protected String getEntityType() {
    return DASHBOARD_ENTITY_NAME;
  }
}
