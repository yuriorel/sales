package com.example.sales.common.error;

/// A requested resource does not exist (HTTP 404).
public final class NotFoundException extends SalesException {

    private final String resource;
    private final Object id;

    /// Uses a flexible constructor body: fields are assigned before `super(...)`.
    public NotFoundException(String resource, Object id) {
        this.resource = resource;
        this.id = id;
        super("%s '%s' was not found".formatted(resource, id));
    }

    public String resource() {
        return resource;
    }

    public Object id() {
        return id;
    }
}
