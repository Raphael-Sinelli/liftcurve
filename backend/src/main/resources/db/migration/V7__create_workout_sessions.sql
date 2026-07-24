CREATE TABLE workout_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    routine_id UUID REFERENCES routines (id) ON DELETE SET NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    notes TEXT
);

CREATE INDEX idx_workout_sessions_user_id ON workout_sessions (user_id);
