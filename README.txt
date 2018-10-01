Partie 1: 

- À partir de la racine du dossier "ResponseTime_Analyser", compiler le code avec la commande "ant".
- Aller dans le dossier "bin" nouvellement créé pour démarrer le registre RMI avec "rmiregistry &".
- Pour partir une instance locale du serveur:
    - Exécuter commande ./server.sh à partir de la racine du dossier "ResponseTime_Analyser".
- Pour partir une instance du serveur sur la VM:
    - Envoyer le code du serveur à la VM avec "scp -i (clé privée) nom_du_fichier ubuntu@ip-flottante:(répertoire destination)".
    - Se connecter à la VM avec "ssh -i (clé privée) ubuntu@ip-flottante".
- Partir le client avec la commande suivie de l'adresse ip pour appeler le serveur distant: ./client.sh adresse_ip 
    - Pour l'appel au serveur local, le client prendra l'adresse locale "127.0.0.1" par défaut. 

Partie 2:

- À partir de la racine du dossier "ResponseTime_Analyser", compiler le code avec la commande "ant".
- Ouvrir 3 fenêtres du terminal à partir de ce même répertoire.
- Dans le 1er terminal, aller dans le dossier "bin" et entrer la commande "rmiregistry &" afin de démarrer le registre RMI. Revenir ensuite au niveau plus haut (FilesManager).
- Dans le 2e terminal, lancer le serveur d'authentification en appelant "./authenticationServer.sh".
- Dans le 3e terminal, lancer le serveur de fichiers en appelant "./server.sh".
- De retour dans le 1er terminal, partir le client en entrant une des commandes suivantes (description dans l'énoncé du TP):
        ./client list  
        ./client create file_name 
        ./client syncLocalDirectory
        ./client get file_name
        ./client lock file_name 
        ./client push file_name
- Note: Si le client s'exécute pour la première fois, on vous demande d'entrer un nom d'utilisateur (login) suivi d'un mot de passe (password).