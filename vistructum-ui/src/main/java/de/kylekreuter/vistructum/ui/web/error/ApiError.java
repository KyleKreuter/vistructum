package de.kylekreuter.vistructum.ui.web.error;

public final class ApiError extends RuntimeException {

    private final int status;

    private ApiError(int status, String code) {
        super(code, null, false, false);
        this.status = status;
    }

    public static ApiError unauthorized() {
        return new ApiError(401, "unauthorized");
    }

    public static ApiError forbidden() {
        return new ApiError(403, "forbidden");
    }

    public static ApiError csrf() {
        return new ApiError(403, "csrf");
    }

    public static ApiError notFound() {
        return new ApiError(404, "not_found");
    }

    public static ApiError badRequest() {
        return new ApiError(400, "bad_request");
    }

    public static ApiError notShareable() {
        return new ApiError(409, "not_shareable");
    }

    public static ApiError unavailable() {
        return new ApiError(503, "unavailable");
    }

    public int status() {
        return status;
    }

    public String code() {
        return getMessage();
    }
}
