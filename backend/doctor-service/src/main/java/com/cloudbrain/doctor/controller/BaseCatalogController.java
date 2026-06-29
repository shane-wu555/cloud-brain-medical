package com.cloudbrain.doctor.controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/catalog")
public class BaseCatalogController {
    private final JdbcTemplate jdbc;
    public BaseCatalogController(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @GetMapping("/registration-levels") public List<PricedItem> registrationLevels() {
        return jdbc.query("select code,name,fee from registration_level where active order by fee",
                (rs,row)->new PricedItem(rs.getString(1),rs.getString(2),rs.getBigDecimal(3)));
    }
    @PostMapping("/registration-levels") @PreAuthorize("hasRole('ADMIN')")
    public PricedItem createRegistrationLevel(@RequestBody PricedItem item) {
        jdbc.update("insert into registration_level(code,name,fee) values (?,?,?)",item.code(),item.name(),item.price()); return item;
    }
    @GetMapping("/settlement-categories") public List<NamedItem> settlementCategories() {
        return jdbc.query("select code,name from settlement_category where active order by code",
                (rs,row)->new NamedItem(rs.getString(1),rs.getString(2)));
    }
    @PostMapping("/settlement-categories") @PreAuthorize("hasRole('ADMIN')")
    public NamedItem createSettlementCategory(@RequestBody NamedItem item) {
        jdbc.update("insert into settlement_category(code,name) values (?,?)",item.code(),item.name()); return item;
    }
    @GetMapping("/medical-items") public List<MedicalItem> medicalItems(@RequestParam(name="category", required=false) String category) {
        StringBuilder sql = new StringBuilder("""
                select code,name,category,price from medical_item
                where active
                """);
        List<Object> args = new ArrayList<>();
        if (category != null && !category.isBlank()) {
            sql.append(" and category = ?");
            args.add(category);
        }
        sql.append(" order by category,name");
        return jdbc.query(sql.toString(),
                (rs,row)->new MedicalItem(rs.getString(1),rs.getString(2),rs.getString(3),rs.getBigDecimal(4)),
                args.toArray());
    }
    public record NamedItem(String code,String name) {}
    public record PricedItem(String code,String name,BigDecimal price) {}
    public record MedicalItem(String code,String name,String category,BigDecimal price) {}
}
