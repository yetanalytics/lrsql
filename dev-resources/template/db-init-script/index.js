const { Client } = require('pg')
var pgformat = require('pg-format');

const username = process.env.PG_USER;
const password = process.env.PG_PASS;
const db = process.env.PG_DB;

const client = new Client({
    host: process.env.PG_HOST,
    port: process.env.PG_PORT,
    database: db,
    user: process.env.PG_ADMIN_USER,
    password: process.env.PG_ADMIN_PASS,
});

const checkQuery = 'SELECT FROM pg_catalog.pg_roles WHERE rolname = $1::text';
const createQuery = pgformat('CREATE USER %I WITH ENCRYPTED PASSWORD %L', username, password);
const grantQuery = pgformat('GRANT ALL PRIVILEGES ON DATABASE %I TO %I', db, username);

exports.handler = async (event) => {
    try {
        console.log("Attempting database connection");
        await client.connect();
        console.log("Beginning db init transaction");
        await client.query("BEGIN");
        try {
            //check for admin user
            const userResponse = await client.query(checkQuery, [username]);
            console.log(userResponse);
            if (userResponse.rowCount < 1) {
                //create user
                console.log("Creating User");
                const createResponse = await client.query(createQuery);
                //grant db priv to user
                console.log("Granting privileges to user");
                const grantResponse = await client.query(grantQuery);
                console.log(await client.query("SELECT FROM pg_catalog.pg_roles WHERE rolname = $1::text", [username]));
            }
            await client.query("COMMIT");
            console.log("Finished db init");
        } catch(err) {
            console.log("db init transaction failed");
            console.log(err)
            await client.query("ROLLBACK")
        }
    } catch (err) {
        console.log("Error connecting to database");
        console.log(err);
    } finally {
        client.end();
    }
};
