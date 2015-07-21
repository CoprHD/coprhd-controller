/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.installer;

import com.emc.storageos.installer.controller.InstallerManager;
import com.emc.storageos.installer.controller.InstallerWizardController;
import com.emc.storageos.model.property.PropertyConstants;
import com.emc.storageos.installer.widget.InstallerWizard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class InstallerMain {
    private static final Logger log = LoggerFactory.getLogger(InstallerMain.class);

    private static void usage() {
        System.out.println("Usage: installer <vipr-release-version> <boot_mode> (<devmode>|multicast [<alive node id> ..])");
        System.out.println("\t<vipr-release-version>: e.g. vipr-2.2.0.0.71b9a4");
        System.out.println("\t<boot_mode>: init|install|config|redeploy");
        System.out.println("\t<dev_mode>: true|false(default), , only valid for init, install and config mode");
        System.out.println("\tmulticast: valid for redeploy mode, to multi-cast cluster config for redeploying node");
        System.out.println("\t<alive node id> ..: alive node IDs in the cluster which is going to multicast for redeploying node. E.g. \"vipr1 vipr3\"");
        System.exit(1);
    }
    
	public static void main(String[] args) {
        if (args.length < 2) {
            usage();
        }

        String release_version = args[0];
        String boot_mode = args[1];
        if ( !(boot_mode.equals(PropertyConstants.INIT_MODE)) &&
             !(boot_mode.equals(PropertyConstants.INSTALL_MODE)) &&
             !(boot_mode.equals(PropertyConstants.CONFIG_MODE))  &&
             !(boot_mode.equals(PropertyConstants.REDEPLOY_MODE)) ) {
            usage();
        }

        boolean multicast_redeploy = false;
        InstallerManager manager = new InstallerManager(release_version, boot_mode);
        if (!boot_mode.equals(PropertyConstants.REDEPLOY_MODE)) {
            if (args.length > 2 && args[2].equals("true")) {
                manager.setDevMode(true);
            }
        } else {
            if (args.length > 2 && args[2].equals("multicast")) {
                if (args.length == 3) {
                    // no parameter for alive nodes
                    log.error("failed to provide alive node ID list");
                    usage();
                }

                // multi-cast cluster configuration for redeploy mode
                ArrayList<String> aliveNodes = new ArrayList<String>();
                for (int i = 3; i < args.length; i++) {
                    aliveNodes.add(args[i]);
                }
//                TODO: read from ovfenv partition later for majority node recovery later
//                try {
//                    Configuration config = (Configuration) InstallerUtil.readObjectFromFile(InstallerConstants.CONFIG_FILE_PATH);
//                    config.setScenario(InstallerConstants.REDEPLOY_MODE);
//                    config.setAliveNodes(aliveNodes);
//
//                    InstallerUtil.doBroadcast(release_version, config, InstallerConstants.REDEPLOY_MULTICAST_TIMEOUT);
//                } catch (Exception e) {
//                    log.error("redeploy multicast task threw", e);
//                    System.exit(1);
//                }
                System.exit(0);
            }
        }

		InstallerWizardController wizardController = new InstallerWizardController(new InstallerWizard(), manager);
	    log.info("Starting Installer UI with options : {} - {}", release_version, boot_mode);
		wizardController.start();
	}
}
