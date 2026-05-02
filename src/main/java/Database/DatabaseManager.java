package Database;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import crdt.BlockCRDT;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:collaborative_editor.db";
    private Connection conn;
    private final Gson gson;

    public DatabaseManager() {
        this.gson = new GsonBuilder().enableComplexMapKeySerialization().create();
        try {
            conn = DriverManager.getConnection(DB_URL);
            initializeDatabase();
            System.out.println("Database initialized successfully.");
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }

    private void initializeDatabase() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS documents (" +
                    "doc_id TEXT PRIMARY KEY, " +
                    "name TEXT NOT NULL, " +
                    "crdt_state TEXT NOT NULL" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS sharing_codes (" +
                    "code TEXT PRIMARY KEY, " +
                    "doc_id TEXT NOT NULL, " +
                    "role TEXT NOT NULL, " + // 'EDITOR' or 'VIEWER'
                    "FOREIGN KEY(doc_id) REFERENCES documents(doc_id)" +
                    ")");
        }
    }

    public void saveDocument(String docId, String name, BlockCRDT crdt) {
        String sql = "INSERT OR REPLACE INTO documents (doc_id, name, crdt_state) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, docId);
            pstmt.setString(2, name);
            pstmt.setString(3, gson.toJson(crdt)); // Serialization
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public BlockCRDT loadDocument(String docId) {
        String sql = "SELECT crdt_state FROM documents WHERE doc_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, docId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String json = rs.getString("crdt_state");
                return gson.fromJson(json, BlockCRDT.class); // Deserialization
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void deleteDocument(String docId) {
        try {
            PreparedStatement p1 = conn.prepareStatement("DELETE FROM sharing_codes WHERE doc_id = ?");
            p1.setString(1, docId);
            p1.executeUpdate();

            PreparedStatement p2 = conn.prepareStatement("DELETE FROM documents WHERE doc_id = ?");
            p2.setString(1, docId);
            p2.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void renameDocument(String docId, String newName) {
        String sql = "UPDATE documents SET name = ? WHERE doc_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newName);
            pstmt.setString(2, docId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Map<String, String>> getAllDocuments() {
        List<Map<String, String>> docs = new ArrayList<>();
        String sql = "SELECT doc_id, name FROM documents";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, String> doc = new HashMap<>();
                doc.put("id", rs.getString("doc_id"));
                doc.put("name", rs.getString("name"));
                docs.add(doc);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return docs;
    }


    public void createSharingCode(String code, String docId, String role) {
        String sql = "INSERT INTO sharing_codes (code, doc_id, role) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, code);
            pstmt.setString(2, docId);
            pstmt.setString(3, role);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public Map<String, String> validateCode(String code) {
        String sql = "SELECT doc_id, role FROM sharing_codes WHERE code = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, code);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                Map<String, String> result = new HashMap<>();
                result.put("doc_id", rs.getString("doc_id"));
                result.put("role", rs.getString("role"));
                return result;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}