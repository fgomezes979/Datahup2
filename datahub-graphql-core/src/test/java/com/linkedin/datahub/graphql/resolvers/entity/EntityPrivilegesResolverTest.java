package com.linkedin.datahub.graphql.resolvers.entity;

import static com.linkedin.datahub.graphql.TestUtils.*;
import static org.testng.Assert.*;

import com.datahub.authentication.Authentication;
import com.linkedin.datahub.graphql.QueryContext;
import com.linkedin.datahub.graphql.generated.Chart;
import com.linkedin.datahub.graphql.generated.Container;
import com.linkedin.datahub.graphql.generated.Dashboard;
import com.linkedin.datahub.graphql.generated.DataJob;
import com.linkedin.datahub.graphql.generated.Dataset;
import com.linkedin.datahub.graphql.generated.Entity;
import com.linkedin.datahub.graphql.generated.EntityPrivileges;
import com.linkedin.datahub.graphql.generated.GlossaryNode;
import com.linkedin.datahub.graphql.generated.GlossaryTerm;
import com.linkedin.entity.client.EntityClient;
import com.linkedin.metadata.Constants;
import com.linkedin.r2.RemoteInvocationException;
import graphql.schema.DataFetchingEnvironment;
import java.util.concurrent.CompletionException;
import org.mockito.Mockito;
import org.testng.annotations.Test;

public class EntityPrivilegesResolverTest {

  final String glossaryTermUrn = "urn:li:glossaryTerm:11115397daf94708a8822b8106cfd451";
  final String glossaryNodeUrn = "urn:li:glossaryNode:11115397daf94708a8822b8106cfd451";
  final String datasetUrn = "urn:li:dataset:(urn:li:dataPlatform:kafka,protobuf.MessageA,TEST)";
  final String chartUrn = "urn:li:chart:(looker,baz1)";
  final String dashboardUrn = "urn:li:dashboard:(looker,dashboards.1)";
  final String dataJobUrn =
      "urn:li:dataJob:(urn:li:dataFlow:(spark,test_machine.sparkTestApp,local),QueryExecId_31)";

  final String containerUrn = "urn:li:container:test";

  private DataFetchingEnvironment setUpTestWithPermissions(Entity entity) {
    QueryContext mockContext = getMockAllowContext();
    Mockito.when(mockContext.getAuthentication()).thenReturn(Mockito.mock(Authentication.class));
    DataFetchingEnvironment mockEnv = Mockito.mock(DataFetchingEnvironment.class);
    Mockito.when(mockEnv.getContext()).thenReturn(mockContext);
    Mockito.when(mockEnv.getSource()).thenReturn(entity);
    return mockEnv;
  }

  @Test
  public void testGetTermSuccessWithPermissions() throws Exception {
    final GlossaryTerm glossaryTerm = new GlossaryTerm();
    glossaryTerm.setUrn(glossaryTermUrn);

    EntityClient mockClient = Mockito.mock(EntityClient.class);
    DataFetchingEnvironment mockEnv = setUpTestWithPermissions(glossaryTerm);

    EntityPrivilegesResolver resolver = new EntityPrivilegesResolver(mockClient);
    EntityPrivileges result = resolver.get(mockEnv).get();

    assertTrue(result.getCanManageEntity());
  }

  @Test
  public void testGetNodeSuccessWithPermissions() throws Exception {
    final GlossaryNode glossaryNode = new GlossaryNode();
    glossaryNode.setUrn(glossaryNodeUrn);

    EntityClient mockClient = Mockito.mock(EntityClient.class);
    DataFetchingEnvironment mockEnv = setUpTestWithPermissions(glossaryNode);

    EntityPrivilegesResolver resolver = new EntityPrivilegesResolver(mockClient);
    EntityPrivileges result = resolver.get(mockEnv).get();

    assertTrue(result.getCanManageEntity());
    assertTrue(result.getCanManageChildren());
  }

  private DataFetchingEnvironment setUpTestWithoutPermissions(Entity entity) {
    QueryContext mockContext = getMockDenyContext();
    Mockito.when(mockContext.getAuthentication()).thenReturn(Mockito.mock(Authentication.class));
    DataFetchingEnvironment mockEnv = Mockito.mock(DataFetchingEnvironment.class);
    Mockito.when(mockEnv.getContext()).thenReturn(mockContext);
    Mockito.when(mockEnv.getSource()).thenReturn(entity);
    return mockEnv;
  }

