/*
 * Copyright (c) 2016 Dell EMC Software
 * All Rights Reserved
 */

package com.iwave.ext.windows.winrm.ntlm;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.EndianUtils;
import org.apache.commons.lang3.CharEncoding;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AUTH;
import org.apache.http.message.BasicHeader;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A collection of useful methods and constants for use by the NTLM protocol.
  */
public final class NTLMUtils {

    /**
     * Bouncy castle provider, required for MD4.
     */
    private static final Provider PROVIDER = new BouncyCastleProvider();

    /** Constant for HMAC-MD5. */
    private static final String HMAC_MD5_NAME = "HmacMD5";
    /** Constant for the negotitate that needs to be prepended to the header. */
    private static final String NEGOTIATE_WITH_SPACE = "Negotiate ";
    /** The length of the negotiate message. */
    private static final int NEGOTIATE_WITH_SPACE_LENGTH = NEGOTIATE_WITH_SPACE.length();
    /** Default charset to use for NTLM communication. */
    public static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");

    /**
     * The logger for this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(NTLMUtils.class);

    /** Indicates that Unicode strings are supported for use in security buffer data. */
    public static final int NEGOTIATE_UNICODE = 1;
    /** Indicates that OEM strings are supported for use in security buffer data. */
    public static final int NEGOTIATE_OEM = 1 << 1;
    /** Requests that the server's authentication realm be included in the Type 2 message. */
    public static final int REQUEST_TARGET = 1 << 2;
    /** This flag's usage has not been identified. */
    public static final int UNKNOWN_1 = 1 << 3;
    /**
     * Specifies that authenticated communication between the client and server should carry a digital signature (message
     * integrity).
     */
    public static final int NEGOTIATE_SIGN = 1 << 4;
    /**
     * Specifies that authenticated communication between the client and server should be encrypted (message
     * confidentiality).
     */
    public static final int NEGOTIATE_SEAL = 1 << 5;
    /** Indicates that datagram authentication is being used. */
    public static final int NEGOTIATE_DATAGRAM_STYLE = 1 << 6;
    /** Indicates that the Lan Manager Session Key should be used for signing and sealing authenticated communications. */
    public static final int NEGOTIATE_LAN_MANAGER_KEY = 1 << 7;
    /** This flag's usage has not been identified. */
    public static final int NEGOTIATE_NETWARE = 1 << 8;
    /** Indicates that NTLM authentication is being used. */
    public static final int NEGOTIATE_NTLM = 1 << 9;
    /** This flag's usage has not been identified. */
    public static final int UNKNOWN_2 = 1 << 10;
    /**
     * Sent by the client in the Type 3 message to indicate that an anonymous context has been established. This also affects
     * the response fields (as detailed in the "Anonymous Response" section).
     */
    public static final int NEGOTIATE_ANONYMOUS = 1 << 11;
    /**
     * Sent by the client in the Type 1 message to indicate that the name of the domain in which the client workstation has
     * membership is included in the message. This is used by the server to determine whether the client is eligible for
     * local authentication.
     */
    public static final int NEGOTIATE_DOMAIN_SUPPLIED = 1 << 12;
    /**
     * Indicates that 56-bit encryption is supported. Supplied Sent by the client in the Type 1 message to indicate that the
     * client workstation's name is included in the message. This is used by the server to determine whether the client is
     * eligible for local authentication.
     */
    public static final int NEGOTIATE_WORKSTATION_SUPPLIED = 1 << 13;
    /**
     * Sent by the server to indicate that the server and client are on the same machine. Implies that the client may use the
     * established local credentials for authentication instead of calculating a response to the challenge.
     */
    public static final int NEGOTIATE_LOCAL_CALL = 1 << 14;
    /**
     * Indicates that authenticated communication between the client and server should be signed with a "dummy" signature.
     */
    public static final int NEGOTIATE_ALWAYS_SIGN = 1 << 15;
    /** Sent by the server in the Type 2 message to indicate that the target authentication realm is a domain. */
    public static final int TARGET_TYPE_DOMAIN = 1 << 16;
    /** Sent by the server in the Type 2 message to indicate that the target authentication realm is a server. */
    public static final int TARGET_TYPE_SERVER = 1 << 17;
    /**
     * Sent by the server in the Type 2 message to indicate that the target authentication realm is a share. Presumably, this
     * is for share-level authentication. Usage is unclear.
     */
    public static final int TARGET_TYPE_SHARE = 1 << 18;
    /**
     * Indicates that the NTLM2 signing and sealing scheme should be used for protecting authenticated communications. Note
     * that this refers to a particular session security scheme, and is not related to the use of NTLMv2 authentication. This
     * flag can, however, have an effect on the response calculations (as detailed in the "NTLM2 Session Response" section).
     */
    public static final int NEGOTIATE_NTLM2_KEY = 1 << 19;
    /** This flag's usage has not been identified. */
    public static final int REQUEST_INIT_RESPONSE = 1 << 20;
    /** This flag's usage has not been identified. */
    public static final int REQUEST_ACCEPT_RESPONSE = 1 << 21;
    /** This flag's usage has not been identified. */
    public static final int REQUEST_NON_NT_SESSION_KEY = 1 << 22;
    /**
     * Sent by the server in the Type 2 message to indicate that it is including a Target Information block in the message.
     * The Target Information block is used in the calculation of the NTLMv2 response.
     */
    public static final int NEGOTIATE_TARGET_INFO = 1 << 23;
    /** This flag's usage has not been identified. */
    public static final int UNKNOWN_3 = 1 << 24;
    /** This flag's usage has not been identified. */
    public static final int UNKNOWN_4 = 1 << 25;
    /** This flag's usage has not been identified. */
    public static final int UNKNOWN_5 = 1 << 26;
    /** This flag's usage has not been identified. */
    public static final int UNKNOWN_6 = 1 << 27;
    /** This flag's usage has not been identified. */
    public static final int UNKNOWN_7 = 1 << 28;
    /** Indicates that 128-bit encryption is supported. */
    public static final int NEGOTIATE_128 = 1 << 29;
    /** Indicates that the client will provide an encrypted master key in the "Session Key" field of the Type 3 message. */
    public static final int NEGOTIATE_KEY_EXCHANGE = 1 << 30;
    /** Indicates that 56-bit encryption is supported. */
    public static final int NEGOTIATE_56 = 1 << 31;

