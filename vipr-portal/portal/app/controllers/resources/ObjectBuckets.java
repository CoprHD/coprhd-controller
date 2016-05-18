/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.resources;

import static com.emc.sa.util.ResourceType.BUCKET;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import models.datatable.BucketACLDataTable;
import models.datatable.ObjectBucketsDataTable;

import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.i18n.Messages;
import play.mvc.Util;
import play.mvc.With;
import util.BourneUtil;
import util.MessagesUtils;
import util.StringOption;
import util.VirtualArrayUtils;
import util.VirtualPoolUtils;
import util.datatable.DataTablesSupport;

import com.emc.sa.util.ResourceType;
import com.emc.storageos.model.object.BucketACE;
import com.emc.storageos.model.object.BucketACL;
import com.emc.storageos.model.object.BucketDeleteParam;
import com.emc.storageos.model.object.BucketRestRep;
import com.emc.storageos.model.object.ObjectBucketACLUpdateParams;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.util.FlashException;

@With(Common.class)
public class ObjectBuckets extends ResourceController {

    private static final String UNKNOWN = "resources.buckets.unknown";
    protected static final String DELETED = "resources.buckets.acl.deleted";
    protected static final String ADDED = "resources.buckets.acl.added";
    protected static final String USER = "User";
    protected static final String GROUP = "Group";
    protected static final String CUSTOMGROUP = "Customgroup";

    private static ObjectBucketsDataTable objectbucketsDataTable = new ObjectBucketsDataTable();

    public static void buckets(String projectId) {
        setActiveProjectId(projectId);
        renderArgs.put("dataTable", objectbucketsDataTable);
        addReferenceData();
        render();
    }

    public static void bucketsJson(String projectId) {
        if (StringUtils.isNotBlank(projectId)) {
            setActiveProjectId(projectId);
        } else {
            projectId = getActiveProjectId();
        }
        List<ObjectBucketsDataTable.Bucket> buckets = ObjectBucketsDataTable.fetch(uri(projectId));
        renderJSON(DataTablesSupport.createJSON(buckets, params));
    }

    public static void bucket(String bucketId) {

        ViPRCoreClient client = BourneUtil.getViprClient();

        BucketRestRep bucket = null;
        if (isBucketId(bucketId)) {
            try {
                bucket = client.objectBuckets().get(uri(bucketId));
            } catch (ViPRHttpException e) {
                if (e.getHttpCode() == 404) {
                    flash.error(MessagesUtils.get(UNKNOWN, bucketId));
                    buckets(null);
                }
                throw e;
            }
        }
        if (bucket == null) {
            notFound(Messages.get("resources.bucket.notfound"));
        }

        if (bucket.getVirtualArray() != null) { // NOSONAR
                                                // ("Suppressing Sonar violation of Possible null pointer dereference of volume. When volume is null, the previous if condition handles with throw")
            renderArgs.put("virtualArray", VirtualArrayUtils.getVirtualArrayRef(bucket.getVirtualArray()));
        }
        if (bucket.getVirtualPool() != null) {
            renderArgs.put("virtualPool", VirtualPoolUtils.getObjectVirtualPoolRef(bucket.getVirtualPool()));
        }

        Tasks<BucketRestRep> tasksResponse = client.objectBuckets().getTasks(bucket.getId());
        List<Task<BucketRestRep>> tasks = tasksResponse.getTasks();
        renderArgs.put("tasks", tasks);

        render(bucket);
    }

    @FlashException(referrer = { "bucket" })
    public static void deleteBucket(String bucketId, String deleteType) {
        if (StringUtils.isNotBlank(bucketId)) {
            ViPRCoreClient client = BourneUtil.getViprClient();

            boolean forceDelete = false;
            Task<BucketRestRep> task = client.objectBuckets().deactivate(uri(bucketId),
                    new BucketDeleteParam(forceDelete, deleteType));
            flash.put("info", MessagesUtils.get("resources.bucket.deactivate"));
        }
        bucket(bucketId);
    }

