package ca.polymtl.inf8480.tp1.client;

import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import java.util.Arrays;
import java.lang.Integer;

import ca.polymtl.inf8480.tp1.shared.ServerInterface;

public class Client {
	public static void main(String[] args) {
		String distantHostname = null;
		int powerBytes = 0;

		if (args.length > 0) {
			distantHostname = args[0];

			if (args.length > 1) {
				powerBytes = Integer.parseInt(args[1]);
			}
		}

		Client client = new Client(distantHostname, powerBytes);
		client.run();
	}

	FakeServer localServer = null; // Pour tester la latence d'un appel de
									// fonction normal.
	private ServerInterface localServerStub = null;
	private ServerInterface distantServerStub = null;

	private byte[] table = null;

	public Client(String distantServerHostname, int powerBytes) {
		super();
		
		this.table = new byte[(int)Math.pow(10, powerBytes)];
		byte nb = 1;
		Arrays.fill(this.table, nb);

		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}

		localServer = new FakeServer();
		localServerStub = loadServerStub("127.0.0.1");

		if (distantServerHostname != null) {
			distantServerStub = loadServerStub(distantServerHostname);
		}
	}

	private void run() {
		appelNormal();

		if (localServerStub != null) {
			appelRMILocal();
		}

		if (distantServerStub != null) {
			appelRMIDistant();
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

	private void appelNormal() {
		long start = System.nanoTime();
		localServer.execute(this.table);
		long end = System.nanoTime();

		System.out.println("Temps écoulé appel normal: " + (end - start)
				+ " ns");
	}

	private void appelRMILocal() {
		try {
			long start = System.nanoTime();
			localServerStub.execute(this.table);
			long end = System.nanoTime();

			System.out.println("Temps écoulé appel RMI local: " + (end - start)
					+ " ns");
		} catch (RemoteException e) {
			System.out.println("Erreur: " + e.getMessage());
		}
	}

	private void appelRMIDistant() {
		try {
			long start = System.nanoTime();
			distantServerStub.execute(this.table);
			long end = System.nanoTime();

			System.out.println("Temps écoulé appel RMI distant: "
					+ (end - start) + " ns");
		} catch (RemoteException e) {
			System.out.println("Erreur: " + e.getMessage());
		}
	}
}
