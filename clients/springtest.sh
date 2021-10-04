#!/bin/bash
echo "Issuing tokens"
curl -H "Content-Type: application/json" --request POST -d '{"numberOfTokens": 100, "nodeName": "O=Grid, L=London, C=GB"}' \
localhost:10090/issueTokens
echo ""

echo "Creating account"
curl -H "Content-Type: application/json" --request POST -d '{"acctName": "Batmobile"}' localhost:10070/createAccount
echo ""

echo "Getting VW peers"
curl -H "Content-Type: application/json" --request GET \
localhost:10070/peers
echo ""

echo "Getting Grid peers"
curl -H "Content-Type: application/json" --request GET \
localhost:10080/peers
echo ""

echo "Sharing account"
curl -H "Content-Type: application/json" --request POST \
-d '{"accountName": "Batmobile", "nodeName": "O=Grid,L=London,C=GB"}' localhost:10070/shareAccountInfo
echo ""

echo "Sending energy tokens"
transaction_hash=$(curl -H "Content-Type: application/json" --request POST \
-d '{"numberOfTokens": 20, "sendToAccountName":"Batmobile", "sanctionsBody": "O=Parsedata,L=Toronto,C=CA", "dataHash":"abc123", "note":"have a nice day"}' \
localhost:10080/sendEnergyTokens | jq '.transactionHash')
echo "transaction hash: $transaction_hash"

echo "Getting balance of Batmobile"
curl -H "Content-Type: application/json" --request GET \
-d '{"account":"Batmobile"}' localhost:10070/accountTokenBalance
echo ""

echo "Getting transaction details"
echo 'Using data:{"transaction_hash":'${transaction_hash}'}'
curl -H "Content-Type: application/json" --request GET \
-d '{"transactionHash":'${transaction_hash}'}' localhost:10070/transactionDetails
echo ""

echo "Querying by data hash"
curl -H "Content-Type: application/json" --request GET \
-d '{"dataHash":"abc123"}' localhost:10070/queryByDataHash
echo ""

echo "Creating new account Bluesmobile on Grid node"
curl -H "Content-Type: application/json" --request POST -d '{"acctName": "Bluesmobile"}' localhost:10080/createAccount
echo ""

echo "Sharing account Bluesmobile with VW"
curl -H "Content-Type: application/json" --request POST \
-d '{"accountName": "Bluesmobile", "nodeName": "O=VW,L=Wolfsburg,C=DE"}' localhost:10080/shareAccountInfo
echo ""

echo "Sending tokens from Batmobile to Bluesmobile"
curl -H "Content-Type: application/json" --request POST \
-d '{"sendToAccountName":"Bluesmobile","sendFromAccountName":"Batmobile","numberOfTokens":10}' \
localhost:10070/sendfromaccount
echo ""

echo "Getting Bluesmobile balance"
curl -H "Content-Type: application/json" --request GET \
-d '{"account":"Bluesmobile"}' localhost:10080/accountTokenBalance
echo ""

echo "Getting total Grid balance"
curl -H "Content-Type: application/json" --request GET \
localhost:10080/nodeTokenBalance

#O%3DGrid%2CL%3DLondon%2CC%3DGB
#O%3DParsedata%2CL%3DToronto%2CC%3DCA
