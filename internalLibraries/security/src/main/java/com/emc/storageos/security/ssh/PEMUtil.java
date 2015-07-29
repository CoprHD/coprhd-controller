/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.ssh;

import com.emc.storageos.security.exceptions.SecurityException;
import com.emc.storageos.security.keystore.impl.Base64Util;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.*;
import java.security.spec.ECPoint;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;

/**
 * PEM encoding utils for ssl and ssh. Append some information when encoding public keys for ssh
 */
public class PEMUtil {
    public static final String PRIVSTE_KEY_HEADER = "-----BEGIN PRIVATE KEY-----\n";
    public static final String PRIVATE_KEY_FOOTER = "-----END PRIVATE KEY-----";
    static final String PRIVATE_KEY_FOOTER_WITH_NEWLINE = "\n" + PRIVATE_KEY_FOOTER + "\n";

    public static boolean isPKCS8Key(String privateKey) {
        return privateKey.contains(PRIVSTE_KEY_HEADER);
    }

    public static byte[] extractPKCS8Key(String privateKey) {
        String encodedKey = privateKey.replace(PEMUtil.PRIVSTE_KEY_HEADER, "");
        encodedKey = encodedKey.replace(PEMUtil.PRIVATE_KEY_FOOTER, "");

        return Base64.decodeBase64(encodedKey);
    }

    public static byte[] decodePKCS8PrivateKey(String privateKey) throws Exception {
        String encodedKey = privateKey.replace(PEMUtil.PRIVSTE_KEY_HEADER, "");
        encodedKey = encodedKey.replace(PEMUtil.PRIVATE_KEY_FOOTER, "");

        byte[] buf = Base64.decodeBase64(encodedKey);

        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(buf);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(keySpec).getEncoded();
    }

    /**
     * encode private key into PKCS8 PEM String
     * 
     * @param keyBytes
     * @return
     */
    public static String encodePrivateKey(byte[] keyBytes) {

        byte[] b64Key = Base64Util.encodeWithNewLine(keyBytes);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        out.write(PRIVSTE_KEY_HEADER.getBytes(), 0, PRIVSTE_KEY_HEADER.length());
        out.write(b64Key, 0, b64Key.length);

        // if the length of encoded key is multiple of 64, \n is appended by base64 lib. so don't append again.
        String footer = (b64Key[b64Key.length - 1] == '\n') ?
                PRIVATE_KEY_FOOTER : PRIVATE_KEY_FOOTER_WITH_NEWLINE;
        out.write(footer.getBytes(), 0, footer.length());

        return out.toString().replace("\0", "").replace("\r", "");
    }

    public static String encodeRSAPubKey(byte[] keyBytes, String user) throws Exception {

        PublicKey publicKey = null;
        try {
            publicKey = KeyFactory.getInstance(SSHParam.KeyAlgo.RSA.name()).
                    generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw SecurityException.fatals.failedToLoadPublicKey(e);
        }

        String publicKeyEncoded;
        try (ByteArrayOutputStream byteOs = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(byteOs)) {

            if (!publicKey.getAlgorithm().equals("RSA")) {
                throw new IllegalArgumentException("Unknown public key encoding: "
                        + publicKey.getAlgorithm());
            }

            RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;
            dos.writeInt("ssh-rsa".getBytes().length);
            dos.write("ssh-rsa".getBytes());
            dos.writeInt(rsaPublicKey.getPublicExponent().toByteArray().length);
            dos.write(rsaPublicKey.getPublicExponent().toByteArray());
            dos.writeInt(rsaPublicKey.getModulus().toByteArray().length);
            dos.write(rsaPublicKey.getModulus().toByteArray());
            publicKeyEncoded = "ssh-rsa ";

            publicKeyEncoded += new String(Base64.encodeBase64(byteOs.toByteArray()));
            if (StringUtils.isNotBlank(user)) {
                publicKeyEncoded += " " + user;
            }
        }

        return publicKeyEncoded;
    }

