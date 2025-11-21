/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.tombrophy_server;

import java.io.*;
import java.net.*;
/**
 *
 * @author tombr
 */
public class TomBrophy_ClientRun implements Runnable {
    Socket client_link = null;
    String EventBoardClientID;
    
    public TomBrophy_ClientRun(Socket connection, String eID) {
        this.client_link = connection;
        EventBoardClientID = eID;
    }
    
    @Override
    public void run () {
        try {
            BufferedReader in = new BufferedReader( new InputStreamReader(client_link.getInputStream()));
            PrintWriter out = new PrintWriter(client_link.getOutputStream(),true);
            
            String message = in.readLine();
            System.out.println("Message received from client:" + EventBoardClientID + " " + message);
            out.println("Echo Message: " + message);
        } catch(IOException e) {
            //e.printStackTrace();
        }
        
        finally
        {
            try {
                System.out.println("\n* Closing connection with the client " + EventBoardClientID + " ... ");
                client_link.close();
            }
            catch(IOException e) {
                System.out.println("Disconnection failed!");
            }
        }
    }
}