  @Test
  public void testGetTermSuccessWithoutPermissions() throws Exception {
    final GlossaryTerm glossaryTerm = new GlossaryTerm();
    glossaryTerm.setUrn(glossaryTermUrn);

    EntityClient mockClient = Mockito.mock(EntityClient.class);
    DataFetchingEnvironment mockEnv = setUpTestWithoutPermissions(glossaryTerm);

    EntityPrivilegesResolver resolver = new EntityPrivilegesResolver(mockClient);
    EntityPrivileges result = resolver.get(mockEnv).get();

    assertFalse(result.getCanManageEntity());
  }

  @Test
  public void testGetNodeSuccessWithoutPermissions() throws Exception {
    final GlossaryNode glossaryNode = new GlossaryNode();
    glossaryNode.setUrn(glossaryNodeUrn);

    EntityClient mockClient = Mockito.mock(EntityClient.class);
    DataFetchingEnvironment mockEnv = setUpTestWithoutPermissions(glossaryNode);

    EntityPrivilegesResolver resolver = new EntityPrivilegesResolver(mockClient);
    EntityPrivileges result = resolver.get(mockEnv).get();

    assertFalse(result.getCanManageEntity());
    assertFalse(result.getCanManageChildren());
  }

  @Test
  public void testGetFailure() throws Exception {
    final GlossaryNode glossaryNode = new GlossaryNode();
    glossaryNode.setUrn(glossaryNodeUrn);

    EntityClient mockClient = Mockito.mock(EntityClient.class);
    DataFetchingEnvironment mockEnv = setUpTestWithoutPermissions(glossaryNode);

    Mockito.doThrow(RemoteInvocationException.class)
        .when(mockClient)
        .getV2(
            Mockito.eq(Constants.GLOSSARY_NODE_ENTITY_NAME),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(Authentication.class));

    EntityPrivilegesResolver resolver = new EntityPrivilegesResolver(mockClient);
    assertThrows(CompletionException.class, () -> resolver.get(mockEnv).join());
  }

  @Test
  public void testGetDatasetSuccessWithPermissions() throws Exception {
    final Dataset dataset = new Dataset();
    dataset.setUrn(datasetUrn);

    EntityClient mockClient = Mockito.mock(EntityClient.class);
    DataFetchingEnvironment mockEnv = setUpTestWithPermissions(dataset);

    EntityPrivilegesResolver resolver = new EntityPrivilegesResolver(mockClient);
    EntityPrivileges result = resolver.get(mockEnv).get();
    assertTrue(result.getCanEditSchemaFieldDescription());
    assertTrue(result.getCanEditSchemaFieldTags());
    assertTrue(result.getCanEditSchemaFieldGlossaryTerms());
    validateHasCommonEntityPrivileges(result);
  }

  @Test
  public void testGetDatasetSuccessWithoutPermissions() throws Exception {
    final Dataset dataset = new Dataset();
    dataset.setUrn(datasetUrn);

    EntityClient mockClient = Mockito.mock(EntityClient.class);
    DataFetchingEnvironment mockEnv = setUpTestWithoutPermissions(dataset);

    EntityPrivilegesResolver resolver = new EntityPrivilegesResolver(mockClient);
    EntityPrivileges result = resolver.get(mockEnv).get();
    assertFalse(result.getCanEditSchemaFieldDescription());
    assertFalse(result.getCanEditSchemaFieldTags());
    assertFalse(result.getCanEditSchemaFieldGlossaryTerms());
    validateMissingCommonEntityPrivileges(result);
  }

  @Test
  public void testGetChartSuccessWithPermissions() throws Exception {
    final Chart chart = new Chart();
    chart.setUrn(chartUrn);

    EntityClient mockClient = Mockito.mock(EntityClient.class);
    DataFetchingEnvironment mockEnv = setUpTestWithPermissions(chart);

    EntityPrivilegesResolver resolver = new EntityPrivilegesResolver(mockClient);
    EntityPrivileges result = resolver.get(mockEnv).get();

    validateHasCommonEntityPrivileges(result);
  }

  @Test
  public void testGetChartSuccessWithoutPermissions() throws Exception {
    final Chart chart = new Chart();
    chart.setUrn(chartUrn);

    EntityClient mockClient = Mockito.mock(EntityClient.class);
    DataFetchingEnvironment mockEnv = setUpTestWithoutPermissions(chart);

    EntityPrivilegesResolver resolver = new EntityPrivilegesResolver(mockClient);
    EntityPrivileges result = resolver.get(mockEnv).get();

    validateMissingCommonEntityPrivileges(result);
  }

