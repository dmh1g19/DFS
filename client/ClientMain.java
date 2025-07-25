package client;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.Scanner;

import exceptions.NotEnoughDstoresException;
import utils.Loggert;
import utils.Loggert.LoggingType;

public class ClientMain {
	
	public static void main(String[] args) throws Exception{
		
		final int cport = Integer.parseInt(args[0]);
		int timeout = Integer.parseInt(args[1]);
		
		// this client expects a 'downloads' folder in the current directory; all files loaded from the store will be stored in this folder
		File downloadFolder = new File("downloads");
		if (!downloadFolder.exists())
			if (!downloadFolder.mkdir()) throw new RuntimeException("Cannot create download folder (folder absolute path: " + downloadFolder.getAbsolutePath() + ")");
		
		// this client expects a 'to_store' folder in the current directory; all files to be stored in the store will be collected from this folder
		File uploadFolder = new File("to_store");
		if (!uploadFolder.exists())
			throw new RuntimeException("to_store folder does not exist");
	
		// launch an interactive client
		//interactiveClient(cport, timeout, downloadFolder, uploadFolder);			
		
		// launch a single client
		//testClient(cport, timeout, downloadFolder, uploadFolder);

		// launch a number of concurrent clients, each doing the same operations
		for (int i = 0; i < 10; i++) {
			new Thread() {
				public void run() {
					test2Client(cport, timeout, downloadFolder, uploadFolder);
				}
			}.start();
		}
	}
	
	public static void test2Client(int cport, int timeout, File downloadFolder, File uploadFolder) {
		Client client = null;
		
		try {
			client = new Client(cport, timeout, Loggert.LoggingType.ON_FILE_AND_TERMINAL);
			client.connect();
			Random random = new Random(System.currentTimeMillis() * System.nanoTime());
			
			File fileList[] = uploadFolder.listFiles();
			for (int i=0; i<fileList.length/2; i++) {
				File fileToStore = fileList[random.nextInt(fileList.length)];
				try {					
					client.store(fileToStore);
				} catch (Exception e) {
					System.out.println("Error storing file " + fileToStore);
					e.printStackTrace();
				}
			}
			
			String list[] = null;
			try { list = list(client); } catch(IOException e) { e.printStackTrace(); }
			
			for (int i = 0; i < list.length/4; i++) {
				String fileToRemove = list[random.nextInt(list.length)];
				try {
					client.remove(fileToRemove);
				} catch (Exception e) {
					System.out.println("Error remove file " + fileToRemove);
					e.printStackTrace();
				}
			}
			
			try { list = list(client); } catch(IOException e) { e.printStackTrace(); }
			
		} catch(IOException e) {
			e.printStackTrace();
		} finally {
			if (client != null)
				try { client.disconnect(); } catch(Exception e) { e.printStackTrace(); }
		}
	}
	
	public static void testClient(int cport, int timeout, File downloadFolder, File uploadFolder) {
		Client client = null;
		
		try {
			
			client = new Client(cport, timeout, Loggert.LoggingType.ON_FILE_AND_TERMINAL);
		
			try { client.connect(); } catch(IOException e) { e.printStackTrace(); return; }
			
			try { list(client); } catch(IOException e) { e.printStackTrace(); }
			
			// store first file in the to_store folder twice, then store second file in the to_store folder once
			File fileList[] = uploadFolder.listFiles();
			if (fileList.length > 0) {
				try { client.store(fileList[0]); } catch(IOException e) { e.printStackTrace(); }				
				try { client.store(fileList[0]); } catch(IOException e) { e.printStackTrace(); }
			}
			if (fileList.length > 1) {
				try { client.store(fileList[1]); } catch(IOException e) { e.printStackTrace(); }
			}

			String list[] = null;
			try { list = list(client); } catch(IOException e) { e.printStackTrace(); }
			
			if (list != null)
				for (String filename : list)
					try { client.load(filename, downloadFolder); } catch(IOException e) { e.printStackTrace(); }
			
			if (list != null)
				for (String filename : list)
					try { client.remove(filename); } catch(IOException e) { e.printStackTrace(); }
			if (list != null && list.length > 0)
				try { client.remove(list[0]); } catch(IOException e) { e.printStackTrace(); }
			
			try { list(client); } catch(IOException e) { e.printStackTrace(); }
			
		} finally {
			if (client != null)
				try { client.disconnect(); } catch(Exception e) { e.printStackTrace(); }
		}
	}

	public static String[] list(Client client) throws IOException, NotEnoughDstoresException {
		System.out.println("Retrieving list of files...");
		String list[] = client.list();
		
		System.out.println("Ok, " + list.length + " files:");
		int i = 0; 
		for (String filename : list)
			System.out.println("[" + i++ + "] " + filename);
		
		return list;
	}

	public static void interactiveClient(int cport, int timeout, File downloadFolder, File uploadFolder) {
	    Client client = null;
	    Scanner scanner = new Scanner(System.in);

	    try {
	        client = new Client(cport, timeout, Loggert.LoggingType.ON_FILE_AND_TERMINAL);
	        client.connect();

	        System.out.println("Connected. Enter commands (store/load/remove/list/exit):");

	        while (true) {
	            System.out.print("> ");
	            String line = scanner.nextLine();
	            String[] parts = line.trim().split("\\s+", 2);

	            if (parts.length == 0 || parts[0].isEmpty()) {
	                continue;
	            }

	            String command = parts[0].toLowerCase();
	            String argument = parts.length > 1 ? parts[1].trim() : "";

	            try {
	                switch (command) {
	                    case "store":
	                        if (argument.isEmpty()) {
	                            System.out.println("Usage: store <filename>");
	                            break;
	                        }
	                        File fileToStore = new File(uploadFolder, argument);
	                        if (!fileToStore.exists()) {
	                            System.out.println("File not found in to_store folder: " + argument);
	                            break;
	                        }
	                        client.store(fileToStore);
	                        System.out.println("Stored file: " + argument);
	                        break;

	                    case "load":
	                        if (argument.isEmpty()) {
	                            System.out.println("Usage: load <filename>");
	                            break;
	                        }
	                        client.load(argument, downloadFolder);
	                        System.out.println("Loaded file to downloads: " + argument);
	                        break;

	                    case "remove":
	                        if (argument.isEmpty()) {
	                            System.out.println("Usage: remove <filename>");
	                            break;
	                        }
	                        client.remove(argument);
	                        System.out.println("Removed file: " + argument);
	                        break;

	                    case "list":
	                        String[] files = client.list();
	                        System.out.println("Files in store:");
	                        for (int i = 0; i < files.length; i++) {
	                            System.out.println("[" + i + "] " + files[i]);
	                        }
	                        break;

	                    case "exit":
	                        System.out.println("Exiting interactive client.");
	                        return;

	                    default:
	                        System.out.println("Unknown command: " + command);
	                        System.out.println("Available commands: store, load, remove, list, exit");
	                        break;
	                }
	            } catch (Exception e) {
	                System.out.println("Error executing command: " + e.getMessage());
	                e.printStackTrace();
	            }
	        }
	    } catch (Exception e) {
	        System.out.println("Error connecting client: " + e.getMessage());
	        e.printStackTrace();
	    } finally {
	        if (client != null) {
	            try {
	                client.disconnect();
	            } catch (Exception e) {
	                e.printStackTrace();
	            }
	        }
	        scanner.close();
	    }
	}

	
}
