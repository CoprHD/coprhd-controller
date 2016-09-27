RUN_DIR=`pwd`
NAME=ViPRBase
VERSION=3.5.0.0
JOB=1
CONF=${RUN_DIR}/${NAME}.conf
CREATE_APPLIANCE_DIR=/opt/ADG/createAppliance
CREATE_APPLIANCE_TOOL=/usr/bin/perl ${CREATE_APPLIANCE_DIR}/bin/createAppliance.pl
JOB_DIR=${CREATE_APPLIANCE_DIR}/jobs/${NAME}-${VERSION}.${JOB}
OUTPUT_DIR=/opt/downloads/images/${NAME}-${VERSION}.${JOB}
SIMULATORS_BRANCH=master
DEBUG=d

all: prepare appliance
appliance: prepare copySimulatorCode createAppliance
prepare: clean 

createAppliance:
	cat ${RUN_DIR}/../configurations.sh > config.sh
	cat ViPRBase_config.sh >> config.sh
	${CREATE_APPLIANCE_TOOL} -lc${DEBUG} ${CONF} ${JOB} -p appliance

copySimulatorCode:
	rm -rf root/aio-simulators
	mkdir -p root/aio-simulators
	git clone https://review.coprhd.org/scm/ce/aio-simulators.git -b ${SIMULATORS_BRANCH}
	cp -pr aio-simulators/openSUSE/13.2/templates/root/AIO_scripts root/aio-simulators/.
	rm -rf aio-simulators
	
bless:
	${CREATE_APPLIANCE_TOOL} -b ${CONF} ${JOB}

clean:
	rm -rf config.sh ViPRBase.list root/aio-simulators
	rm -rf ${JOB_DIR}

destroy: clean
	rm -rf ${OUTPUT_DIR}

