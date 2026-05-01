# --- !Ups

CREATE TABLE nomination_drafts (
    id VARCHAR(36) PRIMARY KEY,
    nominee_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    requested_by VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    award_category VARCHAR(100) NOT NULL,
    draft_text TEXT NOT NULL,
    skills_cited TEXT NOT NULL DEFAULT '',
    appreciations_used INTEGER NOT NULL DEFAULT 0,
    generated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_nomination_drafts_nominee ON nomination_drafts(nominee_id);
CREATE INDEX idx_nomination_drafts_requester ON nomination_drafts(requested_by);

# --- !Downs

DROP TABLE IF EXISTS nomination_drafts;
