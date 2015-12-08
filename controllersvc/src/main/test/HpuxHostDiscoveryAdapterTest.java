/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.emc.hpux.HpuxSystem;
import com.emc.hpux.command.GetHpuxVersionCommand;
import com.emc.hpux.model.HpuxVersion;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.util.EndpointUtility;
import com.emc.storageos.util.SanUtils;
import com.iwave.ext.linux.model.HBAInfo;

public class HpuxHostDiscoveryAdapterTest {

    @Test
    void testVersion() {
        HpuxSystem hpux = new HpuxSystem("lglal017", 22, "root", "dangerous");

        hpux.executeCommand(new GetHpuxVersionCommand());
    }

    public HpuxVersion getHpuxMinimumVersion(boolean forceLookup) {
        if (forceLookup || aixVersion == null) {
            String versionProp = this.getSysProperty(HPUX_MIN_PROP);
            if (isValidVersionNumber(versionProp)) {
                hpuxVersion = new HpuxVersion(versionProp);
            }
            else {
                hpuxVersion = null;
                throw new IllegalStateException(String.format("System property for HPUX Version Number(%s) is invalid - value is '%s'",
                        HPUX_MIN_PROP, versionProp));
            }
        }
        return hpuxVersion;
    }

    @Test
    public void testDiscovery() {
        List<Initiator> oldInitiators = new ArrayList<>();

        HpuxSystem hpux = new HpuxSystem("lglal017", 22, "root", "dangerous");
        List<Initiator> addedInitiators = new ArrayList<>();

        try {
            for (HBAInfo hba : hpux.listInitiators()) {
                Initiator initiator;
                String wwpn = SanUtils.normalizeWWN(hba.getWwpn());
                initiator = create(wwpn);

                // discoverFCInitiator(host, initiator, hba);
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail("Fatal exception");
        }
    }

    private Initiator create(String port) {

        Initiator initiator = new Initiator();
        initiator.setInitiatorPort(port);
        initiator.setLabel(EndpointUtility.changeCase(port));

        initiator.setIsManualCreation(false);
        return initiator;
    }

}
