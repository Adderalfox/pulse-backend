# --- !Ups

CREATE TABLE skills (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    normalized_name VARCHAR(255) NOT NULL UNIQUE,
    category VARCHAR(50) NOT NULL
                    CHECK (category IN ('TECHNICAL', 'BEHAVIORAL', 'DOMAIN')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_skills_normalized ON skills(normalized_name);
CREATE INDEX idx_skills_category ON skills(category);


# --- !Downs

DROP TABLE IF EXISTS skills;