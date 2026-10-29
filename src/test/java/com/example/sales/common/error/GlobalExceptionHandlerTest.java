package com.example.sales.common.error;

import com.example.sales.common.logging.CorrelationFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new CorrelationFilter())
            .build();

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(textBlock = """
            not-found,       404, Product '42' was not found
            conflict,        409, Cart is locked
            validation,      400, Quantity exceeds stock
            unique,          409, The request conflicts with existing data
            check,           400, The request contains a value that violates a data constraint
            unexpected,      500, Unexpected server error
            """)
    void mapsExceptionToProblemDetail(String kind, int status, String detail) throws Exception {
        mvc.perform(get("/throw/{kind}", kind).header(CorrelationFilter.HEADER, "corr-1"))
                .andExpect(status().is(status))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.detail").value(detail))
                .andExpect(jsonPath("$.instance").value("/throw/" + kind))
                .andExpect(jsonPath("$.correlationId").value("corr-1"));
    }

    @Test
    void businessValidationListsFieldErrors() throws Exception {
        mvc.perform(get("/throw/validation"))
                .andExpect(jsonPath("$.errors[0].field").value("quantity"))
                .andExpect(jsonPath("$.errors[0].message").value("Quantity exceeds stock"));
    }

    @Test
    void invalidRequestBodyListsFieldErrors() throws Exception {
        mvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "", "quantity": 0}
                        """).header(CorrelationFilter.HEADER, "corr-2"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.correlationId").value("corr-2"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'quantity')]").exists());
    }

    @Test
    void invalidRequestParameterListsFieldErrors() throws Exception {
        mvc.perform(get("/param").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void unsupportedMethodIsProblemDetail() throws Exception {
        mvc.perform(post("/throw/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    record Body(@NotBlank String name, @Min(1) int quantity) {
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/throw/{kind}")
        void raise(@PathVariable String kind) {
            throw switch (kind) {
                case "not-found" -> new NotFoundException("Product", 42);
                case "conflict" -> new ConflictException("Cart is locked");
                case "validation" -> new ValidationException("quantity", "Quantity exceeds stock");
                case "unique" -> new DataIntegrityViolationException("dup", new SQLException("dup", "23505"));
                case "check" -> new DataIntegrityViolationException("check", new SQLException("check", "23514"));
                default -> new IllegalStateException("boom");
            };
        }

        @PostMapping("/body")
        void body(@Valid @RequestBody Body body) {
        }

        @GetMapping("/param")
        void param(@RequestParam @Min(1) int size) {
        }
    }
}
