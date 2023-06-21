import { EntityType } from '../../../types.generated';
import {
    CONTAINER_FILTER_NAME,
    DOMAINS_FILTER_NAME,
    ENTITY_FILTER_NAME,
    GLOSSARY_TERMS_FILTER_NAME,
    ORIGIN_FILTER_NAME,
    OWNERS_FILTER_NAME,
    PLATFORM_FILTER_NAME,
    TAGS_FILTER_NAME,
    TYPE_NAMES_FILTER_NAME,
} from '../utils/constants';

export const SORTED_FILTERS = [
    PLATFORM_FILTER_NAME,
    ORIGIN_FILTER_NAME,
    DOMAINS_FILTER_NAME,
    ENTITY_FILTER_NAME,
    TYPE_NAMES_FILTER_NAME,
    GLOSSARY_TERMS_FILTER_NAME,
    OWNERS_FILTER_NAME,
    TAGS_FILTER_NAME,
    CONTAINER_FILTER_NAME,
];

export const FACETS_TO_ENTITY_TYPES = {
    [DOMAINS_FILTER_NAME]: [EntityType.Domain],
    [GLOSSARY_TERMS_FILTER_NAME]: [EntityType.GlossaryTerm],
    [OWNERS_FILTER_NAME]: [EntityType.CorpUser, EntityType.CorpGroup],
    [TAGS_FILTER_NAME]: [EntityType.Tag],
    [CONTAINER_FILTER_NAME]: [EntityType.Container],
};
