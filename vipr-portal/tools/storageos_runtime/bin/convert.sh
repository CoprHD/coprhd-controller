# Copyright 2015 EMC Corporation
# All Rights Reserved

#
# Converts a remote version of StorageOS to run locally
#
GC_LOGGING=true

DIR=`dirname ${0}`
. ${DIR}/setEnv.sh

export LANG=C

_setHeapSize() {
    sed -i .bak "s,\-Xmx.*m ,-Xmx$2 ,g" $INSTALL_HOME/storageos/bin/$1
    if [ -f "$INSTALL_HOME/storageos/bin/$1-debug" ] ; then
        sed -i .bak "s,\-Xmx.*m ,-Xmx$2 ,g" $INSTALL_HOME/storageos/bin/$1-debug
    fi
}

echo Converting ${INSTALL_HOME}/storageos/bin
for filename in ${INSTALL_HOME}/storageos/bin/*; do
	if [ -f "${filename}" ] ; then

		sed -i .bak "s,/var/run/,${RUNTIME_ROOT}/run/,g" $filename
		sed -i .bak "s,/opt/,${INSTALL_HOME}/,g" $filename 
		sed -i .bak "s,JAVA_HOME=.*,JAVA_HOME=${JAVA_HOME},g" $filename
	fi
done

echo Overriding Java HeapSizes
_setHeapSize apisvc 128m
_setHeapSize authsvc 128m
_setHeapSize controllersvc 512m
_setHeapSize coordinatorsvc 512m
_setHeapSize dbsvc 512m
_setHeapSize syssvc 128m
_setHeapSize vasasvc 512m

if ! $GC_LOGGING; then
    echo Turning off GC logging
    for filename in ${INSTALL_HOME}/storageos/bin/*; do
    	if [ -f "${filename}" ] ; then

    		sed -i .bak "s,-XX:.* , ,g" $filename
    	fi
    done
fi

ECLIPSE_SA_MODEL_CLASSPATH=$RUNTIME_ROOT/../../com.emc.sa.model/bin

# Attempt to automatically find the classpath (on Eclipse this should be the out directory)
if [ -z "$SA_MODEL_CLASSPATH" ] && [ -e $ECLIPSE_SA_MODEL_CLASSPATH ]; then
    echo Automatically found Eclipse SA_MODEL_PATH, setting to $ECLIPSE_SA_MODEL_CLASSPATH
    SA_MODEL_CLASSPATH=$ECLIPSE_SA_MODEL_CLASSPATH
fi

if [ ! -z "$SA_MODEL_CLASSPATH" ]; then
    echo Adding Model Classpath to DB Service
    sed -i .bak "s,export CLASSPATH=\",export CLASSPATH=\"$SA_MODEL_CLASSPATH:," $INSTALL_HOME/storageos/bin/dbsvc
    sed -i .bak "s,export CLASSPATH=\",export CLASSPATH=\"$SA_MODEL_CLASSPATH:," $INSTALL_HOME/storageos/bin/dbsvc-debug
fi

rm ${INSTALL_HOME}/storageos/bin/*.bak

echo Converting ${INSTALL_HOME}/storageos/conf
for filename in ${INSTALL_HOME}/storageos/conf/*; do
	if [ -f "${filename}" ] ; then
		sed -i .bak "s,/var/run/,${RUNTIME_ROOT}/run/,g" $filename
		sed -i .bak "s,/opt/,${INSTALL_HOME}/,g" $filename
		sed -i .bak "s,${INSTALL_HOME}/storageos/logs,${RUNTIME_ROOT}/logs,g" $filename
		sed -i .bak "s,/data/,${RUNTIME_ROOT}/data/,g" $filename
		sed -i .bak "s,\${product.home}/logs,${RUNTIME_ROOT}/logs,g" $filename
	fi
done
sed -i .bak 's,name="host" value=".*",name="host" value="localhost",g' ${INSTALL_HOME}/storageos/conf/db-jmx-var.xml

echo Knocking Out SysSvc::StatService
sed -i .bak 's,<property name="pids"  value="-1" />,,g' "${INSTALL_HOME}/storageos/conf/sys-conf.xml"

sed -i .bak "s,/data/,${RUNTIME_ROOT}/data/,g" $filename

if [ ! -z "$SA_MODEL_CLASSPATH" ]; then
    echo Fixing sa model package in DB Service
    sed -i .bak "s,com.emc.storageos.sa.model,com.emc.sa.model,g" "$INSTALL_HOME/storageos/conf/db-conf.xml"
fi


rm ${INSTALL_HOME}/storageos/conf/*.bak

echo Deleting storageos-samodels.jar
rm ${INSTALL_HOME}/storageos/lib/storageos-samodel.jar
rm ${INSTALL_HOME}/storageos/lib/storageos-samodel-coverage.jar


