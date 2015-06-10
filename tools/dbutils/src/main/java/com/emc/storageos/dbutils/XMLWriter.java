/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.dbutils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class XMLWriter {

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
            System.out.println(" --> Exception : " + e.getMessage());
            e.printStackTrace();
        }
        return;
    }

    // Unit test
    public static void main(String[] args) {
        XMLWriter writer = new XMLWriter();
        writer.writeXMLToFile("<TEST>(CHECK STRING FORMATTING ISSUE WITH A BRACE () )</TEST>", "XMLFile");
    }
}

