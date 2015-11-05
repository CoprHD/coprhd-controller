RUN_DIR=`pwd`
NAME=ViPRDevKit
VERSION=2.4.1.0
JOB=1
CONF=${RUN_DIR}/${NAME}.conf
CREATE_APPLIANCE_DIR=/opt/ADG/createAppliance
CREATE_APPLIANCE_TOOL=/usr/bin/perl ${CREATE_APPLIANCE_DIR}/bin/createAppliance.pl
JOB_DIR=${CREATE_APPLIANCE_DIR}/jobs/${NAME}-${VERSION}.${JOB}
OUTPUT_DIR=/opt/downloads/images/${NAME}-${VERSION}.${JOB}
DEBUG=d

all: prepare appliance 
appliance: prepare createAppliance
prepare: clean 

createAppliance:
	cat ${RUN_DIR}/../configurations.sh > config.sh
	cat ViPRDevKit_config.sh >> config.sh
	${CREATE_APPLIANCE_TOOL} -lc${DEBUG} ${CONF} ${JOB} -p appliance[vmx,tbz]
	
bless:
	${CREATE_APPLIANCE_TOOL} -b ${CONF} ${JOB}

clean:
	git checkout ${RUN_DIR}/LISTS/${NAME}.list
	rm -rf config.sh
	rm -rf ${JOB_DIR}

destroy: clean
	rm -rf ${OUTPUT_DIR}

