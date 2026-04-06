# --- !Ups

CREATE TABLE appreciations
(
    id                VARCHAR(36) PRIMARY KEY,
    giver_id          VARCHAR(36) NOT NULL,
    receiver_id       VARCHAR(36) NOT NULL,
    company_id        VARCHAR(36) NOT NULL,
    department_id     VARCHAR(36),
    text              TEXT        NOT NULL,
    appreciation_type VARCHAR(30) NOT NULL
        CHECK (appreciation_type IN
               ('peer', 'manager_nomination', 'lead_recognition')),
    visibility        VARCHAR(20) NOT NULL     DEFAULT 'public'
        CHECK (visibility IN ('public', 'team only')),
    points_awarded    INT                      DEFAULT 0,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_appreciation_giver
        FOREIGN KEY (giver_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_appreciation_receiver
        FOREIGN KEY (receiver_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_appreciation_company
        FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE,
    CONSTRAINT fk_appreciation_department
        FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE CASCADE SET NULL
);

CREATE TABLE appreciation_skill_tags
(
    id               VARCHAR(36) PRIMARY KEY,
    appreciation_id  VARCHAR(36) NOT NULL,
    confidence_score FLOAT                    DEFAULT 1.0,
    source           VARCHAR(20) NOT NULL     DEFAULT 'manual'
        CHECK (source IN ('manual', 'ai_extracted')),
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tag_appreciation
        FOREIGN KEY (appreciation_id) REFERENCES appreciations (id) ON DELETE CASCADE
);

# --- !Downs

DROP TABLE IF EXISTS appreciation_skill_tags;
DROP TABLE IF EXISTS appreciation;