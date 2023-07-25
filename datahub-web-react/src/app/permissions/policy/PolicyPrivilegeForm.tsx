import React, { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { Form, Select, Tag, Tooltip, Typography } from 'antd';
import styled from 'styled-components/macro';

import { useEntityRegistry } from '../../useEntityRegistry';
import { useAppConfig } from '../../useAppConfig';
import {
    useGetSearchResultsForMultipleLazyQuery,
    useGetSearchResultsLazyQuery,
} from '../../../graphql/search.generated';
import { ResourceFilter, PolicyType, EntityType } from '../../../types.generated';
import {
    convertLegacyResourceFilter,
    createCriterionValue,
    createCriterionValueWithEntity,
    EMPTY_POLICY,
    getFieldValues,
    mapResourceTypeToDisplayName,
    mapResourceTypeToEntityType,
    mapResourceTypeToPrivileges,
    setFieldValues,
} from './policyUtils';

type Props = {
    policyType: PolicyType;
    resources?: ResourceFilter;
    setResources: (resources: ResourceFilter) => void;
    privileges: Array<string>;
    setPrivileges: (newPrivs: Array<string>) => void;
};

const SearchResultContainer = styled.div`
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px;
`;

const PrivilegesForm = styled(Form)`
    margin: 12px;
    margin-top: 36px;
    margin-bottom: 40px;
`;

/**
 * Component used to construct the "privileges" and "resources" portion of a DataHub
 * access Policy.
 */
export default function PolicyPrivilegeForm({
    policyType,
    resources: maybeResources,
    setResources,
    privileges,
    setPrivileges,
}: Props) {
    const entityRegistry = useEntityRegistry();

    // Configuration used for displaying options
    const {
        config: { policiesConfig },
    } = useAppConfig();

    const resources: ResourceFilter = convertLegacyResourceFilter(maybeResources) || EMPTY_POLICY.resources;
    const resourceTypes = getFieldValues(resources.filter, 'RESOURCE_TYPE') || [];
    const resourceEntities = getFieldValues(resources.filter, 'RESOURCE_URN') || [];

    const getDisplayName = (entity) => {
        if (!entity) {
            return null;
        }
        return entityRegistry.getDisplayName(entity.type, entity);
    };

    const resourceUrnToDisplayName = new Map();
    resourceEntities.forEach((resourceEntity) => {
        resourceUrnToDisplayName[resourceEntity.value] = getDisplayName(resourceEntity.entity);
    });
    // Search for resources
    const [searchResources, { data: resourcesSearchData }] = useGetSearchResultsForMultipleLazyQuery();
    const resourceSearchResults = resourcesSearchData?.searchAcrossEntities?.searchResults;

    // Same for domains
    const domains = getFieldValues(resources.filter, 'DOMAIN') || [];
    const domainUrnToDisplayName = new Map();
    domains.forEach((domainEntity) => {
        domainUrnToDisplayName[domainEntity.value] = getDisplayName(domainEntity.entity);
    });
    // Search for domains
    const [searchDomains, { data: domainsSearchData }] = useGetSearchResultsLazyQuery();
    const domainSearchResults = domainsSearchData?.search?.searchResults;

    // Whether to show the resource filter inputs including "resource type", "resource", and "domain"
    const showResourceFilterInput = policyType !== PolicyType.Platform;

    // Current Select dropdown values
    const resourceTypeSelectValue = resourceTypes.map((criterionValue) => criterionValue.value);
    const resourceSelectValue = resourceEntities.map((criterionValue) => criterionValue.value);
    const domainSelectValue = getFieldValues(resources.filter, 'DOMAIN').map((criterionValue) => criterionValue.value);
    const privilegesSelectValue = privileges;

    // Construct privilege options for dropdown
    const platformPrivileges = policiesConfig?.platformPrivileges || [];
    const resourcePrivileges = useMemo(() => policiesConfig?.resourcePrivileges || [], [policiesConfig]);
    const resourcePrivilegesForType = useMemo(
        () => mapResourceTypeToPrivileges(resourceTypeSelectValue, resourcePrivileges),
        [resourceTypeSelectValue, resourcePrivileges],
    );
    const privilegeOptions = policyType === PolicyType.Platform ? platformPrivileges : resourcePrivilegesForType;

    const getEntityFromSearchResults = (searchResults, urn) =>
        searchResults?.map((result) => result.entity).find((entity) => entity.urn === urn);

    // When a privilege is selected, add its type to the privileges list
    const onSelectPrivilege = (privilege: string) => {
        if (privilege === 'All') {
            setPrivileges(privilegeOptions.map((priv) => priv.type) as never[]);
        } else {
            const newPrivs = [...privileges, privilege];
            setPrivileges(newPrivs as never[]);
        }
    };

    // When a privilege is selected, remove its type from the privileges list
    const onDeselectPrivilege = (privilege: string) => {
        let newPrivs;
        if (privilege === 'All') {
            newPrivs = [];
        } else {
            newPrivs = privileges.filter((priv) => priv !== privilege);
        }
        setPrivileges(newPrivs as never[]);
    };

    // When a resource is selected, add its urn to the list of resources
    const onSelectResourceType = (selectedResourceType: string) => {
        const filter = resources.filter || {
            criteria: [],
        };
        setResources({
            ...resources,
            filter: setFieldValues(filter, 'RESOURCE_TYPE', [
                ...resourceTypes,
                createCriterionValue(selectedResourceType),
            ]),
        });
    };

    const onDeselectResourceType = (deselectedResourceType: string) => {
        const filter = resources.filter || {
            criteria: [],
        };
        setResources({
            ...resources,
            filter: setFieldValues(
                filter,
                'RESOURCE_TYPE',
                resourceTypes?.filter((criterionValue) => criterionValue.value !== deselectedResourceType),
            ),
        });
    };

    // When a resource is selected, add its urn to the list of resources
    const onSelectResource = (resource) => {
        const filter = resources.filter || {
            criteria: [],
        };
        setResources({
            ...resources,
            filter: setFieldValues(filter, 'RESOURCE_URN', [
                ...resourceEntities,
                createCriterionValueWithEntity(
                    resource,
                    getEntityFromSearchResults(resourceSearchResults, resource) || null,
                ),
            ]),
        });
    };

    // When a resource is deselected, remove its urn from the list of resources
    const onDeselectResource = (resource) => {
        const filter = resources.filter || {
            criteria: [],
        };
        setResources({
            ...resources,
            filter: setFieldValues(
                filter,
                'RESOURCE_URN',
                resourceEntities?.filter((criterionValue) => criterionValue.value !== resource),
            ),
        });
    };

    // When a domain is selected, add its urn to the list of domains
    const onSelectDomain = (domain) => {
        const filter = resources.filter || {
            criteria: [],
        };
        const updatedFilter = setFieldValues(filter, 'DOMAIN', [
            ...domains,
            createCriterionValueWithEntity(domain, getEntityFromSearchResults(domainSearchResults, domain) || null),
        ]);
        setResources({
            ...resources,
            filter: updatedFilter,
        });
    };

    // When a domain is deselected, remove its urn from the list of domains
    const onDeselectDomain = (domain) => {
        const filter = resources.filter || {
            criteria: [],
        };
        setResources({
            ...resources,
            filter: setFieldValues(
                filter,
                'DOMAIN',
                domains?.filter((criterionValue) => criterionValue.value !== domain),
            ),
        });
    };

    // Handle resource search, if the resource type has an associated EntityType mapping.
    const handleResourceSearch = (text: string) => {
        const trimmedText: string = text.trim();
        const entityTypes = resourceTypeSelectValue
            .map((resourceType) => mapResourceTypeToEntityType(resourceType, resourcePrivileges))
            .filter((entityType): entityType is EntityType => !!entityType);
        searchResources({
            variables: {
                input: {
                    types: entityTypes,
                    query: trimmedText.length > 2 ? trimmedText : '*',
                    start: 0,
                    count: 10,
                },
            },
        });
    };

    // Handle domain search, if the domain type has an associated EntityType mapping.
    const handleDomainSearch = (text: string) => {
        const trimmedText: string = text.trim();
        searchDomains({
            variables: {
                input: {
                    type: EntityType.Domain,
                    query: trimmedText.length > 2 ? trimmedText : '*',
                    start: 0,
                    count: 10,
                },
            },
        });
    };

    const renderSearchResult = (result) => {
        return (
            <SearchResultContainer>
                {entityRegistry.getDisplayName(result.entity.type, result.entity)}
                <Link
                    target="_blank"
                    rel="noopener noreferrer"
                    to={() => `${entityRegistry.getEntityUrl(result.entity.type, result.entity.urn)}`}
                >
                    查看
                </Link>
            </SearchResultContainer>
        );
    };

    const displayStringWithMaxLength = (displayStr, length) => {
        return displayStr.length > length
            ? `${displayStr.substring(0, Math.min(length, displayStr.length))}...`
            : displayStr;
    };

    return (
        <PrivilegesForm layout="vertical">
            {showResourceFilterInput && (
                <Form.Item label={<Typography.Text strong>资源类型</Typography.Text>} labelAlign="right">
                    <Typography.Paragraph>
                        选择该规则需要应用到的资源类型. 如果选择<b>none</b>, 则该规则会被应用到<b>所有</b>资源类型.
                    </Typography.Paragraph>
                    <Select
                        value={resourceTypeSelectValue}
                        mode="multiple"
                        placeholder="默认应用到所有资源类型.选择您要具体应用的类型.比如Dataset,Platform等"
                        onSelect={onSelectResourceType}
                        onDeselect={onDeselectResourceType}
                        tagRender={(tagProps) => (
                            <Tag closable={tagProps.closable} onClose={tagProps.onClose}>
                                {mapResourceTypeToDisplayName(tagProps.value.toString(), resourcePrivileges)}
                            </Tag>
                        )}
                    >
                        {resourcePrivileges
                            .filter((privs) => privs.resourceType !== 'all')
                            .map((resPrivs) => {
                                return (
                                    <Select.Option value={resPrivs.resourceType}>
                                        {resPrivs.resourceTypeDisplayName}
                                    </Select.Option>
                                );
                            })}
                    </Select>
                </Form.Item>
            )}
            {showResourceFilterInput && (
                <Form.Item label={<Typography.Text strong>资源</Typography.Text>}>
                    <Typography.Paragraph>
                        选择规则需要应用到的指定资源. 如果选择是 <b>none</b> 规则则会被应用到<b>所有</b>资源.
                    </Typography.Paragraph>
                    <Select
                        notFoundContent="未找到结果"
                        value={resourceSelectValue}
                        mode="multiple"
                        filterOption={false}
                        placeholder="默认应用到所有资源. 选择您要具体应用的资源. 您可以输入要查询的资源名称或者关键字."
                        onSelect={onSelectResource}
                        onDeselect={onDeselectResource}
                        onSearch={handleResourceSearch}
                        tagRender={(tagProps) => (
                            <Tag closable={tagProps.closable} onClose={tagProps.onClose}>
                                <Tooltip title={tagProps.value.toString()}>
                                    {displayStringWithMaxLength(
                                        resourceUrnToDisplayName[tagProps.value.toString()] ||
                                            tagProps.value.toString(),
                                        75,
                                    )}
                                </Tooltip>
                            </Tag>
                        )}
                    >
                        {resourceSearchResults?.map((result) => (
                            <Select.Option value={result.entity.urn}>{renderSearchResult(result)}</Select.Option>
                        ))}
                    </Select>
                </Form.Item>
            )}
            {showResourceFilterInput && (
                <Form.Item label={<Typography.Text strong>Domain</Typography.Text>}>
                    <Typography.Paragraph>
                        选择规则需要应用到的domains. 如果选择是 <b>none</b> , 规则则会被应用到<b>所有</b>domain中的资源.
                    </Typography.Paragraph>
                    <Select
                        notFoundContent="未找到结果"
                        value={domainSelectValue}
                        mode="multiple"
                        filterOption={false}
                        placeholder="默认应用到所有domains. 选择您要具体应用的 domains."
                        onSelect={onSelectDomain}
                        onDeselect={onDeselectDomain}
                        onSearch={handleDomainSearch}
                        tagRender={(tagProps) => (
                            <Tag closable={tagProps.closable} onClose={tagProps.onClose}>
                                {displayStringWithMaxLength(
                                    domainUrnToDisplayName[tagProps.value.toString()] || tagProps.value.toString(),
                                    75,
                                )}
                            </Tag>
                        )}
                    >
                        {domainSearchResults?.map((result) => (
                            <Select.Option value={result.entity.urn}>{renderSearchResult(result)}</Select.Option>
                        ))}
                    </Select>
                </Form.Item>
            )}
            <Form.Item label={<Typography.Text strong>权限集</Typography.Text>}>
                <Typography.Paragraph>选择您要授予的权限集.</Typography.Paragraph>
                <Select
                    value={privilegesSelectValue}
                    mode="multiple"
                    onSelect={(value: string) => onSelectPrivilege(value)}
                    onDeselect={(value: any) => onDeselectPrivilege(value)}
                    tagRender={(tagProps) => (
                        <Tag closable={tagProps.closable} onClose={tagProps.onClose}>
                            {tagProps.label}
                        </Tag>
                    )}
                >
                    {privilegeOptions.map((priv) => (
                        <Select.Option value={priv.type}>{priv.displayName}</Select.Option>
                    ))}
                    <Select.Option value="All">All Privileges</Select.Option>
                </Select>
            </Form.Item>
        </PrivilegesForm>
    );
}
