#!/bin/sh
# UIM ESXi First Boot Configuration Script

# define variables to be used by the first boot script
HOSTNAME="__HOSTNAME_MARKER__"
HOSTIP="__HOSTIP_MARKER__"
NETMASK="__NETMASK_MARKER__"
GATEWAY="__GATEWAY_MARKER__"
DEFAULT_VLAN="__DEFAULT_VLAN_MARKER__"
DNS_PRIMARY_IP="__DNS_PRIMARY_IP_MARKER__"
DNS_SECONDARY_IP="__DNS_SECONDARY_IP_MARKER__"
#BOOT_VOLUME_LABEL="__BOOT_VOLUME_LABEL_MARKER__"
#ESX_MGMT_VMNIC_MACS="__ESX_MGMT_VMNIC_MACS_MARKER__"
NTP_SERVER_IP="__NTP_SERVER_MARKER__"
ENABLE_SSH="true"

# set ESXi locations for needed commands
CUT=/bin/cut
ECHO=/bin/echo
SED=sed
VIMCMD=/bin/vim-cmd
LOG=logger
LOG_OPT="-t vipr"
ESXCFG_DIR=/sbin
LN=ln
AWK=/bin/awk
VMWARE=/bin/vmware
ADVCFG=/sbin/esxcfg-advcfg

# wrapper to allow multiple execution of a command until successful or
# repeat limit is reached.
# param 1: the command
# param 2: repeat limit
# param 3: sleep interval (seconds)
COMMAND_WRAPPER()
{
  $LOG $LOG_OPT "Executing: \"$1\", up to $2 times, with $3 seconds interval"

  for i in `seq 1 $2`
  do
    $LOG $LOG_OPT "attempt $i"
    eval $1
    if [ $? -ne 0 ]; then
      $LOG $LOG_OPT "failed; sleep and try again"
      sleep $3
    else
      $LOG $LOG_OPT "successful execution"
      break
    fi
  done
}

$LOG $LOG_OPT "firstboot configuration starting"

# first dump variable values into the syslog for debugging
$LOG $LOG_OPT "HOSTNAME=${HOSTNAME}"
$LOG $LOG_OPT "HOSTIP=${HOSTIP}"
$LOG $LOG_OPT "NETMASK=${NETMASK}"
$LOG $LOG_OPT "GATEWAY=${GATEWAY}"
$LOG $LOG_OPT "DEFAULT_VLAN=${DEFAULT_VLAN}"
$LOG $LOG_OPT "DNS_PRIMARY_IP=${DNS_PRIMARY_IP}"
$LOG $LOG_OPT "DNS_SECONDARY_IP=${DNS_SECONDARY_IP}"
$LOG $LOG_OPT "NTP_SERVER_IP=${NTP_SERVER_IP}"
$LOG $LOG_OPT "ENABLE_SSH=${ENABLE_SSH}"
$LOG $LOG_OPT "CUSTOM_FIRST_BOOT=${CUSTOM_FIRST_BOOT}"
#$LOG $LOG_OPT "BOOT_VOLUME_LABEL=${BOOT_VOLUME_LABEL}"
#$LOG $LOG_OPT "ESX_MGMT_VMNIC_MACS=${ESX_MGMT_VMNIC_MACS}"

# detect the major ESX release we are dealing with
ESX_MAJOR_VERSION=`$VMWARE -v | $SED -e "s/^VMware ESXi\? \([0-9]\+\).*$/\1/"`

$LOG $LOG_OPT "setting advanced configuration options for ESX major version $ESX_MAJOR_VERSION"

# turn off DHCP and configure the IP if one was supplied
if [ -n "$HOSTIP" ]; then
    if [ -z "$NETMASK" ]; then
        NETMASK="255.255.255.0"
    fi
    $LOG $LOG_OPT "configuring static IP settings: ${HOSTIP}/${NETMASK}"
    COMMAND_WRAPPER "$VIMCMD hostsvc/net/vnic_set --ip-address=$HOSTIP --ip-subnet=$NETMASK --ip-dhcp=false vmk0" 5 5
fi

# set the default ipv4 gateway if supplied
if [ -n "$GATEWAY" ]; then
    $LOG $LOG_OPT "configuring default gateway: $GATEWAY"
    COMMAND_WRAPPER "$VIMCMD hostsvc/net/route_set --gateway=$GATEWAY" 5 5
fi

# configure the hostname if supplied
if [ -n "$HOSTNAME" ]; then
    BAREHOST=`$ECHO $HOSTNAME | $CUT -d . -f 1`
    DOMAIN=`$ECHO $HOSTNAME | $SED -e "s/^\([^\.]\+\)[\.]*\(.*\)$/\2/"`
    if [ -z "$DOMAIN" ]; then
        DOMAIN=localdomain
    fi
    $LOG $LOG_OPT "configuring host/domain: ${BAREHOST}/${DOMAIN}"
    COMMAND_WRAPPER "$VIMCMD hostsvc/net/dns_set --dhcp=false --hostname=$BAREHOST --domainname=$DOMAIN" 5 5
fi

# configure DNS resolution if supplied
NAMESERVERS=""
if [ -n "$DNS_PRIMARY_IP" ]; then
    NAMESERVERS=${DNS_PRIMARY_IP}
fi
if [ -n "$DNS_SECONDARY_IP" ]; then
    if [ -n "$NAMESERVERS" ]; then
        NAMESERVERS="${NAMESERVERS},${DNS_SECONDARY_IP}"
    else
        NAMESERVERS=${DNS_SECONDARY_IP}
    fi
fi
if [ -n "$NAMESERVERS" ]; then
    $LOG $LOG_OPT "configuring DNS resolution: $NAMESERVERS"
    COMMAND_WRAPPER "$VIMCMD hostsvc/net/dns_set --dhcp=false --ip-addresses=$NAMESERVERS" 5 5
fi

#Setting up NTP Server if provided.
if [ -n "$NTP_SERVER_IP" ]; then
    NTP_CONFIG_FILE=/etc/ntp.conf
    $LOG $LOG_OPT "Writing to file ntp.conf"
    echo "server $NTP_SERVER_IP" >> $NTP_CONFIG_FILE

    $LOG $LOG_OPT "Starting NTP Deamon"
    /etc/init.d/ntpd start

    $LOG $LOG_OPT "Adding NTP Deamon to startup"
    chkconfig ntpd on
fi

#enable SSH access
if [ -n "$ENABLE_SSH" ]; then
   $LOG $LOG_OPT "Permit root access via SSH"
   if [ $ESX_MAJOR_VERSION == 6 ]; then
        chkconfig SSH on
   elif [ $ESX_MAJOR_VERSION == 5 ]; then
        chkconfig SSH on
   elif [ $ESX_MAJOR_VERSION == 4 ]; then
        chkconfig TSM-SSH on
   fi
fi

# configure the default console vlan if supplied, we do this last to make sure the newly generated cert is available
if [ -n "$DEFAULT_VLAN" ]; then
    $LOG $LOG_OPT "Assigning VLAN ID to Management Network port group: ${DEFAULT_VLAN}"
    COMMAND_WRAPPER "$VIMCMD hostsvc/net/portgroup_set --portgroup-vlanid=$DEFAULT_VLAN vSwitch0 \"Management Network\"" 10 5
fi

# restart services
$LOG $LOG_OPT "restarting services"
/sbin/services.sh restart
