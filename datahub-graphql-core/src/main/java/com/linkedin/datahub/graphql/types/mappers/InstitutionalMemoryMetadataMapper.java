package com.linkedin.datahub.graphql.types.mappers;

import com.linkedin.datahub.graphql.generated.InstitutionalMemoryMetadata;

import javax.annotation.Nonnull;

public class InstitutionalMemoryMetadataMapper implements ModelMapper<com.linkedin.common.InstitutionalMemoryMetadata, InstitutionalMemoryMetadata> {

    public static final InstitutionalMemoryMetadataMapper INSTANCE = new InstitutionalMemoryMetadataMapper();

    public static InstitutionalMemoryMetadata map(@Nonnull final com.linkedin.common.InstitutionalMemoryMetadata metadata) {
        return INSTANCE.apply(metadata);
    }

    @Override
    public InstitutionalMemoryMetadata apply(@Nonnull final com.linkedin.common.InstitutionalMemoryMetadata input) {
        final InstitutionalMemoryMetadata result = new InstitutionalMemoryMetadata();
        result.setUrl(input.getUrl().toString());
        result.setDescription(input.getDescription());
        result.setCreated(AuditStampMapper.map(input.getCreateStamp()));
        return result;
    }
}
