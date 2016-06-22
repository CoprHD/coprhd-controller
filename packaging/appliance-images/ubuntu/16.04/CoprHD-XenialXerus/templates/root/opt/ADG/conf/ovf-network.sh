#!/bin/sh
CDROM_DEVICE="/dev/sr0"
CDROM_MOUNT="/tmp/cdromOvfEnv"

export PATH=/bin:/usr/bin:/sbin:/usr/sbin

mountCDROM()
{
  mkdir -p ${CDROM_MOUNT}
  if [ ! -f /tmp/cdromOvfEnv/ovf-env.xml ]; then
    mount ${CDROM_DEVICE} ${CDROM_MOUNT}
  fi
}

umountCDROM()
{
  if [ -f /tmp/cdromOvfEnv/ovf-env.xml ]; then
    umount ${CDROM_MOUNT}
  fi
  rm -fr ${CDROM_MOUNT}
}

parseOVF()
{
  hostname=$( more /tmp/cdromOvfEnv/ovf-env.xml | grep network.hostname.SetupVM | grep -oP 'oe:value="\K[^"]*' )
  DOM=$( more /tmp/cdromOvfEnv/ovf-env.xml | grep network.DOM.SetupVM | grep -oP 'oe:value="\K[^"]*' )
  ipv40=$( more /tmp/cdromOvfEnv/ovf-env.xml | grep network.ipv40.SetupVM | grep -oP 'oe:value="\K[^"]*' )
  ipv4dns=$( more /tmp/cdromOvfEnv/ovf-env.xml | grep network.ipv4dns.SetupVM | grep -oP 'oe:value="\K[^"]*' | tr ',' ' ' )
  ipv4gateway=$( more /tmp/cdromOvfEnv/ovf-env.xml | grep network.ipv4gateway.SetupVM | grep -oP 'oe:value="\K[^"]*' )
  ipv4netmask0=$( more /tmp/cdromOvfEnv/ovf-env.xml | grep network.ipv4netmask0.SetupVM | grep -oP 'oe:value="\K[^"]*' )
  vip=$( more /tmp/cdromOvfEnv/ovf-env.xml | grep network.vip.SetupVM | grep -oP 'oe:value="\K[^"]*' )
  interface=$( ip addr | grep BROADCAST,MULTICAST | head -n 1 | tail -n 1 | cut -d ':' -f 2 | tr -d ' ' )

  if [ ! -z "$hostname" ]; then
    echo "$hostname" > /etc/hostname
    hostname -b -F /etc/hostname
    echo "127.0.0.1 $hostname" >> /etc/hosts
  fi

  if [ ! -f /etc/ovfenv.properties ]; then
    echo "network_1_ipaddr6=::0" >> /etc/ovfenv.properties
    echo "network_1_ipaddr=$ipv40" >> /etc/ovfenv.properties
    echo "network_gateway6=::0" >> /etc/ovfenv.properties
    echo "network_gateway=$ipv4gateway" >> /etc/ovfenv.properties
    echo "network_netmask=$ipv4netmask0" >> /etc/ovfenv.properties
    echo "network_prefix_length=64" >> /etc/ovfenv.properties
    echo "network_vip6=::0" >> /etc/ovfenv.properties
    echo "network_vip=$vip" >> /etc/ovfenv.properties
    echo "node_count=1" >> /etc/ovfenv.properties
    echo "node_id=vipr1" >> /etc/ovfenv.properties
    chown storageos:storageos /etc/ovfenv.properties
  fi

  if [ ! -f /etc/network/interfaces.d/$interface ]; then
    echo "# The $interface network interface" >> /etc/network/interfaces.d/$interface
    echo "auto $interface" >> /etc/network/interfaces.d/$interface
    if [ -z "$ipv40" ]; then
      echo "iface $interface inet dhcp" >> /etc/network/interfaces.d/$interface
    elif [ "$ipv40" = "0.0.0.0" ]; then
      echo "iface $interface inet dhcp" >> /etc/network/interfaces.d/$interface
    else
      echo "iface $interface inet static" >> /etc/network/interfaces.d/$interface
      echo "  address $ipv40" >> /etc/network/interfaces.d/$interface
    fi
    if [ ! -z "$ipv4netmask0" ]; then
      echo "  netmask $ipv4netmask0" >> /etc/network/interfaces.d/$interface
    fi
    if [ ! -z "$ipv4gateway" ]; then
      echo "  gateway $ipv4gateway" >> /etc/network/interfaces.d/$interface
    fi
    if [ ! -z "$DOM" ]; then
      echo "  dns-search $DOM" >> /etc/network/interfaces.d/$interface
    fi
    if [ ! -z "$ipv4netmask0" ]; then
      echo "  dns-nameservers $ipv4dns" >> /etc/network/interfaces.d/$interface
    fi
    ifup --allow auto $interface
  fi
}

if [ ! -f /etc/ovfenv.properties ]; then
  mountCDROM
  if [ -f /tmp/cdromOvfEnv/ovf-env.xml ]; then
    parseOVF
    bash /opt/ADG/conf/configure.sh enableStorageOS
  else
    interface=$( ip addr | grep BROADCAST,MULTICAST | head -n 1 | tail -n 1 | cut -d ':' -f 2 | tr -d ' ' )
    if [ ! -f /etc/network/interfaces.d/$interface ]; then
      echo "# The $interface network interface" >> /etc/network/interfaces.d/$interface
      echo "auto $interface" >> /etc/network/interfaces.d/$interface
      echo "iface $interface inet dhcp" >> /etc/network/interfaces.d/$interface

      ifup --allow auto $interface
    fi
  fi
  umountCDROM
fi
