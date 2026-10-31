import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.time.LocalDate;

/**
 * Записывает и читает данные одного участника.
 * Каждая запись занимает ровно 125 байт.
 */
public final class PlayerCodec {
    public static final int RECORD_SIZE =
            4 + Player.MAX_NAME_LENGTH * 2 + 4 + 8 + 8 + 1;

    private PlayerCodec() {
    }

    public static void write(DataOutput output, Player player)
            throws IOException {

        output.writeInt(player.getId());

        String name = player.getName();

        for (int i = 0; i < Player.MAX_NAME_LENGTH; i++) {
            if (i < name.length()) {
                output.writeChar(name.charAt(i));
            } else {
                output.writeChar(' ');
            }
        }

        output.writeInt(player.getRating());
        output.writeDouble(player.getFee());
        output.writeLong(player.getRegisteredOn().toEpochDay());
        output.writeBoolean(player.isActive());
    }

    public static Player read(DataInput input) throws IOException {
        int id = input.readInt();

        StringBuilder name = new StringBuilder();

        for (int i = 0; i < Player.MAX_NAME_LENGTH; i++) {
            name.append(input.readChar());
        }

        int rating = input.readInt();
        double fee = input.readDouble();
        LocalDate registeredOn = LocalDate.ofEpochDay(input.readLong());
        boolean active = input.readBoolean();

        return new Player(
                id,
                name.toString().trim(),
                rating,
                fee,
                registeredOn,
                active
        );
    }
}