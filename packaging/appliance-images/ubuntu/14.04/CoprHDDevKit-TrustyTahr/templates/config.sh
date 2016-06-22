#!/bin/bash
#
# Copyright 2016 EMC Corporation
# All Rights Reserved
#

bash /opt/ADG/conf/configure.sh installNetwork
bash /opt/ADG/conf/configure.sh installNginx
bash /opt/ADG/conf/configure.sh installJava 8
bash /opt/ADG/conf/configure.sh installSystem
bash /opt/ADG/conf/configure.sh installStorageOS
