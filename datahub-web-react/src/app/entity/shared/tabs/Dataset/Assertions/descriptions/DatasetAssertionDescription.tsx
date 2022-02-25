import { Popover, Typography } from 'antd';
import React from 'react';
import {
    DatasetAssertionInfo,
    DatasetAssertionScope,
    DatasetColumnAssertion,
    DatasetRowsAssertion,
    DatasetSchemaAssertion,
} from '../../../../../../../types.generated';
import { DatasetColumnAssertionDescription } from './DatasetColumnsAssertionDescription';
import { DatasetRowsAssertionDescription } from './DatasetRowsAssertionDescription';
import { DatasetSchemaAssertionDescription } from './DatasetSchemaAssertionsDescription';
import { validateColumnAssertionFieldPath } from './util';

type Props = {
    assertionInfo: DatasetAssertionInfo;
};

export const DatasetAssertionDescription = ({ assertionInfo }: Props) => {
    const { logic, parameters } = assertionInfo;

    let description;
    let type;

    if (logic) {
        type = 'SQL';
        description = (
            <span>
                <pre style={{ margin: 0, padding: 0 }}>{logic}</pre>
            </span>
        );
    } else {
        const { scope } = assertionInfo;
        switch (scope) {
            case DatasetAssertionScope.DatasetRows: {
                type = assertionInfo.rowsAssertion?.nativeType;
                description = (
                    <DatasetRowsAssertionDescription
                        assertion={assertionInfo.rowsAssertion as DatasetRowsAssertion}
                        parameters={parameters || undefined}
                    />
                );
                break;
            }
            case DatasetAssertionScope.DatasetColumn: {
                const fieldPath = validateColumnAssertionFieldPath(assertionInfo);
                type = assertionInfo.columnAssertion?.nativeType;
                description = (
                    <DatasetColumnAssertionDescription
                        fieldPath={fieldPath}
                        assertion={assertionInfo.columnAssertion as DatasetColumnAssertion}
                        parameters={parameters || undefined}
                    />
                );
                break;
            }
            case DatasetAssertionScope.DatasetSchema: {
                type = assertionInfo.schemaAssertion?.nativeType;
                description = (
                    <DatasetSchemaAssertionDescription
                        assertion={assertionInfo.schemaAssertion as DatasetSchemaAssertion}
                        parameters={parameters || undefined}
                    />
                );
                break;
            }
            default:
                throw new Error(`Unsupported Dataset Assertion Scope ${scope}`);
        }
    }

    return (
        <Popover
            overlayStyle={{ maxWidth: 440 }}
            title={<Typography.Text strong>Details</Typography.Text>}
            content={
                <>
                    <Typography.Text strong>type</Typography.Text>: {type || 'N/A'}
                    {parameters &&
                        parameters.map((parameter) => (
                            <div>
                                <Typography.Text strong>{parameter.key}</Typography.Text>: {parameter.value}
                            </div>
                        ))}
                </>
            }
        >
            <div>{description}</div>
        </Popover>
    );
};
