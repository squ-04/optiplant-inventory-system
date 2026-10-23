-- El administrador de PostgreSQL crea el rol de grupo antes del primer arranque.
-- Flyway usa credenciales de migración distintas de las del datasource operativo.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='optiplant_runtime') THEN
        RAISE EXCEPTION 'Debe crear el rol NOLOGIN optiplant_runtime antes de ejecutar Flyway';
    END IF;
END $$;

GRANT USAGE ON SCHEMA public TO optiplant_runtime;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO optiplant_runtime;

DO $$
DECLARE
    t RECORD;
BEGIN
    FOR t IN SELECT tablename FROM pg_tables WHERE schemaname='public'
        AND tablename NOT IN ('flyway_schema_history','spring_session','spring_session_attributes')
    LOOP
        EXECUTE format('GRANT SELECT, INSERT, UPDATE ON TABLE public.%I TO optiplant_runtime', t.tablename);
    END LOOP;
END $$;

GRANT SELECT ON flyway_schema_history TO optiplant_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON spring_session, spring_session_attributes TO optiplant_runtime;
REVOKE DELETE ON catalog_product FROM optiplant_runtime;
