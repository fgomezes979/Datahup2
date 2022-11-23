package com.datahub.authentication.authenticator;

import com.auth0.jwk.JwkException;
import com.datahub.authentication.Actor;
import com.datahub.authentication.Authentication;
import com.datahub.authentication.AuthenticationException;
import com.datahub.authentication.AuthenticationRequest;
import com.google.common.collect.ImmutableMap;
import java.net.MalformedURLException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.testng.annotations.Test;

import static com.datahub.authentication.AuthenticationConstants.*;
import static org.mockito.Mockito.*;
import static org.testng.AssertJUnit.*;


public class CustomClaimTokenAuthenticatorTest {

  @Test
  void testAuthentication()
      throws NoSuchAlgorithmException, InvalidKeySpecException, MalformedURLException, JwkException,
             AuthenticationException {
    String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwidXNlcm5hbWUiOiJqb2huX3Nub3ci"
        + "LCJhZG1pbiI6dHJ1ZSwiaWF0IjoxNTE2MjM5MDIyLCJpc3MiOiJodHRwczovL3Rlc3QuY29tL3JlYWxtL2RvbWFpbiJ9.Hz6Zpt-b5gpw3YvJ4fk8x"
        + "fhLQL9Rmwqj2hPhVcvpyDw5IHoiMLIxGZsiC80lxfU8a02f-2Tmek5bNKaXbgSNzYWITL5lrwEO-rTXYNamy8gJOBoM8n7gHDOo6"
        + "JDd25go4MsLbjHbQ-WNq5SErgaNOMfZdkg2jqKVldZvjW33v8aupx08fzONnuzaYIJBQpONhGzDkYZKkkrewdrYYVl_naNRWsKt8uSVu83G3mLhMPazk"
        + "xNT5CWfNR7sdXfladz8U6ruLFOGUJJ5KDjEVAReRpEbxaKOIY6oFio1TeUQsi6vppLXB0RupTBmE5dr7rxdL4j9eDY94M2uowBDuOsEGA";
    String validPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAu1SU1LfVLPHCozMxH2Mo"
        + "4lgOEePzNm0tRgeLezV6ffAt0gunVTLw7onLRnrq0/IzW7yWR7QkrmBL7jTKEn5u"
        + "+qKhbwKfBstIs+bMY2Zkp18gnTxKLxoS2tFczGkPLPgizskuemMghRniWaoLcyeh"
        + "kd3qqGElvW/VDL5AaWTg0nLVkjRo9z+40RQzuVaE8AkAFmxZzow3x+VJYKdjykkJ"
        + "0iT9wCS0DRTXu269V264Vf/3jvredZiKRkgwlL9xNAwxXFg0x/XFw005UWVRIkdg"
        + "cKWTjpBP2dPwVZ4WWC+9aGVd+Gyn1o0CLelf4rEjGoXbAAEgAqeGUxrcIlbjXfbc" + "mwIDAQAB";

    CustomClaimTokenAuthenticator mock = mock(CustomClaimTokenAuthenticator.class);
    RSAPublicKey pk = loadPublicKey(validPublicKey);
    when(mock.getPublicKey(any())).thenReturn(pk);

    final AuthenticationRequest context = new AuthenticationRequest(ImmutableMap.of(AUTHORIZATION_HEADER_NAME, token));

    when(mock.authenticate(context)).thenCallRealMethod();

    Map<String, Object> config = new HashMap<String, Object>();
    config.put("idClaim", "username");
    config.put("trustedIssuers", getTrustedIssuer());
    doCallRealMethod().when(mock).init(config, null);

    mock.init(config, null);
    Authentication result = mock.authenticate(context);
    Actor actor = result.getActor();
    String actualCredential = result.getCredentials();
    assertEquals(token, actualCredential);
    assertEquals("urn:li:corpuser:john_snow", actor.toUrnStr());
  }

  @Test(expectedExceptions = InvalidKeySpecException.class)
  void testInvalidKey() throws NoSuchAlgorithmException, InvalidKeySpecException, MalformedURLException, JwkException,
                               AuthenticationException {

    String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwidXNlcm5hbWUiOiJqb2huX3Nub3ci"
        + "LCJhZG1pbiI6dHJ1ZSwiaWF0IjoxNTE2MjM5MDIyLCJpc3MiOiJodHRwczovL3Rlc3QuY29tL3JlYWxtL2RvbWFpbiJ9.Hz6Zpt-b5gpw3YvJ4fk8x"
        + "fhLQL9Rmwqj2hPhVcvpyDw5IHoiMLIxGZsiC80lxfU8a02f-2Tmek5bNKaXbgSNzYWITL5lrwEO-rTXYNamy8gJOBoM8n7gHDOo6"
        + "JDd25go4MsLbjHbQ-WNq5SErgaNOMfZdkg2jqKVldZvjW33v8aupx08fzONnuzaYIJBQpONhGzDkYZKkkrewdrYYVl_naNRWsKt8uSVu83G3mLhMPazk"
        + "xNT5CWfNR7sdXfladz8U6ruLFOGUJJ5KDjEVAReRpEbxaKOIY6oFio1TeUQsi6vppLXB0RupTBmE5dr7rxdL4j9eDY94M2uowBDuOsEGA";
    String inValidPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAu1SU1LfVLPHCozMxH2Mo"
        + "4lgOEePzNm0tRgeLezV6ffAt0gunVTLw7onLRnrq0/IzW7yWR7QkrmBL7jTKEn5u"
        + "+qKhbwKfBstIs+bMY2Zkp18gnTxKLxoS2tFczGkPLPgizskuemMghRniWaoLcyeh"
        + "kd3qqGElvW/VDL5AaWTg0nLVkjRo9z+40RQzuVaE8AkAFmxZzow3x+VJYKdjykkJ" + "0iT9wCS0DRTXu269V264Vf/3jvr";

    CustomClaimTokenAuthenticator mock = mock(CustomClaimTokenAuthenticator.class);
    RSAPublicKey pk = loadPublicKey(inValidPublicKey);
    when(mock.getPublicKey(any())).thenReturn(pk);

    final AuthenticationRequest context = new AuthenticationRequest(ImmutableMap.of(AUTHORIZATION_HEADER_NAME, token));

    Map<String, Object> config = new HashMap<>();
    config.put("idClaim", "missingClaimInToken");
    config.put("trustedIssuers", getTrustedIssuer());
    doCallRealMethod().when(mock).init(config, null);

    mock.init(config, null);

    when(mock.authenticate(context)).thenCallRealMethod();
    mock.authenticate(context);
  }

