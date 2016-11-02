RUN_DIR=`pwd`
NAME=ViPRBase
VERSION=3.6.0.0
JOB=1
CONF=${RUN_DIR}/${NAME}.conf
CREATE_APPLIANCE_DIR=/opt/ADG/ICAF
CREATE_APPLIANCE_TOOL=/usr/bin/perl ${CREATE_APPLIANCE_DIR}/bin/icaf.pl
JOB_DIR=${CREATE_APPLIANCE_DIR}/jobs/${NAME}-${VERSION}.${JOB}
OUTPUT_DIR=/opt/downloads/images/${NAME}-${VERSION}.${JOB}
DEBUG=d

all: prepare appliance
appliance: prepare createAppliance
prepare: clean 

createAppliance:
	cat ${RUN_DIR}/../configurations.sh > config.sh
	cat ViPRBase_config.sh >> config.sh
	${CREATE_APPLIANCE_TOOL} -lc${DEBUG} ${CONF} ${JOB} -p appliance
	${CREATE_APPLIANCE_TOOL} ${CONF} ${JOB} -l -c -p appliance 

bless:
	${CREATE_APPLIANCE_TOOL} -b ${CONF} ${JOB}

clean:
	rm -rf config.sh ViPRBase.list
	rm -rf ${JOB_DIR}

destroy: clean
	rm -rf ${OUTPUT_DIR}

