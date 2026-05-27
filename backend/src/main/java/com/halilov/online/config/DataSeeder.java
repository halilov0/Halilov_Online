package com.halilov.online.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.halilov.online.catalog.Category;
import com.halilov.online.catalog.CategoryRepository;
import com.halilov.online.catalog.Product;
import com.halilov.online.catalog.ProductRepository;
import com.halilov.online.user.Role;
import com.halilov.online.user.User;
import com.halilov.online.user.UserRepository;

/**
 * Idempotent first-boot seeding. Inserts an initial admin user from
 * {@code INIT_ADMIN_*} env vars (so a fresh deploy has a way in), and
 * the dev-time category/product fixtures. Safe to leave enabled in
 * prod — every step is conditional on absence.
 */
@Configuration
class DataSeeder {

    @Bean
    CommandLineRunner seedRunner(
        UserRepository users,
        CategoryRepository categories,
        ProductRepository products,
        PasswordEncoder encoder,
        @Value("${app.init.admin.email:admin@halilov.local}") String initAdminEmail,
        @Value("${app.init.admin.password:admin123!}") String initAdminPassword,
        @Value("${app.init.admin.fullName:Admin}") String initAdminFullName
    ) {
        return args -> {
            if (!users.existsByEmail(initAdminEmail)) {
                User admin = new User();
                admin.setEmail(initAdminEmail);
                admin.setPasswordHash(encoder.encode(initAdminPassword));
                admin.setFullName(initAdminFullName);
                admin.setRole(Role.ADMIN);
                users.save(admin);
            }

            if (categories.count() == 0) {
                Category food = saveCat(categories, "food", "מזון", 1);
                Category drinks = saveCat(categories, "drinks", "משקאות", 2);
                Category home = saveCat(categories, "home", "לבית", 3);

                saveProduct(products, "SKU-001", "milk-1l", "חלב תנובה 1 ליטר",
                    "חלב 3% שומן", food.getId(), 690, 100);
                saveProduct(products, "SKU-002", "bread-white", "לחם אחיד פרוס",
                    "750 גרם", food.getId(), 720, 50);
                saveProduct(products, "SKU-003", "hummus-tehina", "חומוס עם טחינה",
                    "400 גרם, צמחוני", food.getId(), 1490, 40);
                saveProduct(products, "SKU-004", "cola-1.5l", "קוקה קולה 1.5 ליטר",
                    "בקבוק 1.5 ליטר", drinks.getId(), 990, 80);
                saveProduct(products, "SKU-005", "water-6pack", "מים מינרליים 6 בקבוקים",
                    "6x1.5 ליטר", drinks.getId(), 1490, 60);
                saveProduct(products, "SKU-006", "dish-soap", "סבון כלים פיירי",
                    "750 מ\"ל", home.getId(), 1290, 30);
            }
        };
    }

    private static Category saveCat(CategoryRepository repo, String slug, String nameHe, int sortOrder) {
        Category c = new Category();
        c.setSlug(slug);
        c.setNameHe(nameHe);
        c.setSortOrder(sortOrder);
        return repo.save(c);
    }

    private static void saveProduct(ProductRepository repo, String sku, String slug, String nameHe,
                                    String descriptionHe, Long categoryId, int priceAgorot, int stockQty) {
        Product p = new Product();
        p.setSku(sku);
        p.setSlug(slug);
        p.setNameHe(nameHe);
        p.setDescriptionHe(descriptionHe);
        p.setCategoryId(categoryId);
        p.setPriceAgorot(priceAgorot);
        p.setStockQty(stockQty);
        p.setActive(true);
        repo.save(p);
    }
}