    @FlashException(value = "buckets")
    public static void delete(@As(",") String[] ids, String deleteType) {
        delete(uris(ids), deleteType);
    }

    private static void delete(List<URI> ids, String deleteType) {
        if (ids != null) {
            ViPRCoreClient client = BourneUtil.getViprClient();
            List<Task<BucketRestRep>> tasks = Lists.newArrayList();
            for (URI id : ids) {
                boolean forceDelete = false;
                Task<BucketRestRep> task = client.objectBuckets().deactivate(id,
                        new BucketDeleteParam(forceDelete, deleteType));
                tasks.add(task);
            }
            if (!tasks.isEmpty()) {
                flash.put("info", MessagesUtils.get("resources.buckets.deactivate", tasks.size()));
            }
        }
        buckets(null);
    }

    @Util
    private static boolean isBucketId(String id) {
        return ResourceType.isType(BUCKET, id);
    }
    
    public static void listBucketACL(String id) {
        renderArgs.put("dataTable", new BucketACLDataTable());

        renderArgs.put("bucketId", uri(id));
        ViPRCoreClient client = BourneUtil.getViprClient();
        BucketRestRep bucket = client.objectBuckets().get(uri(id));
        renderArgs.put("bucketName", bucket.getName());
        BucketACLForm bucketACL = new BucketACLForm();
        render(bucketACL);

    }

    public static void listBucketACLJson(String id) {

        ViPRCoreClient client = BourneUtil.getViprClient();
        List<BucketACE> bucketAcl = client.objectBuckets().getBucketACL(uri(id));
        List<BucketACLDataTable.AclInfo> acl = Lists.newArrayList();
        for (BucketACE ace : bucketAcl) {
            String userOrGroupOrCustomgroup = ace.getUser();
            String type = USER;
            if (ace.getGroup() != null && !ace.getGroup().isEmpty()) {
                type = GROUP;
                userOrGroupOrCustomgroup = ace.getGroup();
            } else if (ace.getCustomGroup() != null && !ace.getCustomGroup().isEmpty()) {
                type = CUSTOMGROUP;
                userOrGroupOrCustomgroup = ace.getCustomGroup();
            }
            acl.add(new BucketACLDataTable.AclInfo(userOrGroupOrCustomgroup, type, ace.getPermissions(), id, ace.getDomain()));
        }

        renderJSON(DataTablesSupport.createJSON(acl, params));

    }

    /**
     * This method called When user selects ACLs and hit delete button.
     * 
     * @param aclURL
     *            URL of the file system share.
     * @param ids
     *            ids of the selected ACL
     */
    @FlashException(value = "buckets")
    public static void deleteAcl(String bucketId, @As(",") String[] ids) {
        BucketACL aclsToDelete = new BucketACL();
        List<BucketACE> bucketAcl = Lists.newArrayList();

        if (ids != null && ids.length > 0) {
            for (String id : ids) {
                String type = BucketACLForm.extractTypeFromId(id);
                String name = BucketACLForm.extractNameFromId(id);
                String domain = BucketACLForm.extractDomainFromId(id);
                BucketACE ace = new BucketACE();
                if (GROUP.equalsIgnoreCase(type)) {
                    ace.setGroup(name);
                } else if (CUSTOMGROUP.equalsIgnoreCase(type)) {
                    ace.setCustomGroup(name);
                } else {
                    ace.setUser(name);
                }
                if (domain != null && !"".equals(domain) && !"null".equals(domain)) {
                    ace.setDomain(domain);
                }
                bucketAcl.add(ace);
            }
            aclsToDelete.setBucketACL(bucketAcl);
            ObjectBucketACLUpdateParams input = new ObjectBucketACLUpdateParams();
            input.setAclToDelete(aclsToDelete);
            ViPRCoreClient client = BourneUtil.getViprClient();
            client.objectBuckets().updateBucketACL(uri(bucketId), input);
        }
        flash.success(MessagesUtils.get(DELETED));
        listBucketACL(bucketId);

    }

