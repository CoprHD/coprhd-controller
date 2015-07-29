/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.logsvc;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.emc.vipr.model.sys.logging.LogRequest;

import javax.xml.bind.*;

import org.junit.Test;
import org.junit.Before;

public class LogRequestTest {
    private static final String PATH = "build" + File.separator + "tmp";

    @Before
    public void before() {
        File path = new File(PATH);
        if (!path.exists()) {
            path.mkdirs();
        }
    }

    @Test
    public void testMarshal() throws Exception {
        System.out.println("Entering testMatshal()");
        LogRequest req = new LogRequest();
        List<String> baseNames = new ArrayList<String>();
        List<String> nodeIds = new ArrayList<String>();
        baseNames.add("apisvc");
        nodeIds.add("standalone");
        req.setBaseNames(baseNames);
        req.setMaxCount(10);
        req.setNodeIds(nodeIds);
        File file = new File(PATH + File.separator + "file.xml");
        JAXBContext jc = JAXBContext.newInstance(LogRequest.class);
        Marshaller marshaller = jc.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(req, file);
        marshaller.marshal(req, System.out);
        System.out.println("Done testMastshal()");
    }

    @Test
    public void testUnMarshal() throws Exception {
        System.out.println("Entering testUnMarshal()");
        File file = new File(PATH + File.separator + "file.xml");
        JAXBContext jc = JAXBContext.newInstance(LogRequest.class);
        Unmarshaller jaxbUnmarshaller = jc.createUnmarshaller();
        LogRequest req = (LogRequest) jaxbUnmarshaller.unmarshal(file);
        System.out.println(req);
        System.out.println("Done testUnMarshal()");
    }

    @Test
    public void testMarshalUnMarshal() throws Exception {
        System.out.println("Entering testMarshlUnMarshal()");
        LogRequest req = new LogRequest();
        List<String> baseNames = new ArrayList<String>();
        List<String> nodeIds = new ArrayList<String>();
        baseNames.add("apisvc");
        nodeIds.add("standalone");
        req.setBaseNames(baseNames);
        req.setMaxCount(10);
        req.setNodeIds(nodeIds);
        File file = new File(PATH + File.separator + "file.xml");
        JAXBContext jc = JAXBContext.newInstance(LogRequest.class);
        Marshaller marshaller = jc.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(req, file);
        Unmarshaller jaxbUnmarshaller = jc.createUnmarshaller();
        LogRequest reqNew = (LogRequest) jaxbUnmarshaller.unmarshal(file);
        assertTrue("The object after marshal and unMarshal should be the same with "
                + "the origianl one", req.toString().equals(reqNew.toString()));
        System.out.println("Done testMarshlUnMarshal() ");
    }

}
