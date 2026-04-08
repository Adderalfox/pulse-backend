# --- !Ups

CREATE TABLE interaction_edges
(
    id               VARCHAR(36) PRIMARY KEY,
    user_id_from     VARCHAR(36) NOT NULL,
    user_id_to       VARCHAR(36) NOT NULL,
    company_id       VARCHAR(36) NOT NULL,
    interaction_type VARCHAR(30) NOT NULL
        CHECK (interaction_type IN
               ('appreciated', 'co_appreciated', 'same_team', 'manager_of', 'lead_of')),
    weight           FLOAT                    DEFAULT 1.0,
    last_seen_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_edge_from FOREIGN KEY (user_id_from) REFERENCES users (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_edge_to FOREIGN KEY (user_id_to) REFERENCES users (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_edge_company FOREIGN KEY (company_id) REFERENCES companies (id)
        ON DELETE CASCADE,
    UNIQUE (user_id_from, user_id_to, interaction_type)
);

CREATE TABLE user_skill_scores
(
    id                VARCHAR(36) PRIMARY KEY,
    user_id           VARCHAR(36)  NOT NULL,
    company_id        VARCHAR(36)  NOT NULL,
    skill_name        VARCHAR(100) NOT NULL,
    score             FLOAT                    DEFAULT 0.0,
    endorsement_count INT                      DEFAULT 0,
    certified         BOOLEAN                  DEFAULT false,
    updated_at        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_skill_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_skill_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE,
    UNIQUE (user_id, skill_name)
);

# --- !Downs

DROP TABLE IF EXISTS user_skill_scores;
DROP TABLE IF EXISTS interaction_edges;