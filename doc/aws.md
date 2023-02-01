[<- Back to Index](index.md)

# Example AWS Deployment

In order to make it easy to get started deploying the SQL LRS, included in this repository is a set of sample Amazon Web Services (AWS) CloudFormation Templates. When deployed, the templates create a scalable and secured cloud installation of the SQL LRS.

__NOTE:__ *This configuration is not one-size-fits-all and you may require a different configuration for your particular needs. It is provided for demonstration purposes only and can be used as a reference to adapt to your particular enterprise's needs. If you apply these templates in your own AWS account, it can and will incur charges from Amazon Web Services. Yet Analytics is in no way responsible for any charges due to applying and implementing these templates, and is in no way responsible for any outcomes of applying these templates or implementing SQL LRS. If your team is interested in consulting or support in setting up or maintaining a SQL LRS please [contact Yet here](https://www.sqllrs.com/contact)*.

### Deployment Overview

This deployment entails the creation of an Auto-Scaling Group of EC2 Servers containing the SQL LRS Application, an RDS Aurora Postgres Database Cluster, an Application Load Balancer and associated network resources and utilities. The installation also makes use of DNS and TLS Certificates as needed to enable a secure connection to the SQL LRS.

The way that these resources are deployed is through the use of CloudFormation Templates, which in this case are YAML files containing descriptions of all required resources and associations. The basic methodology is to visit AWS CloudFormation, provide the template files in the correct order, and provide the appropriate parameters for each template. The process takes about 20-30 minutes (which is mostly waiting for AWS to spin up the required resources).

### Step 1: Preparation

In this step we will not be deploying any templates but will instead be preparing the ancillary resources that the templates will need in order to deploy the LRS properly. All you need to start off with is an AWS Account, the rest is detailed here.

#### Domain

Configuring a domain or subdomain will allow you to access the SQL LRS at that URL. If you have (or can acquire) the domain through AWS Route53, these templates provide automated DNS updates that will route the domain to the LRS upon deployment or update. If you already have the domain you will be using through another registrar, you will need to update a DNS record in your own registrar at the end of deployment in order to use the domain.

Note that if you do not use Route53 DNS you MUST provide one or more allowed CORS origins with the CORSAllowedOrigins LRS template parameter (see Step 4 below).

#### TLS Certificate

In these templates the Load Balancer expects to provide access to the LRS via HTTPS/443. You will need to either acquire a free Amazon Certificate Manager cert (highly recommended) or import your own cert from another CA into ACM for use in the deployment.

#### Database Passwords in Systems Manager

The deployment requires two passwords for the Postgres database. One 'Master' password provided to the database as it is created, and one 'App' password provided to SQL LRS to access the database. These passwords are managed in Systems Manager (SSM).

- Visit AWS Systems Manager
- Go to 'Parameter Store'
- Create two new parameters (Master and App Passwords)
  - Name is up to you but you will need it later
  - For type choose SecureString
  - Value is the password, and must be 8-128 ASCII characters excluding /, \`, or @

#### EC2 Key-Pair (optional)

If you foresee needing SSH access to the servers directly, you'll want to have the name of the EC2 Key-Pair of your choice ready to provide during deployment. In practice if you wish to SSH into the servers you will likely also need another EC2 instance (not covered in this guide) in a public subnet, because the servers themselves will be deployed into private subnets inaccessible from the internet directly.

### Step 2: Networking (optional)

This step creates a VPC with two public subnets and two private subnets with routing and an internet gateway. For an advanced AWS user with an existing account this may not be needed or preferable, but either way at the end you will need two public and two private subnets, and the private subnets must have a NAT with outbound access to the internet and routing equivalent to the template. For simplicity, instructions in subsequent steps will assume you deployed this template.

- Go to AWS CloudFormation
- Choose Create Stack (New Resources)
- Choose 'Template is Ready' / 'Upload a template file'
- Upload the Template `dev-resources/template/0_vpc.yml`
- Click Next
- Name the Stack, and review the CIDR ranges to make sure they do not conflict with existing network topology in your AWS account, and adjust as needed
- Deploy the Stack

After deployment is complete CloudFormation should give you access to an 'Outputs' tab which contains the details about the created subnets. You will be referencing these subnets extensively in the next steps, so it's advisable to keep this tab open.

### Step 3: Database

In this step you will deploy the Postgres Aurora database. The remainder of this guide will only cover the necessary parameters for deployment, and will assume that the default was accepted for all the others.

- Similar to Step 2, create and name a new stack and upload the DB template (1_db.yml)
- Parameters
  - DBMasterUsername: Create a username for the DB root user
  - DBMasterUserPasswordPath: Use the name selected in Systems Manager for the Master Password in Step 1
  - DBName: Choose desired database name
  - DBSubnets: Choose the two Private Subnets Created in Step 2
  - VPCId: Choose the VPC created in Step 2
- Deploy the Stack

After deployment this stack will also have an 'Outputs' tab containing useful information for the next step.

### Step 4: LRS

This template deploys the application servers, the load balancer, and also a small AWS Lambda script which grants database access for application servers.

- Similar to previous steps, create and name a new stack and upload the LRS template (2_lrs.yml)
- Parameters
  - ALBCertARN: Copy the ARN from the ACM Certificate from Step 1
  - ALBHostName: (Optional) Set the desired (sub)domain name from Step 1
  - ALBHostedZone: (Optional) Set the Hosted Zone ID if the domain registrar is Route53 to enable automatic DNS management
  - ALBSubnets: Choose the two Public Subnets from Step 2
  - CORSAllowedOrigins: If you are using your own DNS and do not provide ALBHostName and ALBHostedZone above, put the HTTPS address of your LRS here, ie. `https://mydomain.com` to allow CORS requests.
  - DBAppUserName: Choose a desired database username for the application
  - DBAppUserPasswordPath: Use the name selected in Systems Manager for the App Password in Step 1
  - DBHost: Copy and paste the DBEndpoint Output from Step 3
  - DBInstanceSG: Select the DBInstanceSG Output from Step 3
  - DBMasterUserName: Must be the same value as in Step 3
  - DBMasterUserPasswordPath: Must be the same value as in Step 3
  - DBName: Must be the same value as in Step 3
  - DBPort: 3306
  - DBSubnets: Select the two Private Subnets from Step 2
  - DefaultAdminPass: Enter a temporary seed password for the LRS Admin login (for first login).
  - DefaultAdminUser: Enter initial seed username for LRS Admin Login
  - InstanceKeyName: (Optional) Enter the name of your preferred EC2 Key-Pair from Step 1
  - InstanceSubnets: Choose the two Private Subnets from Step 2
  - LogGroupPrefix: Leave this at the default: `/yet/lrsql/`
  - LogGroupRetentionInDays: Leave this at the default of 7 (days)
  - LrsVersion: Select the desired version of SQL LRS from the GitHub Releases page [here](https://github.com/yetanalytics/lrsql/releases)
  - S3Bucket: Leave this at the default: `lrsql-dbfn`
  - S3Key: Leave this at the default: (blank)
  - VPCId: VPC Created in Step 1
- Deploy the Stack

If all goes well, the LRS should be fully deployed. In the 'Outputs' tab of this stack you will find two outputs, `LrsAddress` and `LBEndpoint`. If you used an AWS Route53 hosted zone you should be able to visit the LRS by following the `LRSAddress` URL as soon as DNS propagates. If you did not you will need to create an A record in your domain's registrar pointed to the value in `LBEndpoint`. Once the LRS is accessible you will be able to use `DefaultAdminUser` value from this template to log in for the first time.

[<- Back to Index](index.md)
