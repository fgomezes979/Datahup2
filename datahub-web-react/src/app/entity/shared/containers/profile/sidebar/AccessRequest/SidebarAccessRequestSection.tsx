import { Typography, Button, Modal, message } from 'antd';
import React, { useState } from 'react';
import { EditOutlined } from '@ant-design/icons';
import { EMPTY_MESSAGES } from '../../../../constants';
import { useEntityData, useMutationUrn, useRefetch } from '../../../../EntityContext';
import { SidebarHeader } from '../SidebarHeader';
import { SetAccessRequestModal } from './SetAccessRequestModal';
import { useUnsetDomainMutation } from '../../../../../../../graphql/mutations.generated';
import { DomainLink } from '../../../../../../shared/tags/DomainLink';
import { ENTITY_PROFILE_DOMAINS_ID } from '../../../../../../onboarding/config/EntityProfileOnboardingConfig';

interface Props {
    readOnly?: boolean;
}

export const SidebarAccessRequestSection = ({ readOnly }: Props) => {
    const { entityData } = useEntityData();
    const refetch = useRefetch();
    const urn = useMutationUrn();
    const [unsetDomainMutation] = useUnsetDomainMutation();
    const [showModal, setShowModal] = useState(false);
    const domain = entityData?.domain?.domain;

    const removeDomain = (urnToRemoveFrom) => {
        unsetDomainMutation({ variables: { entityUrn: urnToRemoveFrom } })
            .then(() => {
                message.success({ content: 'Removed Domain.', duration: 2 });
                refetch?.();
            })
            .catch((e: unknown) => {
                message.destroy();
                if (e instanceof Error) {
                    message.error({ content: `Failed to remove domain: \n ${e.message || ''}`, duration: 3 });
                }
            });
    };

    const onRemoveDomain = (urnToRemoveFrom) => {
        Modal.confirm({
            title: `Confirm Domain Removal`,
            content: `Are you sure you want to remove this domain?`,
            onOk() {
                removeDomain(urnToRemoveFrom);
            },
            onCancel() {},
            okText: 'Yes',
            maskClosable: true,
            closable: true,
        });
    };

    return (
        <div>
            <div id={ENTITY_PROFILE_DOMAINS_ID} className="sidebar-domain-section">
                <SidebarHeader title="Access Request" />
                <div>
                    {domain && (
                        <DomainLink
                            domain={domain}
                            closable={!readOnly}
                            readOnly={readOnly}
                            onClose={(e) => {
                                e.preventDefault();
                                onRemoveDomain(entityData?.domain?.associatedUrn);
                            }}
                        />
                    )}
                    {!domain && (
                        <>
                            <Typography.Paragraph type="secondary">
                                {EMPTY_MESSAGES.domain.title}. {EMPTY_MESSAGES.domain.description}
                            </Typography.Paragraph>
                            {!readOnly && (
                                <Button type="default" onClick={() => setShowModal(true)}>
                                    <EditOutlined /> Access Request
                                </Button>
                            )}
                        </>
                    )}
                </div>
                {showModal && (
                    <SetAccessRequestModal
                        urns={[urn]}
                        refetch={refetch}
                        onCloseModal={() => {
                            setShowModal(false);
                        }}
                    />
                )}
            </div>
        </div>
    );
};
