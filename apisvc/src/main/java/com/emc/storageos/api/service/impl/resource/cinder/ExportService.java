/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.api.service.impl.resource.cinder;

import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.BlockService;
import com.emc.storageos.api.service.impl.resource.BlockServiceApi;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.api.service.impl.resource.utils.CapacityUtils;
import com.emc.storageos.api.service.impl.resource.utils.ExportUtils;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.api.service.impl.response.SearchedResRepList;
import com.emc.storageos.cinder.CinderConstants.ComponentStatus;
import com.emc.storageos.cinder.CinderConstants.ExportOperations;
import com.emc.storageos.cinder.model.CinderInitConnectionResponse;
import com.emc.storageos.cinder.model.Connector;
import com.emc.storageos.cinder.model.UsageStats;
import com.emc.storageos.cinder.model.VolumeActionRequest;
import com.emc.storageos.cinder.model.VolumeAttachResponse;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;
import com.emc.storageos.db.client.model.HostInterface.Protocol;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.HostInterface;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.QuotaOfCinder;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.ScopedLabelSet;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.util.TaskUtils;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringMapUtil;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.db.client.util.iSCSIUtility;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.block.export.ITLRestRep;
import com.emc.storageos.model.block.export.ITLRestRepList;
import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.BlockExportController;
import com.emc.storageos.volumecontroller.ControllerException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.networkcontroller.impl.NetworkAssociationHelper;

@Path("/v2/{tenant_id}/volumes")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN },
        readAcls = { ACL.OWN, ACL.ALL },
        writeRoles = { Role.TENANT_ADMIN },
        writeAcls = { ACL.OWN, ACL.ALL })
@SuppressWarnings({ "unchecked", "rawtypes" })
public class ExportService extends VolumeService {
    private static final Logger _log = LoggerFactory.getLogger(ExportService.class);
    private static final long GB = 1024 * 1024 * 1024;
    private static final int RETRY_COUNT = 15;
    private NameGenerator _nameGenerator;
    private static final String STATUS = "status";

    public NameGenerator getNameGenerator() {
        return _nameGenerator;
    }

    public void setNameGenerator(NameGenerator nameGenerator) {
        _nameGenerator = nameGenerator;
    }
    
    private QuotaHelper getQuotaHelper() {
        return QuotaHelper.getInstance(_dbClient, _permissionsHelper);
    }

