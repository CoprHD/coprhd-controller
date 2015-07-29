/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Shim base class that gives us a context of user added initiators.
 * This allows back-end management like *ExportOperations to be able to identify
 * exactly which initiators were added to masks, given the possibility that some
 * initiators associated with the mask may be "borrowed" from other masks.
 * 
 * This gives us a much more honest list of initiators that we added to the mask,
 * which helps us make better decisions when it's time to remove the mask, which
 * may impact other masks using those same initiators.
 */
public abstract class ExportMaskInitiatorCompleter extends ExportTaskCompleter {

    private List<URI> _userAddedInitiatorURIs;

    public ExportMaskInitiatorCompleter(Class clazz, URI id, String opId) {
        super(clazz, id, opId);
    }

    public ExportMaskInitiatorCompleter(Class clazz, URI id, URI emURI,
            String opId) {
        super(clazz, id, emURI, opId);
    }

    public List<URI> getUserAddedInitiatorURIs() {
        if (_userAddedInitiatorURIs == null) {
            _userAddedInitiatorURIs = new ArrayList<>();
        }
        return _userAddedInitiatorURIs;
    }

    public void addInitiator(URI initiator) {
        if (_userAddedInitiatorURIs == null) {
            _userAddedInitiatorURIs = new ArrayList<>();
        }
        _userAddedInitiatorURIs.add(initiator);
    }

    public void addInitiators(Collection<URI> initiators) {
        if (_userAddedInitiatorURIs == null) {
            _userAddedInitiatorURIs = new ArrayList<>();
        }
        for (URI initiator : initiators) {
            _userAddedInitiatorURIs.add(initiator);
        }
    }

    public void removeInitiator(URI initiator) {
        if (_userAddedInitiatorURIs != null) {
            _userAddedInitiatorURIs.remove(initiator);
        }
    }

    public void removeInitiators(Collection<URI> initiators) {
        if (_userAddedInitiatorURIs != null) {
            for (URI initiator : initiators) {
                _userAddedInitiatorURIs.remove(initiator);
            }
        }
    }
}