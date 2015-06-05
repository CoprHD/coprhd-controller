# Copyright 2015 EMC Corporation
# All Rights Reserved

#
# Cleans all runtime data
#

DIR=`dirname $0`
. ${DIR}/setEnv.sh

USER=`whoami`

echo "You may be asked for Sudo password..."
echo "Fixing ${DIR}/../data"
sudo chown -R $USER ${DIR}/../data
echo "Fixing ${DIR}/../logs"
sudo chown -R $USER ${DIR}/../logs
