import React from 'react';
import { render, waitFor } from '@testing-library/react';
import { MockedProvider } from '@apollo/client/testing';
import { createMemoryHistory } from 'history';

import TagProfile from '../TagProfile';
import TestPageContainer from '../../../../utils/test-utils/TestPageContainer';
import { mocks } from '../../../../Mocks';
import { Route } from 'react-router';

describe('TagProfile', () => {
    it('renders tag details', async () => {
        const { getByText, queryByText } = render(
            <MockedProvider mocks={mocks} addTypename={false}>
                <TestPageContainer initialEntries={['/tag/urn:li:tag:abc-sample-tag']}>
                    <Route path={'/tag/:urn'} render={() => <TagProfile />} />
                </TestPageContainer>
            </MockedProvider>,
        );

        await waitFor(() => expect(queryByText('abc-sample-tag')).toBeInTheDocument());

        expect(getByText('abc-sample-tag')).toBeInTheDocument();
        expect(getByText('sample tag description')).toBeInTheDocument();
    });

    it('renders tag ownership', async () => {
        const { getByText, getByTestId, queryByText } = render(
            <MockedProvider mocks={mocks} addTypename={false}>
                <TestPageContainer initialEntries={['/tag/urn:li:tag:abc-sample-tag']}>
                    <Route path={'/tag/:urn'} render={() => <TagProfile />} />
                </TestPageContainer>
            </MockedProvider>,
        );

        await waitFor(() => expect(queryByText('abc-sample-tag')).toBeInTheDocument());

        expect(getByTestId('avatar-tag-urn:li:corpuser:3')).toBeInTheDocument();
        expect(getByTestId('avatar-tag-urn:li:corpuser:2')).toBeInTheDocument();

        expect(getByTestId('avatar-tag-urn:li:corpuser:2').closest('a').href).toEqual(
            'http://localhost/user/urn:li:corpuser:2',
        );
        expect(getByTestId('avatar-tag-urn:li:corpuser:3').closest('a').href).toEqual(
            'http://localhost/user/urn:li:corpuser:3',
        );
    });
});
