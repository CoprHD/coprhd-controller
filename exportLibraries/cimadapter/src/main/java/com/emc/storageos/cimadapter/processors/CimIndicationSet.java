/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.cimadapter.processors;

// Java imports
import java.util.Calendar;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.TimeZone;

import javax.cim.CIMDataType;
import javax.cim.CIMDateTimeAbsolute;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cimadapter.connections.cim.CimAlertType;
import com.emc.storageos.cimadapter.connections.cim.CimCompositeID;
import com.emc.storageos.cimadapter.connections.cim.CimConstants;
import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;

/**
 * Encapsulates a CIM indication as a table of name/value pairs.
 */
public class CimIndicationSet {

    // The CIM indication as a set of name/value pairs.
    private Hashtable<String, String> _pairs = new Hashtable<String, String>();

    // A logger reference.
    private static final Logger s_logger = LoggerFactory
            .getLogger(CimIndicationSet.class);

    /**
     * Constructs a CimIndicationSet from the given indication.
     */
    public CimIndicationSet(CIMInstance indication) {
        processInstance(indication);
        setIndicationClass();
        setImpactedClassTags();
        if (isAlertIndication()) {
            setAlertTags();
        }
    }

    /**
     * Determines if there is a name/value pair with the passed name.
     * 
     * @param key The key representing the name.
     * 
     * @return true if there is an entry with the passed key.
     */
    public boolean containsKey(Object key) {
        return _pairs.containsKey(key);
    }

    /**
     * Returns the table of name/value pairs.
     * 
     * @return The table of name/value pairs.
     */
    public Hashtable<String, String> getMap() {
        return _pairs;
    }

    /**
     * Gets the value associated with the passed name.
     * 
     * @param name The key for the desired entry.
     * 
     * @return The value for the passed name.
     */
    public String get(String name) {
        return _pairs.get(name);
    }

    /**
     * Add or replace a name/value pair. If 'value' is null, the name is
     * removed.
     * 
     * @param name The name to be added to the table.
     * @param value The associated value to be added top the table.
     */
    public void set(String name, String value) {
        internalSet(name, value);
    }

    /**
     * Add or replace a name/value pair. If 'value' is null, the name is
     * removed.
     * 
     * @param name The name to be added to the table.
     * @param value The associated value to be added top the table.
     */
    private void internalSet(String name, String value) {
        if (value != null) {
            _pairs.put(name, value);
        } else {
            _pairs.remove(name);
        }
    }

    /**
     * Removes the name/value pair with the passed name.
     * 
     * @param key The key represented the entry to be removed.
     * 
     * @return The value for the entry being removed, or null if there is no
     *         entry.
     */
    public String remove(Object key) {
        return _pairs.remove(key);
    }

    /**
     * Returns the names for the entries in the table.
     * 
     * @return The list of names for which there are entries in the table.
     */
    public synchronized Enumeration<String> getNames() {
        return _pairs.keys();
    }

    /**
     * Determines whether or not the indication is an alert.
     * 
     * @return true if the indication is a CIM_AlertIndication, otherwise false.
     */
    public final boolean isAlertIndication() {
        return containsKey(CimConstants.ALERT_INDICATION_KEY);
    }

    /**
     * Determines whether or not the indication is an instance indication.
     * 
     * @return true if the indication is a CIM_InstIndication, otherwise false.
     */
    public final boolean isInstIndication() {
        return containsKey(CimConstants.INST_INDICATION_KEY);
    }

    /**
     * Adds name-value pairs for the given CIM object instance. A pair is
     * created for the CIM class. Then each property is processed to create more
     * name-value pairs.
     * 
     * 
     * Example:
     * 
     * (logged instance) SourceInstance = instance of Clar_FrontEndFCPort {
     * CreationClassName = "Clar_FrontEndFCPort"; DeviceID =
     * "CLARiiON+FNM00083700232+SP_B+0"; }; ;
     * 
     * (name-value pairs with prefix "SourceInstance")
     * SourceInstanceClassName|Clar_FrontEndFCPort
     * SourceInstanceCreationClassName|Clar_FrontEndFCPort
     * SourceInstanceDeviceID|CLARiiON+FNM00083700232+SP_B+0
     * 
     * @param prefix the prefix that is applied to value names
     * @param instance the CIM object instance
     */
    public void processInstance(String prefix, CIMInstance instance) {
        set(prefix + CimConstants.CLASS_NAME_KEY, instance.getClassName());
        for (CIMProperty<?> p : instance.getProperties()) {
            processProperty(prefix, p);
        }
    }

