package ca.polymtl.inf8480.tp1.shared;

import java.rmi.Remote;
import java.rmi.RemoteException;

import java.util.HashMap;

import ca.polymtl.inf8480.tp1.shared.AuthenticationException;
import ca.polymtl.inf8480.tp1.shared.FileAlreadyExistsException;
import ca.polymtl.inf8480.tp1.shared.FileAlreadyLockedException;
import ca.polymtl.inf8480.tp1.shared.UnknownFileException;

public interface ServerInterface extends Remote {
	void create(String name, String login, String password) throws AuthenticationException, FileAlreadyExistsException, RemoteException;
	
	byte[] lock(String name, byte[] localChecksum, String login, String password) throws
					AuthenticationException, UnknownFileException, FileAlreadyLockedException, RemoteException;
	// HashMap<String, String> list();
}
