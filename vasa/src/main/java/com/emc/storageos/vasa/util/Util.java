/* **********************************************************
 * Copyright 2010 VMware, Inc.  All rights reserved. -- VMware Confidential
 * **********************************************************/
package com.emc.storageos.vasa.util;

import java.io.FileReader;
import java.io.IOException;
import java.lang.String;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.vmware.vim.vasa._1_0.data.xsd.MountInfo;
import com.vmware.vim.vasa._1_0.data.xsd.HostInitiatorInfo;
import com.vmware.vim.vasa._1_0.data.xsd.StorageFileSystem;
import com.vmware.vim.vasa._1_0.InvalidArgument;
import org.apache.log4j.Logger;

/**
 * Helper functions
 */
public class Util {

    private static String configPath = "/config/parameters/";
    private static Logger log = Logger.getLogger(Util.class);

    public static boolean isEmpty(MountInfo[] mi) {
        return mi == null || mi.length == 0
                || ((mi.length == 1) && mi[0] == null);
    }

    public static boolean isEmpty(HostInitiatorInfo[] hii) {
        return hii == null || hii.length == 0
                || ((hii.length == 1) && hii[0] == null);
    }

    private static boolean stringsAreEqual(String s1, String s2) {
        if ((s1 == null) && (s2 == null)) {
            // both null means equal
            return true;
        }

        if ((s1 == null) || (s2 == null)) {
            // one is null, the other is not
            return false;
        }
        return s1.equals(s2);
    }

    private static boolean objectsAreEqual(HostInitiatorInfo hii1,
            HostInitiatorInfo hii2) {
        if (!stringsAreEqual(hii1.getPortWwn(), hii2.getPortWwn())) {
            return false;
        }
        if (!stringsAreEqual(hii1.getNodeWwn(), hii2.getNodeWwn())) {
            return false;
        }
        if (!stringsAreEqual(hii1.getIscsiIdentifier(),
                hii2.getIscsiIdentifier())) {
            return false;
        }
        return true;
    }

    private static boolean objectsAreEqual(Object o1, Object o2)
            throws InvalidArgument {
        if ((o1 instanceof HostInitiatorInfo)
                && (o2 instanceof HostInitiatorInfo)) {
            return objectsAreEqual((HostInitiatorInfo) o1,
                    (HostInitiatorInfo) o2);
        }
        log.error("objectsAreEqual: unknown object type");
        throw FaultUtil.InvalidArgument();
    }

    public static boolean listsAreEqual(ArrayList l1, ArrayList l2)
            throws InvalidArgument {
        if ((l1 == null) && (l2 == null)) {
            // both null means equal
            return true;
        }

        if ((l1 == null) || (l2 == null)) {
            // one is null, the other is not
            return false;
        }

        if (l1.size() != l2.size()) {
            // sizes not equal, lists cannot be equal
            return false;
        }

        /*
         * make sure that the same object is not found more than one in the list
         * 
         * it is only necessary to do this check for one of the lists
         */
        for (int i = 0; i < l1.size() - 1; i++) {
            for (int j = i + 1; j < l1.size(); j++) {
                if (objectsAreEqual(l1.get(i), l1.get(j))) {
                    log.error("listsAreEqual: list contains the same object more than once");
                    throw FaultUtil.InvalidArgument();
                }
            }
        }

        /*
         * check that each object in l1 is found in l2 since there are no
         * duplicate objects in l1 this means there are no extra objects in l2
         */
        for (int i = 0; i < l1.size(); i++) {
            boolean matchFound = false;
            for (int j = 0; j < l2.size(); j++) {
                if (objectsAreEqual(l1.get(i), l2.get(j))) {
                    matchFound = true;
                }
            }

            if (!matchFound) {
                return false;
            }
        }
        return true;
    }

    public static boolean isEmpty(StorageFileSystem[] sfs) {
        return sfs == null || sfs.length == 0
                || ((sfs.length == 1) && sfs[0] == null);
    }

    public static boolean isEmpty(String[] s) {
        return s == null || s.length == 0 || ((s.length == 1) && s[0] == null);
    }

    public static boolean isEmpty(String s) {
        return s == null || s.trim().length() == 0;
    }

    /**
     * All the UniqueIds used by the VP are integers If any of the
     * UniqueIds in the String array are not integers then they cannot possibly
     * be valid ids and so the expection is thrown.
     */
    public static void allUniqueIdsAreValid(String[] uniqueIds,
            boolean allowEmpty) throws InvalidArgument {
        if (isEmpty(uniqueIds)) {
            if (!allowEmpty) {
                throw FaultUtil
                        .InvalidArgument("UniqueId list cannot be empty");
            }
            return;
        }

        for (int i = 0; i < uniqueIds.length; i++) {
            uniqueIdIsValid(uniqueIds[i]);
        }
    }

