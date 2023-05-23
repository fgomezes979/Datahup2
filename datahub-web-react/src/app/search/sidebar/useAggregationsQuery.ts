import { useAggregateAcrossEntitiesLazyQuery } from '../../../graphql/search.generated';
import { ORIGIN_FILTER_NAME, PLATFORM_FILTER_NAME } from '../utils/constants';
import { EntityType } from '../../../types.generated';
import useGetSearchQueryInputs from '../useGetSearchQueryInputs';
import applyOrFilterOverrides from '../utils/applyOrFilterOverrides';

type Props = {
    entityType: EntityType;
    environment?: string | null;
    facets: string[];
    skip: boolean;
};

const useAggregationsQuery = ({ entityType, environment, facets }: Props) => {
    const filterOverrides = [...(environment ? [{ field: ORIGIN_FILTER_NAME, value: environment }] : [])];

    const excludedFilterFields = filterOverrides.map((filter) => filter.field);

    const { query, orFilters, viewUrn } = useGetSearchQueryInputs(excludedFilterFields);

    const [getAggregations, { data: newData, previousData, loading, error }] = useAggregateAcrossEntitiesLazyQuery({
        fetchPolicy: 'cache-first',
    });

    const getAggregationsApi = () => {
        getAggregations({
            variables: {
                input: {
                    types: [entityType],
                    query,
                    orFilters: applyOrFilterOverrides(orFilters, filterOverrides),
                    viewUrn,
                    facets,
                },
            },
        });
    };

    const data = error ? null : newData ?? previousData;

    const environmentAggregations =
        data?.aggregateAcrossEntities?.facets
            ?.find((facet) => facet.field === ORIGIN_FILTER_NAME)
            ?.aggregations.filter((aggregation) => aggregation.count > 0) ?? [];

    const platformAggregations =
        data?.aggregateAcrossEntities?.facets
            ?.find((facet) => facet.field === PLATFORM_FILTER_NAME)
            ?.aggregations.filter((aggregation) => aggregation.count > 0) ?? [];

    return [
        getAggregationsApi,
        {
            loading,
            loaded: !!data || !!error,
            error,
            environmentAggregations,
            platformAggregations,
        } as const,
    ] as const;
};

export default useAggregationsQuery;
