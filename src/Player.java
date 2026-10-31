import java.time.LocalDate;

/*Одна запись в базе участников шахматного клуба. */
public class Player {
    public static final int MAX_NAME_LENGTH = 50;

    private final int id;
    private final String name;
    private final int rating;
    private final double fee;
    private final LocalDate registeredOn;
    private final boolean active;

    public Player(int id, String name, int rating, double fee,
                  LocalDate registeredOn, boolean active) {

        if (id <= 0) {
            throw new IllegalArgumentException("ID должен быть положительным");
        }

        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Имя не должно быть пустым");
        }

        name = name.trim();

        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Имя не должно превышать " + MAX_NAME_LENGTH + " символов"
            );
        }

        if (rating < 0 || rating > 4000) {
            throw new IllegalArgumentException("Рейтинг должен быть от 0 до 4000");
        }

        if (!Double.isFinite(fee) || fee < 0) {
            throw new IllegalArgumentException(
                    "Взнос должен быть конечным неотрицательным числом"
            );
        }

        if (registeredOn == null) {
            throw new IllegalArgumentException("Не указана дата регистрации");
        }

        this.id = id;
        this.name = name;
        this.rating = rating;
        this.fee = fee;
        this.registeredOn = registeredOn;
        this.active = active;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getRating() {
        return rating;
    }

    public double getFee() {
        return fee;
    }

    public LocalDate getRegisteredOn() {
        return registeredOn;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public String toString() {
        return "Player{id=" + id
                + ", name='" + name + "'"
                + ", rating=" + rating
                + ", fee=" + fee
                + ", registeredOn=" + registeredOn
                + ", active=" + active + "}";
    }
}