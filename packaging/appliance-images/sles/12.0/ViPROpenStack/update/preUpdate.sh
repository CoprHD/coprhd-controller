#!/bin/bash

# pre Update - start
# Stop all services
echo "Stopping all services"
service openstack-cinder-volume stop
service openstack-cinder-scheduler stop
service openstack-cinder-api stop
