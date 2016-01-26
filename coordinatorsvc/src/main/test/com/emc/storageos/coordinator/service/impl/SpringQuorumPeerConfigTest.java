package com.emc.storageos.coordinator.service.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import com.emc.storageos.coordinator.client.model.Constants;
import org.apache.zookeeper.server.quorum.QuorumPeer.LearnerType;
import org.apache.zookeeper.server.quorum.QuorumPeer.QuorumServer;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpringQuorumPeerConfigTest {
    private static final Logger log = LoggerFactory.getLogger(SpringQuorumPeerConfigTest.class);

    private Properties properties;
    private SpringQuorumPeerConfig springQuorumPeerConfig;

    @Before
    public void setup() {
        properties = new Properties();
        properties.setProperty("tickTime", "2000");
        properties.setProperty("dataDir", "/tmp/zk");
        properties.setProperty("clientPort", "2181");
        properties.setProperty("initLimit", "5");
        properties.setProperty("syncLimit", "2");
        properties.setProperty("server.1", "192.168.1.1,2888,3888");
        properties.setProperty("server.2", "hostname,2888,3888");
        properties.setProperty("server.3", "[fe80:0:0:0:81fe:4fd:95b1:8bbf],2888,3888");

        springQuorumPeerConfig = new SpringQuorumPeerConfig();
    }

    @Test
    public void testInitWithCustomizedSeperator() throws Exception {
        springQuorumPeerConfig.setProperties(properties);
        springQuorumPeerConfig.init();

        assertTrue(springQuorumPeerConfig.getServers().size() == 3);

        QuorumServer server1 = springQuorumPeerConfig.getServers().get(new Long(1));
        assertTrue(server1.addr.toString().equals("/192.168.1.1:2888"));
        assertTrue(server1.electionAddr.toString().equals("/192.168.1.1:3888"));
        assertTrue(server1.type == LearnerType.PARTICIPANT);

        QuorumServer server2 = springQuorumPeerConfig.getServers().get(new Long(2));
        assertTrue(server2.addr.toString().equals("hostname:2888"));
        assertTrue(server2.electionAddr.toString().equals("hostname:3888"));
        assertTrue(server2.type == LearnerType.PARTICIPANT);

        QuorumServer server3 = springQuorumPeerConfig.getServers().get(new Long(3));
        assertTrue(server3.addr.toString().equals("/fe80:0:0:0:81fe:4fd:95b1:8bbf:2888"));
        assertTrue(server3.electionAddr.toString().equals("/fe80:0:0:0:81fe:4fd:95b1:8bbf:3888"));
        assertTrue(server3.type == LearnerType.PARTICIPANT);
    }

    @Test
    public void testServerPropertiesHasbeenRemoved() throws Exception {
        SpringQuorumPeerConfig target = new SpringQuorumPeerConfig() {
            @Override
            protected void preprocessQuorumServers(Properties zkProp) throws ConfigException {
                super.preprocessQuorumServers(zkProp);

                // check whether server properties are removed
                for (Object key : zkProp.keySet()) {
                    assertFalse(key.toString().trim().startsWith("server."));
                }
            }
        };

        target.setProperties(properties);
        target.init();
    }
}
