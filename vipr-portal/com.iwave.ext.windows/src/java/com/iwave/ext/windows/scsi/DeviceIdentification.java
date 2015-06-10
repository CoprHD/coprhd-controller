/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.scsi;

import com.google.common.collect.Maps;
import org.apache.commons.codec.binary.Hex;
import java.util.Map;

/**
 * This contains tools for parsing the SCSI DeviceIdentifierPage in Windows.
 */
public class DeviceIdentification {
    private static final byte HEADER_QUALIFIER_DEVICE_TYPE = 0x00;
    private static final byte HEADER_PAGE_CODE = (byte) 0x83;
    private static final byte CODE_SET_BINARY = 0x01;
    private static final byte CODE_SET_ASCII = 0x02;
    private static final int MIN_LENGTH = 8;

    private static final int NAA_IDENTIFIER_TYPE = 3;
    
    private static final int ALTERNATE_IDENTIFIER_TYPE = 2;

    public static String getWwid(byte[] deviceIdentifierPage) {
        Map<Integer, String> identifiers = parseIdentifiers(deviceIdentifierPage);
        
        String identifier = identifiers.get(NAA_IDENTIFIER_TYPE) == null ? identifiers.get(ALTERNATE_IDENTIFIER_TYPE) : identifiers.get(NAA_IDENTIFIER_TYPE);
        
        if (identifier == null) {
        	throw new IllegalArgumentException("No device identifier found in the device identifier page in the registry");
        }
        return identifier;
    }

    public static Map<Integer,String> parseIdentifiers(byte[] bytes) {
        if (bytes.length < MIN_LENGTH) {
            throw new IllegalArgumentException("Not enough data from device identifier page");
        }

        if (bytes[0] != HEADER_QUALIFIER_DEVICE_TYPE && bytes[1] != HEADER_PAGE_CODE) {
            throw new IllegalArgumentException("Invalid device identifier page header");
        }
        int offset = 2;

        // bytes[2], bytes[3] are total length
        int totalLength = (bytes[offset++] & 0xFF) << 8 | (bytes[offset++] & 0xFF);
        if (totalLength != bytes.length - offset) {
            throw new IllegalArgumentException(String.format("Device identifier page size (%s) does not match content size (%s)", totalLength, bytes.length - offset));
        }

        // bytes[4] starts descriptor list:
        //   Byte 0 - Protocol Identifier + Code Set          01 = Binary,  02 = Ascii
        //   Byte 1 - PIV, Association, Designator Type       01 = Vendor ID, 03 = NAA (WWID)
        //   Byte 2 - Reserved
        //   Byte 3 - Length
        //   Byte 4..n - Identifier
        Map<Integer,String> identifiers = Maps.newLinkedHashMap();
        while (offset < totalLength) {
            byte protocolIdentifierAndCodeSet = bytes[offset++];
            int type = bytes[offset++]; // Last half of the byte for the type but PIV for us should always be 0
            offset++;
            int identifierLength = (bytes[offset++] & 0xFF);
            if (identifierLength + offset > bytes.length) {
                throw new IllegalArgumentException("Device Identifier length too long for page size");
            }

            // Read device identifier
            if (protocolIdentifierAndCodeSet == CODE_SET_BINARY) {
                byte[] identifierBytes = new byte[identifierLength];
                System.arraycopy(bytes, offset, identifierBytes, 0, identifierLength);
                String identifier = Hex.encodeHexString(identifierBytes);
                identifiers.put(type, identifier);
            }
            else if (protocolIdentifierAndCodeSet == CODE_SET_ASCII) {
                String identifier = new String(bytes, offset, identifierLength);
                identifiers.put(type, identifier);
            }
            offset+=identifierLength;
        }

        return identifiers;
    }
}