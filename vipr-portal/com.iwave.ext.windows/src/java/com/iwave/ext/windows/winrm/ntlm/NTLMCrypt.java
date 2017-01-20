/*
 * Copyright (c) 2016 Dell EMC Software
 * All Rights Reserved
 */

package com.iwave.ext.windows.winrm.ntlm;

import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * This class handles the specifics of encrypting/decrypting NTLM payloads. The protocol requires a stateful RC4 cipher. The
 * details can be found at https://msdn.microsoft.com/en-us/library/cc236621.aspx or if you're not insane, a more readable
 * (although less official) version can be found at http://davenport.sourceforge.net/ntlm.html.
 * 
 * The NTLM encrypted payload has the following structure (also it's always in LITTLE_ENDIAN)
 * 
 * <pre>
 * Byte     What it is      Value
 * ------------------------------
 * 0        SIGNATURE_SIZE  16
 * 4        VERSION_NUMBER  1
 * 8        SIGNATURE       RC4(HMAC(signingkey, sequence + plaintext)[0-7])
 * 16       SEQUENCE        sequence
 * 20       Encrypted payload
 * </pre>
 *
 */
public class NTLMCrypt {

    /** The computed client signing key. */
    private byte[] clientSigningKey;
    /** The computed client sealing key. */
    private byte[] clientSealingKey;
    /** The computed server signing key. */
    private byte[] serverSigningKey;
    /** The computed server sealing key. */
    private byte[] serverSealingKey;
    /** The RC4 stream for sending stuff. */
    private Cipher clientRc4;
    /** The RC4 stream for receiving stuff. */
    private Cipher serverRc4;
    /** The number of messages we have sent. */
    private int clientSequence;
    /** The number of messages we have received. */
    private int serverSequence;

    /**
     * Initialized the encryption/decryption mechanism with the necessary values.
     * 
     * @param key
     *            the key to use when signing and sealing
     * @throws Exception
     *             if something goes wrong
     */
    public NTLMCrypt(byte[] key) throws Exception {
        // http://davenport.sourceforge.net/ntlm.html#ntlm2SessionSecurity mentions the specifics of what we're doing here
        // http://davenport.sourceforge.net/ntlm.html#appendixC7 is an example of values that work if someone feels like
        // refactoring
        this.clientSigningKey = NTLMUtils.md5(key, NTLMConstants.CLIENTSIGNINGCONSTANT);
        this.clientSealingKey = NTLMUtils.md5(key, NTLMConstants.CLIENTSEALINGCONSTANT);
        this.serverSigningKey = NTLMUtils.md5(key, NTLMConstants.SERVERSIGNINGCONSTANT);
        this.serverSealingKey = NTLMUtils.md5(key, NTLMConstants.SERVERSEALINGCONSTANT);

        clientRc4 = Cipher.getInstance("RC4");
        clientRc4.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(this.clientSealingKey, "RC4"));

        serverRc4 = Cipher.getInstance("RC4");
        serverRc4.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(this.serverSealingKey, "RC4"));

        clientSequence = 0;
        serverSequence = 0;
    }

    /**
     * Encrypts and signs the payload. These operations must be performed atomically.
     * 
     * @param plaintext
     *            the message to encrypt.
     * @return the encrypted message
     */
    public byte[] encryptAndSignPayload(byte[] plaintext) {
        byte[] crypted = clientRc4.update(plaintext);
        byte[] signature = getSignature(plaintext, clientSequence, clientRc4, clientSigningKey);
        clientSequence++;
        return NTLMUtils.concat(NTLMConstants.SIGNATURE_SIZE, signature, crypted);
    }

    /**
     * Decrypts the message and verifies that it matches the signature. These operations must be performed atomically.
     * 
     * @param crypted
     *            the encrypted payload (including signature)
     * @param length
     *            the length of the message (not including signature)
     * @return the decrypted message
     * @throws UnrecognizedNTLMMessageException
     *             if the message is not able to be decrypted
     */
    public byte[] decryptAndVerifyPayload(byte[] crypted, int length) throws UnrecognizedNTLMMessageException {
        if (crypted.length != length + NTLMConstants.ENCRYPTED_HEADER_LENGTH) {
            throw new UnrecognizedNTLMMessageException("Message length is incorrect");
        }
        boolean cryptOperationPerformed = false;
        byte[] plaintext = null;
        byte[] signature = null;
        try {
            byte[] size = Arrays.copyOfRange(crypted, 0, NTLMConstants.SIGNATURE_SIZE.length);
            if (!Arrays.equals(size, NTLMConstants.SIGNATURE_SIZE)) {
                throw new UnrecognizedNTLMMessageException(
                        "Signature is not the right size. The message is probably malformed.");
            }
            byte[] cryptedPayload = Arrays.copyOfRange(crypted, NTLMConstants.ENCRYPTED_HEADER_LENGTH,
                    NTLMConstants.ENCRYPTED_HEADER_LENGTH + length);
            plaintext = serverRc4.update(cryptedPayload);
            signature = getSignature(plaintext, serverSequence, serverRc4, serverSigningKey);
            cryptOperationPerformed = true;
            if (!Arrays.equals(signature, Arrays.copyOfRange(crypted, NTLMConstants.SIGNATURE_SIZE.length,
                    NTLMConstants.ENCRYPTED_HEADER_LENGTH))) {
                throw new UnrecognizedNTLMMessageException("Signature does not match");
            }
            serverSequence++;
        } catch (UnrecognizedNTLMMessageException e) {
            // RC4 is a fun cipher, in that if you encrypt something, and then decrypt it, the key stream goes back to it's
            // initial position. If we hit this block of code, it's because the message that we decrypted isn't the one we
            // were supposed to decrypt. In this case, we reset the key stream to the position it was in before we started.
            if (cryptOperationPerformed) {
                serverRc4.update(signature);
                serverRc4.update(plaintext);
            }
            throw e;
        }
        return plaintext;
    }

    /**
     * Computes the signature operation on the provided plaintext.
     * 
     * @param plaintext
     *            the plaintext
     * @param sequence
     *            the sequence number
     * @param rc4
     *            the cipher to use
     * @param key
     *            the signingkey to use
     * @return the computed signature
     */
    private byte[] getSignature(byte[] plaintext, int sequence, Cipher rc4, byte[] key) {
        byte[] sequenceBytes = NTLMUtils.convertInt(sequence);
        byte[] hmac = NTLMUtils.calculateHmacMD5(key, NTLMUtils.concat(sequenceBytes, plaintext));
        byte[] sig = rc4.update(Arrays.copyOfRange(hmac, 0, 8));
        byte[] signature = NTLMUtils.concat(NTLMConstants.VERSION_NUMBER, sig, sequenceBytes);
        return signature;
    }

}
