#
# Cleans all runtime data
#

DIR=`dirname $0`
. ${DIR}/setEnv.sh

DATA_DIR=${DIR}/../data
LOGS_DIR=${DIR}/../logs

echo "You may be asked for Sudo password..."
echo "Removing ${DIR}/../data/*"
sudo rm -rf ${DATA_DIR}/*

echo "Removing ${DIR}/../logs/*"
sudo rm -rf ${LOGS_DIR}/*

echo "Removing ${INSTALL_HOME}/storageos/logs/*"
sudo rm -rf $INSTALL_HOME/storageos/logs/*