    /**
     * Returns the contents of an NTLM security buffer. The buffer works in the following way.
     * 
     * <pre>
     *      Description     Content         Example
     * 0    Length          2 byte number   0xd204 (1234 bytes)
     * 2    Allocated Space 2 byte number   0xd204 (1234 bytes)
     * 4    Offset          4 byte number   0xe1100000 (4321 bytes)
     * 
     * Length is the length of the content. 
     * Allocated Space is the total size of the content. 
     * Offset is the starting position of the content from the beginning of the message.
     * </pre>
     * 
     * @param start
     *            the starting position of the security buffer
     * @param bytes
     *            the total message
     * @return the contents of the security buffer
     */
    public static byte[] getSecurityBuffer(int start, byte[] bytes) {
        short length = getShort(start, bytes);
        // Not really used, as it just defines the allocated size of the byte buffer... actually could probably cause a
        // security issue if length > allocatedSpace
        // short allocatedSpace = getShort(start + 2, bytes);
        int offset = getInt(start + 4, bytes);
        return Arrays.copyOfRange(bytes, offset, offset + length);
    }

    /**
     * Gets a short out of the byte array.
     * 
     * @param start
     *            the start of the short
     * @param bytes
     *            the byte array
     * @return the short
     */
    public static short getShort(int start, byte[] bytes) {
        return EndianUtils.readSwappedShort(bytes, start);
    }

