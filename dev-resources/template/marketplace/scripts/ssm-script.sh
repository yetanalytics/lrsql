#!/bin/bash

# The following script is for a client side machine to connect to the proxy
# target is the SSM Instance ID, in the template that's under 3_proxy.yml->SSMInstanceId
# host is the Proxy Endpoint, in the template that's under 3_proxy.yml->Endpoint
#
# This requires the aws cli ssm plugin to use, link for installation instructions:
# https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html
aws ssm start-session \
  --region "us-east-1" \
  --target "i-0ef078a6f09cf2a98" \
  --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters host="lrsql-ami-proxy-lrsql-proxy.proxy-cmyvzxmdapvz.us-east-1.rds.amazonaws.com",portNumber="5432",localPortNumber="5432"
