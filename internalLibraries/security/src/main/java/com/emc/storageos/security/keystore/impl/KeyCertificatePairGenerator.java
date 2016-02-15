/*
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.keystore.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

import com.emc.storageos.security.helpers.SecurityUtil;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sun.security.x509.AlgorithmId;
import sun.security.x509.AuthorityKeyIdentifierExtension;
import sun.security.x509.CertificateAlgorithmId;
import sun.security.x509.CertificateExtensions;
import sun.security.x509.CertificateSerialNumber;
import sun.security.x509.CertificateValidity;
import sun.security.x509.CertificateVersion;
import sun.security.x509.CertificateX509Key;
import sun.security.x509.DNSName;
import sun.security.x509.GeneralName;
import sun.security.x509.GeneralNames;
import sun.security.x509.IPAddressName;
import sun.security.x509.KeyIdentifier;
import sun.security.x509.SubjectAlternativeNameExtension;
import sun.security.x509.SubjectKeyIdentifierExtension;
import sun.security.x509.X500Name;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CertInfo;

import com.emc.storageos.security.exceptions.SecurityException;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * Class responsible for generating RSA keys and their certificates.
 */
public class KeyCertificatePairGenerator {

    private static Logger log = LoggerFactory.getLogger(KeyCertificatePairGenerator.class);

    public static final String CERTIFICATE_COMMON_NAME_FORMAT = "CN=%s";
    public static final String PRIVATE_RSA_KEY_PEM_FORMAT_NAME =
            "SSLCPKCS1RSAPrivateKeyPEM";
    public static final String PRIVATE_RSA_KEY_BER_FORMAT_NAME = "RSAPrivateKeyBER";
    public static final String RSA_JAVA_DEVICE_NAME = "Java";
    private static final int SUBJECT_ALT_NAME_DNS_NAME = 2;
    private static final int SUBJECT_ALT_NAME_IP_ADDRESS = 7;
    private static final int PEM_OUTPUT_LINE_SIZE = 64;

    public static final String PEM_BEGIN_CERT = "-----BEGIN CERTIFICATE-----";
    public static final String PEM_END_CERT = "-----END CERTIFICATE-----";

    public static final String PEM_BEGIN_RSA_PRIVATE_KEY = "-----BEGIN RSA PRIVATE KEY-----";
    public static final String PEM_END_RSA_PRIVATE_KEY = "-----END RSA PRIVATE KEY-----";

    private KeyCertificateAlgorithmValuesHolder valuesHolder;

    public void setKeyCertificateAlgorithmValuesHolder(
            KeyCertificateAlgorithmValuesHolder valuesHolder) {
        this.valuesHolder = valuesHolder;
    }

