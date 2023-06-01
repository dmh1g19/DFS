import java.io.*;
import java.net.Socket;

public class DstoreConnectionHandler implements Runnable {
    private final Socket clientSocket;
    private Socket controllerSocket;
    private PrintWriter out;
    private BufferedReader in;
    private Dstore dstore;
    private DstoreAction dstoreAction;
    private File file;

    public DstoreConnectionHandler(File file, Socket socket, Dstore dstore, Socket controllerSocket) throws IOException {
        this.file = file;
        this.clientSocket = socket;
        this.controllerSocket = controllerSocket;
        this.out = new PrintWriter(this.clientSocket.getOutputStream(), true);
        this.in = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
        this.dstore = dstore;
        this.dstoreAction = new DstoreAction(this.file, this.clientSocket, this.dstore, this.controllerSocket);
    }

    public void run() {

        try {
            // writing the received message from connection
            String line;
            while ((line = in.readLine()) != null) {
                String[] splitLine = line.split(" ");
                dstoreAction.action(splitLine);
                System.out.printf("DSTORE sent from %s: %s\n", this.clientSocket.getPort(), line);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                    clientSocket.close();
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
