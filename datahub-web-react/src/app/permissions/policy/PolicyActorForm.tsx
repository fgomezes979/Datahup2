import React from 'react';
import { Form, Select, Switch, Tag, Typography } from 'antd';
import styled from 'styled-components';
import { Maybe } from 'graphql/jsutils/Maybe';

import { useEntityRegistry } from '../../useEntityRegistry';
import { ActorFilter, CorpUser, EntityType, PolicyType, SearchResult } from '../../../types.generated';
import { useGetSearchResultsLazyQuery } from '../../../graphql/search.generated';
import { useListOwnershipTypesQuery } from '../../../graphql/ownership.generated';
import { CustomAvatar } from '../../shared/avatar';

type Props = {
    policyType: PolicyType;
    actors: ActorFilter;
    setActors: (actors: ActorFilter) => void;
};

const SearchResultContainer = styled.div`
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 2px;
`;

const ActorForm = styled(Form)`
    margin: 12px;
    margin-top: 36px;
    margin-bottom: 40px;
`;

const ActorFormHeader = styled.div`
    margin-bottom: 28px;
`;

const SearchResultContent = styled.div`
    display: flex;
    justify-content: center;
    align-items: center;
`;

const OwnershipWrapper = styled.div`
    margin-top: 12px;
`;

/**
 * Component used to construct the "actors" portion of a DataHub
 * access Policy by populating an ActorFilter object.
 */
