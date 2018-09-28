package ca.polymtl.inf8480.tp1.client;

import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Map;
import java.util.HashMap;

import ca.polymtl.inf8480.tp1.shared.AuthenticationException;
import ca.polymtl.inf8480.tp1.shared.FileAlreadyExistsException;
import ca.polymtl.inf8480.tp1.shared.FileNotLockedException;
import ca.polymtl.inf8480.tp1.shared.FileAlreadyLockedException;
import ca.polymtl.inf8480.tp1.shared.UnknownFileException;
import ca.polymtl.inf8480.tp1.shared.ServerInterface;
import ca.polymtl.inf8480.tp1.shared.AuthenticationServerInterface;

public class Client {

	static final String LOCAL_CREDENTIAL = "local_credential.txt";
	static final String LOCAL_FILE_PATH = "local_files/";

	private String login = null;
	private String password = null;
	private ServerInterface serverStub = null;
	private AuthenticationServerInterface authServerStub = null;

	public static void main(String[] args) {
		String method = null;
		String parameter = null;

		if (args.length > 0) {
			method = args[0];
			if (args.length > 1 ) {
				parameter = args[1];
			}
		}

		Client client = new Client();
	}

	public Client() {
		super();

		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}

		String serverHostname = "127.0.0.1";
		this.serverStub = loadServerStub(serverHostname);
		this.authServerStub = loadAuthServerStub(serverHostname);
	}

	private void run() {
		// Create local files repository if it does not exist
		File repository = new File(this.LOCAL_FILE_PATH);
		repository.mkdir();
		
		authenticateUser();

		if (serverStub != null) {
			this.push("test");
		}


	}

	private ServerInterface loadServerStub(String hostname) {
		ServerInterface stub = null;

		try {
			Registry registry = LocateRegistry.getRegistry(hostname);
			stub = (ServerInterface) registry.lookup("server");
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

	private void authenticateUser() {
		try {
			File localCredential = new File(this.LOCAL_FILE_PATH + this.LOCAL_CREDENTIAL);

			if (localCredential.createNewFile()) {
				BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
				
				System.out.print("Entrez votre nom d'utilisateur: ");
				this.login = bufferedReader.readLine();
		
				System.out.print("Entrez votre mot de passe: ");
				this.password = bufferedReader.readLine();
		
				bufferedReader.close();

				BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(localCredential, true));
				bufferedWriter.write(this.login + " " + this.password);
				bufferedWriter.close();

			    this.authServerStub.newUser(this.login, this.password);
			} else {
				BufferedReader bufferedReader = new BufferedReader(new FileReader(localCredential));
				String credential = bufferedReader.readLine();
				bufferedReader.close();

				String[] parts = credential.split(" ");
				this.login = parts[0];
				this.password = parts[1];
			}

		} catch (Exception e) {
			System.err.println("Erreur: " + e.getMessage());
		}
	}

	private void newUser(String login, String password) {
		try {
			this.authServerStub.newUser(login, password);
		} catch (RemoteException e) {
			System.out.println("Erreur: " + e.getMessage());
		}
	}

	private void create(String name) {
		try {
			this.serverStub.create(name, this.login, this.password);
			File newFile = new File(this.LOCAL_FILE_PATH + name);
			newFile.createNewFile();
			System.out.println(name + " ajoute."); // TODO accents!
		} catch (IOException e) {
			System.out.println("Erreur: " + e.getMessage());
		} catch (AuthenticationException | FileAlreadyExistsException e) {
			System.out.println(e.getMessage());
		}
	}

	private void list() {
		try {
			String list = this.serverStub.list(this.login, this.password);
			System.out.println(list);
		} catch (RemoteException e) {
			System.out.println("Erreur: " + e.getMessage());
		} catch (AuthenticationException e) {
			System.out.println(e.getMessage());
		}
	}

	private void syncLocalDirectory() {
		try {
			HashMap<String, byte[]> syncedFiles = this.serverStub.syncLocalDirectory(this.login, this.password);
			for (Map.Entry<String, byte[]> entry : syncedFiles.entrySet()) {
				String fileName = entry.getKey();
				byte[] fileContent = entry.getValue();
				File file = new File(this.LOCAL_FILE_PATH + fileName);
				file.delete(); // TODO check if necessary
				try (FileOutputStream fos = new FileOutputStream(this.LOCAL_FILE_PATH + fileName)) {
					fos.write(fileContent);
				}
			}
		} catch (RemoteException e) {
			System.out.println("Erreur: " + e.getMessage());
		} catch (AuthenticationException e) {
			System.out.println(e.getMessage());
		} catch (Exception e) {
			System.out.println("Erreur: " + e.getMessage());
		}
	}

	private void get(String name) {
		try {
			// If the file does not exists, checksum = null
			byte[] localChecksum = new File(this.LOCAL_FILE_PATH + name).exists() ? this.getChecksum(name) : null;
			byte[] synchronizedFile = this.serverStub.get(name, localChecksum, this.login, this.password);

			// Synchronize the user's file if necessary
			if (synchronizedFile != null) {
				this.synchronizeFile(name, synchronizedFile);
			}

			System.out.println(name + " synchronise");
		} catch (AuthenticationException | UnknownFileException e) {
			System.out.println(e.getMessage());
		} catch (RemoteException e) {
			System.out.println("Erreur: " + e.getMessage());
		} 
	}

	private void lock(String name) {
		try {
            // If the file does not exists, checksum = null
			byte[] localChecksum = new File(this.LOCAL_FILE_PATH + name).exists() ? this.getChecksum(name) : null;
			byte[] synchronizedFile = this.serverStub.lock(name, localChecksum, this.login, this.password);

			// Update the user's newly locked file with its most recent version
			if (synchronizedFile != null) {
				this.synchronizeFile(name, synchronizedFile);
			}

			System.out.println(name + " verouille");
		} catch (AuthenticationException | UnknownFileException | FileAlreadyLockedException e) {
			System.out.println(e.getMessage());
		} catch (Exception e) {
			System.out.println("Erreur: " + e.getMessage());
		} 
	}

	private void push(String name) {
		try {
			if (!new File(this.LOCAL_FILE_PATH + name).exists()) {
				System.out.println("Le fichier " + name + " n'existe pas");
				return;
			}
			byte[] fileBytes = Files.readAllBytes(Paths.get(this.LOCAL_FILE_PATH + name));
			this.serverStub.push(name, fileBytes, this.login, this.password);
			System.out.println(name + " a été envoyé au serveur");
		} catch (AuthenticationException | FileNotLockedException | FileAlreadyLockedException e) {
			System.out.println(e.getMessage());
		} catch (Exception e) {
			System.out.println("Erreur: " + e.getMessage());
		} 
		
	}

	private byte[] getChecksum(String fileName) {
		try {
			byte[] fileBytes = Files.readAllBytes(Paths.get(this.LOCAL_FILE_PATH + fileName));
			return MessageDigest.getInstance("MD5").digest(fileBytes); 
		} catch (Exception e) {
			System.out.println("Erreur: " + e.getMessage());
			return null;
		}
	}

	private void synchronizeFile(String name, byte[] synchronizedFile) {
		File oldFile = new File(this.LOCAL_FILE_PATH + name);
		oldFile.delete();
		try (FileOutputStream fos = new FileOutputStream(this.LOCAL_FILE_PATH + name)) {
			fos.write(synchronizedFile);
		} catch (Exception e) {
			System.out.println("Erreur: " + e.getMessage());
		}
	}
}
