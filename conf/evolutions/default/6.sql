# --- !Ups

CREATE TABLE appreciation_skills (
    id VARCHAR(36) PRIMARY KEY,
    appreciation_id VARCHAR(36) NOT NULL REFERENCES appreciations(id) ON DELETE CASCADE,
    skill_id VARCHAR(36) NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    llm_confidence DOUBLE PRECISION NOT NULL,
    extraction_model VARCHAR(100) NOT NULL,
    extracted_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_appreciation_skill UNIQUE (appreciation_id, skill_id)
);

CREATE INDEX idx_appr_skills_appreciation ON appreciation_skills(appreciation_id);
CREATE INDEX idx_appr_skills_skill ON appreciation_skills(skill_id);

# --- !Downs

DROP TABLE IF EXISTS appreciation_skills;