package org.alloytools.alloy.grpc.util;

import io.grpc.stub.StreamObserver;

/**
 * Test utility class for capturing gRPC responses in tests.
 * This class implements StreamObserver to capture responses, errors, and completion status.
 */
public class TestStreamObserver<T> implements StreamObserver<T> {
    private T response;
    private Throwable error;
    private boolean completed = false;

    @Override
    public void onNext(T value) {
        this.response = value;
    }

    @Override
    public void onError(Throwable t) {
        this.error = t;
    }

    @Override
    public void onCompleted() {
        this.completed = true;
    }

    public boolean hasResponse() {
        return response != null;
    }

    public boolean hasError() {
        return error != null;
    }

    public boolean isCompleted() {
        return completed;
    }

    public T getResponse() {
        return response;
    }

    public Throwable getError() {
        return error;
    }
}
