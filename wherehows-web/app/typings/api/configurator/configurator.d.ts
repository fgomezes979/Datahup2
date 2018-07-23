import { ApiStatus } from 'wherehows-web/utils/api';
import { DatasetPlatform } from 'wherehows-web/constants';

/**
 * Describes the interface for the IAppConfig object
 * @interface IAppConfig
 */
interface IAppConfig {
  isInternal: boolean | void;
  jitAclAccessWhitelist: Array<DatasetPlatform> | void;
  shouldShowBrowserRevamp: boolean;
  shouldShowDatasetLineage: boolean;
  tracking: {
    isEnabled: boolean;
    trackers: {
      piwik: {
        piwikSiteId: number;
        piwikUrl: string;
      };
    };
  };
  [key: string]: any;
}

/**
 * Describes the interface for the json response when a GET request is made to the
 * configurator endpoint
 * @interface IConfiguratorGetResponse
 */
interface IConfiguratorGetResponse {
  status: ApiStatus;
  config: IAppConfig;
}

export { IAppConfig, IConfiguratorGetResponse };
