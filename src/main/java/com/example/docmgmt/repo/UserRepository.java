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
                    "role TEXT NOT NULL,\n" +
                    "UNIQUE(username, role)\n" +
                    ")");
        }
    }

    public long addUser(String username, Role role) throws SQLException {
        String sql = "INSERT INTO users(username, role) VALUES(?, ?) ON CONFLICT(username, role) DO NOTHING RETURNING id";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, role.name());
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

    public User getByUsername(String username) throws SQLException {
        String sql = "SELECT id, username, role FROM users WHERE username = ? LIMIT 1";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                long id = rs.getLong("id");
                String un = rs.getString("username");
                Role role = Role.valueOf(rs.getString("role"));
                return new User(id, un, role);
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
        String sql = "SELECT id, username, role FROM users ORDER BY id";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            try (var rs = ps.executeQuery()) {
                List<User> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new User(rs.getLong("id"), rs.getString("username"), Role.valueOf(rs.getString("role"))));
                }
                return out;
            }
        }
    }
}

