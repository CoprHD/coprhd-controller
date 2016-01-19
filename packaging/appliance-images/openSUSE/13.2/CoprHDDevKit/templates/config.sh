#!/bin/bash
#
# Copyright 2015 EMC Corporation
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
# Add zypper repos
#--------------------------------------
zypper addrepo --name repo-oss --no-gpgcheck --no-check http://download.opensuse.org/distribution/13.2/repo/oss repo-oss.repo
zypper addrepo --name devel-languages-python --no-gpgcheck --no-check http://download.opensuse.org/repositories/devel:/languages:/python/openSUSE_13.2 devel-languages-python.repo
zypper addrepo --name server-monitoring --no-gpgcheck --no-check http://download.opensuse.org/repositories/server:/monitoring/openSUSE_13.2 server-monitoring.repo
zypper addrepo --name home-seife-testing --no-gpgcheck --no-check http://download.opensuse.org/repositories/home:/seife:/testing/openSUSE_13.2 home-seife-testing.repo
zypper refresh

#======================================
# CoprHDDevKit setup
#--------------------------------------
mkdir -p /workspace
chmod 1777 /workspace

cp -f /etc/inputrc /etc/inputrc-original
LineNo=$(grep -A 5 -n ".*Normal keypad and cursor of xterm" /etc/inputrc | grep history-search-backward | cut -d- -f1)
LineNo="$LineNo "$(grep -A 5 -n ".*Normal keypad and cursor of xterm" /etc/inputrc | grep set-mark | cut -d- -f1)
for ln in $LineNo; do sed -i "$ln s/.*/#&/" /etc/inputrc; done

workspace=$( pwd )
mkdir -p /tmp/nginx
mv /nginx-1.6.2 /tmp/nginx/
mv /nginx_upstream_check_module-0.3.0 /tmp/nginx/
mv /headers-more-nginx-module-0.25 /tmp/nginx/
cd /tmp/nginx/nginx-1.6.2
patch -p1 < ../nginx_upstream_check_module-0.3.0/check_1.5.12+.patch
./configure --add-module=../nginx_upstream_check_module-0.3.0 --add-module=../headers-more-nginx-module-0.25 --with-http_ssl_module --prefix=/usr --conf-path=/etc/nginx/nginx.conf
make
make install
rm -fr /tmp/nginx
cd $workspace

groupadd storageos
useradd -d /opt/storageos -g storageos storageos
chown -R storageos:storageos /opt/storageos
chown -R storageos:storageos /data

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

## BEGIN:XORG - File to setup X-11 to run eclipse
cat > /etc/X11/xorg.conf <<EOF
Section "ServerLayout"
        Identifier     "X.org Configured"
        Screen      0  "Screen0" 0 0
        InputDevice    "Mouse0" "CorePointer"
        InputDevice    "Keyboard0" "CoreKeyboard"
EndSection

Section "Files"
        ModulePath   "/usr/lib64/xorg/modules"
        FontPath     "/usr/share/fonts/misc:unscaled"
        FontPath     "/usr/share/fonts/Type1/"
        FontPath     "/usr/share/fonts/100dpi:unscaled"
        FontPath     "/usr/share/fonts/75dpi:unscaled"
        FontPath     "/usr/share/fonts/ghostscript/"
        FontPath     "/usr/share/fonts/cyrillic:unscaled"
        FontPath     "/usr/share/fonts/misc/sgi:unscaled"
        FontPath     "/usr/share/fonts/truetype/"
        FontPath     "built-ins"
EndSection

Section "Module"
        Load  "glx"
EndSection

Section "InputDevice"
        Identifier  "Keyboard0"
        Driver      "kbd"
EndSection

Section "InputDevice"
        Identifier  "Mouse0"
        Driver      "mouse"
        Option      "Protocol" "auto"
        Option      "Device" "/dev/input/mice"
        Option      "ZAxisMapping" "4 5 6 7"
EndSection

Section "Monitor"
        Identifier   "Monitor0"
        VendorName   "Monitor Vendor"
        ModelName    "Monitor Model"
EndSection

Section "Device"
        Identifier  "Card0"
        Driver      "vmware"
        BusID       "PCI:0:15:0"
EndSection

Section "Screen"
        Identifier "Screen0"
        Device     "Card0"
        Monitor    "Monitor0"
        SubSection "Display"
                Viewport   0 0
                Depth     1
        EndSubSection
        SubSection "Display"
                Viewport   0 0
                Depth     4
        EndSubSection
        SubSection "Display"
                Viewport   0 0
                Depth     8
        EndSubSection
        SubSection "Display"
                Viewport   0 0
                Depth     15
        EndSubSection
        SubSection "Display"
                Viewport   0 0
                Depth     16
        EndSubSection
        SubSection "Display"
                Viewport   0 0
                Depth     24
        EndSubSection
EndSection
EOF
## END:XORG

#======================================
# Remove yast if not in use
#--------------------------------------
suseRemoveYaST

#======================================
# Umount kernel filesystems
#--------------------------------------
baseCleanMount

exit 0
