package com.ishan.sciverse.summit.data;




import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.ishan.sciverse.summit.repository.PresentationRepository;

import org.springframework.beans.factory.annotation.Autowired;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private PresentationRepository presentationRepository;

    @Override
    public void run(String... args) throws Exception {
        // Check if the database already contains data to prevent re-populating on every restart
        if (presentationRepository.count() == 0) {
            System.out.println("Seeding initial data...");

            // Create 5 sample Presentation records
            Presentation p1 = new Presentation("Student 1", true, true);
            Presentation p2 = new Presentation("Student 2", true, false);
            Presentation p3 = new Presentation("Student 3", false, true);
            Presentation p4 = new Presentation("Student 4", false, false);
            Presentation p5 = new Presentation("Student 5", true, true);

            // Save all records to the repository (and thus, the H2 file database)
            presentationRepository.save(p1);
            presentationRepository.save(p2);
            presentationRepository.save(p3);
            presentationRepository.save(p4);
            presentationRepository.save(p5);

            System.out.println("Data seeding complete. Total records: " + presentationRepository.count());
        } else {
            System.out.println("Database already contains data. Skipping initial seeding.");
        }
    }
}