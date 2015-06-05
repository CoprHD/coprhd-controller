# Copyright 2015 EMC Corporation
# All Rights Reserved

#
# Cleans all runtime data
#

DIR=`dirname $0`
. ${DIR}/setEnv.sh

USER=`whoami`

echo "Fixing ${DIR}/../data"
chown -R $USER ${DIR}/../data
echo "Fixing ${DIR}/../logs"
chown -R $USER ${DIR}/../logs
