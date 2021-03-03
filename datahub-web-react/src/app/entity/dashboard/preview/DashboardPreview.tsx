import React from 'react';
import { AccessLevel, EntityType, GlobalTags, Owner } from '../../../../types.generated';
import DefaultPreviewCard from '../../../preview/DefaultPreviewCard';
import { useEntityRegistry } from '../../../useEntityRegistry';
import { getLogoFromPlatform } from '../../chart/getLogoFromPlatform';

export const DashboardPreview = ({
    urn,
    name,
    description,
    platform,
    access,
    owners,
    tags,
}: {
    urn: string;
    platform: string;
    name?: string;
    description?: string | null;
    access?: AccessLevel | null;
    owners?: Array<Owner> | null;
    tags?: GlobalTags;
}): JSX.Element => {
    const entityRegistry = useEntityRegistry();

    return (
        <DefaultPreviewCard
            url={`/${entityRegistry.getPathName(EntityType.Dashboard)}/${urn}`}
            name={name || ''}
            description={description || ''}
            type="Dashboard"
            logoUrl={getLogoFromPlatform(platform) || ''}
            platform={platform}
            qualifier={access}
            owners={
                owners?.map((owner) => {
                    return {
                        urn: owner.owner.urn,
                        name: owner.owner.info?.fullName || '',
                        photoUrl: owner.owner.editableInfo?.pictureLink || '',
                    };
                }) || []
            }
            tags={tags}
        />
    );
};
