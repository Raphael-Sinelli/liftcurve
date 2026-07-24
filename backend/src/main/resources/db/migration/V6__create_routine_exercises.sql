CREATE TABLE routine_exercises (
    id UUID PRIMARY KEY,
    routine_id UUID NOT NULL REFERENCES routines (id) ON DELETE CASCADE,
    exercise_id UUID NOT NULL REFERENCES exercises (id),
    order_index INT NOT NULL,
    planned_sets INT NOT NULL,
    planned_reps INT NOT NULL,
    planned_load_kg NUMERIC
);

CREATE INDEX idx_routine_exercises_routine_id ON routine_exercises (routine_id);
