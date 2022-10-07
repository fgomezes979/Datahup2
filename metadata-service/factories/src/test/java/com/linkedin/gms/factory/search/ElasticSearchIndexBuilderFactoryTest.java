package com.linkedin.gms.factory.search;

import com.linkedin.metadata.search.elasticsearch.indexbuilder.ESIndexBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.testng.Assert.*;

@SpringBootTest(
        properties = "elasticsearch.index.settingsOverrides={\"my_index\":{\"number_of_shards\":\"10\"}}",
        classes = {ElasticSearchIndexBuilderFactory.class})
public class ElasticSearchIndexBuilderFactoryTest extends AbstractTestNGSpringContextTests {
    @Autowired
    ESIndexBuilder test;

    @Test
    void testInjection() {
        assertNotNull(test);
        assertEquals("10", test.getIndexSettingOverrides().get("my_index").get("number_of_shards"));
    }
}
