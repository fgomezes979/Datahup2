import {
    Entity,
    FacetMetadata,
    MatchedField,
    Maybe,
    Scalars,
    SearchAcrossEntitiesInput,
    SearchInsight,
} from '../../../../../../types.generated';

export type GetSearchResultsParams = {
    variables: {
        input: SearchAcrossEntitiesInput;
    };
} & Record<string, any>;

export type SearchResultInterface = {
    entity: Entity;
    /** Insights about why the search result was matched */
    insights?: Maybe<Array<SearchInsight>>;
    /** Matched field hint */
    matchedFields: Array<MatchedField>;
    path?: Maybe<Array<Maybe<Entity>>>;
} & Record<string, any>;

export type SearchResultsInterface = {
    /** The offset of the result set */
    start: Scalars['Int'];
    /** The number of entities included in the result set */
    count: Scalars['Int'];
    /** The total number of search results matching the query and filters */
    total: Scalars['Int'];
    /** The search result entities */
    searchResults: Array<SearchResultInterface>;
    /** Candidate facet aggregations used for search filtering */
    facets?: Maybe<Array<FacetMetadata>>;
};
