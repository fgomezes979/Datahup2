import React from 'react';
import { Typography } from 'antd';
import {
    getFieldDescription,
    getFieldOperatorDescription,
    getFieldParametersDescription,
    getFieldTransformDescription,
} from './fieldDescriptionUtils';

type Props = {
    assertionInfo: any;
};

/**
 * A human-readable description of a Field Assertion.
 */
export const FieldAssertionDescription = ({ assertionInfo }: Props) => {
    const field = getFieldDescription(assertionInfo);
    const operator = getFieldOperatorDescription(assertionInfo);
    const transform = getFieldTransformDescription(assertionInfo);
    const parameters = getFieldParametersDescription(assertionInfo);

    return (
        <Typography.Text>
            {transform}
            {transform ? ' of ' : ''}
            <Typography.Text code>{field}</Typography.Text> {operator} {parameters}
        </Typography.Text>
    );
};