    /**
     * Action could be either export or unexport volume
     * 
     * NOTE: This is an asynchronous operation.
     * 
     * @prereq none
     * 
     * @param param POST data containing the volume action information.
     * The different kinds of operations that are part of the export are
     * Reserve, unreserve, terminate, begin detach, detach, attach, init connection,
     * extend, set bootable, set Readonly
     * 
     * os-reserve: reserve a volume for initiating the attach operation.
     * os-unreserve: unreserve the volume to indicate the attach operation being performed is over.
     * os-begin_detaching: Initiate the detach operation by setting the status to detaching.
     * os-detach: Set the detach related status in the db.
     * os-terminate_connection: detach int hebackend.
     * os-initialize_connection: create export of the volume to the nova node.
     * os-attach: perform the mount of the volume that has been exported to the nova instance.
     * os-extend: extend size of volume.
     * os-reset_status: reset the status of the volume.
     * os-set_bootable: set bootable flag on volume.
     * os-update_readonly_flag: update the volume as readonly.
     * 
     * @brief Export/Unexport volume
     * @return A reference to a BlockTaskList containing a list of
     *         TaskResourceRep references specifying the task data for the
     *         volume creation tasks.
     * @throws InternalException
     * @throws InterruptedException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{volume_id}/action")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public Object actionOnVolume(@PathParam("tenant_id") String openstackTenantId,
            @PathParam("volume_id") String volumeId,
            String input
            ) throws InternalException, InterruptedException {
        // Step 1: Parameter validation
        // Eventually we should use the project id that comes from the API
        _log.info("String format input is  = {}", input);
        _log.info("Action on volume: id = {}", volumeId);
        boolean bReserve = false;
        boolean bUnReserve = false;
        boolean bTerminate = false;
        boolean bBeginDetach = false;
        boolean bDetach = false;
        boolean bAttach = false;
        boolean bInitCon = false;
        boolean bExtend = false;
        boolean bBootable = false;
        boolean bReadonly = false;

        if (input.contains(ExportOperations.OS_RESERVE.getOperation()))
            bReserve = true;
        if (input.contains(ExportOperations.OS_UNRESERVE.getOperation()))
            bUnReserve = true;
        if (input.contains(ExportOperations.OS_TERMINATE_CONNECTION.getOperation()))
            bTerminate = true;
        if (input.contains(ExportOperations.OS_BEGIN_DETACHING.getOperation()))
            bBeginDetach = true;
        if (input.contains(ExportOperations.OS_DETACH.getOperation()))
            bDetach = true;
        if (input.contains(ExportOperations.OS_ATTACH.getOperation()))
            bAttach = true;
        if (input.contains(ExportOperations.OS_INITIALIZE_CONNECTION.getOperation()))
            bInitCon = true;
        if (input.contains(ExportOperations.OS_EXTEND.getOperation())) // for expand volume
            bExtend = true;
        if (input.contains(ExportOperations.OS_SET_BOOTABLE.getOperation()))
            bBootable = true;
        if (input.contains(ExportOperations.OS_UPDATE_READONLY.getOperation()))
            bReadonly = true;

        if (input.contains(ExportOperations.OS_RESET_STATUS.getOperation())) {
            Volume vol = findVolume(volumeId, openstackTenantId);
            if (vol != null) {
                return changeVolumeStatus(vol, input);
            } else {
                return Response.status(404).build();
            }
        }

        _log.info(String.format("bReserve:  %b , bUnReserve:  %b, bTerminate:%b, bBeginDetach:%b , bDetach:%b , " +
                "bAttach:%b , bInitCon:%b , bExtend:%b, bReadonly:%b", bReserve, bUnReserve, bTerminate, bBeginDetach, bDetach, bAttach,
                bInitCon, bExtend, bReadonly));

        // TODO : handle xml format requests also and cater to the operations
        Gson gson = new Gson();
        VolumeActionRequest action = gson.fromJson(input, VolumeActionRequest.class);
        Volume vol = findVolume(volumeId, openstackTenantId);
        if (vol == null)
            throw APIException.badRequests.parameterIsNotValid(volumeId);

        // Step 2: Check if the user has rights for volume modification
        verifyUserCanModifyVolume(vol);
        _log.debug("User can modify volume");

        // Step 3: Determine action (export/unexport) and process it
        // if ( (action.attach.connector!=null) && (action.attach.connector.ip!=null) && (action.attach.connector.ip.length() > 0)){
        if ((bInitCon) && (action.attach.connector != null) && (action.attach.connector.ip != null)
                && (action.attach.connector.ip.length() > 0)) {
            String chosenProtocol = getProtocol(vol, action.attach.connector);
            boolean bIsSuccess = processAttachRequest(vol, action.attach, openstackTenantId, chosenProtocol);

            if (bIsSuccess) {
                if (chosenProtocol.equals("iSCSI")) {
                	return populateIscsiConnectionInfo(vol);
                }
                else if (chosenProtocol.equals("FC")) {
                    return populateFcConnectionInfo(chosenProtocol, vol, action, openstackTenantId);
                }                
            }
            else {
                vol.getExtensions().put("status", "OPENSTACK_ATTACHING_TIMED_OUT");
                _dbClient.updateObject(vol);
                _log.info("After fifteen tries, the ITLs are not found yet and hence failing initilize connection");
            }

            throw APIException.internalServerErrors.genericApisvcError("Export failed", new Exception(
                    "Initialize_connection operation failed due to timeout"));
        }
        else if (bDetach) {
            getVolExtensions(vol).put("status", ComponentStatus.AVAILABLE.getStatus().toLowerCase());
            _dbClient.updateObject(vol);
            return Response.status(202).build();
        }
        else if (bBeginDetach) {
            if (getVolExtensions(vol).containsKey("status")
                    && getVolExtensions(vol).get("status").equals(ComponentStatus.IN_USE.getStatus().toLowerCase()))
            {
                getVolExtensions(vol).put("status", ComponentStatus.DETACHING.getStatus().toLowerCase());
                _dbClient.updateObject(vol);
                return Response.status(202).build();
            }
            else {
                _log.info("Volume is already in detached state.");
                throw APIException.internalServerErrors
                        .genericApisvcError("Unexport failed", new Exception("Volume not in attached state"));
            }
        }
        else if (bTerminate) {
            if (getVolExtensions(vol).containsKey("status") &&
                    getVolExtensions(vol).get("status").equals(ComponentStatus.DETACHING.getStatus().toLowerCase())) {
                String chosenProtocol = getProtocol(vol, action.detach.connector);
                processDetachRequest(vol, action.detach, openstackTenantId, chosenProtocol);
                getVolExtensions(vol).put("status", ComponentStatus.AVAILABLE.getStatus().toLowerCase());
                getVolExtensions(vol).remove("OPENSTACK_NOVA_INSTANCE_ID");
                getVolExtensions(vol).remove("OPENSTACK_NOVA_INSTANCE_MOUNTPOINT");
                getVolExtensions(vol).remove("OPENSTACK_ATTACH_MODE");
                _dbClient.updateObject(vol);
                return Response.status(202).build();
            }
            else {
                _log.info("Volume not in a state for terminate connection.");
                throw APIException.internalServerErrors.genericApisvcError("Unexport failed", new Exception(
                        "Volume not in state for termination"));
            }
        }
        // else if( (action.attachToInstance!=null) && (action.attachToInstance.instance_uuid!=null) &&
        // (action.attachToInstance.instance_uuid.length() > 0)){
        else if (bAttach) {
            _log.info("IN THE IF CONDITION OF THE ATTACH VOLUME TO INSTANCE");
            if ((action.attachToInstance != null) && (action.attachToInstance.instance_uuid != null) &&
                    (action.attachToInstance.instance_uuid.length() > 0)) {
                processAttachToInstance(vol, action.attachToInstance, openstackTenantId);
                return Response.status(202).build();
            }
            else {
                throw APIException.badRequests.parameterIsNullOrEmpty("Instance");
            }
        }
        else if (bReserve) {
            _log.info("IN THE IF CONDITION OF RESERVE");
            processReserveRequest(vol, openstackTenantId);
            return Response.status(202).build();
        }
        else if (bUnReserve) {
            processUnReserveRequest(vol, openstackTenantId);
            return Response.status(202).build();
        }
        else if (bBootable) {
            _log.debug("set Volume bootable Flag");
            if (action.bootVol != null) {
                setBootable(vol, action.bootVol, openstackTenantId);
                return Response.status(200).build();
            }
            else {
                throw APIException.badRequests.parameterIsNullOrEmpty("Volume");
            }
        }
        else if (bReadonly) {
            _log.debug("Set Readonly Flag for Volume");

            if (action.readonlyVol != null) {
                setReadOnlyFlag(vol, action.readonlyVol, openstackTenantId);
                return Response.status(200).build();
            }
            else {
                throw APIException.badRequests.parameterIsNullOrEmpty("Volume");
            }
        }
        else if (bExtend) {// extend volume size
            _log.info("Extend existing volume size");

            if (action.extendVol != null) {
                extendExistingVolume(vol, action.extendVol, openstackTenantId, volumeId);
                return Response.status(202).build();
            }
            else {
                throw APIException.badRequests.parameterIsNullOrEmpty("Volume");
            }
        }

        throw APIException.badRequests.parameterIsNotValid("Action Type");
    }

    /*
     * Populate the connection info of the ISCSI volume after completing the 
     * export of volume to the host in ViPR
     */
    private VolumeAttachResponse populateIscsiConnectionInfo(Volume vol) throws InterruptedException{

        ITLRestRepList listOfItls = ExportUtils.getBlockObjectInitiatorTargets(vol.getId(), _dbClient,
                isIdEmbeddedInURL(vol.getId()));
        
		VolumeAttachResponse objCinderInit = new VolumeAttachResponse();
		objCinderInit.connection_info = objCinderInit.new ConnectionInfo();
		objCinderInit.connection_info.data = objCinderInit.new Data();
        objCinderInit.connection_info.driver_volume_type = "iscsi";
        objCinderInit.connection_info.data.access_mode = "rw";
        objCinderInit.connection_info.data.target_discovered = false;

        for (ITLRestRep itl : listOfItls.getExportList()) {

            // TODO: user setter methods to set the values of object below.
            objCinderInit.connection_info.data.target_iqn = itl.getStoragePort().getPort();
            objCinderInit.connection_info.data.target_portal = itl.getStoragePort().getIpAddress() + ":"
                    + itl.getStoragePort().getTcpPort();
            objCinderInit.connection_info.data.volume_id = getCinderHelper().trimId(vol.getId().toString());
            objCinderInit.connection_info.data.target_lun = itl.getHlu();

            _log.info(String
                    .format("itl.getStoragePort().getPort() is %s: itl.getStoragePort().getIpAddress():%s,itl.getHlu() :%s, objCinderInit.toString():%s",
                            itl.getStoragePort().getPort(), itl.getStoragePort().getIpAddress() + ":"
                                    + itl.getStoragePort().getTcpPort(), itl.getHlu(),
                            objCinderInit.toString()));

            return objCinderInit;
        }
        
        return objCinderInit;
    }

