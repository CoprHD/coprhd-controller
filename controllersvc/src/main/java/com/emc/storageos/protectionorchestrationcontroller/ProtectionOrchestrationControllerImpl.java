package com.emc.storageos.protectionorchestrationcontroller;

import java.net.URI;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.block.Copy;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.Dispatcher;

public class ProtectionOrchestrationControllerImpl implements ProtectionOrchestrationController {
    private Dispatcher _dispatcher;
    private ProtectionOrchestrationController _controller;
    private DbClient _dbClient;
    
    // Operations
    private static final String PERFORM_SRDF_PROTECTION_OPERATION = "performSRDFProtectionOperation";
    
    private void execOrchestration(String methodName, Object... args) throws ControllerException {
        _dispatcher.queue(NullColumnValueGetter.getNullURI(), PROTECTION_ORCHESTRATION_DEVICE,
                getController(), methodName, args);
    }
    
    @Override
    public void performSRDFProtectionOperation(URI storageSystemId, Copy copy, String op, String task) {
        execOrchestration(PERFORM_SRDF_PROTECTION_OPERATION, storageSystemId, copy, op, task);
    }

    public Dispatcher getDispatcher() {
        return _dispatcher;
    }

    public void setDispatcher(Dispatcher dispatcher) {
        this._dispatcher = dispatcher;
    }

    public ProtectionOrchestrationController getController() {
        return _controller;
    }

    public void setController(ProtectionOrchestrationController controller) {
        this._controller = controller;
    }

    public DbClient getDbClient() {
        return _dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this._dbClient = dbClient;
    }
}