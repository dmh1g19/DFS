package client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.FileAlreadyExistsException;

import exceptions.FileDoesNotExistException;
import exceptions.NotEnoughDstoresException;
import utils.Loggert;
import utils.Loggert.LoggingType;

public class Client {
    private final int a;
    private final int b;
    private Socket c;
    private BufferedReader d;
    private PrintWriter e;
    private int f;

    public Client(int cport, int timeout, Loggert.LoggingType loggingType) {
        this.a = cport;
        this.b = timeout;
        ClientLogger.init(loggingType);
    }

    public void connect() throws IOException {
        try {
            this.disconnect();
        } catch (IOException iOException) {
        }
        try {
            this.c = new Socket(InetAddress.getLoopbackAddress(), this.a);
            this.c.setSoTimeout(this.b);
            ClientLogger.getInstance().connectionEstablished(this.c.getPort());
            this.e = new PrintWriter(this.c.getOutputStream(), true);
            this.d = new BufferedReader(new InputStreamReader(this.c.getInputStream()));
            return;
        } catch (Exception exception) {
            ClientLogger.getInstance().errorConnecting(this.a);
            throw exception;
        }
    }

    public void disconnect() throws IOException {
        if (this.c != null) {
            this.c.close();
        }
    }

    public void send(String message) {
        this.e.println(message);
        ClientLogger.getInstance().messageSent(this.c.getPort(), message);
    }

    public String[] list() throws IOException, NotEnoughDstoresException {
        this.e.println("LIST");
        ClientLogger.getInstance().messageSent(this.c.getPort(), "LIST");
        ClientLogger.getInstance().listStarted();

        String responseLine;
        try {
            responseLine = this.d.readLine();
        } catch (SocketTimeoutException socketTimeoutException) {
            ClientLogger.getInstance().timeoutExpiredWhileReading(this.c.getPort());
            ClientLogger.getInstance().listFailed();
            throw socketTimeoutException;
        }

        ClientLogger.getInstance().messageReceived(this.c.getPort(), responseLine);

        if (responseLine != null) {
            String[] tokens = responseLine.split(" ");
            if (tokens.length > 0) {
                if (tokens[0].equals("ERROR_NOT_ENOUGH_DSTORES")) {
                    ClientLogger.getInstance().error("Not enough Dstores have joined the data store yet");
                    ClientLogger.getInstance().listFailed();
                    throw new NotEnoughDstoresException();
                }

                if (tokens[0].equals("LIST")) {
                    String[] result = new String[tokens.length - 1];
                    for (int i = 0; i < result.length; ++i) {
                        result[i] = tokens[i + 1];
                    }
                    ClientLogger.getInstance().listCompleted();
                    return result;
                }
            }
        }

        String errorMsg = "Connection closed by the Controller";
        ClientLogger.getInstance().error(errorMsg);
        ClientLogger.getInstance().listFailed();
        throw new IOException(errorMsg);
    }

