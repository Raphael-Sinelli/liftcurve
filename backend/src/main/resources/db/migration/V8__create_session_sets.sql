CREATE TABLE session_sets (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES workout_sessions (id) ON DELETE CASCADE,
    exercise_id UUID NOT NULL REFERENCES exercises (id),
    set_number INT NOT NULL,
    weight_kg NUMERIC NOT NULL,
    reps INT NOT NULL,
    rpe NUMERIC,
    estimated_1rm_epley NUMERIC NOT NULL,
    estimated_1rm_brzycki NUMERIC,
    estimated_1rm_best NUMERIC NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_session_sets_session_id ON session_sets (session_id);
CREATE INDEX idx_session_sets_exercise_id ON session_sets (exercise_id);
