package com.example.sales.seed;

import com.example.sales.seed.SeedData.SeedCategory;
import com.example.sales.seed.SeedData.SeedProduct;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// Checks the committed seed file that [SeedDataGenerator] produced.
class SeedDataTest {

    static final List<String> SECTIONS = List.of("Overview", "What's in the Box", "Specifications", "Setup", "Operation",
            "Advanced Features", "Maintenance and Care", "Troubleshooting", "Safety Information", "Warranty and Support", "FAQ");

    static SeedData seed;

    @BeforeAll
    static void readSeedFile() throws IOException {
        try (var in = new ClassPathResource(SeedDataLoader.SEED_FILE).getInputStream()) {
            seed = JsonMapper.builder().build().readValue(in, SeedData.class);
        }
    }

    @Test
    void tenCategoriesAllPopulated() {
        var used = seed.products().stream().map(SeedProduct::categorySlug).collect(Collectors.toSet());

        assertThat(seed.categories()).hasSize(10);
        assertThat(used).containsExactlyInAnyOrderElementsOf(seed.categories().stream().map(SeedCategory::slug).toList());
    }

    @Test
    void aboutOneHundredFiftyToOneHundredNinetyProducts() {
        assertThat(seed.products()).hasSizeBetween(150, 190);
        assertThat(seed.products()).extracting(SeedProduct::sku).doesNotHaveDuplicates();
    }

    @Test
    void everyGuideIsDetailed() {
        assertThat(seed.products()).allSatisfy(product -> {
            assertThat(product.guideWords()).as("words in the guide of %s", product.sku()).isGreaterThanOrEqualTo(1500);
            assertThat(product.guideSections()).as("sections of %s", product.sku()).containsAll(SECTIONS);
        });
    }

    @Test
    void everyProductIsValidProductData() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(seed.products()).allSatisfy(product -> {
                assertThat(validator.validate(product.toProductData(1))).as("violations of %s", product.sku()).isEmpty();
                assertThat(product.tags()).isNotEmpty();
                assertThat(product.imageUrls()).isNotEmpty();
                assertThat(product.attributes()).containsKeys("weight", "warranty", "shipping", "returnPolicy");
                assertThat(product.stock()).isNotNegative();
            });
        }
    }
}
