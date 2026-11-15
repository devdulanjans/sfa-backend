-- Optional product picture (V123), shown on the public website catalogue. Stored like the
-- company logo (MinIO, or the local folder in development); the row keeps only its path.
ALTER TABLE products
    ADD COLUMN image_object_path  VARCHAR(300),
    ADD COLUMN image_content_type VARCHAR(100),
    ADD COLUMN image_updated_at   TIMESTAMPTZ;
