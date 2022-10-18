package com.linkedin.gms.factory.telemetry;

import com.linkedin.gms.factory.spring.YamlPropertySourceFactory;
import com.mixpanel.mixpanelapi.MixpanelAPI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Scope;


@Configuration
@PropertySource(value = "classpath:/application.yml", factory = YamlPropertySourceFactory.class)
public class MixpanelApiFactory {
  private static final String EVENTS_ENDPOINT = "https://track.datahubproject.io/mp/track";
  private static final String PEOPLE_ENDPOINT = "https://track.datahubproject.io/mp/engage";

  @Bean(name = "mixpanelApi")
  @ConditionalOnProperty("telemetry.enabledServer")
  @Scope("singleton")
  protected MixpanelAPI getInstance() throws Exception {
    return new MixpanelAPI(EVENTS_ENDPOINT, PEOPLE_ENDPOINT);
  }
}
