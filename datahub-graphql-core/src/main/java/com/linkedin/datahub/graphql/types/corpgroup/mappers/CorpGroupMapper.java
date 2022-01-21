package com.linkedin.datahub.graphql.types.corpgroup.mappers;

import com.linkedin.data.DataMap;
import com.linkedin.datahub.graphql.generated.CorpGroup;
import com.linkedin.datahub.graphql.generated.EntityType;
import com.linkedin.datahub.graphql.types.common.mappers.util.MappingHelper;
import com.linkedin.datahub.graphql.types.mappers.ModelMapper;
import com.linkedin.entity.EntityResponse;
import com.linkedin.entity.EnvelopedAspectMap;
import com.linkedin.identity.CorpGroupInfo;
import com.linkedin.metadata.key.CorpGroupKey;
import javax.annotation.Nonnull;

import static com.linkedin.metadata.Constants.*;


/**
 * Maps Pegasus {@link RecordTemplate} objects to objects conforming to the GQL schema.
 *
 * To be replaced by auto-generated mappers implementations
 */
public class CorpGroupMapper implements ModelMapper<EntityResponse, CorpGroup> {

    public static final CorpGroupMapper INSTANCE = new CorpGroupMapper();

    public static CorpGroup map(@Nonnull final EntityResponse entityResponse) {
        return INSTANCE.apply(entityResponse);
    }

    @Override
    public CorpGroup apply(@Nonnull final EntityResponse entityResponse) {
        final CorpGroup result = new CorpGroup();
        result.setUrn(entityResponse.getUrn().toString());
        result.setType(EntityType.CORP_GROUP);
        EnvelopedAspectMap aspectMap = entityResponse.getAspects();
        MappingHelper<CorpGroup> mappingHelper = new MappingHelper<>(aspectMap, result);
        mappingHelper.mapToResult(CORP_GROUP_KEY_ASPECT_NAME, this::mapCorpGroupKey);
        mappingHelper.mapToResult(CORP_GROUP_INFO_ASPECT_NAME, this::mapCorpGroupInfo);

        return mappingHelper.getResult();
    }

    private void mapCorpGroupKey(CorpGroup corpGroup, DataMap dataMap) {
        CorpGroupKey corpGroupKey = new CorpGroupKey(dataMap);
        corpGroup.setName(corpGroupKey.getName());
    }

    private void mapCorpGroupInfo(CorpGroup corpGroup, DataMap dataMap) {
        CorpGroupInfo corpGroupInfo = new CorpGroupInfo(dataMap);
        corpGroup.setProperties(CorpGroupPropertiesMapper.map(corpGroupInfo));
        corpGroup.setInfo(CorpGroupInfoMapper.map(corpGroupInfo));
    }
}
