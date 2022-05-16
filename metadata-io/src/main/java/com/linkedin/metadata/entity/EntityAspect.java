package com.linkedin.metadata.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.annotation.Nonnull;
import java.sql.Timestamp;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class EntityAspect {

    @Nonnull
    private String urn;

    @Nonnull
    private String aspect;

    private long version;

    private String metadata;

    private String systemMetadata;

    private Timestamp createdOn;

    private String createdBy;

    private String createdFor;

    public EntityAspectIdentifier toAspectIdentifier() {
        return new EntityAspectIdentifier(getUrn(), getAspect(), getVersion());
    }
}
