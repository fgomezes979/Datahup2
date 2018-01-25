import Ember from 'ember';
import { getApiRoot, ApiStatus } from 'wherehows-web/utils/api/shared';
import { IAuthenticateResponse, ICurrentUserResponse, IUser } from 'wherehows-web/typings/api/authentication/user';

const { $: { getJSON, get } } = Ember;
const logoutUrl = '/logout';
const loginUrl = '/authenticate';
const currentUserUrl = `${getApiRoot()}/user/me`;

const castGetJSONToPromise = <T>(url: string): Promise<T> => Promise.resolve(getJSON(url));

/**
 * Requests the currently logged in user and if the response is ok,
 * returns the user, otherwise throws
 * @return {Promise<IUser>}
 */
const currentUser = async (): Promise<IUser> => {
  const response = await castGetJSONToPromise<ICurrentUserResponse>(currentUserUrl);
  const { status = ApiStatus.FAILED, user } = response;
  if (status === ApiStatus.OK) {
    return user;
  }

  throw new Error(`Exception: ${status}`);
};

/**
 * Calls the logout endpoint to log out the currently logged in user
 * @return {Promise<void>}
 */
const logout = (): Promise<void> => Promise.resolve(get(logoutUrl));

/**
 * Calls the login endpoint to
 * @return {Promise<IAuthenticateResponse>}
 * TODO: use in ESA authenticator
 */
const login = (): Promise<IAuthenticateResponse> => castGetJSONToPromise<IAuthenticateResponse>(loginUrl);

export { currentUser, logout, login };
