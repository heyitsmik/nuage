package ca.polymtl.inf8480.tp1.shared;

public class FileAlreadyExistsException extends Exception {
    public FileAlreadyExistsException() { super(); }
    public FileAlreadyExistsException(String message) { super(message); }
}