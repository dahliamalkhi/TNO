@echo off

set SOLR_DEPLOYMENT_PATH=solr_deployment

echo Posting XML documents...
pushd %SOLR_DEPLOYMENT_PATH% && (java -jar post.jar exampledocs\*.xml & popd)
