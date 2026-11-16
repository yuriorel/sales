package com.example.sales.seed;

import module java.base;
import module java.net.http;

import com.example.sales.catalog.domain.Dimensions;
import com.example.sales.seed.SeedData.SeedCategory;
import com.example.sales.seed.SeedData.SeedProduct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/// One-off dev tool that builds `src/main/resources/seed/products.json`:
///
/// 1. downloads all products from DummyJSON,
/// 2. maps its categories to the shop's 10 categories (groceries are dropped: a user guide does not fit them),
/// 3. has the chat model write a detailed Markdown user guide per product (at least [#MIN_WORDS] words),
///    on virtual threads with at most [#CONCURRENCY] requests at a time,
/// 4. writes the result, which is committed.
///
/// Guides already in the output file are reused, so a run that failed for some
/// products can simply be repeated. Run with `./mvnw -Pseed-generate spring-boot:run`;
/// it reads `OPENAI_API_KEY` from the environment or from `.env`.
public final class SeedDataGenerator {

    static final URI SOURCE = URI.create("https://dummyjson.com/products?limit=0");
    static final Path OUTPUT = Path.of("src/main/resources/seed/products.json");
    static final int MIN_WORDS = 1500;
    static final int MAX_ATTEMPTS = 3;
    static final int CONCURRENCY = 12;

    static final List<SeedCategory> CATEGORIES = List.of(
            new SeedCategory("smartphones-tablets", "Smartphones & Tablets", "Phones and tablets from the leading brands"),
            new SeedCategory("laptops-computers", "Laptops & Computers", "Laptops and notebooks for work, study and play"),
            new SeedCategory("mobile-accessories", "Mobile Accessories", "Chargers, earphones, speakers, cases and other gadgets"),
            new SeedCategory("kitchen-appliances", "Kitchen & Appliances", "Cookware, utensils and kitchen appliances"),
            new SeedCategory("home-furniture", "Home & Furniture", "Furniture and home decoration"),
            new SeedCategory("sports-outdoors", "Sports & Outdoors", "Equipment for sports, fitness and outdoor activities"),
            new SeedCategory("watches-eyewear", "Watches & Eyewear", "Watches, sunglasses and jewellery"),
            new SeedCategory("beauty-fragrance", "Beauty & Fragrance", "Make-up, skin care and fragrances"),
            new SeedCategory("fashion", "Fashion", "Clothing, shoes and bags for men and women"),
            new SeedCategory("vehicles-motorcycles", "Vehicles & Motorcycles", "Cars and motorcycles"));

    /// DummyJSON category -> shop category slug; unmapped categories are dropped.
    static final Map<String, String> CATEGORY_OF_SOURCE = Map.ofEntries(
            Map.entry("smartphones", "smartphones-tablets"),
            Map.entry("tablets", "smartphones-tablets"),
            Map.entry("laptops", "laptops-computers"),
            Map.entry("mobile-accessories", "mobile-accessories"),
            Map.entry("kitchen-accessories", "kitchen-appliances"),
            Map.entry("furniture", "home-furniture"),
            Map.entry("home-decoration", "home-furniture"),
            Map.entry("sports-accessories", "sports-outdoors"),
            Map.entry("mens-watches", "watches-eyewear"),
            Map.entry("womens-watches", "watches-eyewear"),
            Map.entry("sunglasses", "watches-eyewear"),
            Map.entry("womens-jewellery", "watches-eyewear"),
            Map.entry("beauty", "beauty-fragrance"),
            Map.entry("fragrances", "beauty-fragrance"),
            Map.entry("skin-care", "beauty-fragrance"),
            Map.entry("mens-shirts", "fashion"),
            Map.entry("mens-shoes", "fashion"),
            Map.entry("tops", "fashion"),
            Map.entry("womens-dresses", "fashion"),
            Map.entry("womens-shoes", "fashion"),
            Map.entry("womens-bags", "fashion"),
            Map.entry("vehicle", "vehicles-motorcycles"),
            Map.entry("motorcycle", "vehicles-motorcycles"));

