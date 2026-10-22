-- Doc 29, phase C: users, roles, teams and permissions now live in each company's core database (user_account,
-- user_group, user_group_member, user_group_permission, user_auth_identity); sign-in reads platform_login.
-- The users were copied there by the phase C identity copy, so the earlier tables go.
DROP TABLE IF EXISTS nx_user_identities CASCADE;
DROP TABLE IF EXISTS nx_user_roles CASCADE;
DROP TABLE IF EXISTS nx_role_permissions CASCADE;
DROP TABLE IF EXISTS nx_teams CASCADE;
DROP TABLE IF EXISTS nx_users CASCADE;
