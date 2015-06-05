/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package controllers.resources;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import models.datatable.FileSnapshotsDataTable;
import models.datatable.ShareACLDataTable;

import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.With;
import util.BourneUtil;
import util.FileUtils;
import util.FileUtils.ExportRuleInfo;
import util.MessagesUtils;
import util.datatable.DataTablesSupport;

import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.model.file.ExportRules;
import com.emc.storageos.model.file.FileCifsShareACLUpdateParams;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSnapshotRestRep;
import com.emc.storageos.model.file.FileSystemShareParam;
import com.emc.storageos.model.file.ShareACL;
import com.emc.storageos.model.file.ShareACLs;
import com.emc.storageos.model.file.SmbShareResponse;
import com.emc.storageos.model.file.SnapshotCifsShareACLUpdateParams;
import com.emc.storageos.model.file.SnapshotExportUpdateParams;
import com.emc.storageos.volumecontroller.FileShareExport;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.exceptions.ServiceErrorException;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.util.FlashException;

@With(Common.class)
public class FileSnapshots extends ResourceController {

    private static final String UNKNOWN = "resources.filesnapshots.unknown";
	private static FileSnapshotsDataTable fileSnapshotsDataTable = new FileSnapshotsDataTable();

    public static void snapshots(String projectId) {
        setActiveProjectId(projectId);
        renderArgs.put("dataTable", fileSnapshotsDataTable);
        addReferenceData();
        render();
    }    

    public static void snapshotsJson(String projectId) {
    	if (StringUtils.isNotBlank(projectId)) {
    		setActiveProjectId(projectId);
    	}
    	else {
    		projectId = getActiveProjectId();
    	}    	
        List<FileSnapshotsDataTable.FileSnapshot> fileSnapshots = FileSnapshotsDataTable.fetch(uri(projectId));
        renderJSON(DataTablesSupport.createJSON(fileSnapshots, params));
    }

