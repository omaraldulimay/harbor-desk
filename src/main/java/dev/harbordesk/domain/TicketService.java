package dev.harbordesk.domain;

import dev.harbordesk.persistence.CommentRepository;
import dev.harbordesk.persistence.TenantTx;
import dev.harbordesk.persistence.TicketRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/** Ticket use cases. Every database access runs inside TenantTx, which binds the transaction to the tenant. */
@Service
public class TicketService {
    private final TenantTx tx;
    private final TicketRepository tickets;
    private final CommentRepository comments;

    public TicketService(TenantTx tx, TicketRepository tickets, CommentRepository comments) {
        this.tx = tx;
        this.tickets = tickets;
        this.comments = comments;
    }

    public Ticket create(String title, String body) {
        Plan plan = TenantContext.require().plan();
        return tx.run(s -> {
            if (plan.maxTickets() >= 0) {
                // serialize creates of one tenant so count and insert cannot race past the quota
                tickets.lockQuota(s);
                if (tickets.count(s) >= plan.maxTickets()) {
                    throw new QuotaExceededException(plan);
                }
            }
            return tickets.insert(s, title, body == null ? "" : body);
        });
    }

    public List<Ticket> list(String status, int limit) {
        if (status != null) {
            TicketStatus.require(status);
        }
        return tx.run(s -> tickets.list(s, status, Math.max(1, Math.min(limit, 200))));
    }

    public Ticket get(long id) {
        return tx.run(s -> tickets.find(s, id).orElseThrow(() -> new NotFoundException("ticket")));
    }

    public Ticket changeStatus(long id, String status) {
        TicketStatus.require(status);
        return tx.run(s -> tickets.updateStatus(s, id, status).orElseThrow(() -> new NotFoundException("ticket")));
    }

    public Comment comment(long ticketId, String author, String body) {
        return tx.run(s -> {
            tickets.find(s, ticketId).orElseThrow(() -> new NotFoundException("ticket"));
            return comments.insert(s, ticketId, author, body);
        });
    }

    public List<Comment> comments(long ticketId) {
        return tx.run(s -> {
            tickets.find(s, ticketId).orElseThrow(() -> new NotFoundException("ticket"));
            return comments.list(s, ticketId);
        });
    }
}
