import React from 'react';
import { fireEvent, render, waitFor } from '@testing-library/react';

import { dataset3WithLineage, dataset4WithLineage, dataset5WithLineage, dataset6WithLineage } from '../../../Mocks';
import { EntityType } from '../../../types.generated';
import { Direction, FetchedEntities } from '../types';
import constructTree from '../utils/contructTree';
import LineageTree from '../LineageTree';
import { hierarchy } from '@vx/hierarchy';
import extendAsyncEntities from '../utils/extendAsyncEntities';
import { Zoom } from '@vx/zoom';

export const margin = { top: 10, left: 280, right: 280, bottom: 10 };
const [windowWidth, windowHeight] = [1000, 500];

const height = windowHeight - 125;
const width = windowWidth;
const yMax = height - margin.top - margin.bottom;
const xMax = (width - margin.left - margin.right) / 2;
const initialTransform = {
    scaleX: 2 / 3,
    scaleY: 2 / 3,
    translateX: width / 2,
    translateY: 0,
    skewX: 0,
    skewY: 0,
};

describe('LineageTree', () => {
    it('renders a tree with many layers', () => {
        const fetchedEntities = [
            { entity: dataset4WithLineage, direction: Direction.Upstream, fullyFetched: true },
            { entity: dataset5WithLineage, direction: Direction.Upstream, fullyFetched: true },
            { entity: dataset6WithLineage, direction: Direction.Upstream, fullyFetched: true },
        ];
        const mockFetchedEntities = fetchedEntities.reduce(
            (acc, entry) => extendAsyncEntities(acc, entry.entity, entry.direction, entry.fullyFetched),
            {} as FetchedEntities,
        );

        const downstreamData = hierarchy(constructTree(dataset3WithLineage, mockFetchedEntities, Direction.Upstream));

        const { getByTestId } = render(
            <Zoom
                width={width}
                height={height}
                scaleXMin={1 / 8}
                scaleXMax={2}
                scaleYMin={1 / 8}
                scaleYMax={2}
                transformMatrix={initialTransform}
            >
                {(zoom) => (
                    <svg>
                        <LineageTree
                            data={downstreamData}
                            zoom={zoom}
                            onEntityClick={jest.fn()}
                            onLineageExpand={jest.fn()}
                            canvasHeight={yMax}
                            canvasWidth={xMax}
                            margin={margin}
                            direction={Direction.Upstream}
                        />
                    </svg>
                )}
            </Zoom>,
        );

        expect(getByTestId('edge-urn:li:dataset:6-urn:li:dataset:5-Upstream'));
        expect(getByTestId('edge-urn:li:dataset:4-urn:li:dataset:6-Upstream'));
        expect(getByTestId('edge-urn:li:dataset:4-urn:li:dataset:5-Upstream'));
        expect(getByTestId('edge-urn:li:dataset:3-urn:li:dataset:4-Upstream'));

        expect(getByTestId('node-urn:li:dataset:6-Upstream'));
        expect(getByTestId('node-urn:li:dataset:5-Upstream'));
        expect(getByTestId('node-urn:li:dataset:4-Upstream'));
        expect(getByTestId('node-urn:li:dataset:3-Upstream'));
    });
});