    /**
     * Gets an int out of the byte array.
     * 
     * @param start
     *            the start of the int
     * @param bytes
     *            the byte array
     * @return the int
     */
    public static int getInt(int start, byte[] bytes) {
        return EndianUtils.readSwappedInteger(bytes, start);
    }

    /**
     * Gets a long out of the byte array.
     * 
     * @param start
     *            the start of the long
     * @param bytes
     *            the byte array
     * @return the long
     */
    public static long getLong(int start, byte[] bytes) {
        return EndianUtils.readSwappedLong(bytes, start);
    }

    /**
     * Converts a long into a byte array.
     * 
     * @param num
     *            the long to convert
     * @return the byte array
     */
    public static byte[] convertLong(long num) {
        byte[] toReturn = new byte[8];
        EndianUtils.writeSwappedLong(toReturn, 0, num);
        return toReturn;
    }

    /**
     * Converts an int into a byte array.
     * 
     * @param num
     *            the int to convert
     * @return the byte array
     */
    public static byte[] convertInt(int num) {
        byte[] toReturn = new byte[4];
        EndianUtils.writeSwappedInteger(toReturn, 0, num);
        return toReturn;
    }

    /**
     * Converts a short into a byte array.
     * 
     * @param num
     *            the short to convert
     * @return the byte array
     */
    public static byte[] convertShort(short num) {
        byte[] toReturn = new byte[2];
        EndianUtils.writeSwappedShort(toReturn, 0, num);
        return toReturn;
    }

    /**
     * Creates a security buffer using the given bytes as the content.
     * 
     * @param length
     *            the length of the security buffer
     * @param offset
     *            the offset of where the content will be in the created message
     * @return the security buffer
     */
    public static byte[] createSecurityBuffer(short length, int offset) {
        byte[] toReturn = new byte[8];
        EndianUtils.writeSwappedShort(toReturn, 0, length);
        EndianUtils.writeSwappedShort(toReturn, 2, length);
        EndianUtils.writeSwappedInteger(toReturn, 4, offset);
        return toReturn;
    }

    /**
     * Concatenates any number of byte arrays into a single byte array.
     * 
     * @param args
     *            the arrays to concatenate
     * @return the concatenated array
     */
    public static byte[] concat(byte[]... args) {
        int length = 0;
        for (int i = 0; i < args.length; i++) {
            byte[] bytes = args[i];
            length += bytes.length;
        }
        byte[] data = new byte[length];
        int offset = 0;
        for (byte[] bytes : args) {
            System.arraycopy(bytes, 0, data, offset, bytes.length);
            offset += bytes.length;
        }
        return data;
    }

    /**
     * Performs an rc4 operation on the provided input.
     * 
     * @param input
     *            the input to cipher
     * @param key
     *            the key to cipher with
     * @return the ciphered bytes
     * @throws Exception
     *             if something went wrong
     */
    public static byte[] rc4(byte[] input, byte[] key) throws Exception {
        final Cipher rc4 = Cipher.getInstance("RC4");
        SecretKeySpec sKey = new SecretKeySpec(key, "RC4");
        // The mode doesn't actually matter for RC4, as it's a xor operation regardless of whether it's encrypt or decrypt
        rc4.init(Cipher.ENCRYPT_MODE, sKey);
        return rc4.doFinal(input);
    }

    /**
     * Calculates the HMAC-MD5 hash.
     * 
     * @param key
     *            the key to use
     * @param data
     *            the data to hash
     * @return the hashed value
     */
    public static byte[] calculateHmacMD5(byte[] key, byte[] data) {
        Mac hmacMD5 = createHmacMD5(key);
        hmacMD5.update(data);
        return hmacMD5.doFinal();
    }

    /**
     * Instantiates an HMAC-MD5 hash.
     * 
     * @param key
     *            the key to instantiate with
     * @return the HMAC
     */
    private static Mac createHmacMD5(byte[] key) {
        try {
            // Create a MAC object using HMAC-MD5 and initialize with key
            Mac hmacMD5 = Mac.getInstance(HMAC_MD5_NAME);
            hmacMD5.init(new SecretKeySpec(key, HMAC_MD5_NAME));
            return hmacMD5;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid key", e);
        }
    }

