package com.mycompany.tombrophy_server;

import java.io.*;
import java.net.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.*;

// Simple class to hold event data
class Event {
    String date;
    String time;
    String description;

    public Event(String date, String time, String description) {
        this.date = date.trim();
        this.time = time.trim();
        this.description = description.trim();
    }

    // This is used to uniquely identify an event for removal
    @Override
    public String toString() {
        return String.format("%s; %s; %s", date, time, description);
    }
    
    @Override
    public boolean equals(Object o ) {
        if (this == o) 
            return true;
        if (!(o instanceof Event))
            return false;
        Event other = (Event) o;
        return date.equalsIgnoreCase(other.date) && time.equalsIgnoreCase(other.time) && description.equalsIgnoreCase(other.description);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(
                date.toLowerCase(),
                time.toLowerCase(),
                description.toLowerCase()
        );
    }
}

public class TomBrophy_Server {
    private static final int PORT = 1012; // Using a standard port for the server
    // Thread-safe map to store events, keyed by date for easy listing
    private static final String EVENTS_FILE = "events.txt";
    private final Map<String, List<Event>> eventBoard = new ConcurrentHashMap<>();

    public void start() {
        System.out.println("TomBrophy_Server started on port: " + PORT);
        
        loadEventsFromFile();
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                // Accept new client connection and start a new thread to handle it
                Socket clientSocket = serverSocket.accept();
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.err.println("Could not listen on port " + PORT);
            System.exit(1);
        }
    }

    private class ClientHandler extends Thread {
        private final Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
            System.out.println("Client connected: " + socket.getRemoteSocketAddress());
        }

        @Override
        public void run() {
            try (
                // Setup input and output streams for the client socket
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))
            ) {
                String serverAddress = clientSocket.getLocalAddress().getHostAddress() + ":" + clientSocket.getLocalPort();
                String clientAddress = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
                
                // Send welcome message to the client
                out.println("WELCOME To EventBoard. Connected to server at " + serverAddress + ", Your address is " + clientAddress);

                String clientMessage;
                // Main loop to read messages from the client
                while ((clientMessage = in.readLine()) != null) {
                    String response;
                    // Send the response back to the client
                    try {
                        response = processMessage(clientMessage);
                    } catch (InvalidCommandException e) {
                        response = "ERROR: " + e.getMessage();
                    }
                    
                    out.println(response);
                   
                    // Check for the termination signal
                    if ("TERMINATE".equalsIgnoreCase(response)) {
                        break;
                    }
                }
            } catch (IOException e) {
                System.err.println("Client handler error: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                    System.out.println("Client disconnected: " + clientSocket.getRemoteSocketAddress());
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        private String processMessage(String message) throws InvalidCommandException {
            message = message.trim();
            if (message == null || message.isEmpty()) {
                throw new InvalidCommandException("ERROR: Empty message.");
            }

            // Split the message by semicolon. Use 4 to keep trailing empty strings.
            String[] parts = message.split(";", 4);
            String action = parts[0].trim().toLowerCase();

            // 1. Handle STOP command first (Protocol-level command)
            if ("stop".equals(action)) {
                return "TERMINATE";
            }

            // Check for minimum parts for other commands
            if (parts.length < 2) {
                return "ERROR: Malformed message. Missing action or event details.";
            }

            // Extract event details
            String date = parts.length > 1 ? parts[1].trim() : "";
            String time = parts.length > 2 ? parts[2].trim() : "";
            String desc = parts.length > 3 ? parts[3].trim() : "";

            switch (action) {
                case "add":
                    // Assignment: action; date; time; description (4 fields)
                    if (parts.length < 4) {
                        throw new InvalidCommandException("Usage: add; date; time; description");
                    }
                    
                    
                    validateDate(date);
                    
                    validateTime(time);

                    Event eventAdded = new Event(date, time, desc);
                   
                    // Synchronized block for thread safety on the map
                    synchronized (eventBoard) {
                        // Get or create the list for the given date
                        List<Event> eventsForDate = eventBoard.computeIfAbsent(date, k -> Collections.synchronizedList(new ArrayList<>()));
                        eventsForDate.add(eventAdded);
                        Collections.sort(eventsForDate, Comparator.comparing(e -> e.time));
//                        boolean eventExists = eventsForDate.stream().anyMatch(e -> 
//                                e.date.trim().equalsIgnoreCase(eventToAdd.date.trim()) &&
//                                e.time.trim().equalsIgnoreCase(eventToAdd.time.trim()) &&
//                                e.description.trim().equalsIgnoreCase(eventToAdd.description.trim())        
//                        );
//                        
//                        if (!eventExists) {
//                            eventsForDate.add(eventToAdd);
//                            // Sort the list by time (simple string comparison for now)
//                            Collections.sort(eventsForDate, Comparator.comparing(e -> e.time));
//                        }
                    }
                   
                    saveEventsToFile();
                    
                    // Server replies with a list of all events due on the new event's date.
                    return eventAdded.date + "; " + eventAdded.time + ", " + eventAdded.description + " has been added";

                case "remove":
                    // Assignment: action; date; time; description (4 fields)
                    if (parts.length < 4) {
                        throw new InvalidCommandException("Usage: remove; date; time; description");
                    }
                    
                    validateDate(date);
                    validateTime(time);
                   
                    //Event eventToRemove = new Event(date, time, desc);
                    
                    List<Event> eventsOnDate = eventBoard.get(date);
                   
                    if (eventsOnDate != null) {
                        Event removedEvent = null;
                        
                        Iterator<Event> it = eventsOnDate.iterator();
                        while (it.hasNext()) {
                            Event e = it.next();
                            if (e.date.trim().equalsIgnoreCase(date.trim()) &&
                                e.time.trim().equalsIgnoreCase(time.trim()) &&
                                e.description.trim().equalsIgnoreCase(desc.trim())) {
                                
                                removedEvent = e;
                                it.remove();
                                break;
                            }
                        }
                        
                        if(removedEvent == null) {
                            return "Error: Event not found on " + date;
                        }
                        
                        Collections.sort(eventsOnDate, Comparator.comparing(e -> e.time));
                        
                        saveEventsToFile();
                       
                        // Server replies with a list of all events that are still due on the date of the removed event.
                        return removedEvent.date + "; " + removedEvent.time +", " + removedEvent.description + " has been removed";
                    }
                   
                    return "no events";

                case "list":
                    // Assignment: action; date (minimum 2 fields)
                    if (parts.length < 2) {
                        return "ERROR: Usage: list; date";
                    }
                   
                    List<Event> listEvents = eventBoard.get(date);
                   
                    // Server replies with a list of all events due on that date (or "no events").
                    return formatEventList(listEvents);

                default:
                    throw new InvalidCommandException("Invalid command: " + action);
            }
        }
       
        // Helper method to format the list of events as a string
        private String formatEventList(List<Event> events) {
            if (events == null || events.isEmpty()) {
                return "no events";
            }
            
            String date = events.get(0).date;
            
            StringBuilder sb = new StringBuilder();
            sb.append(date).append("; ");
            
            for (int i = 0; i < events.size(); i++) {
                Event e = events.get(i);
                
                if (i > 0) {
                    sb.append("; ");
                }
                //This is to make the response from the server after the time be a comma and not a semi-colon
                sb.append(e.time)
                  .append(", ")
                  .append(e.description);
            }
            return sb.toString();
        }
        
        private void validateDate(String date) throws InvalidCommandException {
            if (date == null || date.isEmpty()) {
                throw new InvalidCommandException("No Date Found.");
            }
            
            String[] parts = date.trim().split("\\s+");
            if(parts.length != 3) {
                throw new InvalidCommandException(
                    "You entered: " + date + ". You're supposed to format the date and include a day, month and year"
                );
            }
            
            int day;
            
            try {
                day = Integer.parseInt(parts[0]);
            } catch (NumberFormatException e) {
                throw new InvalidCommandException("You didn't enter a number. You entered: " + parts[0] + ".");
            }
            if (day < 1 || day > 31) {
                throw new InvalidCommandException("The Day you entered is too high or too low. You entered: " + parts[0] + ".");
            }
            
            String month = parts[1].toLowerCase(Locale.ROOT);
            Set<String> months = new HashSet<>(Arrays.asList(
                    "january", "february", "march", "april", "may", "june",
                    "july", "august", "september", "october", "november", "december"
            ));
            if (!months.contains(month)) {
                throw new InvalidCommandException("Month must be a full month name. You entered: " + parts[1] + ".");
            }
            
            int year;
            
            try {
                year = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                throw new InvalidCommandException("Year must be a number. You entered: " + parts[2] + ".");
            }
            if (year < 1900 || year > 2100) {
                throw new InvalidCommandException("Year must be between 1900 and 2100. You entered: " + parts[2] + ".");
            }
        }
        
        private void validateTime(String time) throws InvalidCommandException {
            if (time == null || time.isEmpty()) {
                throw new InvalidCommandException("No Time Found.");
            }
            
            String t = time.toLowerCase(Locale.ROOT);
            if (!(t.contains("am") || t.contains("pm"))) {
                throw new InvalidCommandException(
                        "You entered: " + time + ". You're supposed to include 'am' or 'pm'. Try again"
                );
            }
        }
    }
    
    private static final DateTimeFormatter[] DATE_FMTS = new DateTimeFormatter[] {
        new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("d MMMM yyyy")
            .toFormatter(Locale.ENGLISH),
        DateTimeFormatter.ISO_LOCAL_DATE
    };
    
    private LocalDate parseDate(String s) {
        for (DateTimeFormatter f : DATE_FMTS) {
            try {
                return LocalDate.parse(s.trim(), f);
            } catch (DateTimeParseException ignored) {}
        }
        return LocalDate.MAX;
    }
    
    private synchronized void saveEventsToFile() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(EVENTS_FILE))) {
            
            List<String> dates = new ArrayList<>(eventBoard.keySet());
            dates.sort((d1, d2) -> parseDate(d1).compareTo(parseDate(d2)));
            
            for (String dateKey : dates) {
                List<Event> events = eventBoard.get(dateKey);
                if (events == null || events.isEmpty()) 
                    continue;
                
                List<Event> copy = new ArrayList<>(events);
                copy.sort(Comparator.comparing(e -> e.time));
                
                for (Event e : copy) {
                    writer.printf("%s; %s; %s%n", e.date, e.time, e.description);
                }
            }
//            for (List<Event> events : eventBoard.values()) {
//                for (Event e : events) {
//                    writer.printf("%s; %s; %s%n", e.date, e.time, e.description);
//                }
//            }
        } catch (IOException e) {
            System.err.println("Failed to save events to file: " + e.getMessage());
        }
    }
    
    private synchronized void loadEventsFromFile() {
        File file = new File(EVENTS_FILE);
        if (!file.exists()) {
            return;
        }
        
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;
                
                String[] parts = line.split(";", 3);
                if (parts.length < 3) 
                    continue;
                
                String date = parts[0].trim();
                String time = parts[1].trim();
                String desc = parts[2].trim();
                
                Event e = new Event(date, time, desc);
                
                synchronized (eventBoard) {
                    List<Event> list = eventBoard.computeIfAbsent(
                            date, k -> Collections.synchronizedList(new ArrayList<>()));
                    list.add(e);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load events from file: " + e.getMessage());
        }
        
    }
    public static void main(String[] args) {
        new TomBrophy_Server().start();
    }
}