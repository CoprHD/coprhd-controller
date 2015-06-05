#!/bin/sh

# Copyright 2015 EMC Corporation
# All Rights Reserved

HOSTNAME="__HOSTNAME_MARKER__"
HOSTIP="__HOSTIP_MARKER__"
NETMASK="__NETMASK_MARKER__"
GATEWAY="__GATEWAY_MARKER__"
DNS_PRIMARY_IP="__DNS_PRIMARY_IP_MARKER__"
DNS_SECONDARY_IP="__DNS_SECONDARY_IP_MARKER__"
#MGMT_VNIC_MACS="__MGMT_VNIC_MACS_MARKER__"
NTP_SERVER_IP="__NTP_SERVER_MARKER__"
ENABLE_SSH="true"

LOG_TO_FILE="/var/log/osagent-fb.log"

LOG()
{
    echo $1 >> $LOG_TO_FILE
}

LOG "firstboot configuration starting"

# first dump variable values into the log for debugging
LOG "HOSTNAME=${HOSTNAME}"
LOG "HOSTIP=${HOSTIP}"
LOG "NETMASK=${NETMASK}"
LOG "GATEWAY=${GATEWAY}"
LOG "DNS_PRIMARY_IP=${DNS_PRIMARY_IP}"
LOG "DNS_SECONDARY_IP=${DNS_SECONDARY_IP}"
#LOG "MGMT_VNIC_MACS=${MGMT_VNIC_MACS}"
LOG "NTP_SERVER_IP=${NTP_SERVER_IP}"
LOG "ENABLE_SSH=${ENABLE_SSH}"
LOG "CUSTOM_FIRST_BOOT=${CUSTOM_FIRST_BOOT}"

# setup eth0
ETH="eth0"
LOG "Setting up interface $ETH"
filename="/etc/sysconfig/network-scripts/ifcfg-$ETH"
echo "DEVICE=$ETH" > $filename
# echo "HWADDR=$MAC" >> $filename
echo "TYPE=Ethernet" >> $filename
echo "ONBOOT=yes" >> $filename
if [ -n "$HOSTIP" ]; then
    LOG "Configuring with static IP"
    echo "BOOTPROTO=static" >> $filename
    echo "IPADDR=$HOSTIP" >> $filename
    echo "GATEWAY=$GATEWAY" >> $filename
    echo "NETMASK=$NETMASK" >> $filename
    echo "IPV6INIT=no" >> $filename
    echo "MTU=1500" >> $filename
else
    LOG "Configuring with dhcp"
    echo "BOOTPROTO=dhcp" >> $filename
fi

#setting host name
if [ -n "$HOSTNAME" ]; then
    LOG "Setting hostname to $HOSTNAME"
    echo "NETWORKING=yes" > /etc/sysconfig/network
    echo "HOSTNAME=$HOSTNAME" >> /etc/sysconfig/network
fi

#setup DNS
if [ -n "$DNS_PRIMARY_IP" ]; then
    LOG "Adding DNS server $DNS_PRIMARY_IP"
    echo "nameserver $DNS_PRIMARY_IP" >> /etc/resolv.conf
fi
if [ -n "$DNS_SECONDARY_IP" ]; then
    LOG "Adding DNS server $DNS_SECONDARY_IP"
    echo "nameserver $DNS_SECONDARY_IP" >> /etc/resolv.conf
fi

#Setting up NTP Server if provided
if [ -n "$NTP_SERVER_IP" ] && [ -s "/etc/ntp.conf" ]; then
    NTP_CONFIG_FILE=/etc/ntp.conf
    LOG "Writing to file ntp.conf"
    echo "server $NTP_SERVER_IP" >> $NTP_CONFIG_FILE

    LOG "Starting NTP Deamon"
    /etc/init.d/ntpd start

    LOG "Adding NTP Deamon to startup"
    chkconfig ntpd on
fi

#enable SSH access
if [ -n "$ENABLE_SSH" ]; then
    LOG "Enable SSH"
    service sshd start
else
    LOG "Disable SSH"
    service sshd stop
fi

LOG "firstboot configuration complete"

# do this last
service network restart


