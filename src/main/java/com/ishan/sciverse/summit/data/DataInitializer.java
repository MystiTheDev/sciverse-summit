package com.ishan.sciverse.summit.data;


import com.ishan.sciverse.summit.entity.User;
import com.ishan.sciverse.summit.repository.PresentationRepository;
import com.ishan.sciverse.summit.repository.UserRepository;
import com.ishan.sciverse.summit.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private PresentationRepository presentationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // Seed the one-time bootstrap admin account that creates the first chair.
        // It is deleted as soon as the first chair account is created, and is never re-seeded
        // once a chair exists.
        if (!UserService.hasChairWithUsers(userRepository)) {
            if (userRepository.findByUsername("admin").isEmpty()) {
                System.out.println("Seeding temporary admin account (username: admin) ...");
                User admin = new User();
                admin.setUsername("admin");
                admin.setEmail("admin@sciverse.local");
                admin.setFullName("Summit Administrator");
                admin.setPassword("admin123");
                admin.setRawPassword("admin123");
                admin.setRole("ADMIN");
                admin.setPassword(passwordEncoder.encode(admin.getPassword()));
                userRepository.save(admin);
            }
        }

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
