/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package controllers.auth;

import com.emc.storageos.api.service.impl.resource.UserGroupService;
import com.emc.storageos.model.auth.AuthnProviderRestRep;
import com.emc.storageos.model.usergroup.*;
import com.emc.storageos.model.usergroup.UserGroupUpdateParam;
import com.emc.storageos.model.usergroup.UserGroupCreateParam;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.security.Security;
import controllers.util.FlashException;
import controllers.util.ViprResourceController;
import models.datatable.UserGroupDataTable;
import models.datatable.UserGroupDataTable.UserGroupInfo;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.CollectionUtils;
import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.With;
import util.AuthnProviderUtils;
import util.MessagesUtils;
import util.StringOption;
import util.UserGroupUtils;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.emc.vipr.client.core.util.ResourceUtils.uris;

/**
 * Controller for User Group widget.
 */
@With(Common.class)
@Restrictions({ @Restrict("SECURITY_ADMIN"), @Restrict("TENANT_ADMIN") })
public class UserGroup extends ViprResourceController {
    protected static final String SAVED = "userGroup.saved";
    protected static final String DELETED = "userGroup.deleted";
    protected static final String FAILED = "userGroup.failed";
    protected static final String UNKNOWN = "userGroup.unknown";
    protected static final String MODEL_NAME = "userGroup";
    public static final String EXPECTED_GEO_VERSION = UserGroupService.getExpectedGeoVDCVersion();

    //
    // Add reference data so that they can be reference in html template
    //
    private static void addReferenceData() {
        List<StringOption> domains = Lists.newArrayList();
        for (AuthnProviderRestRep authProvider : AuthnProviderUtils.getAuthnProviders()) {
            if (!authProvider.getDisable()) {
                for(String domain : authProvider.getDomains()) {
                    StringOption domainOption = new StringOption(domain, StringOption.getDisplayValue(domain, "Domains"));
                    domains.add(domainOption);
                }
            }
        }
        renderArgs.put("domainsJson", domains);
    }

    /**
     * if it was not redirect from another page, clean flash
     *
     */
    public static void list() {
        renderArgs.put("dataTable", new UserGroupDataTable());
        render();
    }

    public static void listJson() {
        performListJson(UserGroupUtils.getUserGroups(), new JsonItemOperation());
    }

    @Restrictions({ @Restrict("SECURITY_ADMIN") })
    public static void create() {
        UserGroupForm userGroup = new UserGroupForm();
        // put all "initial create only" defaults here rather than field initializers
        edit(userGroup);
    }

    @Restrictions({ @Restrict("SECURITY_ADMIN") })
    private static void edit(UserGroupForm userGroup) {
        addReferenceData();
        render("@edit", userGroup);
    }

    @FlashException("list")
    @Restrictions({ @Restrict("SECURITY_ADMIN") })
    public static void edit(String id) {
        UserGroupRestRep userGroup = UserGroupUtils.getUserGroup(id);
        if (userGroup == null) {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            list();
        }

        edit(new UserGroupForm(userGroup));
    }

    @FlashException(keep=true)
    public static void save(UserGroupForm userGroup) {
        userGroup.validate("userGroup");
        if (Validation.hasErrors()) {
            Common.handleError();
        }

        userGroup.save();
        flash.success(MessagesUtils.get(SAVED, userGroup.name));
        list();
    }

    public static void delete(@As(",") String[] ids) {
        delete(uris(ids));
    }

    private static void delete(List<URI> ids) {
        performSuccessFail(ids, new DeleteOperation(), DELETED, FAILED);
        list();
    }

    public static class UserGroupForm {
        private static String LINE_BREAK = "\r\n";

        public String id;

        @Required
        @MaxSize(128)
        @MinSize(2)
        public String name;

        @Required
        public String domain;

        public List<AttributeMapping> attributes = Lists.newArrayList();

        public UserGroupForm() {
        }

        public UserGroupForm(UserGroupRestRep userGroupRep) {
            readFrom(userGroupRep);
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }

