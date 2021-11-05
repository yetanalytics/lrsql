const { Client } = require('pg')
var pgformat = require('pg-format');
const AWS = require("aws-sdk");
const sm = new AWS.SSM();

//helper to grab and parse secure strings from ssm
const getParam = async (path, secure) => {
   try {
        const appPassword = await sm.getParameter({
          Name: path,
          WithDecryption: secure,
        }).promise();
        return await appPassword.Parameter.Value;
    } catch (e)  {
        console.log(e);
        return null;
    }
};

//lambda takes db info from custom resolver and inits app db user idempotently
exports.handler = async (event) => {

    console.log("init db started");
    console.log(event);
    const input = event.ResourceProperties;
    const appUser = input.DBUsername;
    const appPass = await getParam(input.DBPasswordPath, true);
    const db = input.DBName;
    const client = new Client({
        host: input.DBHost,
        port: input.DBPort,
        database: db,
        user: input.DBMasterUsername,
        password: await getParam(input.DBMasterPasswordPath, true)
    });

    //needed to use a pg query formtter because you can't use identifiers as vars in prepared statements
    const checkQuery = 'SELECT FROM pg_catalog.pg_roles WHERE rolname = $1::text';
    const createQuery = pgformat('CREATE USER %I WITH ENCRYPTED PASSWORD %L', appUser, appPass);
    const grantQuery = pgformat('GRANT ALL PRIVILEGES ON DATABASE %I TO %I', db, appUser);

    try {
        console.log("Attempting database connection");
        await client.connect();
        console.log("Beginning db init transaction");
        await client.query("BEGIN");
        try {
            //check for admin user
            const userResponse = await client.query(checkQuery, [appUser]);
            console.log(userResponse);
            if (userResponse.rowCount < 1) {
                //create user
                console.log("Creating User");
                const createResponse = await client.query(createQuery);
                //grant db priv to user
                console.log("Granting privileges to user.");
                const grantResponse = await client.query(grantQuery);
            } else {
                console.log("User already exists. Exiting.");
            }
            await client.query("COMMIT");
            console.log("Finished db init");
        } catch(err) {
            console.log("db init transaction failed");
            console.log(err);
            await client.query("ROLLBACK");
        }
    } catch (err) {
        console.log("Error connecting to database");
        console.log(err);
    } finally {
        client.end();
    }
};
