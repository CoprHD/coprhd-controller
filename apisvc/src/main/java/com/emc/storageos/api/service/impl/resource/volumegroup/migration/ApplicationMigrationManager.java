package com.emc.storageos.api.service.impl.resource.volumegroup.migration;

import static com.emc.storageos.db.client.constraint.AlternateIdConstraint.Factory.getVolumesByAssociatedId;

import java.net.URI;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.placement.PlacementManager;
import com.emc.storageos.api.service.impl.resource.TenantsService;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.application.ApplicationMigrationParam;
import com.emc.storageos.security.audit.AuditLogManager;

public class ApplicationMigrationManager {
	
	// A reference to a database client.
    private final DbClient _dbClient;

    // A reference to a permissions helper.
    private PermissionsHelper _permissionsHelper = null;

    // A reference to the audit log manager.
    private AuditLogManager _auditLogManager = null;

    // A reference to the placement manager
    private PlacementManager _placementManager = null;

    // A reference to the full copy request.
    protected HttpServletRequest _request;

    // A reference to the security context
    private final SecurityContext _securityContext;

    // A reference to the URI information.
    private final UriInfo _uriInfo;
    
    private ApplicationMigrationApiImpl _migrationImpl;
   
    // A reference to a logger.
    private static final Logger logger = LoggerFactory.getLogger(ApplicationMigrationManager.class);
	
    /**
     * Constructor
     * 
     * @param dbClient A reference to a database client.
     * @param permissionsHelper A reference to a permission helper.
     * @param auditLogManager A reference to an audit log manager.
     * @param coordinator A reference to the coordinator.
     * @param placementManager A reference to the placement manager.
     * @param securityContext A reference to the security context.
     * @param uriInfo A reference to the URI info.
     * @param request A reference to the full copy request.
     * @param tenantsService A reference to the tenants service or null.
     */
    public ApplicationMigrationManager(DbClient dbClient, PermissionsHelper permissionsHelper,
            AuditLogManager auditLogManager, CoordinatorClient coordinator,
            PlacementManager placementManager, SecurityContext securityContext,
            UriInfo uriInfo, HttpServletRequest request, TenantsService tenantsService) {
        _dbClient = dbClient;
        _permissionsHelper = permissionsHelper;
        _auditLogManager = auditLogManager;
        _placementManager = placementManager;
        _securityContext = securityContext;
        _uriInfo = uriInfo;
        _request = request;  
        
        _migrationImpl = new ApplicationMigrationApiImpl();
    }
    
    public ApplicationMigrationApiImpl getMigrationImpl() {
		return _migrationImpl;
	}

	public void setMigrationImpl(ApplicationMigrationApiImpl _migrationImpl) {
		this._migrationImpl = _migrationImpl;
	}    
    
	//TODO: Does this method really return a TaskList or just a Task? 
    public TaskList performApplicationMigrationOperation(final URI volumeGroupId, final ApplicationMigrationParam param, 
    											ResourceOperationTypeEnum opType) {
                    	
    	if (null == param) {
    		//throw exception here
    	}
    	        
        VolumeGroup volumeGroup = _dbClient.queryObject(VolumeGroup.class, volumeGroupId);  
        
        if (null == volumeGroup) {
        	//throw exception here
        }
        
        List<Volume> volumes = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, Volume.class,
                getVolumesByAssociatedId(volumeGroupId.toString()));
        
        if (!volumes.isEmpty()) {
        	//throw exception here
        }
        
        TaskList taskList = new TaskList();              

        logger.info("ApplicationMigration : Operation {}", opType.name());
        
        switch (opType) {
            case MIGRATION_CREATE:               	
            	getMigrationImpl().migrationCreate();
                break;
            case MIGRATION_MIGRATE:
                break;
            case MIGRATION_COMMIT:
                break;
            case MIGRATION_CANCEL:               
                break;
            case MIGRATION_REFRESH:
            	break;
            case MIGRATION_RECOVER:
            	break;
            case MIGRATION_REMOVE_ENV:
            	break;
            case MIGRATION_SYNCSTART:
            	break;
            case MIGRATION_SYNCSTOP:
            	break;
            default:
                logger.error("Unsupported operation {}", opType.getDescription());
                break;
        }            
        return taskList;
    }
}