    /**
     * Performs md4 hashing on the passed data.
     * 
     * @param data
     *            the data to hash
     * @return the hash
     * @throws NoSuchAlgorithmException
     *             Thrown if MD4 cannot be found
     */
    public static byte[] md4(byte[] data) throws NoSuchAlgorithmException {
        // The md4 instance needs to be re-created every time because the digest method is not thread safe
        MessageDigest md4 = MessageDigest.getInstance("MD4", PROVIDER);
        return md4.digest(data);
    }

    /**
     * Generate the key uses for signing and sealing.
     * 
     * @param challenge
     *            the challenge extracted from the type 2 message
     * @param nonce
     *            the nonce extracted from the type 3 message
     * @param sessionkey
     *            the session key extracted from the type 3 message
     * @param password
     *            the users passsword
     * @throws Exception
     *             if something goes wrong
     * @return the v1 key
     */
    public static byte[] calculateV1Key(byte[] challenge, byte[] nonce, byte[] sessionkey, String password)
            throws Exception {
        byte[] ntlmHash = NTLMUtils.md4(password.getBytes(CharEncoding.UTF_16LE));
        byte[] ntlmUserSessionKey = NTLMUtils.md4(ntlmHash);
        byte[] ntlm2SessionResponseUserSessionKey = NTLMUtils.calculateHmacMD5(ntlmUserSessionKey,
                NTLMUtils.concat(challenge, nonce));
        return NTLMUtils.rc4(sessionkey, ntlm2SessionResponseUserSessionKey);
    }

    /**
     * Performs md5 hasing on the passed data.
     * 
     * @param data
     *            the data to hash
     * @return the hash
     */
    public static byte[] md5(byte[]... data) {
        MessageDigest md5 = createMD5();
        for (byte[] b : data) {
            md5.update(b);
        }
        return md5.digest();
    }

    /**
     * Creates an instance of the MD5 message digest.
     * 
     * @return the created digest
     */
    private static MessageDigest createMD5() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Calculates the ntlm2 session response. The procedure is taken from
     * http://davenport.sourceforge.net/ntlm.html#theNtlm2SessionResponse.
     * 
     * @param password
     *            the password to use
     * @param challenge
     *            the server challenge
     * @param nonce
     *            the client nonce
     * @return the ntlm2 session response
     * @throws Exception
     *             if something goes wrong
     */
    public static byte[] getNTLM2SessionResponse(String password, byte[] challenge, byte[] nonce) throws Exception {
        byte[] ntlmHash = NTLMUtils.md4(password.getBytes(CharEncoding.UTF_16LE));
        byte[] sessionNonce = NTLMUtils.md5(challenge, nonce);
        byte[] ntlm2SessionHash = new byte[8];
        System.arraycopy(sessionNonce, 0, ntlm2SessionHash, 0, 8);
        byte[] nullPaddedNtlmHash = new byte[21];
        System.arraycopy(ntlmHash, 0, nullPaddedNtlmHash, 0, 16);

        return encryptHashWithDES(ntlm2SessionHash, nullPaddedNtlmHash);
    }

    /**
     * Encrypts the ntlm2 session hash with DES using the method described at
     * http://davenport.sourceforge.net/ntlm.html#theNtlm2SessionResponse.
     * 
     * @param ntlm2SessionHash
     *            the ntlm2 session hash to encrypt
     * @param ntlmHash
     *            the 21 byte long ntlm hash
     * @return the encrypted session hash
     * @throws Exception
     *             if something goes wrong
     */
    private static byte[] encryptHashWithDES(byte[] ntlm2SessionHash, byte[] ntlmHash) throws Exception {
        byte[] toReturn = new byte[24];
        for (int i = 0; i < 3; i++) {
            byte[] key = new byte[7];
            System.arraycopy(ntlmHash, i * 7, key, 0, 7);
            key = adjustOddParityForDES(key);
            DESKeySpec desKeySpec = new DESKeySpec(key);
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
            SecretKey secretKey = keyFactory.generateSecret(desKeySpec);
            Cipher desCipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
            desCipher.init(Cipher.ENCRYPT_MODE, secretKey);
            System.arraycopy(desCipher.doFinal(ntlm2SessionHash), 0, toReturn, i * 8, 8);
        }
        return toReturn;
    }

