CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE issues (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_users(id),
    title VARCHAR(200) NOT NULL,
    description VARCHAR(10000) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'UNTRIAGED',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX issues_owner_created_idx ON issues(owner_id, created_at DESC);
CREATE INDEX issues_status_created_idx ON issues(status, created_at DESC);

CREATE TABLE triage_results (
    id UUID PRIMARY KEY,
    issue_id UUID NOT NULL REFERENCES issues(id),
    model VARCHAR(100) NOT NULL,
    category VARCHAR(20) NOT NULL,
    category_confidence DOUBLE PRECISION NOT NULL,
    category_probabilities JSONB NOT NULL,
    urgency_score DOUBLE PRECISION NOT NULL,
    urgency_confidence DOUBLE PRECISION NOT NULL,
    urgency_probabilities JSONB NOT NULL,
    sufficient_information_probability DOUBLE PRECISION NOT NULL,
    outcome VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX triage_results_issue_created_idx ON triage_results(issue_id, created_at DESC);

CREATE TABLE reply_drafts (
    id UUID PRIMARY KEY,
    issue_id UUID NOT NULL REFERENCES issues(id),
    created_by UUID NOT NULL REFERENCES app_users(id),
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX reply_drafts_issue_created_idx ON reply_drafts(issue_id, created_at DESC);
