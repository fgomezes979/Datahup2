import React from 'react';
import styled from 'styled-components';
import { ANTD_GRAY } from '../../constants';

type Props = {
    stats: Array<React.ReactNode>;
};

const StatsContainer = styled.div`
    overflow: hidden;
    margin-top: 8px;
`;

const StatsListContainer = styled.div`
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    margin-left: -10px;
`;

const StatContainer = styled.div`
    /* Flex needed so the child stats can animate */
    display: flex;
    padding-left: 10px;
    border-left: 1px solid ${ANTD_GRAY[4]};
`;

export const StatsSummary = ({ stats }: Props) => {
    return (
        <StatsContainer>
            {!!stats.length && (
                <StatsListContainer>
                    {stats.map((statView) => (
                        <StatContainer>{statView}</StatContainer>
                    ))}
                </StatsListContainer>
            )}
        </StatsContainer>
    );
};
