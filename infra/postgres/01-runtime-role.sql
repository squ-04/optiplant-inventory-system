-- Only the fresh local cluster runs this file; never contains a public password.
\set ON_ERROR_STOP on
\set ECHO none
\getenv runtime_password APP_DB_PASSWORD
\getenv migration_password APP_DB_MIGRATION_PASSWORD
\getenv database_name POSTGRES_DB
CREATE ROLE optiplant_runtime NOLOGIN;
SELECT format('CREATE ROLE optiplant_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L', :'runtime_password')
\gexec
SELECT format('CREATE ROLE optiplant_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L', :'migration_password')
\gexec
GRANT optiplant_runtime TO optiplant_app;
GRANT CONNECT ON DATABASE :"database_name" TO optiplant_app, optiplant_migrator;
ALTER SCHEMA public OWNER TO optiplant_migrator;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
\unset runtime_password
\unset migration_password
