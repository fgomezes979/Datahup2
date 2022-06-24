import React from 'react';
import { Link } from 'react-router-dom';
import styled from 'styled-components/macro';
import { message, Button, List, Typography, Divider } from 'antd';
import { LinkOutlined, DeleteOutlined } from '@ant-design/icons';
import { EntityType } from '../../../../../../types.generated';
import { useEntityData } from '../../../EntityContext';
import { useEntityRegistry } from '../../../../../useEntityRegistry';
import { ANTD_GRAY } from '../../../constants';
import { formatDateString } from '../../../containers/profile/utils';
import { useRemoveLinkMutation } from '../../../../../../graphql/mutations.generated';

const LinkListItem = styled(List.Item)`
    border-radius: 5px;
    > .ant-btn {
        opacity: 0;
    }
    &:hover {
        background-color: ${ANTD_GRAY[2]};
        > .ant-btn {
            opacity: 1;
        }
    }
`;

const ListOffsetIcon = styled.span`
    margin-left: -18px;
    margin-right: 6px;
`;

const StyledDivider = styled(Divider)`
    margin: 0;
`;

type LinkListProps = {
    refetch?: () => Promise<any>;
};

export const LinkList = ({ refetch }: LinkListProps) => {
    const { urn, entityData } = useEntityData();
    const entityRegistry = useEntityRegistry();
    const [removeLinkMutation] = useRemoveLinkMutation();
    const links = entityData?.institutionalMemory?.elements || [];
    const sourceUrl = entityData?.properties?.sourceUrl;

    const handleDeleteLink = async (linkUrl: string) => {
        try {
            await removeLinkMutation({
                variables: { input: { linkUrl, resourceUrn: urn } },
            });
            message.success({ content: 'Link Removed', duration: 2 });
        } catch (e: unknown) {
            message.destroy();
            if (e instanceof Error) {
                message.error({ content: `Error removing link: \n ${e.message || ''}`, duration: 2 });
            }
        }
        refetch?.();
    };

    return entityData ? (
        <>
            {sourceUrl && (
                <List
                    size="large"
                    dataSource={[sourceUrl]}
                    renderItem={(url) => (
                        <LinkListItem>
                            <List.Item.Meta
                                title={
                                    <Typography.Title level={5} style={{ margin: 0 }}>
                                        <a href={url} target="_blank" rel="noreferrer">
                                            <ListOffsetIcon>
                                                <LinkOutlined />
                                            </ListOffsetIcon>
                                            Definition
                                        </a>
                                    </Typography.Title>
                                }
                            />
                        </LinkListItem>
                    )}
                />
            )}
            {sourceUrl && links.length > 0 && <StyledDivider />}
            {links.length > 0 && (
                <List
                    size="large"
                    dataSource={links}
                    renderItem={(link) => (
                        <LinkListItem
                            extra={
                                <Button onClick={() => handleDeleteLink(link.url)} type="text" shape="circle" danger>
                                    <DeleteOutlined />
                                </Button>
                            }
                        >
                            <List.Item.Meta
                                title={
                                    <Typography.Title level={5}>
                                        <a href={link.url} target="_blank" rel="noreferrer">
                                            <ListOffsetIcon>
                                                <LinkOutlined />
                                            </ListOffsetIcon>
                                            {link.description || link.label}
                                        </a>
                                    </Typography.Title>
                                }
                                description={
                                    <>
                                        Added {formatDateString(link.created.time)} by{' '}
                                        <Link
                                            to={`/${entityRegistry.getPathName(EntityType.CorpUser)}/${
                                                link.author.urn
                                            }`}
                                        >
                                            {link.author.username}
                                        </Link>
                                    </>
                                }
                            />
                        </LinkListItem>
                    )}
                />
            )}
        </>
    ) : null;
};
