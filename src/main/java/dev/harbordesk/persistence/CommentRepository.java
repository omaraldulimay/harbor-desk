package dev.harbordesk.persistence;

import dev.harbordesk.domain.Comment;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CommentRepository {
    private static final RowMapper<Comment> MAPPER = (rs, n) -> new Comment(rs.getLong("id"), rs.getLong("ticket_id"),
            rs.getString("author"), rs.getString("body"), rs.getTimestamp("created_at").toInstant());

    public Comment insert(TenantSession s, long ticketId, String author, String body) {
        return s.jdbc().sql("insert into comments (tenant_id, ticket_id, author, body) values (:t, :ticket, :author, :body) "
                        + "returning id, ticket_id, author, body, created_at")
                .param("t", s.tenant().id()).param("ticket", ticketId).param("author", author).param("body", body)
                .query(MAPPER).single();
    }

    public List<Comment> list(TenantSession s, long ticketId) {
        return s.jdbc().sql("select id, ticket_id, author, body, created_at from comments where ticket_id = :ticket order by id")
                .param("ticket", ticketId).query(MAPPER).list();
    }
}
