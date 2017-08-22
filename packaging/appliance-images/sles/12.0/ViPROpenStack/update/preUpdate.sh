#!/bin/bash

# pre Update - start
# Stop all services
echo "Stopping all services"
service openstack-cinder-volume stop
service openstack-cinder-scheduler stop
service openstack-cinder-api stop

# To avoid version mismatch while transitioning
# from Mitaka to Newton image.
rpm -ev --nodeps python-ldap
