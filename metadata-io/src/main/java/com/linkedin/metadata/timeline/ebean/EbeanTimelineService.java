package com.linkedin.metadata.timeline.ebean;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.diff.JsonDiff;
import com.linkedin.common.urn.Urn;
import com.linkedin.metadata.entity.ebean.EbeanAspectDao;
import com.linkedin.metadata.entity.ebean.EbeanAspectV2;
import com.linkedin.metadata.timeline.TimelineService;
import com.linkedin.metadata.timeline.data.ChangeCategory;
import com.linkedin.metadata.timeline.data.ChangeEvent;
import com.linkedin.metadata.timeline.data.ChangeTransaction;
import com.linkedin.metadata.timeline.data.SemanticChangeType;
import com.linkedin.metadata.timeline.ebean.differ.BasicDiffer;
import com.linkedin.metadata.timeline.ebean.differ.DiffFactory;
import com.linkedin.metadata.timeline.ebean.differ.Differ;
import com.linkedin.metadata.timeline.ebean.differ.EditableDatasetPropertiesDiffer;
import com.linkedin.metadata.timeline.ebean.differ.GlobalTagsDiffer;
import com.linkedin.metadata.timeline.ebean.differ.InstitutionalMemoryDiffer;
import com.linkedin.metadata.timeline.ebean.differ.OwnershipDiffer;
import com.linkedin.metadata.timeline.ebean.differ.SchemaDiffer;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.apache.parquet.SemanticVersion;

import static com.linkedin.metadata.Constants.*;


public class EbeanTimelineService implements TimelineService {

  //private static final int DEFAULT_MAX_TRANSACTION_RETRY = 3;
  private static final long DEFAULT_LOOKBACK_TIME_WINDOW_MILLIS = 7 * 24 * 60 * 60 * 1000L; // 1 week lookback
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final long FIRST_TRANSACTION_ID = 0;
  private static final String BUILD_VALUE_COMPUTED = "-computed";

  private final EbeanAspectDao _entityDao;
  //private final JacksonDataTemplateCodec _dataTemplateCodec = new JacksonDataTemplateCodec();
  private final DiffFactory _diffFactory;
  private final HashMap<String, HashMap<ChangeCategory, Set<String>>> entityTypeElementAspectRegistry = new HashMap<>();

  public EbeanTimelineService(@Nonnull EbeanAspectDao entityDao) {
    this._entityDao = entityDao;

    // TODO: Load up from yaml file
    // Dataset registry
    HashMap<ChangeCategory, Set<String>> datasetElementAspectRegistry = new HashMap<>();
    _diffFactory = new DiffFactory();
    String entityType = DATASET_ENTITY_NAME;
    for (ChangeCategory elementName : ChangeCategory.values()) {
      Set<String> aspects = new HashSet<>();
      switch (elementName) {
        case TAG: {
          aspects.add(SCHEMA_METADATA_ASPECT_NAME);
          _diffFactory.addDiffer(entityType, elementName, SCHEMA_METADATA_ASPECT_NAME, new SchemaDiffer());
          aspects.add(EDITABLE_SCHEMA_METADATA_ASPECT_NAME);
          _diffFactory.addDiffer(entityType, elementName, EDITABLE_SCHEMA_METADATA_ASPECT_NAME, new BasicDiffer());
          aspects.add(GLOBAL_TAGS_ASPECT_NAME);
          _diffFactory.addDiffer(entityType, elementName, GLOBAL_TAGS_ASPECT_NAME, new GlobalTagsDiffer());
        }
        break;
        case OWNERSHIP: {
          aspects.add(OWNERSHIP_ASPECT_NAME);
          _diffFactory.addDiffer(entityType, elementName, OWNERSHIP_ASPECT_NAME, new OwnershipDiffer());
        }
        break;
        case DOCUMENTATION: {
          aspects.add(INSTITUTIONAL_MEMORY_ASPECT_NAME);
          _diffFactory.addDiffer(entityType, elementName, INSTITUTIONAL_MEMORY_ASPECT_NAME,
              new InstitutionalMemoryDiffer());
          aspects.add(EDITABLE_DATASET_PROPERTIES_ASPECT_NAME);
          _diffFactory.addDiffer(entityType, elementName, EDITABLE_DATASET_PROPERTIES_ASPECT_NAME, new EditableDatasetPropertiesDiffer());
          aspects.add(DATASET_PROPERTIES_ASPECT_NAME);
          _diffFactory.addDiffer(entityType, elementName, DATASET_PROPERTIES_ASPECT_NAME, new BasicDiffer());
          aspects.add(SCHEMA_METADATA_ASPECT_NAME);
          _diffFactory.addDiffer(entityType, elementName, SCHEMA_METADATA_ASPECT_NAME, new SchemaDiffer());
        }
        break;
        case GLOSSARY_TERM: {
          aspects.add(GLOSSARY_TERMS_ASPECT_NAME);
          _diffFactory.addDiffer(entityType, elementName, GLOSSARY_TERMS_ASPECT_NAME, new BasicDiffer());
        }
        break;
        case TECHNICAL_SCHEMA: {
          aspects.add(SCHEMA_METADATA_ASPECT_NAME);
          _diffFactory.addDiffer(entityType, elementName, SCHEMA_METADATA_ASPECT_NAME, new SchemaDiffer());
        }
        break;
        default:
          break;
      }
      datasetElementAspectRegistry.put(elementName, aspects);
    }
    entityTypeElementAspectRegistry.put(DATASET_ENTITY_NAME, datasetElementAspectRegistry);
  }

