package de.kylekreuter.vistructum.core.sidecar;

public final class SidecarException extends RuntimeException {

    private final int status;
    private final String body;

    public SidecarException(int status, String body) {
        super("sidecar responded " + status + ": " + body);
        this.status = status;
        this.body = body;
    }

    public int status() {
        return status;
    }

    public String body() {
        return body;
    }
}
