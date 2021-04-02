import React, { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router';

import { Alert, Button, Card, Drawer } from 'antd';
import styled from 'styled-components';

import { useGetDatasetLazyQuery, useGetDatasetQuery } from '../../graphql/dataset.generated';
import { Message } from '../shared/Message';
import { Dataset, EntityType } from '../../types.generated';
import { useEntityRegistry } from '../useEntityRegistry';
import CompactContext from '../shared/CompactContext';
import { BrowsableEntityPage } from '../browse/BrowsableEntityPage';
import { Direction, EntitySelectParams, FetchedEntities, LineageExpandParams, LineageExplorerParams } from './types';
import getChildren from './utils/getChildren';
import LineageViz from './LineageViz';
import extendAsyncEntities from './utils/extendAsyncEntities';

const ControlCard = styled(Card)`
    position: absolute;
    box-shadow: 4px 4px 4px -1px grey;
    bottom: 20px;
    right: 20px;
`;

export default function LineageExplorer() {
    const { type, urn } = useParams<LineageExplorerParams>();
    const { loading, error, data } = useGetDatasetQuery({ variables: { urn } });
    const [getUpstreamDataset, { data: upstreamDatasetData }] = useGetDatasetLazyQuery();
    const [getDownstreamDataset, { data: downstreamDatasetData }] = useGetDatasetLazyQuery();
    const [isDrawerVisible, setIsDrawVisible] = useState(false);
    const [selectedEntity, setSelectedEntity] = useState<EntitySelectParams | undefined>(undefined);
    const entityRegistry = useEntityRegistry();
    const [asyncEntities, setAsyncEntities] = useState<FetchedEntities>({});

    const maybeAddAsyncLoadedEntity = useCallback(
        ({ entity, direction, isRoot }: { entity?: Dataset; direction: Direction | null; isRoot: boolean }) => {
            if (entity?.urn && !asyncEntities[entity?.urn]?.fullyFetched) {
                // record that we have added this entity
                let newAsyncEntities = extendAsyncEntities(asyncEntities, entity, direction, true);

                // add the partially fetched downstream & upstream datasets
                if (isRoot || direction === Direction.Downstream) {
                    getChildren(entity, Direction.Downstream).forEach((downstream) => {
                        newAsyncEntities = extendAsyncEntities(
                            newAsyncEntities,
                            downstream.dataset,
                            Direction.Downstream,
                            false,
                        );
                    });
                }
                if (isRoot || direction === Direction.Upstream) {
                    getChildren(entity, Direction.Upstream).forEach((upstream) => {
                        newAsyncEntities = extendAsyncEntities(
                            newAsyncEntities,
                            upstream.dataset,
                            Direction.Upstream,
                            false,
                        );
                    });
                }
                setAsyncEntities(newAsyncEntities);
            }
        },
        [asyncEntities, setAsyncEntities],
    );

    useEffect(() => {
        maybeAddAsyncLoadedEntity({ entity: data?.dataset as Dataset, direction: null, isRoot: true });
        maybeAddAsyncLoadedEntity({
            entity: downstreamDatasetData?.dataset as Dataset,
            direction: Direction.Downstream,
            isRoot: false,
        });
        maybeAddAsyncLoadedEntity({
            entity: upstreamDatasetData?.dataset as Dataset,
            direction: Direction.Upstream,
            isRoot: false,
        });
    }, [data, downstreamDatasetData, upstreamDatasetData, asyncEntities, setAsyncEntities, maybeAddAsyncLoadedEntity]);

    if (error || (!loading && !error && !data)) {
        return <Alert type="error" message={error?.message || 'Entity failed to load'} />;
    }

    return (
        <BrowsableEntityPage urn={urn} type={data?.dataset?.type as EntityType}>
            {loading && <Message type="loading" content="Loading..." style={{ marginTop: '10%' }} />}
            {data?.dataset && (
                <div>
                    <LineageViz
                        selectedEntity={selectedEntity}
                        fetchedEntities={asyncEntities}
                        dataset={data?.dataset}
                        onEntityClick={(params: EntitySelectParams) => {
                            setIsDrawVisible(true);
                            setSelectedEntity(params);
                        }}
                        onLineageExpand={(params: LineageExpandParams) => {
                            if (params.direction === Direction.Upstream) {
                                getUpstreamDataset({ variables: { urn: params.urn } });
                            } else {
                                getDownstreamDataset({ variables: { urn: params.urn } });
                            }
                        }}
                    />
                </div>
            )}
            <ControlCard size="small">
                <Button href={`/${type}/${urn}`} type="link">
                    Return to {type}
                </Button>
            </ControlCard>
            <Drawer
                title="Entity Overview"
                placement="left"
                closable
                onClose={() => {
                    setIsDrawVisible(false);
                    setSelectedEntity(undefined);
                }}
                visible={isDrawerVisible}
                width={425}
                mask={false}
            >
                <CompactContext.Provider value>
                    {selectedEntity && entityRegistry.renderProfile(selectedEntity.type, selectedEntity.urn)}
                </CompactContext.Provider>
            </Drawer>
        </BrowsableEntityPage>
    );
}
