#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#

cd /tmp
cp /opt/storageos/lib/storageos-datasvcipc.jar .
cp /opt/storageos/lib/storageos-common.jar .
cp ~/testIPC.sh .
sudo -u storageos sh testIPC.sh $1 $2 $3 $4 $5