    /*
     * Populate the connection info of the FC volume after completing the 
     * export of volume to the host in ViPR
     */
    private VolumeAttachResponse populateFcConnectionInfo(String chosenProtocol, 														
														Volume vol,
														VolumeActionRequest action,
														String openstackTenantId) throws InterruptedException{
    	    
    	// After the exportt ask is complete, sometimes there is a delay in the info being reflected in ITL's. So, we are adding a
        // small delay here.
    	Thread.sleep(100000);
    	
    	ITLRestRepList listOfItls = ExportUtils.getBlockObjectInitiatorTargets(vol.getId(), _dbClient,
                isIdEmbeddedInURL(vol.getId()));
    	
        VolumeAttachResponse objCinderInit = new VolumeAttachResponse();
        objCinderInit.connection_info = objCinderInit.new ConnectionInfo();
        objCinderInit.connection_info.data = objCinderInit.new Data();
        objCinderInit.connection_info.data.target_wwn = new ArrayList<String>();
        objCinderInit.connection_info.data.initiator_target_map = new HashMap<String, List<String>>();

        objCinderInit.connection_info.driver_volume_type = "fibre_channel";
        objCinderInit.connection_info.data.access_mode = "rw";
        objCinderInit.connection_info.data.target_discovered = true;

        for (ITLRestRep itl : listOfItls.getExportList()) {
            // TODO: user setter methods to set the values of object below.
            _log.info("itl.getStoragePort().getPort() is {}", itl.getStoragePort().getPort());

            if (itl.getStoragePort().getPort() == null)
                continue;

            objCinderInit.connection_info.data.target_wwn.add(itl.getStoragePort().getPort().toString().replace(":", "")
                    .toLowerCase());
            objCinderInit.connection_info.data.volume_id = getCinderHelper().trimId(vol.getId().toString());
            objCinderInit.connection_info.data.target_lun = itl.getHlu();
            _log.info(String
                    .format("itl.getStoragePort().getPort() is %s: itl.getStoragePort().getIpAddress():%s,itl.getHlu() :%s, objCinderInit.toString():%s",
                            itl.getStoragePort().getPort(), itl.getStoragePort().getIpAddress() + ":"
                                    + itl.getStoragePort().getTcpPort(), itl.getHlu(),
                            objCinderInit.connection_info.data.toString()));
        }

        List<Initiator> lstInitiators = getListOfInitiators(action.attach.connector, openstackTenantId, chosenProtocol, vol);
        for (Initiator iter : lstInitiators) {
            _log.info("iter.getInitiatorPort() {}", iter.getInitiatorPort());
            _log.info("objCinderInit.connection_info.data.target_wwn {}", objCinderInit.connection_info.data.target_wwn);
            objCinderInit.connection_info.data.initiator_target_map.put(iter.getInitiatorPort().replace(":", "").toLowerCase(),
                    objCinderInit.connection_info.data.target_wwn);
        }
        return objCinderInit;            	    	
    }
    
