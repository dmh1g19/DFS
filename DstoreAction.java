import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public class DstoreAction {

    private Socket clientSocket;
    private Socket controllerSocket;
    private Dstore dstore;

    private File folder;
    private File[] fileList;

    public DstoreAction(File file, Socket clientSocket, Dstore dstore, Socket controllerSocket) {
        this.folder = file;
        this.clientSocket = clientSocket;
        this.controllerSocket = controllerSocket;
        this.dstore = dstore;
    }

    public void action(String[] line) throws IOException {
        switch (line[0]) {
            case Protocol.STORE_TOKEN      -> store(line);
            case Protocol.LOAD_DATA_TOKEN  -> loadData(line);
            case Protocol.REMOVE_TOKEN     -> removeAck(line);
            case Protocol.LIST_TOKEN       -> list();
            case Protocol.REBALANCE_TOKEN  -> rebalance(line);
            default                        -> System.out.println("(?): " + line[0]);
        }
    }

    public void rebalance(String[] line) throws IOException {

        int i = 1;
        while (true) {
            try {
                String file = line[i++];
                removeFile(file);
            } catch (Exception e) {
                break;
            }
        }

        sendMsg(this.controllerSocket,Protocol.REBALANCE_COMPLETE_TOKEN);
    }

    public void removeFile(String fileName) throws IOException {
        try {
            Files.delete(Path.of(this.dstore.getDstoreFolderName() + File.separator + fileName));
        }
        catch (NoSuchFileException e) {
            sendMsg(this.controllerSocket, Protocol.ERROR_FILE_DOES_NOT_EXIST_TOKEN);
        }
    }

    public void store(String[] splitInput) throws IOException {

        sendMsg(this.clientSocket, Protocol.ACK_TOKEN);

        String fileName = splitInput[1];
        int filesize = Integer.parseInt(splitInput[2]);

        File output = new File(this.dstore.getDstoreFolderName() + File.separator + fileName);
        FileOutputStream fileStream = new FileOutputStream(output);
        fileStream.write(this.clientSocket.getInputStream().readNBytes(filesize));

        sendMsg(this.controllerSocket, Protocol.STORE_ACK_TOKEN + " " + fileName);
    }

    public void loadData(String[] splitInput) throws IOException {

        String fileName = splitInput[1];
        File file = new File(this.dstore.getDstoreFolderName() + File.separator + fileName);
        FileInputStream fileIn = new FileInputStream(file);


        OutputStream out = this.clientSocket.getOutputStream();
        out.write(fileIn.readNBytes((int) file.length()));
    }

    public void removeAck(String[] splitInput) throws IOException {
        String fileName = splitInput[1];

        try {
            Files.delete(Path.of(this.dstore.getDstoreFolderName() + File.separator + fileName));
        }
        catch (NoSuchFileException e) {
            sendMsg(this.controllerSocket, Protocol.ERROR_FILE_DOES_NOT_EXIST_TOKEN);
        }

        PrintWriter outController = new PrintWriter(this.controllerSocket.getOutputStream(), true);
        outController.println(Protocol.REMOVE_ACK_TOKEN + " " + fileName);
    }

    public void list() throws IOException {
        this.fileList = this.folder.listFiles();

        String filesInDirectory = Protocol.LIST_TOKEN;
        for(File file : this.fileList) {
            if(file.isFile()) {
                filesInDirectory = filesInDirectory.concat(" " + file.getName());
            }
        }

        if(filesInDirectory.equals(Protocol.LIST_TOKEN)) {
            sendMsg(this.controllerSocket, filesInDirectory.concat(" " + "NO_FILES"));
        }
        else {
            sendMsg(this.controllerSocket, filesInDirectory);
        }
    }

    public void sendMsg(Socket socket, String msg) throws IOException {
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        out.println(msg);
    }
}
