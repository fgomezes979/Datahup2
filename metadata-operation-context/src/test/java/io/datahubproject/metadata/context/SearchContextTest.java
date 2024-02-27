package io.datahubproject.metadata.context;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

import com.linkedin.metadata.query.SearchFlags;
import com.linkedin.metadata.utils.elasticsearch.IndexConventionImpl;
import org.testng.annotations.Test;

public class SearchContextTest {

  @Test
  public void searchContextId() {
    SearchContext testNoFlags =
        SearchContext.builder().indexConvention(IndexConventionImpl.NO_PREFIX).build();

    assertEquals(
        testNoFlags.getCacheKeyComponent(),
        SearchContext.builder()
            .indexConvention(IndexConventionImpl.NO_PREFIX)
            .build()
            .getCacheKeyComponent(),
        "Expected consistent context ids across instances");

    SearchContext testWithFlags =
        SearchContext.builder()
            .indexConvention(IndexConventionImpl.NO_PREFIX)
            .searchFlags(new SearchFlags())
            .build();

    assertEquals(
        testWithFlags.getCacheKeyComponent(),
        SearchContext.builder()
            .indexConvention(IndexConventionImpl.NO_PREFIX)
            .searchFlags(new SearchFlags())
            .build()
            .getCacheKeyComponent(),
        "Expected consistent context ids across instances");

    assertNotEquals(
        testNoFlags.getCacheKeyComponent(),
        testWithFlags.getCacheKeyComponent(),
        "Expected differences in search flags to result in different caches");
    assertNotEquals(
        testWithFlags.getCacheKeyComponent(),
        SearchContext.builder()
            .indexConvention(IndexConventionImpl.NO_PREFIX)
            .searchFlags(new SearchFlags().setFulltext(true).setIncludeRestricted(true))
            .build()
            .getCacheKeyComponent(),
        "Expected differences in search flags to result in different caches");

    assertNotEquals(
        testNoFlags.getCacheKeyComponent(),
        SearchContext.builder()
            .indexConvention(new IndexConventionImpl("Some Prefix"))
            .searchFlags(null)
            .build()
            .getCacheKeyComponent(),
        "Expected differences in index convention to result in different caches");

    assertNotEquals(
        SearchContext.builder()
            .indexConvention(IndexConventionImpl.NO_PREFIX)
            .searchFlags(
                new SearchFlags()
                    .setFulltext(false)
                    .setIncludeRestricted(true)
                    .setSkipAggregates(true))
            .build()
            .getCacheKeyComponent(),
        SearchContext.builder()
            .indexConvention(IndexConventionImpl.NO_PREFIX)
            .searchFlags(new SearchFlags().setFulltext(true).setIncludeRestricted(true))
            .build()
            .getCacheKeyComponent(),
        "Expected differences in search flags to result in different caches");
  }
}