  @Test
  public void testGetDashboardSuccessWithPermissions() throws Exception {
    final Dashboard dashboard = new Dashboard();
    dashboard.setUrn(dashboardUrn);

    EntityClient mockClient = Mockito.mock(EntityClient.class);
    DataFetchingEnvironment mockEnv = setUpTestWithPermissions(dashboard);

    EntityPrivilegesResolver resolver = new EntityPrivilegesResolver(mockClient);
    EntityPrivileges result = resolver.get(mockEnv).get();
    validateHasCommonEntityPrivileges(result);
  }

  @Test
  public void testGetDashboardSuccessWithoutPermissions() throws Exception {
    final Dashboard dashboard = new Dashboard();
    dashboard.setUrn(dashboardUrn);

    EntityClient mockClient = Mockito.mock(EntityClient.class);
    DataFetchingEnvironment mockEnv = setUpTestWithoutPermissions(dashboard);

    EntityPrivilegesResolver resolver = new EntityPrivilegesResolver(mockClient);
    EntityPrivileges result = resolver.get(mockEnv).get();
    validateMissingCommonEntityPrivileges(result);
  }

  @Test
  public void testGetDataJobSuccessWithPermissions() throws Exception {
    final DataJob dataJob = new DataJob();
    dataJob.setUrn(dataJobUrn);

    EntityClient mockClient = Mockito.mock(EntityClient.class);
    DataFetchingEnvironment mockEnv = setUpTestWithPermissions(dataJob);

    EntityPrivilegesResolver resolver = new EntityPrivilegesResolver(mockClient);
    EntityPrivileges result = resolver.get(mockEnv).get();
    validateHasCommonEntityPrivileges(result);
  }

  @Test
  public void testGetDataJobSuccessWithoutPermissions() throws Exception {
    final DataJob dataJob = new DataJob();
    dataJob.setUrn(dataJobUrn);

    EntityClient mockClient = Mockito.mock(EntityClient.class);
    DataFetchingEnvironment mockEnv = setUpTestWithoutPermissions(dataJob);

    EntityPrivilegesResolver resolver = new EntityPrivilegesResolver(mockClient);
    EntityPrivileges result = resolver.get(mockEnv).get();

    assertFalse(result.getCanEditLineage());
  }

  @Test
  public void testGetOtherEntitySuccessWithPermissions() throws Exception {
    final Container container = new Container();
    container.setUrn(containerUrn);

    EntityClient mockClient = Mockito.mock(EntityClient.class);
    DataFetchingEnvironment mockEnv = setUpTestWithPermissions(container);

    EntityPrivilegesResolver resolver = new EntityPrivilegesResolver(mockClient);
    EntityPrivileges result = resolver.get(mockEnv).get();
    validateHasCommonEntityPrivileges(result);
  }

  @Test
  public void testGetOtherEntitySuccessWithoutPermissions() throws Exception {
    final Container container = new Container();
    container.setUrn(containerUrn);

    EntityClient mockClient = Mockito.mock(EntityClient.class);
    DataFetchingEnvironment mockEnv = setUpTestWithoutPermissions(container);

    EntityPrivilegesResolver resolver = new EntityPrivilegesResolver(mockClient);
    EntityPrivileges result = resolver.get(mockEnv).get();

    assertFalse(result.getCanEditLineage());
  }

  public void validateHasCommonEntityPrivileges(EntityPrivileges result) {
    assertTrue(result.getCanEditLineage());
    assertTrue(result.getCanEditDomains());
    assertTrue(result.getCanEditDataProducts());
    assertTrue(result.getCanEditTags());
    assertTrue(result.getCanEditGlossaryTerms());
    assertTrue(result.getCanEditOwners());
    assertTrue(result.getCanEditDeprecation());
    assertTrue(result.getCanEditDescription());
    assertTrue(result.getCanEditLinks());
    assertTrue(result.getCanEditAssertions());
  }

  public void validateMissingCommonEntityPrivileges(EntityPrivileges result) {
    assertFalse(result.getCanEditLineage());
    assertFalse(result.getCanEditDomains());
    assertFalse(result.getCanEditDataProducts());
    assertFalse(result.getCanEditTags());
    assertFalse(result.getCanEditGlossaryTerms());
    assertFalse(result.getCanEditOwners());
    assertFalse(result.getCanEditDeprecation());
    assertFalse(result.getCanEditDescription());
    assertFalse(result.getCanEditLinks());
    assertFalse(result.getCanEditAssertions());
  }
}
