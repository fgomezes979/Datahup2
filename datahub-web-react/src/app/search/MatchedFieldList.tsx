import React from 'react';

import { Tooltip, Typography } from 'antd';
import styled from 'styled-components';
import { useMatchedFieldLabel, useMatchedFieldsForList } from './context/SearchResultContext';
import { MatchedField } from '../../types.generated';
import { ANTD_GRAY_V2 } from '../entity/shared/constants';
import { useSearchQuery } from './context/SearchContext';
import { MatchesGroupedByFieldName } from './context/constants';
import { useEntityRegistry } from '../useEntityRegistry';

const MatchesContainer = styled.div`
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
`;

const MatchText = styled(Typography.Text)`
    color: ${ANTD_GRAY_V2[8]};
    background: ${(props) => props.theme.styles['highlight-color']};
    border-radius: 4px;
    padding: 2px 4px 2px 4px;
    padding-right: 4px;
`;

const SURROUNDING_DESCRIPTION_CHARS = 10;
const MATCH_GROUP_LIMIT = 3;
const TOOLTIP_MATCH_GROUP_LIMIT = 10;

type CustomFieldRenderer = (field: MatchedField) => JSX.Element | null;

type Props = {
    customFieldRenderer?: CustomFieldRenderer;
};

const RenderedField = ({
    customFieldRenderer,
    field,
}: {
    customFieldRenderer?: CustomFieldRenderer;
    field: MatchedField;
}) => {
    const entityRegistry = useEntityRegistry();
    const query = useSearchQuery()?.trim().toLowerCase();
    const customRenderedField = customFieldRenderer?.(field);
    if (customRenderedField) return <b>{customRenderedField}</b>;
    if (field.value.includes('urn:li:tag') && !field.entity) return <></>;
    if (field.entity) return <>{entityRegistry.getDisplayName(field.entity.type, field.entity)}</>;
    if (field.name.toLowerCase().includes('description') && query) {
        const queryIndex = field.value.indexOf(query);
        const start = Math.max(0, queryIndex - SURROUNDING_DESCRIPTION_CHARS);
        const end = Math.min(field.value.length, queryIndex + query.length + SURROUNDING_DESCRIPTION_CHARS);
        const startEllipsis = start > 0 ? '...' : undefined;
        const endEllipsis = end < field.value.length ? '...' : undefined;
        return (
            <b>
                {startEllipsis}
                {field.value.slice(start, end)}
                {endEllipsis}
            </b>
        );
    }
    return <b>{field.value}</b>;
};

const MatchedFieldsList = ({
    groupedMatch,
    limit,
    tooltip,
    customFieldRenderer,
}: {
    groupedMatch: MatchesGroupedByFieldName;
    limit: number;
    tooltip?: JSX.Element;
    customFieldRenderer?: CustomFieldRenderer;
}) => {
    const label = useMatchedFieldLabel(groupedMatch.fieldName);
    const count = groupedMatch.matchedFields.length;
    const moreCount = Math.max(count - limit, 0);
    const andMore = (
        <>
            {' '}
            & <b>more</b>
        </>
    );
    return (
        <>
            Matches {count > 1 && `${count} `}
            {label}
            {count > 1 && 's'}{' '}
            {groupedMatch.matchedFields.slice(0, limit).map((field, index) => (
                <>
                    {index > 0 && ', '}
                    <>
                        <RenderedField field={field} customFieldRenderer={customFieldRenderer} />
                    </>
                </>
            ))}
            {moreCount > 0 &&
                (tooltip ? (
                    <Tooltip title={tooltip} placement="bottom" mouseEnterDelay={1}>
                        {andMore}
                    </Tooltip>
                ) : (
                    <>{andMore}</>
                ))}
        </>
    );
};

export const MatchedFieldList = ({ customFieldRenderer }: Props) => {
    const groupedMatches = useMatchedFieldsForList('fieldLabels');

    return (
        <>
            {groupedMatches.length > 0 ? (
                <MatchesContainer>
                    {groupedMatches.map((groupedMatch) => {
                        return (
                            <MatchText key={groupedMatch.fieldName}>
                                <MatchedFieldsList
                                    groupedMatch={groupedMatch}
                                    limit={MATCH_GROUP_LIMIT}
                                    customFieldRenderer={customFieldRenderer}
                                    tooltip={
                                        <MatchedFieldsList
                                            groupedMatch={groupedMatch}
                                            limit={TOOLTIP_MATCH_GROUP_LIMIT}
                                            customFieldRenderer={customFieldRenderer}
                                        />
                                    }
                                />
                            </MatchText>
                        );
                    })}
                </MatchesContainer>
            ) : null}
        </>
    );
};
