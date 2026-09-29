package de.kylekreuter.vistructum.ui.web;

final class ApiError extends RuntimeException {

    private final int status;

    private ApiError(int status, String code) {
        super(code, null, false, false);
        this.status = status;
    }

    static ApiError unauthorized() {
        return new ApiError(401, "unauthorized");
    }

    static ApiError forbidden() {
        return new ApiError(403, "forbidden");
    }

    static ApiError csrf() {
        return new ApiError(403, "csrf");
    }

    static ApiError notFound() {
        return new ApiError(404, "not_found");
    }

    static ApiError badRequest() {
        return new ApiError(400, "bad_request");
    }

    static ApiError notShareable() {
        return new ApiError(409, "not_shareable");
    }

    static ApiError unavailable() {
        return new ApiError(503, "unavailable");
    }

    Reply reply() {
        return Reply.error(status, getMessage());
    }
}
