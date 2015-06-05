/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package models.properties;

import java.util.Map;

public class UpgradePropertyPage extends DefaultPropertyPage {
    private Property repositoryUrl;
    private Property proxy;
    private Property username;
    private Property password;

    public UpgradePropertyPage(Map<String, Property> properties) {
        super("Upgrade");
        repositoryUrl = addProperty(properties.remove("system_update_repo"));
        proxy = addProperty(properties.remove("system_update_proxy"));
        username = addProperty(properties.remove("system_update_username"));
        password = addProperty(properties.remove("system_update_password"));
        if (password != null) {
            password.setPasswordField(true);
        }
    }

    public Property getRepositoryUrl() {
        return repositoryUrl;
    }

    public Property getProxy() {
        return proxy;
    }

    public Property getUsername() {
        return username;
    }

    public Property getPassword() {
        return password;
    }
}
