/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnx.xmlapi;

public class VNXFileMountAndUnmount extends VNXBaseClass {

    public VNXFileMountAndUnmount() {
        super();
    }

    public static String getDeleteMountXML(String dataMover, String mountPoint) {
        String xml = requestHeader +
                "\t<StartTask timeout=\"" + timeout + "\">\n" +
                "\t<DeleteMount mover=\"" + dataMover + "\" path=\"" + mountPoint + "\"/>\n" +
                "\t</StartTask>\n" +
                requestFooter;

        return xml;
    }

    public static String getCreateMountXML(String dataMover, String fsName, String fsId) {
        String xml = requestHeader +
                "\t<StartTask timeout=\"" + timeout + "\">\n" +
                "\t<NewMount path=\"/" + fsName + "\" fileSystem=\"" + fsId + "\" ntCredential=\"false\">\n" +
                "\t\t<MoverOrVdm mover=\"" + dataMover + "\" moverIdIsVdm=\"false\" />\n" +
                "\t\t<NfsOptions prefetch=\"false\" ro=\"false\" uncached=\"true\" virusScan=\"false\"/>\n" +
                "\t\t<CifsOptions accessPolicy=\"NATIVE\" lockingPolicy=\"nolock\" cifsSyncwrite=\"true\" notify=\"true\"" +
                " notifyOnAccess=\"true\" notifyOnWrite=\"true\" oplock=\"true\" triggerLevel=\"128\"/>" +
                "\t</NewMount>\n" +
                "\t</StartTask>\n" +
                requestFooter;

        return xml;
    }

}
