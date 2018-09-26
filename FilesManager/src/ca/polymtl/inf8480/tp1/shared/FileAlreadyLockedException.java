package ca.polymtl.inf8480.tp1.shared;

public class FileAlreadyLockedException extends Exception {
    public FileAlreadyLockedException() { super(); }
    public FileAlreadyLockedException(String message) { super(message); }
}