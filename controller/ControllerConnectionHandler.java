package controller;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import utils.Rebalance;

public class ControllerConnectionHandler implements Runnable {
    private final Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private Controller controller;
    private ControllerAction controllerAction;
    private Rebalance rebalance;
    private final ScheduledExecutorService scheduler;

    public ControllerConnectionHandler(Socket socket, Controller controller, Rebalance rebalance) throws IOException {
        this.clientSocket = socket;
        this.controller = controller;
        this.rebalance = rebalance;
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.out = new PrintWriter(this.clientSocket.getOutputStream(), true);
        this.in = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
        this.controllerAction = new ControllerAction(this.controller, this.rebalance);
    }

    public void run() {

        try {
            String line;
            while ((line = in.readLine()) != null) {
                String[] splitLine = line.split(" ");
                this.controllerAction.action(splitLine, this.clientSocket);

                // If the socket belongs to a client store is in a list of clients
                if(!this.controller.getDstoreAndPort().contains(this.clientSocket)) {
                    this.controller.addClienToList(this.clientSocket);
                }

                // writing the received message from client
                System.out.printf("CONTROLLER sent from %s: %s\n", this.clientSocket.getPort(), line);
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