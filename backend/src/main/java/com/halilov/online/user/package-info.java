/**
 * User accounts, roles, and saved addresses.
 *
 * <p>{@link com.halilov.online.user.User} is the central identity:
 * {@code email} (unique), bcrypt password hash, {@link com.halilov.online.user.Role}
 * ({@code CUSTOMER} or {@code ADMIN}), profile fields, marketing
 * opt-in state, lockout / force-logout / 2FA columns.
 *
 * <p>Three controllers cut by audience:
 * <ul>
 *   <li>{@link com.halilov.online.user.AccountController} — logged-in
 *       user self-service ({@code /api/me/**}): profile update, password
 *       change (rotates {@code force_logout_at} and returns a fresh JWT),
 *       saved-address CRUD, marketing opt-in toggle.</li>
 *   <li>{@link com.halilov.online.user.AdminUserController} — admin
 *       CRUD ({@code /api/admin/users/**}): search, disable/enable,
 *       force-logout, reset password.</li>
 * </ul>
 *
 * <p>Saved addresses ({@link com.halilov.online.user.SavedAddress}) are
 * a separate table from the {@code Address} on
 * {@link com.halilov.online.order.Order} — orders snapshot the address
 * at checkout so future profile edits do not rewrite shipped invoices.
 */
package com.halilov.online.user;