    /**
     * Adds name-value pairs for the given CIM object instance.
     * 
     * @param instance the CIM object instance
     */
    public void processInstance(CIMInstance instance) {
        processInstance("", instance);
    }

    /**
     * Adds name-value pairs for the given CIM object path. A pair is created
     * for each key, and a {@link CimCompositeID} is formulated and stored as an
     * additional name-value pair.
     * 
     * Example:
     * 
     * (logged object path) AlertingManagedElement =
     * "//172.23.120.60/root/emc:clar_diskdrive
     * .CreationClassName=\"Clar_DiskDrive\",DeviceID=\"CLARiiON+2_0_2\"
     * ,SystemCreationClassName=\"Clar_StorageSystem\",SystemName=\"CLAR
     * iiON+FNM00083700232\"";
     * 
     * (name-value pairs with prefix "AlertingManagedElement")
     * AlertingManagedElementClassName|clar_diskdrive
     * AlertingManagedElementCreationClassName|Clar_DiskDrive
     * AlertingManagedElementDeviceID|CLARiiON+2_0_2
     * AlertingManagedElementSystemCreationClassName|Clar_StorageSystem
     * AlertingManagedElementSystemName|CLARiiON+FNM00083700232
     * AlertingManagedElementCompositeID|CLARiiON+FNM00083700232/CLARiiO N+2_0_2
     * 
     * @param prefix the prefix that is applied to value names
     * @param path the CIM object path
     */
    public void processObjectPath(String prefix, CIMObjectPath path) {
        if (path.toString().length() == 0) {
            return;
        }

        set(prefix + CimConstants.CLASS_NAME_KEY, path.getObjectName());
        set(prefix + CimConstants.COMPOSITE_ID_KEY, CimCompositeID.parsePath(path));
        for (CIMProperty<?> p : path.getKeys()) {
            processProperty(prefix, p);
        }
    }

    /**
     * Adds name-value pairs for the given CIM object path.
     * 
     * @param path the CIM object path
     */
    public void processObjectPath(CIMObjectPath path) {
        processObjectPath("", path);
    }

