import java.sql.*;
public class DumpRepairs {
  public static void main(String[] a) throws Exception {
    Class.forName("org.sqlite.JDBC");
    Connection c = DriverManager.getConnection("jdbc:sqlite:data/psr.db");
    ResultSet rs = c.createStatement().executeQuery(
      "SELECT r.id, c.org||' '||c.fio, r.equipment, r.problem, r.engineer FROM repairs r LEFT JOIN customers c ON c.id=r.customer_id ORDER BY r.id");
    while (rs.next()) System.out.println(rs.getLong(1)+" | "+rs.getString(2)+" | "+rs.getString(3)+" | "+rs.getString(4)+" | "+rs.getString(5));
  }
}
