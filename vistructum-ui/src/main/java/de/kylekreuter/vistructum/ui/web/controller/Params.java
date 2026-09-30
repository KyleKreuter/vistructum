package de.kylekreuter.vistructum.ui.web.controller;

import de.kylekreuter.vistructum.ui.web.error.ApiError;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

final class Params {

    static final int MAX_LIMIT = 100;

    private Params() {
    }

    static long id(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw ApiError.badRequest();
        }
    }

    static UUID uuid(String value) {
        try {
            UUID uuid = UUID.fromString(value);
            if (!uuid.toString().equalsIgnoreCase(value)) {
                throw ApiError.badRequest();
            }
            return uuid;
        } catch (IllegalArgumentException e) {
            throw ApiError.badRequest();
        }
    }

    static Instant instant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw ApiError.badRequest();
        }
    }

    static int limit(String value) {
        long limit = id(value);
        if (limit < 1 || limit > MAX_LIMIT) {
            throw ApiError.badRequest();
        }
        return (int) limit;
    }

    static int page(String value, int pageSize) {
        long page = id(value);
        if (page < 1 || (page - 1) * pageSize > Integer.MAX_VALUE) {
            throw ApiError.badRequest();
        }
        return (int) page;
    }
}
