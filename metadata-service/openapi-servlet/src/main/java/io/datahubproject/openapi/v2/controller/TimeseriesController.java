package io.datahubproject.openapi.v2.controller;

import static com.linkedin.metadata.authorization.ApiGroup.TIMESERIES;
import static com.linkedin.metadata.authorization.ApiOperation.READ;

import com.datahub.authentication.Authentication;
import com.datahub.authentication.AuthenticationContext;
import com.datahub.authorization.AuthUtil;
import com.datahub.authorization.AuthorizerChain;
import com.linkedin.common.urn.UrnUtils;
import com.linkedin.metadata.authorization.PoliciesConfig;
import com.linkedin.metadata.models.AspectSpec;
import com.linkedin.metadata.models.registry.EntityRegistry;
import com.linkedin.metadata.query.filter.SortCriterion;
import com.linkedin.metadata.query.filter.SortOrder;
import com.linkedin.metadata.timeseries.GenericTimeseriesDocument;
import com.linkedin.metadata.timeseries.TimeseriesAspectService;
import com.linkedin.metadata.timeseries.TimeseriesScrollResult;
import com.linkedin.metadata.utils.SearchUtil;
import io.datahubproject.openapi.exception.UnauthorizedException;
import io.datahubproject.openapi.v2.models.GenericScrollResult;
import io.datahubproject.openapi.v2.models.GenericTimeseriesAspect;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URISyntaxException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v2/timeseries")
@Slf4j
@Tag(
    name = "Generic Timeseries Aspects",
    description = "APIs for ingesting and accessing timeseries aspects")
public class TimeseriesController {

  @Autowired private EntityRegistry entityRegistry;

  @Autowired private TimeseriesAspectService timeseriesAspectService;

  @Autowired private AuthorizerChain authorizationChain;

  @GetMapping(value = "/{entityName}/{aspectName}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<GenericScrollResult<GenericTimeseriesAspect>> getAspects(
      @PathVariable("entityName") String entityName,
      @PathVariable("aspectName") String aspectName,
      @RequestParam(value = "count", defaultValue = "10") Integer count,
      @RequestParam(value = "scrollId", required = false) String scrollId,
      @RequestParam(value = "startTimeMillis", required = false) Long startTimeMillis,
      @RequestParam(value = "endTimeMillis", required = false) Long endTimeMillis,
      @RequestParam(value = "systemMetadata", required = false, defaultValue = "false")
          Boolean withSystemMetadata)
      throws URISyntaxException {

    Authentication authentication = AuthenticationContext.getAuthentication();
    if (!AuthUtil.isAPIAuthorizedEntityType(
        authentication,
        authorizationChain,
        PoliciesConfig.lookupAPIPrivilege(TIMESERIES, READ),
        entityName)) {
      throw new UnauthorizedException(
          authentication.getActor().toUrnStr() + " is unauthorized to " + READ + " " + TIMESERIES);
    }

    AspectSpec aspectSpec = entityRegistry.getEntitySpec(entityName).getAspectSpec(aspectName);
    if (!aspectSpec.isTimeseries()) {
      throw new IllegalArgumentException("Only timeseries aspects are supported.");
    }

    List<SortCriterion> sortCriterion =
        List.of(
            SearchUtil.sortBy("timestampMillis", SortOrder.DESCENDING),
            SearchUtil.sortBy("messageId", SortOrder.DESCENDING));

    TimeseriesScrollResult result =
        timeseriesAspectService.scrollAspects(
            entityName,
            aspectName,
            null,
            sortCriterion,
            scrollId,
            count,
            startTimeMillis,
            endTimeMillis);

    if (!AuthUtil.isAPIAuthorizedUrns(
        authentication,
        authorizationChain,
        PoliciesConfig.lookupAPIPrivilege(TIMESERIES, READ),
        result.getDocuments().stream()
            .map(doc -> UrnUtils.getUrn(doc.getUrn()))
            .collect(Collectors.toSet()))) {
      throw new UnauthorizedException(
          authentication.getActor().toUrnStr() + " is unauthorized to " + READ + " entities.");
    }

    return ResponseEntity.ok(
        GenericScrollResult.<GenericTimeseriesAspect>builder()
            .scrollId(result.getScrollId())
            .results(toGenericTimeseriesAspect(result.getDocuments(), withSystemMetadata))
            .build());
  }

  private static List<GenericTimeseriesAspect> toGenericTimeseriesAspect(
      List<GenericTimeseriesDocument> docs, boolean withSystemMetadata) {
    return docs.stream()
        .map(
            doc ->
                GenericTimeseriesAspect.builder()
                    .urn(doc.getUrn())
                    .messageId(doc.getMessageId())
                    .timestampMillis(doc.getTimestampMillis())
                    .systemMetadata(withSystemMetadata ? doc.getSystemMetadata() : null)
                    .event(doc.getEvent())
                    .build())
        .collect(Collectors.toList());
  }
}
