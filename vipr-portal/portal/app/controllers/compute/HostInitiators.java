/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.compute;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import models.BlockProtocols;
import models.datatable.HostInitiatorDataTable;

import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.i18n.Messages;
import play.mvc.With;
import util.HostUtils;
import util.MessagesUtils;
import util.datatable.DataTablesSupport;

import com.emc.storageos.db.client.util.EndpointUtility;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.host.BaseInitiatorParam;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.InitiatorCreateParam;
import com.emc.storageos.model.host.InitiatorRestRep;
import com.emc.storageos.model.host.PairedInitiatorCreateParam;
import com.emc.storageos.model.valid.Endpoint;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;
import controllers.util.ViprResourceController;

@With(Common.class)
@Restrictions({ @Restrict("TENANT_ADMIN") })
public class HostInitiators extends ViprResourceController {

    protected static final String ADDED = "initiators.added";
    protected static final String DELETED = "initiators.deleted";

    public static void list(String hostId) {
        HostRestRep host = HostUtils.getHost(uri(hostId));
        if (host == null) {
            flash.error(Messages.get(Hosts.UNKNOWN, hostId));
            Hosts.list();
        }
        boolean initiatorsEditable = host.getDiscoverable() == null ? false : !host.getDiscoverable();
        InitiatorForm initiator = new InitiatorForm();
        renderArgs.put("dataTable", new HostInitiatorDataTable());
        if (!initiatorsEditable) {
            flash.put("warning", MessagesUtils.get("initiators.notEditable"));
        }
        render(host, initiator, initiatorsEditable);
    }

    public static void listJson(String hostId) {
        List<InitiatorRestRep> initiators = HostUtils.getInitiators(uri(hostId));
        renderJSON(DataTablesSupport.createJSON(initiators, params));
    }

    // We do not actually support edit right now but the route is used in DataTable.crud
    public static void edit(String hostId) {
        error(Messages.get("HostInitiators.notSupported"));
    }

    @FlashException
    public static void create(String hostId, InitiatorForm initiator) {
        initiator.validate("initiator");
        if (Validation.hasErrors()) {
            params.flash();
            Validation.keep();
            list(hostId);
        }

        HostUtils.createInitiator(uri(hostId), initiator.toCreateParam());
        flash.success(MessagesUtils.get(ADDED, initiator.port));
        list(hostId);
    }

    @FlashException
    public static void createPair(String hostId, PairedInitiatorForm pairedInitiator) {
        pairedInitiator.validate("pairedInitiator");
        if (Validation.hasErrors()) {
            params.flash();
            Validation.keep();
            list(hostId);
        }

        HostUtils.createInitiatorPair(uri(hostId), pairedInitiator.toCreateParam());
        flash.success(MessagesUtils.get(ADDED, pairedInitiator.firstPort));
        flash.success(MessagesUtils.get(ADDED, pairedInitiator.secondPort));
        list(hostId);
    }

    @FlashException
    public static void delete(String hostId, @As(",") String[] ids) {
        if (ids != null && ids.length > 0) {

	    List<String> associatedInitiators = new ArrayList<String>();

            for (String initiatorId : ids) {
                InitiatorRestRep initiator = getViprClient().initiators().get(uri(initiatorId));
		RelatedResourceRep associatedInitiator = initiator.getAssociatedInitiator();
                                
                if (associatedInitiator != null){
                associatedInitiators.add(associatedInitiator.toString());
                }
		
		if (!associatedInitiators.contains(initiatorId)){
                HostUtils.deactivateInitiator(initiator);
		}
            }
            flash.success(MessagesUtils.get(DELETED));
        }
        list(hostId);
    }

    @FlashException
    public static void deregisterHostInitiators(String hostId, @As(",") String[] ids) {
        deregisterHostInitiators(hostId, uris(ids));
    }

    private static void deregisterHostInitiators(String hostId, List<URI> ids) {
        performSuccessFail(ids, new DeregisterOperation(), DEREGISTER_SUCCESS, DEREGISTER_ERROR);
        list(hostId);
    }

    @FlashException
    public static void registerHostInitiators(String hostId, @As(",") String[] ids) {
        registerHostInitiators(hostId, uris(ids));
    }

    private static void registerHostInitiators(String hostId, List<URI> ids) {
        performSuccessFail(ids, new RegisterOperation(), REGISTER_SUCCESS, REGISTER_ERROR);
        list(hostId);
    }

    private static String getProtocolFromWWN(String wwn) {
        if (StringUtils.startsWith(wwn, "iqn.")) {
            return BlockProtocols.iSCSI;
        }
        else {
            return BlockProtocols.FC;
        }
    }

    public static class DeregisterOperation implements ResourceIdOperation<InitiatorRestRep> {

        @Override
        public InitiatorRestRep performOperation(URI id) throws Exception {
            getViprClient().initiators().deregister(id);
            return getViprClient().initiators().get(id);
        }

    }

