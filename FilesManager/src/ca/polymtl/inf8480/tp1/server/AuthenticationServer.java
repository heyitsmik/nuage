package ca.polymtl.inf8480.tp1.server;

import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.security.DigestInputStream;

import ca.polymtl.inf8480.tp1.shared.AuthenticationServerInterface;

public class AuthenticationServer implements AuthenticationServerInterface {

	private final String SERVER_FILE_PATH = "server_files/";
	private final String ALL_CREDENTIALS = "all_credentials.txt";

	public static void main(String[] args) {
		AuthenticationServer server = new AuthenticationServer();
		server.run();
	}

	public AuthenticationServer() {
		super();
	}

	private void run() {
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}

		try {
			AuthenticationServerInterface stub = (AuthenticationServerInterface) UnicastRemoteObject.exportObject(this, 0);

			Registry registry = LocateRegistry.getRegistry();
			registry.rebind("authentication_server", stub);
			System.out.println("AuthenticationServer ready.");
		} catch (ConnectException e) {
			System.err.println("Impossible de se connecter au registre RMI. Est-ce que rmiregistry est lancé ?");
			System.err.println();
			System.err.println("Erreur: " + e.getMessage());
		} catch (Exception e) {
			System.err.println("Erreur: " + e.getMessage());
		}
	}

	@Override
	public boolean newUser(String newLogin, String newPassword) throws RemoteException {
		try {
			// Create or retrieve the credentials file
			File credentials = new File(this.SERVER_FILE_PATH + this.ALL_CREDENTIALS);
			credentials.createNewFile();
			
			BufferedReader bufferedReader = new BufferedReader(new FileReader(credentials));
			String credential;

			// Ensure there isn't a user with the same credentials
			while ((credential = bufferedReader.readLine()) != null) {
				String[] parts = credential.split(" ");
				String login = parts[0];

				if (newLogin.equals(login)) {
					return false;
				}
			}

			bufferedReader.close();

			// If it is a new user indeed, append the credentials to the end of the file
			BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(credentials, true));
			bufferedWriter.write(newLogin + " " + newPassword);
			bufferedWriter.newLine();
			bufferedWriter.close();

			return true;
		} catch (Exception e) {
			System.err.println("Erreur: " + e.getMessage());
			return false;
		}
	}

	@Override
	public boolean verify(String login, String password) {
		try {
			// Retrieve the credentials file
			File credentials = new File(this.SERVER_FILE_PATH + this.ALL_CREDENTIALS);

			BufferedReader bufferedReader = new BufferedReader(new FileReader(credentials));
			String credential;

			// Iterate through every entry of the credentials file to make the verification
			while ((credential = bufferedReader.readLine()) != null) {
				String[] parts = credential.split(" ");
				String readLogin = parts[0];
				String readPassword = parts[1];

				if (readLogin.equals(login) && readPassword.equals(password)){
					bufferedReader.close();
					return true;
				}
			}

			bufferedReader.close();
			return false;
		} catch (Exception e) {
			System.err.println("Erreur: " + e.getMessage());
			return false;
		}
	}
}
