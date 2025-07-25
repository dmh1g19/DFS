package controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import utils.FileManager;
import utils.Protocol;
import utils.Rebalance;
import utils.RebalanceAlgorithm;

public class ControllerAction {

    private Controller controller;

    private final ReadWriteLock lock;
    private int reloadAttempt;
    private Rebalance rebalance;

    private int activePortToLoadFrom;
    private Set<Integer> portsToLoadFrom;

    public ControllerAction(Controller controller, Rebalance rebalance) {
        this.reloadAttempt = 0;
        this.rebalance = rebalance;
        this.controller = controller;
        this.portsToLoadFrom = ConcurrentHashMap.newKeySet();
        this.lock = new ReentrantReadWriteLock(true);
    }

    public void action(String[] line, Socket socket) throws IOException {
        switch (line[0]) {
            case Protocol.JOIN_TOKEN               -> isDstoreOrClient(socket, line);
            case Protocol.LIST_TOKEN               -> listCall(line, socket);
            case Protocol.STORE_TOKEN              -> store(socket, line);
            case Protocol.STORE_ACK_TOKEN          -> storeComplete(line);
            case Protocol.LOAD_TOKEN               -> load(line, socket);
            case Protocol.RELOAD_TOKEN             -> reload(line, socket);
            case Protocol.REMOVE_TOKEN             -> remove(line, socket);
            case Protocol.REMOVE_ACK_TOKEN         -> removeAck(line);
            case Protocol.REBALANCE_TOKEN          -> rebalance();
            case Protocol.REBALANCE_COMPLETE_TOKEN -> rebalanceComplete();
            case Protocol.LIST_DSTORE_TOKEN        -> printConnectedDstores();
            case Protocol.LIST_CLIENT_TOKEN        -> printConnectedClients();
            case Protocol.LIST_FILE_STATUS_TOKEN   -> printFileStatus();
        }
    }

    public void rebalanceComplete() {
        this.controller.setRebalanceActive(false);
    }

    public void listStandard(Socket clientSocket) throws IOException {
        String fileList = Protocol.LIST_TOKEN;

        this.lock.readLock().lock();
        try {
            if (this.controller.getDstoreAndPort().size() < this.controller.getRepFactor()) {
                sendMsg(clientSocket,Protocol.ERROR_NOT_ENOUGH_DSTORES_TOKEN);
                return;
            }
            else {
                for (FileManager currFile : this.controller.getManagedFiles()) {
                    fileList = fileList.concat(" " + currFile.getFileName());
                }
            }
        } finally {
            this.lock.readLock().unlock();
        }

        sendMsg(clientSocket, fileList);
    }

