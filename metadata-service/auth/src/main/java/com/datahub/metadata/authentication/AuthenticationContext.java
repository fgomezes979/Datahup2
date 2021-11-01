package com.datahub.metadata.authentication;

import com.datahub.authentication.Authentication;


public class AuthenticationContext {
  private static final ThreadLocal<Authentication> AUTHENTICATION = new ThreadLocal<>();

  public static Authentication getAuthentication() {
    return AUTHENTICATION.get();
  }

  public static void setAuthentication(Authentication authentication) {
    AUTHENTICATION.set(authentication);
  }

  public static void remove() {
    AUTHENTICATION.remove();
  }

  private AuthenticationContext() { }
}
