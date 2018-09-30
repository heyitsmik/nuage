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
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.util.HashMap;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;

import ca.polymtl.inf8480.tp1.shared.AuthenticationException;
import ca.polymtl.inf8480.tp1.shared.FileAlreadyExistsException;
import ca.polymtl.inf8480.tp1.shared.FileAlreadyLockedException;
import ca.polymtl.inf8480.tp1.shared.FileNotLockedException;
import ca.polymtl.inf8480.tp1.shared.UnknownFileException;
import ca.polymtl.inf8480.tp1.shared.ServerInterface;
import ca.polymtl.inf8480.tp1.shared.AuthenticationServerInterface;

public class Server implements ServerInterface {

	private final String LOCKS_FILE = "locks.txt";
	private final String SERVER_FILE_PATH = "server_files/";
	private final String SERVER_HOSTNAME = "127.0.0.1";

	private AuthenticationServerInterface authServerStub = null;
	private HashMap<String, String> locks = new HashMap<>();

	public static void main(String[] args) {
		Server server = new Server();
		server.run();
	}

	public Server() {
		super();
		this.authServerStub = loadAuthServerStub();
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
			System.err.println("Impossible de se connecter au registre RMI. Est-ce que rmiregistry est lancé ?");
			System.err.println();
			System.err.println("Erreur: " + e.getMessage());
		} catch (Exception e) {
			System.err.println("Erreur: " + e.getMessage());
		}

		// Create server files repository if it does not exist
		File repository = new File(SERVER_FILE_PATH);
		repository.mkdir();

		// Create file containing locks if it does not exist
		File locksFile = new File(this.SERVER_FILE_PATH + "locks.txt");
		boolean locksFileCreated = false;
		try {
			locksFileCreated = locksFile.createNewFile();
		} catch (IOException e) {
			System.err.println("Erreur: " + e.getMessage());
		}