  Set<String> getAspectsFromElements(String entityType, Set<ChangeCategory> elementNames) {
    if (this.entityTypeElementAspectRegistry.containsKey(entityType)) {
      return elementNames.stream()
          .map(x -> entityTypeElementAspectRegistry.get(entityType).get(x))
          .flatMap(Collection::stream)
          .collect(Collectors.toSet());
    } else {
      throw new UnsupportedOperationException("Entity Type " + entityType + " not supported");
    }
  }

  @Nonnull
  @Override
  public List<ChangeTransaction> getTimeline(@Nonnull final Urn urn, @Nonnull final Set<ChangeCategory> elementNames,
      long startTimeMillis, long endTimeMillis, String startVersionStamp, String endVersionStamp,
      boolean rawDiffRequested) throws JsonProcessingException {

    Set<String> aspectNames = getAspectsFromElements(urn.getEntityType(), elementNames);

    // TODO: Add more logic for defaults
    if (startVersionStamp != null && startTimeMillis != 0) {
      throw new IllegalArgumentException("Cannot specify both VersionStamp start and timestamp start");
    }

    if (endTimeMillis == 0) {
      endTimeMillis = System.currentTimeMillis();
    }
    if (startTimeMillis == -1) {
      startTimeMillis = endTimeMillis - DEFAULT_LOOKBACK_TIME_WINDOW_MILLIS;
    }

    List<EbeanAspectV2> foo = this._entityDao.getAspectsInRange(urn, aspectNames, startTimeMillis, endTimeMillis);
    Map<String, TreeSet<EbeanAspectV2>> aspectRowSetMap = new HashMap<>();
    foo.forEach(row -> {
      TreeSet<EbeanAspectV2> rowList = aspectRowSetMap.computeIfAbsent(row.getAspect(),
          k -> new TreeSet<>(Comparator.comparing(EbeanAspectV2::getCreatedOn)));
      rowList.add(row);
      /*
       Long minVersion = aspectMinVersionMap.get(row.getAspect());
       if (minVersion == null) {
       aspectMinVersionMap.put(row.getAspect(), row.getVersion());
       } else {
       if (minVersion < row.getVersion() && minVersion != 0) {
       aspectMinVersionMap.put(row.getAspect(), row.getVersion());
       }
       }
       */
    });

    // we need to pull previous versions of these aspects that are currently at a 0
    Map<String, Long> nextVersions = _entityDao.getNextVersions(urn.toString(), aspectNames);

    for (Map.Entry<String, TreeSet<EbeanAspectV2>> aspectMinVersion : aspectRowSetMap.entrySet()) {
      EbeanAspectV2 oldestAspect = aspectMinVersion.getValue().first();
      Long nextVersion = nextVersions.get(aspectMinVersion.getKey());
      if (((oldestAspect.getVersion() == 0L) && (nextVersion == 1L)) || (oldestAspect.getVersion() == 1L)) {
        MissingEbeanAspectV2 missingEbeanAspectV2 = new MissingEbeanAspectV2();
        missingEbeanAspectV2.setAspect(aspectMinVersion.getKey());
        missingEbeanAspectV2.setCreatedOn(new Timestamp(0L));
        missingEbeanAspectV2.setVersion(-1);
        aspectMinVersion.getValue().add(missingEbeanAspectV2);
      } else {
        // get the next version
        long versionToGet = (oldestAspect.getVersion() == 0L) ? nextVersion - 1 : oldestAspect.getVersion() - 1;
        EbeanAspectV2 row = _entityDao.getAspect(urn.toString(), aspectMinVersion.getKey(), versionToGet);
        aspectRowSetMap.get(row.getAspect()).add(row);
      }
    }

    SortedMap<Long, List<ChangeTransaction>> semanticDiffs = new TreeMap<>();
    for (Map.Entry<String, TreeSet<EbeanAspectV2>> aspectRowSetEntry : aspectRowSetMap.entrySet()) {
      semanticDiffs.putAll(
          computeDiffs(aspectRowSetEntry.getValue(), urn.getEntityType(), elementNames, rawDiffRequested));
    }
    assignSemanticVersions(semanticDiffs);
    List<ChangeTransaction> changeTransactions = new ArrayList<>();
    for (Map.Entry<Long, List<ChangeTransaction>> entry : semanticDiffs.entrySet()) {
      changeTransactions.addAll(entry.getValue());
    }
    changeTransactions.sort(Comparator.comparing(ChangeTransaction::getTimestamp));
    return changeTransactions;
    /*
     return foo.stream().map(row -> Model.SemanticChangeEvent.builder()
     .changeType("UPSERT")
     .target(urn.toString())
     .actor(null)
     .actorId(null)
     .description("This is a change at version " + row.getVersion())
     .element(row.getAspect())
     .elementId(null)
     .proxy(null)
     .rawDiff(row.getMetadata())
     .build()).collect(Collectors.toList());
     */
  }