    /**
     * Adds name-value pairs for the given CIM property. Many property types
     * result in a single name-value pair, but some explode into multiple
     * name-value pairs. The given prefix is applied to each name.
     * 
     * 
     * <b>CIM Object Path Arrays:</b>
     * 
     * A set of indexed name-value pairs is made for each element. An additional
     * name-value pair is added to store the number of non -null elements. Null
     * elements are not included.
     * 
     * Example:
     * 
     * (what this probably looks like when logged) DeadPresidents = {
     * "//172.23.120.128/root/ustreasury/President.Creation
     * ClassName=\"MonetizedPresident\",LastName=\"Washington\"
     * ,FirstName=\"George\"",
     * "//172.23.120.128/root/ustreasury/President.Creation
     * ClassName=\"MonetizedPresident\",LastName=\"Lincoln\",Fi
     * rstName=\"Abraham\"" };
     * 
     * (name-value pairs with prefix "cashDeadPresidents")
     * cashDeadPresidentsCount|2 cashDeadPresidents0ClassName|President
     * cashDeadPresidents0CreationClassName|MonetizedPresident
     * cashDeadPresidents0LastName|Washington
     * cashDeadPresidents0FirstName|George
     * cashDeadPresidents1ClassName|President
     * cashDeadPresidents1CreationClassName|MonetizedPresident
     * cashDeadPresidents1LastName|Lincoln cashDeadPresidents1FirstName|Abraham
     * 
     * 
     * <b>CIM Instance Arrays</b>
     * 
     * A set of indexed name-value pairs is made for each element. An additional
     * name-value pair is added to store the number of non -null elements. Null
     * elements are not included.
     * 
     * Example:
     * 
     * (what this probably looks like when it is logged) NeedfulThings = {
     * instance of EvilItem { CreationClassName = "ForbiddenFruit"; Location =
     * "Garden of Eden"; }, instance of EvilItem { CreationClassName =
     * "MagicRing"; Owner = "Sauron"; } };
     * 
     * (name-value pairs with prefix "mammonNeedfulThings")
     * mammonNeedfulThingsCount|2 mammonNeedfulThings0ClassName|EvilItem
     * mammonNeedfulThings0CreationClassName|ForbiddenFruit
     * mammonNeedfulThings0Location|Garden of Eden
     * mammonNeedfulThings1ClassName|EvilItem
     * mammonNeedfulThings1CreationClassName|MagicRing
     * mammonNeedfulThings1Owner|Sauron
     * 
     * 
     * <b>Other (Scalar) Arrays:</b>
     * 
     * Adds a name-value pair that contains a comma-separated list of the
     * elements. It is assumed that these values won't contain a comma
     * themselves in use cases where it matters, so an escape mechanism is
     * currently not implemented. Empty arrays are not processed.
     * 
     * Example:
     * 
     * (logged array) SourceInstance = instance of Clar_FrontEndFCPort {
     * OperationalStatus = { 10,2 }; };
     * 
     * (name-value pair with prefix "SourceInstance")
     * SourceInstanceOperationalStatus|10,2
     * 
     * 
     * <b>CIM Object Paths:</b>
     * 
     * See @processObjectPath
     * 
     * 
     * <b>CIM Object Instances:</b>
     * 
     * See @processInstance
     * 
     * 
     * <b>Strings That Represent CIM Object Paths</b>
     * 
     * If a string "looks" like an object path, an attempt is made to process it
     * as an object path in addition to processing it as an "ordinary" scalar.
     * 
     * 
     * <b>(Ordinary) Scalars:</b>
     * 
     * Example:
     * 
     * (logged scalar) PerceivedSeverity = 2;
     * 
     * (name-value pairs with prefix "") PerceivedSeverity|2
     * 
     * 
     * @param prefix the prefix that is applied to value names
     * @param p the CIM property
     */
    public void processProperty(String prefix, CIMProperty<?> p) {
        String name = prefix + p.getName();
        int type = p.getDataType().getType();
        if (p.getValue() == null) {
            // A property value can be a null reference!
            // This won't show up when the indication is
            // logged.
            return;
        }
        if (p.getDataType().isArray()) {
            switch (type) {
                case CIMDataType.REFERENCE:

                    // CIM object path array
                    for (Object o : (Object[]) p.getValue()) {
                        if (o instanceof CIMObjectPath) {
                            processObjectPath(name, (CIMObjectPath) o);
                        }
                    }
                    break;

                case CIMDataType.OBJECT:

                    // CIM object instance array
                    int count = 0;
                    for (Object o : (Object[]) p.getValue()) {
                        if (o instanceof CIMInstance) {
                            processInstance(name + count, (CIMInstance) o);
                            count++;
                        }
                    }
                    if (count > 0) {
                        set(name + CimConstants.COUNT_KEY, Integer.toString(count));
                    }
                    break;

                default:

                    // Array of scalars
                    StringBuilder buffer = new StringBuilder();
                    for (Object o : (Object[]) p.getValue()) {
                        if (o == null) {
                            continue;
                        }
                        buffer.append(',');
                        buffer.append(o.toString());
                    }

                    if (buffer.length() > 1) {
                        // Remove the leading comma
                        set(name, buffer.substring(1));
                    } else {
                        // Empty array
                        return;
                    }
            }
        } else {
            switch (type) {
                case CIMDataType.DATETIME:
                    // Date/time is converted to long. Milliseconds are truncated.
                    // s_logger.debug("Date time property is {}", name);
                    Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
                    String dateTimeStr = p.getValue().toString();
                    // s_logger.debug("Date time value is {}", dateTimeStr);
                    try {
                        CIMDateTimeAbsolute cd = new CIMDateTimeAbsolute(dateTimeStr);
                        c.set(cd.getYear(), cd.getMonth() - 1, cd.getDay(), cd.getHour(),
                                cd.getMinute(), cd.getSecond());
                        c.set(Calendar.MILLISECOND, 0);
                    } catch (Exception e) {
                        // It seems that date time properties may not be set and the
                        // value causes an exception when you create a
                        // CIMDateTimeAbsolute. Just set the date to the start of
                        // the epoch for this error condition.
                        s_logger.debug("Date and time are not set for property {}.", name);
                        c.setTimeInMillis(0);
                    }
                    // s_logger.debug("Date for property {} is {}", name, c.getTime().toString());
                    set(name, String.valueOf(c.getTimeInMillis()));
                    break;

                case CIMDataType.REFERENCE:

                    // CIM object path
                    processObjectPath(name, (CIMObjectPath) p.getValue());
                    break;

                case CIMDataType.OBJECT:

                    // CIM object instance
                    processInstance(name, (CIMInstance) p.getValue());
                    break;

                case CIMDataType.STRING:

                    // String. Check to see if it represents
                    // an object path. If so, try to process
                    // it as an object path. No matter what,
                    // fall to the default, so that it can
                    // be processed as an ordinary scalar.
                    String value = p.getValue().toString();

                    // Start with an inexpensive test.
                    // Does it look anything like a WBEM URI?
                    if (value.matches("/+[\\w\\.\\d/]+.*")) {
                        // Confirm with an expensive test.
                        // Can we make a CIMObjectPath from it?
                        CIMObjectPath path = null;
                        try {
                            path = CimObjectPathCreator.createInstance(value);
                        } catch (Exception e) {
                            s_logger.error(e.getMessage(), e);
                        }
                        if (path != null) {
                            processObjectPath(name, path);
                        }
                    }

                    // Continue processing this as an ordinary scalar

                default:

                    // Should be an ordinary scalar
                    set(name, p.getValue().toString());
            }
        }
    }