        public void readFrom(UserGroupRestRep userGroupRep) {
            this.id = stringId(userGroupRep);
            this.name = userGroupRep.getName();
            this.domain = userGroupRep.getDomain();

            if (!CollectionUtils.isEmpty(userGroupRep.getAttributes())) {
                for (UserAttributeParam userAttributeMapping : userGroupRep.getAttributes()) {
                    if (userAttributeMapping != null)  {
                        AttributeMapping mapping = new AttributeMapping();
                        mapping.key = userAttributeMapping.getKey();
                        mapping.values = StringUtils.join(userAttributeMapping.getValues(), "\n");
                        this.attributes.add(mapping);
                    }
                }
            }
        }

        public UserGroupRestRep save() {
            if (isNew()) {
                return create();
            } else {
                return update();
            }
        }

        private UserGroupRestRep create() {
            UserGroupCreateParam param = new UserGroupCreateParam();
            param.setLabel(this.name);
            param.setDomain(this.domain);
            for(AttributeMapping mapping : this.attributes){
                if (mapping != null) {
                    param.getAttributes().add(mapping.createUserAttributeParam());
                }
            }

            return UserGroupUtils.create(param);
        }

        private UserGroupRestRep update() {
            UserGroupUpdateParam param = new UserGroupUpdateParam();
            UserGroupRestRep userGroupRestRep = UserGroupUtils.getUserGroup(this.id);

            param.setLabel(userGroupRestRep.getName());
            param.setDomain(this.domain);

            Set<UserAttributeParam> oldAttributes = userGroupRestRep.getAttributes();
            Set<UserAttributeParam> newAttributes = new HashSet<UserAttributeParam>();
            for(AttributeMapping mapping : this.attributes){
                if (mapping != null) {
                    newAttributes.add(mapping.createUserAttributeParam());
                }
            }

            param.getAddAttributes().addAll(newAttributes);
            param.getAddAttributes().removeAll(oldAttributes);

            for (UserAttributeParam oldAttribute : oldAttributes) {
                param.getRemoveAttributes().add(oldAttribute.getKey());
            }

            for (UserAttributeParam newAttribute : newAttributes) {
                param.getRemoveAttributes().remove(newAttribute.getKey());
            }

            return UserGroupUtils.update(this.id, param);
        }

        public void validate(String fieldName) {
            Validation.valid(fieldName, this);

            Validation.required(fieldName + ".name", this.name);
            Validation.required(fieldName + ".domain", this.domain);

            if (this.name.contains("@")) {
                Validation.addError(fieldName + ".name", "userGroup.name.invalid");
            }

            validateAttributeEntries(fieldName);
        }

        public void validateAttributeEntries (String fieldName) {
            int attributeIndex = 0;
            for (AttributeMapping mapping : this.attributes) {
                if (mapping != null) {
                    if (StringUtils.isBlank(mapping.key)) {
                        Validation.addError(fieldName + ".attributes[" + attributeIndex + "].key", "userGroup.attributes.key.required");
                    }

                    if (StringUtils.isBlank(mapping.values)) {
                        Validation.addError(fieldName + ".attributes[" + attributeIndex + "].values", "userGroup.attributes.values.required");
                    }
                }
                attributeIndex++;
            }
        }

        public static class AttributeMapping {
            public String key;
            public String values;

            public UserAttributeParam createUserAttributeParam() {
                UserAttributeParam attributeParam = new UserAttributeParam();
                attributeParam.setKey(key);

                Set<String> valueList = Sets.newHashSet();

                if (!StringUtils.isBlank(this.values)) {
                    String[] values = this.values.split(LINE_BREAK);
                    for (String val : values) {
                        if (!StringUtils.isBlank(val.trim())) {
                            valueList.add(val.trim());
                        }
                    }
                }
                attributeParam.setValues(valueList);

                return attributeParam;
            }
        }
    }

    protected static class JsonItemOperation implements ResourceValueOperation<UserGroupInfo, UserGroupRestRep> {
        @Override
        public UserGroupInfo performOperation(UserGroupRestRep userGroupRestRep) throws Exception {
            return new UserGroupInfo(userGroupRestRep, Security.isSecurityAdmin());
        }
    }

    protected static class DeleteOperation implements ResourceIdOperation<Void> {
        @Override
        public Void performOperation(URI id) throws Exception {
            UserGroupUtils.delete(id);
            return null;
        }
    }
}
