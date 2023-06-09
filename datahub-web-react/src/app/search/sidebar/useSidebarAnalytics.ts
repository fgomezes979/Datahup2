import { EntityType } from '../../../types.generated';
import { BrowseV2ToggleNodeEvent, EventType } from '../../analytics';
import analytics from '../../analytics/analytics';
import { useEntityRegistry } from '../../useEntityRegistry';
import {
    useBrowsePathLength,
    useEntityType,
    useMaybeEnvironmentAggregation,
    useMaybePlatformAggregation,
} from './BrowseContext';

const useSidebarAnalytics = () => {
    const registry = useEntityRegistry();
    const entityType = useEntityType();
    const entityCollectionName = registry.getCollectionName(entityType);
    const environment = useMaybeEnvironmentAggregation()?.value;
    const platformAggregation = useMaybePlatformAggregation();
    const platform = platformAggregation?.entity
        ? registry.getDisplayName(EntityType.DataPlatform, platformAggregation.entity)
        : platformAggregation?.value;
    const depth = 1 + (environment ? 1 : 0) + (platform ? 1 : 0) + useBrowsePathLength();

    const trackToggleNodeEvent = (isOpen: boolean, target: BrowseV2ToggleNodeEvent['target']) => {
        analytics.event({
            type: EventType.BrowseV2ToggleNodeEvent,
            target,
            action: isOpen ? 'open' : 'close',
            entity: entityCollectionName,
            ...(environment ? { environment } : {}),
            ...(platform ? { platform } : {}),
            depth,
        });
    };

    return { trackToggleNodeEvent } as const;
};

export default useSidebarAnalytics;
