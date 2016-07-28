/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import org.junit.BeforeClass;
import org.junit.Test;
import java.util.List;

import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.vnxe.models.ReplicationSession;

//test ReplicationSessionRequest
public class ReplicationSessionRequestTest {
    private static KHClient _client;
    private static String host = "10.247.28.164";
    private static String userName = "admin";
    private static String password = "Password123!";

    @BeforeClass
    public static void setup() throws Exception {
        synchronized (_client) {
            _client = new KHClient(host, userName, password);
        }

    }

    @Test
    public void testGetReplicationSession() throws Exception {
        ReplicationSessionRequest req = new ReplicationSessionRequest(_client);
        List<ReplicationSession> sessions = req.get();
        for (ReplicationSession session: sessions){

           System.out.println(session.getName());
           System.out.println(session.getId());
           System.out.println("session.getSrcResourceId()");
           System.out.println("session.getDstResourceId()");
        }
    }

}
