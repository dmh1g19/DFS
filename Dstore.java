import java.io.*;
import java.net.*;
import java.util.Objects;

class Dstore {

    private ServerSocket server;
    private Socket socket;
    private int port;
    private int cport;

    private String folderName;
    private File file;

    public Dstore(int port, int cport, String folderName) throws IOException {
        this.port = port; //port dstore is listening on
        this.cport = cport; //controller's port
        this.server = new ServerSocket(this.port);
        this.server.setReuseAddress(true);
        this.folderName = folderName;
    }

    public void start() throws IOException {

        //check a folder already exists, create one if not, otherwise empty the folder
        createFolder();

        // Establish a persistent connection with the controller
        Socket controllerConnection = new Socket("localhost", this.cport);
        PrintWriter out = new PrintWriter(controllerConnection.getOutputStream(), true);
        out.println(Protocol.JOIN_TOKEN + " " + this.port);

        // Handle messages received from the controller
        DstoreConnectionHandler controllerSock = new DstoreConnectionHandler(this.file, controllerConnection, this, controllerConnection);
        new Thread(controllerSock).start();

        try {
            // running infinite loop for getting client request
            while (true) {

                // socket object to receive incoming client requests
                this.socket = server.accept();

                // Displaying that new client is connected to server
                System.out.println("DSTORE new connection: " + this.socket.getInetAddress().getHostAddress());

                // create a new thread object, this thread will handle the client separately
                DstoreConnectionHandler clientSock = new DstoreConnectionHandler(this.file, this.socket, this, controllerConnection);
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

    public void createFolder() {
        this.file = new File(this.folderName);

        if(file.exists()) {
            System.out.println(this.folderName + " already exists, keeping folder but deleting content.");
            for(File file : Objects.requireNonNull(file.listFiles())) {
                file.delete();
            }
        }
        else {
            file.mkdirs();
            System.out.println("Made folder: " + this.folderName);
        }
    }

    public String getDstoreFolderName() {
        return this.folderName;
    }

    public static void main(String[] args) throws IOException {
        Dstore dstore = new Dstore(Integer.parseInt(args[0]),Integer.parseInt(args[1]), args[2]);
        dstore.start();
    }
}