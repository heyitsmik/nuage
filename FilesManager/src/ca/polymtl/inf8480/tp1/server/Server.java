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

	static final String SERVER_FILE_PATH = "server_files/";
	static final String LOCKS_FILE = "locks.txt";

	private AuthenticationServerInterface authServerStub = null;
	private HashMap<String, String> locks = new HashMap<>();

	public static void main(String[] args) {
		Server server = new Server();
		server.run();
	}

	public Server() {
		super();
		this.authServerStub = loadAuthServerStub("127.0.0.1");
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
			boolean locksFileCreated = locksFile.createNewFile();
	
			// Retrieve locks if they exist
			// TODO what to do if the user delete a file?
			if (!locksFileCreated) {
				BufferedReader bufferedReader = new BufferedReader(new FileReader(locksFile));
				String lock;
	
				while ((lock = bufferedReader.readLine()) != null) {
					String[] parts = lock.split(" ");
					String file = parts[0];
					String user = parts.length == 2 ? parts[1] : null;
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
			if (newFile.createNewFile()) {
				this.locks.put(name, null);
			    this.updateLocksFile();
			}
			else {
				throw new FileAlreadyExistsException("Le fichier " + name + " existe deja");
			}
		} catch (IOException e) {
			System.out.println("Erreur: " + e.getMessage());
		}
	}

	@Override
	public String list(String login, String password) throws AuthenticationException, RemoteException {
		// Authentify the client
		this.authentifyUser(login, password);

		String list = "";
		for (Map.Entry<String, String> entry : this.locks.entrySet()) {
			list += "* " + entry.getKey();
			String lockUser = entry.getValue();
			list += (lockUser == null ? "   non verrouille\n" : "   verrouille par " + lockUser + "\n");
		}
		list += this.locks.size() + " fichier(s)";
		return list;
	}

	@Override
	public HashMap<String, byte[]> syncLocalDirectory(String login, String password) throws AuthenticationException, RemoteException {
		// Authentify the client
		this.authentifyUser(login, password);

		HashMap<String, byte[]> files = new HashMap<>();
		try {
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
		// Authentify the client
		this.authentifyUser(login, password);

		// Verify if the file exists
		if (!(new File(this.SERVER_FILE_PATH + name).exists())) {
			throw new UnknownFileException("Le fichier " + name + " n'existe pas.");
		}

		// Return the file if: 1) the client does not have it locally OR
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
		// Authentify the client
		this.authentifyUser(login, password);
		
		// Verify if the file exists
		if (!(new File(this.SERVER_FILE_PATH + name).exists())) {
			throw new UnknownFileException("Le fichier " + name + " n'existe pas.");
		}

		// Verify if the file is already locked
		String user = this.locks.get(name);
		if (user != null) {
			throw new FileAlreadyLockedException("Le fichier " + name + " est deja verrouille par " + user + ".");
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
		// Authentify the client
		this.authentifyUser(login, password);
  
		String user = this.locks.get(name);
		if (user == null) {
			throw new FileNotLockedException("Operation refusee: Le fichier " + name + " n'est pas verrouille.");
		}
		else if (!user.equals(login)) {
			throw new FileAlreadyLockedException("Operation refusee: Le fichier " + name + " est verrouille par " + user);
		}
		else {
			this.synchronizeFile(name, fileContent);
			this.locks.put(name, null);
			this.updateLocksFile();
		}
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

	private void synchronizeFile(String name, byte[] synchronizedFile) {
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
			Path filePath = Paths.get(this.SERVER_FILE_PATH + this.LOCKS_FILE);
			Files.write(filePath, content, StandardCharsets.UTF_8);
		} catch(IOException e) {
			System.err.println("Erreur: " + e.getMessage());
		}
	}
}
