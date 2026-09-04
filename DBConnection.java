import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class DBConnection {
	private static final int ITERATIONS = 65_536;
	private static final int KEY_LENGTH = 256;

	private DBConnection() { }

	public static Connection open() throws SQLException {
		String url = setting("DB_URL", "jdbc:mysql://localhost:3306/home_decor_store?useSSL=false&serverTimezone=UTC");
		String user = setting("DB_USER", "root");
		String password = setting("DB_PASSWORD", "");
		return DriverManager.getConnection(url, user, password);
	}

	public static boolean register(String name, String email, String password) throws SQLException {
		String sql = "INSERT INTO users (name, email, password_hash, password_salt) VALUES (?, ?, ?, ?)";
		byte[] salt = new byte[16];
		new SecureRandom().nextBytes(salt);
		try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, name);
			statement.setString(2, email.toLowerCase());
			statement.setString(3, hash(password, salt));
			statement.setString(4, Base64.getEncoder().encodeToString(salt));
			statement.executeUpdate();
			return true;
		} catch (SQLException exception) {
			if (exception.getErrorCode() == 1062) return false;
			throw exception;
		}
	}

	public static String login(String email, String password) throws SQLException {
		String sql = "SELECT name, password_hash, password_salt FROM users WHERE email = ?";
		try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, email.toLowerCase());
			try (ResultSet result = statement.executeQuery()) {
				if (!result.next()) return null;
				byte[] salt = Base64.getDecoder().decode(result.getString("password_salt"));
				return hash(password, salt).equals(result.getString("password_hash")) ? result.getString("name") : null;
			}
		}
	}

	public static long createOrder(String name, String email, String address, double total) throws SQLException {
		String sql = "INSERT INTO orders (customer_name, email, shipping_address, total_amount) VALUES (?, ?, ?, ?)";
		try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, name);
			statement.setString(2, email.toLowerCase());
			statement.setString(3, address);
			statement.setDouble(4, total);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				return keys.next() ? keys.getLong(1) : -1;
			}
		}
	}

	private static String setting(String name, String fallback) {
		String value = System.getenv(name);
		return value == null || value.isBlank() ? fallback : value;
	}

	private static String hash(String password, byte[] salt) {
		try {
			KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
			byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
			return Base64.getEncoder().encodeToString(hash);
		} catch (NoSuchAlgorithmException | InvalidKeySpecException exception) {
			throw new IllegalStateException("Password hashing is unavailable", exception);
		}
	}
}
