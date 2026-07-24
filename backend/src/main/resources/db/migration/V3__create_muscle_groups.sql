CREATE TABLE muscle_groups (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

INSERT INTO muscle_groups (id, name) VALUES
    (gen_random_uuid(), 'Peito'),
    (gen_random_uuid(), 'Costas'),
    (gen_random_uuid(), 'Pernas'),
    (gen_random_uuid(), 'Ombros'),
    (gen_random_uuid(), 'Bíceps'),
    (gen_random_uuid(), 'Tríceps'),
    (gen_random_uuid(), 'Core'),
    (gen_random_uuid(), 'Glúteos'),
    (gen_random_uuid(), 'Panturrilha'),
    (gen_random_uuid(), 'Cardio/Outro');
