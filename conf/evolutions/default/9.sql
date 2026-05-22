# --- !Ups

CREATE TABLE award_definitions
(
    id            VARCHAR(36) PRIMARY KEY,
    company_id    VARCHAR(36)  NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    department_id VARCHAR(36)  REFERENCES departments (id) ON DELETE SET NULL,
    name          VARCHAR(255) NOT NULL,
    description   TEXT         NOT NULL,
    criteria_text TEXT         NOT NULL,
    created_by    VARCHAR(36)  NOT NULL REFERENCES users (id) ON DELETE SET NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_award_definitions_company ON award_definitions (company_id);
CREATE INDEX idx_award_definitions_department ON award_definitions (department_id);
CREATE INDEX idx_award_definitions_created_by ON award_definitions (created_by);

# --- !Downs

DROP TABLE IF EXISTS award_definitions;