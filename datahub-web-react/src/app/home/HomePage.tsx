import React from 'react';
import { HomePageHeader } from './HomePageHeader';
import { HomePageBody } from './HomePageBody';
import analytics, { EventType } from '../analytics';

export const HomePage = () => {
    analytics.event({ type: EventType.PageViewEvent, originPath: window.location.pathname });
    return (
        <>
            <HomePageHeader />
            <HomePageBody />
        </>
    );
};
