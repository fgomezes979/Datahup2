package datahub.client.patch.dataFlow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linkedin.common.TimeStamp;
import datahub.client.patch.AbstractMultiFieldPatchBuilder;
import datahub.client.patch.subtypesSupport.CustomPropertiesPatchBuilderSupport;
import datahub.client.patch.common.CustomPropertiesPatchBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.Getter;
import org.apache.commons.lang3.tuple.ImmutableTriple;

import static com.fasterxml.jackson.databind.node.JsonNodeFactory.*;
import static com.linkedin.metadata.Constants.*;


public class DataFlowInfoPatchBuilder extends AbstractMultiFieldPatchBuilder<DataFlowInfoPatchBuilder>
    implements CustomPropertiesPatchBuilderSupport<DataFlowInfoPatchBuilder> {

  public static final String BASE_PATH = "/";

  public static final String NAME_KEY = "name";
  public static final String DESCRIPTION_KEY = "description";
  public static final String PROJECT_KEY = "project";
  public static final String CREATED_KEY = "created";
  public static final String LAST_MODIFIED_KEY = "lastModified";
  public static final String TIME_KEY = "time";
  public static final String ACTOR_KEY = "actor";
  public static final String CUSTOM_PROPERTIES_KEY = "customProperties";

  private String name = null;
  private String description = null;
  private String project = null;
  private TimeStamp created = null;
  private TimeStamp lastModified = null;
  @Getter
  private CustomPropertiesPatchBuilder<DataFlowInfoPatchBuilder> customPropertiesPatchBuilder;

  public DataFlowInfoPatchBuilder name(String name) {
    this.name = name;
    return this;
  }

  public DataFlowInfoPatchBuilder description(String description) {
    this.description = description;
    return this;
  }

  public DataFlowInfoPatchBuilder project(String project) {
    this.project = project;
    return this;
  }

  public DataFlowInfoPatchBuilder created(TimeStamp created) {
    this.created = created;
    return this;
  }

  public DataFlowInfoPatchBuilder lastModified(TimeStamp lastModified) {
    this.lastModified = lastModified;
    return this;
  }

  @Override
  protected Stream<Object> getRequiredProperties() {
    return Stream.of(this.targetEntityUrn, this.op);
  }

  @Override
  protected List<ImmutableTriple<String, String, JsonNode>> getPathValues() {
    List<ImmutableTriple<String, String, JsonNode>> triples = new ArrayList<>();

    if (name != null) {
      triples.add(ImmutableTriple.of(this.op, BASE_PATH + NAME_KEY, instance.textNode(name)));
    }
    if (description != null) {
      triples.add(ImmutableTriple.of(this.op, BASE_PATH + DESCRIPTION_KEY, instance.textNode(description)));
    }
    if (project != null) {
      triples.add(ImmutableTriple.of(this.op, BASE_PATH + PROJECT_KEY, instance.textNode(project)));
    }
    if (created != null) {
      ObjectNode createdNode = instance.objectNode();
      createdNode.put(TIME_KEY, created.getTime());
      if (created.getActor() != null) {
        createdNode.put(ACTOR_KEY, created.getActor().toString());
      }
      triples.add(ImmutableTriple.of(this.op, BASE_PATH + CREATED_KEY, createdNode));
    }
    if (lastModified != null) {
      ObjectNode lastModifiedNode = instance.objectNode();
      lastModifiedNode.put(TIME_KEY, lastModified.getTime());
      if (lastModified.getActor() != null) {
        lastModifiedNode.put(ACTOR_KEY, lastModified.getActor().toString());
      }
      triples.add(ImmutableTriple.of(this.op, BASE_PATH + LAST_MODIFIED_KEY, lastModifiedNode));
    }
    if (customPropertiesPatchBuilder != null) {
      triples.addAll(customPropertiesPatchBuilder.getSubPaths());
    }

    return triples;
  }

  @Override
  protected String getAspectName() {
    return DATA_FLOW_INFO_ASPECT_NAME;
  }

  @Override
  protected String getEntityType() {
    return DATA_FLOW_ENTITY_NAME;
  }

  @Override
  public CustomPropertiesPatchBuilder<DataFlowInfoPatchBuilder> customPropertiesPatchBuilder() {
    customPropertiesPatchBuilder = new CustomPropertiesPatchBuilder<>(this);
    return customPropertiesPatchBuilder;
  }
}
