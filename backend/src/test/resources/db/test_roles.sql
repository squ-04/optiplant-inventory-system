-- Credencial exclusiva de un contenedor de pruebas efímero, nunca semilla desplegable.
CREATE ROLE optiplant_runtime NOLOGIN;
CREATE ROLE optiplant_test_runtime LOGIN PASSWORD 'test-runtime-not-for-deployment'
    NOSUPERUSER NOCREATEDB NOCREATEROLE IN ROLE optiplant_runtime;
