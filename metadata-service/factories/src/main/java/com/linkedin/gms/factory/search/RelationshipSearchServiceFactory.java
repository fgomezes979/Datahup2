package com.linkedin.gms.factory.search;

import com.linkedin.gms.factory.common.GraphServiceFactory;
import com.linkedin.gms.factory.spring.YamlPropertySourceFactory;
import com.linkedin.metadata.graph.GraphService;
import com.linkedin.metadata.search.RelationshipSearchService;
import com.linkedin.metadata.search.SearchService;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;


@Configuration
@Import({GraphServiceFactory.class})
@PropertySource(value = "classpath:/application.yml", factory = YamlPropertySourceFactory.class)
public class RelationshipSearchServiceFactory {

  @Autowired
  @Qualifier("searchService")
  private SearchService searchService;

  @Autowired
  @Qualifier("graphService")
  private GraphService graphService;

  @Bean(name = "relationshipSearchService")
  @Primary
  @Nonnull
  protected RelationshipSearchService getInstance() {
    return new RelationshipSearchService(searchService, graphService);
  }
}