    /**
     * This method is used to change the status of volume on administrator request
     * 
     * @param vol : Volume created on ViPR
     * @param input : json input
     * @return Response object if valid then 202 otherwise 404
     */
    private Response changeVolumeStatus(Volume vol, String jsonInput) {
        String vol_status = getRequestedStatusFromRequest(jsonInput);
        _log.info("Changing the status of volume : " + vol + " to " + vol_status);
        StringMap extensions = vol.getExtensions();
        extensions.put(STATUS, vol_status);
        _dbClient.updateObject(vol);
        return Response.status(202).build();
    }

    /**
     * This method take json input, parse the json input and return requested status for volume
     * 
     * @param jsonInput
     * @return
     */
    private String getRequestedStatusFromRequest(String jsonInput) {
        String jsonString[] = new String[] { ExportOperations.OS_RESET_STATUS.getOperation(), STATUS };
        Gson gson = new GsonBuilder().create();
        for (int i = 0; i < jsonString.length; i++) {
            Map<String, Object> r = gson.fromJson(jsonInput, Map.class);
            jsonInput = gson.toJson(r.get(jsonString[i]));
        }
        jsonInput = jsonInput.replace("\"", "");
        return jsonInput.trim();
    }

    private String getProtocol(Volume vol, Connector connector) {

        if ((vol.getProtocol().contains(Protocol.iSCSI.name())) && (connector.initiator != null)) {
            return Protocol.iSCSI.name();
        }
        else if (vol.getProtocol().contains(Protocol.FC.name())) {
            return Protocol.FC.name();
        }
        else {
            throw APIException.internalServerErrors.genericApisvcError("Unsupported volume protocol",
                    new Exception("The protocol specified is not supported. The protocols supported are FC and iSCSI"));
        }
    }

    private StringMap getVolExtensions(Volume vol) {
        StringMap extensions = vol.getExtensions();
        if (extensions == null) {
            extensions = new StringMap();
            vol.setExtensions(extensions);
        }
        return vol.getExtensions();
    }

    private void processReserveRequest(Volume vol, String openstackTenantId) {
        StringMap extensions = vol.getExtensions();
        if (extensions == null) {
            extensions = new StringMap();
        }
        extensions.put("status", ComponentStatus.ATTACHING.getStatus().toLowerCase());
        vol.setExtensions(extensions);
        _dbClient.updateObject(vol);
    }

    private void processUnReserveRequest(Volume vol, String openstackTenantId) {
        StringMap extensions = vol.getExtensions();
        if (extensions == null) {
            extensions = new StringMap();
        }
        extensions.put("status", ComponentStatus.AVAILABLE.getStatus().toLowerCase());
        vol.setExtensions(extensions);
        _dbClient.updateObject(vol);
    }

    private void setBootable(Volume vol, VolumeActionRequest.BootableVolume bootableVol, String openstackTenantId) {
        _log.info("Set bootable flag");
        StringMap extensions = vol.getExtensions();
        if (extensions == null) {
            extensions = new StringMap();
        }
        if (bootableVol.bootable.contains("true")) {
            extensions.put("bootable", "true");
        } else {
            extensions.put("bootable", "false");
        }

        vol.setExtensions(extensions);
        _dbClient.updateObject(vol);
    }

    private void setReadOnlyFlag(Volume vol, VolumeActionRequest.ReadOnlyVolume readonlyVolume, String openstackTenantId) {
        StringMap extensions = vol.getExtensions();
        if (extensions == null) {
            extensions = new StringMap();
        }
        if (readonlyVolume.readonly.contains("true")) {
            extensions.put("readonly", "true");
        } else {
            extensions.put("readonly", "false");
        }

        vol.setExtensions(extensions);
        _dbClient.updateObject(vol);
    }

    private void processAttachToInstance(Volume vol, VolumeActionRequest.AttachToInstance attachToInst,
            String openstackTenantId) {
        _log.info("Attach to the nova instance request");
        // Step 1: get list of host initiators to be added
        _log.info("THE ATTACH.INSTANCE IS {}", attachToInst.instance_uuid.toString());
        _log.info("ID IS {}", vol.getId().toString());
        _log.info("extensions IS {}", vol.getExtensions());
        if (vol.getExtensions() == null) {
            vol.setExtensions(new StringMap());
        }

        vol.getExtensions().put("OPENSTACK_NOVA_INSTANCE_ID", attachToInst.instance_uuid.toString());
        vol.getExtensions().put("OPENSTACK_NOVA_INSTANCE_MOUNTPOINT", attachToInst.mountpoint.toString());
        vol.getExtensions().put("OPENSTACK_ATTACH_MODE", attachToInst.mode);
        vol.getExtensions().put("status", ComponentStatus.IN_USE.getStatus().toLowerCase());
        _dbClient.updateObject(vol);
    }

