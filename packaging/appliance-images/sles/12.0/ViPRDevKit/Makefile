RUN_DIR=`pwd`
NAME=ViPRDevKit
VERSION=4.0.0.0
JOB=1
CONF=${RUN_DIR}/${NAME}.conf
ICAF_DIR=/opt/ADG/ICAF
ICAF_TOOL=/usr/bin/perl ${ICAF_DIR}/bin/icaf.pl
JOB_DIR=${ICAF_DIR}/jobs/${NAME}-${VERSION}.${JOB}
OUTPUT_DIR=/opt/downloads/images/${NAME}-${VERSION}.${JOB}
DEBUG=d

all: prepare appliance 
appliance: prepare createAppliance
prepare: clean 

createAppliance:
	cat ${RUN_DIR}/../configurations.sh > config.sh
	cat ViPRDevKit_config.sh >> config.sh
	udevd -d &
	${ICAF_TOOL} ${CONF} ${JOB} -l -c -p appliance[vmx,tbz]
	
bless:
	${ICAF_TOOL} ${CONF} ${JOB} -b

clean:
	rm -rf config.sh ViPRDevKit.list
	rm -rf ${JOB_DIR}

destroy: clean
	rm -rf ${OUTPUT_DIR}

