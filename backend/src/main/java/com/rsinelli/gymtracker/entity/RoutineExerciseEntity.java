package com.rsinelli.gymtracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "routine_exercises")
public class RoutineExerciseEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "routine_id", nullable = false)
    private RoutineEntity routine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private ExerciseEntity exercise;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "planned_sets", nullable = false)
    private int plannedSets;

    @Column(name = "planned_reps", nullable = false)
    private int plannedReps;

    @Column(name = "planned_load_kg")
    private BigDecimal plannedLoadKg;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public RoutineEntity getRoutine() {
        return routine;
    }

    public void setRoutine(RoutineEntity routine) {
        this.routine = routine;
    }

    public ExerciseEntity getExercise() {
        return exercise;
    }

    public void setExercise(ExerciseEntity exercise) {
        this.exercise = exercise;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public int getPlannedSets() {
        return plannedSets;
    }

    public void setPlannedSets(int plannedSets) {
        this.plannedSets = plannedSets;
    }

    public int getPlannedReps() {
        return plannedReps;
    }

    public void setPlannedReps(int plannedReps) {
        this.plannedReps = plannedReps;
    }

    public BigDecimal getPlannedLoadKg() {
        return plannedLoadKg;
    }

    public void setPlannedLoadKg(BigDecimal plannedLoadKg) {
        this.plannedLoadKg = plannedLoadKg;
    }
}