    // INTERNAL FUNCTIONS
    private boolean processDetachRequest(Volume vol,
            VolumeActionRequest.DetachVolume detach,
            String openstackTenantId, String protocol) throws InterruptedException {
        _log.info("Detach request");
        // Step 1: Find export group of volume
        ExportGroup exportGroup = findExportGroup(vol);

        if (exportGroup == null)
            throw APIException.badRequests.parameterIsNotValid("volume_id");

        // Step 2: Validate initiators are part of export group
        List<URI> currentURIs = new ArrayList<URI>();
        List<URI> detachURIs = new ArrayList<URI>();
        List<Initiator> detachInitiators = getListOfInitiators(detach.connector, openstackTenantId, protocol, vol);
        currentURIs = StringSetUtil.stringSetToUriList(exportGroup.getInitiators());
        for (Initiator initiator : detachInitiators) {
            URI uri = initiator.getId();
            if (!currentURIs.contains(uri)) {
                throw APIException.badRequests.parameterIsNotValid("volume_id");
            }
            detachURIs.add(uri);
        }

        // Step 3: Remove initiators from export group
        currentURIs.removeAll(detachURIs);
        _log.info("updateExportGroup request is submitted.");
        // get block controller
        BlockExportController exportController =
                getController(BlockExportController.class, BlockExportController.EXPORT);
        // Now update export group
        String task = UUID.randomUUID().toString();
        Map<URI, Integer> volumeMap = new HashMap<URI, Integer>();
        volumeMap = StringMapUtil.stringMapToVolumeMap(exportGroup.getVolumes());

        initTaskStatus(exportGroup, task, Operation.Status.pending, ResourceOperationTypeEnum.DELETE_EXPORT_VOLUME);

        Map<URI, Integer> noUpdatesVolumeMap = new HashMap<URI, Integer>();

        List<URI> updatedInitiators = StringSetUtil.stringSetToUriList(StringSetUtil.uriListToStringSet(currentURIs));
        List<URI> updatedHosts = StringSetUtil.stringSetToUriList(exportGroup.getHosts());
        List<URI> updatedClusters = StringSetUtil.stringSetToUriList(exportGroup.getClusters());

        exportController.exportGroupUpdate(exportGroup.getId(), noUpdatesVolumeMap, noUpdatesVolumeMap,
                updatedClusters, updatedHosts, updatedInitiators, task);

        return waitForTaskCompletion(exportGroup.getId(), task);

        // TODO: If now the list is empty, change volume status to 'not exported'
        // and delete the exportGroup
    }

    private TaskResourceRep extendExistingVolume(Volume vol, VolumeActionRequest.ExtendVolume extendExistVol,
            String openstackTenantId, String volumeId) {

        // Check if the volume is on VMAX V3 which doesn't support expansion yet
        StorageSystem storage = _dbClient.queryObject(StorageSystem.class, vol.getStorageController());
        if (storage.checkIfVmax3()) {
            _log.error("Volume expansion is not supported for VMAX V3 array {}", storage.getSerialNumber());
            throw APIException.badRequests.volumeNotExpandable(vol.getLabel());
        }

        // Verify that the volume is 'expandable'
        VirtualPool virtualPool = _dbClient.queryObject(VirtualPool.class, vol.getVirtualPool());
        if (!virtualPool.getExpandable()) {
            throw APIException.badRequests.volumeNotExpandable(vol.getLabel());
        }

        // Don't operate on VPLEX backend or RP Journal volumes.
        BlockServiceUtils.validateNotAnInternalBlockObject(vol, false);

        // Don't operate on ingested volumes
        VolumeIngestionUtil.checkOperationSupportedOnIngestedVolume(vol,
                ResourceOperationTypeEnum.EXPAND_BLOCK_VOLUME, _dbClient);

        // Get the new size.
        Long newSize = extendExistVol.new_size * GB;

        // verify quota in cinder side
        Project project = getCinderHelper().getProject(openstackTenantId, getUserFromContext());
        if (!validateVolumeExpand(openstackTenantId, null, vol, newSize, project)) {
            _log.info("The volume can not be expanded because of insufficient project quota.");
            throw APIException.badRequests.insufficientQuotaForProject(project.getLabel(), "volume");
        } else if (!validateVolumeExpand(openstackTenantId, virtualPool, vol, newSize, project)) {
            _log.info("The volume can not be expanded because of insufficient quota for virtual pool.");
            throw APIException.badRequests.insufficientQuotaForVirtualPool(virtualPool.getLabel(), "virtual pool");
        }

        // When newSize is the same as current size of the volume, this can be recovery attempt from failing previous expand to cleanup
        // dangling meta members created for failed expansion
        if (newSize.equals(vol.getCapacity()) && vol.getMetaVolumeMembers() != null && !(vol.getMetaVolumeMembers().isEmpty())) {
            _log.info(String
                    .format(
                            "expandVolume --- Zero capacity expansion: allowed as a recovery to cleanup dangling members from previous expand failure.\n"
                                    +
                                    "VolumeId id: %s, Current size: %d, New size: %d, Dangling volumes: %s ", volumeId,
                            vol.getCapacity(), newSize, vol.getMetaVolumeMembers()));
        } else if (newSize <= vol.getCapacity()) {
            _log.info(String.format(
                    "expandVolume: VolumeId id: %s, Current size: %d, New size: %d ", volumeId, vol.getCapacity(), newSize));
            throw APIException.badRequests.newSizeShouldBeLargerThanOldSize("volume");
        }

        _log.info(String.format(
                "expandVolume --- VolumeId id: %s, Current size: %d, New size: %d", volumeId,
                vol.getCapacity(), newSize));

        // Get the Block service implementation for this volume.
        BlockServiceApi blockServiceApi = BlockService.getBlockServiceImpl(vol, _dbClient);

        // Verify that the volume can be expanded.
        blockServiceApi.verifyVolumeExpansionRequest(vol, newSize);

        // verify quota in vipr side
        if (newSize > vol.getProvisionedCapacity()) {
            long size = newSize - vol.getProvisionedCapacity();
            TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, vol.getTenant().getURI());
            ArgValidator.checkEntity(tenant, vol.getTenant().getURI(), false);
            ArgValidator.checkEntity(project, vol.getProject().getURI(), false);
            VirtualPool cos = _dbClient.queryObject(VirtualPool.class, vol.getVirtualPool());
            ArgValidator.checkEntity(cos, vol.getVirtualPool(), false);
            CapacityUtils.validateQuotasForProvisioning(_dbClient, cos, project, tenant, size, "volume");
        }

