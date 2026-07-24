package com.rsinelli.gymtracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "session_sets")
public class SessionSetEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private WorkoutSessionEntity session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private ExerciseEntity exercise;

    @Column(name = "set_number", nullable = false)
    private int setNumber;

    @Column(name = "weight_kg", nullable = false)
    private BigDecimal weightKg;

    @Column(nullable = false)
    private int reps;

    @Column
    private BigDecimal rpe;

    @Column(name = "estimated_1rm_epley", nullable = false)
    private BigDecimal estimated1rmEpley;

    @Column(name = "estimated_1rm_brzycki")
    private BigDecimal estimated1rmBrzycki;

    @Column(name = "estimated_1rm_best", nullable = false)
    private BigDecimal estimated1rmBest;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public WorkoutSessionEntity getSession() {
        return session;
    }

    public void setSession(WorkoutSessionEntity session) {
        this.session = session;
    }

    public ExerciseEntity getExercise() {
        return exercise;
    }

    public void setExercise(ExerciseEntity exercise) {
        this.exercise = exercise;
    }

    public int getSetNumber() {
        return setNumber;
    }

    public void setSetNumber(int setNumber) {
        this.setNumber = setNumber;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public int getReps() {
        return reps;
    }

    public void setReps(int reps) {
        this.reps = reps;
    }

    public BigDecimal getRpe() {
        return rpe;
    }

    public void setRpe(BigDecimal rpe) {
        this.rpe = rpe;
    }

    public BigDecimal getEstimated1rmEpley() {
        return estimated1rmEpley;
    }

    public void setEstimated1rmEpley(BigDecimal estimated1rmEpley) {
        this.estimated1rmEpley = estimated1rmEpley;
    }

    public BigDecimal getEstimated1rmBrzycki() {
        return estimated1rmBrzycki;
    }

    public void setEstimated1rmBrzycki(BigDecimal estimated1rmBrzycki) {
        this.estimated1rmBrzycki = estimated1rmBrzycki;
    }

    public BigDecimal getEstimated1rmBest() {
        return estimated1rmBest;
    }

    public void setEstimated1rmBest(BigDecimal estimated1rmBest) {
        this.estimated1rmBest = estimated1rmBest;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
