package com.emc.storageos.driver.ibmsvcdriver.api;

import java.util.List;

import com.emc.storageos.driver.ibmsvcdriver.exceptions.CommandException;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCDriverUtils;
import com.emc.storageos.driver.ibmsvcdriver.utils.ParsePattern;
import com.emc.storageos.storagedriver.model.StoragePort;

public class IBMSVCQueryStoragePortCommand extends
        AbstractIBMSVCQueryCommand<IBMSVCQueryStoragePortResult> {

    public static final String PORT_ID = "PortID";
    public static final String PORT_STATUS = "PortStatus";
    public static final String PORT_SPEED = "PortSpeed";
    public static final String ISCSI_NAME = "iSCSIName";
    private StoragePort storagePort = null;

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[] {
            new ParsePattern("port_id:(.*)", PORT_ID),
            new ParsePattern("port_status:(.*)", PORT_STATUS),
            new ParsePattern("port_speed:([0-9]+).*", PORT_SPEED),
            new ParsePattern("iscsi_name:(.*)", ISCSI_NAME)
    };

    public IBMSVCQueryStoragePortCommand(String nodeName) {
        addArgument("svcinfo lsnode -delim : " + "\"" + nodeName + "\"");
    }

    @Override
    protected void processError() throws CommandException {
        results.setErrorString(getErrorMessage());
        results.setSuccess(false);
    }

    @Override
    ParsePattern[] getOutputPatternSpecification() {
        return PARSING_CONFIG;
    }

    @Override
    void beforeProcessing() {
        results = new IBMSVCQueryStoragePortResult();
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        switch (spec.getPropertyName()) {
            case PORT_ID:
                storagePort = new StoragePort();
                storagePort.setPortNetworkId(IBMSVCDriverUtils.formatWWNString(capturedStrings.get(0)));
                storagePort.setNativeId(capturedStrings.get(0));
                break;
            case PORT_STATUS:
                String portStatus = capturedStrings.get(0);
                if (portStatus.equals("active")) {
                    storagePort.setOperationalStatus(StoragePort.OperationalStatus.OK);
                } else if (portStatus.equals("inactive")) {
                    storagePort.setOperationalStatus(StoragePort.OperationalStatus.NOT_OK);
                } else {
                    storagePort.setOperationalStatus(StoragePort.OperationalStatus.UNKNOWN);
                }
                break;
            case PORT_SPEED:
                storagePort.setPortSpeed(Long.parseLong(capturedStrings.get(0)));
                results.addStoragePort(storagePort);
                break;
            case ISCSI_NAME:
                storagePort = new StoragePort();
                storagePort.setPortNetworkId(capturedStrings.get(0));
                storagePort.setNativeId(capturedStrings.get(0));
                storagePort.setOperationalStatus(StoragePort.OperationalStatus.OK);
                results.addStoragePort(storagePort);
                break;
        }
        results.setSuccess(true);
    }
}
