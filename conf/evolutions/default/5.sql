# --- !Ups

CREATE TABLE user_skills (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    skill_id VARCHAR(36) NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    raw_score DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    recency_weighted_score DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    frequency_count INTEGER NOT NULL DEFAULT 0,
    appreciator_diversity INTEGER NOT NULL DEFAULT 0,
    composite_score DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    appreciator_ids TEXT NOT NULL DEFAULT '',
    last_updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_skill UNIQUE (user_id, skill_id)
);

CREATE INDEX idx_user_skills_user_id ON user_skills(user_id);
CREATE INDEX idx_user_skills_skill_id ON user_skills(skill_id);
CREATE INDEX idx_user_skills_composite ON user_skills(composite_score DESC);

# --- !Downs

DROP TABLE IF EXISTS user_skills;