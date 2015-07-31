/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.cimadapter.consumers;

// Java imports
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Enumeration;
import java.util.Hashtable;

// Logger imports
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An indication consumer that writes the received indications to a file in the
 * current working directory. This consumer expects the passed indication to be
 * in the form of name/value pairs passed in a Hashtable<String, String>.
 */
public class FileCimIndicationConsumer extends CimIndicationConsumer {

    // The name of the system variable that returns the current working
    // directory.
    public static final String WORKING_DIR_SYSTEM_VARIABLE = "user.dir";

    // The name of the file to which the indications are written.
    public static final String INDICATIONS_FILE_NAME = "IndicationsFile.txt";

    // A reference to a logger.
    private static final Logger s_logger = LoggerFactory.getLogger(FileCimIndicationConsumer.class);

    /**
     * {@inheritDoc}
     */
    public void consumeIndication(Object indicationData) {
        // Expects the data as a hashtable of name/value pairs.
        if (indicationData == null) {
            s_logger.error("File consumer received null data.");
            return;
        }

        if (!(indicationData instanceof Hashtable<?, ?>)) {
            s_logger.error("File consumer expects a hashtable of name/value pairs.");
            return;
        }

        try {
            @SuppressWarnings("unchecked")
            Hashtable<String, String> indicationsTable = (Hashtable<String, String>) indicationData;
            StringBuffer fileNameBuff = new StringBuffer(System.getProperty(WORKING_DIR_SYSTEM_VARIABLE));
            fileNameBuff.append(File.separator);
            fileNameBuff.append(INDICATIONS_FILE_NAME);
            File outFile = new File(fileNameBuff.toString());
            FileWriter fileWriter = new FileWriter(outFile, true);
            BufferedWriter bufferedFileWriter = new BufferedWriter(fileWriter);
            Enumeration<String> nameEnum = indicationsTable.keys();
            while (nameEnum.hasMoreElements()) {
                String name = nameEnum.nextElement();
                String value = indicationsTable.get(name);
                bufferedFileWriter.write(name + ": " + value);
                bufferedFileWriter.newLine();
            }
            bufferedFileWriter.newLine();
            bufferedFileWriter.close();
        } catch (Exception e) {
            s_logger.error("Exception writing indication data to file", e);
        }
    }
}
