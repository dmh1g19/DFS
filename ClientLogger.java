public class ClientLogger
extends Loggert {
    private static ClientLogger a = null;
    
    public static synchronized void init(LoggingType loggingType) {
        if (a == null) {
            a = new ClientLogger(loggingType);
        }
    }

    public static ClientLogger getInstance() {
        if (a == null) {
            throw new RuntimeException("ClientLogger has not been initialised yet");
        }
        return a;
    }

    protected ClientLogger(LoggingType loggingType) {
        super(loggingType);
    }

    @Override
    protected String getLogFileSuffix() {
        return "client";
    }

    public void errorConnecting(int cport) {
        this.log("Cannot connect to the Controller on port " + cport);
    }

    public void storeStarted(String filename) {
        this.log("Store operation started for file " + filename);
    }

    public void dstoresWhereToStoreTo(String filename, int[] dstorePorts) {
        StringBuffer stringBuffer = new StringBuffer("Controller replied to store " + filename + " in these Dstores: ");
        for (int port : dstorePorts) {
            stringBuffer.append(port + " ");
        }
        this.log(stringBuffer.toString());
    }

    public void storeToDstoreStarted(String filename, int dstorePort) {
        this.log("Storing file " + filename + " to Dstore " + dstorePort);
    }

    public void ackFromDstore(String filename, int dstorePort) {
        this.log("ACK received from Dstore " + dstorePort + " to store file " + filename);
    }

    public void storeToDstoreCompleted(String filename, int dstorePort) {
        this.log("Store of file " + filename + " to Dstore " + dstorePort + " successfully completed");
    }

    public void storeToDstoreFailed(String filename, int dstorePort) {
        this.log("Store of file " + filename + " to Dstore " + dstorePort + " failed");
    }

    public void fileToStoreAlreadyExists(String filename) {
        this.log("File to store " + filename + " already exists in the data store");
    }

    public void storeCompleted(String filename) {
        this.log("Store operation for file " + filename + " completed");
    }

    public void loadStarted(String filename) {
        this.log("Load operation for file " + filename + " started");
    }

    public void retryLoad(String filename) {
        this.log("Retrying to load file " + filename);
    }

    /*
     * Log which Dstore and file the Controller replied to load from, including file size.
     */
    public void dstoreWhereToLoadFrom(String filename, int dstorePort, int filesize) {
        this.log("Controller replied to load file " + filename + " (size: " + filesize + " bytes) from Dstore " + dstorePort);
    }

    /*
     * Log loading file from Dstore.
     */
    public void loadFromDstore(String filename, int dstorePort) {
        this.log("Loading file " + filename + " from Dstore " + dstorePort);
    }

    /*
     * Log when loading from a Dstore fails.
     */
    public void loadFromDstoreFailed(String filename, int dstorePort) {
        this.log("Load operation for file " + filename + " from Dstore " + dstorePort + " failed");
    }

    /*
     * Log when loading a file fails after contacting multiple Dstores.
     */
    public void loadFailed(String filename, int dstoreCount) {
        this.log("Load operation for file " + filename + " failed after having contacted " + dstoreCount + " different Dstores");
    }

    /*
     * Log when the file to load does not exist.
     */
    public void fileToLoadDoesNotExist(String filename) {
        this.log("Load operation failed because file does not exist (filename: " + filename + ")");
    }

    /*
     * Log when loading a file is completed successfully.
     */
    public void loadCompleted(String filename, int dstorePort) {
        this.log("Load operation of file " + filename + " from Dstore " + dstorePort + " successfully completed");
    }

    /*
     * Log when remove operation is started.
     */
    public void removeStarted(String filename) {
        this.log("Remove operation for file " + filename + " started");
    }

    /*
     * Log when the file to remove does not exist.
     */
    public void fileToRemoveDoesNotExist(String filename) {
        this.log("Remove operation failed because file does not exist (filename: " + filename + ")");
    }

    /*
     * Log when remove operation is completed successfully.
     */
    public void removeComplete(String filename) {
        this.log("Remove operation for file " + filename + " successfully completed");
    }

    /*
     * Log when remove operation fails.
     */
    public void removeFailed(String filename) {
        this.log("Remove operation for file " + filename + " not completed successfully");
    }

    public void listStarted() {
        this.log("List operation started");
    }

    public void listFailed() {
        this.log("List operation failed");
    }

    public void listCompleted() {
        this.log("List operation successfully completed");
    }

    /*
     * Log when a connection is established.
     */
    public void connectionEstablished(int port) {
        this.log("Connection established on port " + port);
    }

    /*
     * Log when a message is sent to a port.
     */
    public void messageSent(int port, String message) {
        this.log("Message sent to port " + port + ": " + message);
    }

    /*
     * Log when a message is received from a port.
     */
    public void messageReceived(int port, String message) {
        this.log("Message received from port " + port + ": " + message);
    }

    /*
     * Log when a timeout expires while reading from a port.
     */
    public void timeoutExpiredWhileReading(int port) {
        this.log("Timeout expired while reading from port " + port);
    }

    /*
     * Log an error message.
     */
    public void error(String message) {
        this.log("ERROR: " + message);
    }
}
