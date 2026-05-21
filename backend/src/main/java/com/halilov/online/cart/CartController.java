package com.halilov.online.cart;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cart;

    public CartController(CartService cart) {
        this.cart = cart;
    }

    @GetMapping
    public List<CartDtos.CartLineView> get(Authentication auth) {
        return cart.getCart(requireEmail(auth));
    }

    /** Continuous-sync replace — full desired state. */
    @PutMapping
    public List<CartDtos.CartLineView> replace(Authentication auth,
                                               @Valid @RequestBody CartDtos.CartReplaceRequest req) {
        return cart.replaceCart(requireEmail(auth), req.items());
    }

    /** Login merge — sum incoming with existing rows. */
    @PostMapping("/merge")
    public List<CartDtos.CartLineView> merge(Authentication auth,
                                             @Valid @RequestBody CartDtos.CartReplaceRequest req) {
        return cart.mergeCart(requireEmail(auth), req.items());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(Authentication auth) {
        cart.clearCart(requireEmail(auth));
    }

    private static String requireEmail(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no session");
        }
        return auth.getName();
    }
}
