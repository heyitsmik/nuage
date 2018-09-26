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

import ca.polymtl.inf8480.tp1.shared.AuthenticationException;
import ca.polymtl.inf8480.tp1.shared.FileAlreadyExistsException;
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
		String distantHostname = null;

		if (args.length > 0) {
			distantHostname = args[0];
		}

		Client client = new Client(distantHostname);
		client.run();
	}

	public Client(String serverHostname) {
		super();

		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}

		serverHostname = serverHostname != null ? serverHostname : "127.0.0.1";
		this.serverStub = loadServerStub(serverHostname);
		this.authServerStub = loadAuthServerStub(serverHostname);
	}

	private void run() {
		// Create local files repository if it does not exist
		File repository = new File(this.LOCAL_FILE_PATH);
		repository.mkdir();
		
		authenticateUser();

		if (serverStub != null) {
			this.create("test");
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

	private void get(String fileName) {
		
	}

	private void lock(String name) {
		try {
			byte[] localChecksum = this.getChecksum(name);
			byte[] upToDateFile = this.serverStub.lock(name, localChecksum, this.login, this.password);

			// Update the user's newly locked file with its most recent version
			if (upToDateFile != null) {
				File lockedFile = new File(this.LOCAL_FILE_PATH + name);
				lockedFile.delete(); // TODO check if necessary
				try (FileOutputStream fos = new FileOutputStream(this.LOCAL_FILE_PATH + name)) {
					fos.write(upToDateFile);
				}
			}

			System.out.println(name + " verouille");
		} catch (AuthenticationException | UnknownFileException | FileAlreadyLockedException e) {
			System.out.println(e.getMessage());
		} catch (Exception e) {
			System.out.println("Erreur: " + e.getMessage());
		} 
	}

	private byte[] getChecksum(String fileName) {
		try {
			byte[] fileBytes = Files.readAllBytes(Paths.get(LOCAL_FILE_PATH + fileName));
			return MessageDigest.getInstance("MD5").digest(fileBytes); 
		} catch (Exception e) {
			System.out.println("Erreur: " + e.getMessage());
			return null;
		}
	}
}
