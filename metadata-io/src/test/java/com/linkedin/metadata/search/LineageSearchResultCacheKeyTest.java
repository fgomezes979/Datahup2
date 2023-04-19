package com.linkedin.metadata.search;

import java.time.temporal.ChronoUnit;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;


public class LineageSearchResultCacheKeyTest extends AbstractTestNGSpringContextTests {

  @Test
  public void testNulls() {
    // ensure no NPE
    assertEquals(new EntityLineageResultCacheKey(null, null, null, null, null, ChronoUnit.DAYS),
            new EntityLineageResultCacheKey(null, null, null, null, null, ChronoUnit.DAYS));
  }

  @Test
  public void testDateTruncation() {
    // expect start of day milli
    assertEquals(new EntityLineageResultCacheKey(null, null, 1679529600000L,
            1679616000000L, null, ChronoUnit.DAYS),
        new EntityLineageResultCacheKey(null, null, 1679530293000L,
            1679530293001L, null, ChronoUnit.DAYS));
  }
}