    /*
     * Verify that the uniqueId is composed of characters only in the range of
     * '0' - '9'. The assumption is that uniqueId will not be NULL.
     */
    public static void uniqueIdIsValid(String uniqueId) throws InvalidArgument {
        try {
            if (uniqueId == null) {
                throw FaultUtil.InvalidArgument("UniqueId[" + uniqueId
                        + "] is not a valid id for VP");
            }
        } catch (Exception e) {
            throw FaultUtil.InvalidArgument("UniqueId " + uniqueId
                    + " unexpected exception: " + e);
        }
    }

    /*
     * Verify that the event or alarm Id is -1 or greater.
     */
    public static void eventOrAlarmIdIsValid(long id) throws InvalidArgument {
        if (id < -1) {
            throw FaultUtil.InvalidArgument("invalid id " + id);
        }
    }

    /*
     * Convert to comma separated string
     */
    public static String getIdString(String[] id) {
        if (id == null || id.length < 1) {
            return null;
        }
        StringBuffer buff = new StringBuffer();
        for (int i = 0; i < id.length - 1; i++) {
            buff.append(id[i]);
            buff.append(",");
        }
        buff.append(id[id.length - 1]);
        return buff.toString();
    }

    public static String getConfigParameter(String configName) {
        try {
            XmlParser configParser = new XmlParser();
            configParser.loadResource("com/emc/sos/vasa/config.xml");
            return configParser.getString(configPath + configName, null);
        } catch (Exception e) {
            log.error("getStringConfigValue ", e);
            return null;
        }
    }

    public static String getConfigValue(String configName) {
        try {
            XmlParser configParser = new XmlParser();
            configParser.loadResource("com/emc/sos/vasa/config.xml");
            return configParser.getString(configName, null);
        } catch (Exception e) {
            log.error("getConfigValue ", e);
            return null;
        }
    }

    public static String getCanonicalNameOfLocalHost() {

        InetAddress addr = null;
        try {
            addr = InetAddress.getLocalHost();
            String hostnameCanonical = addr.getCanonicalHostName();
            if (hostnameCanonical.contains("null")) {
                return null;
            }
            if ("localhost".equals(hostnameCanonical)) {
                return null;
            }
            return hostnameCanonical;
        } catch (UnknownHostException e) {
            log.error("UnknownHostException occurred: " + e.getMessage());
            return null;
        }

    }

    public static String getIPAddressOfLocalHost() {

        InetAddress addr = null;

        try {
            addr = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            log.error("UnknownHostException occurred: " + e.getMessage());
            return null;
        }
        String ipAddress = addr.getHostAddress();
        if ("127.0.0.1".equals(ipAddress)) {
            return null;
        }

        return ipAddress;

    }

    public static String getPropertyValue(String filePath, String propertyKey) {

        final String methodName = "getPropertyValue(): ";
        log.trace(methodName + "Entering with input filePath[" + filePath
                + "] propertyKey[" + propertyKey + "]");

        FileReader fileReader = null;
        Properties prop = new Properties();

        try {
            fileReader = new FileReader(filePath);
            prop.load(fileReader);

            String value = null;
            value = prop.getProperty(propertyKey);
            log.trace(methodName + "returning [" + value + "]");
            return value;
        } catch (IOException ex) {
            log.warn(methodName + "IOException occured: " + ex.getMessage());
            log.trace(methodName + "returning [null]");
            return null;
        } finally {
            if (fileReader != null) {
                try {
                    fileReader.close();
                } catch (IOException e) {
                    // Do nothing
                }
            }
        }

    }

    public static boolean areListsEqual(List<String> list1, List<String> list2) {

        if (list1 == null && list2 == null) {
            return true;
        }

        if ((list1 == null) || (list2 == null)) {
            // one is null, the other is not
            return false;
        }

        if (list1.size() != list2.size()) {
            // sizes not equal, lists cannot be equal
            return false;
        }

        for (int i = 0; i < list1.size(); i++) {
            boolean matchFound = false;
            for (int j = 0; j < list2.size(); j++) {
                if (list1.get(i).equals(list2.get(j))) {
                    matchFound = true;
                }
            }

            if (!matchFound) {
                return false;
            }
        }
        return true;
    }
}