    /**
     * Adjusts the DES key to have odd parity according to the procedure described at
     * http://davenport.sourceforge.net/ntlm.html#theLmResponse.
     * 
     * @param key
     *            the key to adjust
     * @return the adjusted key
     */
    private static byte[] adjustOddParityForDES(byte[] key) {
        final byte[] toReturn = new byte[8];

        // We take out 7 (8-bit) byte array and convert it into an 8 (7-bit) byte array with the trailing bit being 0
        toReturn[0] = key[0];
        int len = toReturn.length;
        for (int i = 1; i < len - 1; i++) {
            toReturn[i] = (byte) (key[i - 1] << (len - i) | (key[i] & 0xff) >> i);
        }
        toReturn[7] = (byte) (key[6] << 1);

        // We apply an odd parity to each byte of the byte array, changing the trailing 0 to a 1 if necesary to ensure an odd
        // number of bits in the key
        for (int i = 0; i < toReturn.length; i++) {
            int parity = 0;
            for (int j = 0; j < 8; j++) {
                parity += (toReturn[i] >> j) % 2;
            }
            if (parity % 2 == 0 && toReturn[i] % 2 == 0) {
                toReturn[i] |= 1;
            }
        }
        return toReturn;
    }

    /**
     * Builds a NTLMMessage from the header so that we can interract with it.
     * 
     * @param header
     *            the header to read
     * @return the NTLMMessage
     */
    private static NTLMMessage parseNTLMHeader(Header header) {
        if (header != null) {
            try {
                // Strip NEGOTIATE from the beginning of the message, and then use that to build the NTLMMessage
                return NTLMMessage.parse(getRaw(header));
            } catch (Exception e) {
                LOG.error("Error extracting NTLM message from: " + header.getValue() + "Ignoring and proceeding to process it as a non-NTLM message");
                LOG.error("-############-" + "\n" + e);
            }
        }
        return null;
    }

    /**
     * Retrieves the raw bytes from the header.
     * 
     * @param header
     *            the header to retreive the bytes from
     * @return the bytes of the header
     */
    private static byte[] getRaw(Header header) {
        return Base64.decodeBase64(header.getValue().substring(NEGOTIATE_WITH_SPACE_LENGTH));
    }

    /**
     * Extracts an NTLM Message from an HttpRequest.
     * 
     * @param request
     *            the request to extract the NTLM Message from
     * @return the NTLM Message
     */
    public static NTLMMessage getNTLMMessage(HttpRequest request) {
        return parseNTLMHeader(request.getFirstHeader(AUTH.WWW_AUTH_RESP));
    }

    /**
     * Extracts the byte array from the NTLM Message from an HttpRequest.
     * 
     * @param request
     *            the request to extract the byte array from
     * @return the byte array
     */
    public static byte[] getRawNTLMMessage(HttpRequest request) {
        return getRaw(request.getFirstHeader(AUTH.WWW_AUTH_RESP));
    }

    /**
     * Extracts an NTLM Message from an HttpResponse.
     * 
     * @param response
     *            the response to extract the NTLM Message from
     * @return the NTLM Message
     */
    public static NTLMMessage getNTLMMessage(HttpResponse response) {
        return parseNTLMHeader(response.getFirstHeader(AUTH.WWW_AUTH));
    }

    /**
     * Extracts the byte array from the NTLM Message from an HttpResponse.
     * 
     * @param response
     *            the response to extract the byte array from
     * @return the byte array
     */
    public static byte[] getRawNTLMMessage(HttpResponse response) {
        return getRaw(response.getFirstHeader(AUTH.WWW_AUTH));
    }

