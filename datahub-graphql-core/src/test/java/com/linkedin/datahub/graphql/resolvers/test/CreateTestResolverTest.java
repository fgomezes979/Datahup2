package com.linkedin.datahub.graphql.resolvers.test;

import com.datahub.authentication.Authentication;
import com.linkedin.datahub.graphql.QueryContext;
import com.linkedin.datahub.graphql.generated.CreateTestInput;
import com.linkedin.datahub.graphql.generated.TestDefinitionInput;
import com.linkedin.entity.client.EntityClient;
import com.linkedin.events.metadata.ChangeType;
import com.linkedin.metadata.Constants;
import com.linkedin.metadata.key.TestKey;
import com.linkedin.metadata.test.TestEngine;
import com.linkedin.metadata.test.definition.ValidationResult;
import com.linkedin.metadata.utils.GenericRecordUtils;
import com.linkedin.mxe.MetadataChangeProposal;
import com.linkedin.r2.RemoteInvocationException;
import com.linkedin.test.TestDefinitionType;
import com.linkedin.test.TestInfo;
import graphql.schema.DataFetchingEnvironment;
import java.util.Collections;
import java.util.concurrent.CompletionException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import static com.linkedin.datahub.graphql.TestUtils.getMockAllowContext;
import static com.linkedin.datahub.graphql.TestUtils.getMockDenyContext;
import static org.mockito.ArgumentMatchers.anyString;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;


public class CreateTestResolverTest {

  private static final CreateTestInput TEST_INPUT =
      new CreateTestInput("test-id", "test-name", "test-category", "test-description", new TestDefinitionInput("{}"));

  @Test
  public void testGetSuccess() throws Exception {
    // Create resolver
    EntityClient mockClient = Mockito.mock(EntityClient.class);
    TestEngine mockEngine = Mockito.mock(TestEngine.class);
    CreateTestResolver resolver = new CreateTestResolver(mockClient, mockEngine);

    // Execute resolver
    QueryContext mockContext = getMockAllowContext();
    DataFetchingEnvironment mockEnv = Mockito.mock(DataFetchingEnvironment.class);
    Mockito.when(mockEnv.getArgument(Mockito.eq("input"))).thenReturn(TEST_INPUT);
    Mockito.when(mockEnv.getContext()).thenReturn(mockContext);
    Mockito.when(mockEngine.validateJson(anyString())).thenReturn(ValidationResult.validResult());

    resolver.get(mockEnv).get();

    final TestKey key = new TestKey();
    key.setId("test-id");

    // Not ideal to match against "any", but we don't know the auto-generated execution request id
    ArgumentCaptor<MetadataChangeProposal> proposalCaptor = ArgumentCaptor.forClass(MetadataChangeProposal.class);
    Mockito.verify(mockClient, Mockito.times(1))
        .ingestProposal(proposalCaptor.capture(), Mockito.any(Authentication.class));
    MetadataChangeProposal resultProposal = proposalCaptor.getValue();
    assertEquals(resultProposal.getEntityType(), Constants.TEST_ENTITY_NAME);
    assertEquals(resultProposal.getAspectName(), Constants.TEST_INFO_ASPECT_NAME);
    assertEquals(resultProposal.getChangeType(), ChangeType.UPSERT);
    assertEquals(resultProposal.getEntityKeyAspect(), GenericRecordUtils.serializeAspect(key));
    TestInfo resultInfo = GenericRecordUtils.deserializeAspect(resultProposal.getAspect().getValue(),
        resultProposal.getAspect().getContentType(), TestInfo.class);
    assertEquals(resultInfo.getName(), "test-name");
    assertEquals(resultInfo.getCategory(), "test-category");
    assertEquals(resultInfo.getDescription(), "test-description");
    assertEquals(resultInfo.getDefinition().getType(), TestDefinitionType.JSON);
    assertEquals(resultInfo.getDefinition().getJson(), "{}");
  }

  @Test
  public void testInvalidInput() throws Exception {
    // Create resolver
    EntityClient mockClient = Mockito.mock(EntityClient.class);
    TestEngine mockEngine = Mockito.mock(TestEngine.class);
    CreateTestResolver resolver = new CreateTestResolver(mockClient, mockEngine);

    // Execute resolver
    DataFetchingEnvironment mockEnv = Mockito.mock(DataFetchingEnvironment.class);
    QueryContext mockContext = getMockAllowContext();
    Mockito.when(mockEnv.getArgument(Mockito.eq("input"))).thenReturn(TEST_INPUT);
    Mockito.when(mockEnv.getContext()).thenReturn(mockContext);
    Mockito.when(mockEngine.validateJson(anyString())).thenReturn(new ValidationResult(false, Collections.emptyList()));

    assertThrows(CompletionException.class, () -> resolver.get(mockEnv).join());
    Mockito.verify(mockClient, Mockito.times(0)).ingestProposal(Mockito.any(), Mockito.any(Authentication.class));
  }

  @Test
  public void testGetUnauthorized() throws Exception {
    // Create resolver
    EntityClient mockClient = Mockito.mock(EntityClient.class);
    TestEngine mockEngine = Mockito.mock(TestEngine.class);
    CreateTestResolver resolver = new CreateTestResolver(mockClient, mockEngine);

    // Execute resolver
    DataFetchingEnvironment mockEnv = Mockito.mock(DataFetchingEnvironment.class);
    QueryContext mockContext = getMockDenyContext();
    Mockito.when(mockEnv.getArgument(Mockito.eq("input"))).thenReturn(TEST_INPUT);
    Mockito.when(mockEnv.getContext()).thenReturn(mockContext);

    assertThrows(CompletionException.class, () -> resolver.get(mockEnv).join());
    Mockito.verify(mockClient, Mockito.times(0)).ingestProposal(Mockito.any(), Mockito.any(Authentication.class));
  }

  @Test
  public void testGetEntityClientException() throws Exception {
    // Create resolver
    EntityClient mockClient = Mockito.mock(EntityClient.class);
    TestEngine mockEngine = Mockito.mock(TestEngine.class);
    Mockito.doThrow(RemoteInvocationException.class)
        .when(mockClient)
        .ingestProposal(Mockito.any(), Mockito.any(Authentication.class));
    CreateTestResolver resolver = new CreateTestResolver(mockClient, mockEngine);

    // Execute resolver
    DataFetchingEnvironment mockEnv = Mockito.mock(DataFetchingEnvironment.class);
    QueryContext mockContext = getMockAllowContext();
    Mockito.when(mockEnv.getArgument(Mockito.eq("input"))).thenReturn(TEST_INPUT);
    Mockito.when(mockEnv.getContext()).thenReturn(mockContext);

    assertThrows(CompletionException.class, () -> resolver.get(mockEnv).join());
  }
}