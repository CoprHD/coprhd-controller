/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.monitoring.cim;

import java.util.Hashtable;
import java.util.Iterator;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableDeviceEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.utility.CIMConstants;

public abstract class CIMRecordableDeviceEvent extends RecordableDeviceEvent {

    public CIMRecordableDeviceEvent(DbClient dbClient) {
        super(dbClient);
    }

    /**
     * Appends the passed extensions to the extensions for the passed event.
     * 
     * @param event
     *            The event to which the extensions are added.
     * @param newExtensions
     *            The extensions to be added.
     */
    public static String getEventExtensions(
            Hashtable<String, String> newExtensions) {

        StringBuilder strBuilder = new StringBuilder();

        // Iterate over the passed extensions and append each.
        Iterator<String> newExtensionsIter = newExtensions.keySet().iterator();
        while (newExtensionsIter.hasNext()) {

            // Add a separator between each extension name/value pair.
            if (strBuilder.length() > 0) {
                strBuilder.append(CIMConstants.EXTENSION_SEPARATOR);
            }

            // Add the new extension in the from of name=value.
            String extensionName = newExtensionsIter.next();
            String extensionValue = newExtensions.get(extensionName);
            strBuilder.append(extensionName);
            strBuilder.append("=");
            strBuilder.append(extensionValue);
        }

        return strBuilder.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAlertType() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getService() {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSeverity() {
        // TODO Auto-generated method stub
        return null;
    }
}
