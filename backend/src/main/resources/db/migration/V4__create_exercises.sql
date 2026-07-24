CREATE TABLE exercises (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    muscle_group_id UUID NOT NULL REFERENCES muscle_groups (id),
    owner_id UUID REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_exercises_owner_id ON exercises (owner_id);
CREATE INDEX idx_exercises_muscle_group_id ON exercises (muscle_group_id);
