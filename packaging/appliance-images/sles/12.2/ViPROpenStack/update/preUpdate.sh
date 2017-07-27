#!/bin/bash

# pre Update - start
# Stop all services
echo "Stopping all services"
service openstack-cinder-volume stop
service openstack-cinder-scheduler stop
service openstack-cinder-api stop

# To avoid version mismatch while transitioning
# from Mitaka to Newton image.
rpm -q python-ldap >/dev/null
if [ $? -eq 0 ]; then
  rpm -ev --nodeps python-ldap
  [ $? -eq 0 ] && echo "Successfully uninstalled python-ldap..."
fi

# Removed as the sendmail support from SUSE will cease to exist on Sept 2017 (https://www.suse.com/releasenotes/x86_64/SUSE-SLES/12/)
rpm -q sendmail >/dev/null
if [ $? -eq 0 ]; then
  rpm -ev --nodeps sendmail 2>&1 >/dev/null
  [ $? -eq 0 ] && echo "Successfully uninstalled sendmail..."
fi

rpm -q connectemc >/dev/null
if [ $? -eq 0 ]; then
  rpm -ev --nodeps connectemc 2>&1 >/dev/null
  [ $? -eq 0 ] && echo "Successfully uninstalled connectemc..."
fi

