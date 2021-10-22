package com.linkedin.gms.factory.recommendation;

import com.google.common.collect.ImmutableList;
import com.linkedin.gms.factory.recommendation.candidatesource.HighUsageCandidateSourceFactory;
import com.linkedin.gms.factory.recommendation.candidatesource.RecentlyViewedCandidateSourceFactory;
import com.linkedin.gms.factory.recommendation.candidatesource.TopPlatformsCandidateSourceFactory;
import com.linkedin.gms.factory.recommendation.candidatesource.TopTagsCandidateSourceFactory;
import com.linkedin.gms.factory.recommendation.candidatesource.TopTermsCandidateSourceFactory;
import com.linkedin.metadata.recommendation.RecommendationService;
import com.linkedin.metadata.recommendation.candidatesource.HighUsageCandidateSource;
import com.linkedin.metadata.recommendation.candidatesource.RecentlyViewedCandidateSource;
import com.linkedin.metadata.recommendation.candidatesource.TopPlatformsCandidateSource;
import com.linkedin.metadata.recommendation.candidatesource.TopTagsCandidateSource;
import com.linkedin.metadata.recommendation.candidatesource.TopTermsCandidateSource;
import com.linkedin.metadata.recommendation.ranker.SimpleRecommendationRanker;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;


@Configuration
@Import({TopPlatformsCandidateSourceFactory.class, RecentlyViewedCandidateSourceFactory.class,
    HighUsageCandidateSourceFactory.class, TopTagsCandidateSourceFactory.class, TopTermsCandidateSourceFactory.class})
public class RecommendationServiceFactory {

  @Autowired
  @Qualifier("topPlatformsCandidateSource")
  private TopPlatformsCandidateSource topPlatformsCandidateSource;

  @Autowired
  @Qualifier("recentlyViewedCandidateSource")
  private RecentlyViewedCandidateSource recentlyViewedCandidateSource;

  @Autowired
  @Qualifier("highUsageCandidateSource")
  private HighUsageCandidateSource highUsageCandidateSource;

  @Autowired
  @Qualifier("topTagsCandidateSource")
  private TopTagsCandidateSource topTagsCandidateSource;

  @Autowired
  @Qualifier("topTermsCandidateSource")
  private TopTermsCandidateSource topTermsCandidateSource;

  @Bean
  @Nonnull
  protected RecommendationService getInstance() {
    return new RecommendationService(
        ImmutableList.of(topPlatformsCandidateSource, recentlyViewedCandidateSource, highUsageCandidateSource,
            topTagsCandidateSource, topTermsCandidateSource), new SimpleRecommendationRanker());
  }
}