    public void listRebalance(String[] line, Socket dStoreSocket) {

        //Get files in dstore
        ArrayList<String> filesInDstore = new ArrayList<String>(Arrays.asList(line));
        filesInDstore.remove(0);

        //Pair the file list to appropriate dstore port number
        this.lock.readLock().lock();
        try {
            for (Map.Entry<Integer, Socket> socketAndList : this.controller.getDstoreAndPort().entrySet()) {
                if(socketAndList.getValue().equals(dStoreSocket)) {
                    this.controller.addStoreAndFileList(socketAndList.getKey(), filesInDstore);
                }
            }
        } finally {
            this.lock.readLock().unlock();
        }

        //Wait for all dstores to reply
        //TODO: check for dead dstores
        new Thread(() -> {
            try {
                Thread.sleep(this.controller.getTimeout());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        this.lock.readLock().lock();
        try {
            //Get the appropriate file distribution s.t files are replicated at least R times and evenly spread over N dstores
            ArrayList<ArrayList<String>> fileAllocation = new RebalanceAlgorithm().allocateFiles(this.controller.getDstoreAndPort().size(),
                    this.controller.getRepFactor(),
                    this.controller.getManagedFiles());

            //Make list of files to remove for each dstore
            Iterator<ArrayList<String>> it = fileAllocation.iterator();
            for (Map.Entry<Integer, ArrayList<String>> socketAndList : this.controller.getDstoreAndFileList().entrySet()) {
                //TODO: check to see if this is deleting dstores in Controller
                //TODO: Remove incomplete stores from index

                socketAndList.getValue().removeAll(it.next());
                Socket dstoreSocket = new Socket(this.controller.getControllerSocket().getInetAddress(), socketAndList.getKey());

                String msg = Protocol.REBALANCE_TOKEN;
                for(String file : socketAndList.getValue()) {
                    msg = msg.concat(" " + file);
                }
                sendMsg(dstoreSocket, msg);
                //System.out.println("FILES TO REMOVE: " + socketAndList.getValue().toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            this.lock.readLock().unlock();
        }

        //System.out.println("CONTENTS: " + this.controller.getDstoreAndFileList().size());
        //for (Map.Entry<Integer, ArrayList<String>> socketAndList : this.controller.getDstoreAndFileList().entrySet()) {
        //    System.out.println("GOT: " + socketAndList.getValue() + " " + socketAndList.getKey());
        //}
    }

    public void listCall(String[] line, Socket clientSocket) throws IOException {
        if(line.length > 1) {
            listRebalance(line, clientSocket);
        }
        else {
            listStandard(clientSocket);
        }
    }

    public void store(Socket socket, String[] line) throws IOException {
        String fileName = line[1];
        int fileSize = Integer.parseInt(line[2]);

        FileManager managedFile = new FileManager(fileName, fileSize, socket);

        //Check if the file is already in the index, reply with an error
        this.lock.readLock().lock();
        this.rebalance.updateStoreState(true);
        try {
            for (FileManager file : this.controller.getManagedFiles()) {
                if(fileName.equals(file.getFileName())) {
                    sendMsg(socket, Protocol.ERROR_FILE_ALREADY_EXISTS_TOKEN);
                    return;
                }
            }
        } finally {
            this.lock.readLock().unlock();
        }

        this.lock.readLock().lock();
        try {
            //if the total amount of connected dstores is less than R, terminate
            if(this.controller.getDstoreAndPort().size() < this.controller.getRepFactor()) {
                sendMsg(socket, Protocol.ERROR_NOT_ENOUGH_DSTORES_TOKEN);
                return;
            }
            else {
                //Send STORE_TO message to the client
                String msg = Protocol.STORE_TO_TOKEN;
                for(Map.Entry<Integer, Socket> socketPort : this.controller.getDstoreAndPort().entrySet()) {
                    msg = msg.concat(" " + socketPort.getKey());
                }
                sendMsg(socket, msg);
            }
        } finally {
            this.lock.readLock().unlock();
        }

        //Add the file to a list for managing files and their ACKS individually
        managedFile.setState_STORING();
        this.controller.getManagedFiles().add(managedFile);
    }

    public void storeComplete(String[] splitInput) throws IOException {

        FileManager managedFile = null;

        this.lock.readLock().lock();
        try {
            for (FileManager file : this.controller.getManagedFiles()) {
                if(splitInput[1].equals(file.getFileName())) {
                    managedFile = file;
                }
            }

            //Wait for all managedFiles to return their ACKS
            managedFile.incrementStoreACKs();
            if(managedFile.getStoreACKs() >= this.controller.getRepFactor() && managedFile.getFileState() != managedFile.getStateComplete()) {
                managedFile.setState_STORING_COMPLETE();
                sendMsg(managedFile.getClientSocket(), Protocol.STORE_COMPLETE_TOKEN);

                this.rebalance.updateStoreState(false);
                return;
            }
        } finally {
            this.lock.readLock().unlock();
        }

        // If time out occurs, remove file from index as it should have gathered all ACKS
        FileManager finalManagedFile = managedFile;
        new Thread(() -> {
            this.lock.writeLock().lock();
            try {
                Thread.sleep(this.controller.getTimeout());
                if(finalManagedFile.getStoreACKs() < this.controller.getRepFactor()) {
                    System.out.println("Time out, " + finalManagedFile.getFileName() + " not stored..");
                    this.controller.getManagedFiles().remove(finalManagedFile);
                }

                this.rebalance.updateStoreState(false);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                this.lock.writeLock().unlock();
            }
        }).start();
    }

    public void load(String[] splitInput, Socket socketClient) throws IOException {

        String fileName = splitInput[1];
        FileManager file = null;

        this.lock.readLock().lock();
        try {
            //find file
            for (FileManager currFile : this.controller.getManagedFiles()) {
                if (fileName.equals(currFile.getFileName())) {
                    file = currFile;
                }
            }

            if (this.controller.getDstoreAndPort().size() < this.controller.getRepFactor()) {
                sendMsg(socketClient, Protocol.ERROR_NOT_ENOUGH_DSTORES_TOKEN);
                return;
            }
            else if (file == null) {
                sendMsg(socketClient, Protocol.ERROR_NOT_ENOUGH_DSTORES_TOKEN);
            }
            else if (this.controller.getDstoreAndPort().size() == 0) {
                sendMsg(socketClient, Protocol.ERROR_LOAD_TOKEN);
            }
            else {
                // empty the active ports list and re-populate
                this.portsToLoadFrom.clear();
                for (Map.Entry<Integer, Socket> socket  : this.controller.getDstoreAndPort().entrySet()) {
                    this.portsToLoadFrom.add(socket.getKey());
                }

                // no dstore has been assigned yet, pick the first one from the list of active R stores
                // and remove it from the list so it isnt used again until a STORE command is requested
                // it might be a bad idea to use a locally assigned list to manage active ports
                // when the main controller class already has one?
                this.activePortToLoadFrom = this.portsToLoadFrom.iterator().next();
                this.portsToLoadFrom.remove(this.activePortToLoadFrom);

                sendMsg(socketClient, Protocol.LOAD_FROM_TOKEN + " " + this.activePortToLoadFrom + " " + file.getFileSize());
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            this.lock.readLock().unlock();
        }
    }

    public void reload(String[] splitInput, Socket socketClient) throws IOException {

        //check if all R dstores have been checked already, if so return an error
        this.reloadAttempt++;
        if(this.reloadAttempt == this.controller.getRepFactor()) {
            sendMsg(socketClient, Protocol.ERROR_LOAD_TOKEN);
            return;
        }

        String fileName = splitInput[1];
        FileManager file = null;

        this.lock.readLock().lock();
        try {
            //find file
            for (FileManager currFile : this.controller.getManagedFiles()) {
                if (fileName.equals(currFile.getFileName())) {
                    file = currFile;
                }
            }

        } finally {
            this.lock.readLock().unlock();
        }

        this.activePortToLoadFrom = portsToLoadFrom.iterator().next();
        this.portsToLoadFrom.remove(this.activePortToLoadFrom);

        String loadMsg = Protocol.LOAD_FROM_TOKEN + " " + this.activePortToLoadFrom + " " + file.getFileSize();

        //could get the client socket directly from the managedfile object instead of passing it to the reload method
        sendMsg(socketClient, loadMsg);
    }

    public void remove(String[] splitInput, Socket clientSocket) throws IOException {

        String fileName = splitInput[1];

        FileManager fileToRemove = null;
        this.lock.readLock().lock();
        try {
            //get the file from the index
            for (FileManager currFile : this.controller.getManagedFiles()) {
                if (fileName.equals(currFile.getFileName())) {
                    fileToRemove = currFile;
                    break;
                }
            }
        } finally {
            this.lock.readLock().unlock();
        }

        if(fileToRemove == null) {
            sendMsg(clientSocket, Protocol.ERROR_FILE_DOES_NOT_EXIST_TOKEN);
        }
        else {

            //Send remove request to active DStores
            this.lock.readLock().lock();
            try {
                fileToRemove.setState_REMOVING();

                for(Map.Entry<Integer, Socket> socketPort : this.controller.getDstoreAndPort().entrySet()) {
                    //send remove request
                    sendMsg(socketPort.getValue(), Protocol.REMOVE_TOKEN + " " + fileToRemove.getFileName());
                }
            } finally {
                this.lock.readLock().unlock();
            }
        }
    }

    public void removeAck(String[] splitInput) throws IOException {
        String fileName = splitInput[1];

        FileManager fileToRemove = null;
        this.lock.readLock().lock();
        try {
            //get the file from the index
            for (FileManager currFile : this.controller.getManagedFiles()) {
                if (fileName.equals(currFile.getFileName())) {
                    fileToRemove = currFile;
                    break;
                }
            }
        } finally {
            this.lock.readLock().unlock();
        }

        //find file in index and increment its remove ACK
        fileToRemove.incrementRemoveACKs();

        //check the file has all the remove ACKS
        if(fileToRemove.getRemoveACKs() >= this.controller.getDstoreAndPort().size()) {
            fileToRemove.setState_REMOVING_COMPLETE();

            this.lock.writeLock().lock();
            try {
                this.controller.getManagedFiles().remove(fileToRemove);
            } finally {
                this.lock.writeLock().unlock();

            }

            sendMsg(fileToRemove.getClientSocket(), Protocol.REMOVE_COMPLETE_TOKEN);
        }
    }

    public void rebalance() throws IOException {

        this.lock.readLock().lock();
        try {
            if(!this.controller.getIsRebalanceActive()) {
                this.controller.setRebalanceActive(true);

                if(this.controller.getDstoreAndPort() == null) {
                    System.out.println("Rebalance operation halted, no active dstores available..");
                }
                else {
                    for(Map.Entry<Integer, Socket> socketPort : this.controller.getDstoreAndPort().entrySet()) {
                        System.out.println("Sending list request to: " + socketPort.getKey() + " : " + socketPort.getValue().getPort());
                        sendMsg(socketPort.getValue(), Protocol.LIST_TOKEN);
                    }
                }
            }
            else {
                System.out.println("Rebalance already in progress..");
            }
        } finally {
            this.lock.readLock().unlock();
        }
    }

    //Add new dstores socket with their serverSocket port number
    public void isDstoreOrClient(Socket socket, String[] line) throws IOException {
        this.controller.addStoreAndPort(socket, Integer.parseInt(line[1]));
    }

    public void printFileStatus() {
        System.out.println("*** INDEX STATUS ***");
        for(FileManager file : this.controller.getManagedFiles()) {
            System.out.println(file.getFileName() + " " + file.getFileState());
        }
    }

    //** Print a list of currently connected dsores
    public void printConnectedDstores() {
        System.out.println("**LIST OF CONNECTED DSTORES**");
        for(Map.Entry<Integer, Socket> socket : this.controller.getDstoreAndPort().entrySet()) {
            System.out.printf("DSTORE: %s / %s\n", socket.getKey(), socket.getValue());
        }
    }

    //** Print a list of currently connected clients
    public void printConnectedClients() {
        System.out.println("**LIST OF CONNECTED CLIENTS**");
        for(Socket socket : this.controller.getClientList()) {
            System.out.printf("CLIENT: %s / %s\n", socket.getPort(), socket.getLocalPort());
        }
    }

    public void sendMsg(Socket socket, String msg) throws IOException {
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        out.println(msg);
    }
}
