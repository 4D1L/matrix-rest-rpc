package com.example.grpc.shared;

public class MatrixException extends Exception {
    private static final long serialVersionUID = 1L;

    public MatrixException(String msg, Throwable err) {
        super(msg, err);
    }

    public MatrixException(String msg) {
        super(msg);
    }
}
