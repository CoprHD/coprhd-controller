#!/bin/bash
#
# Copyright 2016 EMC Corporation
# All Rights Reserved
#

workspace=$( pwd )
cd /tmp/templates
tar -xzvf nginx-1.6.2.tar.gz
tar -xzvf v0.3.0.tar.gz
tar -xzvf v0.25.tar.gz
cd nginx-1.6.2
patch -p1 < ../nginx_upstream_check_module-0.3.0/check_1.5.12+.patch
./configure --add-module=../nginx_upstream_check_module-0.3.0 --add-module=../headers-more-nginx-module-0.25 --with-http_ssl_module --prefix=/usr --conf-path=/etc/nginx/nginx.conf
make
make install
cd $workspace

cat > /etc/init/ovf-network.conf << EOF
# ovf-network
#
# Network Configuration

description "ADG Pre Network configuration"

start on local-filesystems
stop on runlevel [!2345]

script
  exec /etc/init.d/ovf-network
end script
EOF

cat > /etc/init.d/ovf-network << EOF
#!/bin/sh
### BEGIN INIT INFO
# Provides:             ovf-network
# Required-Start:       \$local_fs
# Required-Stop:        
# Default-Start:        2 3 4 5
# Default-Stop:         0 1 6
# Short-Description:    ADG network configuration script
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
  hostname=\$( more /tmp/cdromOvfEnv/ovf-env.xml | grep network.hostname.SetupVM | grep -oP 'oe:value="\K[^"]*' )
  DOM=\$( more /tmp/cdromOvfEnv/ovf-env.xml | grep network.DOM.SetupVM | grep -oP 'oe:value="\K[^"]*' )
  ipv40=\$( more /tmp/cdromOvfEnv/ovf-env.xml | grep network.ipv40.SetupVM | grep -oP 'oe:value="\K[^"]*' )
  ipv4dns=\$( more /tmp/cdromOvfEnv/ovf-env.xml | grep network.ipv4dns.SetupVM | grep -oP 'oe:value="\K[^"]*' | tr ',' ' ' )
  ipv4gateway=\$( more /tmp/cdromOvfEnv/ovf-env.xml | grep network.ipv4gateway.SetupVM | grep -oP 'oe:value="\K[^"]*' )
  ipv4netmask0=\$( more /tmp/cdromOvfEnv/ovf-env.xml | grep network.ipv4netmask0.SetupVM | grep -oP 'oe:value="\K[^"]*' )
  vip=\$( more /tmp/cdromOvfEnv/ovf-env.xml | grep network.vip.SetupVM | grep -oP 'oe:value="\K[^"]*' )
  interface=\$( ip addr | grep BROADCAST,MULTICAST | head -n 1 | tail -n 1 | cut -d ':' -f 2 | tr -d ' ' )

  if [ ! -z "\$hostname" ]; then
    hostname "\$hostname"
  fi

  if [ ! -f /etc/ovfenv.properties ]; then
    echo "network_1_ipaddr6=::0" >> /etc/ovfenv.properties
    echo "network_1_ipaddr=\$ipv40" >> /etc/ovfenv.properties
    echo "network_gateway6=::0" >> /etc/ovfenv.properties
    echo "network_gateway=\$ipv4gateway" >> /etc/ovfenv.properties
    echo "network_netmask=\$ipv4netmask0" >> /etc/ovfenv.properties
    echo "network_prefix_length=64" >> /etc/ovfenv.properties
    echo "network_vip6=::0" >> /etc/ovfenv.properties
    echo "network_vip=\$vip" >> /etc/ovfenv.properties
    echo "node_count=1" >> /etc/ovfenv.properties
    echo "node_id=vipr1" >> /etc/ovfenv.properties
  fi

  if [ ! -f /etc/network/interfaces ]; then
    echo "# This file describes the network interfaces available on your system" >> /etc/network/interfaces
    echo "# and how to activate them. For more information, see interfaces(5)." >> /etc/network/interfaces
    echo "source /etc/network/interfaces.d/*" >> /etc/network/interfaces

    echo "" >> /etc/network/interfaces
    echo "# The loopback network interface" >> /etc/network/interfaces
    echo "auto lo" >> /etc/network/interfaces
    echo "iface lo inet loopback" >> /etc/network/interfaces

    echo "" >> /etc/network/interfaces
    echo "# The \$interface network interface" >> /etc/network/interfaces
    echo "auto \$interface" >> /etc/network/interfaces
    if [ -z "\$ipv40" ]; then
      echo "iface \$interface inet dhcp" >> /etc/network/interfaces
    elif [ "\$ipv40" = "0.0.0.0" ]; then
      echo "iface \$interface inet dhcp" >> /etc/network/interfaces
    else
      echo "iface \$interface inet static" >> /etc/network/interfaces
      echo "  address \$ipv40" >> /etc/network/interfaces
    fi
    if [ ! -z "\$ipv4netmask0" ]; then
      echo "  netmask \$ipv4netmask0" >> /etc/network/interfaces
    fi
    if [ ! -z "\$ipv4gateway" ]; then
      echo "  gateway \$ipv4gateway" >> /etc/network/interfaces
    fi
    if [ ! -z "\$DOM" ]; then
      echo "  dns-search \$DOM" >> /etc/network/interfaces
    fi
    if [ ! -z "\$ipv4netmask0" ]; then
      echo "  dns-nameservers \$ipv4dns" >> /etc/network/interfaces
    fi
  fi
}

mountCDROM
parseOVF
umountCDROM
EOF
chmod 755 /etc/init.d/ovf-network
update-rc.d ovf-network defaults
update-rc.d ovf-network enable
rm /etc/network/interfaces

groupadd -g 444 storageos
useradd -r -d /opt/storageos -c "StorageOS" -g 444 -u 444 -s /bin/bash storageos

update-alternatives --install /usr/bin/java java /usr/lib/jvm/java-1.8.0-openjdk-amd64/bin/java 1
update-alternatives --install /usr/bin/javac javac /usr/lib/jvm/java-1.8.0-openjdk-amd64/bin/javac 1

update-alternatives --set java /usr/lib/jvm/java-1.8.0-openjdk-amd64/bin/java
update-alternatives --set javac /usr/lib/jvm/java-1.8.0-openjdk-amd64/bin/javac

exit 0
