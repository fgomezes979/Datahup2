import React, { useMemo } from 'react';
import { Popover } from 'antd';
import { Bar } from '@vx/shape';
import { Group } from '@vx/group';
import { AxisBottom } from '@vx/axis';
import { scaleUtc } from '@vx/scale';
import { ANTD_GRAY } from '../../../constants';

export type BooleanResult = {
    result: boolean;
    title: React.ReactNode;
    content: React.ReactNode;
};

export type BooleanDataPoint = {
    time: number;
    result: BooleanResult;
};

export type TimeRange = {
    startMs: number;
    endMs: number;
};

type Props = {
    data: Array<BooleanDataPoint>;
    timeRange: TimeRange;
    width: number;
};

const SUCCESS_COLOR_HEX = '#52C41A';
const FAILURE_COLOR_HEX = '#F5222D';

export const BooleanTimeline = ({ data, timeRange, width }: Props) => {
    const yMax = 72;
    const left = 0;

    // scales, memoize for performance
    const xScale = useMemo(
        () =>
            scaleUtc({
                domain: [new Date(timeRange.startMs), new Date(timeRange.endMs)],
                range: [0, width],
            }),
        [timeRange, width],
    );

    const transformedData = data.map((result, i) => {
        return {
            index: i,
            title: result.result.title,
            content: result.result.content,
            result: result.result.result,
            time: result.time,
        };
    });

    return (
        <>
            <svg width={width} height={100}>
                <Group>
                    {transformedData.map((d) => {
                        const barWidth = 8;
                        const barHeight = 18;
                        const barX = xScale(new Date(d.time));
                        const barY = yMax - barHeight;
                        const fillColor = d.result ? SUCCESS_COLOR_HEX : FAILURE_COLOR_HEX;
                        return (
                            <Popover
                                key={d.time}
                                title={d.title}
                                overlayStyle={{
                                    maxWidth: 440,
                                    wordWrap: 'break-word',
                                }}
                                content={d.content}
                            >
                                <Bar
                                    key={`bar-${d.time}`}
                                    x={barX}
                                    y={barY}
                                    stroke="white"
                                    width={barWidth}
                                    height={barHeight}
                                    fill={fillColor}
                                />
                            </Popover>
                        );
                    })}
                </Group>
                <AxisBottom
                    top={72}
                    left={left}
                    scale={xScale}
                    numTicks={7}
                    stroke={ANTD_GRAY[5]}
                    tickFormat={(v: any) => v.toLocaleDateString('en-us', { month: 'short', day: 'numeric' })}
                    tickLabelProps={(_) => ({
                        fontSize: 11,
                        angle: 0,
                        textAnchor: 'middle',
                    })}
                />
            </svg>
        </>
    );
};