  private SemanticVersion getGroupSemanticVersion(SemanticChangeType highestChangeInGroup,
      SemanticVersion previousVersion) {
    if (previousVersion == null) {
      // Start with all 0s if there is no previous version.
      return new SemanticVersion(0, 0, 0, null, null, BUILD_VALUE_COMPUTED);
    }
    // Evaluate the version for this group based on previous version and the hightest semantic change type in the group.
    if (highestChangeInGroup == SemanticChangeType.MAJOR) {
      // Bump up major, reset all lower to 0s.
      return new SemanticVersion(previousVersion.major + 1, 0, 0, null, null, BUILD_VALUE_COMPUTED);
    } else if (highestChangeInGroup == SemanticChangeType.MINOR) {
      // Bump up minor, reset all lower to 0s.
      return new SemanticVersion(previousVersion.major, previousVersion.minor + 1, 0, null, null, BUILD_VALUE_COMPUTED);
    } else if (highestChangeInGroup == SemanticChangeType.PATCH) {
      // Bump up patch.
      return new SemanticVersion(previousVersion.major, previousVersion.minor, previousVersion.patch + 1, null, null,
          BUILD_VALUE_COMPUTED);
    }
    return previousVersion;
  }

  private void assignSemanticVersions(SortedMap<Long, List<ChangeTransaction>> changeTransactionsMap) {
    SemanticVersion curGroupVersion = null;
    long transactionId = FIRST_TRANSACTION_ID - 1;
    for (Map.Entry<Long, List<ChangeTransaction>> entry : changeTransactionsMap.entrySet()) {
      assert (transactionId < entry.getKey());
      transactionId = entry.getKey();
      SemanticChangeType highestChangeInGroup = SemanticChangeType.NONE;
      ChangeTransaction highestChangeTransaction =
          entry.getValue().stream().max(Comparator.comparing(ChangeTransaction::getSemVerChange)).orElse(null);
      if (highestChangeTransaction != null) {
        highestChangeInGroup = highestChangeTransaction.getSemVerChange();
      }
      curGroupVersion = getGroupSemanticVersion(highestChangeInGroup, curGroupVersion);
      for (ChangeTransaction t : entry.getValue()) {
        t.setSemanticVersion(curGroupVersion.toString());
      }
    }
  }

  private SortedMap<Long, List<ChangeTransaction>> computeDiffs(TreeSet<EbeanAspectV2> aspectTimeline,
      String entityType, Set<ChangeCategory> elementNames, boolean rawDiffsRequested) throws JsonProcessingException {
    EbeanAspectV2 previousValue = null;
    SortedMap<Long, List<ChangeTransaction>> changeTransactionsMap = new TreeMap<>();
    long transactionId = FIRST_TRANSACTION_ID;
    for (EbeanAspectV2 currentValue : aspectTimeline) {
      if (previousValue != null) {
        // we skip the first element and only compare once we have two in hand
        changeTransactionsMap.put(transactionId,
            computeDiff(previousValue, currentValue, entityType, elementNames, rawDiffsRequested));
        ++transactionId;
      }
      previousValue = currentValue;
    }
    return changeTransactionsMap;
  }

  private List<ChangeTransaction> computeDiff(@Nonnull EbeanAspectV2 previousValue, @Nonnull EbeanAspectV2 currentValue,
      String entityType, Set<ChangeCategory> elementNames, boolean rawDiffsRequested) throws JsonProcessingException {
    String aspectName = currentValue.getAspect();

    List<ChangeTransaction> semanticChangeTransactions = new ArrayList<>();
    JsonPatch rawDiff = getRawDiff(previousValue, currentValue);
    for (ChangeCategory element : elementNames) {
      Differ differ = _diffFactory.getDiffer(entityType, element, aspectName);
      if (differ != null) {
        try {
          semanticChangeTransactions.add(
              differ.getSemanticDiff(previousValue, currentValue, element, rawDiff, rawDiffsRequested));
        } catch (Exception e) {
          semanticChangeTransactions.add(ChangeTransaction.builder()
              .semVerChange(SemanticChangeType.EXCEPTIONAL)
              .changeEvents(Collections.singletonList(ChangeEvent.builder()
                  .description(String.format("%s:%s", e.getClass().getName(), e.getMessage()))
                  .build()))
              .build());
        }
      }
    }
    return semanticChangeTransactions;
  }

  private JsonPatch getRawDiff(EbeanAspectV2 previousValue, EbeanAspectV2 currentValue) throws JsonProcessingException {
    JsonNode prevNode = OBJECT_MAPPER.nullNode();
    if (previousValue.getVersion() != -1) {
      prevNode = OBJECT_MAPPER.readTree(previousValue.getMetadata());
    }
    JsonNode currNode = OBJECT_MAPPER.readTree(currentValue.getMetadata());
    return JsonDiff.asJsonPatch(prevNode, currNode);
  }
}