    /*
     * WARNING - void declaration
     */
    public void store(File file) throws IOException, NotEnoughDstoresException, FileAlreadyExistsException {
        byte[] arrby;
        if (!file.exists()) {
            String string = "File to store does not exist (absolute path: " + file.getAbsolutePath() + ")";
            ClientLogger.getInstance().error(string);
            throw new IOException(string);
        }
        String string = file.getName();
        if (string.contains(" ")) {
            String string2 = "Filename includes spaces (absolute path: " + file.getAbsolutePath() + ")";
            ClientLogger.getInstance().error(string2);
            throw new IOException(string2);
        }
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            arrby = fileInputStream.readAllBytes();
            fileInputStream.close();
        } catch (IOException iOException) {
            ClientLogger.getInstance()
                    .error("Error reading data from file (absolute path: " + file.getAbsolutePath() + ")");
            throw iOException;
        }
        this.store(string, arrby);
    }

    /*
     * WARNING - void declaration
     */
    public void store(String filename2, byte[] data)
            throws IOException, NotEnoughDstoresException, FileAlreadyExistsException {
        String string;
        String string2;
        String string3 = "STORE " + filename2 + " " + data.length;
        this.e.println(string3);
        ClientLogger.getInstance().messageSent(this.c.getPort(), string3);
        ClientLogger.getInstance().storeStarted(filename2);
        try {
            string2 = this.d.readLine();
        } catch (SocketTimeoutException socketTimeoutException) {
            ClientLogger.getInstance().timeoutExpiredWhileReading(this.c.getPort());
            throw socketTimeoutException;
        }
        ClientLogger.getInstance().messageReceived(this.c.getPort(), string2);
        int[] arrn = Client.a(filename2, string2);
        ClientLogger.getInstance().dstoresWhereToStoreTo(filename2, arrn);
        int[] arrn2 = arrn;
        int n = arrn.length;
        for (int i = 0; i < n; ++i) {
            int n2 = arrn2[i];
            Socket socket = null;
            try {
                try {
                    socket = new Socket(InetAddress.getLoopbackAddress(), n2);
                    ClientLogger.getInstance().connectionEstablished(socket.getPort());
                    Object object = socket.getOutputStream();
                    Object object2 = new PrintWriter((OutputStream) object, true);
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    ((PrintWriter) object2).println(string3);
                    ClientLogger.getInstance().messageSent(socket.getPort(), string3);
                    ClientLogger.getInstance().storeToDstoreStarted(filename2, n2);
                    try {
                        object2 = bufferedReader.readLine();
                    } catch (SocketTimeoutException socketTimeoutException) {
                        ClientLogger.getInstance().timeoutExpiredWhileReading(socket.getPort());
                        throw socketTimeoutException;
                    }
                    ClientLogger.getInstance().messageReceived(socket.getPort(), (String) object2);
                    if (object2 == null) {
                        object = "Connection closed by Dstore ".concat(String.valueOf(n2));
                        ClientLogger.getInstance().error((String) object);
                        throw new IOException((String) object);
                    }
                    if (!((String) object2).trim().equals("ACK")) {
                        object = "Unexpected message received from Dstore (ACK was expected): "
                                .concat(String.valueOf(object2));
                        ClientLogger.getInstance().error((String) object);
                        throw new IOException((String) object);
                    }
                    ClientLogger.getInstance().ackFromDstore(filename2, n2);
                    ((OutputStream) object).write(data);
                    ClientLogger.getInstance().storeToDstoreCompleted(filename2, n2);
                } catch (Exception exception) {
                    ClientLogger.getInstance().storeToDstoreFailed(filename2, n2);
                    if (socket == null)
                        continue;
                }
            } catch (Throwable t) {
                if (socket != null) {
                    socket.close();
                }
                throw t;
            }

            socket.close();
        }
        try {
            string = this.d.readLine();
        } catch (SocketTimeoutException socketTimeoutException) {
            ClientLogger.getInstance().timeoutExpiredWhileReading(this.c.getPort());
            throw socketTimeoutException;
        }
        ClientLogger.getInstance().messageReceived(this.c.getPort(), string);
        if (string == null) {
            String string4 = "Connection closed by the Controller";
            ClientLogger.getInstance().error(string4);
            throw new IOException(string4);
        }
        if (string.trim().equals("STORE_COMPLETE")) {
            ClientLogger.getInstance().storeCompleted(filename2);
            return;
        }

        String string5 = "Unexpected message received (STORE_COMPLETE was expected): ".concat(String.valueOf(string2));
        ClientLogger.getInstance().error(string5);
        throw new IOException(string5);
    }

    private static int[] a(String filename, String message) throws IOException {
        if (message == null) {
            String errorMsg = "Connection closed by the Controller";
            ClientLogger.getInstance().error(errorMsg);
            throw new IOException(errorMsg);
        }

        String[] tokens = message.split(" ");

        if (tokens[0].equals("STORE_TO")) {
            int[] ports = new int[tokens.length - 1];
            for (int i = 0; i < ports.length; ++i) {
                ports[i] = Integer.parseInt(tokens[i + 1]);
            }
            return ports;
        } else if (tokens[0].equals("ERROR_FILE_ALREADY_EXISTS")) {
            ClientLogger.getInstance().fileToStoreAlreadyExists(filename);
            throw new FileAlreadyExistsException(filename);
        } else if (tokens[0].equals("ERROR_NOT_ENOUGH_DSTORES")) {
            ClientLogger.getInstance().error("Not enough Dstores have joined the data store yet");
            throw new NotEnoughDstoresException();
        } else {
            String errorMsg = "Unexpected message received: " + message;
            ClientLogger.getInstance().error(errorMsg);
            throw new IOException(errorMsg);
        }
    }

    public void load(String filename, File fileFolder)
            throws IOException, NotEnoughDstoresException, FileDoesNotExistException {

        if (!fileFolder.exists()) {
            String errorMsg = "The folder where to store the file does not exist (absolute path: "
                    + fileFolder.getAbsolutePath() + ")";
            ClientLogger.getInstance().error(errorMsg);
            throw new IOException(errorMsg);
        }

        if (!fileFolder.isDirectory()) {
            String errorMsg = "The provided folder where to store the file is not actually a directory (absolute path: "
                    + fileFolder.getAbsolutePath() + ")";
            ClientLogger.getInstance().error(errorMsg);
            throw new IOException(errorMsg);
        }

        byte[] fileData = this.load(filename); // assumes load(filename) returns file content as byte[]
        File fileToWrite = new File(fileFolder, filename);

        try (FileOutputStream fos = new FileOutputStream(fileToWrite)) {
            fos.write(fileData);
        }
    }

    public byte[] load(String filename) throws IOException, NotEnoughDstoresException, FileDoesNotExistException {
        this.f = 0;

        if (filename.contains(" ")) {
            String errorMsg = "Filename includes spaces (filename: " + filename + ")";
            ClientLogger.getInstance().error(errorMsg);
            throw new IOException(errorMsg);
        }

        String command = "LOAD " + filename;
        this.e.println(command);
        ClientLogger.getInstance().messageSent(this.c.getPort(), command);
        ClientLogger.getInstance().loadStarted(filename);

        byte[] fileData = null;

        try {
            fileData = this.a(filename); // a(...) must return byte[]
        } catch (a ignored) {
            // intentionally ignore or log if needed
        }

        while (fileData == null) {
            String retryCommand = "RELOAD " + filename;
            this.e.println(retryCommand);
            ClientLogger.getInstance().messageSent(this.c.getPort(), retryCommand);
            ClientLogger.getInstance().retryLoad(filename);

            try {
                fileData = this.a(filename);
            } catch (a ignored) {
                // repeat until non-null
            }
        }

        return fileData;
    }

    private byte[] a(String filename) throws IOException {
        String line;

        try {
            line = this.d.readLine();
        } catch (SocketTimeoutException e) {
            ClientLogger.getInstance().timeoutExpiredWhileReading(this.c.getPort());
            throw e;
        }

        ClientLogger.getInstance().messageReceived(this.c.getPort(), line);

        if (line == null) {
            String errorMsg = "Connection closed by the Controller";
            ClientLogger.getInstance().error(errorMsg);
            throw new IOException(errorMsg);
        }

        String[] tokens = line.split(" ");

        if (tokens[0].equals("ERROR_LOAD")) {
            ClientLogger.getInstance().loadFailed(filename, this.f);
            throw new IOException("Load operation for file " + filename + " failed after having contacted " + this.f
                    + " different Dstores");
        }

        if (tokens[0].equals("ERROR_FILE_DOES_NOT_EXIST")) {
            ClientLogger.getInstance().fileToLoadDoesNotExist(filename);
            throw new FileDoesNotExistException(filename);
        }

        if (tokens[0].equals("ERROR_NOT_ENOUGH_DSTORES")) {
            ClientLogger.getInstance().error("Not enough Dstores have joined the data store yet");
            throw new NotEnoughDstoresException();
        }

        if (!tokens[0].equals("LOAD_FROM")) {
            String errorMsg = "Unexpected message received (expected LOAD_FROM): " + line;
            ClientLogger.getInstance().error(errorMsg);
            throw new IOException(errorMsg);
        }

        int dstorePort;
        int expectedBytes;

        try {
            dstorePort = Integer.parseInt(tokens[1]);
            expectedBytes = Integer.parseInt(tokens[2]);
        } catch (NumberFormatException e) {
            String errorMsg = "Error parsing LOAD_FROM message. Received message: " + line;
            ClientLogger.getInstance().error(errorMsg);
            throw new IOException(errorMsg);
        }

        ClientLogger.getInstance().dstoreWhereToLoadFrom(filename, dstorePort, expectedBytes);

        byte[] fileBytes;

        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), dstorePort)) {
            ++this.f;
            ClientLogger.getInstance().connectionEstablished(socket.getPort());

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            InputStream in = socket.getInputStream();

            String loadCommand = "LOAD_DATA " + filename;
            out.println(loadCommand);

            ClientLogger.getInstance().messageSent(socket.getPort(), loadCommand);
            ClientLogger.getInstance().loadFromDstore(filename, dstorePort);

            try {
                fileBytes = in.readNBytes(expectedBytes);
            } catch (SocketTimeoutException e) {
                ClientLogger.getInstance().timeoutExpiredWhileReading(this.c.getPort());
                throw e;
            }

            if (fileBytes.length < expectedBytes) {
                throw new IOException(
                        "Expected to read " + expectedBytes + " bytes, but only read " + fileBytes.length);
            }

            ClientLogger.getInstance().loadCompleted(filename, dstorePort);
        } catch (IOException e) {
            ClientLogger.getInstance().loadFromDstoreFailed(filename, dstorePort);
            throw new a(this, e); // this custom exception class 'a' is assumed to exist in your code
        }

        return fileBytes;
    }

    public void remove(String filename) throws IOException, NotEnoughDstoresException, FileDoesNotExistException {
        String command = "REMOVE " + filename;
        this.e.println(command);
        ClientLogger.getInstance().messageSent(this.c.getPort(), command);
        ClientLogger.getInstance().removeStarted(filename);

        String response;

        try {
            response = this.d.readLine();
        } catch (SocketTimeoutException e) {
            ClientLogger.getInstance().timeoutExpiredWhileReading(this.c.getPort());
            ClientLogger.getInstance().removeFailed(filename);
            throw e;
        }

        ClientLogger.getInstance().messageReceived(this.c.getPort(), response);

        if (response == null) {
            String errorMsg = "Connection closed by the Controller";
            ClientLogger.getInstance().error(errorMsg);
            ClientLogger.getInstance().removeFailed(filename);
            throw new IOException(errorMsg);
        }

        String[] tokens = response.split(" ");

        switch (tokens[0]) {
            case "ERROR_FILE_DOES_NOT_EXIST":
                ClientLogger.getInstance().fileToRemoveDoesNotExist(filename);
                throw new FileDoesNotExistException(filename);

            case "REMOVE_COMPLETE":
                ClientLogger.getInstance().removeComplete(filename);
                return;

            case "ERROR_NOT_ENOUGH_DSTORES":
                ClientLogger.getInstance().error("Not enough Dstores have joined the data store yet");
                ClientLogger.getInstance().removeFailed(filename);
                throw new NotEnoughDstoresException();

            default:
                String errorMsg = "Unexpected message received. Expected message: REMOVE_COMPLETE";
                ClientLogger.getInstance().error(errorMsg);
                ClientLogger.getInstance().removeFailed(filename);
                throw new IOException(errorMsg);
        }
    }

    private final class a
            extends IOException {
        private static final long serialVersionUID = -5505350949933067170L;

        private a(Client client, IOException iOException) {
            super(iOException);
        }
    }
}