    public static String encodeDSAPubKey(byte[] keyBytes) throws IOException {

        String publicKeyEncoded = null;
        String userName = null;

        PublicKey publicKey = null;
        try {
            publicKey = KeyFactory.getInstance(SSHParam.KeyAlgo.DSA.name()).
                    generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw SecurityException.fatals.failedToLoadPublicKey(e);
        }

        try (ByteArrayOutputStream byteOs = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(byteOs)) {

            DSAPublicKey dsaPublicKey = (DSAPublicKey) publicKey;
            DSAParams dsaParams = dsaPublicKey.getParams();
            dos.writeInt("ssh-dss".getBytes().length);
            dos.write("ssh-dss".getBytes());
            dos.writeInt(dsaParams.getP().toByteArray().length);
            dos.write(dsaParams.getP().toByteArray());
            dos.writeInt(dsaParams.getQ().toByteArray().length);
            dos.write(dsaParams.getQ().toByteArray());
            dos.writeInt(dsaParams.getG().toByteArray().length);
            dos.write(dsaParams.getG().toByteArray());
            dos.writeInt(dsaPublicKey.getY().toByteArray().length);
            dos.write(dsaPublicKey.getY().toByteArray());
            publicKeyEncoded = "ssh-dss ";

            publicKeyEncoded += new String(Base64.encodeBase64(byteOs.toByteArray()));
            if (userName != null && !userName.isEmpty()) {
                publicKeyEncoded += " " + userName;
            }
        }
        return publicKeyEncoded;
    }

    public static String encodeECPubKey(byte[] keyBytes) throws IOException {
        String publicKeyEncoded = "";

        try (ByteArrayOutputStream byteOs = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(byteOs)) {

            PublicKey publicKey = null;
            try {
                publicKey = KeyFactory.getInstance(SSHParam.KeyAlgo.ECDSA.name()).
                        generatePublic(new X509EncodedKeySpec(keyBytes));
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                throw SecurityException.fatals.failedToLoadPublicKey(e);
            }

            ECPublicKey ecPubKey = (ECPublicKey) publicKey;

            String curveName = null;
            int fieldSize = ecPubKey.getParams().getCurve().getField().getFieldSize();
            switch (fieldSize) {
                case 256:
                    curveName = "nistp256";
                    break;
                case 384:
                    curveName = "nistp384";
                    break;
                case 521:
                    curveName = "nistp521";
                    break;
            }

            String fullName = "ecdsa-sha2-" + curveName;

            dos.writeInt(fullName.getBytes().length);
            dos.write(fullName.getBytes());
            dos.writeInt(curveName.getBytes().length);
            dos.write(curveName.getBytes());

            ECPoint group = ecPubKey.getW();
            int elementSize = (ecPubKey.getParams().getCurve().getField().getFieldSize() + 7) / 8;
            byte[] M = new byte[2 * elementSize + 1];
            M[0] = 0x04;

            byte[] affineX = group.getAffineX().toByteArray();
            int startPos = dropWhile(affineX, 0x00);
            int length = affineX.length - startPos;

            System.arraycopy(affineX, startPos, M, 1 + elementSize - length, length);

            byte[] affineY = group.getAffineY().toByteArray();
            startPos = dropWhile(affineY, 0x00);
            length = affineY.length - startPos;

            System.arraycopy(affineY, startPos, M, 1 + elementSize + elementSize - length, length);

            dos.writeInt(M.length);
            dos.write(M);

            publicKeyEncoded = fullName + " ";
            publicKeyEncoded += new String(Base64.encodeBase64(byteOs.toByteArray()));
        }

        return publicKeyEncoded;
    }

    private static int dropWhile(byte[] buf, int x) {
        int i = 0;
        for (i = 0; i < buf.length; i++) {
            if (buf[i] != x) {
                break;
            }
        }

        return i;
    }
}
