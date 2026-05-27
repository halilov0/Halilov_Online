/**
 * Israeli postal places autocomplete.
 *
 * <p>{@link com.halilov.online.places.PlacesController} backs the city
 * picker on the checkout shipping form. {@code PlacesService} resolves
 * a user-typed prefix against the locality dataset and returns the
 * top matches.
 *
 * <p>Kept as a proxy so we can swap the underlying source (currently a
 * static bundled list) for a live API without disturbing the frontend.
 */
package com.halilov.online.places;
