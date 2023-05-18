import React, { memo, useCallback, useEffect, useState } from 'react';
import styled from 'styled-components';
import { Typography } from 'antd';
import { DownCircleOutlined, UpCircleOutlined } from '@ant-design/icons';
import { ANTD_GRAY } from '../../entity/shared/constants';
import { AggregationMetadata, EntityType } from '../../../types.generated';
import { useEntityRegistry } from '../../useEntityRegistry';
import { IconStyleType } from '../../entity/Entity';
import { formatNumber } from '../../shared/formatNumber';
import ExpandableNode from './ExpandableNode';
import EnvironmentNode from './EnvironmentNode';
import useAggregationsQuery from './useAggregationsQuery';
import { ORIGIN_FILTER_NAME, PLATFORM_FILTER_NAME } from '../utils/constants';
import PlatformNode from './PlatformNode';

const Header = styled.div<{ isOpen: boolean }>`
    display: flex;
    align-items: center;
    justify-content: space-between;
    cursor: pointer;
    user-select: none;
    padding-top: 16px;
    border-bottom: ${(props) => `1px solid ${props.isOpen ? ANTD_GRAY[2] : ANTD_GRAY[4]}`};
`;

const HeaderLeft = styled.div`
    display: flex;
    align-items: center;
    gap: 8px;
`;

const Title = styled(Typography.Text)`
    font-size: 16px;
    color: ${(props) => props.color};
`;

const Count = styled(Typography.Text)`
    font-size: 12px;
    color: ${(props) => props.color};
`;

const Body = styled.div``;

type Props = {
    entityAggregation: AggregationMetadata;
};

const facets = [ORIGIN_FILTER_NAME, PLATFORM_FILTER_NAME];

// todo consider passing in aggregation object in here instead of more consistency with Platform
const EntityNode = ({ entityAggregation }: Props) => {
    const depth = 0;
    const registry = useEntityRegistry();
    const [isOpen, setIsOpen] = useState<boolean>(false);
    const color = isOpen ? ANTD_GRAY[9] : ANTD_GRAY[7];
    const [fetchAggregations, { loaded, error, called, environmentAggregations, platformAggregations }] =
        useAggregationsQuery();
    const entityType = entityAggregation.value as EntityType;

    const onClickHeader = useCallback(() => {
        if (!called) fetchAggregations(entityType, facets);
        setIsOpen((current) => !current);
    }, [called, entityType, fetchAggregations]);

    useEffect(() => {
        if (!isOpen) return;
        if (!called) return;
        fetchAggregations(entityType, facets);
    }, [called, entityType, fetchAggregations, isOpen]);

    const forceEnvironments = false;
    const singleEnvironment = environmentAggregations.length === 1 ? environmentAggregations[0] : null;
    const showEnvironments = environmentAggregations.length > 1 || forceEnvironments;

    return (
        <ExpandableNode
            isOpen={isOpen && loaded}
            depth={depth}
            header={
                <Header isOpen={isOpen} onClick={onClickHeader}>
                    <HeaderLeft>
                        {registry.getIcon(entityType, 16, IconStyleType.HIGHLIGHT, color)}
                        <Title color={color}>{registry.getCollectionName(entityType as EntityType)}</Title>
                        <Count color={color}>{formatNumber(entityAggregation.count)}</Count>
                    </HeaderLeft>
                    {isOpen ? <UpCircleOutlined style={{ color }} /> : <DownCircleOutlined style={{ color }} />}
                </Header>
            }
            body={
                <Body>
                    {error && <Typography.Text type="danger">There was a problem loading the sidebar.</Typography.Text>}
                    {showEnvironments
                        ? environmentAggregations.map((environmentAggregation) => (
                              <EnvironmentNode
                                  key={environmentAggregation.value}
                                  entityAggregation={entityAggregation}
                                  environmentAggregation={environmentAggregation}
                              />
                          ))
                        : platformAggregations.map((platform) => (
                              <PlatformNode
                                  key={platform.value}
                                  entityAggregation={entityAggregation}
                                  environmentAggregation={singleEnvironment}
                                  platformAggregation={platform}
                                  depth={depth + 1}
                              />
                          ))}
                </Body>
            }
        />
    );
};

export default memo(EntityNode);
