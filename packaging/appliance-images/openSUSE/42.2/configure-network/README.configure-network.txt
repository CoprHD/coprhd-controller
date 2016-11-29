The configure-network package setups the network configuration automatically from properties added on VSphere OVF deployments.
The following are the steps to follow to update the network with a fixed IP manually when the properties are not defined automatically:

# 01. Copy the file /opt/ADG/conf/ovf.properties.example to /opt/ADG/conf/ovf.properties
# 02. Update the example values with the network configuration desired
# 03. Run this command to update network properties: /opt/ADG/bin/setNetwork 0
# 04. Run this command to restart the network: service network restart
# 05. Verify if the network was assigned properly with the command: ifconfig

Note: to allow DHCP, edit configurations from '/etc/sysconfig/network/ifcfg-???' and set 'BOOTPROTO' to 'dhcp' and remove the 'IPADDR' line when running steps between 3 and 4.
