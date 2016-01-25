#!/bin/bash
#
# Copyright 2015-2016 EMC Corporation
# All Rights Reserved
#

#======================================
# Functions...
#--------------------------------------
test -f /.kconfig && . /.kconfig
test -f /.profile && . /.profile

#======================================
# Greeting...
#--------------------------------------
echo "Configure image: [$kiwi_iname]..."

#======================================
# Mount system filesystems
#--------------------------------------
baseMount

#======================================
# Setup baseproduct link
#--------------------------------------
suseSetupProduct

#======================================
# Add missing gpg keys to rpm
#--------------------------------------
suseImportBuildKey

#======================================
# Activate services
#--------------------------------------
suseInsertService sshd

#======================================
# Setup default target, multi-user
#--------------------------------------
baseSetRunlevel 3

#==========================================
# remove package docs
#------------------------------------------
rm -rf /usr/share/doc/packages/*
rm -rf /usr/share/doc/manual/*
rm -rf /opt/kde*

#======================================
# SuSEconfig
#--------------------------------------
suseConfig

#======================================
# CoprHDDevKit install
#--------------------------------------
bash /opt/ADG/conf/configure.sh installRepositories
bash /opt/ADG/conf/configure.sh installPackages
bash /opt/ADG/conf/configure.sh installJava
bash /opt/ADG/conf/configure.sh installNginx
bash /opt/ADG/conf/configure.sh installStorageOS
bash /opt/ADG/conf/configure.sh installNetwork
bash /opt/ADG/conf/configure.sh installXorg

#======================================
# CoprHDDevKit setup
#--------------------------------------
mkdir -p /workspace
chmod 1777 /workspace

cp -f /etc/inputrc /etc/inputrc-original
LineNo=$(grep -A 5 -n ".*Normal keypad and cursor of xterm" /etc/inputrc | grep history-search-backward | cut -d- -f1)
LineNo="$LineNo "$(grep -A 5 -n ".*Normal keypad and cursor of xterm" /etc/inputrc | grep set-mark | cut -d- -f1)
for ln in $LineNo; do sed -i "$ln s/.*/#&/" /etc/inputrc; done

mkdir -p /kiwi-hooks
## BEGIN:/kiwi-hooks/preCallInit.sh - first boot configuration
cat > /kiwi-hooks/preCallInit.sh <<EOF
#!/bin/bash
# ===
# FILE: /kiwi-hooks/preCallInit.sh
# ===

# include KIWI modules
/include

# cleanup temporary folders created by 'KIWILinuxRC.sh'
rm -fr /run/initramfs
rm -fr /Swap

mkswap /dev/system*/LVSwap
echo "\$( ls /dev/system*/LVSwap ) swap swap defaults 0 0" >> /etc/fstab
swapon -a

# turn off on screen console messages after first boot
dmesg --console-off

# cleanup kiwi-hooks folder
rm -fr /kiwi-hooks
EOF
chmod u+x /kiwi-hooks/preCallInit.sh
## END:/kiwi-hooks/preCallInit.sh

mkdir -p /etc/init.d
## BEGIN:/etc/init.d/boot.getCoprHDEnv - setup ovf properties on first boot
cat > /etc/init.d/boot.getCoprHDEnv <<EOF
#!/bin/sh
#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.
#
# /etc/init.d/boot.getCoprHDEnv
#
# Boot time script to populate configuration files using parameters
# provided in ovf-env.xml
#
### BEGIN INIT INFO
# Provides:          boot.getCoprHDEnv
# Required-Start:    boot.localfs
# Required-Stop:
# Should-Stop:
# Default-Start:     3 5
# Default-Stop:      0 1 2 6
# Description:       Generate CoprHD network config
### END INIT INFO
CDROM_DEVICE="/dev/sr0"
CDROM_MOUNT="/tmp/cdromOvfEnv"

export PATH=/bin:/usr/bin:/sbin:/usr/sbin

mountCDROM()
{
  mkdir -p \${CDROM_MOUNT}
  if [ ! -f /tmp/cdromOvfEnv/ovf-env.xml ]; then
    mount \${CDROM_DEVICE} \${CDROM_MOUNT}
  fi
}

umountCDROM()
{
  if [ -f /tmp/cdromOvfEnv/ovf-env.xml ]; then
    umount \${CDROM_MOUNT}
  fi
  rm -fr \${CDROM_MOUNT}
}

parseOVF()
{
  ipv40=\$( more /tmp/cdromOvfEnv/ovf-env.xml | grep network.ipv40.SetupVM | grep -oP 'oe:value="\K[^"]*' )
  ipv4dns=\$( more /tmp/cdromOvfEnv/ovf-env.xml | grep network.ipv4dns.SetupVM | grep -oP 'oe:value="\K[^"]*' )
  ipv4gateway=\$( more /tmp/cdromOvfEnv/ovf-env.xml | grep network.ipv4gateway.SetupVM | grep -oP 'oe:value="\K[^"]*' )
  ipv4netmask0=\$( more /tmp/cdromOvfEnv/ovf-env.xml | grep network.ipv4netmask0.SetupVM | grep -oP 'oe:value="\K[^"]*' )
  vip=\$( more /tmp/cdromOvfEnv/ovf-env.xml | grep network.vip.SetupVM | grep -oP 'oe:value="\K[^"]*' )
  cat > /etc/ovfenv.properties <<EOS
network_1_ipaddr6=::0
network_1_ipaddr=\$ipv40
network_gateway6=::0
network_gateway=\$ipv4gateway
network_netmask=\$ipv4netmask0
network_prefix_length=64
network_vip6=::0
network_vip=\$vip
node_count=1
node_id=vipr1
EOS
}

if [ ! -f /etc/ovfenv.properties ]; then
  mountCDROM
  parseOVF
  umountCDROM
fi
EOF
chmod u+x /etc/init.d/boot.getCoprHDEnv
insserv -d boot.getCoprHDEnv
## END:boot.getCoprHDEnv

#======================================
# Remove yast if not in use
#--------------------------------------
suseRemoveYaST

#======================================
# Umount kernel filesystems
#--------------------------------------
baseCleanMount

exit 0