    public static void editBucketAce(@Required String id) {

        String type = BucketACLForm.extractTypeFromId(id);
        String name = BucketACLForm.extractNameFromId(id);
        String domain = BucketACLForm.extractDomainFromId(id);
        String bucketId = BucketACLForm.extractBucketFromId(id);
        String permissions = BucketACLForm.extractPermissionsFromId(id);
        if ("null".equals(domain)) {
            domain = "";
        }

        BucketACLForm bucketACL = new BucketACLForm();
        bucketACL.type = type;
        bucketACL.domain = domain;
        bucketACL.name = name;
        String[] strPerm = permissions.split("\\|");
        bucketACL.permissions = new HashSet<String>(Arrays.asList(strPerm));

        List<StringOption> permissionOptions = BucketACLForm.getPermissionOptions();
        renderArgs.put("permissionOptions", permissionOptions);
        renderArgs.put("bucketId", bucketId);
        ViPRCoreClient client = BourneUtil.getViprClient();
        BucketRestRep restRep = client.objectBuckets().get(uri(bucketId));
        renderArgs.put("bucketName", restRep.getName());
        renderArgs.put("TYPE", type.toUpperCase());
        render(bucketACL);

    }

    @FlashException(keep = true, referrer = { "editBucketAce" })
    public static void saveBucketAce(BucketACLForm bucketACL) {

        String name = params.get("name");
        String type = params.get("type");
        String domain = params.get("domain");
        String bucketId = params.get("bucketId");
        Set<String> permissions = bucketACL.permissions;
        String strPer = "";
        for (String permission : permissions) {
            strPer = strPer + permission + "|";
        }
        if (strPer.length() > 0) {
            strPer = strPer.substring(0, strPer.length() - 1);
        }

        List<BucketACE> acl = Lists.newArrayList();
        BucketACE ace = new BucketACE();
        BucketACL aclToModify = new BucketACL();

        if (GROUP.equalsIgnoreCase(type)) {
            ace.setGroup(name);
        } else if (CUSTOMGROUP.equalsIgnoreCase(type)) {
            ace.setCustomGroup(name);
        } else {
            ace.setUser(name);
        }
        ace.setPermissions(strPer);
        if (domain != null && !domain.isEmpty()) {
            ace.setDomain(domain);
        }
        acl.add(ace);
        aclToModify.setBucketACL(acl);

        ObjectBucketACLUpdateParams updateParam = new ObjectBucketACLUpdateParams();
        updateParam.setAclToModify(aclToModify);
        ViPRCoreClient client = BourneUtil.getViprClient();
        client.objectBuckets().updateBucketACL(uri(bucketId), updateParam);

        listBucketACL(bucketId);
    }

    public static void addBucketAcl(String bucketId,
            BucketACLForm bucketACL, String formAccessControlList) {

        if (formAccessControlList == null || formAccessControlList.isEmpty()) {
            flash.error(MessagesUtils
                    .get("resources.bucket.acl.invalid.settings"));
            listBucketACL(bucketId);
        }
        ObjectBucketACLUpdateParams updateParam = createObjectBucketACLUpdateParams(formAccessControlList);
        ViPRCoreClient client = BourneUtil.getViprClient();
        try {
            client.objectBuckets().updateBucketACL(uri(bucketId), updateParam);
        } catch (Exception e) {
            flash.error(e.getMessage(), null);
            listBucketACL(bucketId);
        }
        flash.success(MessagesUtils.get(ADDED));
        listBucketACL(bucketId);

    }

