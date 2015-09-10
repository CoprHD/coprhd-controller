package com.emc.storageos.networkcontroller.impl.mds;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collections;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.NetworkSystem;
import com.emc.storageos.networkcontroller.SSHSession;
import com.emc.storageos.networkcontroller.exceptions.NetworkDeviceControllerException;
//import com.emc.storageos.networkcontroller.impl.NetworkConnectionDirector;
import com.emc.storageos.volumecontroller.impl.Dispatcher;
import com.emc.storageos.volumecontroller.impl.metering.plugins.smis.Cassandraforplugin;

public class MDSConnectionTest {
    private static final Log _log = LogFactory.getLog(MDSConnectionTest.class);
    // private NetworkConnectionDirector factory = new NetworkConnectionDirector();
    @Autowired
    private CoordinatorClientImpl coordinator = null;
    private ApplicationContext _context = null;
    private DbClient dbClient = null;
    private Dispatcher dispatcher = null;

    private void init() throws IOException {
        dbClient = Cassandraforplugin.returnDBClient();
        coordinator = new CoordinatorClientImpl(); // really need to figure this out
        dispatcher = new Dispatcher();
        dispatcher.setCoordinator(coordinator);
        dispatcher.setDeviceMaxConnectionMap(Collections.singletonMap("mds", 1));
    }

    private MDSDialog setUpDialog(NetworkSystem network) throws NetworkDeviceControllerException {
        try {
            // getConnectionFactory().acquireLease(network);
            SSHSession session = new SSHSession();
            session.connect(network.getIpAddress(), network.getPortNumber(), network.getUsername(), network.getPassword());
            MDSDialog dialog = new MDSDialog(network, session, getDefaultTimeout());
            dialog.initialize();
            return dialog;
        } catch (Exception ex) {
            String exMsg = ex.getLocalizedMessage();
            if (exMsg.equals("Auth fail"))
                exMsg = "Authorization Failed";
            if (exMsg.equals("timeout: socket is not established"))
                exMsg = "Connection Failed";
            String msg = MessageFormat.format("Could not connect to device {0}: {1}", network.getLabel(), exMsg);
            _log.error(msg);
            // getConnectionFactory().returnLease(network);
            throw NetworkDeviceControllerException.exceptions.setUpDialogFailed(network.getLabel(), exMsg, ex);
        }
    }

    // private NetworkConnectionDirector getConnectionFactory() {
    // return factory;
    // }

    private int getDefaultTimeout() {
        int defaultTimeout = 300 * 1000; // default to 5 minutes
        return defaultTimeout;
    }
}