    public static class RegisterOperation implements ResourceIdOperation<InitiatorRestRep> {

        @Override
        public InitiatorRestRep performOperation(URI id) throws Exception {
            return getViprClient().initiators().register(id);
        }

    }

    public static class InitiatorForm {
        public String node;
        @Required
        public String port;

        public InitiatorCreateParam toCreateParam() {
            String protocol = getProtocolFromWWN(port);

            InitiatorCreateParam initiator = new InitiatorCreateParam();
            initiator.setProtocol(protocol);
            if (BlockProtocols.isFC(protocol)) {
                initiator.setNode(node.trim());
                initiator.setPort(port.trim());
            }
            else {
                initiator.setPort(port.trim());
            }
            return initiator;
        }

        public void validate(String formName) {
            Validation.valid(formName, this);

            String protocol = getProtocolFromWWN(port.trim());
            if (BlockProtocols.isFC(protocol)) {
                Validation.required(formName + ".node", node);
                Validation.required(formName + ".port", port);
                if (node != null && !EndpointUtility.isValidEndpoint(node.trim(), Endpoint.EndpointType.WWN)) {
                    Validation.addError(formName + ".node", "initiators.port.invalidWWN");
                }
                if (port != null && !EndpointUtility.isValidEndpoint(port.trim(), Endpoint.EndpointType.WWN)) {
                    Validation.addError(formName + ".port", "initiators.port.invalidWWN");
                }
            }
            else {
                boolean valid = EndpointUtility.isValidEndpoint(port.trim(), Endpoint.EndpointType.IQN);
                if (!valid) {
                    Validation.addError(formName + ".port", "initiators.port.invalidIQN");
                }
            }
        }

    }

    public static class PairedInitiatorForm {
        public String firstNode;
        @Required
        public String firstPort;

        public String secondNode;
        @Required
        public String secondPort;

        public PairedInitiatorCreateParam toCreateParam() {
            String protocol = getProtocolFromWWN(firstPort);

            PairedInitiatorCreateParam pairedInitiator = new PairedInitiatorCreateParam();
            BaseInitiatorParam firstInitiator = new BaseInitiatorParam();
            BaseInitiatorParam secondInitiator = new BaseInitiatorParam();
            firstInitiator.setProtocol(protocol);
            if (BlockProtocols.isFC(protocol)) {
                firstInitiator.setNode(firstNode.trim());
                firstInitiator.setPort(firstPort.trim());
            }
            else {
                firstInitiator.setPort(firstPort.trim());
            }
            secondInitiator.setProtocol(protocol);
            if (BlockProtocols.isFC(protocol)) {
                secondInitiator.setNode(secondNode.trim());
                secondInitiator.setPort(secondPort.trim());
            }
            else {
                secondInitiator.setPort(secondPort.trim());
            }
            pairedInitiator.setFirstInitiator(firstInitiator);
            pairedInitiator.setSecondInitiator(secondInitiator);
            return pairedInitiator;
        }

        public void validate(String formName) {
            Validation.valid(formName, this);

            String protocol = getProtocolFromWWN(firstPort.trim());
            if (BlockProtocols.isFC(protocol)) {
                Validation.required(formName + ".firstNode", firstNode);
                Validation.required(formName + ".firstPort", firstPort);
                if (firstNode != null && !EndpointUtility.isValidEndpoint(firstNode.trim(), Endpoint.EndpointType.WWN)) {
                    Validation.addError(formName + ".firstNode", "initiators.port.invalidWWN");
                }
                if (firstPort != null && !EndpointUtility.isValidEndpoint(firstPort.trim(), Endpoint.EndpointType.WWN)) {
                    Validation.addError(formName + ".firstPort", "initiators.port.invalidWWN");
                }
            }
            else {
                boolean valid = EndpointUtility.isValidEndpoint(firstPort.trim(), Endpoint.EndpointType.IQN);
                if (!valid) {
                    Validation.addError(formName + ".firstPort", "initiators.port.invalidIQN");
                }
            }

            protocol = getProtocolFromWWN(secondPort.trim());
            if (BlockProtocols.isFC(protocol)) {
                Validation.required(formName + ".secondNode", secondNode);
                Validation.required(formName + ".secondPort", secondPort);
                if (secondNode != null && !EndpointUtility.isValidEndpoint(secondNode.trim(), Endpoint.EndpointType.WWN)) {
                    Validation.addError(formName + ".secondNode", "initiators.port.invalidWWN");
                }
                if (secondPort != null && !EndpointUtility.isValidEndpoint(secondPort.trim(), Endpoint.EndpointType.WWN)) {
                    Validation.addError(formName + ".secondPort", "initiators.port.invalidWWN");
                }
            }
            else {
                boolean valid = EndpointUtility.isValidEndpoint(secondPort.trim(), Endpoint.EndpointType.IQN);
                if (!valid) {
                    Validation.addError(formName + ".secondPort", "initiators.port.invalidIQN");
                }
            }
        }

    }

}
