import { useCallback, useMemo, useState } from 'react';
import { useGetChartLazyQuery } from '../../../graphql/chart.generated';
import { useGetDashboardLazyQuery } from '../../../graphql/dashboard.generated';
import { useGetDatasetLazyQuery } from '../../../graphql/dataset.generated';
import { useGetDataJobLazyQuery } from '../../../graphql/dataJob.generated';
import { useGetMlFeatureTableLazyQuery } from '../../../graphql/mlFeatureTable.generated';
import { EntityType } from '../../../types.generated';
import { EntityAndType } from '../types';

export default function useLazyGetEntityQuery() {
    const [fetchedEntityType, setFetchedEntityType] = useState<EntityType | undefined>(undefined);
    const [getAsyncDataset, { data: asyncDatasetData }] = useGetDatasetLazyQuery();
    const [getAsyncChart, { data: asyncChartData }] = useGetChartLazyQuery();
    const [getAsyncDashboard, { data: asyncDashboardData }] = useGetDashboardLazyQuery();
    const [getAsyncDataJob, { data: asyncDataJobData }] = useGetDataJobLazyQuery();
    const [getAsyncMLFeatureTable, { data: asyncMLFeatureTable }] = useGetMlFeatureTableLazyQuery();

    const getAsyncEntity = useCallback(
        (urn: string, type: EntityType) => {
            if (type === EntityType.Dataset) {
                setFetchedEntityType(type);
                getAsyncDataset({ variables: { urn } });
            }
            if (type === EntityType.Chart) {
                setFetchedEntityType(type);
                getAsyncChart({ variables: { urn } });
            }
            if (type === EntityType.Dashboard) {
                setFetchedEntityType(type);
                getAsyncDashboard({ variables: { urn } });
            }
            if (type === EntityType.DataJob) {
                setFetchedEntityType(type);
                getAsyncDataJob({ variables: { urn } });
            }
            if (type === EntityType.MlfeatureTable) {
                setFetchedEntityType(type);
                getAsyncMLFeatureTable({ variables: { urn } });
            }
        },
        [
            setFetchedEntityType,
            getAsyncChart,
            getAsyncDataset,
            getAsyncDashboard,
            getAsyncDataJob,
            getAsyncMLFeatureTable,
        ],
    );

    const returnEntityAndType: EntityAndType | undefined = useMemo(() => {
        let returnData;
        switch (fetchedEntityType) {
            case EntityType.Dataset:
                returnData = asyncDatasetData?.dataset;
                if (returnData) {
                    return {
                        entity: returnData,
                        type: EntityType.Dataset,
                    } as EntityAndType;
                }
                break;
            case EntityType.Chart:
                returnData = asyncChartData?.chart;
                if (returnData) {
                    return {
                        entity: returnData,
                        type: EntityType.Chart,
                    } as EntityAndType;
                }
                break;
            case EntityType.Dashboard:
                returnData = asyncDashboardData?.dashboard;
                if (returnData) {
                    return {
                        entity: returnData,
                        type: EntityType.Dashboard,
                    } as EntityAndType;
                }
                break;
            case EntityType.DataJob:
                returnData = asyncDataJobData?.dataJob;
                if (returnData) {
                    return {
                        entity: returnData,
                        type: EntityType.DataJob,
                    } as EntityAndType;
                }
                break;
            case EntityType.MlfeatureTable:
                returnData = asyncMLFeatureTable?.mlFeatureTable;
                if (returnData) {
                    return {
                        entity: returnData,
                        type: EntityType.MlfeatureTable,
                    } as EntityAndType;
                }
                break;
            default:
                break;
        }
        return undefined;
    }, [
        asyncDatasetData,
        asyncChartData,
        asyncDashboardData,
        asyncDataJobData,
        fetchedEntityType,
        asyncMLFeatureTable,
    ]);

    return { getAsyncEntity, asyncData: returnEntityAndType };
}