    /**
     * Create a self-signed X.509 Certificate
     * 
     * @param pair the KeyPair
     */
    private X509Certificate generateCertificate(KeyPair pair)
            throws GeneralSecurityException, IOException {

        PublicKey pubKey = loadPublicKeyFromBytes(pair.getPublic().getEncoded());

        PrivateKey privkey = pair.getPrivate();
        X509CertInfo info = new X509CertInfo();

        Date from = getNotBefore();
        Date to =
                new Date(from.getTime() + valuesHolder.getCertificateValidityInDays()
                        * 86400000L);
        CertificateValidity interval = new CertificateValidity(from, to);
        BigInteger sn = new BigInteger(64, new SecureRandom());
        X500Name owner =
                new X500Name(String.format(CERTIFICATE_COMMON_NAME_FORMAT,
                        valuesHolder.getCertificateCommonName()));

        info.set(X509CertInfo.VALIDITY, interval);
        info.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(sn));
        info.set(X509CertInfo.SUBJECT, owner);
        info.set(X509CertInfo.ISSUER, owner);
        info.set(X509CertInfo.KEY, new CertificateX509Key(pubKey));
        info.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));
        AlgorithmId keyAlgo =
                AlgorithmId
                        .get(KeyCertificateAlgorithmValuesHolder.DEFAULT_KEY_ALGORITHM);
        info.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(keyAlgo));
        AlgorithmId signingAlgo = AlgorithmId.get(valuesHolder.getSigningAlgorithm());
        info.set(CertificateAlgorithmId.NAME + "." + CertificateAlgorithmId.ALGORITHM,
                signingAlgo);

        // add extensions
        CertificateExtensions ext = new CertificateExtensions();

        ext.set(SubjectKeyIdentifierExtension.NAME,
                new SubjectKeyIdentifierExtension(new KeyIdentifier(pubKey).getIdentifier()));

        // CA public key is the same as our public key (self signed)
        ext.set(AuthorityKeyIdentifierExtension.NAME,
                new AuthorityKeyIdentifierExtension(new KeyIdentifier(pubKey), null, null));

        ext.set(SubjectAlternativeNameExtension.NAME, new SubjectAlternativeNameExtension(subjectAltNames()));

        info.set(X509CertInfo.EXTENSIONS, ext);

        X509CertImpl cert = new X509CertImpl(info);
        cert.sign(privkey, valuesHolder.getSigningAlgorithm());

        return cert;
    }

    private GeneralNames subjectAltNames() throws IOException{
        // Consists of VIP and internal node's IPs (IPv4 and IPV6 if have) but no DNS/Host name.
        GeneralNames subAltNames = new GeneralNames();

        for (InetAddress entry : valuesHolder.getAddresses()) {
            subAltNames.add(new GeneralName(new IPAddressName(entry.getHostAddress())));
        }
        return subAltNames;
    }

    /**
     * Set Not Before to a few days in past just in case system runs into problem due to bad system clock.
     * 
     * @return
     */
    private Date getNotBefore() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, valuesHolder.getNotBeforeOffset());
        return cal.getTime();
    }

    public KeyCertificateEntry generateKeyCertificatePair() throws SecurityException {

        KeyPair keyPair = generateKeyPair();

        KeyCertificateEntry returnedEntry;
        try {
            Certificate cert = generateCertificate(keyPair);
            Certificate[] chain = { cert };
            returnedEntry = new KeyCertificateEntry(keyPair.getPrivate(), chain);
        } catch (GeneralSecurityException e) {
            throw SecurityException.fatals.failedToCreateCertificate(e);
        } catch (IOException e) {
            throw SecurityException.fatals.failedToCreateCertificate(e);
        } finally {
            // Cryptographic objects should be cleared once they are no longer
            // needed.
            SecurityUtil.clearSensitiveData(keyPair.getPublic());
            SecurityUtil.clearSensitiveData(keyPair.getPrivate());
            SecurityUtil.clearSensitiveData(keyPair);
        }

        return returnedEntry;
    }

    public static PrivateKey loadPrivateKeyFromBytes(byte[] keyBytes)
            throws SecurityException, NoSuchAlgorithmException {
        try {
            KeyFactory keyFactory =
                    KeyFactory
                            .getInstance(KeyCertificateAlgorithmValuesHolder.DEFAULT_KEY_ALGORITHM);
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (InvalidKeySpecException e) {
            throw SecurityException.fatals.failedToLoadPrivateKey(e);
        }
    }

    public static PublicKey loadPublicKeyFromBytes(byte[] keyBytes)
            throws SecurityException, NoSuchAlgorithmException {

        try {
            KeyFactory keyFactory =
                    KeyFactory.getInstance(KeyCertificateAlgorithmValuesHolder.DEFAULT_KEY_ALGORITHM);
            return keyFactory.generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (InvalidKeySpecException e) {
            throw SecurityException.fatals.failedToLoadPublicKey(e);
        }
    }

    /**
     * verifies that the specified key matches the specified certificate
     * 
     * @param entryToVerify
     * @throws SecurityException if the certificate specified is not x509 certificate, or if validation
     *             fails
     */
    public static void verifyKeyCertificateEntry(KeyCertificateEntry entryToVerify)
            throws SecurityException {
        String signThis = "Sign this to verify that the key and certificate match";
        Signature signatureFactory = null;
        byte[] signature;
        PrivateKey key = null;

        try {
            // we only accept x509certificates
            if (!(entryToVerify.getCertificateChain()[0] instanceof X509Certificate)) {
                throw SecurityException.fatals.certificateMustBeX509();
            }
            X509Certificate cert =
                    (X509Certificate) entryToVerify.getCertificateChain()[0];
            key = loadPrivateKeyFromBytes(entryToVerify.getKey());
            signatureFactory =
                    Signature.getInstance(cert.getSigAlgName());
            signatureFactory.initSign(key);
            signatureFactory.update(signThis.getBytes());
            signature = signatureFactory.sign();
            signatureFactory.initVerify(entryToVerify.getCertificateChain()[0]
                    .getPublicKey());
            signatureFactory.update(signThis.getBytes());
            if (!signatureFactory.verify(signature)) {
                throw APIException.badRequests.keyCertificateVerificationFailed();
            }
        } catch (NoSuchAlgorithmException e) {
            throw APIException.badRequests.keyCertificateVerificationFailed(e);
        } catch (InvalidKeyException e) {
            throw APIException.badRequests.keyCertificateVerificationFailed(e);
        } catch (SignatureException e) {
            throw APIException.badRequests.keyCertificateVerificationFailed(e);
        } finally {
            // all are used here. so clear
            SecurityUtil.clearSensitiveData(signatureFactory);
            SecurityUtil.clearSensitiveData(key);
        }
    }

    /**
     * @param certificateChainString the certificate chain in PEM format or crt format. other formats, such
     *            as DER, p7b and p7c are not supported from BouncyCastle docs: At the
     *            moment this will deal with "-----BEGIN CERTIFICATE-----" to
     *            "-----END CERTIFICATE-----" base 64 encoded certs, as well as the BER
     *            binaries of certificates and some classes of PKCS#7 objects.
     * @return the certificate chain as Certificate[]
     * @throws CertificateException if parsing of the certificate chain has failed
     */
    public static Certificate[] getCertificateChainFromString(
            String certificateChainString) throws CertificateException {

        InputStream inStream =
                new ByteArrayInputStream(certificateChainString.getBytes());

        CertificateFactory certFactory =
                CertificateFactory.getInstance("X.509");
        Collection<? extends Certificate> certs =
                certFactory.generateCertificates(inStream);
        return certs.toArray(new Certificate[certs.size()]);
    }

    /**
     * @param certificateStr the certificate chain in PEM format or crt format. other formats, such
     *            as DER, p7b and p7c are not supported from BouncyCastle docs: At the
     *            moment this will deal with "-----BEGIN CERTIFICATE-----" to
     *            "-----END CERTIFICATE-----" base 64 encoded certs, as well as the BER
     *            binaries of certificates and some classes of PKCS#7 objects.
     * @return the certificate chain as Certificate[]
     * @throws CertificateException if parsing of the certificate chain has failed
     */
    public static Certificate getCertificateFromString(String certificateStr)
            throws CertificateException {
        InputStream inStream = new ByteArrayInputStream(certificateStr.getBytes());
        CertificateFactory certFactory =
                CertificateFactory.getInstance("X.509");
        return certFactory.generateCertificate(inStream);
    }

    /**
     * @param certChain - the certificate chain to parse to PEM format
     * @return the specified certificate chain in PEM format
     * @throws IOException
     * @throws CertificateEncodingException
     */
    public static String getCertificateChainAsString(Certificate[] certChain)
            throws CertificateEncodingException {
        StringBuilder builder = new StringBuilder();
        Base64 encoder = new Base64(PEM_OUTPUT_LINE_SIZE);
        boolean isFirst = true;
        for (Certificate certificate : certChain) {
            if (!isFirst) {
                builder.append(System.lineSeparator());
            }
            builder.append(PEM_BEGIN_CERT);
            builder.append(System.lineSeparator());
            builder.append(encoder.encodeAsString(certificate.getEncoded()));
            builder.append(PEM_END_CERT);
            isFirst = false;
        }
        return builder.toString();
    }

    /**
     * @param cert - the certificate to parse to PEM format
     * @return the specified certificate in PEM format
     * @throws IOException
     * @throws CertificateEncodingException
     */
    public static String getCertificateAsString(Certificate cert)
            throws CertificateEncodingException {
        Certificate[] chain = { cert };
        return getCertificateChainAsString(chain);
    }

    /**
     * Checks if the specified certificate's IPs match the cluste's IPs
     * 
     * @param cert
     * @throws IllegalArgumentException when the certificate was not created by this generator
     */
    public boolean isCertificateIPsCorrect(X509Certificate cert)
            throws IllegalArgumentException {
        valuesHolder.loadIPsAndNames();
        Set<InetAddress> foundIPs = new HashSet<InetAddress>();
        try {
            for (List<?> element : cert.getSubjectAlternativeNames()) {
                int OID = ((Integer) element.get(0)).intValue();
                String name;
                if (OID == SUBJECT_ALT_NAME_IP_ADDRESS) {
                    name = (String) element.get(1);
                    log.debug("got the following ip from the cert: " + name);
                    foundIPs.add(InetAddress.getByName(name.trim()));
                } else if (OID != SUBJECT_ALT_NAME_DNS_NAME) {
                    throw new IllegalArgumentException("cert is not self generated");
                }
            }
        } catch (CertificateParsingException e) {
            throw new IllegalArgumentException("cert is not self generated");
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("cert has illegal ip values");
        }

        return valuesHolder.getAddresses().equals(foundIPs);
    }

    /**
     * gets the specified key as it's pem representation
     * 
     * @param keyToParse
     * @return
     */

    /*
     * public static String getPrivateKeyAsPEMString(Key keyToParse)
     * throws IOException, JSAFE_UnimplementedException,
     * JSAFE_InvalidParameterException, JSAFE_InvalidKeyException {
     * 
     * JSAFE_PrivateKey privKey = null;
     * 
     * try {
     * privKey = JSAFE_PrivateKey.getInstance(
     * KeyCertificateAlgorithmValuesHolder.DEFAULT_KEY_ALGORITHM,
     * RSA_JAVA_DEVICE_NAME);
     * privKey.setKeyData(PRIVATE_RSA_KEY_BER_FORMAT_NAME,
     * new byte[][]{keyToParse.getEncoded()});
     * 
     * byte[][] pemData = privKey.getKeyData(PRIVATE_RSA_KEY_PEM_FORMAT_NAME);
     * 
     * // this new string and the following substring operations cause key copied in memory and I don't now how to clear.
     * String pemStr = new String(pemData[0]);
     * int index = pemStr.indexOf('\n');
     * index++;
     * index += PEM_OUTPUT_LINE_SIZE;
     * int endIndex = pemStr.indexOf("-----END");
     * StringBuilder builder = new StringBuilder();
     * builder.append(pemStr.substring(0, index));
     * while (index < endIndex) {
     * builder.append(System.lineSeparator());
     * builder.append(pemStr.substring(index, index + PEM_OUTPUT_LINE_SIZE));
     * index += PEM_OUTPUT_LINE_SIZE;
     * }
     * builder.append(pemStr.substring(index));
     * return builder.toString();
     * } finally {
     * if (privKey != null) {
     * privKey.clearSensitiveData();
     * }
     * }
     * }
     */

    /**
     * Loads up the certificate that was set in v1 system property
     * 
     * @return the certificate if it existed and parsed successfully, null otherwise
     */
    public KeyCertificateEntry tryGetV1Cert() {
        String pemCertAndKey = valuesHolder.getV1Cert();
        KeyCertificateEntry returnedEntry = null;
        if (!StringUtils.isBlank(pemCertAndKey)) {
            int pemKeyStart = pemCertAndKey.indexOf(PEM_BEGIN_RSA_PRIVATE_KEY);
            int pemKeyEnd =
                    pemCertAndKey.indexOf(PEM_END_RSA_PRIVATE_KEY)
                            + PEM_END_RSA_PRIVATE_KEY.length();
            int pemCertStart = pemCertAndKey.indexOf(PEM_BEGIN_CERT);
            int pemCertEnd = pemCertAndKey.lastIndexOf(PEM_END_CERT) + PEM_END_CERT.length();
            log.info("pemKeyStart = " + pemKeyStart + ", pemKeyEnd = " + pemKeyEnd + ", pemCertStart = " + pemCertStart + ", pemCertEnd = "
                    + pemCertEnd);
            if (pemKeyStart != -1 && pemKeyEnd != -1 && pemCertStart != -1
                    && pemCertEnd != -1) {
                String pemKey = pemCertAndKey.substring(pemKeyStart, pemKeyEnd);
                String pemCert = pemCertAndKey.substring(pemCertStart, pemCertEnd);

                // multiline values are stored in coordinator with "\\n" instead of "\n"
                pemKey = StringUtils.replace(pemKey, "\\n", "\n");
                pemCert = StringUtils.replace(pemCert, "\\n", "\n");

                log.info("pemCert = " + pemCert);

                try {
                    Certificate[] certChain = getCertificateChainFromString(pemCert);
                    // don't support v1 cert for open source version.
                    byte[] keyBytes = SecurityUtil.loadPrivateKeyFromPEMString(pemKey);
                    if (!ArrayUtils.isEmpty(certChain) && !ArrayUtils.isEmpty(keyBytes)) {
                        log.info("parsed key and certificate successfully");
                        returnedEntry = new KeyCertificateEntry(keyBytes, certChain);
                    }
                } catch (CertificateException e) {
                    log.error("Could not load v1 certificate chain", e);
                } catch (Exception e) {
                    log.error("Could not load v1 key", e);
                }
            }
        }

        return returnedEntry;
    }

    /**
     * Generates a key pair
     * 
     * @return
     */
    public KeyPair generateKeyPair() {
        KeyPairGenerator keyGen = null;
        SecureRandom random = null;
        try {

            random = SecureRandom.getInstance(SecurityUtil.getSecuredRandomAlgorithm());
            keyGen =
                    KeyPairGenerator.getInstance(
                            KeyCertificateAlgorithmValuesHolder.DEFAULT_KEY_ALGORITHM);
            keyGen.initialize(valuesHolder.getKeySize(), random);
            return keyGen.generateKeyPair();
        } catch (Exception e) {
            throw SecurityException.fatals.noSuchAlgorithmException(
                    SecurityUtil.getSecuredRandomAlgorithm(), e);
        } finally {
            if (keyGen != null) {
                SecurityUtil.clearSensitiveData(keyGen);
            }
            if (random != null) {
                SecurityUtil.clearSensitiveData(random);
            }
        }
    }

    public static void validateKeyAndCertPairing(RSAPrivateKey privateKey, Certificate[] certChain) {
        KeyCertificateEntry entry = new KeyCertificateEntry(privateKey.getEncoded(), certChain);
        verifyKeyCertificateEntry(entry);
    }
}
