/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering;

import java.io.File;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.TimeSeriesSerializer;
import com.emc.storageos.plugins.common.Constants;

public abstract class XMLStatsDumpGenerator {
    
    protected Logger              _logger = LoggerFactory
            .getLogger(XMLStatsDumpGenerator.class);
    
    /**
     * Dump Metering Records as XML to disk
     * 
     * @param <T>
     * @param record
     */
    @SuppressWarnings("unchecked")
    public <T extends TimeSeriesSerializer.DataPoint> void dumpRecordstoXML(Map<String, Object> keyMap) {
        PrintWriter writer = null;
        try {
            List<Stat> records = (List<Stat>) keyMap.get(Constants._Stats);
            Map<String, String> meteringProps = (Map<String, String>) keyMap.get(Constants.PROPS);
            String location = constructFileName(meteringProps, keyMap);
            checkAndCreateDirectory(meteringProps.get(Constants.METERINGDUMPLOCATION));
            writer = new PrintWriter(new File(location));
            XMLMarshaller marshaller = new XMLMarshaller() {
                @Override
                public void tailer(PrintWriter writer) {
                    delegatetailer(writer);
                }

                @Override
                public void header(PrintWriter writer) {
                    delegateheader(writer);
                }
            };
            marshaller.dumpXML(writer, records);
        } catch (Exception ex) {
            _logger.error("Dump Failed : ", ex);
        } finally {
            writer.close();
        }
    }

    /**
     * Verify whether directory already exists or not.
     * if it is not there create one.
     * @param directoryName
     */
    private void checkAndCreateDirectory(final String directoryName) {
        try {
            final File dir = new File(directoryName);
            if (!dir.exists() && dir.mkdirs()) {
                _logger.debug("Debug dump directory created successfully.");
            }
        } catch (Exception ex) {
            _logger.error("Debug dump directory creation failed due to {}",
                    ex.getMessage());
        }
    }

    /**
     * Delegate Header to the corresponding subclass of cassandraInsertion.
     * 
     * @param writer
     */
    protected abstract void delegateheader(PrintWriter writer);

    /**
     * Delegate tailer to the corresponding subclass of CassandraInsertion.
     * 
     * @param writer
     */
    protected abstract void delegatetailer(PrintWriter writer);


    /**
     * Generate Unique key, delegate to subclass of CassandraInsertion.
     * This uniqueKey is to identify the XML Stats dump file name uniquely.
     * @param keyMap
     * @return
     */
    protected abstract String generateUniqueKey(Map<String, Object> keyMap);

   /**
     * Construct an unique fileName for Stats XML Dump
     * 
     * @return
     */
    private String constructFileName(Map<String, String> meteringProps, Map<String, Object> keyMap) {
        final StringBuilder dumpFile = new StringBuilder();
        dumpFile.append(meteringProps.get(Constants.METERINGDUMPLOCATION).toString())
                .append("/")
                .append(generateUniqueKey(keyMap))
                .append(getDateTime())
                .append(".xml");
        return dumpFile.toString();
    }

    /**
     * get current Time
     * 
     * @return String
     */
    private String getDateTime() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        Calendar currentDate = Calendar.getInstance();
        return formatter.format(currentDate.getTime());
    }

}
