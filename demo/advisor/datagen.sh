#!/bin/bash
(set -o igncr) 2>/dev/null && set -o igncr; # this comment is required. this should be 2nd line of script.
# Above line makes this script ignore Windows line endings, to avoid this error without having to run dos2unix: 
#    $'\r': command not found
# As per http://stackoverflow.com/questions/14598753/running-bash-script-in-cygwin-on-windows-7

# Configure environment variables etc
source ../../scripts/env.sh

JAR_PATH="../../tno/tno/bin/DataGenTool.jar"
TEMP_DATA_FILE=temp_data.csv
TEMP_TERMS_FILE=temp_terms.txt

# # There should be exactly one argument
# if [ "$#" -ne 1 ]; then
#   echo "Please specify the path to the CSV file to upload (and nothing else). Exiting..."
#   exit 1
# fi

FIRST_DOC_NUMBER=$1
NUM_DOCS=$2
NUM_TERMS=$3

# Check that curl is installed
if [ ! hash curl 2>/dev/null ]; then 
  echo "Please install curl from Cygwin installer - found under the Net category. Exiting..." 
  exit 1
fi

# Generated CSV file goes to stdout. Any reporting goes to stderr
java -DfirstDocNumber=${FIRST_DOC_NUMBER} \
     -DnumDocs=${NUM_DOCS} \
     -DnumTerms=${NUM_TERMS} \
     -jar ${JAR_PATH} 1> ${TEMP_DATA_FILE} 2> ${TEMP_TERMS_FILE}
