/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 *  software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of 
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.security.authentication;

import java.net.URI;

import javax.crypto.SecretKey;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.GenericSerializer;
import com.emc.storageos.db.client.model.SerializationIndex;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.geomodel.TokenResponse;
import com.emc.storageos.security.SignatureHelper;
import com.emc.storageos.security.TokenEncodingDisabler;
import com.emc.storageos.security.authentication.TokenKeyGenerator.KeyIdKeyPair;
import com.emc.storageos.security.authentication.TokenKeyGenerator.TokenKeysBundle;

import static com.emc.storageos.security.authentication.TokenKeyGenerator.TOKEN_SIGNING_ALGO;
import com.emc.storageos.security.exceptions.SecurityException;
import com.emc.storageos.security.geo.GeoClientCacheManager;
import com.emc.storageos.security.geo.InterVDCTokenCacheHelper;
import com.emc.storageos.security.geo.TokenResponseBuilder;
import com.emc.storageos.security.geo.TokenResponseBuilder.TokenResponseArtifacts;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/*
 *  Base64 implementation of the TokenEncoder.  This class encodes and decodes TokenOnWire objects.
 *  Additionally it signs them during encode(), and verifies their signature during decode().
 */
public class Base64TokenEncoder implements TokenEncoder {

    private static final Logger _log = LoggerFactory.getLogger(Base64TokenEncoder.class);
    public static final long VIPR_ENCODING_VERSION = 1000L; // 1.0.0.0

    @Autowired(required = false)
    TokenEncodingDisabler _tokenEncodingDisabler;

    @Autowired
    private TokenKeyGenerator _keyGenerator;

    @Autowired
    private CoordinatorClient _coordinator;

    @Autowired
    private InterVDCTokenCacheHelper interVDCTokenCacheHelper;

    @Autowired
    private GeoClientCacheManager geoClientCacheMgt;

    private final GenericSerializer _serializer = new GenericSerializer();

    /**
     * Initialization method to be called by authsvc
     * 
     * @throws Exception
     */
    public void managerInit() throws Exception {
        _keyGenerator.setCoordinator(_coordinator);
        _keyGenerator.globalInit();
    }

