/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.tombrophy_client;
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
/**
 *
 * @author tombr
 */
public class HTTPImporter {
    private static final DateTimeFormatter[] DATE_FMTS = new DateTimeFormatter[] {
        new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMMM yyyy").toFormatter(Locale.ENGLISH),
        DateTimeFormatter.ISO_LOCAL_DATE
    };
    
    private static final DateTimeFormatter[] TIME_FMTS = new DateTimeFormatter[] {
        new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("h a").toFormatter(Locale.ENGLISH),     
        new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("h:mm a").toFormatter(Locale.ENGLISH),  
        DateTimeFormatter.ofPattern("HH:mm")                                                                         
    };
    
    public static void importEventsFromHttp(String urlString, PrintWriter toServer, BufferedReader fromServer) {
        int imported = 0;
        int skipped  = 0;

        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET"); // <-- HTTP GET
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "EventBoardClient/1.0");
            conn.setRequestProperty("Accept", "text/plain");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            int code = conn.getResponseCode();
            if (code >= 400) {
                System.out.println("Import failed: HTTP " + code);
                return;
            }

            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                int lineNo = 0;

                while ((line = r.readLine()) != null) {
                    lineNo++;
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    String[] parts = line.split(";", 4);
                    if (parts.length != 3) {
                        System.out.println("Skipped line " + lineNo + ": needs exactly 3 fields separated by ';'");
                        skipped++;
                        continue;
                    }

                    String date = parts[0].trim();
                    String time = parts[1].trim();
                    String desc = parts[2].trim();

                    if (!isValidDate(date)) {
                        System.out.println("Skipped line " + lineNo + ": bad date '" + date + "' (e.g., '2 November 2025' or '2025-11-02')");
                        skipped++;
                        continue;
                    }
                    if (!isValidTime(time)) {
                        System.out.println("Skipped line " + lineNo + ": bad time '" + time + "' (e.g., '6 pm', '7:30 pm' or '18:00')");
                        skipped++;
                        continue;
                    }
                    if (desc.isEmpty()) {
                        System.out.println("Skipped line " + lineNo + ": description is empty");
                        skipped++;
                        continue;
                    }

                    // Spec: send to server as-is (don’t reformat)
                    String cmd = "add; " + date + "; " + time + ", " + desc;
                    toServer.println(cmd);
                    toServer.flush();

                    String reply = fromServer.readLine();
                    System.out.println(reply != null ? reply : "(no reply from server)");

                    imported++;
                }
            }

            System.out.println("Imported: " + imported + "; Skipped: " + skipped);

        } catch (MalformedURLException e) {
            System.out.println("Import failed: bad URL");
        } catch (FileNotFoundException e) {
            System.out.println("Import failed: 404 Not Found (check Raw link)");
        } catch (IOException e) {
            System.out.println("Import failed: " + e.getMessage());
        }
    }

    private static boolean isValidDate(String s) {
        for (DateTimeFormatter f : DATE_FMTS) {
            try { 
                LocalDate.parse(s, f); return true; 
            }
            catch (DateTimeException ignored) {}
        }
        return false;
    }

    private static boolean isValidTime(String s) {
        for (DateTimeFormatter f : TIME_FMTS) {
            try { LocalTime.parse(s, f); return true; }
            catch (DateTimeException ignored) {}
        }
        return false;
    }
}
