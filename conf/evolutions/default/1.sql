# --- !Ups

CREATE TABLE companies (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    domain VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE departments (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    company_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_department_company
        FOREIGN KEY(company_id)
        REFERENCES companies(id)
        ON DELETE CASCADE,
    UNIQUE(name, company_id)
);

CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password TEXT NOT NULL,
    role TEXT NOT NULL,
    company_id VARCHAR(36),
    department_id VARCHAR(36),
    total_points INT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_company
        FOREIGN KEY(company_id)
        REFERENCES companies(id)
        ON DELETE SET NULL,
    CONSTRAINT fk_user_department
        FOREIGN KEY(department_id)
        REFERENCES departments(id)
        ON DELETE SET NULL
);

# --- !Downs

DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS departments;
DROP TABLE IF EXISTS companies;