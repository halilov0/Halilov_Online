package com.halilov.online.places;

import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * IL postal lookup under {@code /api/places}. Backs the city + street
 * autocomplete on the checkout shipping form. Inputs are prefix-matched
 * against the locality dataset and capped at 2,000 results to bound
 * response size; the SPA only renders the top 10–20 anyway.
 */
@RestController
@RequestMapping("/api/places")
public class PlacesController {

    private final PlacesService places;

    public PlacesController(PlacesService places) {
        this.places = places;
    }

    @GetMapping("/cities")
    public List<String> cities(
        @RequestParam(name = "q", required = false) String q,
        @RequestParam(name = "limit", defaultValue = "10") int limit
    ) {
        return places.searchCities(q, Math.min(Math.max(limit, 1), 2000));
    }

    @GetMapping("/streets")
    public List<String> streets(
        @RequestParam("city") String city,
        @RequestParam(name = "q", required = false) String q,
        @RequestParam(name = "limit", defaultValue = "10") int limit
    ) {
        return places.searchStreets(city, q, Math.min(Math.max(limit, 1), 2000));
    }
}