        // Create a task for the volume expansion.
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(Volume.class, vol.getId(),
                taskId, ResourceOperationTypeEnum.EXPAND_BLOCK_VOLUME);

        // Try and expand the volume.
        try {
            blockServiceApi.expandVolume(vol, newSize, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            String errMsg = String.format("Controller Error: %s", e.getMessage());
            op = new Operation(Operation.Status.error.name(), errMsg);
            _dbClient.updateTaskOpStatus(Volume.class, URI.create(volumeId), taskId, op);
            throw e;
        }

        auditOp(OperationTypeEnum.EXPAND_BLOCK_VOLUME, true, AuditLogManager.AUDITOP_BEGIN,
                vol.getId().toString(), vol.getCapacity(), newSize);

        StringMap extensions = vol.getExtensions();
        if (extensions == null) {
            extensions = new StringMap();
        }
        extensions.put("status", ComponentStatus.EXTENDING.getStatus().toLowerCase());
        extensions.put("task_id", taskId);
        vol.setExtensions(extensions);
        _log.debug("Volume  Status ={}", vol.getExtensions().get("status"));
        _log.debug("Volume creation task_id ={}", vol.getExtensions().get("task_id"));
        _dbClient.updateObject(vol);

        return toTask(vol, taskId, op);
    }

    boolean waitForTaskCompletion(URI resourceId, String task) throws InterruptedException {
        int tryCnt = 0;
        Task taskObj = null;
        while(tryCnt < RETRY_COUNT){
        //while (true) {
            _log.info("THE TASK var is {}", task);
            Thread.sleep(40000);
            taskObj = TaskUtils.findTaskForRequestId(_dbClient, resourceId, task);
            _log.info("THE TASKOBJ is {}", taskObj.toString());
            _log.info("THE TASKOBJ.GETSTATUS is {}", taskObj.getStatus().toString());

            if (taskObj != null) {
                if (taskObj.getStatus().equals("ready")) {
                    return true;
                }
                if (taskObj.getStatus().equals("error")) {
                    return false;
                }
                else {
                    tryCnt++;
                }
            }
            else {
                return false;
            }
        }
        return false;
    }

    private boolean processAttachRequest(Volume vol, VolumeActionRequest.AttachVolume attach,
            String openstack_tenant_id, String protocol) throws InterruptedException {
        _log.info("Attach request");
        Map<URI, Integer> volumeMap = new HashMap<URI, Integer>();
        List<URI> initiatorURIs = new ArrayList<URI>();
        String task = UUID.randomUUID().toString();
        // Step 1: get list of host initiators to be added
        _log.debug("THE ATTACH.CONNECTOR IS {}", attach.connector.toString());
        List<Initiator> newInitiators = getListOfInitiators(attach.connector, openstack_tenant_id, protocol, vol);

        ExportGroup exportGroup = findExportGroup(vol);

        if (exportGroup != null) {
            // export group exists, we need to add the initiators to it.
            volumeMap = StringMapUtil.stringMapToVolumeMap(exportGroup.getVolumes());
            initiatorURIs = StringSetUtil.stringSetToUriList(exportGroup.getInitiators());
            for (Initiator initiator : newInitiators) {
                URI uri = initiator.getId();
                if (!initiatorURIs.contains(uri)) {
                    initiatorURIs.add(uri);
                }
            }
            _log.info("updateExportGroup request is submitted.");
            // get block controller
            initTaskStatus(exportGroup, task, Operation.Status.pending, ResourceOperationTypeEnum.UPDATE_EXPORT_GROUP);
            BlockExportController exportController =
                    getController(BlockExportController.class, BlockExportController.EXPORT);
            // Now update export group

            Map<URI, Integer> noUpdatesVolumeMap = new HashMap<URI, Integer>();

            List<URI> updatedInitiators = initiatorURIs;
            List<URI> updatedHosts = StringSetUtil.stringSetToUriList(exportGroup.getHosts());
            List<URI> updatedClusters = StringSetUtil.stringSetToUriList(exportGroup.getClusters());


            exportController.exportGroupUpdate(exportGroup.getId(), noUpdatesVolumeMap, noUpdatesVolumeMap,
                    updatedClusters, updatedHosts, updatedInitiators, task);
        }
        else {
            // Create a new export group with the given list of initiators
            String name = "eg-" + vol.getLabel();
            exportGroup = createNewGroup(newInitiators, openstack_tenant_id, name);
            exportGroup.setVirtualArray(vol.getVirtualArray());
            // put volume map
            volumeMap.put(vol.getId(), ExportGroup.LUN_UNASSIGNED);
            exportGroup.addVolume(vol.getId(), ExportGroup.LUN_UNASSIGNED);
            // put list of initiators
            for (Initiator initiator : newInitiators) {
                initiatorURIs.add(initiator.getId());
            }
            exportGroup.setInitiators(StringSetUtil.uriListToStringSet(initiatorURIs));
            _dbClient.createObject(exportGroup);
            _log.info("createExportGroup request is submitted.");
            initTaskStatus(exportGroup, task, Operation.Status.pending, ResourceOperationTypeEnum.CREATE_EXPORT_GROUP);
            // get block controller
            BlockExportController exportController =
                    getController(BlockExportController.class, BlockExportController.EXPORT);
            // Now create export group
            exportController.exportGroupCreate(exportGroup.getId(), volumeMap, initiatorURIs, task);
        }

        boolean bTaskComplete = waitForTaskCompletion(exportGroup.getId(), task);
        return bTaskComplete;
    }

