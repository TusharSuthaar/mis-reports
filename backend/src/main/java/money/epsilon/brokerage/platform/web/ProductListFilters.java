package money.epsilon.brokerage.platform.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A product list or export that takes the shared list filters (R-77, audit L-07). Every such endpoint accepts the whole
 * shared vocabulary (the "All products" screen sends the same filters to every list; a filter a product has no use for
 * is ignored) plus its own {@code @RequestParam}s, and refuses anything else, so a mistaken {@code ?month=} is a clear
 * 400 instead of quietly returning every month.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProductListFilters {
}
