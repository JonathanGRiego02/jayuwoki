package dad.database;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import dad.utils.Utils;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DBManager {

    // Attributes
    private Firestore db;
    private MessageReceivedEvent event;
    private Member discordUser;
    private StringProperty currentServer = new SimpleStringProperty();
    private BooleanProperty openPermissions = new SimpleBooleanProperty();

    public DBManager() {
        try {
            // Cargar el archivo JSON desde la carpeta resources
            InputStream serviceAccount = DBManager.class.getClassLoader()
                    .getResourceAsStream("jayuwokidb-firebase-adminsdk.json");

            if (serviceAccount == null) {
                throw new RuntimeException("Archivo de credenciales no encontrado en resources");
            }

            // Configura Firebase
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setDatabaseUrl("https://JayuwokiDB.firebaseio.com") // Reemplaza con tu URL
                    .build();

            FirebaseApp.initializeApp(options);

            // Obtener una instancia de Firestore
            db = FirestoreClient.getFirestore();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<Player> GetPlayers(String[] nombres, MessageReceivedEvent event) {
        List<Player> players = new ArrayList<>();
        CollectionReference playersCollection = db.collection(currentServer.get()).document("Privadita").collection("Players");

        try {
            // Buscar los jugadores en la base de datos usando whereIn
            QuerySnapshot querySnapshot = playersCollection
                    .whereIn("name", Arrays.asList(nombres))
                    .get()
                    .get();

            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Player player = doc.toObject(Player.class);
                if (player != null) {
                    players.add(player);
                }
            }

            // Verificar si faltan jugadores
            List<String> jugadoresEncontrados = players.stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());

            List<String> jugadoresNoEncontrados = Arrays.stream(nombres)
                    .filter(nombre -> !jugadoresEncontrados.contains(nombre))
                    .collect(Collectors.toList());

            if (!jugadoresNoEncontrados.isEmpty()) {
                StringBuilder message = new StringBuilder("Los siguientes jugadores no están en la base de datos:\n");
                for (String nombre : jugadoresNoEncontrados) {
                    message.append("- ").append(nombre).append("\n");
                }
                event.getChannel().sendMessage(message.toString().trim()).queue();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return players;
    }

    // updates the data from the players list in the database
    public void updatePlayers(String server, ObservableList<Player> players) {
        System.out.println("Actualizando jugadores en el servidor: " + server);

        CollectionReference playersCollection = db.collection(server).document("Privadita").collection("Players");

        WriteBatch batch = db.batch();

        for (Player player : players) {
            System.out.println("Actualizando jugador: " + player.getName() + " con Elo " + player.getElo());

            DocumentReference playerDoc = playersCollection.document(player.getName());
            batch.set(playerDoc, player);
        }

        try {
            batch.commit().get();  // Se espera a que termine la operación
            System.out.println("Jugadores actualizados correctamente.");
        } catch (Exception e) {
            System.out.println("Error al actualizar jugadores: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public void AddPlayer(Player newPlayer) {
        try {
            openPermissions.set(Boolean.parseBoolean(Utils.properties.getProperty("massPermissionCheck")));
            if (CheckPermissions(openPermissions.get())) {
                if (!CheckPlayerFound(newPlayer)) {
                    event.getChannel().sendMessage("El jugador ya está en la base de datos").queue();
                    return;
                }
                // Parce the name of the server to remove special characters and spaces
                CollectionReference playersCollection = db.collection(currentServer.get())
                        .document("Privadita")
                        .collection("Players");

                playersCollection.document(newPlayer.getName()).set(newPlayer);
                event.getChannel().sendMessage("Jugador " + newPlayer.getName() + " añadido a la base de datos").queue();
            }
        } catch(Exception e){
            e.printStackTrace();
        }
    }


    public void AddPlayers(List<Player> newPlayers) {
        openPermissions.set(Boolean.parseBoolean(Utils.properties.getProperty("massPermissionCheck")));
        if (CheckPermissions(openPermissions.get())) {
            try {
                // Obtener los jugadores que no están en la base de datos
                List<Player> playersNotInDB = GetPlayersNotFound(newPlayers);

                // Delete the players that are already in the database
                List<Player> playersAlreadyInDB = newPlayers.stream()
                        .filter(player -> !playersNotInDB.contains(player))
                        .collect(Collectors.toList());

                // Show the players that are already in the database
                if (!playersAlreadyInDB.isEmpty()) {
                    StringBuilder message = new StringBuilder("Los siguientes jugadores ya están en la base de datos:\n");
                    for (Player player : playersAlreadyInDB) {
                        System.out.println("- " + player.getName());
                        message.append("- ").append(player.getName()).append("\n");
                    }
                    event.getChannel().sendMessage(message.toString().trim()).queue();
                }

                // Message if everyone is already in the database
                if (playersNotInDB.isEmpty()) {
                    event.getChannel().sendMessage("Todos los jugadores ya están en la base de datos. No se añaden nuevos jugadores.").queue();
                    return;
                }

                // Message if there are players to add and add them
                StringBuilder addMessage = new StringBuilder("Los siguientes jugadores se van a añadir a la base de datos:\n");
                CollectionReference playersCollection = db.collection(currentServer.get()).document("Privadita").collection("Players");
                WriteBatch batch = db.batch();

                for (Player player : playersNotInDB) {
                    DocumentReference playerDoc = playersCollection.document(player.getName());
                    batch.set(playerDoc, player);
                    addMessage.append("- ").append(player.getName()).append("\n");
                }

                batch.commit().get();

                event.getChannel().sendMessage(addMessage.toString().trim()).queue();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void DeletePlayer(String name) {
        openPermissions.set(Boolean.parseBoolean(Utils.properties.getProperty("massPermissionCheck")));
        if (CheckPermissions(openPermissions.get())) {
            CollectionReference playersCollection = db.collection(currentServer.get()).document("Privadita").collection("Players");

            try {
                // Get the player from the database
                DocumentSnapshot playerDoc = playersCollection.document(name).get().get();

                if (playerDoc.exists()) {
                    playersCollection.document(name).delete();
                    event.getChannel().sendMessage("El jugador ha sido eliminado de la base de datos").queue();
                } else {
                    event.getChannel().sendMessage("El jugador no está en la base de datos").queue();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void ShowPlayerElo(String name) {
        CollectionReference playersCollection = db.collection(currentServer.get()).document("Privadita").collection("Players");

        try {
            // Get the player from the database
            DocumentSnapshot playerDoc = playersCollection.document(name).get().get();

            if (playerDoc.exists()) {
                Player player = playerDoc.toObject(Player.class);
                // Show all the player stats
                StringBuilder message = new StringBuilder("```");
                message.append(player.PrintStats()).append("```");
                event.getChannel().sendMessage(message.toString()).queue();
            } else {
                event.getChannel().sendMessage("El jugador no está en la base de datos").queue();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void ShowAllElo() {
        CollectionReference playersCollection = db.collection(currentServer.get()).document("Privadita").collection("Players");

        try {
            // Get all the players from the database
            QuerySnapshot querySnapshot = playersCollection.get().get();

            // Convert the documents to Player objects and sort them by Elo in descending order
            List<Player> players = querySnapshot.getDocuments().stream()
                    .map(doc -> doc.toObject(Player.class))
                    .sorted((p1, p2) -> Integer.compare(p2.getElo(), p1.getElo()))
                    .collect(Collectors.toList());

            // Show the stats of all the players
            StringBuilder message = new StringBuilder("```");
            for (Player player : players) {
                message.append(player.PrintStats()).append("\n");
            }
            message.append("```");

            event.getChannel().sendMessage(message.toString()).queue();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Check if the player is in the database (individual type)
    public boolean CheckPlayerFound(Player player) {

        CollectionReference playersCollection = db.collection(currentServer.get()).document("Privadita").collection("Players");

        try {
            // Create the query to search the player name
            QuerySnapshot querySnapshot = playersCollection
                    .whereEqualTo("name", player.getName())
                    .get()
                    .get();

            // Return true if the player is not in the database
            return querySnapshot.isEmpty();

        } catch (Exception e) {
            e.printStackTrace();
            return true; // Si hay un error, asumimos que el jugador no está en la base de datos
        }
    }

    // Check if the players are in the database (List type)
    public List<Player> GetPlayersNotFound(List<Player> players) {
        CollectionReference playersCollection = db.collection(currentServer.get()).document("Privadita").collection("Players");

        // Get the name of each player to search it
        List<String> playerNames = players.stream()
                .map(Player::getName)
                .collect(Collectors.toList());

        try {
            // Create the query to search the 10 player names
            QuerySnapshot querySnapshot = playersCollection
                    .whereIn("name", playerNames)
                    .get()
                    .get();

            // List of the player names found in the database
            List<String> foundPlayerNames = querySnapshot.getDocuments().stream()
                    .map(doc -> doc.getString("name"))
                    .collect(Collectors.toList());

            // Return the players that are not in the database
            return players.stream()
                    .filter(player -> !foundPlayerNames.contains(player.getName()))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            e.printStackTrace();
            return players; // Si hay un error, asumimos que ninguno está en la base de datos
        }
    }

    // Check if the user has administrator permissions, so he will be able to modify the database
    private boolean CheckPermissions(Boolean openPermissions) {
        if (openPermissions) {
            return true;
        } else if (discordUser.hasPermission(Permission.ADMINISTRATOR)) {
            return true;
        } else {
            event.getChannel().sendMessage("No tienes permisos para modificar la base de datos").queue();
            return false;
        }

    }

    public Firestore getDb() {
        return db;
    }

    public void setEvent(MessageReceivedEvent event) {
        this.event = event;
        this.discordUser = event.getMember();
    }

    public String getCurrentServer() {
        return currentServer.get();
    }

    public StringProperty currentServerProperty() {
        return currentServer;
    }

    public void setCurrentServer(String currentServer) {
        this.currentServer.set(currentServer);
    }

    public boolean isOpenPermissions() {
        return openPermissions.get();
    }

    public BooleanProperty openPermissionsProperty() {
        return openPermissions;
    }

    public void setOpenPermissions(boolean openPermissions) {
        this.openPermissions.set(openPermissions);
    }




    public static void main(String[] args) {
        DBManager dbManager = new DBManager();
        dbManager.setCurrentServer("Jayuwoki");

        // Lista de jugadores con 2000 de Elo
        ObservableList<Player> players = FXCollections.observableArrayList(
                new Player("Chris", 2000),
                new Player("Estucaquio", 2000),
                new Player("Frodo", 2000),
                new Player("Guamero", 2000),
                new Player("Jonathan", 2000),
                new Player("Jorge", 1000),
                new Player("Messi", 1000),
                new Player("Nuriel", 1000),
                new Player("Nestor", 1000),
                new Player("Nuha", 1000)
        );

        dbManager.updatePlayers(dbManager.getCurrentServer(), players);

    }
}
