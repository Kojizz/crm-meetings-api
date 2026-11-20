package com.example.crm.company;

import com.example.crm.common.ConflictException;
import com.example.crm.common.NotFoundException;
import com.example.crm.common.TimePolicy;
import com.example.crm.common.ValidationException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyService {

    public static final List<String> STATUSES = List.of("new", "no_answer", "refused", "meeting_set");
    private static final int MAX_LIMIT = 100;

    private static final RowMapper<CompanyResponse> COMPANY_MAPPER = (rs, rowNum) -> new CompanyResponse(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("bin"),
            rs.getString("phone"),
            rs.getString("status"),
            rs.getString("created_at"));

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public CompanyService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public CompanyResponse create(CreateCompanyRequest request) {
        List<Long> existing = jdbc.queryForList("SELECT id FROM companies WHERE bin = ?", Long.class, request.bin());
        if (!existing.isEmpty()) {
            throw new ConflictException("Company with BIN %s already exists (id=%d)"
                    .formatted(request.bin(), existing.get(0)));
        }
        jdbc.update("INSERT INTO companies (name, bin, phone, status, created_at) VALUES (?, ?, ?, 'new', ?)",
                request.name().strip(), request.bin(), request.phone().strip(), TimePolicy.format(clock.instant()));
        Long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        return get(id);
    }

    public CompanyResponse get(long id) {
        return jdbc.query("SELECT * FROM companies WHERE id = ?", COMPANY_MAPPER, id).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Company %d not found".formatted(id)));
    }

    public CompanyPage list(String status, int limit, int offset) {
        if (status != null && !Set.copyOf(STATUSES).contains(status)) {
            throw new ValidationException("status", "must be one of: " + String.join(", ", STATUSES));
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ValidationException("limit", "must be between 1 and " + MAX_LIMIT);
        }
        if (offset < 0) {
            throw new ValidationException("offset", "must be greater than or equal to 0");
        }
        String where = status == null ? "" : " WHERE status = ?";
        List<Object> params = new ArrayList<>();
        if (status != null) {
            params.add(status);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM companies" + where, Long.class, params.toArray());
        params.add(limit);
        params.add(offset);
        List<CompanyResponse> items = jdbc.query(
                "SELECT * FROM companies" + where + " ORDER BY id LIMIT ? OFFSET ?", COMPANY_MAPPER, params.toArray());
        return new CompanyPage(items, total, limit, offset);
    }
}
