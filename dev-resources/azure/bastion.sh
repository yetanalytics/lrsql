# Variables
RESOURCE_GROUP="lrsql-infra-rg"
JUMPBOX_VM="lrsqldev-jumpbox"
SSH_KEY="$HOME/.ssh/id_ed25519" 

# Get Bastion endpoint
BASTION_HOST=$(az network bastion list -g $RESOURCE_GROUP --query "[0].name" -o tsv)

# Open an SSH session via Bastion
az network bastion ssh \
  --name $BASTION_HOST \
  --resource-group $RESOURCE_GROUP \
  --target-resource-id $(az vm show -g $RESOURCE_GROUP -n $JUMPBOX_VM --query "id" -o tsv) \
  --auth-type ssh-key \
  --username azureuser \
  --ssh-key $SSH_KEY