    private Operation initTaskStatus(ExportGroup exportGroup, String task, Operation.Status status, ResourceOperationTypeEnum opType) {
        if (exportGroup.getOpStatus() == null) {
            exportGroup.setOpStatus(new OpStatusMap());
        }
        Operation op = new Operation();
        op.setResourceType(opType);
        if (status == Operation.Status.ready) {
            op.ready();
        }
        _dbClient.createTaskOpStatus(ExportGroup.class, exportGroup.getId(), task, op);
        return op;
    }

    private ExportGroup createNewGroup(List<Initiator> initiators,
            String openstack_tenant_id, String name) {
        Project project = getCinderHelper().getProject(openstack_tenant_id, getUserFromContext());
        if (project == null)
            throw APIException.badRequests.parameterIsNotValid(openstack_tenant_id);
        URI tenantUri = project.getTenantOrg().getURI();
        TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, tenantUri);
        if (tenant == null)
            throw APIException.notFound.unableToFindUserScopeOfSystem();
        ExportGroup exportGroup = new ExportGroup();
        // TODO - For temporary backward compatibility
        exportGroup.setLabel(name);
        exportGroup.setType(ExportGroupType.Initiator.name());
        exportGroup.setId(URIUtil.createId(ExportGroup.class));
        exportGroup.setProject(new NamedURI(project.getId(), name));
        exportGroup.setTenant(new NamedURI(tenantUri, name));

