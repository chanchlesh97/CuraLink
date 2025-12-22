#!/bin/bash

set -e
aws --endpoint-url=http://localhost:4566 cloudformation deploy \
 --stack-name cura-link \
 --template-file "./cdk.out/localstack.template.json"  --region us-east-1

aws --endpoint-url=http://localhost:4566 elbv2 describe-load-balancers \
 --query "LoadBalancers[0].DNSName" \
 --output text --region us-east-1