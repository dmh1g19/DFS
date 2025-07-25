package utils;
import java.net.Socket;

public class FileManager {

    private enum FileState {
        STORING,
        STORING_COMPLETE,
        REMOVING,
        REMOVING_COMPLETE
    }

    private Socket clientSocket;
    private String fileName;
    private int fileSize;
    private int storeACK;
    private int removeACK;
    private FileState state;

    public FileManager(String fileName, int fileSize, Socket socket) {
        this.storeACK = 0;
        this.removeACK = 0;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.clientSocket = socket;
    }

    public void incrementStoreACKs() {
        this.storeACK++;
    }

    public void incrementRemoveACKs() {
        this.removeACK++;
    }

    public void setState_REMOVING() {
        this.state = FileState.REMOVING;
    }

    public void setState_REMOVING_COMPLETE() {
        this.state = FileState.REMOVING_COMPLETE;
    }

    public void setState_STORING() {
        this.state = FileState.STORING;
    }

    public void setState_STORING_COMPLETE() {
        this.state = FileState.STORING_COMPLETE;
    }

    public int getStoreACKs() {
        return this.storeACK;
    }

    public int getRemoveACKs() {
        return this.removeACK;
    }

    public int getFileSize() {
        return this.fileSize;
    }

    public String getFileName() {
        return this.fileName;
    }

    public Socket getClientSocket() {
        return this.clientSocket;
    }

    public FileState getFileState() {
        return this.state;
    }

    public FileState getStateComplete() {
        return FileState.STORING_COMPLETE;
    }
}