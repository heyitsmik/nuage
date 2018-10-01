package ca.polymtl.inf8480.tp1.client;

import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Map;
import java.util.HashMap;

import ca.polymtl.inf8480.tp1.shared.AuthenticationException;
import ca.polymtl.inf8480.tp1.shared.AuthenticationServerInterface;
import ca.polymtl.inf8480.tp1.shared.FileAlreadyExistsException;
import ca.polymtl.inf8480.tp1.shared.FileAlreadyLockedException;
import ca.polymtl.inf8480.tp1.shared.FileNotLockedException;
import ca.polymtl.inf8480.tp1.shared.UnknownFileException;
import ca.polymtl.inf8480.tp1.shared.ServerInterface;

public class Client {

	private final String LOCAL_CREDENTIAL = "local_credential.txt";
	private final String LOCAL_FILE_PATH = "local_files/";
	private final String SERVER_HOSTNAME = "127.0.0.1";

	private String login;
	private String password;
	private ServerInterface serverStub = null;
	private AuthenticationServerInterface authServerStub = null;

	public static void main(String[] args) {
		String method = "";
		String parameter = "";

		// ERROR HANDLING
		if (args.length > 0) {
			method = args[0];

			switch (method) {
				case "create" : 
					if (args.length == 2) {
						parameter = args[1];
						break;
					} else if (args.length == 1) {
						System.out.println("Opération refusée: Fichier attendu.");
						return;
					} else if (args.length > 1) {
						System.out.println("Opération refusée: Argument(s) en trop.");
						return;
					}
	
				case "list" : 
					if (args.length > 1) {
						System.out.println("Opération refusée: Argument(s) en trop.");
						return;
					} else {
						break;
					}
	
				case "syncLocalDirectory" :
					if (args.length > 1) {
						System.out.println("Opération refusée: Argument(s) en trop.");
						return;
					} else {
						break;
					}
	
				case "get" :
					if (args.length == 2) {
						parameter = args[1];
						break;
					} else if (args.length == 1) {
						System.out.println("Opération refusée: Fichier attendu.");
						return;
					} else if (args.length > 1) {
						System.out.println("Opération refusée: Argument(s) en trop.");
						return;
					}
	
				case "lock" :
					if (args.length == 2) {
						parameter = args[1];
						break;
					} else if (args.length == 1) {
						System.out.println("Opération refusée: Fichier attendu.");
						return;
					} else if (args.length > 1) {
						System.out.println("Opération refusée: Argument(s) en trop.");
						return;
					}
				
				case "push" :
					if (args.length == 2) {
						parameter = args[1];
						break;
					} else if (args.length == 1) {
						System.out.println("Opération refusée: Fichier attendu.");
						return;
					} else if (args.length > 1) {
						System.out.println("Opération refusée: Argument(s) en trop.");
						return;
					}
				
				default :
					System.out.println("Opération refusée: commande inconnue.");
					return;
			}

			Client client = new Client();
			client.run(method, parameter);

		} else {
			System.out.println("Opération refusée: aucune commande spécifiée.");
			return;
		}
	}

	public Client() {
		super();

		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}

