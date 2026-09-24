CREATE TABLE IF NOT EXISTS block_changes (
    world       TEXT    NOT NULL,
    x           INTEGER NOT NULL,
    y           INTEGER NOT NULL,
    z           INTEGER NOT NULL,
    player      TEXT    NOT NULL,
    changed_at  INTEGER NOT NULL,
    reported_at INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (world, x, y, z, player)
) WITHOUT ROWID;

CREATE INDEX IF NOT EXISTS block_changes_by_time ON block_changes (changed_at);

CREATE TABLE IF NOT EXISTS findings (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    source         TEXT    NOT NULL,
    world          TEXT    NOT NULL,
    min_x          INTEGER NOT NULL,
    min_y          INTEGER NOT NULL,
    min_z          INTEGER NOT NULL,
    max_x          INTEGER NOT NULL,
    max_y          INTEGER NOT NULL,
    max_z          INTEGER NOT NULL,
    score          REAL    NOT NULL,
    votes          INTEGER NOT NULL,
    players        TEXT    NOT NULL,
    detail         TEXT    NOT NULL,
    model_version  TEXT    NOT NULL,
    preview_width  INTEGER NOT NULL,
    preview_height INTEGER NOT NULL,
    preview        BLOB    NOT NULL,
    created_at     INTEGER NOT NULL,
    verdict        TEXT,
    reviewer       TEXT,
    reviewed_at    INTEGER
);

CREATE INDEX IF NOT EXISTS findings_by_world_time ON findings (world, created_at);

CREATE INDEX IF NOT EXISTS findings_by_verdict ON findings (verdict, created_at);

CREATE TABLE IF NOT EXISTS scan_jobs (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    world       TEXT    NOT NULL,
    cause       TEXT    NOT NULL,
    status      TEXT    NOT NULL,
    tiles_total INTEGER NOT NULL DEFAULT 0,
    tiles_done  INTEGER NOT NULL DEFAULT 0,
    findings    INTEGER NOT NULL DEFAULT 0,
    failures    INTEGER NOT NULL DEFAULT 0,
    created_at  INTEGER NOT NULL,
    finished_at INTEGER
);

CREATE INDEX IF NOT EXISTS scan_jobs_by_status ON scan_jobs (status, id);

CREATE TABLE IF NOT EXISTS scan_tiles (
    job_id   INTEGER NOT NULL REFERENCES scan_jobs (id) ON DELETE CASCADE,
    origin_x INTEGER NOT NULL,
    origin_z INTEGER NOT NULL,
    done     INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (job_id, origin_x, origin_z)
) WITHOUT ROWID;

CREATE TABLE IF NOT EXISTS daily_scans (
    day TEXT PRIMARY KEY
) WITHOUT ROWID
