CREATE TABLE trade_import_registry (
    id CHAR(36) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    first_file_name VARCHAR(255) NOT NULL,
    row_count INT NOT NULL,
    first_imported_at DATETIME(6) NOT NULL,
    last_imported_at DATETIME(6) NOT NULL,
    import_count INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    last_success_count INT NOT NULL,
    last_failure_count INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_trade_import_registry_content_hash UNIQUE (content_hash),
    CONSTRAINT chk_trade_import_registry_row_count
        CHECK (row_count BETWEEN 1 AND 200),
    CONSTRAINT chk_trade_import_registry_import_count
        CHECK (import_count > 0),
    CONSTRAINT chk_trade_import_registry_result_counts
        CHECK (
            last_success_count >= 0
            AND last_failure_count >= 0
            AND last_success_count + last_failure_count <= row_count
        ),
    CONSTRAINT chk_trade_import_registry_status
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'PARTIAL', 'FAILED')),
    INDEX idx_trade_import_registry_last_imported_at (last_imported_at)
) ENGINE=InnoDB;