    /**
     * Initialization method to be called by apisvc, syssvc and any token validation apis.
     * 
     * @throws Exception
     */
    public void validatorInit() throws Exception {
        _keyGenerator.setCoordinator(_coordinator);
        _keyGenerator.cacheInit();
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    public void setTokenKeyGenerator(TokenKeyGenerator gen) {
        _keyGenerator = gen;
    }

    public void setInterVDCTokenCacheHelper(InterVDCTokenCacheHelper helper) {
        interVDCTokenCacheHelper = helper;
    }

    /**
     * Takes a TokenOnwire class, serializes it, generates a signature based on
     * a key, and serialized token, combines the serialized token and its
     * signature. Serialize that, base64 encodes it and returns it as a String.
     * 
     */
    @Override
    public String encode(TokenOnWire tw) {
        try {
            // if encoding is disabled, just return the token id as a string
            if (_tokenEncodingDisabler != null) {
                return tw.getTokenId().toString();
            }

            // obtain key artifacts
            KeyIdKeyPair pair = tw.isProxyToken() ? _keyGenerator.getProxyTokenSignatureKeyPair() :
                    _keyGenerator.getCurrentTokenSignatureKeyPair();
            tw.setEncryptionKeyId(pair.getEntry());

            // serialize token
            byte[] rawTokenBytes = _serializer.toByteArray(TokenOnWire.class, tw);

            // sign serialized token
            String signature = SignatureHelper.sign2(rawTokenBytes, pair.getKey(),
                    TOKEN_SIGNING_ALGO);

            // serialize/base64 encode final String.
            SignedToken st = new SignedToken(rawTokenBytes, signature);
            byte[] rawSignedTokenBytes = _serializer.toByteArray(SignedToken.class, st);
            byte[] encodedBytes = Base64.encodeBase64(rawSignedTokenBytes);
            return new String(encodedBytes, "UTF-8");
        } catch (Exception ex) {
            throw APIException.unauthorized.unableToEncodeToken(ex);
        }
    }

    /**
     * base64 decodes the supplied String into a SignedToken object, extracts the signature,
     * deserializes the TokenOnWire object and reads its key id. Fetches the key id from the
     * key generator. Computes a signature based on the fetched key and the serialized token.
     * Compares the resulting signature to the one included in the SignedToken object. If
     * they don't match, throw. if they do, return the deserialized TokenOnWire object.
     * 
     * @throws SecurityException
     */
    @Override
    public TokenOnWire decode(String encodedToken) throws SecurityException {
        try {
            // if encoding is disabled, just parse the string as a token id directly
            if (_tokenEncodingDisabler != null) {
                return new TokenOnWire(URI.create(encodedToken));
            }

            // decode the bytes from the string
            byte[] decoded = Base64.decodeBase64(encodedToken.getBytes("UTF-8"));
            // Get the signedtoken object from the decoded bytes.
            SignedToken st = _serializer.fromByteArray(SignedToken.class, decoded);
            // deserialize the TokenOnWire object
            TokenOnWire tw = _serializer.fromByteArray(TokenOnWire.class, st.getTokenBody());

            // At this point, we know if this token was issued by this VDC or not.
            // If not our VDC, check the cache for keys, if not found, make a request.
            SecretKey foreignKey = null;
            String vdcId = URIUtil.parseVdcIdFromURI(tw.getTokenId());
            if (vdcId == null) {
                _log.info("Old token from ViPR 1.1 - treating token as local");
            } else if (!tw.isProxyToken() &&
                    !VdcUtil.getLocalShortVdcId().equals(vdcId)) {
                foreignKey = getForeignKey(tw, encodedToken);
            } else {
                _log.info("Token VDCid {} matches that of this VDC {}", vdcId, VdcUtil.getLocalShortVdcId());
            }

            // get the key id that the token was encoded with
            String key = tw.getEncryptionKeyId();
            // fetch the corresponding key from the key generator. if the key has been rotated out, we are done.
            SecretKey skey = foreignKey == null ? _keyGenerator.getTokenSignatureKey(key) : foreignKey;
            if (skey == null) {
                String error = String.format("The key id %s provided in the token could not be matched to secret key", key);
                _log.error(error);
                throw SecurityException.fatals.keyIDCouldNotBeMatchedToSecretKey(key);
            }
            // The key is still present in the system. Compute a signature with the fetched key against
            // the body of the supplied token
            String signatureFromToken = st._signature;
            String computedSignature = SignatureHelper.sign2(st._tokenBody, skey,
                    TOKEN_SIGNING_ALGO);
            // compare the computed signature against the supplied one.
            if (!signatureFromToken.equals(computedSignature)) {
                String error = String.format("The signature on the provided token does not validate");
                _log.error(error);
                throw APIException.unauthorized
                        .unableToDecodeTokenTheSignatureDoesNotValidate();
            }
            return tw;
        } catch (Exception ex) {
            throw APIException.unauthorized.unableToDecodeToken(ex);
        }
    }

    /**
     * Attempts to get a secret key from the provided tokenonwire.
     * First attempts from cache, then makes a call to originator vdc if not
     * found in cache.
     * 
     * @param tw
     * @param encodedToken
     * @return
     */
    private SecretKey getForeignKey(TokenOnWire tw, String encodedToken) {
        String vdcId = URIUtil.parseVdcIdFromURI(tw.getTokenId());
        _log.info("Token received from another VDC: {}.  Looking in cache for keys", vdcId);
        SecretKey foreignKey = interVDCTokenCacheHelper.getForeignSecretKey(vdcId, tw.getEncryptionKeyId());
        if (foreignKey == null) {
            TokenKeysBundle bundle = interVDCTokenCacheHelper.getTokenKeysBundle(vdcId);
            try {
                // check if the requested key id falls within reasonable range
                if (bundle != null && !interVDCTokenCacheHelper.
                        sanitizeRequestedKeyIds(bundle, tw.getEncryptionKeyId())) {
                    return null;
                }
                TokenResponse response = geoClientCacheMgt.getGeoClient(vdcId)
                        .getToken(encodedToken,
                                bundle == null ? "0" : bundle.getKeyEntries().get(0),
                                bundle == null ? "0" : bundle.getKeyEntries().size() == 2 ?
                                        bundle.getKeyEntries().get(1) : null);
                if (response != null) {
                    TokenResponseArtifacts artifacts = TokenResponseBuilder.parseTokenResponse(response);
                    interVDCTokenCacheHelper.cacheForeignTokenAndKeys(artifacts, vdcId);
                    return interVDCTokenCacheHelper.getForeignSecretKey(vdcId, tw.getEncryptionKeyId());
                } else {
                    _log.error("Null response from getForeignToken call.  It's possible remote vdc is not reachable.");
                }
            } catch (Exception e) {
                _log.error("Could not validate foreign token ", e);
            }
        } else {
            _log.info("Key found in cache");
        }
        return foreignKey;
    }

    /**
     * Class to encapsulate the token body as a byte array + signature concatenation
     */
    public static class SignedToken {
        // for ViPR versioning
        private long _tokenEncodingVersion = VIPR_ENCODING_VERSION;
        private byte[] _tokenBody;
        private String _signature;

        /**
         * for deserialization
         */
        public SignedToken() {
        }

        /**
         * Creates a signed token with the body and signature provided
         * 
         * @param body
         * @param signature
         */
        // Not an real issue as no write op in class
        public SignedToken(final byte[] body, String signature) { // NOSONAR
                                                                  // ("Suppressing: The user-supplied array 'body' is stored directly")
            _tokenBody = body;
            _signature = signature;
        }

        /**
         * Encoding version is fixed, will used for backward compatibility
         * 
         * @return version
         */
        @SerializationIndex(2)
        public long getTokenEncodingVersion() {
            return _tokenEncodingVersion;
        }

        // setter is there because of property based deserialization
        public void setTokenEncodingVersion(long v) {
            _tokenEncodingVersion = v;
        }

        /**
         * gets the token body
         * 
         * @return
         */
        @SerializationIndex(3)
        public byte[] getTokenBody() {
            // Not an real issue as no write op outside
            return _tokenBody; // NOSONAR ("Suppressing: Returning '_tokenBody' may expose an internal array.")
        }

        /**
         * sets the token body
         * 
         * @param body
         */
        // Not an real issue as no write op in class
        public void setTokenBody(byte[] body) { // NOSONAR ("Suppressing: The user-supplied array 'body' is stored directly")
            _tokenBody = body;
        }

        /**
         * gets the signature
         * 
         * @return
         */
        @SerializationIndex(4)
        public String getSignature() {
            return _signature;
        }

        /**
         * sets the signature
         * 
         * @param signature
         */
        public void setSignature(String signature) {
            _signature = signature;
        }

    }
}
