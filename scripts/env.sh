#!/bin/bash
(set -o igncr) 2>/dev/null && set -o igncr; # this comment is required. this should be 2nd line of script.
# Above line makes this script ignore Windows line endings, to avoid this error without having to run dos2unix: 
#    $'\r': command not found
# As per http://stackoverflow.com/questions/14598753/running-bash-script-in-cygwin-on-windows-7

# Configurable properties
export JAVA_HOME="C:\Program Files\Java\jdk1.6.0_45"

##################################################################################
# Should not need to edit anything below here once stable

JAVA_BIN_PATH=${JAVA_HOME}/jre/bin
if [ ! -e "${JAVA_BIN_PATH}/java.exe" ]; then
  echo Cannot find java.exe at ${JAVA_BIN_PATH}. Check Java is installed there ...
  exit 1
fi

export PATH=${JAVA_BIN_PATH}:${PATH}

HOST=`hostname`
export SOLR_SERVER_HOSTNAME=
export SOLR_SERVER_PORT=
export DOCUMENT_SOURCE=
if [[ "${HOST}" == "TNO-Scale-Test1" ]]; then
  SOLR_SERVER_HOSTNAME=localhost
  SOLR_SERVER_PORT=":8983"
elif [[ "${HOST}" == "TNO-Scale-Test2" ]]; then
#  SOLR_SERVER_HOSTNAME=tno-scale-test2.cloudapp.net
  SOLR_SERVER_HOSTNAME=localhost
  SOLR_SERVER_PORT=":8983"
  DOCUMENT_SOURCE=/cygdrive/f/PerfHourly_200000_500
elif [[ "${HOST}" == "TNO-Scale-Test3" ]]; then
  SOLR_SERVER_HOSTNAME=localhost
  SOLR_SERVER_PORT=":8983"
elif [[ "${HOST}" == "TNO-Scale-Test4" ]]; then
#  SOLR_SERVER_HOSTNAME=tno-scale-test4.cloudapp.net
  SOLR_SERVER_HOSTNAME=localhost
  SOLR_SERVER_PORT=":8983"
  DOCUMENT_SOURCE=/cygdrive/f/PerfHourly_200000_500
elif [[ "${HOST}" == "TNO-Scale-Test5" ]]; then
  SOLR_SERVER_HOSTNAME=localhost
  SOLR_SERVER_PORT=":8983"
elif [[ "${HOST}" == "TNO-Scale-Test6" ]]; then
  # hostname 5a rather than 5 due to a DNS issue during provisioning.
#  SOLR_SERVER_HOSTNAME=tno-scale-test5a.cloudapp.net
  SOLR_SERVER_HOSTNAME=localhost
  SOLR_SERVER_PORT=":8983"
  DOCUMENT_SOURCE=/cygdrive/f/PerfHourly_200000_500
else
  SOLR_SERVER_HOSTNAME=localhost
  DOCUMENT_SOURCE=//jcurrey/data_gen/PerfHourly_200000_500
fi
#echo SOLR_SERVER_HOSTNAME=${SOLR_SERVER_HOSTNAME}
