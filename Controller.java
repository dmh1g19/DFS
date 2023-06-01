import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;

class Controller {

    private ServerSocket server;
    private Socket socket;
    private int port;
    private int repFactor;
    private int timeout;
    private boolean isRebalanceActive;

    private final Set<FileManager> managedFiles;
    private Set<Socket> connectedClient;
    private ConcurrentHashMap<Integer, Socket> dstorePortAndSocket;
    private ConcurrentHashMap<Integer, ArrayList<String>> dstoreAndFileList;

    public Controller(int port, int r, int timeout) throws IOException {
        this.port = port;
        this.repFactor = r;
        this.timeout = timeout;
        this.connectedClient = ConcurrentHashMap.newKeySet();
        this.dstorePortAndSocket = new ConcurrentHashMap<>();
        this.dstoreAndFileList = new ConcurrentHashMap<>();
        this.managedFiles = ConcurrentHashMap.newKeySet();
        this.server = new ServerSocket(this.port); //port listening on
        this.server.setReuseAddress(true);
        this.isRebalanceActive = false;
    }

    public void start() {

        Timer time = new Timer();
        Rebalance rebalance = new Rebalance(this, this.server.getInetAddress(), this.server.getLocalPort(), false);
        time.schedule(rebalance,this.timeout+1000,this.timeout+1000);

        try {
            // running infinite loop for getting client request
            while (true) {

                // socket object to receive incoming client requests
                this.socket = server.accept();

                // Displaying that new client is connected to server
                System.out.println("CONTROLLER new client connected: " + this.socket.getPort() + "/" + this.socket.getLocalPort());

                // create a new thread object, this thread will handle the client separately
                ControllerConnectionHandler clientSock = new ControllerConnectionHandler(this.socket, this, rebalance);
                new Thread(clientSock).start();
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            if (server != null) {
                try {
                    server.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void addStoreAndPort (Socket socket, int port) {
        this.dstorePortAndSocket.putIfAbsent(port, socket);
    }

    public void addClienToList(Socket socket) {
        this.connectedClient.add(socket);
    }

    public void addStoreAndFileList (int port, ArrayList<String> fileList) {
        this.dstoreAndFileList.putIfAbsent(port, fileList);
    }

    public ConcurrentHashMap<Integer, Socket> getDstoreAndPort() {
        return this.dstorePortAndSocket;
    }

    public ConcurrentHashMap<Integer, ArrayList<String>> getDstoreAndFileList() {
        return this.dstoreAndFileList;
    }

    public Set<Socket> getClientList() {
        return this.connectedClient;
    }

    public Set<FileManager> getManagedFiles() {
        return this.managedFiles;
    }

    public int getRepFactor() {
        return this.repFactor;
    }

    public int getTimeout() {
        return this.timeout;
    }

    public boolean getIsRebalanceActive() {
        return this.isRebalanceActive;
    }

    public Socket getControllerSocket() {
        return this.socket;
    }

    public void setRebalanceActive(boolean value) {
        this.isRebalanceActive = value;
    }

    public static void main(String[] args) throws IOException {
        Controller controller = new Controller(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
        controller.start();
    }
}