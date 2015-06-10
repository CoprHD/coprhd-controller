#
# Converts a remote version of StorageOS to run locally
#
GC_LOGGING=true

DIR=`dirname ${0}`
. ${DIR}/setEnv.sh

export LANG=C

_setHeapSize() {
    sed -i .bak "s,\-Xmx.*m ,-Xmx$2 ,g" $NILE_HOME/opt/nile/bin/$1
    if [ -f "$NILE_HOME/opt/nile/bin/$1-debug" ] ; then
        sed -i .bak "s,\-Xmx.*m ,-Xmx$2 ,g" $NILE_HOME/opt/nile/bin/$1-debug
    fi
}

echo Converting ${NILE_HOME}/opt/nile/bin
for filename in ${NILE_HOME}/opt/nile/bin/*; do
	if [ -f "${filename}" ] ; then
		sed -i .bak "s,/var/run/,${RUNTIME_ROOT}/run/,g" $filename
		sed -i .bak "s,/opt/nile,${NILE_HOME}/opt/nile,g" $filename
		sed -i .bak "s,JAVA_HOME=.*,JAVA_HOME=${JAVA_HOME},g" $filename
	fi
done

#echo Overriding Java HeapSizes
#_setHeapSize apisvc 128m
#_setHeapSize authsvc 128m

if ! $GC_LOGGING; then
    echo Turning off GC logging
    for filename in ${NILE_HOME}/opt/nile/bin/*; do
    	if [ -f "${filename}" ] ; then

    		sed -i .bak "s,-XX:.* , ,g" $filename
    	fi
    done
fi

rm ${NILE_HOME}/opt/nile/bin/*.bak
echo Converting ${NILE_HOME}/opt/nile/conf
for filename in ${NILE_HOME}/opt/nile/conf/*; do
echo "Converting $filename"
	if [ -f "${filename}" ] ; then
		sed -i .bak "s,/var/run/,${RUNTIME_ROOT}/run/,g" $filename
		sed -i .bak "s,/opt/,${NILE_HOME}/,g" $filename
		sed -i .bak "s,${NILE_HOME}/opt/nile/logs,${RUNTIME_ROOT}/logs,g" $filename
		sed -i .bak "s,/data/,${RUNTIME_ROOT}/data/,g" $filename
		sed -i .bak "s,\${product.home}/logs,${RUNTIME_ROOT}/logs,g" $filename
	fi
done

echo
echo "** Changing Fabric API Port number to 9090"
sed -i .bak "s,8080,9090,g" ${NILE_HOME}/opt/nile/conf/fabricapi-conf.xml

rm ${NILE_HOME}/opt/nile/conf/*.bak

if [ ! -e ${RUNTIME_ROOT}/run ]; then
    echo
    echo "** Creating runtime directories"
    mkdir ${RUNTIME_ROOT}/run
fi



