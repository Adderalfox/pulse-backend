# --- !Ups

CREATE TABLE embedding_sync_log (
    appreciation_id VARCHAR(36) PRIMARY KEY REFERENCES appreciations(id) ON DELETE CASCADE,
    qdrant_point_id VARCHAR(36) NOT NULL,
    model_version VARCHAR(100) NOT NULL,
    embedded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

# --- !Downs

DROP TABLE IF EXISTS embedding_sync_log;