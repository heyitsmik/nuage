package ca.polymtl.inf8480.tp1.shared;

public class UnknownFileException extends Exception {
    public UnknownFileException() { super(); }
    public UnknownFileException(String message) { super(message); }
}