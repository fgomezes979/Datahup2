import { Divider } from 'antd';
import React from 'react';
import styled from 'styled-components';
import { FacetFilterInput, FacetMetadata } from '../../../types.generated';
import { ORIGIN_FILTER_NAME, UnionType } from '../utils/constants';
import ActiveFilter from './ActiveFilter';
import { SORTED_FILTERS } from './constants';
import MoreFilters from './MoreFilters';
import SaveViewButton from './SaveViewButton';
import SearchFilter from './SearchFilter';
import { TextButton } from './styledComponents';
import { sortFacets } from './utils';

const NUM_VISIBLE_FILTER_DROPDOWNS = 5;

export const FlexWrapper = styled.div`
    display: flex;
    flex-wrap: wrap;
    align-items: center;
`;

export const FlexSpacer = styled.div`
    display: flex;
    justify-content: space-between;
`;

const StyledDivider = styled(Divider)`
    margin: 8px 0 0 0;
`;

export const FilterButtonsWrapper = styled.div`
    display: flex;
    flex-wrap: nowrap;
`;

interface Props {
    availableFilters: FacetMetadata[] | null;
    activeFilters: FacetFilterInput[];
    onChangeFilters: (newFilters: FacetFilterInput[]) => void;
    showAdvancedFilters: () => void;
}

export default function BasicFilters({ availableFilters, activeFilters, onChangeFilters, showAdvancedFilters }: Props) {
    // only want Environment filter if there's 2 or more envs
    const filters = availableFilters
        ?.filter((f) => (f.field === ORIGIN_FILTER_NAME ? f.aggregations.length >= 2 : true))
        .sort((facetA, facetB) => sortFacets(facetA, facetB, SORTED_FILTERS));
    // if there will only be one filter in the "More Filters" dropdown, show that filter instead
    const shouldShowMoreDropdown = filters && filters.length > NUM_VISIBLE_FILTER_DROPDOWNS + 1;
    const visibleFilters = shouldShowMoreDropdown ? filters?.slice(0, NUM_VISIBLE_FILTER_DROPDOWNS) : filters;
    const hiddenFilters = shouldShowMoreDropdown ? filters?.slice(NUM_VISIBLE_FILTER_DROPDOWNS) : [];

    function clearAllFilters() {
        onChangeFilters([]);
    }

    return (
        <>
            <FlexSpacer>
                <FlexWrapper>
                    {visibleFilters?.map((filter) => (
                        <SearchFilter
                            key={filter.field}
                            filter={filter}
                            activeFilters={activeFilters}
                            onChangeFilters={onChangeFilters}
                        />
                    ))}
                    {hiddenFilters && hiddenFilters.length > 0 && (
                        <MoreFilters
                            filters={hiddenFilters}
                            activeFilters={activeFilters}
                            onChangeFilters={onChangeFilters}
                        />
                    )}
                </FlexWrapper>
                <FilterButtonsWrapper>
                    {activeFilters.length > 0 && (
                        <SaveViewButton activeFilters={activeFilters} unionType={UnionType.AND} />
                    )}
                    <TextButton type="text" onClick={showAdvancedFilters} marginTop={0}>
                        Advanced Filters
                    </TextButton>
                </FilterButtonsWrapper>
            </FlexSpacer>
            {activeFilters.length > 0 && (
                <>
                    <StyledDivider />
                    <FlexSpacer>
                        <FlexWrapper>
                            {activeFilters.map((activeFilter) => (
                                <ActiveFilter
                                    key={activeFilter.field}
                                    filter={activeFilter}
                                    availableFilters={availableFilters}
                                    activeFilters={activeFilters}
                                    onChangeFilters={onChangeFilters}
                                />
                            ))}
                        </FlexWrapper>
                        <TextButton type="text" onClick={clearAllFilters} height={14}>
                            clear all
                        </TextButton>
                    </FlexSpacer>
                </>
            )}
        </>
    );
}
