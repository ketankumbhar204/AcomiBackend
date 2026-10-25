-- Immutable human-readable business references. Existing PR, MR, and PAY values are untouched.
-- nextval is allocated in created_at, id order for rows that do not yet have a code.
-- Gaps after rollback are intentional. Numbers are never reused.

CREATE OR REPLACE FUNCTION acomi_reject_reference_code_change()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.reference_code IS NOT NULL
       AND NEW.reference_code IS DISTINCT FROM OLD.reference_code THEN
        RAISE EXCEPTION 'reference_code is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION acomi_backfill_reference_code(
    target_table text,
    prefix text,
    sequence_name text
)
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
    rec record;
    seq bigint;
    digits text;
    code text;
BEGIN
    FOR rec IN EXECUTE format(
        'SELECT id FROM %I WHERE reference_code IS NULL ORDER BY created_at, id',
        target_table)
    LOOP
        seq := nextval(sequence_name);
        digits := seq::text;
        IF length(digits) < 5 THEN
            digits := lpad(digits, 5, '0');
        END IF;
        code := prefix || digits;
        EXECUTE format(
            'UPDATE %I SET reference_code = $1 WHERE id = $2 AND reference_code IS NULL',
            target_table)
            USING code, rec.id;
    END LOOP;
END;
$$;

DO $$
DECLARE
    spec text[] := ARRAY[
        'users|USR|usr_reference_seq',
        'members|MEM|mem_reference_seq',
        'spaces|SPC|spc_reference_seq',
        'buildings|BLD|bld_reference_seq',
        'floors|FLR|flr_reference_seq',
        'units|UNT|unt_reference_seq',
        'rooms|ROM|rom_reference_seq',
        'beds|BED|bed_reference_seq',
        'occupancies|STY|sty_reference_seq',
        'invitations|INV|inv_reference_seq',
        'space_enquiries|ENQ|enq_reference_seq',
        'space_complaints|CMP|cmp_reference_seq',
        'inquiry_credit_packages|ICP|icp_reference_seq',
        'inquiry_credit_purchase_requests|PCR|pcr_reference_seq',
        'inquiry_credit_ledger|ICL|icl_reference_seq',
        'subscription_activation_requests|SAR|sar_reference_seq',
        'meal_billing_change_requests|MBR|mbr_reference_seq',
        'meal_participations|MPL|mpl_reference_seq',
        'meal_polls|POL|pol_reference_seq',
        'food_categories|FDC|fdc_reference_seq',
        'food_items|FDI|fdi_reference_seq',
        'meal_combos|CMB|cmb_reference_seq',
        'member_documents|DOC|doc_reference_seq',
        'member_meal_balance_ledger|MLB|mlb_reference_seq',
        'inventory_items|STK|stk_reference_seq',
        'inventory_suppliers|SUP|sup_reference_seq',
        'inventory_transactions|STM|stm_reference_seq'
    ];
    item text;
    parts text[];
    target_table text;
    prefix text;
    sequence_name text;
    constraint_name text;
    index_name text;
    trigger_name text;
BEGIN
    FOREACH item IN ARRAY spec LOOP
        parts := string_to_array(item, '|');
        target_table := parts[1];
        prefix := parts[2];
        sequence_name := parts[3];
        constraint_name := 'chk_' || target_table || '_reference_code';
        index_name := 'uq_' || target_table || '_reference_code';
        trigger_name := 'trg_' || target_table || '_reference_code_immutable';

        EXECUTE format(
            'ALTER TABLE %I ADD COLUMN IF NOT EXISTS reference_code varchar(24)',
            target_table);
        EXECUTE format(
            'CREATE SEQUENCE IF NOT EXISTS %I START WITH 1 INCREMENT BY 1',
            sequence_name);
        EXECUTE format('LOCK TABLE %I IN SHARE ROW EXCLUSIVE MODE', target_table);
        PERFORM acomi_backfill_reference_code(target_table, prefix, sequence_name);
        EXECUTE format(
            'ALTER TABLE %I ALTER COLUMN reference_code SET NOT NULL',
            target_table);
        EXECUTE format(
            'ALTER TABLE %I DROP CONSTRAINT IF EXISTS %I',
            target_table, constraint_name);
        EXECUTE format(
            'ALTER TABLE %I ADD CONSTRAINT %I CHECK (reference_code ~ %L)',
            target_table, constraint_name, '^' || prefix || '[0-9]{5,}$');
        EXECUTE format(
            'CREATE UNIQUE INDEX IF NOT EXISTS %I ON %I (reference_code)',
            index_name, target_table);
        EXECUTE format('DROP TRIGGER IF EXISTS %I ON %I', trigger_name, target_table);
        EXECUTE format(
            'CREATE TRIGGER %I BEFORE UPDATE ON %I FOR EACH ROW EXECUTE PROCEDURE acomi_reject_reference_code_change()',
            trigger_name, target_table);
    END LOOP;
END $$;
