# --- !Ups

CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    email TEXT NOT NULL,
    role TEXT NOT NULL,
    total_points INT DEFAULT 0
);

# --- !Downs

DROP TABLE users;