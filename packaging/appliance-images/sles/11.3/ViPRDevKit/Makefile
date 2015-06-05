RUN_DIR=`pwd`
NAME=ViPRDevKit
VERSION=2.5.0.0
JOB=1
CONF=${RUN_DIR}/${NAME}.conf
CONFIG_SH=${RUN_DIR}/config.sh
IMAGES_SH=${RUN_DIR}/images.sh
APPLIANCE_MANIFEST=${RUN_DIR}/${NAME}.manifest.appliance
CONTAINER_MANIFEST=${RUN_DIR}/${NAME}.manifest.container
COMMON_DIR=${RUN_DIR}/..
CREATE_APPLIANCE_DIR=/opt/ADG/createAppliance
CREATE_APPLIANCE_TOOL=/usr/bin/perl ${CREATE_APPLIANCE_DIR}/bin/createAppliance.pl
JOB_DIR=${CREATE_APPLIANCE_DIR}/jobs/${NAME}-${VERSION}.${JOB}
OUTPUT_DIR=/opt/downloads/images/${NAME}-${VERSION}.${JOB}
DEBUG=d

all: prepare createAll
appliance: prepare createAppliance
container: prepare createContainer
prepare: clean config 
config: ${CONFIG_SH} ${IMAGES_SH}

${CONFIG_SH}:
	cp ${COMMON_DIR}/configurations.sh ${CONFIG_SH}
	sed -i 's/^TYPE=".*".*/TYPE="config.sh"/' ${CONFIG_SH}

${IMAGES_SH}:
	cp ${COMMON_DIR}/configurations.sh ${IMAGES_SH}
	sed -i 's/^TYPE=".*".*/TYPE="images.sh"/' ${IMAGES_SH}
	
createAppliance:
	checkproc /sbin/udevd || /sbin/udevd --daemon
	export MAKE_MANIFEST=${APPLIANCE_MANIFEST}; ${CREATE_APPLIANCE_TOOL} -lc${DEBUG} ${CONF} ${JOB} -p appliance
createContainer:
	export MAKE_MANIFEST=${CONTAINER_MANIFEST}; ${CREATE_APPLIANCE_TOOL} -lc${DEBUG} ${CONF} ${JOB} -p container
createAll: createAppliance createContainer

bless:
	${CREATE_APPLIANCE_TOOL} -b ${CONF} ${JOB}

clean:
	rm -f ${RUN_DIR}/${NAME}.list
	rm -rf ${JOB_DIR}
	rm -f ${CONFIG_SH}
	rm -f ${IMAGES_SH}
destroy: clean
	rm -rf ${OUTPUT_DIR}

