package com.jorge.prreviewer.service;

import com.jorge.prreviewer.dto.ReviewResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class ReviewServiceEvaluationTest {

    private static final Logger log = LoggerFactory.getLogger(ReviewServiceEvaluationTest.class);

    @Autowired
    private ReviewService reviewService;

    record EvalCase(String diff, String fileName, String expectedCategory) {}

    private static final List<EvalCase> EVAL_CASES = List.of(
            new EvalCase(
                    """
                    - public User findById(String id) {
                    -     return db.query("SELECT * FROM users WHERE id = ?");
                    + public User findById(String id) {
                    +     String query = "SELECT * FROM users WHERE id = " + id;
                    +     return db.query(query);
                    """,
                    "UserRepository.java",
                    "SECURITY"
            ),
            new EvalCase(
                    """
                    - public String getUserName(Long userId) {
                    -     User user = userRepository.findById(userId);
                    -     return user.getName();
                    + public String getUserName(Long userId) {
                    +     User user = userRepository.findById(userId);
                    +     if (user == null) {
                    +         return "unknown";
                    +     }
                    +     return user.getName();
                    """,
                    "UserService.java",
                    "BUG"
            ),
            new EvalCase(
                    """
                    - public class Counter {
                    -     private AtomicInteger count = new AtomicInteger(0);
                    -     public void increment() {
                    -         count.incrementAndGet();
                    -     }
                    + public class Counter {
                    +     private int count = 0;
                    +     public void increment() {
                    +         count = count + 1;
                    +     }
                    """,
                    "Counter.java",
                    "BUG"
            ),
            new EvalCase(
                    """
                    - public List<UserDTO> getAllUsers() {
                    -     List<User> users = userRepository.findAll();
                    -     Map<Long, Profile> profiles = profileRepository
                    -         .findAllByUserIds(users.stream().map(User::getId).toList());
                    -     return users.stream()
                    -         .map(u -> toDTO(u, profiles.get(u.getId())))
                    -         .toList();
                    + public List<UserDTO> getAllUsers() {
                    +     List<User> users = userRepository.findAll();
                    +     List<UserDTO> dtos = new ArrayList<>();
                    +     for (User user : users) {
                    +         Profile profile = profileRepository.findByUserId(user.getId());
                    +         dtos.add(toDTO(user, profile));
                    +     }
                    +     return dtos;
                    """,
                    "UserService.java",
                    "PERFORMANCE"
            ),
            new EvalCase(
                    """
                    - public boolean isActive(User user) {
                    -     return user.getActive();
                    + public boolean isActive(User user) {
                    +     if (user.getActive() == true) {
                    +         return true;
                    +     }
                    +     return false;
                    """,
                    "UserService.java",
                    "STYLE"
            )
    );

    @Test
    void evaluarDeteccionDeCategorias() {
        int aciertos = 0;

        for (EvalCase evalCase : EVAL_CASES) {
            try {
                ReviewResponse response = reviewService.review(
                        new com.jorge.prreviewer.dto.ReviewRequest(evalCase.diff(), evalCase.fileName()));

                List<String> detectedCategories = response.getComments().stream()
                        .map(c -> c.getCategory().name())
                        .toList();

                boolean acierto = detectedCategories.contains(evalCase.expectedCategory());
                if (acierto) aciertos++;

                log.info("[EVAL] {} -> esperado: {}, obtenido: {}, ¿acierto?: {}",
                        evalCase.fileName(),
                        evalCase.expectedCategory(),
                        detectedCategories,
                        acierto);
            } catch (Exception e) {
                log.error("[EVAL] {} -> ERROR: no se pudo completar (excepción: {})",
                        evalCase.fileName(), e.getMessage());
            }

            try {
                Thread.sleep(15000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Sleep interrumpido: {}", e.getMessage());
            }
        }

        log.info("=========================================");
        log.info("Resumen: Aciertos: {}/{}", aciertos, EVAL_CASES.size());
        log.info("=========================================");
    }
}
