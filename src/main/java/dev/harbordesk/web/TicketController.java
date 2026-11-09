package dev.harbordesk.web;

import dev.harbordesk.domain.Comment;
import dev.harbordesk.domain.Ticket;
import dev.harbordesk.domain.TicketService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {
    record CreateTicket(@NotBlank @Size(max = 200) String title, @Size(max = 5000) String body) {
    }

    record ChangeStatus(@NotBlank String status) {
    }

    record CreateComment(@NotBlank @Size(max = 100) String author, @NotBlank @Size(max = 5000) String body) {
    }

    private final TicketService service;

    public TicketController(TicketService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Ticket create(@Valid @RequestBody CreateTicket req) {
        return service.create(req.title(), req.body());
    }

    @GetMapping
    List<Ticket> list(@RequestParam(required = false) String status, @RequestParam(defaultValue = "50") int limit) {
        return service.list(status, limit);
    }

    @GetMapping("/{id}")
    Ticket get(@PathVariable long id) {
        return service.get(id);
    }

    @PatchMapping("/{id}/status")
    Ticket changeStatus(@PathVariable long id, @Valid @RequestBody ChangeStatus req) {
        return service.changeStatus(id, req.status());
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    Comment comment(@PathVariable long id, @Valid @RequestBody CreateComment req) {
        return service.comment(id, req.author(), req.body());
    }

    @GetMapping("/{id}/comments")
    List<Comment> comments(@PathVariable long id) {
        return service.comments(id);
    }
}
