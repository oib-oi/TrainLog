package ru.trainlog.common;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String entity, Long id) {
        super("Не найдено: %s #%d".formatted(entity, id));
    }
}
