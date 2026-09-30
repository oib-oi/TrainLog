package ru.trainlog.workout;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "workout")
public class Workout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "performed_on", nullable = false)
    private LocalDate performedOn;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String notes;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    protected Workout() {
    }

    public Workout(Long accountId, LocalDate performedOn, String title, String notes, Integer durationMinutes) {
        this.accountId = accountId;
        update(performedOn, title, notes, durationMinutes);
    }

    public void update(LocalDate performedOn, String title, String notes, Integer durationMinutes) {
        this.performedOn = performedOn;
        this.title = title;
        this.notes = notes;
        this.durationMinutes = durationMinutes;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getPerformedOn() {
        return performedOn;
    }

    public String getTitle() {
        return title;
    }

    public String getNotes() {
        return notes;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }
}
