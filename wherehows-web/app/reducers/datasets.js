import {
  initializeState,
  receiveNodes,
  receiveEntities,
  createUrnMapping,
  createPageMapping
} from 'wherehows-web/reducers/entities';
import { ActionTypes } from 'wherehows-web/actions/datasets';

/**
 * datasets root reducer
 * Takes the `datasets` slice of the state tree and performs the specified reductions for each action
 * @param {Object} state slice of the state tree this reducer is responsible for
 * @param {Object} action Flux Standard Action representing the action to be preformed on the state
 * @prop {String} action.type actionType
 * @return {Object}
 */
export default (state = initializeState(), action = {}) => {
  switch (action.type) {
    // Action indicating a request for datasets by page
    case ActionTypes.SELECT_PAGED_DATASETS:
    case ActionTypes.REQUEST_PAGED_DATASETS:
      return Object.assign({}, state, {
        query: Object.assign({}, state.query, {
          page: action.payload.page
        }),
        baseURL: action.payload.baseURL,
        isFetching: true
      });
    // Action indicating a receipt of datasets by page
    case ActionTypes.RECEIVE_PAGED_DATASETS:
    case ActionTypes.RECEIVE_PAGED_URN_DATASETS:
      return Object.assign({}, state, {
        isFetching: false,
        byId: receiveEntities('datasets')(state.byId, action.payload),
        byUrn: createUrnMapping('datasets')(state.byUrn, action.payload),
        byPage: createPageMapping('datasets')(state.byPage, action.payload)
      });

    // Action indicating a request for list nodes
    case ActionTypes.REQUEST_DATASET_NODES:
      return Object.assign({}, state, {
        query: Object.assign({}, state.query, action.payload.query),
        isFetching: true
      });

    // Action indicating a receipt of list nodes / datasets for dataset urn
    case ActionTypes.RECEIVE_DATASET_NODES:
      return Object.assign({}, state, {
        isFetching: false,
        nodesByUrn: receiveNodes(state, action.payload)
      });

    default:
      return state;
  }
};
