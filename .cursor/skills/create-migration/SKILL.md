---
name: create-migration
description: Generate a new Supabase migration SQL file with correct sequential numbering in supabase/migrations/
disable-model-invocation: true
---

# Create Supabase Migration

Generate a new SQL migration file in `supabase/migrations/` with the correct sequential number.

## Steps

1. List existing files in `supabase/migrations/` to determine the next sequence number (format: `NNN_description.sql`, zero-padded to 3 digits).
2. Ask the user what the migration should do if not already specified.
3. Write the SQL migration file with:
   - Correct sequential numbering (e.g., if last is `004_`, next is `005_`)
   - Descriptive snake_case filename (e.g., `005_add_status_to_profiles.sql`)
   - Idempotent SQL where possible (use `IF NOT EXISTS`, `IF EXISTS`)
   - A comment header describing the migration purpose
4. Remind the user to:
   - Review the generated SQL
   - Apply it via `supabase db push` or the Supabase MCP `apply_migration` tool
   - Verify Hibernate validation still passes with `./gradlew test`