    static final String GUIDE_PROMPT = """
            Write a detailed user guide in Markdown for the product below. Customers and a support
            assistant will search it, so be concrete and specific to this product.

            Product: %s
            Brand: %s
            Category: %s
            Description: %s
            Price: %s USD
            Dimensions (cm): %s
            Weight: %s
            Warranty: %s
            Shipping: %s
            Return policy: %s
            Tags: %s

            Structure:
            - Start with the title line "# %1$s User Guide".
            - Then exactly these sections, in this order, each as a "## " heading:
              Overview, What's in the Box, Specifications, Setup, Operation, Advanced Features,
              Maintenance and Care, Troubleshooting, Safety Information, Warranty and Support, FAQ.
            - Specifications: a Markdown table.
            - Troubleshooting: a Markdown table with the columns Problem | Possible Cause | Solution and at least 8 rows.
            - FAQ: at least 8 questions, each with a full answer.
            - Use numbered steps for procedures. Add plausible model-specific details where facts are
              missing, but never contradict the facts above.
            - Length: at least 2,200 words. Plain Markdown, no code fences.
            """;

    private static final Logger log = LoggerFactory.getLogger(SeedDataGenerator.class);
    private static final Pattern SKU = Pattern.compile("[A-Z0-9-]{4,32}");

    private SeedDataGenerator() {
    }

    static void main() throws IOException, InterruptedException {
        var json = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        var existing = existingProducts(json);
        var source = download(json);
        var chat = ChatClient.create(OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .apiKey(apiKey())
                        .model("gpt-4.1-mini")
                        .temperature(0.7)
                        .maxCompletionTokens(8000)
                        .timeout(Duration.ofMinutes(5))
                        .build())
                .build());

