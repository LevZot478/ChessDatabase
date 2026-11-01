import java.time.LocalDate;
import java.util.function.Predicate;

/**
 * Поля участника и проверка точного совпадения значения.
 */
public enum PlayerField {
    ID("ID"),
    NAME("Имя"),
    RATING("Рейтинг"),
    FEE("Взнос"),
    REGISTERED_ON("Дата"),
    ACTIVE("Активен");

    private final String title;

    PlayerField(String title) {
        this.title = title;
    }

    @Override
    public String toString() {
        return title;
    }

    public Predicate<Player> condition(String input) {
        String value = input.trim();

        switch (this) {
            case ID: {
                int id = Integer.parseInt(value);
                return player -> player.getId() == id;
            }

            case NAME:
                return player -> player.getName().equals(value);

            case RATING: {
                int rating = Integer.parseInt(value);
                return player -> player.getRating() == rating;
            }

            case FEE: {
                double fee = Double.parseDouble(value.replace(',', '.'));

                if (!Double.isFinite(fee) || fee < 0) {
                    throw new IllegalArgumentException("Некорректный взнос");
                }

                return player -> player.getFee() == fee;
            }

            case REGISTERED_ON: {
                LocalDate date = LocalDate.parse(value);
                return player -> player.getRegisteredOn().equals(date);
            }

            case ACTIVE: {
                if (!value.equalsIgnoreCase("true")
                        && !value.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException(
                            "Введи true или false"
                    );
                }

                boolean active = Boolean.parseBoolean(value);
                return player -> player.isActive() == active;
            }

            default:
                throw new IllegalArgumentException("Неизвестное поле");
        }
    }
}