    private static ObjectBucketACLUpdateParams createObjectBucketACLUpdateParams(String formData) {
        String[] uiAcls = formData.split(",");
        List<BucketACE> aces = Lists.newArrayList();
        for (String uiAce : uiAcls) {
            String[] uiData = uiAce.split("~~~");
            String uiType = uiData[0];
            String uiName = uiData[1];
            String uiDomain = uiData[2];
            String uiPermissions = uiData[3];
            BucketACE bucketAce = new BucketACE();

            if (uiDomain != null && !uiDomain.isEmpty() && !"null".equals(uiDomain)) {
                bucketAce.setDomain(uiDomain);
            }
            if (GROUP.equalsIgnoreCase(uiType.trim())) {
                bucketAce.setGroup(uiName.trim());

            }else if (CUSTOMGROUP.equalsIgnoreCase(uiType.trim())) {
                bucketAce.setCustomGroup(uiName.trim());
            } else {
                bucketAce.setUser(uiName.trim());
            }
            if (uiPermissions != null && !"".equals(uiPermissions) && !"null".equals(uiPermissions)) {
                bucketAce.setPermissions(uiPermissions);
            }

            aces.add(bucketAce);
        }
        BucketACL aclToAdd = new BucketACL();
        aclToAdd.setBucketACL(aces);
        ObjectBucketACLUpdateParams input = new ObjectBucketACLUpdateParams();
        input.setAclToAdd(aclToAdd);
        return input;
    }

    /**
     * This static class holds the form data for the Bucket ACL page.
     */
    public static class BucketACLForm {

        private static final String ID_DELIMITER = "~~~~";
        public String domain;
        public String id;
        @Required
        public String name;
        @Required
        public String type = "USER";
        @Required
        public Set<String> permissions;

        public void validate(String formName) {
            Validation.valid(formName, this);
            Validation.required(formName + ".name", name);
            Validation.required(formName + ".type", type);
            Validation.required(formName + ".permissions", permissions);
            if (name == null || "".equals(name)) {
                Validation.addError(formName + ".name", "resources.filesystem.share.acl.invalid.name");
            }
        }

        public static String createId(String name, String type, String bucketId, String domain, String permissions) {
            return name + ID_DELIMITER + type + ID_DELIMITER + bucketId + ID_DELIMITER + domain + ID_DELIMITER + permissions;
        }

        public static String extractNameFromId(String id) {
            if (StringUtils.isNotBlank(id)) {
                String[] parts = id.split(ID_DELIMITER);
                if (parts.length == 5) {
                    return parts[0];
                } else {
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
                } else {
                    return id;
                }
            }
            return null;
        }

        public static String extractBucketFromId(String id) {
            if (StringUtils.isNotBlank(id)) {
                String[] parts = id.split(ID_DELIMITER);
                if (parts.length == 5) {
                    return parts[2];
                } else {
                    return id;
                }
            }
            return null;
        }

        public static String extractDomainFromId(String id) {
            if (StringUtils.isNotBlank(id)) {
                String[] parts = id.split(ID_DELIMITER);
                if (parts.length == 5) {
                    return parts[3];
                } else {
                    return id;
                }
            }
            return null;
        }

        public static String extractPermissionsFromId(String id) {
            if (StringUtils.isNotBlank(id)) {
                String[] parts = id.split(ID_DELIMITER);
                if (parts.length == 5) {
                    return parts[4];
                } else {
                    return id;
                }
            }
            return null;
        }

        public static List<StringOption> getPermissionOptions() {

            List<StringOption> permissionOptions = Lists.newArrayList();
            permissionOptions.add(new StringOption("read", "Read"));
            permissionOptions.add(new StringOption("write", "Write"));
            permissionOptions.add(new StringOption("execute", "Execute"));
            permissionOptions.add(new StringOption("full_control", "Full Control"));
            permissionOptions.add(new StringOption("delete", "Delete"));
            permissionOptions.add(new StringOption("none", "None"));
            permissionOptions.add(new StringOption("privileged_write", "Privileged Write"));
            permissionOptions.add(new StringOption("read_acl", "Read Acl"));
            permissionOptions.add(new StringOption("write_acl", "Write Acl"));
            return permissionOptions;
        }

    }

}
