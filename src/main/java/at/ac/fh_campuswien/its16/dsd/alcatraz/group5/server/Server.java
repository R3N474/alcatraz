/*
* IT Security Narmandakh Chuluunbaatar "AlcatrazServer"
*/

package at.ac.fh_campuswien.its16.dsd.alcatraz.group5.server;

import at.ac.fh_campuswien.its16.dsd.alcatraz.group5.client.IClient;
import spread.*;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.LinkedHashMap;
import java.util.Map;

public class Server implements IServer, BasicMessageListener {

    public static final String SERVER_GROUP = "ServerGroup";

    private static final int MESSAGE_TYPE_UPDATE = 10;
    private static final int MESSAGE_TYPE_SIGN_OUT = 20;
    private static final int MESSAGE_TYPE_START_GAME = 30;

    private final String serverId;
    private final Map<String, IClient> clients = new LinkedHashMap<String, IClient>(4);

    private final SpreadConnection connection;
    private final SpreadGroup group;

    public Server(String serverId, SpreadConnection connection, SpreadGroup group) {
        this.serverId = serverId;
        this.connection = connection;
        this.group = group;
    }

    /**
     * Send multicast message to all clients
     */
    public void multicast(IClient ref, int type) {
        SpreadMessage message = new SpreadMessage();

        message.addGroup(group);
        message.setCausal();                // Causal ordered
        message.setSafe();                    // Total ordered and virtual synchrony
        message.setType((short) type);

        try {
            System.out.printf("Sending spread multicast message type %d%n", type);
            message.setObject(ref);
            connection.multicast(message);
            System.out.println("Message sent");
        } catch (SpreadException e) {
            System.err.println("Error sending multicast message: " + e.getMessage());
        }
    }

    /**
     * Handle message from other servers (via spread)
     */
    public void messageReceived(SpreadMessage message) {
        IClient client;
        try {
            client = (IClient) message.getObject();
        } catch (SpreadException e) {
            System.err.println("Error while receiving spread message: " + e.getMessage());
            return;
        }

        switch (message.getType()) {
            case MESSAGE_TYPE_START_GAME:
                System.out.println("Received MESSAGE_TYPE_START_GAME");
                handleStartGameMessage();
                break;
            case MESSAGE_TYPE_UPDATE:
                System.out.println("Received MESSAGE_TYPE_UPDATE");
                handleUpdateMessage(client);
                break;
            case MESSAGE_TYPE_SIGN_OUT:
                System.out.println("Received MESSAGE_TYPE_SIGN_OUT");
                handleSignOutMessage(client);
                break;
            default:
                System.err.printf("Received unknown message type: %s%n", message.getType());
        }
    }

    /**
     * AS checks each name of client, because the name of the clients should be unique.
     * If the name is unique, AS sends an update to all server includes itself.
     */
    public void register(IClient ref) throws RemoteException {
        String name = ref.getName();
        if (clients.containsKey(name)) {
            throw new IllegalArgumentException(String.format("Name \"%s\" already in use.", name));
        }
        if (clients.size() >= 4) {
            throw new IllegalStateException("Game is full, please wait for the current game to be started.");
        }
        multicast(ref, MESSAGE_TYPE_UPDATE);
    }

    /**
     * Handle a spread message to update the client references on all clients
     */
    private void handleUpdateMessage(IClient client) {
        // Each Server gets the update and saves the reference of client
        // AlcatrazServer, which the update sent, starts the game when four players have already registered successfully.

        try {
            String name = client.getName();
            System.out.println("Updating client: " + client.getName());
            clients.put(name, client);
            client.saveServerRef(this);
        } catch (RemoteException e) {
            System.err.println("Error updating client: " + e.getMessage());
        }

        if (clients.size() >= 4) {
            // Start the game if 4 players already joined
            multicast(client, MESSAGE_TYPE_START_GAME);
        }
    }

    public void signOut(IClient ref) throws RemoteException {
        if (clients.containsKey(ref.getName())) {
            multicast(ref, MESSAGE_TYPE_SIGN_OUT);
        }
    }

    private void handleSignOutMessage(IClient msg) {
        try {
            String name = msg.getName();
            if (clients.containsKey(name)) {
                clients.remove(name);
                System.out.printf("Client %s signed out %n", name);
            } else {
                System.out.printf("Not such client: %s%n", name);
            }
        } catch (RemoteException e) {
            System.err.println("Error while trying to sign out a client: " + e.getMessage());
        }
    }

    /**
     * Register the client with the server
     * AlcatrazServer sends all client-references and the number of player to each client.
     */
    public void startAlcatraz(IClient ref) throws RemoteException {
        if (clients.size() <= 1) {
            throw new IllegalStateException("Need more than 1 player to start the game.");
        }

        multicast(ref, MESSAGE_TYPE_START_GAME);
    }

    /**
     * Handle a spread message to start the game
     */
    private void handleStartGameMessage() {
        try {
            IClient[] clientArray = new IClient[clients.size()];
            clients.values().toArray(clientArray);

            // Hand over all client references to each of the clients
            for (IClient c : clients.values()) {
                c.deliverReference(clientArray, clientArray.length);
            }

            // Clear client references on the server
            clients.clear();

        } catch (RemoteException e) {
            System.err.println("Error starting the game: " + e.getMessage());
        }
    }

    @Override
    public String toString() {
        return serverId;
    }

    public void connectAndJoinGroup() throws SpreadException {
        System.out.println("Registering server as spread message listener");
        this.connection.add(this);
        System.out.println("Connecting to spread daemon");
        this.connection.connect(null, 0, serverId, false, false);
        System.out.println("Joining spread group " + SERVER_GROUP);
        this.group.join(this.connection, SERVER_GROUP);
    }


    public static void main(String[] args) {
        Server server = new Server(args[0], new SpreadConnection(), new SpreadGroup());

        try {
            // Spread
            server.connectAndJoinGroup();
        } catch (SpreadException e) {
            System.err.println("Error joining spread group: " + e.getMessage());
            System.exit(3);
        }

        // Registry-Server saves the references of alcatrazServer. 
        // Default port of Registry-Server is 1099.
        try {
            registerWithRMI(server);
        } catch (RemoteException e) {
            System.err.println("Error creating/obtaining RMI Registry: " + e.getMessage());
            System.exit(4);
        }
    }

    private static void registerWithRMI(Server server) throws RemoteException {
        Registry registryServer;
        try {
            registryServer = LocateRegistry.createRegistry(1099);
        } catch (Exception e) {
            registryServer = LocateRegistry.getRegistry(1099);
        }

        // The service-name (AlcatrazServer) is bind / replaced with the remote-object reference. 
        // Port of the AlcatrazServer is: random and free port
        registryServer.rebind("AlcatrazServer", UnicastRemoteObject.exportObject(server, 0));
        System.out.println("AlcatrazServer" + server + " is registered by RegistryServer");
    }

}
