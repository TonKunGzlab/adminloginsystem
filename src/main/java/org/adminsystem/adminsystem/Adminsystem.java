package org.adminsystem.adminsystem;
import java.util.concurrent.TimeUnit;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.event.EventHandler;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;

public final class Adminsystem extends Plugin {

    private MySQLConnector mySQLConnector;

    @Override
    public void onEnable() {
        // กำหนดค่าในการเชื่อมต่อกับ MySQL Database

        String host = "localhost";
        String database = "login";
        String username = "yourusername";
        String password = "yourpassword";

        this.mySQLConnector = new MySQLConnector(host, database, username, password);

        // ลงทะเบียน EventListener
        getProxy().getPluginManager().registerListener(this, new EventListener());

        // เริ่ม TaskScheduler ที่จะตรวจสอบและเปลี่ยนสถานะทุก 10 วินาที
        getProxy().getScheduler().schedule(this, this::checkAndChangeStatus, 1, 1, TimeUnit.SECONDS);

    }

    @Override
    public void onDisable() {
        this.mySQLConnector.closeConnection();

    }

    private void checkAndChangeStatus() {
        try {
            // ดึงค่า status จาก MySQL


//            String currentStatus = getStatusFromMySQL(ipAddress);
            String currentStatus = getStatusFromMySQL();  // เพิ่มเมธอดหรือวิธีที่ใช้ดึง status จาก MySQL

            // ตรวจสอบว่า status เป็น "online" หรือไม่
            if ("online".equals(currentStatus)) {
                getLogger().info("Status is online. Waiting for 10 seconds...");

                // รอ 10 วินาที
                try {
                    TimeUnit.SECONDS.sleep(30);  // 10000 มิลลิวินาที = 10 วินาที
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // สร้างคำสั่ง SQL เพื่ออัปเดต status เป็น "offline" สำหรับผู้เล่นที่มี pre_join เป็น 'nojoin'
                String query = "UPDATE player_data SET status = 'offline' WHERE status = 'online' AND pre_join = 'nojoin'";

                try (PreparedStatement statement = mySQLConnector.getConnection().prepareStatement(query)) {
                    int affectedRows = statement.executeUpdate();

                    if (affectedRows > 0) {
                        getLogger().info("Updated status for offline players.");
                    }
                }
            } else {
                // ถ้า status ไม่ได้เป็น "online" ให้ทำการตรวจสอบอีกครั้งในอนาคต
                getLogger().info("Status is not online. Checking again later...");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // เมธอดที่ใช้ดึงค่า status จาก MySQL
    private String getStatusFromMySQL() throws SQLException {
        String query = "SELECT status FROM player_data WHERE status = ?";  // ตัวอย่าง SQL query ที่มีพารามิเตอร์

        try (PreparedStatement statement = mySQLConnector.getConnection().prepareStatement(query)) {
            // ใส่ค่าให้กับพารามิเตอร์ที่ตำแหน่งที่ 1
            statement.setString(1, "online");


            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("status");
                }
            }
        }

        return "";  // หรือค่าเริ่มต้นที่เหมาะสม
    }

    public class EventListener implements net.md_5.bungee.api.plugin.Listener {

        @EventHandler
        public void onPreLogin(PreLoginEvent event) {
            String playerName = event.getConnection().getName();
            String ipAddress = event.getConnection().getAddress().getAddress().getHostAddress();
            getLogger().info(playerName + "'s IP Address: " + ipAddress);

            String cancellationReason = isPlayerAllowedToLogin(playerName, ipAddress);
            if (cancellationReason != null) {
                event.setCancelled(true);
                event.setCancelReason("§8|| §3TonKungProtector §8|| \n\n\n" + cancellationReason);
            }
        }

        private String isPlayerAllowedToLogin(String playerName, String ipAddress) {
            try {
                // ค้นหาข้อมูลผู้ใช้ในฐานข้อมูล
                String query = "SELECT status, ip FROM player_data WHERE username=?";

                try (PreparedStatement statement = mySQLConnector.getConnection().prepareStatement(query)) {
                    statement.setString(1, playerName);

                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            // ตรวจสอบสถานะและ IP Address
                            String status = resultSet.getString("status");
                            String storedIP = resultSet.getString("ip");

                            getLogger().info(playerName + "'s stored IP Address: " + storedIP);

                            // เช็คว่ามีชื่อที่ตรงกันและ IP ตรงกันในฐานข้อมูลหรือไม่
                            if ("online".equalsIgnoreCase(status)) {
                                if (ipAddress.equals(storedIP)) {
                                    return null;  // ให้ผู้ใช้ที่ตรงกันเข้าเซิร์ฟเวอร์ได้
                                } else {
                                    return "§cIP ที่คุณล็อคอินเข้ามาไม่ตรงกันกับ IP ของระบบ \n\n\n §eแจ้งปัญหาติดตตต่อต้นโดยตรง";  // ถ้า IP ไม่ตรง
                                }
                            } else {
                                return "§cstatus ของคุณไม่ถูกต้อง อย่าพยายามเข้าไอดีทีมงาน เตือนแล้วนะ \n\n\n§eแจ้งปัญหาติดตตต่อต้นโดยตรง";  // ถ้า Status ไม่ตรง
                            }
                        }
                    }
                }

                // หากไม่พบชื่อที่ตรงกันในฐานข้อมูล
                getLogger().info(playerName + " not found in the database.");

                return null;  // ถ้าไม่พบผู้ใช้ในฐานข้อมูล
            } catch (SQLException e) {
                e.printStackTrace();
                return "An error occurred while processing your request.";  // ถ้าเกิดข้อผิดพลาด
            }
        }

    }

}
