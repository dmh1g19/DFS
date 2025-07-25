package utils;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Date;
import java.util.TimerTask;

import controller.Controller;

public class Rebalance extends TimerTask {

    private Date current;

    private Controller controller;
    private InetAddress controllerIPAddress;
    private int controllerPort;

    private boolean storeInProgress;
    private boolean loadInProgress;
    private boolean removeInProgress;

    private boolean isThereRDstores;

    public Rebalance(Controller controller, InetAddress controllerIPAddress, int port, boolean initBool) {
        this.controller = controller;
        this.controllerIPAddress = controllerIPAddress;
        this.controllerPort = port;
        this.isThereRDstores = this.controller.getDstoreAndPort().size() >= this.controller.getRepFactor();
        this.storeInProgress = initBool;
        this.loadInProgress = initBool;
        this.removeInProgress = initBool;
    }

    @Override
    public void run() {
        this.current = new Date();
        try {
            if(!this.storeInProgress && !this.loadInProgress && !this.removeInProgress) {

                this.storeInProgress = true;

                //Creating a new socket to send rebalance token to the controller (serverSocket)
                Socket controllerSocket = new Socket(this.controllerIPAddress, controllerPort);
                new PrintWriter(controllerSocket.getOutputStream(), true).println(Protocol.REBALANCE_TOKEN);
                controllerSocket.close();
            }
            else {
                System.out.println("CONTROLLER Cannot rebalance...");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

   public void updateStoreState(boolean value) {
        this.storeInProgress = value;
   }
}