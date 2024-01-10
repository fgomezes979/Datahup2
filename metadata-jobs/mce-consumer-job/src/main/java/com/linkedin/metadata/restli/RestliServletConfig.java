package com.linkedin.metadata.restli;

import com.datahub.auth.authentication.filter.AuthenticationFilter;
import com.linkedin.gms.factory.auth.SystemAuthenticationFactory;
import com.linkedin.restli.server.RestliHandlerServlet;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({SystemAuthenticationFactory.class})
public class RestliServletConfig {

  @Value("${server.port}")
  private int configuredPort;

  @Value("${entityClient.retryInterval:2}")
  private int retryInterval;

  @Value("${entityClient.numRetries:3}")
  private int numRetries;

  @Bean("restliServletRegistration")
  public ServletRegistrationBean<RestliHandlerServlet> restliServletRegistration(
      @Qualifier("restliHandlerServlet") RestliHandlerServlet servlet) {
    return new ServletRegistrationBean<>(servlet, "/gms/*");
  }

  @Bean
  public RestliHandlerServlet restliHandlerServlet() {
    return new RestliHandlerServlet();
  }

  @Bean
  public FilterRegistrationBean<AuthenticationFilter> authenticationFilterRegistrationBean(
      @Qualifier("restliServletRegistration")
          ServletRegistrationBean<RestliHandlerServlet> servlet) {
    FilterRegistrationBean<AuthenticationFilter> registrationBean = new FilterRegistrationBean<>();
    registrationBean.addServletRegistrationBeans(servlet);
    registrationBean.setOrder(1);
    return registrationBean;
  }

  @Bean
  public AuthenticationFilter authenticationFilter(
      FilterRegistrationBean<AuthenticationFilter> filterReg) {
    AuthenticationFilter filter = new AuthenticationFilter();
    filterReg.setFilter(filter);
    return filter;
  }
}
