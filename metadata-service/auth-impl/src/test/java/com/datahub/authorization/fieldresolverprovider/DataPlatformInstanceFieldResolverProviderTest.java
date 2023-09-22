package com.datahub.authorization.fieldresolverprovider;

import com.datahub.authentication.Authentication;
import com.datahub.authorization.ResourceFieldType;
import com.datahub.authorization.ResourceSpec;
import com.linkedin.common.DataPlatformInstance;
import com.linkedin.common.urn.Urn;
import com.linkedin.entity.Aspect;
import com.linkedin.entity.EntityResponse;
import com.linkedin.entity.EnvelopedAspect;
import com.linkedin.entity.EnvelopedAspectMap;
import com.linkedin.entity.client.EntityClient;
import com.linkedin.r2.RemoteInvocationException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Set;

import static com.linkedin.metadata.Constants.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class DataPlatformInstanceFieldResolverProviderTest {

  public static final String DATA_PLATFORM_INSTANCE_URN = "urn:li:dataPlatformInstance:(urn:li:dataPlatform:s3,test-platform-instance)";
  private static final String RESOURCE_URN = "urn:li:dataset:(urn:li:dataPlatform:testPlatform,testDataset,PROD)";
  private static final ResourceSpec RESOURCE_SPEC = new ResourceSpec(DATASET_ENTITY_NAME, RESOURCE_URN);

  @Mock
  private EntityClient entityClientMock;
  @Mock
  private Authentication systemAuthenticationMock;

  private DataPlatformInstanceFieldResolverProvider dataPlatformInstanceFieldResolverProvider;

  @BeforeMethod
  public void setup() {
    MockitoAnnotations.initMocks(this);
    dataPlatformInstanceFieldResolverProvider =
        new DataPlatformInstanceFieldResolverProvider(entityClientMock, systemAuthenticationMock);
  }

  @Test
  public void shouldReturnDataPlatformInstanceType() {
    assertEquals(ResourceFieldType.DATA_PLATFORM_INSTANCE, dataPlatformInstanceFieldResolverProvider.getFieldType());
  }

  @Test
  public void shouldReturnFieldValueWithResourceSpecIfTypeIsDataPlatformInstance() {
    var resourceSpec = new ResourceSpec(DATA_PLATFORM_INSTANCE_ENTITY_NAME, DATA_PLATFORM_INSTANCE_URN);

    var result = dataPlatformInstanceFieldResolverProvider.getFieldResolver(resourceSpec);

    assertEquals(Set.of(DATA_PLATFORM_INSTANCE_URN), result.getFieldValuesFuture().join().getValues());
    verifyZeroInteractions(entityClientMock);
  }

  @Test
  public void shouldReturnEmptyFieldValueWhenResponseIsNull() throws RemoteInvocationException, URISyntaxException {
    when(entityClientMock.getV2(
        eq(DATASET_ENTITY_NAME),
        any(Urn.class),
        eq(Collections.singleton(DATA_PLATFORM_INSTANCE_ASPECT_NAME)),
        eq(systemAuthenticationMock)
    )).thenReturn(null);

    var result = dataPlatformInstanceFieldResolverProvider.getFieldResolver(RESOURCE_SPEC);

    assertTrue(result.getFieldValuesFuture().join().getValues().isEmpty());
    verify(entityClientMock, times(1)).getV2(
        eq(DATASET_ENTITY_NAME),
        any(Urn.class),
        eq(Collections.singleton(DATA_PLATFORM_INSTANCE_ASPECT_NAME)),
        eq(systemAuthenticationMock)
    );
  }

  @Test
  public void shouldReturnEmptyFieldValueWhenResourceHasNoDataPlatformInstance()
      throws RemoteInvocationException, URISyntaxException {
    var entityResponseMock = mock(EntityResponse.class);
    when(entityResponseMock.getAspects()).thenReturn(new EnvelopedAspectMap());
    when(entityClientMock.getV2(
        eq(DATASET_ENTITY_NAME),
        any(Urn.class),
        eq(Collections.singleton(DATA_PLATFORM_INSTANCE_ASPECT_NAME)),
        eq(systemAuthenticationMock)
    )).thenReturn(entityResponseMock);

    var result = dataPlatformInstanceFieldResolverProvider.getFieldResolver(RESOURCE_SPEC);

    assertTrue(result.getFieldValuesFuture().join().getValues().isEmpty());
    verify(entityClientMock, times(1)).getV2(
        eq(DATASET_ENTITY_NAME),
        any(Urn.class),
        eq(Collections.singleton(DATA_PLATFORM_INSTANCE_ASPECT_NAME)),
        eq(systemAuthenticationMock)
    );
  }

  @Test
  public void shouldReturnEmptyFieldValueWhenThereIsAnException() throws RemoteInvocationException, URISyntaxException {
    when(entityClientMock.getV2(
        eq(DATASET_ENTITY_NAME),
        any(Urn.class),
        eq(Collections.singleton(DATA_PLATFORM_INSTANCE_ASPECT_NAME)),
        eq(systemAuthenticationMock)
    )).thenThrow(new RemoteInvocationException());

    var result = dataPlatformInstanceFieldResolverProvider.getFieldResolver(RESOURCE_SPEC);

    assertTrue(result.getFieldValuesFuture().join().getValues().isEmpty());
    verify(entityClientMock, times(1)).getV2(
        eq(DATASET_ENTITY_NAME),
        any(Urn.class),
        eq(Collections.singleton(DATA_PLATFORM_INSTANCE_ASPECT_NAME)),
        eq(systemAuthenticationMock)
    );
  }

  @Test
  public void shouldReturnEmptyFieldValueWhenDataPlatformInstanceHasNoInstance()
      throws RemoteInvocationException, URISyntaxException {

    var dataPlatform = new DataPlatformInstance()
        .setPlatform(Urn.createFromString("urn:li:dataPlatform:s3"));
    var entityResponseMock = mock(EntityResponse.class);
    var envelopedAspectMap = new EnvelopedAspectMap();
    envelopedAspectMap.put(DATA_PLATFORM_INSTANCE_ASPECT_NAME,
        new EnvelopedAspect().setValue(new Aspect(dataPlatform.data())));
    when(entityResponseMock.getAspects()).thenReturn(envelopedAspectMap);
    when(entityClientMock.getV2(
        eq(DATASET_ENTITY_NAME),
        any(Urn.class),
        eq(Collections.singleton(DATA_PLATFORM_INSTANCE_ASPECT_NAME)),
        eq(systemAuthenticationMock)
    )).thenThrow(new RemoteInvocationException());

    var result = dataPlatformInstanceFieldResolverProvider.getFieldResolver(RESOURCE_SPEC);

    assertTrue(result.getFieldValuesFuture().join().getValues().isEmpty());
    verify(entityClientMock, times(1)).getV2(
        eq(DATASET_ENTITY_NAME),
        any(Urn.class),
        eq(Collections.singleton(DATA_PLATFORM_INSTANCE_ASPECT_NAME)),
        eq(systemAuthenticationMock)
    );
  }

  @Test
  public void shouldReturnFieldValueWithDataPlatformInstanceOfTheResource()
      throws RemoteInvocationException, URISyntaxException {

    var dataPlatformInstance = new DataPlatformInstance()
        .setPlatform(Urn.createFromString("urn:li:dataPlatform:s3"))
        .setInstance(Urn.createFromString(DATA_PLATFORM_INSTANCE_URN));
    var entityResponseMock = mock(EntityResponse.class);
    var envelopedAspectMap = new EnvelopedAspectMap();
    envelopedAspectMap.put(DATA_PLATFORM_INSTANCE_ASPECT_NAME,
        new EnvelopedAspect().setValue(new Aspect(dataPlatformInstance.data())));
    when(entityResponseMock.getAspects()).thenReturn(envelopedAspectMap);
    when(entityClientMock.getV2(
        eq(DATASET_ENTITY_NAME),
        any(Urn.class),
        eq(Collections.singleton(DATA_PLATFORM_INSTANCE_ASPECT_NAME)),
        eq(systemAuthenticationMock)
    )).thenReturn(entityResponseMock);

    var result = dataPlatformInstanceFieldResolverProvider.getFieldResolver(RESOURCE_SPEC);

    assertEquals(Set.of(DATA_PLATFORM_INSTANCE_URN), result.getFieldValuesFuture().join().getValues());
    verify(entityClientMock, times(1)).getV2(
        eq(DATASET_ENTITY_NAME),
        any(Urn.class),
        eq(Collections.singleton(DATA_PLATFORM_INSTANCE_ASPECT_NAME)),
        eq(systemAuthenticationMock)
    );
  }
}
