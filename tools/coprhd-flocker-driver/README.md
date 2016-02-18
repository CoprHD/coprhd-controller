COPRHD Flocker Plugin
======================
The plugin for COPRHD Flocker integration.

## Description
ClusterHQ/Flocker provides an efficient and easy way to connect persistent store with Docker containers. This project provides a plugin to provision resillient storage from COPRHD.

## COPRHD Flocker Intergration Block Diagram
![COPRHD Flocker Intergration Block Diagram Missing] 
(to be updated)

## Usage Instructions
To start the plugin on a node, a configuration file must exist on the node at /etc/flocker/agent.yml.
```bash
control-service: {hostname: ${ipaddress}, port: ${port}}
dataset: {backend: coprhd_flocker_plugin}
version: 1
dataset:
    backend: coprhd_flocker_plugin
    xms_ip: ${xms_ip}
    xms_user: ${xms_user}
    xms_password: ${xms_password}
    coprhd_hostname: ${coprhd_hostname}
    coprhd_port: ${coprhd_port}
    coprhd_username: ${coprhd_username}
    coprhd_password: ${coprhd_password}
    coprhd_tenant: ${coprhd_tenant}
    coprhd_project: ${coprhd_project}
    coprhd_varray: ${coprhd_varray}
    coprhd_cookiedir: ${coprhd_cookiedir}
    volume_backend_name: ${volume_backend_name}
    coprhd_vpool: ${coprhd_vpool}
```
A sample vagrant environment help 
Please refer to ClusterHQ/Flocker documentation for usage. A sample deployment and application can be found at to be updated

## Future
- Add Chap protocol support for iSCSI
- Add 


