package datahub.spark.model.dataset;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.hadoop.fs.Path;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.linkedin.common.FabricType;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import datahub.spark.SparkConfigUtil;

public class HdfsPathDatasetTest {

  @Test
  public void testNoPathSpecList() throws InstantiationException, IllegalArgumentException {
    try (MockedStatic<SparkConfigUtil> utilities = Mockito.mockStatic(SparkConfigUtil.class)) {
      utilities.when(SparkConfigUtil::getCommonFabricType).thenReturn(FabricType.PROD);
      SparkDataset dataset = HdfsPathDataset.create(new Path("s3://my-bucket/foo/tests/bar.avro"));
      Assert.assertEquals("urn:li:dataset:(urn:li:dataPlatform:s3,my-bucket/foo/tests/bar.avro,PROD)", dataset.urn()
          .toString());
    }
  }

  @Test
  public void testPathSpecList() throws InstantiationException, IllegalArgumentException {
    try (MockedStatic<SparkConfigUtil> utilities = Mockito.mockStatic(SparkConfigUtil.class)) {
      utilities.when(SparkConfigUtil::getCommonFabricType).thenReturn(FabricType.PROD);
      utilities.when(() -> SparkConfigUtil.getPathSpecListForPlatform("s3")).thenReturn(Arrays.asList(
          "s3a://wrong-my-bucket/foo/{table}",
          "s3a://my-bucket/foo/{table}"));
      SparkDataset dataset = HdfsPathDataset.create(new Path("s3a://my-bucket/foo/tests/bar.avro"));
      Assert.assertEquals("urn:li:dataset:(urn:li:dataPlatform:s3,my-bucket/foo/tests,PROD)", dataset.urn()
          .toString());
    }
  }

  @Test
  public void testNoMatchPathSpecList() throws InstantiationException, IllegalArgumentException {
    try (MockedStatic<SparkConfigUtil> utilities = Mockito.mockStatic(SparkConfigUtil.class)) {
      utilities.when(SparkConfigUtil::getCommonFabricType).thenReturn(FabricType.PROD);
      utilities.when(() -> SparkConfigUtil.getPathSpecListForPlatform("s3")).thenReturn(Arrays.asList(
          "s3a://wrong-my-bucket/foo/{table}"));
      SparkDataset dataset = HdfsPathDataset.create(new Path("s3a://my-bucket/foo/tests/bar.avro"));
      Assert.assertEquals("urn:li:dataset:(urn:li:dataPlatform:s3,my-bucket/foo/tests/bar.avro,PROD)", dataset.urn()
          .toString());
    }
  }

  @Test
  public void testPathSpecListPlatformInstance() throws InstantiationException, IllegalArgumentException {
    try (MockedStatic<SparkConfigUtil> utilities = Mockito.mockStatic(SparkConfigUtil.class)) {
      utilities.when(SparkConfigUtil::getCommonFabricType).thenReturn(FabricType.PROD);
      utilities.when(SparkConfigUtil::getCommonPlatformInstance).thenReturn("instance");
      utilities.when(() -> SparkConfigUtil.getPathSpecListForPlatform("s3")).thenReturn(Arrays.asList(
          "s3a://wrong-my-bucket/foo/{table}",
          "s3a://my-bucket/foo/{table}"));
      SparkDataset dataset = HdfsPathDataset.create(new Path("s3a://my-bucket/foo/tests/bar.avro"));
      Assert.assertEquals("urn:li:dataset:(urn:li:dataPlatform:s3,instance.my-bucket/foo/tests,PROD)", dataset.urn()
          .toString());
    }
  }

  @Test
  public void testPathAliasList() throws InstantiationException, IllegalArgumentException {
    Map<String, String> configMap = new HashMap<String, String>();
    configMap.put("path_spec_list", "s3a://my-bucket/{table}");
    Config config = ConfigFactory.parseMap(configMap);
    try (MockedStatic<SparkConfigUtil> utilities = Mockito.mockStatic(SparkConfigUtil.class)) {
      utilities.when(SparkConfigUtil::getCommonFabricType).thenReturn(FabricType.PROD);
      utilities.when(() -> SparkConfigUtil.getPathAliasListForPlatform("s3")).thenReturn(Arrays.asList("path1",
          "path2"));
      utilities.when(() -> SparkConfigUtil.getPathAliasDetails("path1", "s3")).thenReturn(ConfigFactory.empty());
      utilities.when(() -> SparkConfigUtil.getPathAliasDetails("path2", "s3")).thenReturn(config);
      utilities.when(() -> SparkConfigUtil.getPathSpecList(config)).thenCallRealMethod();
      utilities.when(() -> SparkConfigUtil.getFabricType(config)).thenCallRealMethod();

      SparkDataset dataset = HdfsPathDataset.create(new Path("s3a://my-bucket/foo/tests/bar.avro"));
      Assert.assertEquals("urn:li:dataset:(urn:li:dataPlatform:s3,my-bucket/foo,PROD)", dataset.urn()
          .toString());
    }
  }

  // ====================================================================
  // GCS tests
  // ====================================================================

  @Test
  public void testGcsNoPathSpecList() throws InstantiationException, IllegalArgumentException {
    try (MockedStatic<SparkConfigUtil> utilities = Mockito.mockStatic(SparkConfigUtil.class)) {
      utilities.when(SparkConfigUtil::getCommonFabricType).thenReturn(FabricType.PROD);
      SparkDataset dataset = HdfsPathDataset.create(new Path("gs://my-bucket/foo/tests/bar.avro"));
      Assert.assertEquals("urn:li:dataset:(urn:li:dataPlatform:gcs,my-bucket/foo/tests/bar.avro,PROD)", dataset.urn()
          .toString());
    }
  }

  @Test
  public void testGcsPathSpecList() throws InstantiationException, IllegalArgumentException {
    try (MockedStatic<SparkConfigUtil> utilities = Mockito.mockStatic(SparkConfigUtil.class)) {
      utilities.when(SparkConfigUtil::getCommonFabricType).thenReturn(FabricType.PROD);
      utilities.when(() -> SparkConfigUtil.getPathSpecListForPlatform("gcs")).thenReturn(Arrays.asList(
          "s3a://wrong-my-bucket/foo/{table}",
          "gs://my-bucket/foo/{table}"));
      SparkDataset dataset = HdfsPathDataset.create(new Path("gs://my-bucket/foo/tests/bar.avro"));
      Assert.assertEquals("urn:li:dataset:(urn:li:dataPlatform:gcs,my-bucket/foo/tests,PROD)", dataset.urn()
          .toString());
    }
  }

}