    public static void snapshot(String snapshotId) {
    	ViPRCoreClient client = BourneUtil.getViprClient();

        FileSnapshotRestRep snapshot = null;
        try {
            snapshot = client.fileSnapshots().get(uri(snapshotId));
        }
        catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404 || e.getHttpCode() == 400) {
                flash.error(MessagesUtils.get(UNKNOWN, snapshotId));
                snapshots(null);
            }
            throw e;
        }

    	if(snapshot != null) {
    		if (snapshot.getParent() != null) {
    			FileShareRestRep fileSystem = client.fileSystems().get(snapshot.getParent());
    			renderArgs.put("fileSystem", fileSystem);
    		}
    		
	        Tasks<FileSnapshotRestRep> tasksResponse = client.fileSnapshots().getTasks(snapshot.getId());
	        List<Task<FileSnapshotRestRep>> tasks = tasksResponse.getTasks();
	        renderArgs.put("tasks", tasks);    		
    	}
    	else {
            flash.error(MessagesUtils.get(UNKNOWN, snapshotId));
            snapshots(null);
    	}
    	
    	render(snapshot);
    }    
    
    public static void snapshotExports(String snapshotId) {
    	
    	ViPRCoreClient client = BourneUtil.getViprClient();
    	List<ExportRule> exports = client.fileSnapshots().getExport(uri(snapshotId), true, "");
    	
    	renderArgs.put("permissionTypeOptions", Lists.newArrayList(FileShareExport.Permissions.values()));    	
        render(exports);
    } 
    
    public static void fileSnapshotExportsJson(String id, String path, String sec) {
    	ExportRuleInfo infos = FileUtils.getFSExportRulesInfo(uri(id), path, sec);
        renderJSON(infos);
    }
    
    public static void snapshotShares(String snapshotId) {
    	
    	ViPRCoreClient client = BourneUtil.getViprClient();
    	
    	List<SmbShareResponse> shares = client.fileSnapshots().getShares(uri(snapshotId));
    	
        render(shares);
    }        
    
    @FlashException(referrer={"snapshot"})
    public static void deleteSnapshotShare(String snapshotId, String shareName) {
    	
    	ViPRCoreClient client = BourneUtil.getViprClient();

    	Task<FileSnapshotRestRep> task = client.fileSnapshots().removeShare(uri(snapshotId), shareName);
		flash.put("info", MessagesUtils.get("resources.filesnapshot.share.deactivate"));
    	
        snapshot(snapshotId);
    }   
    
    @FlashException(referrer={"snapshot"})
    public static void deleteSnapshotExport(String snapshotId, String security, String exportPath) {
        ViPRCoreClient client = BourneUtil.getViprClient();
        
        ExportRule rule = new ExportRule();
        rule.setSecFlavor(security);
        
        List<ExportRule> list = Lists.newArrayList();
        list.add(rule);
        
        ExportRules exportRules = new ExportRules();
    	exportRules.setExportRules(list);
    	
    	SnapshotExportUpdateParams params = new SnapshotExportUpdateParams();
    	params.setExportRulesToDelete(exportRules);
    	
    	FileSnapshotRestRep snapshot = client.fileSnapshots().get(uri(snapshotId));
    	String subDir = FileUtils.findSubDirectory(snapshot.getMountPath(), exportPath);
        client.fileSnapshots().updateExport(uri(snapshotId), subDir, params);

        flash.put("info", MessagesUtils.get("resources.filesnapshot.export.deactivate"));

        snapshot(snapshotId);
    }          
    
    @FlashException(referrer={"snapshot"})
    public static void deleteFileSnapshot(String fileSnapshotId) {
        if (StringUtils.isNotBlank(fileSnapshotId)) {
    		ViPRCoreClient client = BourneUtil.getViprClient();

    		Task<FileSnapshotRestRep> task = client.fileSnapshots().deactivate(uri(fileSnapshotId));
    		flash.put("info", MessagesUtils.get("resources.filesnapshot.deactivate"));
        }
        snapshot(fileSnapshotId);
    }            
    
    @FlashException(value="snapshots")
    public static void delete(@As(",") String[] ids) {
        delete(uris(ids));
    }

    private static void delete(List<URI> ids) {
    	if (ids != null) {
    		ViPRCoreClient client = BourneUtil.getViprClient();
    		List<Task<FileSnapshotRestRep>> tasks = Lists.newArrayList();
    		for (URI id : ids) {
    			Task<FileSnapshotRestRep> task = client.fileSnapshots().deactivate(id);
    			tasks.add(task);
    		}
    		if (tasks.size() > 0) {
    			flash.put("info", MessagesUtils.get("resources.filesnapshots.deactivate", tasks.size()));
    		}
    	}
        snapshots(null);
    }
    
    @FlashException(referrer={"snapshot"})
    public static void save(Boolean edit, String id, String fsPath, String exportPath, String security, 
    		String anon, @As(",") List<String> ro, @As(",") List<String> rw, @As(",") List<String> root) {
    	
    	ExportRule rule = new ExportRule();
		rule.setAnon(anon);
		rule.setSecFlavor(security);
		
		// Clean up endpoints list by removing all empty items if any
		List<String> empty = Arrays.asList("");
		rw.removeAll(empty);
		ro.removeAll(empty);
		root.removeAll(empty);
		
		if (!ro.isEmpty()) {
			rule.setReadOnlyHosts(FileUtils.buildEndpointList(ro));	
		}
		if (!rw.isEmpty()) {
			rule.setReadWriteHosts(FileUtils.buildEndpointList(rw));	
		}
		if (!root.isEmpty()) {
			rule.setRootHosts(FileUtils.buildEndpointList(root));	
		}
		
		List<ExportRule> addRules = Lists.newArrayList();
		addRules.add(rule);
    	
    	ExportRules exportRules = new ExportRules();
    	exportRules.setExportRules(addRules);
    	
    	SnapshotExportUpdateParams params = new SnapshotExportUpdateParams();
    	if (!edit) {
    		params.setExportRulesToAdd(exportRules);
    	} else {
    		params.setExportRulesToModify(exportRules);
    	}
    	
    	ViPRCoreClient client = BourneUtil.getViprClient();
    	client.fileSnapshots().updateExport(uri(id), null, params);
    	
    	flash.put("info", MessagesUtils.get("resources.filesystem.export.update"));
    	snapshot(id);
    }
    
    /**
     * This method called when Access Control List page is loaded.
     * @param snapShotId
     *        SnapShot id of the provided SnapShot.
     * @param shareName
     *        Name of the file share.
     */ 
     public static void listSnapshotAcl(String snapshotId, String shareName){
    	 renderArgs.put("dataTable", new ShareACLDataTable());
         renderArgs.put("snapshotId", uri(snapshotId));
         renderArgs.put("shareName", shareName);
         renderArgs.put("aclURL", "/file/snapshots/"+uri(snapshotId)+"/shares/"+shareName+"/acl");
         ViPRCoreClient client = BourneUtil.getViprClient();
         FileSnapshotRestRep restRep = client.fileSnapshots().get(uri(snapshotId));
         renderArgs.put("snapshotName", restRep.getName());
         SnapshotShareACLForm shareACL = new SnapshotShareACLForm();
         render(shareACL);
    	
     }
     
     /**
      * This method called during population of Datatable for ACL data.
      * @param aclURL
      *        URL of the file system share.
      */
     public static void listSnapshotAclJson(String aclURL){
    	 String snapshotId = null;
     	String shareName = null;
     	if (StringUtils.isNotBlank(aclURL)) {
            String[] parts = aclURL.split("/");
            if (parts.length == 7) {
            	snapshotId = parts[3];
               shareName = parts[5];
            }
         }
     	 ViPRCoreClient client = BourneUtil.getViprClient();
         List<ShareACL> shareAcls = client.fileSnapshots().getShareACLs(uri(snapshotId),shareName);
         List<ShareACLDataTable.SnapshotAclInfo> acl = Lists.newArrayList();
         for(ShareACL shareAcl : shareAcls){
             String userOrGroup = shareAcl.getUser();
             String type = SnapshotShareACLForm.USER;
             if (shareAcl.getGroup() != null && shareAcl.getGroup() != ""){
                type = SnapshotShareACLForm.GROUP;
                userOrGroup = shareAcl.getGroup();
             }
             acl.add(new ShareACLDataTable.SnapshotAclInfo(userOrGroup, type, shareAcl.getPermission(), snapshotId, shareName, shareAcl.getDomain()));
          }
         renderJSON(DataTablesSupport.createJSON(acl, params));
     }
     
     /**
      * This method called When user selects ACLs and hit delete button.
      * @param aclURL
      *        URL of the snapshot share.
      * @param ids
      *        ids of the selected ACL
      */
     @FlashException(referrer={"snapshot"})
     public static void removeSnapShotAcl(String aclUrl, @As(",") String[] ids){
    	 
    	 ShareACLs aclsToDelete = new ShareACLs();
     	List<ShareACL> shareAcls = new ArrayList<ShareACL>();
     	String snapshotId = null; 
     	String shareName = null;
     	if (ids != null && ids.length > 0) {
            for (String id : ids) {
                String type = SnapshotShareACLForm.extractTypeFromId(id);
                String name = SnapshotShareACLForm.extractNameFromId(id);
 	            String domain = SnapshotShareACLForm.extractDomainFromId(id);
 	            snapshotId = SnapshotShareACLForm.extractSnapshotFromId(id);
                shareName = SnapshotShareACLForm.extractShareNameFromId(id);
                ShareACL ace = new ShareACL();
                if (SnapshotShareACLForm.GROUP.equalsIgnoreCase(type)){
                   ace.setGroup(name);
                }else{
                   ace.setUser(name);
                }
                if (domain != null && !"".equals(domain) && !"null".equals(domain)){
                   ace.setDomain(domain);
                }
                shareAcls.add(ace);
            }
            aclsToDelete.setShareACLs(shareAcls);
            SnapshotCifsShareACLUpdateParams input = new SnapshotCifsShareACLUpdateParams();
            input.setAclsToDelete(aclsToDelete);
            ViPRCoreClient client = BourneUtil.getViprClient();
            client.fileSnapshots().updateShareACL(uri(snapshotId),shareName,input);
         }
     	 flash.success(MessagesUtils.get("resources.filesystem.share.acl.deleted"));
     	 listSnapshotAcl(snapshotId,shareName);
    	 
     }
     
     /**
      * This method called when user Adds an access control entry from UI.
      * @param aclURL
      *        URL of the snapshot share.
      * @param shareACL
      *        This contains the form data from UI. 
      */
     public static void addSnapshotAcl(String aclURL, SnapshotShareACLForm shareACL, String formAccessControlList){
    	 
    	String snapshotId = null;
     	String shareName = null;
     	if (StringUtils.isNotBlank(aclURL)) {
            String[] parts = aclURL.split("/");
            if (parts.length == 7) {
            	snapshotId = parts[3];
                shareName = parts[5];
            }
         }
     	if (formAccessControlList == null || "".equals(formAccessControlList)) {
            flash.error(MessagesUtils.get("resources.filesystem.share.acl.invalid.settings"),null);
            listSnapshotAcl(snapshotId,shareName);
         }
     	SnapshotCifsShareACLUpdateParams input = createSnapshotCifsShareAclParams(formAccessControlList);
     	ViPRCoreClient client = BourneUtil.getViprClient();
         try{
      	   client.fileSnapshots().updateShareACL(uri(snapshotId),shareName,input);
         }catch(ServiceErrorException  e){
            flash.error(e.getMessage(),null);
            listSnapshotAcl(snapshotId,shareName);
     	}
      	flash.success(MessagesUtils.get("resources.filesystem.share.acl.added"));
      	listSnapshotAcl(snapshotId,shareName);
    	 
     }
     
     private static SnapshotCifsShareACLUpdateParams createSnapshotCifsShareAclParams(String formData){
         String [] uiAcls = formData.split(",");
         List<ShareACL> acls = new ArrayList<ShareACL>();
         for (String uiAce:uiAcls){
            String [] uiData = uiAce.split("~~~");
            String uiType = uiData[0];
            String uiName = uiData[1];
            String uiDomain = uiData[2];
            String uiPermission = uiData[3];
     	   ShareACL shareAcl = new ShareACL();
            if("GROUP".equalsIgnoreCase(uiType)){
               shareAcl.setGroup(uiName);
            }else{
               shareAcl.setUser(uiName);
            }
            shareAcl.setPermission(uiPermission);
            if (uiDomain != null && !"".equals(uiDomain)){
               shareAcl.setDomain(uiDomain);
            }
         
            acls.add(shareAcl);
         }
         SnapshotCifsShareACLUpdateParams input = new SnapshotCifsShareACLUpdateParams();
         ShareACLs aclsToAdd = new ShareACLs();
         aclsToAdd.setShareACLs(acls);
         input.setAclsToAdd(aclsToAdd);
         return input;
      }
     
     /**
      * This method called when user adds sub directory.
      * @param id
      *        id of the file system.
      * @param shareName
      *        shareName of the sub directory.
      * @param subDirectory
      *        name of the sub directory.
      * @param description
      *        Given description during creation of sub directory.
      */
     
      @FlashException(referrer={"snapshot"})
      public static void addSnapShotSubDir(String id, String shareName, String subDirectroy, String description) {
          ViPRCoreClient client = BourneUtil.getViprClient();
          FileSystemShareParam param = new FileSystemShareParam();
          param.setShareName(shareName);
          if(subDirectroy != null && !"".equals(subDirectroy)){
             param.setSubDirectory(subDirectroy);
          }
          if(description != null && !"".equals(description)){
             param.setDescription(description);
          }
          client.fileSnapshots().share(uri(id), param);
          flash.put("info", MessagesUtils.get("resources.filesystem.subdir.add"));
          snapshot(id);
      }
     
     /**
      * This static class holds the form data for the ACL page.
      */ 
      public static class SnapshotShareACLForm {
      	
      	private static final String ID_DELIMITER = "~~~~";
      	public static final String USER = "User";
        public static final String GROUP = "Group";
          public String domain;
      	public String id;
          @Required
          public String name;
          @Required
          public String type= USER;
          @Required
          public String permission="Read";
          
          public SnapshotCifsShareACLUpdateParams createSnapshotCifsShareAclParams(){
             ShareACL shareAcl = new ShareACL();
             if(GROUP.equalsIgnoreCase(type.trim())){
                shareAcl.setGroup(name.trim());
             }else{
                shareAcl.setUser(name.trim());
             }
             shareAcl.setPermission(permission);
             if (domain.trim() != null && !"".equals(domain.trim())){
                 shareAcl.setDomain(domain.trim());
             }
             List<ShareACL> acls = new ArrayList<ShareACL>();
             acls.add(shareAcl);
             SnapshotCifsShareACLUpdateParams input = new SnapshotCifsShareACLUpdateParams();
             ShareACLs aclsToAdd = new ShareACLs();
             aclsToAdd.setShareACLs(acls);
             input.setAclsToAdd(aclsToAdd);
             return input;
          }
          
          public void validate(String formName) {
              Validation.valid(formName, this);
              Validation.required(formName + ".name", name);
              Validation.required(formName + ".type", type);
              Validation.required(formName + ".permission", permission);
              if (name == null || "".equals(name)) {
                 Validation.addError(formName + ".name", "resources.filesystem.share.acl.invalid.name");
              }
          }
          
          public static String createId(String name, String type, String fileSystem, String shareName, String domain) {
              return name + ID_DELIMITER + type + ID_DELIMITER + fileSystem + ID_DELIMITER + shareName + ID_DELIMITER + domain;
          }
          
          public static String extractNameFromId(String id) {
              if (StringUtils.isNotBlank(id)) {
                 String[] parts = id.split(ID_DELIMITER);
                 if (parts.length == 5) {
                    return parts[0];
                 }else {
                    return id;
                 }
              }
              return null;
          }
          
          public static String extractTypeFromId(String id) {
              if (StringUtils.isNotBlank(id)) {
                 String[] parts = id.split(ID_DELIMITER);
                 if (parts.length == 5) {
                    return parts[1];
                 }else {
                    return id;
                 }
              }
              return null;
          }

          public static String extractSnapshotFromId(String id) {
              if (StringUtils.isNotBlank(id)) {
                 String[] parts = id.split(ID_DELIMITER);
                 if (parts.length == 5) {
                    return parts[2];
                 }else {
                    return id;
                 }
              }
              return null;
          }

          public static String extractShareNameFromId(String id) {
              if (StringUtils.isNotBlank(id)) {
                 String[] parts = id.split(ID_DELIMITER);
                 if (parts.length == 5) {
                    return parts[3];
                 }else {
                    return id;
                 }
              }
              return null;
          }

          public static String extractDomainFromId(String id) {
              if (StringUtils.isNotBlank(id)) {
                 String[] parts = id.split(ID_DELIMITER);
                 if (parts.length == 5) {
                    return parts[4];
                 }else {
                    return id;
                 }
              }
              return null;
          }
      	
      }
}
