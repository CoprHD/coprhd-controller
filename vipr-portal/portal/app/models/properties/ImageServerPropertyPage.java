/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package models.properties;

import java.util.Map;

public class ImageServerPropertyPage extends CustomPropertyPage {

    private Property imageServer;
    private Property networkServer;
    private Property username;
    private Property password;
    private Property tftpbootDirectory;

    public ImageServerPropertyPage(Map<String, Property> properties) {
        super("Compute Image Server");
        setRenderTemplate("imageServerPage.html");
        imageServer = addCustomProperty(properties, "image_server_address");
        networkServer = addCustomProperty(properties, "image_server_os_network_ip");
        username = addCustomProperty(properties, "image_server_username");
        password = addCustomProperty(properties, "image_server_encpassword");
        tftpbootDirectory = addCustomProperty(properties, "image_server_tftpboot_directory");
    }

    public Property getImageServer() {
        return imageServer;
    }

    public Property getNetworkServer() {
        return networkServer;
    }

    public Property getUsername() {
        return username;
    }
    
    public Property getPassword() {
        return password;
    }
    
    public Property getTftpbootDirectory() {
        return tftpbootDirectory;
    }
}
