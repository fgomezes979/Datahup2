import React, { useContext, useEffect, useMemo, useState } from 'react';
import { Group } from '@vx/group';
import { LinkHorizontal } from '@vx/shape';
import styled from 'styled-components';

import { useEntityRegistry } from '../useEntityRegistry';
import { IconStyleType } from '../entity/Entity';
import { NodeData, Direction, VizNode, EntitySelectParams, EntityAndType } from './types';
import { ANTD_GRAY } from '../entity/shared/constants';
import { capitalizeFirstLetter } from '../shared/textUtil';
import { nodeHeightFromTitleLength } from './utils/nodeHeightFromTitleLength';
import { LineageExplorerContext } from './utils/LineageExplorerContext';
import { useGetEntityLineageLazyQuery } from '../../graphql/lineage.generated';
import { useIsSeparateSiblingsMode } from '../entity/shared/siblingUtils';

const CLICK_DELAY_THRESHOLD = 1000;
const DRAG_DISTANCE_THRESHOLD = 20;

function truncate(input, length) {
    if (!input) return '';
    if (input.length > length) {
        return `${input.substring(0, length)}...`;
    }
    return input;
}

function getLastTokenOfTitle(title?: string, delimiter?: string): string {
    if (!title) return '';

    const lastToken = title?.split(delimiter || '.').slice(-1)[0];

    // if the last token does not contain any content, the string should not be tokenized on `.`
    if (lastToken.replace(/\s/g, '').length === 0) {
        return title;
    }

    return lastToken;
}

export const width = 212;
export const height = 80;
const iconWidth = 32;
const iconHeight = 32;
const iconX = -width / 2 + 22;
const iconY = -iconHeight / 2;
const centerX = -width / 2;
const centerY = -height / 2;
const textX = iconX + iconWidth + 8;

const PointerGroup = styled(Group)`
    cursor: pointer;
`;

const UnselectableText = styled.text`
    user-select: none;
`;

const MultilineTitleText = styled.p`
    margin-top: -2px;
    font-size: 14px;
    width: 125px;
    word-break: break-all;
`;

