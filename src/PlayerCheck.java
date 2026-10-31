import java.time.LocalDate;

public class PlayerCheck {
    public static void main(String[] args) {
        Player player = new Player(
                1, "Лев", 1700, 500.0,
                LocalDate.of(2026, 10, 31), true
        );

        System.out.println(player);
        System.out.println("Имя: " + player.getName());
        System.out.println("Рейтинг: " + player.getRating());

        try {
            new Player(-1, "Анна", 1500, 0.0, LocalDate.now(), true);
        } catch (IllegalArgumentException exception) {
            System.out.println("Проверка сработала: " + exception.getMessage());
        }
    }
}