-- Snapshot the tenant's server-selected storage prefix with each resumable upload.
-- Existing upload sessions retain their original provider and use the empty prefix.
ALTER TABLE upload_session
    ADD COLUMN object_prefix varchar(200) NOT NULL DEFAULT '';
