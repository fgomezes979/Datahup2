package com.linkedin.entity.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.linkedin.common.Status;
import com.linkedin.common.urn.Urn;
import com.linkedin.common.urn.UrnUtils;
import com.linkedin.entity.Aspect;
import com.linkedin.entity.EnvelopedAspect;
import com.linkedin.entity.EnvelopedAspectMap;
import com.linkedin.metadata.Constants;
import com.linkedin.metadata.config.cache.client.EntityClientCacheConfig;
import com.linkedin.parseq.retry.backoff.ConstantBackoff;
import com.linkedin.r2.RemoteInvocationException;
import com.linkedin.restli.client.Client;
import com.linkedin.restli.client.Request;
import com.linkedin.restli.client.response.BatchKVResponse;
import com.linkedin.restli.client.testutils.MockBatchEntityResponseFactory;
import com.linkedin.restli.client.testutils.MockSuccessfulResponseFutureBuilder;
import com.linkedin.restli.common.EntityResponse;
import com.linkedin.restli.common.HttpStatus;
import io.datahubproject.test.metadata.context.TestOperationContexts;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Set;
import org.testng.annotations.Test;

public class SystemRestliEntityClientTest {
  private static final Urn TEST_URN =
      UrnUtils.getUrn("urn:li:dataset:(urn:li:dataPlatform:hdfs,SampleHdfsDataset,PROD)");

  @Test
  public void testCache() throws RemoteInvocationException, URISyntaxException {
    Client mockRestliClient = mock(Client.class);

    // Test No Cache Config

    EntityClientCacheConfig noCacheConfig = new EntityClientCacheConfig();
    noCacheConfig.setEnabled(true);

    SystemRestliEntityClient noCacheTest =
        new SystemRestliEntityClient(
            TestOperationContexts.systemContextNoSearchAuthorization(),
            mockRestliClient,
            new ConstantBackoff(0),
            0,
            noCacheConfig);

    com.linkedin.entity.EntityResponse responseStatusTrue = buildStatusResponse(true);
    com.linkedin.entity.EntityResponse responseStatusFalse = buildStatusResponse(false);

    mockResponse(mockRestliClient, responseStatusTrue);
    assertEquals(
        noCacheTest.getV2(TEST_URN, Set.of(Constants.STATUS_ASPECT_NAME)),
        responseStatusTrue,
        "Expected un-cached Status.removed=true result");

    mockResponse(mockRestliClient, responseStatusFalse);
    assertEquals(
        noCacheTest.getV2(TEST_URN, Set.of(Constants.STATUS_ASPECT_NAME)),
        responseStatusFalse,
        "Expected un-cached Status.removed=false result");

    verify(mockRestliClient, times(2)).sendRequest(any(Request.class));

    // Test Cache Config
    reset(mockRestliClient);

    // Enable caching for entity/aspect
    EntityClientCacheConfig cacheConfig = new EntityClientCacheConfig();
    cacheConfig.setEnabled(true);
    cacheConfig.setMaxBytes(100);
    cacheConfig.setEntityAspectTTLSeconds(
        Map.of(TEST_URN.getEntityType(), Map.of(Constants.STATUS_ASPECT_NAME, 60)));

    SystemRestliEntityClient cacheTest =
        new SystemRestliEntityClient(
            TestOperationContexts.systemContextNoSearchAuthorization(),
            mockRestliClient,
            new ConstantBackoff(0),
            0,
            cacheConfig);

    mockResponse(mockRestliClient, responseStatusTrue);
    assertEquals(
        cacheTest.getV2(TEST_URN, Set.of(Constants.STATUS_ASPECT_NAME)),
        responseStatusTrue,
        "Expected initial un-cached Status.removed=true result");

    mockResponse(mockRestliClient, responseStatusFalse);
    assertEquals(
        cacheTest.getV2(TEST_URN, Set.of(Constants.STATUS_ASPECT_NAME)),
        responseStatusTrue,
        "Expected CACHED Status.removed=true result");

    verify(mockRestliClient, times(1)).sendRequest(any(Request.class));
  }

  private static com.linkedin.entity.EntityResponse buildStatusResponse(boolean value) {
    EnvelopedAspectMap aspects = new EnvelopedAspectMap();
    aspects.put(
        Constants.STATUS_ASPECT_NAME,
        new EnvelopedAspect()
            .setName(Constants.STATUS_ASPECT_NAME)
            .setValue(new Aspect(new Status().setRemoved(value).data())));
    return new com.linkedin.entity.EntityResponse()
        .setUrn(TEST_URN)
        .setEntityName(TEST_URN.getEntityType())
        .setAspects(aspects);
  }

  private static void mockResponse(Client mock, com.linkedin.entity.EntityResponse response) {
    final BatchKVResponse<String, EntityResponse<com.linkedin.entity.EntityResponse>> mockResponse =
        MockBatchEntityResponseFactory.createWithPrimitiveKey(
            String.class,
            com.linkedin.entity.EntityResponse.class,
            Map.of(TEST_URN.toString(), response),
            Map.of(TEST_URN.toString(), HttpStatus.S_200_OK),
            Map.of());

    MockSuccessfulResponseFutureBuilder<
            String, BatchKVResponse<String, EntityResponse<com.linkedin.entity.EntityResponse>>>
        responseFutureBuilder =
            new MockSuccessfulResponseFutureBuilder<
                    String,
                    BatchKVResponse<String, EntityResponse<com.linkedin.entity.EntityResponse>>>()
                .setEntity(mockResponse);
    when(mock.sendRequest(any(Request.class))).thenReturn(responseFutureBuilder.build());
  }
}
