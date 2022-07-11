import React, { useContext } from 'react';
import { EntityType } from '../../../types.generated';
import { useIsHideSiblingMode } from './siblingUtils';
import { EntityContextType, UpdateEntityType } from './types';

const EntityContext = React.createContext<EntityContextType>({
    urn: '',
    entityType: EntityType.Dataset,
    entityData: null,
    baseEntity: null,
    updateEntity: () => Promise.resolve({}),
    routeToTab: () => {},
    refetch: () => Promise.resolve({}),
    lineage: undefined,
    dataNotCombinedWithSiblings: null,
});

export default EntityContext;

export const useBaseEntity = <T,>(): T => {
    const { baseEntity } = useContext(EntityContext);
    return baseEntity as T;
};

export const useDataNotCombinedWithSiblings = <T,>(): T => {
    const { dataNotCombinedWithSiblings } = useContext(EntityContext);
    return dataNotCombinedWithSiblings as T;
};

export const useEntityUpdate = <U,>(): UpdateEntityType<U> | null | undefined => {
    const { updateEntity } = useContext(EntityContext);
    return updateEntity;
};

export const useEntityData = () => {
    const { urn, entityType, entityData } = useContext(EntityContext);
    return { urn, entityType, entityData };
};

export const useRouteToTab = () => {
    const { routeToTab } = useContext(EntityContext);
    return routeToTab;
};

export const useRefetch = () => {
    const { refetch } = useContext(EntityContext);
    return refetch;
};

export const useLineageData = () => {
    const { lineage } = useContext(EntityContext);
    return lineage;
};

export const useMutationUrn = () => {
    const { urn, entityData } = useContext(EntityContext);
    const isHideSiblingMode = useIsHideSiblingMode();
    if (!entityData?.siblings || entityData?.siblings?.isPrimary || isHideSiblingMode) {
        return urn;
    }
    return entityData?.siblings?.siblings?.[0]?.urn || urn;
};