    /**
     * Adds name-value pairs for the given CIM property.
     * 
     * @param p the CIM property
     */
    public void processProperty(CIMProperty<?> p) {
        processProperty("", p);
    }

    /**
     * Sets name-value pairs:
     * 
     * IndicationClassName IndicationClassTag
     */
    private void setIndicationClass() {
        // Replace top-level "ClassName" with "IndicationClassName"
        String key = CimConstants.CLASS_NAME_KEY;
        String name = get(key);
        set(CimConstants.INDICATION_CLASS_NAME_KEY, name);
        remove(key);

        // Add a name-value pair for the rule set to reference
        // as the "Generic" field in the input that is going
        // to be sent to NOTIF. The indication class name is
        // appropriate, but underscores must be replaced with
        // periods to match the corresponding ECI names that
        // we like to use -- Periods help NOTIF organize the
        // ECI view.
        String tag = name.replace('_', '.');
        set(CimConstants.INDICATION_CLASS_TAG_KEY, tag);
    }

    /**
     * Sets name-value pairs:
     * 
     * AlertingManagedElementClassPrefixTag AlertingManagedElementClassSuffixTag
     * SourceInstanceModelPathClassPrefixTag
     * SourceInstanceModelPathClassSuffixTag
     */
    private void setImpactedClassTags() {
        String prefix = null;
        if (isAlertIndication()) {
            prefix = CimConstants.ALERT_MANAGED_ELEM_CLASS_KEY;
        } else if (isInstIndication()) {
            prefix = CimConstants.SRC_INST_MODEL_PATH_CLASS_KEY;
        }
        if (prefix == null) {
            return;
        }
        String name = get(prefix + CimConstants.NAME_KEY);
        if (name == null) {
            return;
        }
        String[] tokens = name.split("_", 2);
        set(prefix + CimConstants.PREFIX_TAG_KEY, tokens[0]);
        if (tokens.length == 2) {
            set(prefix + CimConstants.SUFFIX_TAG_KEY, tokens[1]);
        }
    }

    /**
     * Sets name-value pairs:
     * 
     * AlertTypeTag ProbableCauseTag
     */
    private void setAlertTags() {
        String key = CimConstants.ALERT_TYPE_KEY;
        if (containsKey(key)) {
            String tag = "";
            try {
                int i = Integer.parseInt(get(key));
                tag = CimAlertType.toString(i);
            } catch (NumberFormatException e) {
                s_logger.error(e.getMessage(), e);
            }

            set(CimConstants.ALERT_TYPE_TAG_KEY, tag);
            key = CimConstants.PROBABLE_CAUSE_DESCR_KEY;
            if (containsKey(key)) {
                // tag = NvTag.toCamelCase(get(key), true);
                set(CimConstants.PROBABLE_CAUSE_TAG_KEY, tag);
            }
        }
    }
}