package com.datahub.authorization;

/**
 * List of resource field types to fetch for a given resource
 */
public enum ResourceFieldType {
  /**
   * Type of resource (e.g. dataset, chart)
   */
  RESOURCE_TYPE,
  /**
   * Urn of resource
   */
  RESOURCE_URN,
  /**
   * Owners of resource
   */
  OWNER,
  /**
   * Domains of resource
   */
  DOMAIN,
  /**
   * Platform instance of resource
   */
  PLATFORM_INSTANCE
}
