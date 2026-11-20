CREATE TABLE IF NOT EXISTS companies (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    bin TEXT NOT NULL UNIQUE CHECK (length(bin) = 12),
    phone TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'new'
        CHECK (status IN ('new', 'no_answer', 'refused', 'meeting_set')),
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_companies_status ON companies (status);

CREATE TABLE IF NOT EXISTS managers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS meetings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL REFERENCES companies (id),
    manager_id INTEGER NOT NULL REFERENCES managers (id),
    start_at TEXT NOT NULL,
    end_at TEXT NOT NULL,
    original_start_at TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'scheduled'
        CHECK (status IN ('scheduled', 'cancelled')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    CHECK (end_at > start_at)
);

CREATE INDEX IF NOT EXISTS idx_meetings_manager_time ON meetings (manager_id, status, start_at);
CREATE INDEX IF NOT EXISTS idx_meetings_company_time ON meetings (company_id, status, start_at);

CREATE TABLE IF NOT EXISTS meeting_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    meeting_id INTEGER NOT NULL REFERENCES meetings (id),
    action TEXT NOT NULL CHECK (action IN ('created', 'rescheduled', 'cancelled')),
    start_at TEXT NOT NULL,
    end_at TEXT NOT NULL,
    changed_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_meeting_history_meeting ON meeting_history (meeting_id);

CREATE TABLE IF NOT EXISTS calls (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL REFERENCES companies (id),
    result TEXT NOT NULL CHECK (result IN ('no_answer', 'refused', 'meeting_set')),
    meeting_id INTEGER REFERENCES meetings (id),
    comment TEXT,
    called_at TEXT NOT NULL,
    CHECK ((result = 'meeting_set') = (meeting_id IS NOT NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_calls_meeting ON calls (meeting_id) WHERE meeting_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_calls_company ON calls (company_id);
