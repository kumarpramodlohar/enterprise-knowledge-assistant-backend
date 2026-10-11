-- Local development only. The local HTTP Basic password is configured separately in .env.
-- Run with: psql "$EKA_DB_URL" -v ON_ERROR_STOP=1 -f src/main/resources/db/seed/local-dev.sql
-- Change the subject and collection name below when seeding another local user/collection.
INSERT INTO collections (name)
VALUES (:'collection_name')
ON CONFLICT (name) DO NOTHING;

INSERT INTO app_users (subject, role)
VALUES (:'local_subject', 'MEMBER')
ON CONFLICT (subject) DO NOTHING;

INSERT INTO collection_grants (subject, collection_id, permission)
SELECT :'local_subject', id, 'MANAGE'
FROM collections
WHERE name = :'collection_name'
ON CONFLICT (subject, collection_id)
DO UPDATE SET permission = EXCLUDED.permission;
