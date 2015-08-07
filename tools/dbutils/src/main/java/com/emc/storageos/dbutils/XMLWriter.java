/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.dbutils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class XMLWriter {
    private static final Logger log = LoggerFactory.getLogger(XMLWriter.class);

    public void writeXMLToFile(String eventData, String baseName) {
        try {
            StringBuilder fileName = new StringBuilder(baseName);
            Date now = new Date();

            SimpleDateFormat format = new SimpleDateFormat("_MMMdd_HHmmsszzz");
            fileName.append(format.format(now)).append(".xml");

            File file = new File(fileName.toString());
            FileWriter fileWriter = new FileWriter(file, true);
            BufferedWriter out = new BufferedWriter(fileWriter);

            out.write(eventData);
            out.newLine();
            out.close();
            System.out.println(" -> Output file available at : " + file.getAbsolutePath());
        } catch (IOException e) {
            System.out.println(" --> Exception : " + e);
            log.error("Caught Exception: ", e);
        }
        return;
    }

    // Unit test
    public static void main(String[] args) {
        XMLWriter writer = new XMLWriter();
        writer.writeXMLToFile("<TEST>(CHECK STRING FORMATTING ISSUE WITH A BRACE () )</TEST>", "XMLFile");
    }
}
