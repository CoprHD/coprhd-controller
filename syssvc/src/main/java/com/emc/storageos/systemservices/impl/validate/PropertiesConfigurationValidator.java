/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.validate;

import static com.emc.storageos.model.property.PropertyConstants.*;

import java.net.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Base64;

import com.emc.storageos.model.property.PropertiesMetadata;
import com.emc.storageos.model.property.PropertyMetadata;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

import com.emc.storageos.db.client.model.EncryptionProvider;
import com.google.common.net.InetAddresses;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PropertiesConfigurationValidator {
    private static final Logger _log = LoggerFactory.getLogger
            (PropertiesConfigurationValidator.class);

    private static List<String> PROPERTIES_ALLOW_EMPTY_VALUE =
            Arrays.asList("system_update_repo");

    private PropertiesMetadata _propertiesMetadata = null;
    private EncryptionProvider _encryptionProvider;
    public static final int PORT_MAX = 65535;
    public static final int PORT_MIN = 1;

    private static enum PROP_LENGTH {
        MAX, MIN
    }

    private static final Pattern pattern;

    private static final String EMAIL_PATTERN = "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@"
            + "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";

    // valid pattern for an individual label in a full hostname string.
    private static String VALID_HOST_NAME_LABEL_PATTERN = "^[a-z0-9-]+";

    static {
        pattern = Pattern.compile(EMAIL_PATTERN);
    }

    public void setEncryptionProvider(EncryptionProvider encryptionProvider) {
        _encryptionProvider = encryptionProvider;
    }

    /**
     * validate property for updating operation.
     */
    public String getValidPropValue(String propertyName, String propertyValue, boolean userMutated) {
        return getValidPropValue(propertyName, propertyValue, userMutated, false);
    }

    /**
     * Validate the property using the properties metadata, for property
     * resetting or updating.
     *
     * @param propertyName
     * @param propertyValue
     * @param userMutated
     * @return
     */
    public String getValidPropValue(String propertyName, String propertyValue, boolean userMutated, boolean bReset) {

        Map<String, PropertyMetadata> metadataMap = getMetaData();
        PropertyMetadata metaData = metadataMap.get(propertyName);
        if (metaData == null) {
            throw APIException.badRequests.propertyIsNotValid(propertyName);
        }

        // check to see if a user changed the property..and if that option is
        // allowed for that property.
        if (userMutated) {
            if (metaData.getUserMutable() != null ? metaData.getUserMutable() : false) {
                return validateProperty(propertyName, propertyValue, metaData, bReset);
            } else {
                throw APIException.badRequests.changePropertyIsNotAllowed(propertyName);
            }
        } else {
            return validateProperty(propertyName, propertyValue, metaData, bReset);
        }
    }

    /**
     * @param propertyName
     * @param propertyValue
     * @param metaData
     * @return
     */
    private String validateProperty(String propertyName, String propertyValue,
             PropertyMetadata metaData,boolean bReset) {

        // If the property is not the encrypted string, trip the leading
        // and trailing whitespaces. That is because, the propertyValue
        // (type encryptedstring or encryptedtext) contains the plain text
        // at this point, so trimming it will remove the whitespaces from
        // what actually user entered. Where that should not be removed
        // until they are encrypted. The base64.encrypt() will take care
        // that.
        if (!(ENCRYPTEDSTRING.equalsIgnoreCase(metaData.getType())
                || ENCRYPTEDTEXT.equalsIgnoreCase(metaData.getType()))) {
            // Remove leading and trailing spaces and newlines
            propertyValue = propertyValue.trim();
        }

        // Minimum Length Check
        if (metaData.getMinLen() != null) {
            if (!validateLength(propertyValue, metaData.getMinLen(), PROP_LENGTH.MIN)) {
                throw APIException.badRequests.propertyValueLengthLessThanMinLength(propertyName, metaData.getMinLen());
            }
        }

        // Maximum Length Check
        if (metaData.getMaxLen() != null) {
            if (!validateLength(propertyValue, metaData.getMaxLen(), PROP_LENGTH.MAX)) {
                throw APIException.badRequests.propertyValueLengthExceedMaxLength(propertyName, metaData.getMaxLen());
            }
        }

        // added to allow nill for properties. If the propertyValue passed the
        // previous test and is null, we should return it directly
        if (StringUtils.isEmpty(propertyValue)) {
            if (bReset || allowEmptyValue(propertyName)) {
                return propertyValue;
            }
        }

        // allowed values check. If the propertyValue doesn't match the
        // allowable values, it should throw a exception. Because we might not
        // explicitly specify null as allowed value, we
        // have to put this logic after the null test.
        if (metaData.getAllowedValues() != null && metaData.getAllowedValues().length > 0) {
            if (!validateAllowedValues(propertyValue, metaData.getAllowedValues())) {
                throw APIException.badRequests.propertyValueDoesNotMatchAllowedValues(propertyName,
                        Arrays.toString(metaData.getAllowedValues()));
            }
        }

        // Valid Type Check. Same reason as above, we should validate a null
        // value's type.
        if (!validateType(propertyValue, metaData)) {
            throw APIException.badRequests.propertyValueTypeIsInvalid(propertyName, metaData.getType());
        }

        String validatedPropVal;
        // Prepare property value according to type
        if (STRING.equalsIgnoreCase(metaData.getType())) {
            validatedPropVal = propertyValue;
        } else if (ENCRYPTEDSTRING.equalsIgnoreCase(metaData.getType())
                || ENCRYPTEDTEXT.equalsIgnoreCase(metaData.getType())) {
            validatedPropVal = new String(Base64.encodeBase64(_encryptionProvider.encrypt(propertyValue)));
        } else if (TEXT.equalsIgnoreCase(metaData.getType())) {
            validatedPropVal = propertyValue;
            validatedPropVal = validatedPropVal.replace("\n", "\\\\n").replace("\r", "");
        } else if (IPLIST.equalsIgnoreCase(metaData.getType()) || EMAILLIST.equalsIgnoreCase(metaData.getType())) {
            validatedPropVal = formatList(propertyValue);
        } else {
            validatedPropVal = propertyValue;
        }

        return validatedPropVal;
    }

    /**
     * check if the property allows empty as its value
     *
     * @param propertyName
     * @return
     */
    private static boolean allowEmptyValue(String propertyName) {
        return PROPERTIES_ALLOW_EMPTY_VALUE.contains(propertyName);
    }

    /**
     * Validate the property's type.
     *
     * @param propertyValue
     * @param metaData
     * @return
     */
    private static boolean validateType(String propertyValue, PropertyMetadata metaData) {

        if (metaData.getType().equalsIgnoreCase(IPADDR)) {
            return validateIpv4Addr(propertyValue);
        } else if (metaData.getType().equalsIgnoreCase(IPV6ADDR)) {
            return validateIpv6Addr(propertyValue);
        } else if (metaData.getType().equalsIgnoreCase(EMAIL)) {
            return validateEmail(propertyValue);
        } else if (metaData.getType().equalsIgnoreCase(EMAILLIST)) {
            return validateEmailList(propertyValue);
        } else if (metaData.getType().equalsIgnoreCase(STRINGLIST)) {
            return validateStringList(propertyValue);
        } else if (metaData.getType().equalsIgnoreCase(URL)) {
            return validateUrl(propertyValue);
        } else if (metaData.getType().equalsIgnoreCase(UINT64)) {
            return validateUint64(propertyValue);
        } else if (metaData.getType().equalsIgnoreCase(UINT32)) {
            return validateUint32(propertyValue);
        } else if (metaData.getType().equalsIgnoreCase(UINT16)) {
            return validateUint16(propertyValue);
        } else if (metaData.getType().equalsIgnoreCase(UINT8)) {
            return validateUint8(propertyValue);
        } else if (metaData.getType().equalsIgnoreCase(BOOLEAN)) {
            return validateBoolean(propertyValue);
        } else if (metaData.getType().equalsIgnoreCase(PERCENT)) {
            return validatePercent(propertyValue);
        } else if (metaData.getType().equalsIgnoreCase(HOSTNAME)) {
            return validateHostName(propertyValue);
        } else if (metaData.getType().equalsIgnoreCase(STRICTHOSTNAME)) {
            return validateStrictHostName(propertyValue);
        } else if (metaData.getType().equalsIgnoreCase(IPLIST)) {
            return validateIpList(propertyValue);
        } else if (metaData.getType().equalsIgnoreCase(IPPORTLIST)) {
            return validateIpPortList(propertyValue);
        } else if (metaData.getType().equalsIgnoreCase(ENCRYPTEDSTRING)) {
            return true;
        } else if (metaData.getType().equalsIgnoreCase(ENCRYPTEDTEXT)) {
            return true;
        } else if (metaData.getType().equalsIgnoreCase(STRING)) {
            return validateString(propertyValue);
        } else if (metaData.getType().equalsIgnoreCase(TEXT)) {
            return true;
        }

        return false;
    }

    /**
     * Validate String - return false if contains newlines
     *
     * @param string
     * @return Boolean
     */
    private static boolean validateString(String string) {
        return !string.contains("\n");
    }

    /**
     * Remove spaces and newlines from ip list
     *
     * @param iplist
     * @return
     */
    private static String formatList(String list) {
        String[] ips = list.split(",");
        StringBuilder sb = new StringBuilder();
        String delim = ",";
        for (String ip : ips) {
            sb.append(delim).append(ip.trim());
        }
        return sb.substring(delim.length());
    }

    /**
     * Validate a property's allowable values.
     *
     * @param propertyValue
     * @param acceptableValues
     * @return boolean
     */
    private static boolean validateAllowedValues(String propertyValue, String[] acceptableValues) {

        for (String value : acceptableValues) {
            if (value.equals(propertyValue)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validate uint64 values.
     *
     * @param value
     * @return
     */
    public static boolean validateUint64(String value) {

        try {
            return Long.parseLong(value) >= 0L;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validate uint16 values.
     *
     * @param value
     * @return
     */
    public static boolean validateUint16(String value) {

        try {
            int intValue = Integer.parseInt(value);
            return intValue >= 0 && intValue <= 65535;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validate uint8 values.
     *
     * @param value
     * @return
     */
    public static boolean validateUint8(String value) {

        try {
            int intValue = Integer.parseInt(value);
            return intValue >= 0 && intValue <= 255;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validate uint32 values.
     *
     * @param value
     * @return
     */
    public static boolean validateUint32(String value) {

        try {
            return Integer.parseInt(value) >= 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validate boolean values.
     *
     * @param value
     * @return
     */
    public static boolean validateBoolean(String value) {

        try {
            Boolean.parseBoolean(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validate percent values.
     *
     * @param value
     * @return
     */
    public static boolean validatePercent(String value) {

        try {
            int intValue = Integer.parseInt(value);
            if (intValue < 0 || intValue > 100) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * Validate value is an IpAddr.
     *
     * @param value
     * @return
     */
    private static boolean validateIpAddr(String value) {
        return InetAddresses.isInetAddress(value);
    }

    /**
     * Validate value is an IPv4 Address
     *
     * @param value
     * @return
     */
    public static boolean validateIpv4Addr(String value) {
        try {
            return validateIpAddr(value) && InetAddresses.forString(value) instanceof Inet4Address;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validate value is an IPv6 Address
     *
     * @param value
     * @return
     */
    public static boolean validateIpv6Addr(String value) {
        try {
            return validateIpAddr(value) && InetAddresses.forString(value) instanceof Inet6Address;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validate that value is an Email.
     *
     * @param value
     * @return
     */
    public static boolean validateEmail(String value) {
        return pattern.matcher((String) value).matches();
    }

    /**
     * Validate that value is an email list.
     *
     * @param value comma separated email list
     * @return
     */
    public static boolean validateEmailList(String value) {
        String[] emails = value.split(",");
        for (String email : emails) {
            if (validateEmail(email.trim()) == false) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validate the value is a valid URL.
     *
     * @param value
     * @return
     */
    public static boolean validateUrl(String value) {

        try {
            URL url = new URL((String) value);
        } catch (MalformedURLException e) {
            return false;
        }
        return true;
    }

    /**
     * Validate ip list
     *
     * @param iplist ip list
     * @return
     */
    public static boolean validateIpList(String iplist) {
        String[] ips = iplist.split(",");
        try {
            for (String ip : ips) {
                ip = ip.trim();
                if (ip.contains("/")) {
                    // Handle subnet specification
                    String[] ipcomps = ip.split("/");
                    // We have to trim the each component to handle the situation when there are spaces in the left or in the right of "/"
                    if (ipcomps.length != 2 || !InetAddresses.isInetAddress(ipcomps[0].trim())) {
                        return false;
                    } else {
                        // We have to put the test on maskBits in a separate try block otherwise a non-integer will cause problem.
                        try {
                            int maskBits = Integer.parseInt(ipcomps[1].trim());
                            if (maskBits > 32 || maskBits < 0) {
                                return false;
                            }
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    }
                } else if (!InetAddresses.isInetAddress(ip)) {
                    return false;
                }
            }
        } catch (NumberFormatException exc) {
            return false;
        }
        return true;
    }

    /**
     * Validate a property's maxLength and minLength values.
     *
     * @param value
     * @param lengthThreshold
     * @param lengthCriteria
     * @return
     */
    public static boolean validateLength(String value, int lengthThreshold, PROP_LENGTH lengthCriteria) {

        if (value == null) {
            return false;
        } else if (lengthCriteria.toString().equals(PROP_LENGTH.MAX.toString())) {
            if (value.length() > lengthThreshold) {
                return false;
            }
        } else if (lengthCriteria.toString().equals(PROP_LENGTH.MIN.toString())) {
            if (value.length() < lengthThreshold) {
                return false;
            }
        } else {
            return false;
        }
        return true;
    }

    /**
     * Validate Hostname
     *
     * @param hostName
     * @return
     */
    public static boolean validateHostName(String hostName) {

        // nothing in hostname, return false.
        if (hostName == null || hostName.isEmpty()) {
            return false;
        }

        // if length is greater than 255, return false;
        if (hostName.length() > 255) {
            return false;
        }

        // Testing uses an IP Address as opposed to a hostname. First test if
        // the
        // hostname looks like an ip address.
        if (validateIpAddr(hostName)) {
            return true;
        }

        // hostname doesn't appear to be an ip adress, let's check for hostname.
        String[] hostLabels = hostName.split("\\.", -1);
        if (hostLabels.length == 0) {
            return false;
        }
        Pattern labelPattern = Pattern.compile(VALID_HOST_NAME_LABEL_PATTERN);
        Matcher matcher = null;
        for (String label : hostLabels) {
            // check for length. must be between 1 to 63 chars.
            if (label.length() == 0 || label.length() > 63) {
                return false;
            }

            // check for pattern. a-z0-9-
            matcher = labelPattern.matcher(label);
            if (!matcher.matches()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validate Strict Hostname
     *
     * @param hostName
     * @return
     */
    public static boolean validateStrictHostName(String hostName) {

        // nothing in hostname, return false.
        if (hostName == null || hostName.isEmpty()) {
            return false;
        }

        // if length is greater than 255, return false.
        if (hostName.length() > 253) {
            return false;
        }

        String[] hostLabels = hostName.split("\\.", -1);
        if (hostLabels.length == 0) {
            return false;
        }
        Pattern labelPattern = Pattern.compile(VALID_HOST_NAME_LABEL_PATTERN);
        Matcher matcher = null;
        for (String label : hostLabels) {
            // check for length. must be between 1 to 63 chars.
            if (label.length() == 0 || label.length() > 63) {
                return false;
            }

            // check for pattern. a-z0-9-
            matcher = labelPattern.matcher(label);
            if (!matcher.matches()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validate the IP-Port List for Syslog
     * <p/>
     * Ex - 1.1.1.1:4444,[22:22::222]:3333
     *
     * @param ipPortList
     * @return
     */
    public static boolean validateIpPortList(String ipPortList) {
        if (ipPortList == null || ipPortList.isEmpty()) {
            return true;
        }
        String[] serverPortList = ipPortList.split(",");

        for (String serverPort : serverPortList) {

            // split on last colon for ip:port
            String ip = serverPort.substring(0, serverPort.lastIndexOf(":"));
            String port = serverPort.substring(serverPort.lastIndexOf(":") + 1);

            if (ip.startsWith("[") && ip.endsWith("]")) {
                // if it is surrounded by [] then it should be IPV6 only
                ip = ip.substring(1, ip.length() - 1);
                if (!validateIpv6Addr(ip))
                    return false;
            } else if (validateIpv6Addr(ip)) {
                // this is an ipv6 address without brackets
                return false;
            } else if (!validateHostName(ip)) {
                // this isn't hostname or ipv4
                return false;
            }

            if (!validateUint16(port))
                return false;
        }
        return true;
    }

    /**
     * Validate that value is an string list.
     *
     * @param value comma separated string list
     * @return
     */
    public static boolean validateStringList(String value) {
        String[] strs = value.split(",");
        for (String str : strs) {
            if (!validateString(str))
                return false;
        }
        return true;
    }

    /**
     * Return the map of the properties metadata.
     *
     * @return
     */
    private Map<String, PropertyMetadata> getMetaData() {
        return _propertiesMetadata.getGlobalMetadata();
    }

    // Spring injected property.
    public void setPropertiesMetadata(PropertiesMetadata propertiesMetadata) {
        _propertiesMetadata = propertiesMetadata;
    }

    // display will be like: Property Error for {propName}. Property value {propvalue} {error description}
    public String getDisplayError(String name, String value, String errDesc) {
        if (errDesc == null || errDesc.isEmpty()) {
            return null;
        }

        StringBuffer buff = new StringBuffer();
        buff.append("Property Error for ");
        buff.append(name).append(". ");
        buff.append("Property value ").append(value).append(" ");
        buff.append(errDesc);
        return buff.toString();
    }
}