export default function LineageEntityNode({
    node,
    isSelected,
    isHovered,
    onEntityClick,
    onEntityCenter,
    onHover,
    onDrag,
    onExpandClick,
    direction,
    isCenterNode,
    nodesToRenderByUrn,
}: {
    node: { x: number; y: number; data: Omit<NodeData, 'children'> };
    isSelected: boolean;
    isHovered: boolean;
    isCenterNode: boolean;
    onEntityClick: (EntitySelectParams) => void;
    onEntityCenter: (EntitySelectParams) => void;
    onHover: (EntitySelectParams) => void;
    onDrag: (params: EntitySelectParams, event: React.MouseEvent) => void;
    onExpandClick: (data: EntityAndType) => void;
    direction: Direction;
    nodesToRenderByUrn: Record<string, VizNode>;
}) {
    const { expandTitles, expandedNodes, setExpandedNodes, showColumns, hoveredField, setHoveredField } =
        useContext(LineageExplorerContext);
    const [isExpanding, setIsExpanding] = useState(false);
    const [expandHover, setExpandHover] = useState(false);
    const [getAsyncEntityLineage, { data: asyncLineageData }] = useGetEntityLineageLazyQuery();
    const isHideSiblingMode = useIsSeparateSiblingsMode();
    const areColumnsExpanded = !!expandedNodes[node?.data?.urn || 'noop'];

    useEffect(() => {
        if (asyncLineageData && asyncLineageData.entity) {
            const entityAndType = {
                type: asyncLineageData.entity.type,
                entity: { ...asyncLineageData.entity },
            } as EntityAndType;
            onExpandClick(entityAndType);
        }
    }, [asyncLineageData, onExpandClick]);

    const entityRegistry = useEntityRegistry();
    const unexploredHiddenChildren =
        node?.data?.countercurrentChildrenUrns?.filter((urn) => !(urn in nodesToRenderByUrn))?.length || 0;

    // we need to track lastMouseDownCoordinates to differentiate between clicks and drags. It doesn't use useState because
    // it shouldn't trigger re-renders
    const lastMouseDownCoordinates = useMemo(
        () => ({
            ts: 0,
            x: 0,
            y: 0,
        }),
        [],
    );

    let platformDisplayText = capitalizeFirstLetter(
        node.data.platform?.properties?.displayName || node.data.platform?.name,
    );
    if (node.data.siblingPlatforms && !isHideSiblingMode) {
        platformDisplayText = node.data.siblingPlatforms
            .map((platform) => platform.properties?.displayName || platform.name)
            .join(' & ');
    }

    const nodeHeight = nodeHeightFromTitleLength(
        expandTitles ? node.data.expandedName || node.data.name : undefined,
        node.data.schemaMetadata,
        showColumns,
        areColumnsExpanded,
    );

    return (
        <PointerGroup data-testid={`node-${node.data.urn}-${direction}`} top={node.x} left={node.y}>
            {unexploredHiddenChildren && (isHovered || isSelected) ? (
                <Group>
                    {[...Array(unexploredHiddenChildren)].map((_, index) => {
                        const link = {
                            source: {
                                x: 0,
                                y: direction === Direction.Upstream ? 70 : -70,
                            },
                            target: {
                                x: (0.5 / (index + 1)) * 80 * (index % 2 === 0 ? 1 : -1),
                                y: direction === Direction.Upstream ? 150 : -150,
                            },
                        };
                        return (
                            <LinkHorizontal
                                // eslint-disable-next-line  react/no-array-index-key
                                key={`link-${index}-${direction}`}
                                data={link}
                                stroke={`url(#gradient-${direction})`}
                                strokeWidth="1"
                                fill="none"
                            />
                        );
                    })}
                </Group>
            ) : null}
            {node.data.unexploredChildren &&
                (!isExpanding ? (
                    <Group
                        onClick={() => {
                            setIsExpanding(true);
                            if (node.data.urn && node.data.type) {
                                // getAsyncEntity(node.data.urn, node.data.type);
                                getAsyncEntityLineage({
                                    variables: { urn: node.data.urn, separateSiblings: isHideSiblingMode },
                                });
                            }
                        }}
                        onMouseOver={() => {
                            setExpandHover(true);
                        }}
                        onMouseOut={() => {
                            setExpandHover(false);
                        }}
                        pointerEvents="bounding-box"
                    >
                        <circle
                            fill="none"
                            cy={centerY + nodeHeight / 2}
                            cx={direction === Direction.Upstream ? centerX - 10 : centerX + width + 10}
                            r="20"
                        />
                        <circle
                            fill="none"
                            cy={centerY + nodeHeight / 2}
                            cx={direction === Direction.Upstream ? centerX - 30 : centerX + width + 30}
                            r="30"
                        />
                        <g
                            fill={expandHover ? ANTD_GRAY[5] : ANTD_GRAY[6]}
                            transform={`translate(${
                                direction === Direction.Upstream ? centerX - 52 : width / 2 + 10
                            } -21.5) scale(0.04 0.04)`}
                        >
                            <path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm192 472c0 4.4-3.6 8-8 8H544v152c0 4.4-3.6 8-8 8h-48c-4.4 0-8-3.6-8-8V544H328c-4.4 0-8-3.6-8-8v-48c0-4.4 3.6-8 8-8h152V328c0-4.4 3.6-8 8-8h48c4.4 0 8 3.6 8 8v152h152c4.4 0 8 3.6 8 8v48z" />
                        </g>
                    </Group>
                ) : (
                    <g
                        fill={ANTD_GRAY[6]}
                        transform={`translate(${
                            direction === Direction.Upstream ? centerX - 52 : width / 2 + 10
                        } -21.5) scale(0.04 0.04)`}
                    >
                        <path
                            className="lineageExpandLoading"
                            d="M512 1024c-69.1 0-136.2-13.5-199.3-40.2C251.7 958 197 921 150 874c-47-47-84-101.7-109.8-162.7C13.5 648.2 0 581.1 0 512c0-19.9 16.1-36 36-36s36 16.1 36 36c0 59.4 11.6 117 34.6 171.3 22.2 52.4 53.9 99.5 94.3 139.9 40.4 40.4 87.5 72.2 139.9 94.3C395 940.4 452.6 952 512 952c59.4 0 117-11.6 171.3-34.6 52.4-22.2 99.5-53.9 139.9-94.3 40.4-40.4 72.2-87.5 94.3-139.9C940.4 629 952 571.4 952 512c0-59.4-11.6-117-34.6-171.3a440.45 440.45 0 00-94.3-139.9 437.71 437.71 0 00-139.9-94.3C629 83.6 571.4 72 512 72c-19.9 0-36-16.1-36-36s16.1-36 36-36c69.1 0 136.2 13.5 199.3 40.2C772.3 66 827 103 874 150c47 47 83.9 101.8 109.7 162.7 26.7 63.1 40.2 130.2 40.2 199.3s-13.5 136.2-40.2 199.3C958 772.3 921 827 874 874c-47 47-101.8 83.9-162.7 109.7-63.1 26.8-130.2 40.3-199.3 40.3z"
                        />
                    </g>
                ))}
            <Group
                onDoubleClick={() => onEntityCenter({ urn: node.data.urn, type: node.data.type })}
                onClick={(event) => {
                    if (
                        event.timeStamp < lastMouseDownCoordinates.ts + CLICK_DELAY_THRESHOLD &&
                        Math.sqrt(
                            (event.clientX - lastMouseDownCoordinates.x) ** 2 +
                                (event.clientY - lastMouseDownCoordinates.y) ** 2,
                        ) < DRAG_DISTANCE_THRESHOLD
                    ) {
                        onEntityClick({ urn: node.data.urn, type: node.data.type });
                    }
                }}
                onMouseOver={() => {
                    onHover({ urn: node.data.urn, type: node.data.type });
                }}
                onMouseOut={() => {
                    onHover(undefined);
                }}
                onMouseDown={(event) => {
                    lastMouseDownCoordinates.ts = event.timeStamp;
                    lastMouseDownCoordinates.x = event.clientX;
                    lastMouseDownCoordinates.y = event.clientY;
                    if (node.data.urn && node.data.type) {
                        onDrag({ urn: node.data.urn, type: node.data.type }, event);
                    }
                }}
            >
                <rect
                    height={nodeHeight}
                    width={width}
                    y={centerY}
                    x={centerX}
                    fill="white"
                    stroke={
                        // eslint-disable-next-line no-nested-ternary
                        isSelected ? '#1890FF' : isHovered ? '#1890FF' : 'rgba(192, 190, 190, 0.25)'
                    }
                    strokeWidth={isCenterNode ? 2 : 1}
                    strokeOpacity={1}
                    rx={5}
                    // eslint-disable-next-line react/style-prop-object
                    style={{ filter: isSelected ? 'url(#shadow1-selected)' : 'url(#shadow1)' }}
                />
                {node.data.siblingPlatforms && !isHideSiblingMode && (
                    <svg x={iconX} y={iconY - 5}>
                        <image
                            // preserveAspectRatio="none"
                            y={0}
                            height={iconHeight * (3 / 4)}
                            width={iconWidth * (3 / 4)}
                            href={node.data.siblingPlatforms[0]?.properties?.logoUrl || ''}
                            clipPath="url(#clipPolygonTop)"
                        />
                        <image
                            // preserveAspectRatio="none"
                            y={25}
                            height={iconHeight * (3 / 4)}
                            width={iconWidth * (3 / 4)}
                            clipPath="url(#clipPolygon)"
                            href={node.data.siblingPlatforms[1]?.properties?.logoUrl || ''}
                        />
                    </svg>
                )}
                {(!node.data.siblingPlatforms || isHideSiblingMode) && node.data.icon && (
                    <image href={node.data.icon} height={iconHeight} width={iconWidth} x={iconX} y={iconY} />
                )}
                {!node.data.icon && (!node.data.siblingPlatforms || isHideSiblingMode) && node.data.type && (
                    <svg
                        viewBox="64 64 896 896"
                        focusable="false"
                        x={iconX}
                        y={iconY}
                        height={iconHeight}
                        width={iconWidth}
                        fill="currentColor"
                        aria-hidden="true"
                    >
                        {entityRegistry.getIcon(node.data.type, 16, IconStyleType.SVG)}
                    </svg>
                )}
                <Group>
                    <UnselectableText
                        dy="-1em"
                        x={textX}
                        fontSize={8}
                        fontFamily="Manrope"
                        fontWeight="bold"
                        textAnchor="start"
                        fill="#8C8C8C"
                    >
                        <tspan>{truncate(platformDisplayText, 16)}</tspan>
                        <tspan dx=".25em" dy="2px" fill="#dadada" fontSize={12} fontWeight="normal">
                            {' '}
                            |{' '}
                        </tspan>
                        <tspan dx=".25em" dy="-2px">
                            {capitalizeFirstLetter(
                                node.data.subtype || (node.data.type && entityRegistry.getEntityName(node.data.type)),
                            )}
                        </tspan>
                    </UnselectableText>
                    {expandTitles ? (
                        <foreignObject x={textX} width="125" height="200">
                            <MultilineTitleText>{node.data.expandedName || node.data.name}</MultilineTitleText>
                        </foreignObject>
                    ) : (
                        <UnselectableText
                            dy="1em"
                            x={textX}
                            fontSize={14}
                            fontFamily="Manrope"
                            textAnchor="start"
                            fill={isCenterNode ? '#1890FF' : 'black'}
                        >
                            {truncate(
                                getLastTokenOfTitle(
                                    node.data.name,
                                    node.data.platform?.properties?.datasetNameDelimiter,
                                ),
                                16,
                            )}
                        </UnselectableText>
                    )}
                </Group>
                {unexploredHiddenChildren && isHovered ? (
                    <UnselectableText
                        dy=".33em"
                        dx={textX}
                        fontSize={16}
                        fontFamily="Arial"
                        textAnchor="middle"
                        fill="black"
                        y={centerY - 20}
                    >
                        {unexploredHiddenChildren} hidden {direction === Direction.Upstream ? 'downstream' : 'upstream'}{' '}
                        {unexploredHiddenChildren > 1 ? 'dependencies' : 'dependency'}
                    </UnselectableText>
                ) : null}
                {showColumns && node.data.schemaMetadata && !areColumnsExpanded && (
                    <Group>
                        <Group>
                            <rect
                                x={iconX}
                                y={centerY + 70}
                                width="170"
                                height="20"
                                fill="white"
                                stroke="black"
                                strokeWidth="1"
                                ry="10"
                                rx="10"
                            />
                            <UnselectableText
                                dy=".33em"
                                x={iconX + 20}
                                y={centerY + 70 + 10}
                                fontSize={12}
                                fontFamily="Arial"
                                fill="black"
                            >
                                {node.data.schemaMetadata.fields[0].fieldPath}
                            </UnselectableText>
                        </Group>
                        <Group
                            onClick={(e) => {
                                const newExpandedNodes = { ...expandedNodes, [node?.data?.urn || 'noop']: true };
                                setExpandedNodes(newExpandedNodes);
                                e.stopPropagation();
                            }}
                        >
                            <rect
                                x={iconX}
                                y={centerY + 100}
                                width="170"
                                height="20"
                                fill="white"
                                stroke="black"
                                strokeWidth="1"
                                ry="10"
                                rx="10"
                            />
                            <UnselectableText
                                dy=".33em"
                                x={iconX + 20}
                                y={centerY + 100 + 10}
                                fontSize={12}
                                fontFamily="Arial"
                                fill="#1890FF"
                            >
                                More +
                            </UnselectableText>
                        </Group>
                    </Group>
                )}
                {showColumns && node.data.schemaMetadata && areColumnsExpanded && (
                    <Group>
                        {node.data.schemaMetadata.fields.map((field, idx) => (
                            <Group
                                onMouseOver={() => {
                                    setHoveredField({ urn: node?.data?.urn, path: field.fieldPath });
                                }}
                                onMouseOut={() => {
                                    setHoveredField(null);
                                }}
                            >
                                <rect
                                    x={iconX}
                                    y={centerY + 70 + idx * 30}
                                    width="170"
                                    height="20"
                                    fill="white"
                                    stroke="black"
                                    strokeWidth="1"
                                    ry="10"
                                    rx="10"
                                />
                                <UnselectableText
                                    dy=".33em"
                                    x={iconX + 20}
                                    y={centerY + 70 + 10 + idx * 30}
                                    fontSize={12}
                                    fontFamily="Arial"
                                    fill={
                                        hoveredField?.urn === node?.data?.urn && hoveredField?.path === field.fieldPath
                                            ? '#1890FF'
                                            : 'black'
                                    }
                                >
                                    {field.fieldPath}
                                </UnselectableText>
                            </Group>
                        ))}
                        <Group
                            onClick={(e) => {
                                const newExpandedNodes = { ...expandedNodes };
                                delete newExpandedNodes[node.data.urn || 'noop'];
                                setExpandedNodes(newExpandedNodes);
                                e.stopPropagation();
                            }}
                        >
                            <rect
                                x={iconX}
                                y={centerY + 70 + node.data.schemaMetadata.fields.length * 30}
                                width="170"
                                height="20"
                                fill="white"
                                stroke="black"
                                strokeWidth="1"
                                ry="10"
                                rx="10"
                            />
                            <UnselectableText
                                dy=".33em"
                                x={iconX + 20}
                                y={centerY + 80 + node.data.schemaMetadata.fields.length * 30}
                                fontSize={12}
                                fontFamily="Arial"
                                fill="#1890FF"
                            >
                                Less -
                            </UnselectableText>
                        </Group>
                    </Group>
                )}
            </Group>
        </PointerGroup>
    );
}