    /**
     * Retrieves the index of a subarray in an array starting from a given point.
     * 
     * @param subSequence
     *            the subarray to find
     * @param sequence
     *            the total array
     * @param index
     *            where in the total array to start looking
     * @return the index of the subarray or -1 if not found
     */
    public static int indexOf(byte[] subSequence, byte[] sequence, int index) {
        if (subSequence.length == 0) {
            return index;
        }
        for (int i = index; i < sequence.length - subSequence.length + 1; i++) {
            boolean found = true;
            for (int j = 0; j < subSequence.length; j++) {
                if (subSequence[j] != sequence[i + j]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds the index of a subarray in an array.
     * 
     * @param subSequence
     *            the subarray to look for
     * @param sequence
     *            the array to traverse
     * @return the index of the subarray, or -1 if not found
     */
    public static int indexOf(byte[] subSequence, byte[] sequence) {
        return indexOf(subSequence, sequence, 0);
    }

    /**
     * Splits a byte array on the specified subsequence.
     * 
     * @param subSequence
     *            the subsequence to split on
     * @param sequence
     *            the sequence to split
     * @return a list of byte arrays
     */
    public static List<byte[]> split(byte[] subSequence, byte[] sequence) {
        List<byte[]> toReturn = new ArrayList<byte[]>();
        int index = 0;
        while (index < sequence.length) {
            int nextIndex = indexOf(subSequence, sequence, index);
            if (nextIndex == -1) {
                // Just set the next index to the end of the array so that we copy all of the sequence
                nextIndex = sequence.length;
            }
            toReturn.add(Arrays.copyOfRange(sequence, index, nextIndex));
            index = nextIndex + subSequence.length;
        }
        return toReturn;
    }

    /**
     * Take the NTLMMessage and turn it into a header.
     * 
     * @param msg
     *            the NTLMMessag to turn into a header.
     * @return the Header object
     */
    public static Header buildNtlmHeader(NTLMMessage msg) {
        return new BasicHeader(AUTH.WWW_AUTH_RESP, NEGOTIATE_WITH_SPACE + Base64.encodeBase64String(msg.toHeaderBytes()));
    }

    /**
     * Retrieves the boundary from an HttpResponse.
     * 
     * @param response
     *            the response to retrieve the boundary from.
     * @return the boundary, or null if not found
     */
    public static String getBoundary(HttpResponse response) {
        Header[] headers = response.getHeaders(HttpHeaders.CONTENT_TYPE);
        for (Header h : headers) {
            for (HeaderElement he : h.getElements()) {
                for (NameValuePair nvp : he.getParameters()) {
                    if (nvp.getName().equals(NTLMConstants.BOUNDARY)) {
                        return nvp.getValue();
                    }
                }
            }
        }
        return null;
    }

    /** Difference between microsoft time (1601-01-01) and epoch time (1970-01-01). */
    private static final long EPOCH_TIME_MS_TIME_DIFF = 11644473600000L;

    /**
     * Retrieve a timestamp from a byte array that is in microsoft filetime format.
     * 
     * @param bytes
     *            the bytes of the timestamp
     * @return a long corresponding to the epoch time
     */
    public static long fromMicrosoftTimestamp(byte[] bytes) {
        long filetime = getLong(0, bytes);
        long mstime = filetime / (1000 * 10);
        long javatime = mstime - EPOCH_TIME_MS_TIME_DIFF;
        return javatime;
    }

    /**
     * Converts a timestamp to a byte array that is in microsoft filetime format.
     * 
     * @param javatime
     *            the timestamp
     * @return a byte array corresponding to the filetime of the timestamp
     */
    public static byte[] toMicrosoftTimestamp(long javatime) {
        final long mstime = javatime + EPOCH_TIME_MS_TIME_DIFF;
        return convertLong(mstime * 1000 * 10);
    }

    /**
     * Utility class.
     */
    private NTLMUtils() {

    }
}