		this.serverStub = loadServerStub();
		this.authServerStub = loadAuthServerStub();
	}

	private void run(String method, String parameter) {
		// Create local files repository if it does not exist
		File repository = new File(this.LOCAL_FILE_PATH);
		repository.mkdir();
		
		boolean isAuthenticated = authenticateUser();
		if (!isAuthenticated) {
			// Exit the program if the user cannot be authenticated
			return;
		}

		// Handle the client's command
		if (serverStub != null) {
        
			switch (method) {
				case "create" : 
					this.create(parameter);
					break;

				case "list" : 
					this.list();
					break;

				case "syncLocalDirectory" :
					this.syncLocalDirectory();
					break;

				case "get" :
					this.get(parameter);
					break;

				case "lock" :
					this.lock(parameter);
					break;
				
				case "push" :
					this.push(parameter);
					break;

				case "" :
					break;
				
				default :
					System.out.println("Commande invalide");
			}
		}
	}

	private ServerInterface loadServerStub() {
		ServerInterface stub = null;

		try {
			Registry registry = LocateRegistry.getRegistry(this.SERVER_HOSTNAME);
			stub = (ServerInterface) registry.lookup("server");
		} catch (NotBoundException e) {
			System.out.println("Erreur: Le nom '" + e.getMessage() + "' n'est pas défini dans le registre.");
		} catch (AccessException e) {
			System.out.println("Erreur: " + e.getMessage());
		} catch (RemoteException e) {
			System.out.println("Erreur: " + e.getMessage());
		}

		return stub;
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

	private boolean authenticateUser() {
		try {
			File localCredential = new File(this.LOCAL_FILE_PATH + this.LOCAL_CREDENTIAL);

			// Returns the length, in bytes, of the file denoted by this abstract pathname, or
			// if the file does not exist
			if (localCredential.length() == 0) {
				localCredential.createNewFile();

				// Recuperate the user's new login credentials
				BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
				
				System.out.print("Entrez votre nom d'utilisateur (1 mot): ");
				String[] loginInput = bufferedReader.readLine().split(" ");
				if (loginInput.length > 1) {
					System.out.println("Erreur: Le nom d'utilisateur peut seulement être 1 mot.");
					return false;
				}
				this.login = loginInput[0];
		
				System.out.print("Entrez votre mot de passe (1 mot): ");
				String[] passwordInput = bufferedReader.readLine().split(" ");
				if (passwordInput.length > 1) {
					System.out.println("Erreur: Le mot de passe peut seulement être 1 mot.");
					return false;
				}
				this.password = passwordInput[0];
		
				bufferedReader.close();
				
				// Inform the authentication server that a new user has been created
				boolean createdNewUser = this.authServerStub.newUser(this.login, this.password);

				if (createdNewUser) {
					// Add the new credentials to the local file
					BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(localCredential, true));
					bufferedWriter.write(this.login + " " + this.password);
					bufferedWriter.close();
					return true;
				} else {
					System.out.println("Erreur: " + "L'utilisateur \"" + this.login + "\" existe déjà.");
					return false;
				}
			} else {
				// Retrieve the user's credentials (previously saved in the file)
				BufferedReader bufferedReader = new BufferedReader(new FileReader(localCredential));
				String credential = bufferedReader.readLine();
				bufferedReader.close();

				String[] parts = credential.split(" ");
				this.login = parts[0];
				this.password = parts[1];
				return true;
			}
		} catch (Exception e) {
			System.err.println("Erreur: " + e.getMessage());
			return false;
		}
	}

	private void create(String name) {
		try {
			File newFile = new File(this.LOCAL_FILE_PATH + name);
			if (newFile.exists()) {
				System.out.println("Opération refusée: Le fichier \"" + name + "\" existe déjà localement.");
				return;
			}

			this.serverStub.create(name, this.login, this.password);

			newFile.createNewFile();
			System.out.println("\"" + name + "\" ajouté.");
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
			// Recuperate all files server side
			HashMap<String, byte[]> syncedFiles = this.serverStub.syncLocalDirectory(this.login, this.password);

			// Update all local files
			for (Map.Entry<String, byte[]> entry : syncedFiles.entrySet()) {
				String fileName = entry.getKey();
				byte[] fileContent = entry.getValue();

				File file = new File(this.LOCAL_FILE_PATH + fileName);
				file.delete();

				// Write new file content
				try (FileOutputStream fos = new FileOutputStream(this.LOCAL_FILE_PATH + fileName)) {
					fos.write(fileContent);
				}
			}
		} catch (AuthenticationException e) {
			System.out.println(e.getMessage());
		} catch (IOException e) {
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

			System.out.println("\"" + name + "\" synchronisé.");
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

			System.out.println("\"" + name + "\" verrouillé.");
		} catch (AuthenticationException | UnknownFileException | FileAlreadyLockedException e) {
			System.out.println(e.getMessage());
		} catch (IOException e) {
			System.out.println("Erreur: " + e.getMessage());
		} 
	}

	private void push(String name) {
		try {
			// Verify if the file exists locally
			if (!new File(this.LOCAL_FILE_PATH + name).exists()) {
				System.out.println("Opération refusée: Le fichier \"" + name + "\" n'existe pas.");
				return;
			}

			// Read the file and send it to the server for update
			byte[] fileBytes = Files.readAllBytes(Paths.get(this.LOCAL_FILE_PATH + name));
			this.serverStub.push(name, fileBytes, this.login, this.password);

			System.out.println("\"" + name + "\" a été envoyé au serveur.");
		} catch (AuthenticationException | FileNotLockedException | FileAlreadyLockedException e) {
			System.out.println(e.getMessage());
		} catch (IOException e) {
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
		// Delete and replace old file with the new content
		File oldFile = new File(this.LOCAL_FILE_PATH + name);
		oldFile.delete();

		try (FileOutputStream fos = new FileOutputStream(this.LOCAL_FILE_PATH + name)) {
			fos.write(synchronizedFile);
		} catch (IOException e) {
			System.out.println("Erreur: " + e.getMessage());
		}
	}
}