        String generatedName = _nameGenerator.generate(tenant.getLabel(),
                exportGroup.getLabel(), exportGroup.getId().toString(), '_', 56);
        exportGroup.setGeneratedName(generatedName);
        return exportGroup;
    }

    private List<Initiator> getListOfInitiators(Connector connector, String tenant_id, String protocol, Volume vol) {
        List<Initiator> initiators = new ArrayList<Initiator>();
        boolean bFound = false;

        if (protocol.equals(Protocol.iSCSI.name())) {
            // this is an iSCSI request
            String port = connector.initiator;
            String hostname = connector.host;
            List<Initiator> iscsi_initiators = new ArrayList<Initiator>();
            Boolean found = searchInDb(port, iscsi_initiators, Protocol.iSCSI.name());
            if (found) {
                initiators.addAll(iscsi_initiators);
            }
            else {
                // not found, create a new one
                _log.info("Creating new iSCSI initiator, iqn = {}", port);
                // Make sure the port is a valid iSCSI port.
                if (!iSCSIUtility.isValidIQNPortName(port) && !iSCSIUtility.isValidEUIPortName(port))
                    throw APIException.badRequests.invalidIscsiInitiatorPort();
                // Find host, and if not found, create new host
                Host host = getHost(hostname, tenant_id);

                // create and populate the initiator
                Initiator initiator = new Initiator();
                initiator.setHost(host.getId());
                initiator.setHostName(connector.host);
                if (!NullColumnValueGetter.isNullURI(host.getCluster())) {
                    Cluster cluster = queryObject(Cluster.class, host.getCluster(), false);
                    initiator.setClusterName(cluster.getLabel());
                }
                initiator.setId(URIUtil.createId(Initiator.class));
                initiator.setInitiatorPort(port);
                initiator.setIsManualCreation(true);  // allows deletion via UI
                initiator.setProtocol(HostInterface.Protocol.iSCSI.name());
                addInitiatorToNetwork(initiator, vol);
                ScopedLabelSet tags = new ScopedLabelSet();
                tags.add(new ScopedLabel("openstack", "dynamic"));
                initiator.setTag(tags);

                _dbClient.createObject(initiator);
                initiators.add(initiator);
            }
        }
        else if (protocol.equals(Protocol.FC.name())) {
            // this is an FC request
            for (String fc_port : connector.wwpns) {
                // See if this initiator exists in our DB
                List<Initiator> fc_initiators = new ArrayList<Initiator>();
                Boolean found = searchInDb(fc_port, fc_initiators, Protocol.FC.name());
                if (found) {
                    bFound = true;
                    initiators.addAll(fc_initiators);
                }
                else {
                    // not found, we don't create dynamically for FC                    
                    _log.info("FC initiator for wwpn {} not found", fc_port);
                }
            }
            if (!bFound) {
                throw APIException.internalServerErrors.genericApisvcError("Export Failed",
                        new Exception("No FC initiator found for export"));
            }
        }
        else
        {
            throw APIException.internalServerErrors.genericApisvcError("Unsupported volume protocol",
                    new Exception("The protocol specified is not supported. The protocols supported are " +
                            Protocol.FC.name() + " and " + Protocol.iSCSI.name()));
        }
        return initiators;
    }

    /**
     * Add ISCSI initiator to Network
     * 
     * @param initiator
     *            ISCSI initiator
     * @param vol
     *            Volume to be attach
     */
    private void addInitiatorToNetwork(Initiator initiator, Volume vol) {
        URI varrayid = vol.getVirtualArray();
        String initiatorPort = initiator.getInitiatorPort();
        List<String> initiatorPorts = new ArrayList<String>();
        initiatorPorts.add(initiatorPort);
        List<URI> networkIds = _dbClient.queryByType(Network.class, true);
        if (null != networkIds && !networkIds.isEmpty()) {
            int validNetworkCount = 0;
            for (URI ntid : networkIds) {
                if (!NetworkAssociationHelper.getNetworkConnectedStoragePorts(
                        ntid.toString(), _dbClient).isEmpty()) {
                    validNetworkCount++;
                    StringSet varrSet = NetworkAssociationHelper
                            .getNetworkConnectedVirtualArrays(ntid, null, null,
                                    _dbClient);
                    if (varrSet.contains(varrayid.toString())) {
                        Network network = (Network) _dbClient.queryObject(ntid);
                        network.addEndpoints(initiatorPorts, false);
                        _dbClient.updateObject(network);
                    }
                }
            }
            if (validNetworkCount < 1) {
                throw APIException.internalServerErrors.genericApisvcError("Export failed", new Exception(
                        "No network is available having storage ports"));
            }
        } else {
            throw APIException.internalServerErrors.genericApisvcError("Export failed", new Exception("No network is available"));
        }
    }

    private Host getHost(String hostname, String tenant_id) {
        Host host = searchHostInDb(hostname);
        if (host == null) {
            _log.info("Creating new Host, hostname = {}", hostname);
            host = new Host();
            host.setId(URIUtil.createId(Host.class));
            StorageOSUser user = getUserFromContext();
            host.setTenant(URI.create(user.getTenantId()));
            host.setHostName(hostname);
            host.setLabel(hostname);
            host.setDiscoverable(false);
            Project proj = getCinderHelper().getProject(tenant_id, getUserFromContext());
            if (proj != null) {
                host.setProject(proj.getId());
            }
            else {
                throw APIException.badRequests.projectWithTagNonexistent(tenant_id);
            }

            host.setType(Host.HostType.Other.name());
            _dbClient.createObject(host);
        }
        return host;
    }

    private boolean validateVolumeExpand(String openstack_tenant_id, VirtualPool pool, Volume vol, long requestedSize, Project proj) {
        QuotaOfCinder objQuota = null;

        if (pool == null)
            objQuota = getQuotaHelper().getProjectQuota(openstack_tenant_id, getUserFromContext());
        else
            objQuota = getQuotaHelper().getVPoolQuota(openstack_tenant_id, pool, getUserFromContext());

        if (objQuota == null) {
            _log.info("Unable to retrive the Quota information");
            return false;
        }

        long totalSizeUsed = 0;
        UsageStats stats = null;

        if (pool != null)
            stats = getQuotaHelper().getStorageStats(pool.getId(), proj.getId());
        else
            stats = getQuotaHelper().getStorageStats(null, proj.getId());

        totalSizeUsed = stats.spaceUsed;

        _log.info(String.format("ProvisionedCapacity:%s ,TotalQuota:%s , TotalSizeUsed:%s, RequestedSize:%s, VolCapacity:%s",
                (long) (vol.getProvisionedCapacity() / GB), objQuota.getTotalQuota(), totalSizeUsed,
                (long) (requestedSize / GB), (long) vol.getCapacity() / GB));

        if ((objQuota.getTotalQuota() != QuotaService.DEFAULT_VOLUME_TYPE_TOTALGB_QUOTA)
                && (objQuota.getTotalQuota() <= (totalSizeUsed + ((long) (requestedSize / GB) - (long) (vol.getProvisionedCapacity() / GB)))))
            return false;
        else
            return true;

    }

    private Host searchHostInDb(String hostname) {
        SearchedResRepList resRepList = new SearchedResRepList(ResourceTypeEnum.HOST);
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getConstraint(Host.class, "hostName", hostname),
                resRepList);
        if (resRepList.iterator() != null) {
            for (SearchResultResourceRep res : resRepList) {
                Host host = _dbClient.queryObject(Host.class, res.getId());
                if ((host != null) && !(host.getInactive())) {
                    return host;
                }
            }
        }
        return null; // if not found
    }

    // Sometimes there are multiple entries for the same initiator
    // and so we return a list here
    private Boolean searchInDb(String port, List<Initiator> list, String protocol) {
        SearchedResRepList resRepList = new SearchedResRepList(ResourceTypeEnum.INITIATOR);
        Boolean found = false;
        String formattedStr = "";

        if (protocol.equalsIgnoreCase(Protocol.FC.name())) {
            int index = 0;
            while (index < (port.length())) {
                formattedStr += port.substring(index, index + 2).toUpperCase();
                index = index + 2;
                if (index < (port.length())) {
                    formattedStr += ":";
                }
            }
        }
        else if (protocol.equalsIgnoreCase(Protocol.iSCSI.name())) {
            formattedStr = port;
        }

        // Finds the Initiator that includes the initiator port specified, if any.
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getInitiatorPortInitiatorConstraint(formattedStr), resRepList);
        if (resRepList.iterator() != null) {
            for (SearchResultResourceRep res : resRepList) {
                Initiator initiator = _dbClient.queryObject(Initiator.class, res.getId());
                if ((initiator != null) && !(initiator.getInactive())) {
                    list.add(initiator);
                    _log.info("Found initiator in DB for port = {}", port);
                    found = true;
                }
            }
        }
        return found;
    }

}
