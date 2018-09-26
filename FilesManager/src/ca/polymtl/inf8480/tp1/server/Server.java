package ca.polymtl.inf8480.tp1.server;

import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.util.HashMap;
import java.util.Arrays;

import ca.polymtl.inf8480.tp1.shared.AuthenticationException;
import ca.polymtl.inf8480.tp1.shared.FileAlreadyExistsException;
import ca.polymtl.inf8480.tp1.shared.FileAlreadyLockedException;
import ca.polymtl.inf8480.tp1.shared.UnknownFileException;
import ca.polymtl.inf8480.tp1.shared.ServerInterface;
import ca.polymtl.inf8480.tp1.shared.AuthenticationServerInterface;

public class Server implements ServerInterface {

	static final String SERVER_FILE_PATH = "server_files/";

	private AuthenticationServerInterface authServerStub = null;
	private HashMap<String, String> locks = new HashMap<>();

	public static void main(String[] args) {
		Server server = new Server();
		server.run();
	}

	public Server() {
		super();
		this.authServerStub = loadAuthServerStub("127.0.0.1"); // TODO passer ladresse du serveur
	}

	private void run() {
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}

		try {
			ServerInterface stub = (ServerInterface) UnicastRemoteObject.exportObject(this, 0);
			Registry registry = LocateRegistry.getRegistry();
			registry.rebind("server", stub);
			System.out.println("Server ready.");
		} catch (ConnectException e) {
			System.err
					.println("Impossible de se connecter au registre RMI. Est-ce que rmiregistry est lancé ?");
			System.err.println();
			System.err.println("Erreur: " + e.getMessage());
		} catch (Exception e) {
			System.err.println("Erreur: " + e.getMessage());
		}

		try {
			// Create server files repository if it does not exist
			File repository = new File(SERVER_FILE_PATH);
			repository.mkdir();
	
			// Create file containing locks if it does not exist
			File locksFile = new File(this.SERVER_FILE_PATH + "locks.txt");
			boolean locksExist = locksFile.createNewFile();
	
			// Retrieve locks if they exist
			if (locksExist) {
				BufferedReader bufferedReader = new BufferedReader(new FileReader(locksFile));
				String lock;
	
				while ((lock = bufferedReader.readLine()) != null) {
					String[] parts = lock.split(" ");
					String file = parts[0];
					String user = parts[1];
					
					this.locks.put(file, user);
				}
	
				bufferedReader.close();
			}
		} catch (Exception e) {
			System.err.println("Erreur: " + e.getMessage());
		}
	}

	private AuthenticationServerInterface loadAuthServerStub(String hostname) {
		AuthenticationServerInterface stub = null;

		try {
			Registry registry = LocateRegistry.getRegistry(hostname);
			stub = (AuthenticationServerInterface) registry.lookup("authentication_server");
		} catch (NotBoundException e) {
			System.out.println("Erreur: Le nom '" + e.getMessage()
					+ "' n'est pas défini dans le registre.");
		} catch (AccessException e) {
			System.out.println("Erreur: " + e.getMessage());
		} catch (RemoteException e) {
			System.out.println("Erreur: " + e.getMessage());
		}

		return stub;
	}

	@Override
	public void create(String name, String login, String password) throws AuthenticationException, FileAlreadyExistsException, RemoteException {
		this.authentifyUser(login, password);
		File newFile = new File(this.SERVER_FILE_PATH + name);

		try {
			if (!newFile.createNewFile()) {
				throw new FileAlreadyExistsException("Le fichier " + name + " existe deja");
			}
		} catch (IOException e) {
			System.out.println("Erreur: " + e.getMessage());
		}
	}

	@Override
	public byte[] lock(String name, byte[] localChecksum, String login, String password) throws 
						AuthenticationException, UnknownFileException, FileAlreadyLockedException, RemoteException {
		this.authentifyUser(login, password);
		
		// Verify if the file exists
		if (!(new File(this.SERVER_FILE_PATH + name).exists())) {
			throw new UnknownFileException("Le fichier " + name + " n'existe pas.");
		}

		// Verify if the file is already locked
		String user = this.locks.get(name);
		if (user != null) {
			throw new FileAlreadyLockedException("Le fichier " + name + "est deja verrouille par " + user + ".");
		}

		try {
			// Lock the file
			this.locks.put(name, login);
			File locksFile = new File(this.SERVER_FILE_PATH + "locks.txt");
			BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(locksFile, true));
			bufferedWriter.write(name + " " + login);
			bufferedWriter.close();
	
			// Verify if the user has the most recent version of the file
			byte[] serverChecksum = this.getChecksum(name);
			if (!Arrays.equals(localChecksum, serverChecksum)) {
				// Return the most recent version of the file
				return Files.readAllBytes(Paths.get(this.SERVER_FILE_PATH + name));
			}
		} catch (IOException e) {
			System.err.println("Erreur: " + e.getMessage());
			return null;
		}

		return null;
	}

	private void authentifyUser(String login, String password) throws AuthenticationException, RemoteException {
		if (!this.authServerStub.verify(login, password)) {
			throw new AuthenticationException("Operation refusee: L'utilisateur " + login + " n'a pas pu etre authentifie");
		}
	}

	private byte[] getChecksum(String fileName) {
		try {
			byte[] fileBytes = Files.readAllBytes(Paths.get(this.SERVER_FILE_PATH + fileName));
			return MessageDigest.getInstance("MD5").digest(fileBytes); 
		} catch (Exception e) {
			System.out.println("Erreur: " + e.getMessage());
			return null;
		}
	}
}
