// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

exports.SUCCESS = "SUCCESS";
exports.FAILED = "FAILED";

//modified for async
exports.send = async (event, context, responseStatus, responseData, physicalResourceId, noEcho) => {

    var responseBody = JSON.stringify({
        Status: responseStatus,
        Reason: "See the details in CloudWatch Log Stream: " + context.logStreamName,
        PhysicalResourceId: physicalResourceId || context.logStreamName,
        StackId: event.StackId,
        RequestId: event.RequestId,
        LogicalResourceId: event.LogicalResourceId,
        NoEcho: noEcho || false,
        Data: responseData
    });

    //console.log("Response body:\n", responseBody);

    var https = require("https");
    var url = require("url");

    var parsedUrl = url.parse(event.ResponseURL);
    var options = {
        hostname: parsedUrl.hostname,
        port: 443,
        path: parsedUrl.path,
        method: "PUT",
        headers: {
            "content-type": "",
            "content-length": responseBody.length
        }
    };

    return new Promise((resolve, reject) => {
        var request = https.request(options, (response) => {
            console.log("Status code: " + response.statusCode);
            console.log("Status message: " + response.statusMessage);
            resolve();
        });

        request.on("error", (error) => {
            console.log("send(..) failed executing https.request(..): " + error);
            reject(error);
        });

        request.write(responseBody);
        request.end();
    });

};
