package com.example.docmgmt.repo;

import com.example.docmgmt.domain.Models.Role;
import com.example.docmgmt.domain.Models.User;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class UserRepository {
    private final DataSource ds;

    public UserRepository(DataSource ds) {
        this.ds = ds;
    }

    public void migrate() throws SQLException {
        try (var c = ds.getConnection(); var st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS users (\n" +
                    "id BIGSERIAL PRIMARY KEY,\n" +
                    "username TEXT NOT NULL,\n" +
                    "password_hash TEXT NOT NULL,\n" +
                    "role TEXT NOT NULL,\n" +
                    "UNIQUE(username, role)\n" +
                    ")");
        }
    }

    public long addUser(String username, String passwordHash, Role role) throws SQLException {
        String sql = "INSERT INTO users(username, password_hash, role) VALUES(?, ?, ?) ON CONFLICT(username, role) DO NOTHING RETURNING id";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, role.name());
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                } else {
                    // User-role combination already exists, get existing id
                    return getByUsername(username).id();
                }
            }
        }
    }

    // Overload method for backward compatibility
    public long addUser(String username, Role role) throws SQLException {
        // Generate a default password hash for existing users
        String passwordHash = "default_password_hash"; // Simplified for now
        return addUser(username, passwordHash, role);
    }

    public User getByUsername(String username) throws SQLException {
        String sql = "SELECT id, username, password_hash, role FROM users WHERE username = ? LIMIT 1";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                long id = rs.getLong("id");
                String un = rs.getString("username");
                String passwordHash = rs.getString("password_hash");
                Role role = Role.valueOf(rs.getString("role"));
                return new User(id, un, passwordHash, role);
            }
        }
    }
    
    public List<Role> getRolesByUsername(String username) throws SQLException {
        String sql = "SELECT role FROM users WHERE username = ?";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (var rs = ps.executeQuery()) {
                List<Role> roles = new ArrayList<>();
                while (rs.next()) {
                    roles.add(Role.valueOf(rs.getString("role")));
                }
                return roles;
            }
        }
    }

    public List<User> list() throws SQLException {
        String sql = "SELECT id, username, password_hash, role FROM users ORDER BY id";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            try (var rs = ps.executeQuery()) {
                List<User> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new User(rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"), Role.valueOf(rs.getString("role"))));
                }
                return out;
            }
        }
    }

    public void updatePassword(String username, String newPasswordHash) throws SQLException {
        String sql = "UPDATE users SET password_hash = ? WHERE username = ?";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, newPasswordHash);
            ps.setString(2, username);
            ps.executeUpdate();
        }
    }
}

