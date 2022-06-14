import { useGetSearchResultsForMultipleQuery } from '../../graphql/search.generated';
import { EntityType } from '../../types.generated';

export function GetTagRecommendation() {
    const { data } = useGetSearchResultsForMultipleQuery({
        variables: {
            input: {
                types: [EntityType.Tag],
                query: '*',
                start: 0,
                count: 5,
            },
        },
    });
    return data?.searchAcrossEntities?.searchResults;
}
