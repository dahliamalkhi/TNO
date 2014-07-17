#!/bin/bash
(set -o igncr) 2>/dev/null && set -o igncr; # this comment is required. this should be 2nd line of script.
# Above line makes this script ignore Windows line endings, to avoid this error without having to run dos2unix: 
#    $'\r': command not found
# As per http://stackoverflow.com/questions/14598753/running-bash-script-in-cygwin-on-windows-7

SOLR_DEPLOYMENT_PATH=solr_deployment

echo Posting XML documents...
( pushd ${SOLR_DEPLOYMENT_PATH} && java -jar post.jar exampledocs/*.xml )
