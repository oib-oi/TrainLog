package ru.trainlog.exercise;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "exercise")
public class Exercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id")
    private Long accountId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "muscle_group", nullable = false, length = 20)
    private MuscleGroup muscleGroup;

    @Column(length = 500)
    private String description;

    protected Exercise() {
    }

    public Exercise(Long accountId, String name, MuscleGroup muscleGroup, String description) {
        this.accountId = accountId;
        update(name, muscleGroup, description);
    }

    public void update(String name, MuscleGroup muscleGroup, String description) {
        this.name = name;
        this.muscleGroup = muscleGroup;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public boolean isCommon() {
        return accountId == null;
    }

    public String getName() {
        return name;
    }

    public MuscleGroup getMuscleGroup() {
        return muscleGroup;
    }

    public String getDescription() {
        return description;
    }
}