  @Test(expectedExceptions = AuthenticationException.class)
  void testNullToken() throws AuthenticationException {

    CustomClaimTokenAuthenticator mock = mock(CustomClaimTokenAuthenticator.class);

    final AuthenticationRequest context = new AuthenticationRequest(ImmutableMap.of());

    when(mock.authenticate(context)).thenCallRealMethod();
    mock.authenticate(context);
  }

  @Test(expectedExceptions = AuthenticationException.class)
  void testMissingClaim() throws NoSuchAlgorithmException, InvalidKeySpecException, MalformedURLException, JwkException,
                                 AuthenticationException {

    String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwidXNlcm5hbWUiOiJqb2huX3Nub3ci"
        + "LCJhZG1pbiI6dHJ1ZSwiaWF0IjoxNTE2MjM5MDIyLCJpc3MiOiJodHRwczovL3Rlc3QuY29tL3JlYWxtL2RvbWFpbiJ9.Hz6Zpt-b5gpw3YvJ4fk8x"
        + "fhLQL9Rmwqj2hPhVcvpyDw5IHoiMLIxGZsiC80lxfU8a02f-2Tmek5bNKaXbgSNzYWITL5lrwEO-rTXYNamy8gJOBoM8n7gHDOo6"
        + "JDd25go4MsLbjHbQ-WNq5SErgaNOMfZdkg2jqKVldZvjW33v8aupx08fzONnuzaYIJBQpONhGzDkYZKkkrewdrYYVl_naNRWsKt8uSVu83G3mLhMPazk"
        + "xNT5CWfNR7sdXfladz8U6ruLFOGUJJ5KDjEVAReRpEbxaKOIY6oFio1TeUQsi6vppLXB0RupTBmE5dr7rxdL4j9eDY94M2uowBDuOsEGA";
    String validPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAu1SU1LfVLPHCozMxH2Mo"
        + "4lgOEePzNm0tRgeLezV6ffAt0gunVTLw7onLRnrq0/IzW7yWR7QkrmBL7jTKEn5u"
        + "+qKhbwKfBstIs+bMY2Zkp18gnTxKLxoS2tFczGkPLPgizskuemMghRniWaoLcyeh"
        + "kd3qqGElvW/VDL5AaWTg0nLVkjRo9z+40RQzuVaE8AkAFmxZzow3x+VJYKdjykkJ"
        + "0iT9wCS0DRTXu269V264Vf/3jvredZiKRkgwlL9xNAwxXFg0x/XFw005UWVRIkdg"
        + "cKWTjpBP2dPwVZ4WWC+9aGVd+Gyn1o0CLelf4rEjGoXbAAEgAqeGUxrcIlbjXfbc" + "mwIDAQAB";

    CustomClaimTokenAuthenticator mock = mock(CustomClaimTokenAuthenticator.class);
    RSAPublicKey pk = loadPublicKey(validPublicKey);
    when(mock.getPublicKey(any())).thenReturn(pk);

    final AuthenticationRequest context = new AuthenticationRequest(ImmutableMap.of(AUTHORIZATION_HEADER_NAME, token));

    when(mock.authenticate(context)).thenCallRealMethod();

    Map<String, Object> config = new HashMap<>();
    config.put("idClaim", "missingClaimInToken");
    config.put("trustedIssuers", getTrustedIssuer());
    doCallRealMethod().when(mock).init(config, null);

    mock.init(config, null);
    Authentication result = mock.authenticate(context);
  }

  private RSAPublicKey loadPublicKey(String publicKey) throws NoSuchAlgorithmException, InvalidKeySpecException {

    byte[] byteKey = Base64.getDecoder().decode(publicKey.getBytes());
    X509EncodedKeySpec x509publicKey = new X509EncodedKeySpec(byteKey);
    KeyFactory kf = null;
    kf = KeyFactory.getInstance("RSA");

    RSAPublicKey key = (RSAPublicKey) kf.generatePublic(x509publicKey);
    return key;
  }

  private LinkedHashMap<String, String> getTrustedIssuer() {
    LinkedHashMap<String, String> trustedIssuer = new LinkedHashMap<>();
    trustedIssuer.putIfAbsent("0", "https://test.com/realm/domain");
    return trustedIssuer;
  }
}