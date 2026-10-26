package psr.sc;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Calendar;

/** Получение точного времени с NTP-сервера. */
public final class NtpClient {

    private static final int NTP_PORT = 123;
    private static final int TIMEOUT_MS = 3000;
    private static final long DIFF_1900_1970 = 2208988800L;

    /** Возвращает Calendar с текущим временем. Источник: настройки time_source и ntp_server. */
    public static Calendar now() {
        String source = Settings.get("time_source");
        if ("ntp".equals(source)) {
            String server = Settings.get("ntp_server");
            if (server != null && !server.trim().isEmpty()) {
                try {
                    long ms = getTime(server.trim());
                    if (ms > 0) {
                        Calendar c = Calendar.getInstance();
                        c.setTimeInMillis(ms);
                        return c;
                    }
                } catch (Exception ignored) { }
            }
        }
        return Calendar.getInstance();
    }

    /** Запрос времени с NTP-сервера. Возвращает миллисекунды с эпохи Unix или -1 при ошибке. */
    public static long getTime(String host) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TIMEOUT_MS);
            InetAddress address = InetAddress.getByName(host);
            byte[] buf = new byte[48];
            buf[0] = 0x1B;
            long sendTime = System.currentTimeMillis();
            DatagramPacket sendPacket = new DatagramPacket(buf, buf.length, address, NTP_PORT);
            socket.send(sendPacket);

            DatagramPacket receivePacket = new DatagramPacket(new byte[48], 48);
            socket.receive(receivePacket);

            long receiveTime = System.currentTimeMillis();
            byte[] data = receivePacket.getData();

            long seconds = ((long)(data[40] & 0xFF) << 24)
                | ((long)(data[41] & 0xFF) << 16)
                | ((long)(data[42] & 0xFF) << 8)
                | (data[43] & 0xFF);
            long fraction = ((long)(data[44] & 0xFF) << 24)
                | ((long)(data[45] & 0xFF) << 16)
                | ((long)(data[46] & 0xFF) << 8)
                | (data[47] & 0xFF);

            long ntpTime = (seconds - DIFF_1900_1970) * 1000L + (fraction * 1000L) / 0x100000000L;

            long roundTrip = receiveTime - sendTime;
            long offset = ntpTime - receiveTime + roundTrip / 2;
            return System.currentTimeMillis() + offset;
        } catch (Exception e) {
            return -1;
        }
    }

    private NtpClient() { }
}
