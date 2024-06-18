#!/bin/bash

trigger_ami_build() {
  set -e

  VERSION=$1
  SEMANTIC_VERSION="${VERSION#v}" # removes trailing v for AWS
  
  COMPONENT_ARN=$(aws imagebuilder list-components | jq -r '.componentVersionList[] | select(.name == "InstallLRSQL").arn')
  PIPELINE_ARN=$(aws imagebuilder list-image-pipelines | jq -r '.imagePipelineList[] | select(.name == "lrsql-ami-pipeline").arn')
  INFRA_ARN=$(aws imagebuilder list-image-pipelines | jq -r '.imagePipelineList[] | select(.name == "lrsql-ami-pipeline").infrastructureConfigurationArn')
  
  COMPONENTS=$(jq -n \
      --arg arn "$COMPONENT_ARN" \
      --arg version "$VERSION" \
  '[
    {
      "componentArn": $arn,
      "parameters": 
      [
        {
          "name": "Version",
          "value": [$version]
        }
      ]
    }
  ]'
  )
  
  # create new recipe
  IMAGE_RECIPE_ARN=$(aws imagebuilder create-image-recipe \
                       --name "lrsql-ami" \
                       --semantic-version $SEMANTIC_VERSION \
                       --parent-image "arn:aws:imagebuilder:us-east-1:aws:image/amazon-linux-2023-x86/x.x.x" \
                       --components "$COMPONENTS" | jq -r '.imageRecipeArn')
  
  
  # update the image pipeline with new recipe
  aws imagebuilder update-image-pipeline \
    --image-pipeline-arn $PIPELINE_ARN \
    --image-recipe-arn $IMAGE_RECIPE_ARN \
    --infrastructure-configuration-arn $INFRA_ARN
  
  # triggers the build
  aws imagebuilder start-image-pipeline-execution \
    --image-pipeline-arn $PIPELINE_ARN
}


# Check if script is being run directly and handle command line arguments
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    while getopts "v:" opt; do
        case $opt in
            v) VERSION="$OPTARG";;
            *) echo "Usage: $0 [-v version]"; exit 1;;
        esac
    done

    if [ -z "$VERSION" ]; then
        echo "Error: Version is not set. Use the -v option to specify the version of LRSQL."
        exit 1
    fi

    trigger_ami_build $VERSION
fi