export default function PolicyActorForm({ policyType, actors, setActors }: Props) {
    const entityRegistry = useEntityRegistry();

    // Search for actors while building policy.
    const [userSearch, { data: userSearchData }] = useGetSearchResultsLazyQuery();
    const [groupSearch, { data: groupSearchData }] = useGetSearchResultsLazyQuery();
    const { data: ownershipData } = useListOwnershipTypesQuery({
        variables: {
            input: {},
        },
    });
    const ownershipTypes =
        ownershipData?.listOwnershipTypes?.ownershipTypes.filter((type) => type.urn !== 'urn:li:ownershipType:none') ||
        [];
    const ownershipTypesMap = Object.fromEntries(ownershipTypes.map((type) => [type.urn, type.info?.name]));
    // Toggle the "Owners" switch
    const onToggleAppliesToOwners = (value: boolean) => {
        setActors({
            ...actors,
            resourceOwners: value,
            resourceOwnersTypes: value ? actors.resourceOwnersTypes : null,
        });
    };

    const onSelectOwnershipTypeActor = (newType: string) => {
        const newResourceOwnersTypes: Maybe<string[]> = [...(actors.resourceOwnersTypes || []), newType];
        setActors({
            ...actors,
            resourceOwnersTypes: newResourceOwnersTypes,
        });
    };

    const onDeselectOwnershipTypeActor = (type: string) => {
        const newResourceOwnersTypes: Maybe<string[]> = actors.resourceOwnersTypes?.filter((u: string) => u !== type);
        setActors({
            ...actors,
            resourceOwnersTypes: newResourceOwnersTypes?.length ? newResourceOwnersTypes : null,
        });
    };

    // When a user search result is selected, add the urn to the ActorFilter
    const onSelectUserActor = (newUser: string) => {
        if (newUser === 'All') {
            setActors({
                ...actors,
                allUsers: true,
            });
        } else {
            const newUserActors = [...(actors.users || []), newUser];
            setActors({
                ...actors,
                users: newUserActors,
            });
        }
    };

    // When a user search result is deselected, remove the urn from the ActorFilter
    const onDeselectUserActor = (user: string) => {
        if (user === 'All') {
            setActors({
                ...actors,
                allUsers: false,
            });
        } else {
            const newUserActors = actors.users?.filter((u) => u !== user);
            setActors({
                ...actors,
                users: newUserActors,
            });
        }
    };

    // When a group search result is selected, add the urn to the ActorFilter
    const onSelectGroupActor = (newGroup: string) => {
        if (newGroup === 'All') {
            setActors({
                ...actors,
                allGroups: true,
            });
        } else {
            const newGroupActors = [...(actors.groups || []), newGroup];
            setActors({
                ...actors,
                groups: newGroupActors,
            });
        }
    };

    // When a group search result is deselected, remove the urn from the ActorFilter
    const onDeselectGroupActor = (group: string) => {
        if (group === 'All') {
            setActors({
                ...actors,
                allGroups: false,
            });
        } else {
            const newGroupActors = actors.groups?.filter((g) => g !== group);
            setActors({
                ...actors,
                groups: newGroupActors,
            });
        }
    };

    // Invokes the search API as the user types
    const handleSearch = (type: EntityType, text: string, searchQuery: any) => {
        searchQuery({
            variables: {
                input: {
                    type,
                    query: text,
                    start: 0,
                    count: 10,
                },
            },
        });
    };

    // Invokes the user search API as the user types
    const handleUserSearch = (text: string) => {
        return handleSearch(EntityType.CorpUser, text, userSearch);
    };

    // Invokes the group search API as the user types
    const handleGroupSearch = (text: string) => {
        return handleSearch(EntityType.CorpGroup, text, groupSearch);
    };

    // Renders a search result in the select dropdown.
    const renderSearchResult = (result: SearchResult) => {
        const avatarUrl =
            result.entity.type === EntityType.CorpUser
                ? (result.entity as CorpUser).editableProperties?.pictureLink || undefined
                : undefined;
        const displayName = entityRegistry.getDisplayName(result.entity.type, result.entity);
        return (
            <SearchResultContainer>
                <SearchResultContent>
                    <CustomAvatar
                        size={24}
                        name={displayName}
                        photoUrl={avatarUrl}
                        isGroup={result.entity.type === EntityType.CorpGroup}
                    />
                    <div>{displayName}</div>
                </SearchResultContent>
            </SearchResultContainer>
        );
    };

    // Whether to show "owners" switch.
    const showAppliesToOwners = policyType === PolicyType.Metadata;

    // User and group dropdown search results!
    const userSearchResults = userSearchData?.search?.searchResults;
    const groupSearchResults = groupSearchData?.search?.searchResults;

    // Select dropdown values.
    const usersSelectValue = actors.allUsers ? ['All'] : actors.users || [];
    const groupsSelectValue = actors.allGroups ? ['All'] : actors.groups || [];
    const ownershipTypesSelectValue = actors.resourceOwnersTypes || [];

    const tagRender = (props) => {
        // eslint-disable-next-line react/prop-types
        const { label, closable, onClose, value } = props;
        const onPreventMouseDown = (event) => {
            event.preventDefault();
            event.stopPropagation();
        };
        return (
            <Tag
                onMouseDown={onPreventMouseDown}
                closable={closable}
                onClose={onClose}
                style={{
                    padding: value === 'All' ? '0px 7px 0px 7px' : '0px 7px 0px 0px',
                    marginRight: 3,
                    display: 'flex',
                    justifyContent: 'start',
                    alignItems: 'center',
                }}
            >
                {label}
            </Tag>
        );
    };

    return (
        <ActorForm layout="vertical">
            <ActorFormHeader>
                <Typography.Title level={4}>应用到</Typography.Title>
                <Typography.Paragraph>选择规则需要应用到的用户或者用户组.</Typography.Paragraph>
            </ActorFormHeader>
            {showAppliesToOwners && (
                <Form.Item label={<Typography.Text strong>所有者</Typography.Text>} labelAlign="right">
                    <Typography.Paragraph>
                        Whether this policy should be apply to owners of the Metadata asset. If true, those who are
                        marked as owners of a Metadata Asset, either directly or indirectly via a Group, will have the
                        selected privileges.
                    </Typography.Paragraph>
                    <Switch size="small" checked={actors.resourceOwners} onChange={onToggleAppliesToOwners} />
                    {actors.resourceOwners && (
                        <OwnershipWrapper>
                            <Typography.Paragraph>
                                List of types of ownership which will be used to match owners. If empty, the policies
                                will applied to any type of ownership.
                            </Typography.Paragraph>
                            <Select
                                value={ownershipTypesSelectValue}
                                mode="multiple"
                                placeholder="所有者类型"
                                onSelect={(asset: any) => onSelectOwnershipTypeActor(asset)}
                                onDeselect={(asset: any) => onDeselectOwnershipTypeActor(asset)}
                                tagRender={(tagProps) => {
                                    return (
                                        <Tag closable={tagProps.closable} onClose={tagProps.onClose}>
                                            {ownershipTypesMap[tagProps.value.toString()]}
                                        </Tag>
                                    );
                                }}
                            >
                                {ownershipTypes.map((resOwnershipType) => {
                                    return (
                                        <Select.Option value={resOwnershipType.urn}>
                                            {resOwnershipType?.info?.name}
                                        </Select.Option>
                                    );
                                })}
                            </Select>
                        </OwnershipWrapper>
                    )}
                </Form.Item>
            )}
            <Form.Item label={<Typography.Text strong>用户</Typography.Text>}>
                <Typography.Paragraph>
                    选择指定的用户来应用规则，如果选择 `所有用户`，那么该规则会应用到所有用户.
                </Typography.Paragraph>
                <Select
                    value={usersSelectValue}
                    mode="multiple"
                    filterOption={false}
                    placeholder="查询用户..."
                    onSelect={(asset: any) => onSelectUserActor(asset)}
                    onDeselect={(asset: any) => onDeselectUserActor(asset)}
                    onSearch={handleUserSearch}
                    tagRender={tagRender}
                >
                    {userSearchResults?.map((result) => (
                        <Select.Option value={result.entity.urn}>{renderSearchResult(result)}</Select.Option>
                    ))}
                    <Select.Option value="All">所有用户</Select.Option>
                </Select>
            </Form.Item>
            <Form.Item label={<Typography.Text strong>用户组</Typography.Text>}>
                <Typography.Paragraph>
                    选择指定的用户组来应用规则，如果选择 `所有用户组`，那么该规则会应用到所有用户组.
                </Typography.Paragraph>
                <Select
                    value={groupsSelectValue}
                    mode="multiple"
                    placeholder="查询用户组..."
                    onSelect={(asset: any) => onSelectGroupActor(asset)}
                    onDeselect={(asset: any) => onDeselectGroupActor(asset)}
                    onSearch={handleGroupSearch}
                    filterOption={false}
                    tagRender={tagRender}
                >
                    {groupSearchResults?.map((result) => (
                        <Select.Option value={result.entity.urn}>{renderSearchResult(result)}</Select.Option>
                    ))}
                    <Select.Option value="All">所有用户组</Select.Option>
                </Select>
            </Form.Item>
        </ActorForm>
    );
}
