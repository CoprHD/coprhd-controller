/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

public class ConfigProperty {
    public static final String NETWORK_VIRTUAL_IP = "network_vip";
    public static final String NETWORK_STANDALONE_IP = "network_standalone_ipaddr";

    public static final String ROOT_PASSWORD = "system_root_encpassword";
    public static final String PROXYUSER_PASSWORD = "system_proxyuser_encpassword";
    public static final String SVCUSER_PASSWORD = "system_svcuser_encpassword";
    public static final String SYSMONITOR_PASSWORD = "system_sysmonitor_encpassword";

    public static final String EXTRA_NODES = "system_datanode_ipaddrs";
    public static final String NAMESERVERS = "network_nameservers";
    public static final String NTPSERVERS = "network_ntpservers";

    public static final String SMTP_SERVER = "system_connectemc_smtp_server";
    public static final String SMTP_PORT = "system_connectemc_smtp_port";
    public static final String SMTP_ENABLE_TLS = "system_connectemc_smtp_enabletls";
    public static final String SMTP_AUTH_TYPE = "system_connectemc_smtp_authtype";
    public static final String SMTP_USERNAME = "system_connectemc_smtp_username";
    public static final String SMTP_PASSWORD = "system_connectemc_smtp_password";
    public static final String SMTP_FROM_ADDRESS = "system_connectemc_smtp_from";

    public static final String FTPS_SERVER = "system_connectemc_ftps_hostname";
    public static final String FTPS_PORT = "system_connectemc_ftps_port";
    public static final String FTPS_USERNAME = "system_connectemc_ftps_username";
    public static final String FTPS_PASSWORD = "system_connectemc_ftps_password";
    public static final String FTPS_FOLDER = "system_connectemc_ftps_fepfolder";

    public static final String CONNECTEMC_TRANSPORT = "system_connectemc_transport";
    public static final String CONNECTEMC_ENCRYPTION = "system_connectemc_encrypt";
    public static final String CONNECTEMC_TARGET_EMAIL = "system_connectemc_smtp_emcto";
    public static final String CONNECTEMC_NOTIFY_EMAIL = "system_connectemc_smtp_to";

    public static final String IMAGE_SERVER_ADDRESS = "image_server_address";
    public static final String IMAGE_SERVER_OS_NETWORK_ADDRESS = "image_server_os_network_ip";
    public static final String IMAGE_SERVER_USERNAME = "image_server_username";
    public static final String IMAGE_SERVER_PASSWORD = "image_server_encpassword";
    public static final String IMAGE_SERVER_TFTPBOOT = "image_server_tftpboot_directory";
}
