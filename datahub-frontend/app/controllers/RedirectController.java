package controllers;

import config.ConfigurationProvider;
import javax.inject.Inject;
import javax.inject.Singleton;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

@Singleton
public class RedirectController extends Controller {

  @Inject ConfigurationProvider config;

  public Result favicon(Http.Request request) {
    if (config.getVisualConfig().getAssets().getFaviconUrl().startsWith("http")) {
      return permanentRedirect(config.getVisualConfig().getAssets().getFaviconUrl());
    } else {
      return ok(Application.class.getResourceAsStream(
              config.getVisualConfig().getAssets().getFaviconUrl().replace("/assets/", "/public/")))
          .as("image/x-icon");
    }
  }
}