		// Retrieve locks if they exist
		if (!locksFileCreated) {
			this.loadLocks(locksFile);
		}
	}

	private AuthenticationServerInterface loadAuthServerStub() {
		AuthenticationServerInterface stub = null;

		try {
			Registry registry = LocateRegistry.getRegistry(this.SERVER_HOSTNAME);
			stub = (AuthenticationServerInterface) registry.lookup("authentication_server");
		} catch (NotBoundException e) {
			System.out.println("Erreur: Le nom '" + e.getMessage() + "' n'est pas défini dans le registre.");
		} catch (AccessException e) {
			System.out.println("Erreur: " + e.getMessage());
		} catch (RemoteException e) {
			System.out.println("Erreur: " + e.getMessage());
		}

		return stub;
	}

	private void loadLocks(File locksFile) {
		try {
			BufferedReader bufferedReader = new BufferedReader(new FileReader(locksFile));
			String lock;

			while ((lock = bufferedReader.readLine()) != null) {
				String[] parts = lock.split(" ");
				String file = parts[0];
				String user = parts.length == 2 ? parts[1] : "";
				this.locks.put(file, user);
			}

			bufferedReader.close();
		} catch (IOException e) {
			System.err.println("Erreur: " + e.getMessage());
		}
	}

	@Override
	public void create(String name, String login, String password) throws AuthenticationException, FileAlreadyExistsException, RemoteException {
		this.authentifyUser(login, password);

		File newFile = new File(this.SERVER_FILE_PATH + name);
		try {
			if (newFile.createNewFile()) {
				this.locks.put(name, "");
			    this.updateLocksFile();
			}
			else {
				throw new FileAlreadyExistsException("Opération refusée: Le fichier \"" + name + "\" existe déjà sur le serveur.");
			}
		} catch (IOException e) {
			System.out.println("Erreur: " + e.getMessage());
		}
	}

	@Override
	public String list(String login, String password) throws AuthenticationException, RemoteException {
		this.authentifyUser(login, password);

		String list = "";
		// Iterate through the locks variable and build the string to display on console
		for (Map.Entry<String, String> entry : this.locks.entrySet()) {
			list += "* " + entry.getKey();
			String lockUser = entry.getValue();
			list += (lockUser == "" ? "   non verrouillé\n" : "   verrouillé par \"" + lockUser + "\"\n");
		}
		list += this.locks.size() + " fichier(s)";

		return list;
	}

	@Override
	public HashMap<String, byte[]> syncLocalDirectory(String login, String password) throws AuthenticationException, RemoteException {
		this.authentifyUser(login, password);

		HashMap<String, byte[]> files = new HashMap<>();
		try {
			// Read all bytes from files on the server
			for (Map.Entry<String, String> entry : this.locks.entrySet()) {
				String fileName = entry.getKey();
				byte[] fileContent = Files.readAllBytes(Paths.get(this.SERVER_FILE_PATH + fileName));
				files.put(fileName, fileContent);
			}
		} catch (IOException e) {
			System.out.println("Erreur: " + e.getMessage());
		}

		return files;
	}

	@Override
	public byte[] get(String name, byte[] localChecksum, String login, String password) throws AuthenticationException, UnknownFileException, RemoteException {
		this.authentifyUser(login, password);

		// Verify if the file exists
		if (!(new File(this.SERVER_FILE_PATH + name).exists())) {
			throw new UnknownFileException("Opération refusée: Le fichier \"" + name + "\" n'existe pas.");
		}

		// Return the file if: 1) the client does not have it locally (localChecksum = null) OR
		//					   2) the client's file version is outdated
		try {
			byte[] serverChecksum = this.getChecksum(name);
			if (localChecksum == null || !Arrays.equals(localChecksum, serverChecksum)) {
				// Return the most recent version of the file
				return Files.readAllBytes(Paths.get(this.SERVER_FILE_PATH + name));
			}
		} catch (IOException e) {
			System.out.println("Erreur: " + e.getMessage());
		}

		return null;
	}

	@Override
	public byte[] lock(String name, byte[] localChecksum, String login, String password) throws 
						AuthenticationException, UnknownFileException, FileAlreadyLockedException, RemoteException {
		this.authentifyUser(login, password);
		
		// Verify if the file exists
		if (!(new File(this.SERVER_FILE_PATH + name).exists())) {
			throw new UnknownFileException("Opération refusée: Le fichier \"" + name + "\" n'existe pas.");
		}

		// Verify if the file is already locked
		String user = this.locks.get(name);
		if (user != "") {
			throw new FileAlreadyLockedException("Opération refusée: Le fichier \"" + name + "\" est déjà verrouillé par \"" + user + "\".");
		}

		try {
			// Lock the file
			this.locks.put(name, login);
			this.updateLocksFile();
	
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

	@Override
	public void push(String name, byte[] fileContent, String login, String password) throws 
					 AuthenticationException, FileNotLockedException, FileAlreadyLockedException, RemoteException {
		this.authentifyUser(login, password);
  
		// Verify if the file is not locked by the user wanting to update
		String user = this.locks.get(name);
		if (user == "") {
			throw new FileNotLockedException("Opération refusée: Le fichier \"" + name + "\" n'est pas verrouillé.");
		}
		// Verify if the file is not locked by another user
		else if (!user.equals(login)) {
			throw new FileAlreadyLockedException("Operation refusee: Le fichier \"" + name + "\" est verrouillé par \"" + user + "\"");
		}
		else {
			this.synchronizeFile(name, fileContent);
			this.locks.put(name, ""); // Remove the lock given to the current user (doing the update)
			this.updateLocksFile();
		}
	}

	private void authentifyUser(String login, String password) throws AuthenticationException, RemoteException {
		if (!this.authServerStub.verify(login, password)) {
			throw new AuthenticationException("Opération refusée: L'utilisateur \"" + login + "\" n'a pas été authentifié.");
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

	private void synchronizeFile(String name, byte[] synchronizedFile) {
		// Delete and replace old file with the new content
		File oldFile = new File(this.SERVER_FILE_PATH + name);
		oldFile.delete();
		
		try (FileOutputStream fos = new FileOutputStream(this.SERVER_FILE_PATH + name)) {
			fos.write(synchronizedFile);
		} catch (Exception e) {
			System.out.println("Erreur: " + e.getMessage());
		}
	}

	private void updateLocksFile() {
		List<String> content = new ArrayList<>();

		for (Map.Entry<String, String> entry : this.locks.entrySet()) {
			String line = "";
			line = entry.getKey() + " " + entry.getValue();
			content.add(line);
		}

		try {
			// Replace the entire old content of the locks file
			Path filePath = Paths.get(this.SERVER_FILE_PATH + this.LOCKS_FILE);
			Files.write(filePath, content, StandardCharsets.UTF_8);
		} catch(IOException e) {
			System.err.println("Erreur: " + e.getMessage());
		}
	}
}
