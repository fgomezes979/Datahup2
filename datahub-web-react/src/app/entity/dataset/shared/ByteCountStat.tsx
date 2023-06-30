import React from 'react';
import { HddOutlined } from '@ant-design/icons';
import { formatBytes, formatNumberWithoutAbbreviation } from '../../../shared/formatNumber';
import ExpandingStat from './ExpandingStat';
import { needsFormatting } from '../../../../utils/formatter';
import StatText from './StatText';

type Props = {
    color: string;
    disabled: boolean;
    bytes?: number | null;
};

export const ByteCountStat = ({ color, disabled, bytes }: Props) => {
    if (!bytes) return null;

    const formattedBytes = formatBytes(bytes);

    return (
        <ExpandingStat
            disabled={disabled || !needsFormatting(bytes)}
            render={(isExpanded) => (
                <StatText color={color}>
                    <HddOutlined style={{ marginRight: 8, color }} />
                    {isExpanded ? (
                        <>
                            <b>{formatNumberWithoutAbbreviation(bytes)}</b> Bytes
                        </>
                    ) : (
                        <>
                            <b>{formattedBytes.number}</b> {formattedBytes.unit}
                        </>
                    )}
                </StatText>
            )}
        />
    );
};

export default ByteCountStat;