        var permits = new Semaphore(CONCURRENCY);
        List<Future<Optional<SeedProduct>>> futures;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            futures = source.stream()
                    .filter(product -> CATEGORY_OF_SOURCE.containsKey(product.path("category").asString()))
                    .map(product -> executor.submit(() -> {
                        permits.acquire();
                        try {
                            return generate(product, existing, chat);
                        } finally {
                            permits.release();
                        }
                    }))
                    .toList();
        }
        var products = futures.stream()
                .map(Future::resultNow)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(SeedProduct::categorySlug).thenComparing(SeedProduct::name))
                .toList();
        Files.createDirectories(OUTPUT.getParent());
        json.writeValue(OUTPUT.toFile(), new SeedData(CATEGORIES, products));
        log.info("Wrote {} of {} products to {}; {} failed (run again to retry them)",
                products.size(), futures.size(), OUTPUT, futures.size() - products.size());
    }

    /// The seed product for one DummyJSON product, or empty when its guide could not be generated.
    private static Optional<SeedProduct> generate(JsonNode source, Map<String, SeedProduct> existing, ChatClient chat) {
        var sku = sku(source);
        try {
            var known = existing.get(sku);
            var guide = known != null && known.guideWords() >= MIN_WORDS ? known.userGuide() : writeGuide(source, chat);
            var product = toSeedProduct(source, sku, guide);
            log.info("Done {}", product);
            return Optional.of(product);
        } catch (RuntimeException e) {
            log.error("Guide for {} ({}) failed", sku, source.path("title").asString(), e);
            return Optional.empty();
        }
    }

    private static String writeGuide(JsonNode p, ChatClient chat) {
        var prompt = GUIDE_PROMPT.formatted(
                p.path("title").asString(), p.path("brand").asString("unbranded"), p.path("category").asString(),
                p.path("description").asString(), p.path("price").asString(), dimensionsText(p.path("dimensions")),
                p.path("weight").asString(), p.path("warrantyInformation").asString(),
                p.path("shippingInformation").asString(), p.path("returnPolicy").asString(), strings(p.path("tags")));
        for (var attempt = 1; ; attempt++) {
            var guide = chat.prompt().user(prompt).call().content().strip();
            var words = guide.split("\\s+").length;
            if (words >= MIN_WORDS) {
                return guide;
            }
            log.warn("Guide for {} has only {} words (attempt {})", p.path("title").asString(), words, attempt);
            if (attempt == MAX_ATTEMPTS) {
                throw new IllegalStateException("Guide stayed shorter than " + MIN_WORDS + " words");
            }
        }
    }

    private static SeedProduct toSeedProduct(JsonNode p, String sku, String text) {
        // the model sometimes writes typographic apostrophes, also in the fixed section headings
        var guide = text.replace('’', '\'');
        var attributes = new LinkedHashMap<String, Object>();
        attributes.put("weight", p.path("weight").asInt());
        attributes.put("warranty", p.path("warrantyInformation").asString());
        attributes.put("shipping", p.path("shippingInformation").asString());
        attributes.put("returnPolicy", p.path("returnPolicy").asString());
        attributes.put("availability", p.path("availabilityStatus").asString());
        attributes.put("minimumOrderQuantity", p.path("minimumOrderQuantity").asInt());
        attributes.put("discountPercentage", p.path("discountPercentage").decimalValue());
        var dimensions = p.path("dimensions");
        return new SeedProduct(
                sku,
                CATEGORY_OF_SOURCE.get(p.path("category").asString()),
                p.path("title").asString(),
                p.hasNonNull("brand") ? p.path("brand").asString() : null,
                p.path("description").asString(),
                p.path("price").decimalValue().setScale(2, RoundingMode.HALF_UP),
                p.path("rating").decimalValue().setScale(1, RoundingMode.HALF_UP),
                strings(p.path("tags")),
                strings(p.path("images")),
                new Dimensions(cm(dimensions, "width"), cm(dimensions, "height"), cm(dimensions, "depth")),
                attributes,
                p.path("stock").asInt(),
                guide,
                guide.lines().filter(line -> line.startsWith("## ")).map(line -> line.substring(3).strip()).toList());
    }

    private static String sku(JsonNode p) {
        var sku = p.path("sku").asString().toUpperCase(Locale.ROOT);
        return SKU.matcher(sku).matches() ? sku : "DJ-" + p.path("id").asInt();
    }

    private static BigDecimal cm(JsonNode dimensions, String field) {
        return dimensions.path(field).decimalValue().setScale(2, RoundingMode.HALF_UP);
    }

    private static String dimensionsText(JsonNode dimensions) {
        return "%s x %s x %s".formatted(dimensions.path("width").asString(), dimensions.path("height").asString(),
                dimensions.path("depth").asString());
    }

    private static List<String> strings(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asString).toList();
    }

    private static List<JsonNode> download(JsonMapper json) throws IOException, InterruptedException {
        try (var http = HttpClient.newHttpClient()) {
            var response = http.send(HttpRequest.newBuilder(SOURCE).build(), HttpResponse.BodyHandlers.ofString());
            var products = StreamSupport.stream(json.readTree(response.body()).path("products").spliterator(), false).toList();
            log.info("Downloaded {} products from {}", products.size(), SOURCE);
            return products;
        }
    }

    private static Map<String, SeedProduct> existingProducts(JsonMapper json) {
        if (!Files.exists(OUTPUT)) {
            return Map.of();
        }
        return json.readValue(OUTPUT.toFile(), SeedData.class).products().stream()
                .collect(Collectors.toMap(SeedProduct::sku, Function.identity()));
    }

    private static String apiKey() throws IOException {
        var key = System.getenv("OPENAI_API_KEY");
        if (key != null && !key.isBlank()) {
            return key;
        }
        var env = Path.of(".env");
        return (Files.exists(env) ? Files.readAllLines(env) : List.<String>of()).stream()
                .filter(line -> line.startsWith("OPENAI_API_KEY="))
                .map(line -> line.substring("OPENAI_API_KEY=".length()).strip())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Set OPENAI_API_KEY in the environment or in .env"));
    }
}
