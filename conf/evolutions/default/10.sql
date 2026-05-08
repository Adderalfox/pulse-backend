# --- !Ups

CREATE TABLE award_recommendations
(
    id                 VARCHAR(36) PRIMARY KEY,
    nominee_id         VARCHAR(36) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    requested_by       VARCHAR(36) NOT NULL REFERENCES users (id) ON DELETE SET NULL,
    recommended_awards TEXT        NOT NULL,
    profile_summary    TEXT        NOT NULL,
    generated_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_award_recommendations_nominee ON award_recommendations (nominee_id);
CREATE INDEX idx_award_recommendations_requested_by ON award_recommendations (requested_by);

# --- !Downs

DROP TABLE IF EXISTS award_recommendations;