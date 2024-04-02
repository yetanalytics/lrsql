# !/bin/bash

# TODO: ADD this to GH Actions and get version dynamically when new LRSQL release is cut
VERSION="0.7.11"
COMPONENT_ARN=$(aws imagebuilder list-components | jq -r '.componentVersionList[] | select(.name == "InstallLRSQL").arn')
PIPELINE_ARN=$(aws imagebuilder list-image-pipelines | jq -r '.imagePipelineList[] | select(.name == "lrsql-ami-pipeline").arn')
INFRA_ARN=$(aws imagebuilder list-image-pipelines | jq -r '.imagePipelineList[] | select(.name == "lrsql-ami-pipeline").infrastructureConfigurationArn')

COMPONENTS=$(jq -n \
    --arg arn "$COMPONENT_ARN" \
    --arg version "v$VERSION" \
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
                     --semantic-version $VERSION \
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
