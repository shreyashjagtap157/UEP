-- Durable marker prevents failed staging-file deletion from monopolizing the cleanup batch forever.
ALTER TABLE upload_session
    ADD COLUMN staging_cleaned_at timestamptz;

CREATE INDEX ix_upload_session_pending_staging_cleanup
    ON upload_session(status, staging_cleaned_at, expires_at, completed_at)
    WHERE staging_cleaned_at IS NULL;
