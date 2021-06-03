package com.linkedin.datahub.graphql.types.dashboard.mappers;

import com.linkedin.common.GlobalTags;

import com.linkedin.common.TagAssociationArray;
import com.linkedin.common.urn.DashboardUrn;
import com.linkedin.common.urn.Urn;
import com.linkedin.datahub.graphql.generated.DashboardUpdateInput;
import com.linkedin.datahub.graphql.types.common.mappers.OwnershipUpdateMapper;
import com.linkedin.datahub.graphql.types.mappers.InputModelMapper;
import com.linkedin.datahub.graphql.types.tag.mappers.TagAssociationUpdateMapper;
import com.linkedin.metadata.aspect.DashboardAspect;
import com.linkedin.metadata.aspect.DashboardAspectArray;
import com.linkedin.metadata.snapshot.DashboardSnapshot;
import java.net.URISyntaxException;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;


public class DashboardUpdateInputSnapshotMapper implements InputModelMapper<DashboardUpdateInput, DashboardSnapshot, Urn> {
    public static final DashboardUpdateInputSnapshotMapper INSTANCE = new DashboardUpdateInputSnapshotMapper();

    public static DashboardSnapshot map(@Nonnull final DashboardUpdateInput dashboardUpdateInput,
                                @Nonnull final Urn actor) {
        return INSTANCE.apply(dashboardUpdateInput, actor);
    }

    @Override
    public DashboardSnapshot apply(@Nonnull final DashboardUpdateInput dashboardUpdateInput,
                           @Nonnull final Urn actor) {
        final DashboardSnapshot result = new DashboardSnapshot();

        try {
            result.setUrn(DashboardUrn.createFromString(dashboardUpdateInput.getUrn()));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(
                String.format("Failed to validate provided urn with value %s", dashboardUpdateInput.getUrn()));
        }

        final DashboardAspectArray aspects = new DashboardAspectArray();

        if (dashboardUpdateInput.getOwnership() != null) {
            aspects.add(DashboardAspect.create(OwnershipUpdateMapper.map(dashboardUpdateInput.getOwnership(), actor)));
        }

        if (dashboardUpdateInput.getGlobalTags() != null) {
            final GlobalTags globalTags = new GlobalTags();
            globalTags.setTags(
                new TagAssociationArray(
                    dashboardUpdateInput.getGlobalTags().getTags().stream().map(
                        element -> TagAssociationUpdateMapper.map(element)
                    ).collect(Collectors.toList())
                )
            );
            aspects.add(DashboardAspect.create(globalTags));
        }

        result.setAspects(aspects);

        return result;
    }

}
