CREATE TABLE IF NOT EXISTS raw_documents (
    id BIGSERIAL PRIMARY KEY,
    url VARCHAR(2048) NOT NULL,
    content TEXT NOT NULL,
    content_type VARCHAR(255),
    charset VARCHAR(64),
    etag VARCHAR(255),
    last_modified VARCHAR(255),
    fetched_at TIMESTAMP,
    content_hash VARCHAR(255)
);
