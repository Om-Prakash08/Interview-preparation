package hotel;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Demo {
    public static void main(String[] args) {
        System.out.println("=== FAANG System Design Demo: Hotel Booking System ===");

        HotelManagementSystem system = HotelManagementSystem.getInstance();

        // 1. Initialize Hotel and Rooms
        Hotel hotel = new Hotel("Grand Palace Hotel", "San Francisco");
        Room r1 = new Room("101", RoomStyle.STANDARD, 100.0);
        Room r2 = new Room("102", RoomStyle.DELUXE, 150.0);
        Room r3 = new Room("103", RoomStyle.SUITE, 250.0);

        hotel.addRoom(r1);
        hotel.addRoom(r2);
        hotel.addRoom(r3);

        system.addHotel(hotel);

        System.out.println("Hotel 'Grand Palace Hotel' registered with Rooms: 101 (Standard), 102 (Deluxe), 103 (Suite).");

        // 2. Search Available Rooms
        System.out.println("\n--- Search available Deluxe rooms for next 3 days ---");
        LocalDate today = LocalDate.now();
        LocalDate threeDaysLater = today.plusDays(3);

        List<Room> availableDeluxe = system.searchAvailableRooms(hotel, RoomStyle.DELUXE, today, threeDaysLater);
        for (Room r : availableDeluxe) {
            System.out.printf("Found available Deluxe Room: %s ($%.2f/night)%n", r.getRoomNumber(), r.getPricePerNight());
        }

        // 3. Scenario 1: Bob books Room 102
        System.out.println("\n--- Bob books Room 102 ---");
        Guest bob = new Guest("Bob", "bob@example.com", "555-0100");
        RoomBooking booking1 = system.bookRoom(hotel, "102", bob, today, threeDaysLater);

        // 4. Scenario 2: Charlie attempts booking overlapping dates
        System.out.println("\n--- Charlie attempts overlapping booking for Room 102 ---");
        Guest charlie = new Guest("Charlie", "charlie@example.com", "555-0200");
        system.bookRoom(hotel, "102", charlie, today.plusDays(1), today.plusDays(4));

        // 5. Scenario 3: Charlie books Room 102 for future non-overlapping dates
        System.out.println("\n--- Charlie books Room 102 for non-overlapping future dates ---");
        system.bookRoom(hotel, "102", charlie, today.plusDays(5), today.plusDays(7));

        // 6. Scenario 4: Lifecycle flows (Check-in, Check-out, Housekeeping)
        System.out.println("\n--- Room Lifecycle: Check-In -> Check-Out -> Housekeeping ---");
        if (booking1 != null) {
            system.checkIn(booking1.getBookingId());
            system.checkOut(booking1.getBookingId());
            system.completeHousekeeping(hotel, "102");
        }

        // 7. Scenario 5: Concurrency check (Multiple booking requests for the same room & dates)
        System.out.println("\n--- Concurrency check: Simultaneous booking requests for Room 103 ---");
        System.out.println("3 guests attempt to book Room 103 for the same dates simultaneously:");
        
        ExecutorService executor = Executors.newFixedThreadPool(3);
        Guest g1 = new Guest("Guest 1", "g1@example.com", "555-9001");
        Guest g2 = new Guest("Guest 2", "g2@example.com", "555-9002");
        Guest g3 = new Guest("Guest 3", "g3@example.com", "555-9003");

        LocalDate futureStart = today.plusDays(10);
        LocalDate futureEnd = today.plusDays(12);

        executor.submit(() -> system.bookRoom(hotel, "103", g1, futureStart, futureEnd));
        executor.submit(() -> system.bookRoom(hotel, "103", g2, futureStart, futureEnd));
        executor.submit(() -> system.bookRoom(hotel, "103", g3, futureStart, futureEnd));

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("\n=== Demo Finished successfully ===");
    }
}
