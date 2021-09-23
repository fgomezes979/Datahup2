import React, { useState } from 'react';
import { Badge, Table } from 'antd';
import styled from 'styled-components';
import { green } from '@ant-design/colors';
import Modal from 'antd/lib/modal/Modal';
import { Link } from 'react-router-dom';

import { ANTD_GRAY } from '../../../../constants';
import { EntityType, ForeignKeyConstraint } from '../../../../../../../types.generated';
import { useBaseEntity } from '../../../../EntityContext';
import { GetDatasetQuery } from '../../../../../../../graphql/dataset.generated';
import { useEntityRegistry } from '../../../../../../useEntityRegistry';

const ForeignKeyBadge = styled(Badge)<{ highlight: boolean }>`
    margin-left: 4px;
    &&& .ant-badge-count {
        background-color: ${(props) => (props.highlight ? green[1] : ANTD_GRAY[1])};
        color: ${green[5]};
        border: 1px solid ${green[2]};
        font-size: 12px;
        font-weight: 400;
        height: 22px;
        cursor: pointer;
    }
`;

type Props = {
    highlight: boolean;
    constraint?: ForeignKeyConstraint | null;
    setActiveConstraint: (newActiveConstraint: string | null) => void;
};

const zip = (a, b) =>
    Array.from(Array(Math.max(b.length, a.length)), (_, i) => ({ source: a[i]?.fieldPath, foreign: b[i]?.fieldPath }));

export default function ForeignKeyLabel({ constraint, highlight, setActiveConstraint }: Props) {
    const [showModal, setShowModal] = useState(false);
    const baseEntity = useBaseEntity<GetDatasetQuery>();
    const entityRegistry = useEntityRegistry();

    const sourceColumn = {
        title: (
            <Link to={entityRegistry.getEntityUrl(EntityType.Dataset, baseEntity?.dataset?.urn || '')}>
                {baseEntity.dataset?.name}
            </Link>
        ),
        dataIndex: 'source',
        key: 'source',
    };

    const foreignColumn = {
        title: (
            <Link to={entityRegistry.getEntityUrl(EntityType.Dataset, constraint?.foreignDataset?.urn || '')}>
                {constraint?.foreignDataset?.name}
            </Link>
        ),
        dataIndex: 'foreign',
        key: 'foreign',
    };

    const rows = zip(constraint?.sourceFields, constraint?.foreignFields);

    return (
        <>
            <Modal title={constraint?.name || 'Foreign Key'} visible={showModal} onCancel={() => setShowModal(false)}>
                <Table columns={[sourceColumn, foreignColumn]} dataSource={rows} pagination={false} />
            </Modal>
            <span
                role="button"
                tabIndex={0}
                onKeyPress={(e) => (e.key === 'Enter' ? setShowModal(true) : null)}
                onClick={() => setShowModal(!showModal)}
                onMouseEnter={() => setActiveConstraint(constraint?.name || null)}
                onMouseLeave={() => setActiveConstraint(null)}
            >
                <ForeignKeyBadge highlight={highlight} count="Foreign Key" />
            </span>
        </>
    );
}
