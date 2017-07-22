RUN_DIR=`pwd`
NAME=ViPROpenStack
VERSION=1.3.0.0
JOB=1
CONF=${RUN_DIR}/${NAME}.conf
ICAF_DIR=/opt/ADG/ICAF
ICAF_TOOL=/usr/bin/perl ${ICAF_DIR}/bin/icaf.pl
JOB_DIR=${ICAF_DIR}/jobs/${NAME}-${VERSION}.${JOB}
OUTPUT_DIR=/opt/downloads/images/${NAME}-${VERSION}.${JOB}
DEBUG=-d

all: prepare appliance
appliance: prepare createImage
prepare: clean 

createImage:
	cat ${RUN_DIR}/configurations.sh > config.sh
	cat ViPROpenStack_config.sh >> config.sh
	udevd -d &
	${ICAF_TOOL} ${DEBUG} ${CONF} ${JOB} -l -c -p appliance

bless:
	${ICAF_TOOL} -b ${CONF} ${JOB}

clean:
##	git checkout ${RUN_DIR}/LISTS/${NAME}.list
	rm -rf config.sh
	rm -rf ${JOB_DIR}

destroy: clean
	rm -rf ${OUTPUT_DIR}

