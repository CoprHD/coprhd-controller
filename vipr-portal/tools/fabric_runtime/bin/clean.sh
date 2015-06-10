#
# Cleans all runtime data
#

DIR=`dirname $0`
. ${DIR}/setEnv.sh

DATA_DIR=${DIR}/../data
LOGS_DIR=${DIR}/../logs

echo "Removing ${DIR}/../data/*"
rm -rf ${DATA_DIR}/*

echo "Removing ${DIR}/../logs/*"
rm -rf ${LOGS_DIR}/*

echo "Removing ${NILE_HOME}/opt/nile/logs/*"
rm -rf $NILE_HOME/opt/nile/logs/*
