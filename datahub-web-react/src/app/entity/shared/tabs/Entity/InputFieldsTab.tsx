import { Empty } from 'antd';
import React from 'react';
import styled from 'styled-components';
import { SchemaField } from '../../../../../types.generated';
import SchemaEditableContext from '../../../../shared/SchemaEditableContext';
import { ANTD_GRAY } from '../../constants';
import { useEntityData } from '../../EntityContext';
import SchemaTable from '../Dataset/Schema/SchemaTable';

const NoSchema = styled(Empty)`
    color: ${ANTD_GRAY[6]};
    padding-top: 60px;
`;

const SchemaTableContainer = styled.div`
    overflow: auto;
    height: 100%;
`;
export const InputFieldsTab = () => {
    const { entityData } = useEntityData();
    const inputFields = entityData?.inputFields || undefined;
    const rows = inputFields?.fields?.map((field) => field?.schemaField) as SchemaField[];
    return (
        <>
            <SchemaTableContainer>
                {rows && rows.length > 0 ? (
                    <>
                        <SchemaEditableContext.Provider value={false}>
                            <SchemaTable
                                schemaMetadata={null}
                                rows={rows}
                                editMode={false}
                                editableSchemaMetadata={null}
                                usageStats={null}
                                schemaFieldBlameList={null}
                                showSchemaAuditView={false}
                            />
                        </SchemaEditableContext.Provider>
                    </>
                ) : (
                    <NoSchema />
                )}
            </SchemaTableContainer>
        </>
    );
};
