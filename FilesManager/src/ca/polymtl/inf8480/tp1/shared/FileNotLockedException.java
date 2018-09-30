package ca.polymtl.inf8480.tp1.shared;

public class FileNotLockedException extends Exception {
    public FileNotLockedException() { super(); }
    public FileNotLockedException(String message) { super(message); }
}