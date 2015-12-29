#!/bin/bash

DIR=/opt/storageos/bin
#. ${DIR}/restore-libs.sh 

echo "lby" > /tmp/lby
#trap clean_up EXIT
nohup ${DIR}/restore-internal.sh "$1" "$2" "$3" "$4"& 
exit 0
