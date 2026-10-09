-- V74: a picker no longer needs an application login.
-- V39 made nx_pickers.user_id NOT NULL, but neither the UI nor the API creates or links a user
-- when adding a picker, so every "Add picker" failed with a constraint violation. Floor staff
-- often have no system account; the RF app identifies them by picker id. Link a login later
-- by setting user_id.
ALTER TABLE nx_pickers ALTER COLUMN user_id DROP NOT NULL;
