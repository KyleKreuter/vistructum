DROP TABLE IF EXISTS block_changes;

CREATE TABLE IF NOT EXISTS block_events (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    world         TEXT    NOT NULL,
    x             INTEGER NOT NULL,
    y             INTEGER NOT NULL,
    z             INTEGER NOT NULL,
    player        TEXT    NOT NULL,
    player_name   TEXT    NOT NULL,
    kind          TEXT    NOT NULL CHECK (kind IN ('PLACE', 'BREAK')),
    block_data    TEXT    NOT NULL,
    previous_data TEXT    NOT NULL,
    changed_at    INTEGER NOT NULL,
    reported_at   INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS block_events_by_time ON block_events (changed_at);

CREATE INDEX IF NOT EXISTS block_events_by_position ON block_events (world, x, z, y);

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

CREATE TABLE IF NOT EXISTS finding_scenes (
    finding_id    INTEGER PRIMARY KEY REFERENCES findings (id) ON DELETE CASCADE,
    kind          TEXT    NOT NULL,
    width         INTEGER NOT NULL,
    height        INTEGER NOT NULL,
    window_top    INTEGER NOT NULL,
    window_left   INTEGER NOT NULL,
    window_bottom INTEGER NOT NULL,
    window_right  INTEGER NOT NULL,
    channels      BLOB    NOT NULL
);

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
) WITHOUT ROWID;

DROP TABLE IF EXISTS player_faces;

CREATE TABLE IF NOT EXISTS player_skins (
    player     TEXT    PRIMARY KEY,
    name       TEXT,
    png        BLOB,
    slim       INTEGER NOT NULL DEFAULT 0,
    fetched_at INTEGER NOT NULL
) WITHOUT ROWID;

CREATE TABLE IF NOT EXISTS finding_thumbnails (
    finding_id INTEGER PRIMARY KEY REFERENCES findings (id) ON DELETE CASCADE,
    width      INTEGER NOT NULL,
    height     INTEGER NOT NULL,
    pixels     BLOB    NOT NULL
);

CREATE TABLE IF NOT EXISTS finding_heatmaps (
    finding_id    INTEGER PRIMARY KEY REFERENCES findings (id) ON DELETE CASCADE,
    width         INTEGER NOT NULL,
    height        INTEGER NOT NULL,
    model_version TEXT    NOT NULL,
    heat          BLOB    NOT NULL
);

CREATE TABLE IF NOT EXISTS motion_chunks (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    player      TEXT    NOT NULL,
    player_name TEXT    NOT NULL,
    world       TEXT    NOT NULL,
    start_ms    INTEGER NOT NULL,
    end_ms      INTEGER NOT NULL,
    min_x       REAL    NOT NULL,
    min_y       REAL    NOT NULL,
    min_z       REAL    NOT NULL,
    max_x       REAL    NOT NULL,
    max_y       REAL    NOT NULL,
    max_z       REAL    NOT NULL,
    frames      BLOB    NOT NULL
);

CREATE INDEX IF NOT EXISTS motion_chunks_by_world_time ON motion_chunks (world, end_ms);

CREATE TABLE IF NOT EXISTS finding_evidence (
    finding_id INTEGER PRIMARY KEY REFERENCES findings (id) ON DELETE CASCADE,
    min_x      INTEGER NOT NULL,
    min_y      INTEGER NOT NULL,
    min_z      INTEGER NOT NULL,
    size_x     INTEGER NOT NULL,
    size_y     INTEGER NOT NULL,
    size_z     INTEGER NOT NULL,
    palette    TEXT    NOT NULL,
    cells      BLOB    NOT NULL,
    from_ms    INTEGER NOT NULL,
    to_ms      INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS finding_changes (
    finding_id  INTEGER NOT NULL REFERENCES findings (id) ON DELETE CASCADE,
    seq         INTEGER NOT NULL,
    x           INTEGER NOT NULL,
    y           INTEGER NOT NULL,
    z           INTEGER NOT NULL,
    player      TEXT    NOT NULL,
    player_name TEXT    NOT NULL,
    kind        TEXT    NOT NULL,
    block_data  TEXT    NOT NULL,
    changed_at  INTEGER NOT NULL,
    PRIMARY KEY (finding_id, seq)
) WITHOUT ROWID;

CREATE TABLE IF NOT EXISTS finding_recordings (
    finding_id  INTEGER NOT NULL REFERENCES findings (id) ON DELETE CASCADE,
    chunk_id    INTEGER NOT NULL,
    player      TEXT    NOT NULL,
    player_name TEXT    NOT NULL,
    start_ms    INTEGER NOT NULL,
    end_ms      INTEGER NOT NULL,
    frames      BLOB    NOT NULL,
    PRIMARY KEY (finding_id, chunk_id)
) WITHOUT ROWID;

CREATE TABLE IF NOT EXISTS web_logins (
    token_hash  TEXT    PRIMARY KEY,
    player      TEXT    NOT NULL,
    player_name TEXT    NOT NULL,
    expires_at  INTEGER NOT NULL
) WITHOUT ROWID;

CREATE TABLE IF NOT EXISTS web_sessions (
    token_hash  TEXT    PRIMARY KEY,
    player      TEXT    NOT NULL,
    player_name TEXT    NOT NULL,
    expires_at  INTEGER NOT NULL
) WITHOUT ROWID;

CREATE TABLE IF NOT EXISTS evidence_shares (
    finding_id   INTEGER PRIMARY KEY REFERENCES findings (id) ON DELETE CASCADE,
    token        TEXT    NOT NULL UNIQUE,
    active       INTEGER NOT NULL,
    shared_since INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS share_events (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    finding_id INTEGER NOT NULL REFERENCES findings (id) ON DELETE CASCADE,
    at         INTEGER NOT NULL,
    actor      TEXT    NOT NULL,
    kind       TEXT    NOT NULL
);

CREATE INDEX IF NOT EXISTS share_events_by_time ON share_events (at);

CREATE INDEX IF NOT EXISTS findings_by_review_time ON findings (reviewed_at);

CREATE TABLE IF NOT EXISTS finding_terrain (
    finding_id INTEGER PRIMARY KEY REFERENCES findings (id) ON DELETE CASCADE,
    min_x      INTEGER NOT NULL,
    min_y      INTEGER NOT NULL,
    min_z      INTEGER NOT NULL,
    size_x     INTEGER NOT NULL,
    size_y     INTEGER NOT NULL,
    size_z     INTEGER NOT NULL,
    palette    TEXT    NOT NULL,
    cells      BLOB    NOT NULL,
    scene_x    INTEGER,
    scene_z    INTEGER
);
