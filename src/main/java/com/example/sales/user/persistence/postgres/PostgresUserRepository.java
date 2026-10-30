package com.example.sales.user.persistence.postgres;

import com.example.sales.user.domain.AuthProvider;
import com.example.sales.user.domain.PostalAddress;
import com.example.sales.user.domain.Role;
import com.example.sales.user.domain.User;
import com.example.sales.user.domain.UserPrincipal;
import com.example.sales.user.domain.UserRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/// [UserRepository] on Postgres. The `postal_address` composite is expanded with
/// `(default_address).field` on read and built with `row(...)::postal_address` on write.
@Repository
class PostgresUserRepository implements UserRepository {

    private static final String COLUMNS = """
            id, username, email, password_hash, auth_provider, external_id, roles,
            (default_address).line1, (default_address).line2, (default_address).city,
            (default_address).region, (default_address).postal_code, (default_address).country_code,
            preferences, created_at""";

    private static final TypeReference<Map<String, Object>> PREFERENCES = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final RowMapper<User> userMapper = (rs, _) -> user(rs);

    PostgresUserRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<UserPrincipal> findLocal(String username) {
        return jdbc.sql("select " + COLUMNS + " from users where username = :username and auth_provider = 'LOCAL'")
                .param("username", username)
                .query((rs, _) -> new UserPrincipal(user(rs), rs.getString("password_hash")))
                .optional();
    }

    @Override
    public Optional<User> findByExternalId(String externalId) {
        return jdbc.sql("select " + COLUMNS + " from users where external_id = :externalId")
                .param("externalId", externalId)
                .query(userMapper)
                .optional();
    }

    @Override
    public User createLocal(String username, String email, String passwordHash) {
        return jdbc.sql("""
                        insert into users (username, email, password_hash)
                        values (:username, :email, :passwordHash)
                        returning\s""" + COLUMNS)
                .param("username", username)
                .param("email", email)
                .param("passwordHash", passwordHash)
                .query(userMapper)
                .single();
    }

    @Override
    public User createOAuth2(String externalId, String username, String email) {
        // the no-op update makes "returning" yield the row when a concurrent request inserted it first
        return jdbc.sql("""
                        insert into users (username, email, auth_provider, external_id)
                        values (:username, :email, 'OAUTH2', :externalId)
                        on conflict (external_id) do update set external_id = excluded.external_id
                        returning\s""" + COLUMNS)
                .param("username", username)
                .param("email", email)
                .param("externalId", externalId)
                .query(userMapper)
                .single();
    }

    @Override
    public User updateProfile(UUID id, PostalAddress address, Map<String, Object> preferences) {
        var present = address != null;
        return jdbc.sql("""
                        update users
                           set default_address = case when :present then
                                   row(:line1, :line2, :city, :region, :postalCode, :countryCode)::postal_address
                               end,
                               preferences = cast(:preferences as jsonb)
                         where id = :id
                        returning\s""" + COLUMNS)
                .param("id", id)
                .param("present", present)
                .param("line1", present ? address.line1() : null, Types.VARCHAR)
                .param("line2", present ? address.line2() : null, Types.VARCHAR)
                .param("city", present ? address.city() : null, Types.VARCHAR)
                .param("region", present ? address.region() : null, Types.VARCHAR)
                .param("postalCode", present ? address.postalCode() : null, Types.VARCHAR)
                .param("countryCode", present ? address.countryCode() : null, Types.VARCHAR)
                .param("preferences", json.writeValueAsString(preferences))
                .query(userMapper)
                .single();
    }

    private User user(ResultSet rs) throws SQLException {
        var roles = Arrays.stream((String[]) rs.getArray("roles").getArray())
                .flatMap(Role::named)
                .collect(Collectors.toUnmodifiableSet());
        var address = rs.getString("line1") == null ? null : new PostalAddress(
                rs.getString("line1"), rs.getString("line2"), rs.getString("city"),
                rs.getString("region"), rs.getString("postal_code"), rs.getString("country_code"));
        return new User(
                rs.getObject("id", UUID.class),
                rs.getString("username"),
                rs.getString("email"),
                AuthProvider.valueOf(rs.getString("auth_provider")),
                rs.getString("external_id"),
                roles,
                address,
                json.readValue(rs.getString("preferences"), PREFERENCES),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
