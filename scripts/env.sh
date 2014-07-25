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
if [[ "${HOST}" == "TNO-Scale-Test1" ]]; then
  SOLR_SERVER_HOSTNAME=localhost
elif [[ "${HOST}" == "TNO-Scale-Test2" ]]; then
  SOLR_SERVER_HOSTNAME=TNO-Scale-Test1
else
  SOLR_SERVER_HOSTNAME=localhost
fi
