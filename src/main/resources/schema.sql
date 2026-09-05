DROP TABLE IF EXISTS exception_record;
DROP TABLE IF EXISTS match_result;
DROP TABLE IF EXISTS tax_log;
DROP TABLE IF EXISTS bank_statement_line;
DROP TABLE IF EXISTS settlement_record;

CREATE TABLE settlement_record (
                                   settlement_id     VARCHAR(50) PRIMARY KEY,
                                   payment_id        VARCHAR(50) NOT NULL,
                                   gross_amount      NUMERIC(15,2) NOT NULL,
                                   fee               NUMERIC(15,2) NOT NULL,
                                   tax_on_fee        NUMERIC(15,2) NOT NULL,
                                   reserve_held      NUMERIC(15,2) NOT NULL DEFAULT 0,
                                   reserve_released  NUMERIC(15,2) NOT NULL DEFAULT 0,
                                   net_settled       NUMERIC(15,2) NOT NULL,
                                   utr               VARCHAR(50),
                                   settled_at        TIMESTAMP NOT NULL,
                                   reconciliation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                                       CHECK (reconciliation_status IN ('PENDING', 'MATCHED', 'AMBIGUOUS','UNRESOLVED')),
                                   version           BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE bank_statement_line (
                                     line_id           SERIAL PRIMARY KEY,
                                     narration         TEXT NOT NULL,
                                     amount            NUMERIC(15,2) NOT NULL,
                                     value_date        DATE NOT NULL,
                                     raw_utr_guess     VARCHAR(50)
);

CREATE TABLE tax_log (
                         transaction_id      VARCHAR(50) PRIMARY KEY,
                         gst_on_fee           NUMERIC(15,2) NOT NULL,
                         tds_194o              NUMERIC(15,2) NOT NULL,
                         expected_deduction   NUMERIC(15,2) NOT NULL
);

CREATE TABLE match_result (
                              id              SERIAL PRIMARY KEY,
                              settlement_id   VARCHAR(50) REFERENCES settlement_record(settlement_id),
                              bank_line_id    INT REFERENCES bank_statement_line(line_id),
                              pass            SMALLINT NOT NULL CHECK (pass IN (1, 2, 3)),
                              confidence      NUMERIC(4,3) NOT NULL CHECK (confidence >= 0 AND confidence <= 1),
                              rules_applied   TEXT[],
                              reasoning       TEXT NOT NULL,
                              matched_at      TIMESTAMP DEFAULT now()
);

CREATE TABLE exception_record (
                                  id              SERIAL PRIMARY KEY,
                                  settlement_id   VARCHAR(50) REFERENCES settlement_record(settlement_id),
                                  category        VARCHAR(50) NOT NULL CHECK (category IN (
                                                                                           'Unmatched_Gateway_Record',
                                                                                           'Fee_Variance',
                                                                                           'Timing_Discrepancy',
                                                                                           'Duplicate_Amount_Ambiguity'
                                      )),
                                  reasoning       TEXT NOT NULL,
                                  flagged_at      TIMESTAMP DEFAULT now()
);