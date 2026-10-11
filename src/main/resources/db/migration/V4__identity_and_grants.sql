-- Server-side identity and permission data. Roles and grants are never taken from client input.
CREATE TABLE app_users (
    subject      TEXT PRIMARY KEY,
    display_name TEXT,
    role         TEXT        NOT NULL CHECK (role IN ('ADMIN', 'MEMBER')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE collection_grants (
    subject       TEXT        NOT NULL REFERENCES app_users (subject) ON DELETE CASCADE,
    collection_id UUID        NOT NULL REFERENCES collections (id) ON DELETE CASCADE,
    permission    TEXT        NOT NULL CHECK (permission IN ('READ', 'MANAGE')),
    PRIMARY KEY (subject, collection_id)
);
CREATE INDEX idx_collection_grants_collection ON collection_grants (collection_id);
