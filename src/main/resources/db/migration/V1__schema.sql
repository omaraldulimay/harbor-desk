-- Application role: no superuser, no BYPASSRLS, owns nothing. Created once per cluster.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'harbor_app') THEN
    CREATE ROLE harbor_app LOGIN PASSWORD 'harbor_app' NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
  END IF;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- Tenant registry. The id is the tenant id, so the same policy shape applies.
CREATE TABLE tenants (
  id         uuid PRIMARY KEY,
  slug       text NOT NULL UNIQUE,
  name       text NOT NULL,
  plan       text NOT NULL CHECK (plan IN ('free', 'pro', 'enterprise')),
  db_name    text,          -- null = shared pool database, otherwise the silo database
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE tickets (
  id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  tenant_id  uuid NOT NULL,
  title      text NOT NULL,
  body       text NOT NULL DEFAULT '',
  status     text NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'in_progress', 'resolved', 'closed')),
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (tenant_id, id)
);
CREATE INDEX tickets_tenant_idx ON tickets (tenant_id, id DESC);

-- The composite key makes a comment on another tenant's ticket impossible, even without RLS.
CREATE TABLE comments (
  id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  tenant_id  uuid NOT NULL,
  ticket_id  bigint NOT NULL,
  author     text NOT NULL,
  body       text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY (tenant_id, ticket_id) REFERENCES tickets (tenant_id, id)
);
CREATE INDEX comments_ticket_idx ON comments (tenant_id, ticket_id, id);

-- Row level security, enforced even for the table owner. An unset setting matches no row.
ALTER TABLE tenants  ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenants  FORCE  ROW LEVEL SECURITY;
ALTER TABLE tickets  ENABLE ROW LEVEL SECURITY;
ALTER TABLE tickets  FORCE  ROW LEVEL SECURITY;
ALTER TABLE comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE comments FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON tenants
  USING      (id = nullif(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON tickets
  USING      (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON comments
  USING      (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

-- No DELETE and no DDL for the app role.
GRANT SELECT ON tenants TO harbor_app;
GRANT SELECT, INSERT, UPDATE ON tickets, comments TO harbor_app;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO harbor_app;
