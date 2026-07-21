package ru.practicum.main.service.exception;

public class ConditionsNotMetException extends RuntimeException {

    public ConditionsNotMetException(String message) {
        super(message);
    }
}