/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.mycompany.tombrophy_client;
import java.io.*;
import java.net.*;

/**
 *
 * @author tombr
 */
public class TomBrophy_Client {
    private static InetAddress host;
    private static final int PORT = 1012;
    
    
    public static void main(String[] args) {
        try {
            host = InetAddress.getLocalHost();
        }
        catch (UnknownHostException e) {
            System.out.println("Host missing");
            return;
        }
        run();
    }
    
    //This is will run the client and tell the user if the server is running or not.
    private static void run() {
        try (
            Socket link = new Socket(host, PORT);
            BufferedReader in = new BufferedReader(new InputStreamReader(link.getInputStream()));
            PrintWriter out = new PrintWriter(link.getOutputStream(),true);
            BufferedReader userEntry = new BufferedReader(new InputStreamReader(System.in))) {
            
            //This reads the banner.
            String banner = in.readLine();
            if (banner != null) {
                System.out.println("SERVER> " + banner);
                
                //This is the url that the project is importing to.
                String defaultUrl = "https://raw.githubusercontent.com/TomBrophy101/23405724_TomBrophy/master/23045724_TomBrophy_Server/events.txt";
                System.out.println("Importing intials events from: " + defaultUrl);
                HTTPImporter.importEventsFromHttp(defaultUrl, out, in);
                
                //The commands that have to be entered are add, remove or list and it includes the action, date, time and the Description.
                System.out.println("\nType a specific command to the server: add, remove, or list, make sure it also includes a date, time and description and Type STOP to quit");
                while (true) {
                    String message =  userEntry.readLine();
                    if (message == null) {
                        break;
                    }
                    message = message.trim();
                    if (message.isEmpty()) {
                        continue;
                    }
                
                    if (message.toLowerCase().startsWith("import")) {
                        String[] parts = message.split(";", 2);
                        if (parts.length == 2) {
                            String urlString = parts[1].trim();
                            System.out.println("Attempting to import events from " + urlString);
                            HTTPImporter.importEventsFromHttp(urlString, out, in);
                            continue;
                        } else {
                            System.out.println("The import is invalid");
                            continue;
                        }
                    }
                    
                    out.println(message); 
                
                    String response = in.readLine();
                    if (response == null) {
                        System.out.println("The SERVER is closed");
                        break;
                    }
                    //This is the response from the server.
                    System.out.println("\nSERVER RESPONSE> " + response);
                    
                    //This will terminate the client when STOP is typed.
                    if ("TERMINATE".equalsIgnoreCase(response)) {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            //This will show when an error occurs.
            System.out.println("I/O error: " + e.getMessage());
        }
    }
}
