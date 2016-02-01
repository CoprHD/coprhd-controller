/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.ssh;

import com.emc.storageos.security.exceptions.SecurityException;
import com.emc.storageos.security.helpers.SecurityUtil;
import com.emc.storageos.security.keystore.impl.KeyCertificateAlgorithmValuesHolder;

import java.security.*;
import java.security.spec.ECGenParameterSpec;

/**
 * DSA and EC Key pair generation for SSH service. RSA is not supported
 */
public class SSHKeyPairGenerator {

    public final static String RANDOM_ECDRBG = "ECDRBG";
    public final static String ALGO_EC_P521 = "P521";
    public final static int DSA_KEY_SIZE = 1024;

    SSHParam.KeyAlgo algo;

    SSHKeyPairGenerator(SSHParam.KeyAlgo algo) {
        this.algo = algo;
    }

    public static SSHKeyPairGenerator getInstance(SSHParam.KeyAlgo algo) {
        return new SSHKeyPairGenerator(algo);
    }

    public SSHKeyPair generate() {
        switch (algo) {
            case DSA:
                return generatePairForDSA();
            case ECDSA:
                return generatePairForEC();
            case RSA:
                return generatePairForRSA();
            default:
                throw SecurityException.fatals.notSupportAlgorithm(algo.name());
        }
    }

    private SSHKeyPair generatePairForRSA() {
        try {
            SecureRandom random = SecurityUtil.getSecureRandomInstance();

            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(
                    KeyCertificateAlgorithmValuesHolder.DEFAULT_KEY_ALGORITHM);

            keyGen.initialize(KeyCertificateAlgorithmValuesHolder.FIPS_MINIMAL_KEY_SIZE, random);

            return SSHKeyPair.toKeyPair(keyGen.generateKeyPair());
        } catch (Exception e) {
            throw SecurityException.fatals.noSuchAlgorithmException(
                    SecurityUtil.getSecuredRandomAlgorithm(), e);
        }
    }

    public SSHKeyPair generatePairForEC() {

        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(SSHParam.KeyAlgo.ECDSA.name());

            SecureRandom random = SecureRandom.getInstance(RANDOM_ECDRBG);

            ECGenParameterSpec ecParamSpec = new ECGenParameterSpec(ALGO_EC_P521);

            keyGen.initialize(ecParamSpec, random);

            KeyPair keyPair = keyGen.generateKeyPair();

            return SSHKeyPair.toKeyPair(keyPair);
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw SecurityException.fatals.failToGenerateKeypair(algo.name(), e);
        }
    }

    public SSHKeyPair generatePairForDSA() {

        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(SSHParam.KeyAlgo.DSA.name());

            keyGen.initialize(DSA_KEY_SIZE);

            KeyPair keyPair = keyGen.generateKeyPair();

            return SSHKeyPair.toKeyPair(keyPair);
        } catch (NoSuchAlgorithmException e) {
            throw SecurityException.fatals.failToGenerateKeypair(algo.name(), e);
        }
    }
}
