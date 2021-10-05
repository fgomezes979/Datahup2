import EntityRegistry from '../../entity/EntityRegistry';
import { Direction, EntityAndType, FetchedEntities, NodeData } from '../types';
import constructFetchedNode from './constructFetchedNode';

export default function constructTree(
    entityAndType: EntityAndType | null | undefined,
    fetchedEntities: FetchedEntities,
    direction: Direction,
    entityRegistry: EntityRegistry,
): NodeData {
    console.log('construct tree', entityAndType);
    if (!entityAndType?.entity) return { name: 'loading...', children: [] };
    const constructedNodes = {};

    const fetchedEntity = entityRegistry.getLineageVizConfig(entityAndType.type, entityAndType.entity);

    const root: NodeData = {
        name: fetchedEntity?.name || '',
        urn: fetchedEntity?.urn,
        type: fetchedEntity?.type,
        subtype: fetchedEntity?.subtype,
        icon: fetchedEntity?.icon,
        platform: fetchedEntity?.platform,
        unexploredChildren: 0,
    };
    const lineageConfig = entityRegistry.getLineageVizConfig(entityAndType.type, entityAndType.entity);
    let children: EntityAndType[] = [];
    if (direction === Direction.Upstream) {
        children = lineageConfig?.upstreamChildren || [];
    }
    if (direction === Direction.Downstream) {
        children = lineageConfig?.downstreamChildren || [];
    }

    root.children = children
        .map((child) => {
            console.log('for child', child.entity.urn);
            if (child.entity.urn === root.urn) {
                return null;
            }
            return constructFetchedNode(child.entity.urn, fetchedEntities, direction, constructedNodes, [
                root.urn || '',
            ]);
        })
        ?.filter(Boolean) as Array<NodeData>;
    console.log('done constructing tree');
    return root;
}
