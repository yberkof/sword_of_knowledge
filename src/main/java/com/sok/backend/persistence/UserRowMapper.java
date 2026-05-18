package com.sok.backend.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class UserRowMapper implements RowMapper<UserRecord> {
  @Override
  public UserRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new UserRecord(
        rs.getString("id"),
        rs.getString("display_name"),
        rs.getString("username"),
        rs.getString("avatar_url"),
        rs.getString("country_code"),
        rs.getString("title"),
        rs.getInt("level"),
        rs.getInt("xp"),
        rs.getInt("gold"),
        rs.getInt("gems"),
        rs.getInt("trophies"),
        rs.getString("rank"),
        rs.getString("inventory"));
  }
}
