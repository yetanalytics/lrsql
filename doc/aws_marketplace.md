[<- Back to Index](index)

# Marketplace Deployment


### Overview

Many of the same steps from [AWS Deployment](aws.md) apply here. Most steps for preparation are the same, However the Datase Passwords in Systems Manager step is not needed. Step 2, Networking, is also the same. Make sure to deploy the VPC template. Where things mainly differ is with the 1_secret.yml template.


### Step 1: Secrets

The template `secret.yml` generates the passwords for the app and the db and sticks them in SSM. Here's the process for deployment:

- Go to AWS CloudFormation
- Choose Create Stack (New Resources)
- Choose 'Template is Ready' / 'Upload a template file'
- Upload the Template `dev-resources/template/marketplace/1_secret.yml`
- Click Next
- Set the following Parameters:
    - DBMasterUserPasswordPath: (optional) Set to desired path in SSM
    - DBAppUserPasswordPath: (optional) Set to desired path in SSM.
    - DBMasterUsername: (optional) This is the master username for Postgres.
    - DBAppUsername: (optional) This is the master username for LRSQL. 
- Deploy the Stack.

This template deploys the credentials for the database in SSM. Make sure that the paths do not conflict with other paths for parameters contained within SSM. You can go into SSM to these paths to retrieve the credentials if you need to access the database.

### Step 2: Database

In this step you will deploy the Postgres Aurora database.

- Similar to Step 1, create a new stack and upload the db template located at `dev-resources/template/marketplace/2_db.yml`
- Parameters:
    - VPCStackName: Make sure this references the name of the stack the VPC is deployed in.
    - SecretStackName: Make sure this references the name of the Secret Stack name
    - DBName: Choose desired database name.
- Deploy the stack


### Step 3: LRS

This template deploys the application servers, the load balancer, and also a small AWS Lambda script which grants database access for application servers.

- Similar to previous steps, create and name a new stack and upload the LRS template (3_lrs.yml)
- Parameters
  - ALBCertARN: Copy the ARN from the ACM Certificate
  - ALBHostName: (Optional) Set the desired (sub)domain name 
  - ALBHostedZone: (Optional) Set the Hosted Zone ID if the domain registrar is Route53 to enable automatic DNS management
  - CORSAllowedOrigins: If you are using your own DNS and do not provide ALBHostName and ALBHostedZone above, put the HTTPS address of your LRS here, ie. `https://mydomain.com` to allow CORS requests.
  - SecretStackName: Choose the name of the stack deployed in step 1
  - DBStackName: Choose the name of the stack deployed in Step 2
  - VPCStackName: Choose name of deployed VPC stack.
  - DefaultAdminPass: Enter a temporary seed password for the LRS Admin login (for first login). **NOTE: You will NOT be able to see this password after you set it, so please write it down!**
  - DefaultAdminUser: Enter initial seed username for LRS Admin Login
  - InstanceKeyName: (Optional) Enter the name of your preferred EC2 Key-Pair
  - LogGroupPrefix: Leave this at the default: `/yet/lrsql/`
  - LogGroupRetentionInDays: Leave this at the default of 7 (days)
  - LrsVersion: Select the desired version of SQL LRS from the GitHub Releases page [here](https://github.com/yetanalytics/lrsql/releases)
  - DBInitFnVersion: Leave this at the default value
  - DBInitFnBucketOverride: Leave this blank unless you are deploying to a region outside of the US
  - DBInitFnKeyOverride: Leave this blank unless you are deploying to a region outside of the US
- Deploy the Stack

If all goes well, the LRS should be fully deployed. In the 'Outputs' tab of this stack you will find two outputs, `LrsAddress` and `LBEndpoint`. If you used an AWS Route53 hosted zone you should be able to visit the LRS by following the `LRSAddress` URL as soon as DNS propagates. If you did not you will need to create an A record in your domain's registrar pointed to the value in `LBEndpoint`. Once the LRS is accessible you will be able to use `DefaultAdminUser` value from this template to log in for the first time.
